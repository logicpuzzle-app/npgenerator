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

use crate::model::{BlockConstraint, Status};

const INFINITY: i32 = 1 << 29;
const MAX_BLOCKS: usize = 50;

struct StackList<T: Copy> {
    values: [T; MAX_BLOCKS],
    len: usize,
}

impl<T: Copy> StackList<T> {
    #[inline]
    fn new(initial: T) -> Self {
        Self {
            values: [initial; MAX_BLOCKS],
            len: 0,
        }
    }

    #[inline]
    fn clear(&mut self) {
        self.len = 0;
    }

    #[inline]
    fn push(&mut self, value: T) {
        self.values[self.len] = value;
        self.len += 1;
    }

    #[inline]
    fn len(&self) -> usize {
        self.len
    }
}

impl<T: Copy> std::ops::Index<usize> for StackList<T> {
    type Output = T;

    #[inline]
    fn index(&self, index: usize) -> &Self::Output {
        &self.values[index]
    }
}

struct CandidatePoints {
    size: usize,
    values: Vec<i32>,
}

impl CandidatePoints {
    fn new(cell_count: usize, size: usize) -> Self {
        Self {
            size: size + 1,
            values: vec![INFINITY; cell_count * (size + 1)],
        }
    }

    fn get(&self, cell: usize, n: i32) -> i32 {
        self.values[cell * self.size + n as usize]
    }

    fn lower(&mut self, cell: usize, n: i32, value: i32) -> bool {
        let index = cell * self.size + n as usize;
        if self.values[index] > value {
            self.values[index] = value;
            true
        } else {
            false
        }
    }
}

fn delete_peer(state: &mut Status, cell: usize, n: i32, points: &mut CandidatePoints) {
    for value in 1..=state.size as i32 {
        if value != n {
            state.delete_candidate(cell, value);
            points.lower(cell, value, 1);
        }
    }
    let membership_count = state.block.where_cell[cell].len();
    for membership_index in 0..membership_count {
        let block = state.block.where_cell[cell][membership_index];
        let cell_count = state.block.blocks[block].len();
        for cell_index in 0..cell_count {
            let peer = state.block.blocks[block][cell_index];
            if peer == cell {
                continue;
            }
            state.delete_candidate(peer, n);
            points.lower(peer, n, if block < state.size * 2 { 3 } else { 2 });
        }
    }
}

fn add_number(state: &mut Status, cell: usize, n: i32, points: &mut CandidatePoints) {
    if state.assign_value(cell, n) {
        delete_peer(state, cell, n, points);
    }
}

fn localization(state: &mut Status, points: &mut CandidatePoints) -> bool {
    let mut updated = false;
    let mut c1 = [0_u8; 26];
    let mut c2 = [0_u8; 26];
    let mut c12 = [0_u8; 26];
    let intersection_count = state.block.intersection_list.len();
    for intersection_index in 0..intersection_count {
        let intersection = state.block.intersection_list[intersection_index];
        let i = intersection.first;
        let j = intersection.second;
        for n in 1..=state.size {
            c12[n] = (state.candidate_index_mask(i, n as i32)
                & intersection.first_mask)
                .count_ones() as u8;
            c1[n] = state.cand_count_block(i, n as i32) as u8 - c12[n];
            c2[n] = state.cand_count_block(j, n as i32) as u8 - c12[n];
        }
        for n in 1..=state.size {
            if c12[n] == 0 {
                continue;
            }
            if c1[n] > 0 && c2[n] == 0 {
                let mut value = 0;
                for (index, &cell) in state.block.blocks[j].iter().enumerate() {
                    if intersection.second_mask & (1 << index) == 0 {
                        value += points.get(cell, n as i32);
                    }
                }
                let cell_count = state.block.blocks[i].len();
                for cell_index in 0..cell_count {
                    let cell = state.block.blocks[i][cell_index];
                    if intersection.first_mask & (1 << cell_index) == 0 {
                        state.delete_candidate(cell, n as i32);
                        updated |= points.lower(cell, n as i32, value);
                    }
                }
            }
            if c1[n] == 0 && c2[n] > 0 {
                let mut value = 0;
                for (index, &cell) in state.block.blocks[i].iter().enumerate() {
                    if intersection.first_mask & (1 << index) == 0 {
                        value += points.get(cell, n as i32);
                    }
                }
                let cell_count = state.block.blocks[j].len();
                for cell_index in 0..cell_count {
                    let cell = state.block.blocks[j][cell_index];
                    if intersection.second_mask & (1 << cell_index) == 0 {
                        state.delete_candidate(cell, n as i32);
                        updated |= points.lower(cell, n as i32, value);
                    }
                }
            }
        }
    }
    updated
}

fn naked_pair(state: &mut Status, points: &mut CandidatePoints) -> bool {
    let mut updated = false;
    let mut cells = StackList::new(0_usize);
    let mut pairs = StackList::new(0_i32);
    for block in 0..state.block.blocks.len() {
        cells.clear();
        pairs.clear();
        for cell_index in 0..state.block.blocks[block].len() {
            let cell = state.block.blocks[block][cell_index];
            if state.cand_count_cell(cell) == 2 {
                let first_bit = state.cand[cell].trailing_zeros() as i32 + 1;
                let second_bit =
                    (state.cand[cell] & (state.cand[cell] - 1)).trailing_zeros() as i32 + 1;
                cells.push(cell);
                pairs.push(first_bit * (state.size as i32 + 1) + second_bit);
            }
        }
        for j in 0..cells.len() {
            for k in 0..j {
                if pairs[j] != pairs[k] {
                    continue;
                }
                let p1 = pairs[j] / (state.size as i32 + 1);
                let p2 = pairs[j] % (state.size as i32 + 1);
                let mut value = 0;
                for n in 1..=state.size as i32 {
                    if n != p1 && n != p2 {
                        value += points.get(cells[j], n);
                        value += points.get(cells[k], n);
                    }
                }
                let cell_count = state.block.blocks[block].len();
                for cell_index in 0..cell_count {
                    let cell = state.block.blocks[block][cell_index];
                    if cell != cells[j] && cell != cells[k] {
                        state.delete_candidate(cell, p1);
                        updated |= points.lower(cell, p1, value);
                        state.delete_candidate(cell, p2);
                        updated |= points.lower(cell, p2, value);
                    }
                }
            }
        }
    }
    updated
}

fn hidden_pair(state: &mut Status, points: &mut CandidatePoints) -> bool {
    let mut updated = false;
    let mut numbers = StackList::new(0_i32);
    let mut sets = StackList::new(0_u32);
    for block in 0..state.block.blocks.len() {
        numbers.clear();
        sets.clear();
        for n in 1..=state.size as i32 {
            let positions = state.candidate_index_mask(block, n);
            if positions.count_ones() == 2 {
                numbers.push(n);
                sets.push(positions);
            }
        }
        for i in 0..numbers.len() {
            for j in 0..i {
                if sets[i] != sets[j] {
                    continue;
                }
                let mut value = 0;
                for (index, &cell) in state.block.blocks[block].iter().enumerate() {
                    if sets[i] & (1 << index) == 0 {
                        value += points.get(cell, numbers[i]);
                        value += points.get(cell, numbers[j]);
                    }
                }
                let first = sets[i].trailing_zeros() as usize;
                let second = (sets[i] & (sets[i] - 1)).trailing_zeros() as usize;
                for &index in &[first, second] {
                    let cell = state.block.blocks[block][index];
                    for n in 1..=state.size as i32 {
                        if n != numbers[i] && n != numbers[j] {
                            state.delete_candidate(cell, n);
                            updated |= points.lower(cell, n, value);
                        }
                    }
                }
            }
        }
    }
    updated
}

fn naked_triple(state: &mut Status, points: &mut CandidatePoints) -> bool {
    let mut updated = false;
    let mut cells = StackList::new(0_usize);
    let mut sets = StackList::new(0_i32);
    for block in 0..state.block.blocks.len() {
        cells.clear();
        sets.clear();
        for cell_index in 0..state.block.blocks[block].len() {
            let cell = state.block.blocks[block][cell_index];
            if state.cand_count_cell(cell) <= 3 {
                cells.push(cell);
                sets.push(state.cand[cell] << 1);
            }
        }
        for j in 0..cells.len() {
            for k in 0..j {
                for l in 0..k {
                    let union = sets[j] | sets[k] | sets[l];
                    if union.count_ones() != 3 {
                        continue;
                    }
                    let mut value = 0;
                    for n in 1..=state.size as i32 {
                        if union & (1 << n) == 0 {
                            value += points.get(cells[j], n);
                            value += points.get(cells[k], n);
                            value += points.get(cells[l], n);
                        }
                    }
                    let cell_count = state.block.blocks[block].len();
                    for cell_index in 0..cell_count {
                        let cell = state.block.blocks[block][cell_index];
                        if cell != cells[j] && cell != cells[k] && cell != cells[l] {
                            for n in (1..=state.size as i32).rev() {
                                if union & (1 << n) != 0 {
                                    state.delete_candidate(cell, n);
                                    updated |= points.lower(cell, n, value);
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    updated
}

fn hidden_triple(state: &mut Status, points: &mut CandidatePoints) -> bool {
    let mut updated = false;
    let mut numbers = StackList::new(0_i32);
    let mut sets = StackList::new(0_u32);
    for block in 0..state.block.blocks.len() {
        numbers.clear();
        sets.clear();
        for n in 1..=state.size as i32 {
            if state.cand_count_block(block, n) <= 3 {
                numbers.push(n);
                sets.push(state.candidate_index_mask(block, n));
            }
        }
        for i in 0..numbers.len() {
            for j in 0..i {
                for k in 0..j {
                    let union = sets[i] | sets[j] | sets[k];
                    if union.count_ones() != 3 {
                        continue;
                    }
                    let value: i32 = state.block.blocks[block]
                        .iter()
                        .enumerate()
                        .filter(|(index, _)| union & (1 << index) == 0)
                        .map(|(_, &cell)| cell)
                        .map(|cell| {
                            points.get(cell, numbers[i])
                                + points.get(cell, numbers[j])
                                + points.get(cell, numbers[k])
                        })
                        .sum();
                    for index in (0..state.size).rev() {
                        if union & (1_u32 << index) == 0 {
                            continue;
                        }
                        let cell = state.block.blocks[block][index];
                        for n in 1..=state.size as i32 {
                            if n != numbers[i] && n != numbers[j] && n != numbers[k] {
                                state.delete_candidate(cell, n);
                                updated |= points.lower(cell, n, value);
                            }
                        }
                    }
                }
            }
        }
    }
    updated
}

fn xwing(state: &mut Status, points: &mut CandidatePoints) -> bool {
    let mut updated = false;
    let size = state.size;
    for n in 1..=size as i32 {
        for vertical in [true, false] {
            let range = if vertical { 0..size } else { size..size * 2 };
            for i in range.clone() {
                let positions = state.candidate_index_mask(i, n);
                if positions.count_ones() != 2 {
                    continue;
                }
                for j in i + 1..range.end {
                    if state.candidate_index_mask(j, n) != positions {
                        continue;
                    }
                    let mut value = 0;
                    for &source in &[i, j] {
                        for (index, &cell) in
                            state.block.blocks[source].iter().enumerate()
                        {
                            if positions & (1 << index) == 0 {
                                value += points.get(cell, n);
                            }
                        }
                    }
                    let mut targets = positions;
                    while targets != 0 {
                        let target_index = targets.trailing_zeros() as usize;
                        targets &= targets - 1;
                        let target_block =
                            if vertical { target_index + size } else { target_index };
                        let cell_count = state.block.blocks[target_block].len();
                        for cell_index in 0..cell_count {
                            let cell = state.block.blocks[target_block][cell_index];
                            let source = if vertical {
                                cell % size
                            } else {
                                cell / size + size
                            };
                            if source != i && source != j {
                                state.delete_candidate(cell, n);
                                updated |= points.lower(cell, n, value);
                            }
                        }
                    }
                }
            }
        }
    }
    updated
}

fn swordfish(state: &mut Status, points: &mut CandidatePoints) -> bool {
    let mut updated = false;
    let size = state.size;
    let mut sets = [0_u32; MAX_BLOCKS];
    for n in 1..=size as i32 {
        for (block, set) in sets[..size * 2].iter_mut().enumerate() {
            *set = if state.cand_count_block(block, n) > 3 {
                0
            } else {
                state.candidate_index_mask(block, n)
            };
        }
        for vertical in [true, false] {
            let range = if vertical { 0..size } else { size..size * 2 };
            for i in range.clone() {
                if sets[i] == 0 {
                    continue;
                }
                for j in i + 1..range.end {
                    if sets[j] == 0 {
                        continue;
                    }
                    for k in j + 1..range.end {
                        if sets[k] == 0 {
                            continue;
                        }
                        let union = sets[i] | sets[j] | sets[k];
                        if union.count_ones() != 3 {
                            continue;
                        }
                        let value: i32 = [i, j, k]
                            .into_iter()
                            .flat_map(|block| state.block.blocks[block].iter().copied())
                            .filter(|&cell| {
                                if vertical {
                                    union & (1_u32 << (cell / size)) == 0
                                } else {
                                    union & (1_u32 << (cell % size)) == 0
                                }
                            })
                            .map(|cell| points.get(cell, n))
                            .sum();
                        for target_index in (0..size).rev() {
                            if union & (1_u32 << target_index) == 0 {
                                continue;
                            }
                            let target = if vertical {
                                target_index + size
                            } else {
                                target_index
                            };
                            let cell_count = state.block.blocks[target].len();
                            for cell_index in 0..cell_count {
                                let cell = state.block.blocks[target][cell_index];
                                let source = if vertical { cell % size } else { cell / size + size };
                                if source != i && source != j && source != k {
                                    state.delete_candidate(cell, n);
                                    updated |= points.lower(cell, n, value);
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    updated
}

pub fn evaluate(size: usize, block: Arc<BlockConstraint>, problem: &[i32]) -> f64 {
    let mut state = Status::new(size, block);
    let mut points = CandidatePoints::new(state.cell.len(), size);
    for (cell, &n) in problem.iter().enumerate() {
        if n > 0 {
            add_number(&mut state, cell, n, &mut points);
        }
    }
    let mut value = 0.0;
    let mut previous = -1;
    while state.space_count > 0 {
        let mut block_score = 0.0;
        let mut minimum = 1 << 28;
        let mut minimum_cell = None;
        let mut minimum_number = -1;
        for block_index in 0..state.block.blocks.len() {
            for n in 1..=size as i32 {
                if state.cand_count_block(block_index, n) != 1 {
                    continue;
                }
                let cell = state.block.blocks[block_index]
                    .iter()
                    .copied()
                    .find(|&cell| state.is_cand(cell, n) && state.cell[cell] == 0);
                let Some(cell) = cell else { continue };
                let mut cost: f64 = state.block.blocks[block_index]
                    .iter()
                    .filter(|&&other| !state.is_cand(other, n))
                    .map(|&other| points.get(other, n) as f64)
                    .sum();
                if n == previous {
                    cost /= 2.0;
                }
                if block_index < size * 2 {
                    block_score += 1.0 / cost;
                    if minimum as f64 > cost {
                        minimum = cost as i32;
                        minimum_cell = Some(cell);
                        minimum_number = n;
                    }
                } else {
                    let weighted = cost * 1.5;
                    block_score += 1.0 / weighted;
                    if minimum as f64 * (size as f64).sqrt() > weighted {
                        minimum = (weighted / (size as f64).sqrt()) as i32;
                        minimum_cell = Some(cell);
                        minimum_number = n;
                    }
                }
            }
        }
        let mut cell_score = 0.0;
        for cell in 0..size * size {
            if state.cell[cell] != 0 || state.cand_count_cell(cell) != 1 {
                continue;
            }
            let number = state.unique_candidate(cell);
            let mut cost: f64 = (1..=size as i32)
                .filter(|&n| !state.is_cand(cell, n))
                .map(|n| points.get(cell, n) as f64)
                .sum();
            if number == previous {
                cost /= 2.0;
            }
            let weighted = cost * 2.0;
            cell_score += 1.0 / weighted;
            if minimum as f64 > weighted {
                minimum = weighted as i32;
                minimum_cell = Some(cell);
                minimum_number = number;
            }
        }
        let score = block_score + cell_score;
        if score > 1e-8 {
            value += state.space_count as f64 / score;
            if let Some(cell) = minimum_cell {
                previous = minimum_number;
                add_number(&mut state, cell, minimum_number, &mut points);
            }
            continue;
        }
        if localization(&mut state, &mut points)
            || (if naked_pair(&mut state, &mut points) {
                hidden_pair(&mut state, &mut points);
                xwing(&mut state, &mut points);
                true
            } else {
                false
            })
            || (if hidden_pair(&mut state, &mut points) {
                xwing(&mut state, &mut points);
                true
            } else {
                false
            })
            || xwing(&mut state, &mut points)
            || (if naked_triple(&mut state, &mut points) {
                hidden_triple(&mut state, &mut points);
                swordfish(&mut state, &mut points);
                true
            } else {
                false
            })
            || (if hidden_triple(&mut state, &mut points) {
                swordfish(&mut state, &mut points);
                true
            } else {
                false
            })
            || swordfish(&mut state, &mut points)
        {
            continue;
        }
        break;
    }
    value
}
