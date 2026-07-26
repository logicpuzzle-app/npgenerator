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

use std::ops::Index;
use std::sync::Arc;

#[derive(Clone)]
pub struct FlatLists {
    values: Box<[usize]>,
    offsets: Box<[usize]>,
}

impl FlatLists {
    #[inline]
    pub fn len(&self) -> usize {
        self.offsets.len() - 1
    }
}

impl Index<usize> for FlatLists {
    type Output = [usize];

    #[inline]
    fn index(&self, index: usize) -> &Self::Output {
        &self.values[self.offsets[index]..self.offsets[index + 1]]
    }
}

#[derive(Clone)]
pub struct BlockConstraint {
    pub blocks: FlatLists,
    pub where_cell: FlatLists,
    where_positions: Box<[u8]>,
    pub intersection_list: Box<[Intersection]>,
}

#[derive(Clone, Copy)]
pub struct Intersection {
    pub first: usize,
    pub second: usize,
    pub first_mask: u32,
    pub second_mask: u32,
}

impl BlockConstraint {
    pub fn rectangle(n: usize, w: usize, h: usize, diagonal: bool) -> Arc<Self> {
        Self::configured(n, true, true, diagonal, false, Some((w, h)), &[])
    }

    pub fn from_labels(n: usize, labels: &[i32], diagonal: bool) -> Arc<Self> {
        Self::configured(
            n,
            true,
            true,
            diagonal,
            false,
            None,
            &[labels.to_vec()],
        )
    }

    pub fn configured(
        n: usize,
        vertical: bool,
        horizontal: bool,
        diagonal: bool,
        diagonal_last: bool,
        rectangle: Option<(usize, usize)>,
        label_groups: &[Vec<i32>],
    ) -> Arc<Self> {
        let mut blocks = Vec::new();
        if vertical {
            Self::add_vertical_blocks(&mut blocks, n);
        }
        if horizontal {
            Self::add_horizontal_blocks(&mut blocks, n);
        }
        if diagonal && !diagonal_last {
            Self::add_diagonal_blocks(&mut blocks, n);
        }
        if let Some((width, height)) = rectangle {
            Self::add_rectangle_blocks(&mut blocks, n, width, height);
        }
        for labels in label_groups {
            Self::add_label_blocks(&mut blocks, n, labels);
        }
        if diagonal && diagonal_last {
            Self::add_diagonal_blocks(&mut blocks, n);
        }
        Arc::new(Self::new(blocks, n))
    }

    fn add_vertical_blocks(blocks: &mut Vec<Vec<usize>>, n: usize) {
        for column in 0..n {
            blocks.push((0..n).map(|row| row * n + column).collect());
        }
    }

    fn add_horizontal_blocks(blocks: &mut Vec<Vec<usize>>, n: usize) {
        for row in 0..n {
            blocks.push((0..n).map(|column| row * n + column).collect());
        }
    }

    fn add_diagonal_blocks(blocks: &mut Vec<Vec<usize>>, n: usize) {
        blocks.push((0..n).map(|index| index * n + index).collect());
        blocks.push((0..n).map(|index| (n - 1 - index) * n + index).collect());
    }

    fn add_rectangle_blocks(
        blocks: &mut Vec<Vec<usize>>,
        n: usize,
        width: usize,
        height: usize,
    ) {
        for column in (0..n).step_by(width) {
            for row in (0..n).step_by(height) {
                let mut block = Vec::with_capacity(n);
                for x in 0..width {
                    for y in 0..height {
                        block.push((row + y) * n + column + x);
                    }
                }
                blocks.push(block);
            }
        }
    }

    fn add_label_blocks(blocks: &mut Vec<Vec<usize>>, n: usize, labels: &[i32]) {
        let mut normalized = std::collections::HashMap::new();
        let mut group_blocks: Vec<Vec<usize>> = Vec::new();
        for (cell, &label) in labels.iter().enumerate() {
            if label == 0 {
                continue;
            }
            let next = normalized.len();
            let group = *normalized.entry(label).or_insert_with(|| {
                group_blocks.push(Vec::with_capacity(n));
                next
            });
            group_blocks[group].push(cell);
        }
        blocks.extend(group_blocks);
    }

    fn new(mut blocks: Vec<Vec<usize>>, n: usize) -> Self {
        for block in &mut blocks {
            block.sort_unstable();
        }
        let mut where_cell = vec![Vec::new(); n * n];
        for (block_index, block) in blocks.iter().enumerate() {
            for (position, &cell) in block.iter().enumerate() {
                where_cell[cell].push((block_index, position as u8));
            }
        }
        let mut intersection_list = Vec::new();
        for i in 0..blocks.len() {
            for j in i + 1..blocks.len() {
                let mut first_mask = 0_u32;
                let mut second_mask = 0_u32;
                for (first_position, cell) in blocks[i].iter().enumerate() {
                    if let Ok(second_position) = blocks[j].binary_search(cell) {
                        first_mask |= 1 << first_position;
                        second_mask |= 1 << second_position;
                    }
                }
                if first_mask.count_ones() >= 2 {
                    intersection_list.push(Intersection {
                        first: i,
                        second: j,
                        first_mask,
                        second_mask,
                    });
                }
            }
        }
        let mut block_offsets = Vec::with_capacity(blocks.len() + 1);
        let mut flat_blocks = Vec::with_capacity(blocks.iter().map(Vec::len).sum());
        block_offsets.push(0);
        for block in blocks {
            flat_blocks.extend(block);
            block_offsets.push(flat_blocks.len());
        }
        let mut where_offsets = Vec::with_capacity(where_cell.len() + 1);
        let mut where_blocks = Vec::new();
        let mut where_positions = Vec::new();
        where_offsets.push(0);
        for memberships in where_cell {
            for (block, position) in memberships {
                where_blocks.push(block);
                where_positions.push(position);
            }
            where_offsets.push(where_blocks.len());
        }
        Self {
            blocks: FlatLists {
                values: flat_blocks.into_boxed_slice(),
                offsets: block_offsets.into_boxed_slice(),
            },
            where_cell: FlatLists {
                values: where_blocks.into_boxed_slice(),
                offsets: where_offsets.into_boxed_slice(),
            },
            where_positions: where_positions.into_boxed_slice(),
            intersection_list: intersection_list.into_boxed_slice(),
        }
    }

    #[inline]
    pub fn block_count(&self) -> usize {
        self.blocks.len()
    }

    #[inline]
    pub fn block(&self, index: usize) -> &[usize] {
        &self.blocks[index]
    }

    #[inline]
    pub fn where_blocks(&self, cell: usize) -> &[usize] {
        &self.where_cell[cell]
    }

    #[inline]
    pub fn where_positions(&self, cell: usize) -> &[u8] {
        let start = self.where_cell.offsets[cell];
        let end = self.where_cell.offsets[cell + 1];
        &self.where_positions[start..end]
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum AnswerKind {
    Unique,
    NoAnswer,
    Multiple,
    Irregular,
    NoJudge,
}

#[derive(Clone, Copy)]
pub struct UniqueMethod {
    pub vh_unique: bool,
    pub cell_unique: bool,
    pub block_unique: bool,
}

impl Default for UniqueMethod {
    fn default() -> Self {
        Self {
            vh_unique: true,
            cell_unique: true,
            block_unique: true,
        }
    }
}

#[derive(Clone, Copy)]
pub struct SolverMethod {
    pub localization: bool,
    pub naked_pair: bool,
    pub hidden_pair: bool,
    pub naked_triple: bool,
    pub hidden_triple: bool,
    pub xwing: bool,
    pub swordfish: bool,
    pub unique: UniqueMethod,
}

impl SolverMethod {
    pub fn none() -> Self {
        Self {
            localization: false,
            naked_pair: false,
            hidden_pair: false,
            naked_triple: false,
            hidden_triple: false,
            xwing: false,
            swordfish: false,
            unique: UniqueMethod::default(),
        }
    }

    pub fn all() -> Self {
        Self {
            localization: true,
            naked_pair: true,
            hidden_pair: true,
            naked_triple: true,
            hidden_triple: true,
            xwing: true,
            swordfish: true,
            unique: UniqueMethod::default(),
        }
    }
}

#[derive(Clone)]
pub struct Status {
    pub size: usize,
    pub cell: Vec<i32>,
    pub cand: Vec<i32>,
    cand_counts: Box<[u8]>,
    pub exist: Vec<i32>,
    cand_positions: Box<[u32]>,
    pub space_count: usize,
    pub cand_count: i32,
    pub block: Arc<BlockConstraint>,
    pub kind: AnswerKind,
    pub unique: UniqueMethod,
}

impl Status {
    pub fn new(size: usize, block: Arc<BlockConstraint>) -> Self {
        let mut value = Self {
            size,
            cell: vec![0; size * size],
            cand: vec![0; size * size],
            cand_counts: vec![0; size * size].into_boxed_slice(),
            exist: vec![0; block.block_count()],
            cand_positions: vec![0; block.block_count() * (size + 1)].into_boxed_slice(),
            space_count: size * size,
            cand_count: 0,
            block,
            kind: AnswerKind::NoJudge,
            unique: UniqueMethod::default(),
        };
        value.clear();
        value
    }

    pub fn clear(&mut self) {
        self.kind = AnswerKind::NoJudge;
        self.cell.fill(0);
        self.exist.fill(0);
        self.space_count = self.cell.len();
        self.cand_count = (self.size * self.size * self.size) as i32;
        self.cand_positions.fill(0);
        for block in 0..self.block.block_count() {
            let full_mask = (1_u32 << self.block.block(block).len()) - 1;
            let start = block * (self.size + 1) + 1;
            self.cand_positions[start..start + self.size].fill(full_mask);
        }
        self.cand.fill((1_i32 << self.size) - 1);
        self.cand_counts.fill(self.size as u8);
    }

    pub fn copy_from_status(&mut self, source: &Self) {
        debug_assert_eq!(self.size, source.size);
        debug_assert_eq!(self.cell.len(), source.cell.len());
        debug_assert_eq!(self.cand_positions.len(), source.cand_positions.len());
        self.cell.copy_from_slice(&source.cell);
        self.cand.copy_from_slice(&source.cand);
        self.cand_counts.copy_from_slice(&source.cand_counts);
        self.exist.copy_from_slice(&source.exist);
        self.cand_positions
            .copy_from_slice(&source.cand_positions);
        self.space_count = source.space_count;
        self.cand_count = source.cand_count;
        self.kind = source.kind;
        self.unique = source.unique;
    }

    #[inline]
    pub fn is_cand(&self, cell: usize, n: i32) -> bool {
        self.cand[cell] & (1 << (n - 1)) != 0
    }

    #[inline]
    pub fn cand_count_cell(&self, cell: usize) -> u32 {
        self.cand_counts[cell] as u32
    }

    #[inline]
    pub fn is_unique_candidate(&self, cell: usize) -> bool {
        self.cand[cell] != 0 && self.cand[cell] & (self.cand[cell] - 1) == 0
    }

    #[inline]
    pub fn unique_candidate(&self, cell: usize) -> i32 {
        self.cand[cell].trailing_zeros() as i32 + 1
    }

    pub fn nth_candidate(&self, cell: usize, mut n: i32) -> i32 {
        let mut bits = self.cand[cell];
        while n > 0 {
            bits &= bits - 1;
            n -= 1;
        }
        if bits == 0 {
            -1
        } else {
            bits.trailing_zeros() as i32 + 1
        }
    }

    #[inline]
    pub fn candidate_index_mask(&self, block: usize, n: i32) -> u32 {
        self.cand_positions[block * (self.size + 1) + n as usize]
    }

    #[inline]
    pub fn cand_count_block(&self, block: usize, n: i32) -> u32 {
        self.candidate_index_mask(block, n).count_ones()
    }

    #[inline]
    pub fn is_no_answer(&self) -> bool {
        matches!(self.kind, AnswerKind::NoAnswer | AnswerKind::Irregular)
    }

    #[inline]
    pub fn is_invalid(&self) -> bool {
        self.is_no_answer()
    }

    pub fn assign_value(&mut self, cell: usize, n: i32) -> bool {
        if n == 0 {
            return false;
        }
        if self.cell[cell] != 0 {
            if n != self.cell[cell] {
                self.kind = AnswerKind::NoAnswer;
            }
            return false;
        }
        if !self.is_cand(cell, n) {
            self.kind = AnswerKind::NoAnswer;
            return false;
        }
        if self.is_no_answer() {
            return false;
        }
        self.cell[cell] = n;
        self.space_count -= 1;
        for &block in self.block.where_blocks(cell) {
            if self.exist[block] & (1 << (n - 1)) != 0 {
                self.kind = AnswerKind::NoAnswer;
            } else {
                self.exist[block] |= 1 << (n - 1);
            }
        }
        true
    }

    pub fn delete_candidate(&mut self, cell: usize, n: i32) -> bool {
        if n == 0 || self.is_no_answer() || !self.is_cand(cell, n) {
            return false;
        }
        if self.cand[cell] == 0 {
            self.kind = AnswerKind::NoAnswer;
            return false;
        }
        self.cand[cell] &= !(1 << (n - 1));
        self.cand_counts[cell] -= 1;
        self.cand_count -= 1;
        let offset = n as usize;
        let membership_count = self.block.where_blocks(cell).len();
        for membership_index in 0..membership_count {
            let block = self.block.where_blocks(cell)[membership_index];
            let position = self.block.where_positions(cell)[membership_index];
            let index = block * (self.size + 1) + offset;
            self.cand_positions[index] &= !(1 << position);
            if self.cand_positions[index] == 0 {
                self.kind = AnswerKind::NoAnswer;
            }
        }
        true
    }
}
