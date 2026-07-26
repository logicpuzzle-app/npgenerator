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

use crate::model::{AnswerKind, SolverMethod, Status};

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

pub fn add_number(state: &mut Status, cell: usize, n: i32) -> bool {
    if state.assign_value(cell, n) {
        delete_cand_peer(state, cell, n);
        true
    } else {
        false
    }
}

fn delete_cand_peer(state: &mut Status, cell: usize, n: i32) {
    for x in 1..=state.size as i32 {
        if x != n {
            remove_cand(state, cell, x);
        }
    }
    let membership_count = state.block.where_cell[cell].len();
    for membership_index in 0..membership_count {
        let block = state.block.where_cell[cell][membership_index];
        let mut positions = state.candidate_index_mask(block, n);
        while positions != 0 {
            let cell_index = positions.trailing_zeros() as usize;
            positions &= positions - 1;
            let peer = state.block.blocks[block][cell_index];
            if peer != cell {
                remove_cand(state, peer, n);
            }
        }
    }
}

fn remove_cand(state: &mut Status, cell: usize, n: i32) -> bool {
    if !state.delete_candidate(cell, n) {
        return false;
    }
    if state.unique.cell_unique && state.is_unique_candidate(cell) && state.cell[cell] == 0 {
        add_number(state, cell, state.unique_candidate(cell));
    }
    let membership_count = state.block.where_cell[cell].len();
    for membership_index in 0..membership_count {
        let block = state.block.where_cell[cell][membership_index];
        if !state.unique.vh_unique && block < state.size * 2 {
            continue;
        }
        if !state.unique.block_unique && block >= state.size * 2 {
            continue;
        }
        let positions = state.candidate_index_mask(block, n);
        if positions != 0 && positions & (positions - 1) == 0 {
            let candidate_cell =
                state.block.blocks[block][positions.trailing_zeros() as usize];
            add_number(state, candidate_cell, n);
        }
    }
    true
}

fn localization(state: &mut Status) -> bool {
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
                let mut targets =
                    state.candidate_index_mask(i, n as i32) & !intersection.first_mask;
                while targets != 0 {
                    let cell_index = targets.trailing_zeros() as usize;
                    targets &= targets - 1;
                    let cell = state.block.blocks[i][cell_index];
                    if remove_cand(state, cell, n as i32) {
                        updated = true;
                    }
                }
            } else if c1[n] == 0 && c2[n] > 0 {
                let mut targets =
                    state.candidate_index_mask(j, n as i32) & !intersection.second_mask;
                while targets != 0 {
                    let cell_index = targets.trailing_zeros() as usize;
                    targets &= targets - 1;
                    let cell = state.block.blocks[j][cell_index];
                    if remove_cand(state, cell, n as i32) {
                        updated = true;
                    }
                }
            }
        }
    }
    updated
}

fn naked_pair(state: &mut Status) -> bool {
    let mut updated = false;
    let mut ids = StackList::new(0_usize);
    let mut pairs = StackList::new(0_i32);
    for block in 0..state.block.blocks.len() {
        ids.clear();
        pairs.clear();
        for cell_index in 0..state.block.blocks[block].len() {
            let cell = state.block.blocks[block][cell_index];
            if state.cand_count_cell(cell) == 2 {
                let first_bit = state.cand[cell].trailing_zeros() as i32 + 1;
                let second_bit =
                    (state.cand[cell] & (state.cand[cell] - 1)).trailing_zeros() as i32 + 1;
                ids.push(cell);
                pairs.push(first_bit * (state.size as i32 + 1) + second_bit);
            }
        }
        for j in 0..ids.len() {
            for k in 0..j {
                if pairs[j] == pairs[k] {
                    let p1 = pairs[j] / (state.size as i32 + 1);
                    let p2 = pairs[j] % (state.size as i32 + 1);
                    let cell_count = state.block.blocks[block].len();
                    for cell_index in 0..cell_count {
                        let cell = state.block.blocks[block][cell_index];
                        if cell == ids[j] || cell == ids[k] {
                            continue;
                        }
                        if state.is_cand(cell, p1) {
                            updated |= remove_cand(state, cell, p1);
                        }
                        if state.is_cand(cell, p2) {
                            updated |= remove_cand(state, cell, p2);
                        }
                    }
                }
            }
        }
    }
    updated
}

fn hidden_pair(state: &mut Status) -> bool {
    let mut updated = false;
    let mut ids = StackList::new(0_i32);
    let mut sets = StackList::new(0_u32);
    for block in 0..state.block.blocks.len() {
        ids.clear();
        sets.clear();
        for n in 1..=state.size as i32 {
            let positions = state.candidate_index_mask(block, n);
            if positions.count_ones() == 2 {
                ids.push(n);
                sets.push(positions);
            }
        }
        for i in 0..ids.len() {
            for j in 0..i {
                if sets[i] == sets[j] {
                    let first = sets[i].trailing_zeros() as usize;
                    let second = (sets[i] & (sets[i] - 1)).trailing_zeros() as usize;
                    for &index in &[first, second] {
                        let cell = state.block.blocks[block][index];
                        if state.cand_count_cell(cell) > 2 {
                            for n in 1..=state.size as i32 {
                                if n != ids[i] && n != ids[j] {
                                    updated |= remove_cand(state, cell, n);
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

fn naked_triple(state: &mut Status) -> bool {
    let mut updated = false;
    let mut ids = StackList::new(0_usize);
    let mut sets = StackList::new(0_i32);
    for block in 0..state.block.blocks.len() {
        ids.clear();
        sets.clear();
        for cell_index in 0..state.block.blocks[block].len() {
            let cell = state.block.blocks[block][cell_index];
            if state.cand_count_cell(cell) <= 3 {
                ids.push(cell);
                sets.push(state.cand[cell] << 1);
            }
        }
        for j in 0..ids.len() {
            for k in 0..j {
                for l in 0..k {
                    let union: i32 = sets[j] | sets[k] | sets[l];
                    if union.count_ones() != 3 {
                        continue;
                    }
                    let cell_count = state.block.blocks[block].len();
                    for cell_index in 0..cell_count {
                        let cell = state.block.blocks[block][cell_index];
                        if cell == ids[j] || cell == ids[k] || cell == ids[l] {
                            continue;
                        }
                        let mut candidates = union & (state.cand[cell] << 1);
                        while candidates != 0 {
                            let n = 31 - candidates.leading_zeros() as i32;
                            candidates &= !(1 << n);
                            updated |= remove_cand(state, cell, n);
                        }
                    }
                }
            }
        }
    }
    updated
}

fn hidden_triple(state: &mut Status) -> bool {
    let mut updated = false;
    let mut ids = StackList::new(0_i32);
    let mut sets = StackList::new(0_u32);
    for block in 0..state.block.blocks.len() {
        ids.clear();
        sets.clear();
        for n in 1..=state.size as i32 {
            if state.cand_count_block(block, n) <= 3 {
                ids.push(n);
                sets.push(state.candidate_index_mask(block, n));
            }
        }
        for i in 0..ids.len() {
            for j in 0..i {
                for k in 0..j {
                    let union = sets[i] | sets[j] | sets[k];
                    if union.count_ones() != 3 {
                        continue;
                    }
                    for index in (0..state.size).rev() {
                        if union & (1_u32 << index) == 0 {
                            continue;
                        }
                        let cell = state.block.blocks[block][index];
                        if state.cand_count_cell(cell) > 2 {
                            for n in 1..=state.size as i32 {
                                if n != ids[i] && n != ids[j] && n != ids[k] {
                                    updated |= remove_cand(state, cell, n);
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

fn xwing(state: &mut Status) -> bool {
    let mut updated = false;
    let size = state.size;
    for n in 1..=size as i32 {
        for i in 0..size {
            let positions = state.candidate_index_mask(i, n);
            if positions.count_ones() != 2 {
                continue;
            }
            for j in i + 1..size {
                if state.candidate_index_mask(j, n) != positions {
                    continue;
                }
                let mut rows = positions;
                while rows != 0 {
                    let row = rows.trailing_zeros() as usize;
                    rows &= rows - 1;
                    let target_block = row + size;
                    let excluded = (1_u32 << i) | (1_u32 << j);
                    let mut targets = state.candidate_index_mask(target_block, n) & !excluded;
                    while targets != 0 {
                        let cell_index = targets.trailing_zeros() as usize;
                        targets &= targets - 1;
                        let cell = state.block.blocks[target_block][cell_index];
                        updated |= remove_cand(state, cell, n);
                    }
                }
            }
        }
        for i in size..size * 2 {
            let positions = state.candidate_index_mask(i, n);
            if positions.count_ones() != 2 {
                continue;
            }
            for j in i + 1..size * 2 {
                if state.candidate_index_mask(j, n) != positions {
                    continue;
                }
                let mut columns = positions;
                while columns != 0 {
                    let column = columns.trailing_zeros() as usize;
                    columns &= columns - 1;
                    let excluded =
                        (1_u32 << (i - size)) | (1_u32 << (j - size));
                    let mut targets = state.candidate_index_mask(column, n) & !excluded;
                    while targets != 0 {
                        let cell_index = targets.trailing_zeros() as usize;
                        targets &= targets - 1;
                        let cell = state.block.blocks[column][cell_index];
                        updated |= remove_cand(state, cell, n);
                    }
                }
            }
        }
    }
    updated
}

fn swordfish(state: &mut Status) -> bool {
    let mut updated = false;
    let size = state.size;
    let mut sets = StackList::new(0_u32);
    for n in 1..=size as i32 {
        sets.clear();
        for block in 0..size * 2 {
            if state.cand_count_block(block, n) > 3 {
                sets.push(0_u32);
            } else {
                sets.push(state.candidate_index_mask(block, n));
            }
        }
        for i in 0..size {
            if sets[i] == 0 {
                continue;
            }
            for j in i + 1..size {
                if sets[j] == 0 {
                    continue;
                }
                for k in j + 1..size {
                    if sets[k] == 0 {
                        continue;
                    }
                    let union = sets[i] | sets[j] | sets[k];
                    if union.count_ones() != 3 {
                        continue;
                    }
                    for group_index in (0..size).rev() {
                        if union & (1_u32 << group_index) == 0 {
                            continue;
                        }
                        let group = group_index + size;
                        let excluded =
                            (1_u32 << i) | (1_u32 << j) | (1_u32 << k);
                        let mut targets =
                            state.candidate_index_mask(group, n) & !excluded;
                        while targets != 0 {
                            let cell_index = targets.trailing_zeros() as usize;
                            targets &= targets - 1;
                            let cell = state.block.blocks[group][cell_index];
                            updated |= remove_cand(state, cell, n);
                        }
                    }
                }
            }
        }
        for i in size..size * 2 {
            if sets[i] == 0 {
                continue;
            }
            for j in i + 1..size * 2 {
                if sets[j] == 0 {
                    continue;
                }
                for k in j + 1..size * 2 {
                    if sets[k] == 0 {
                        continue;
                    }
                    let union = sets[i] | sets[j] | sets[k];
                    if union.count_ones() != 3 {
                        continue;
                    }
                    for group in (0..size).rev() {
                        if union & (1_u32 << group) == 0 {
                            continue;
                        }
                        let excluded = (1_u32 << (i - size))
                            | (1_u32 << (j - size))
                            | (1_u32 << (k - size));
                        let mut targets =
                            state.candidate_index_mask(group, n) & !excluded;
                        while targets != 0 {
                            let cell_index = targets.trailing_zeros() as usize;
                            targets &= targets - 1;
                            let cell = state.block.blocks[group][cell_index];
                            updated |= remove_cand(state, cell, n);
                        }
                    }
                }
            }
        }
    }
    updated
}

pub fn answer_in_place(state: &mut Status, method: SolverMethod) {
    state.unique = method.unique;
    for cell in 0..state.cell.len() {
        let n = state.cell[cell];
        if n != 0 {
            if !state.is_cand(cell, n) {
                state.kind = AnswerKind::Irregular;
            }
            delete_cand_peer(state, cell, n);
        }
    }
    if state.is_invalid() {
        return;
    }
    let mut updated = true;
    while updated {
        updated = false;
        if state.space_count == 0 {
            break;
        }
        if method.localization && !updated {
            updated = localization(state);
        }
        if method.naked_pair && !updated {
            updated = naked_pair(state);
        }
        if method.hidden_pair && !updated {
            updated = hidden_pair(state);
        }
        if method.xwing && !updated {
            updated = xwing(state);
        }
        if method.naked_triple && !updated {
            updated = naked_triple(state);
        }
        if method.hidden_triple && !updated {
            updated = hidden_triple(state);
        }
        if method.swordfish && !updated {
            updated = swordfish(state);
        }
        if state.is_no_answer() {
            return;
        }
    }
    state.kind = if state.space_count > 0 {
        AnswerKind::Multiple
    } else {
        AnswerKind::Unique
    };
}

pub fn answer(mut state: Status, method: SolverMethod) -> Status {
    answer_in_place(&mut state, method);
    state
}
