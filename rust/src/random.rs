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

pub struct JavaRandom {
    state: u64,
}

impl JavaRandom {
    const MULTIPLIER: u64 = 0x5DEECE66D;
    const ADDEND: u64 = 0xB;
    const MASK: u64 = (1_u64 << 48) - 1;

    pub fn new(seed: i64) -> Self {
        Self {
            state: ((seed as u64) ^ Self::MULTIPLIER) & Self::MASK,
        }
    }

    fn next(&mut self, bits: u32) -> i32 {
        self.state = self
            .state
            .wrapping_mul(Self::MULTIPLIER)
            .wrapping_add(Self::ADDEND)
            & Self::MASK;
        (self.state >> (48 - bits)) as i32
    }

    pub fn next_int(&mut self, bound: i32) -> i32 {
        assert!(bound > 0);
        if (bound & -bound) == bound {
            return ((bound as i64 * self.next(31) as i64) >> 31) as i32;
        }
        loop {
            let bits = self.next(31);
            let value = bits % bound;
            if bits.wrapping_sub(value).wrapping_add(bound - 1) >= 0 {
                return value;
            }
        }
    }

    pub fn shuffle<T>(&mut self, values: &mut [T]) {
        for i in (2..=values.len()).rev() {
            let j = self.next_int(i as i32) as usize;
            values.swap(i - 1, j);
        }
    }
}

#[cfg(test)]
mod tests {
    use super::JavaRandom;

    #[test]
    fn java_sequence() {
        let mut random = JavaRandom::new(1);
        assert_eq!(random.next_int(9), 6);
        assert_eq!(random.next_int(9), 1);
        assert_eq!(random.next_int(9), 1);
    }
}

