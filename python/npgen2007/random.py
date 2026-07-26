# Copyright (C) 2007 Time Intermedia Corporation <puzzle@timedia.co.jp>
# Derived from NPGenerator V2.0.2. SPDX-License-Identifier: GPL-3.0-or-later
"""java.util.Random compatible 48-bit LCG."""

from __future__ import annotations

from typing import MutableSequence, TypeVar

T = TypeVar("T")


class JavaRandom:
    MULTIPLIER = 0x5DEECE66D
    ADDEND = 0xB
    MASK = (1 << 48) - 1

    def __init__(self, seed: int) -> None:
        self.set_seed(seed)

    def set_seed(self, seed: int) -> None:
        self._state = (seed ^ self.MULTIPLIER) & self.MASK

    def next(self, bits: int) -> int:
        if not 0 <= bits <= 32:
            raise ValueError("bits must be between 0 and 32")
        self._state = (
            self._state * self.MULTIPLIER + self.ADDEND
        ) & self.MASK
        return self._state >> (48 - bits)

    def next_int(self, bound: int) -> int:
        if bound <= 0:
            raise ValueError("bound must be positive")
        if bound & -bound == bound:
            return (bound * self.next(31)) >> 31
        while True:
            bits = self.next(31)
            value = bits % bound
            test = (bits - value + bound - 1) & 0xFFFFFFFF
            if test < 0x80000000:
                return value

    def shuffle(self, values: MutableSequence[T]) -> None:
        for size in range(len(values), 1, -1):
            index = self.next_int(size)
            values[size - 1], values[index] = values[index], values[size - 1]
