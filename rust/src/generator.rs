/*
 * Copyright (C) 2007 Time Intermedia Corporation <puzzle@timedia.co.jp>
 * Rust rewrite derived from NPGenerator V2.0.2.
 *
 * Number Place Generator Version 2.0
 * Director: Hirofumi Fujiwara / Puzzler: Naoki Inaba
 * Programmer: Masaya Kiwada
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

use std::sync::Arc;

use crate::model::{AnswerKind, BlockConstraint, SolverMethod, Status};
use crate::random::JavaRandom;
use crate::solver::{add_number, answer_in_place};

pub struct Generator<'a> {
    size: usize,
    hint: Vec<i32>,
    hidden: Vec<i32>,
    seed: Vec<i32>,
    hidden_list: Vec<usize>,
    hint_list: Vec<usize>,
    groups: Vec<Vec<usize>>,
    random: &'a mut JavaRandom,
    seed_method: SolverMethod,
    method: SolverMethod,
    forbidden: i32,
    seed_status: Status,
    solve_status: Status,
    group_status: Status,
    original: Status,
    trial: Status,
}

impl<'a> Generator<'a> {
    pub fn new(
        size: usize,
        hint: Vec<i32>,
        hidden: Vec<i32>,
        block: Arc<BlockConstraint>,
        random: &'a mut JavaRandom,
    ) -> Self {
        Self::new_with_seed(size, hint, hidden, block, random, None)
    }

    pub fn new_with_seed(
        size: usize,
        hint: Vec<i32>,
        hidden: Vec<i32>,
        block: Arc<BlockConstraint>,
        random: &'a mut JavaRandom,
        seed: Option<Vec<i32>>,
    ) -> Self {
        let hidden_list = hidden
            .iter()
            .enumerate()
            .filter_map(|(index, &value)| (value != 0).then_some(index))
            .collect();
        let hint_list: Vec<_> = hint
            .iter()
            .enumerate()
            .filter_map(|(index, &value)| (value != 0).then_some(index))
            .collect();
        let mut group_count = (hint_list.len() as f64).sqrt() as usize;
        if group_count == 0 {
            group_count = 1;
        }
        let remainder = hint_list.len() % group_count;
        let unit = hint_list.len() / group_count;
        let mut groups = Vec::new();
        let mut cursor = 0;
        for i in 0..group_count {
            let count = unit + usize::from(i < remainder);
            groups.push(hint_list[cursor..cursor + count].to_vec());
            cursor += count;
        }
        let mut seed_method = SolverMethod::none();
        seed_method.localization = true;
        seed_method.naked_pair = true;
        seed_method.hidden_pair = true;
        let seed_status = Status::new(size, Arc::clone(&block));
        let solve_status = Status::new(size, Arc::clone(&block));
        let group_status = Status::new(size, Arc::clone(&block));
        let original = Status::new(size, Arc::clone(&block));
        let trial = Status::new(size, Arc::clone(&block));
        Self {
            size,
            hint,
            hidden,
            seed: seed.unwrap_or_default(),
            hidden_list,
            hint_list,
            groups,
            random,
            seed_method,
            method: SolverMethod::none(),
            forbidden: -1,
            seed_status,
            solve_status,
            group_status,
            original,
            trial,
        }
    }

    pub fn set_method(&mut self, method: SolverMethod) {
        self.method = method;
    }

    pub fn set_forbidden(&mut self, forbidden: i32) {
        self.forbidden = forbidden;
    }

    fn generate_seed_sub(&mut self) -> Option<Vec<i32>> {
        let status = &mut self.seed_status;
        status.clear();
        for (cell, &n) in self.hidden.iter().enumerate() {
            if n > 0 {
                add_number(status, cell, n);
            }
        }
        answer_in_place(status, self.seed_method);
        for cell in 0..self.hidden.len() {
            if status.cell[cell] != 0 {
                continue;
            }
            let count = status.cand_count_cell(cell) as i32;
            if count == 0 || status.is_no_answer() {
                return None;
            }
            let random_index = self.random.next_int(count);
            let mut n = status.nth_candidate(cell, random_index);
            if self.hint[cell] != 0 && n == self.forbidden {
                if count == 1 {
                    return None;
                }
                n = status.nth_candidate(cell, (random_index + 1) % count);
            }
            add_number(status, cell, n);
            answer_in_place(status, self.seed_method);
        }
        if self.forbidden > 0
            && self
                .hint_list
                .iter()
                .any(|&cell| status.cell[cell] == self.forbidden)
        {
            return None;
        }
        (status.space_count == 0).then(|| status.cell.clone())
    }

    fn generate_seed(&mut self) -> Option<Vec<i32>> {
        for _ in 0..=100 {
            if let Some(seed) = self.generate_seed_sub() {
                return Some(seed);
            }
        }
        None
    }

    fn fits_hidden(&self, cell: &[i32]) -> bool {
        self.hidden_list
            .iter()
            .all(|&index| cell[index] == self.hidden[index])
    }

    pub fn generate(&mut self) -> Option<Vec<i32>> {
        self.seed = self.generate_seed()?;
        let mut problem = vec![0; self.hidden.len()];
        for &cell in &self.hint_list {
            problem[cell] = self.seed[cell];
        }
        let status = &mut self.solve_status;
        status.clear();
        status.unique = self.method.unique;
        for (cell, &n) in problem.iter().enumerate() {
            if n > 0 {
                add_number(status, cell, n);
            }
        }
        answer_in_place(status, self.method);
        if status.kind == AnswerKind::Unique {
            return Some(problem);
        }

        self.random.shuffle(&mut self.hint_list);
        let mut zero = status.space_count;
        if zero == 0 {
            return Some(problem);
        }
        let mut candidates = Vec::with_capacity(self.size);
        let mut yet = true;
        while yet {
            yet = false;
            for group_index in 0..self.groups.len() {
                self.group_status.clear();
                self.group_status.unique = self.method.unique;
                for (other_index, other_group) in self.groups.iter().enumerate() {
                    if group_index != other_index {
                        for &cell in other_group {
                            add_number(&mut self.group_status, cell, problem[cell]);
                        }
                    }
                }
                let group_len = self.groups[group_index].len();
                for cell_index in 0..group_len {
                    let cell = self.groups[group_index][cell_index];
                    if self.hidden[cell] != 0 {
                        continue;
                    }
                    let previous = problem[cell];
                    problem[cell] = 0;
                    self.original.copy_from_status(&self.group_status);
                    for other_index in 0..group_len {
                        let other = self.groups[group_index][other_index];
                        if other != cell {
                            add_number(&mut self.original, other, problem[other]);
                        }
                    }
                    if self.original.cand_count_cell(cell) <= 1 {
                        problem[cell] = previous;
                        continue;
                    }
                    candidates.clear();
                    for n in 1..=self.size as i32 {
                        if self.original.is_cand(cell, n) {
                            candidates.push(n);
                        }
                    }
                    if !candidates.contains(&previous) {
                        candidates.push(previous);
                    }
                    let previous_index = candidates
                        .iter()
                        .position(|&n| n == previous)
                        .expect("previous candidate must be present");
                    candidates.rotate_left(previous_index);
                    candidates.remove(0);
                    problem[cell] = previous;
                    for candidate_index in 0..candidates.len() {
                        let next = candidates[candidate_index];
                        if next == previous || next == self.forbidden {
                            continue;
                        }
                        self.trial.copy_from_status(&self.original);
                        add_number(&mut self.trial, cell, next);
                        answer_in_place(&mut self.trial, self.method);
                        let space = self.trial.space_count;
                        if !self.trial.is_no_answer()
                            && self.fits_hidden(&self.trial.cell)
                            && zero > space
                        {
                            zero = space;
                            problem[cell] = next;
                            if zero == 0 {
                                return Some(problem);
                            }
                            yet = true;
                            break;
                        }
                    }
                    if yet {
                        break;
                    }
                }
            }
        }
        (zero == 0).then_some(problem)
    }
}
