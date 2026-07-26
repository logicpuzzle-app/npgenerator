# Copyright (C) 2007 Time Intermedia Corporation <puzzle@timedia.co.jp>
# Derived from NPGenerator V2.0.2. SPDX-License-Identifier: GPL-3.0-or-later
"""Core data model used by the solver and generator."""

from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum, auto


class _LazyBitCounts:
    """Indexable bit-count cache without allocating all 2**size masks."""

    def __init__(self) -> None:
        self._values: dict[int, int] = {0: 0}

    def __getitem__(self, mask: int) -> int:
        value = self._values.get(mask)
        if value is None:
            value = mask.bit_count()
            self._values[mask] = value
        return value


class _LazyMaskNumbers:
    """Indexable candidate-list cache used for the larger supported sizes."""

    def __init__(self, size: int, descending: bool = False) -> None:
        self.size = size
        self.descending = descending
        self._values: dict[int, tuple[int, ...]] = {0: ()}

    def __getitem__(self, mask: int) -> tuple[int, ...]:
        values = self._values.get(mask)
        if values is None:
            values = tuple(
                number
                for number in range(1, self.size + 1)
                if mask & (1 << (number - 1))
            )
            if self.descending:
                values = values[::-1]
            self._values[mask] = values
        return values


class KindOfAnswer(Enum):
    UNIQUE_ANSWER = auto()
    NO_ANSWER = auto()
    MULTIPLE_ANSWER = auto()
    IRREGULAR_PROBLEM = auto()
    NO_JUDGE = auto()


@dataclass
class UniqueMethod:
    vh_unique: bool = True
    cell_unique: bool = True
    block_unique: bool = True


@dataclass
class SolverMethod:
    localization: bool = False
    naked_pair: bool = False
    hidden_pair: bool = False
    naked_triple: bool = False
    hidden_triple: bool = False
    xwing: bool = False
    swordfish: bool = False
    unique: UniqueMethod = field(default_factory=UniqueMethod)

    @classmethod
    def all(cls) -> SolverMethod:
        return cls(True, True, True, True, True, True, True)


class BlockConstraint:
    def __init__(self, blocks: list[list[int]], size: int) -> None:
        self.size = size
        self.full_mask = (1 << size) - 1
        if size <= 12:
            self.bit_counts = tuple(
                mask.bit_count() for mask in range(1 << size)
            )
            self.mask_numbers = tuple(
                tuple(
                    number for number in range(1, size + 1)
                    if mask & (1 << (number - 1))
                )
                for mask in range(1 << size)
            )
            self.mask_numbers_desc = tuple(
                values[::-1] for values in self.mask_numbers
            )
        else:
            self.bit_counts = _LazyBitCounts()
            self.mask_numbers = _LazyMaskNumbers(size)
            self.mask_numbers_desc = _LazyMaskNumbers(size, True)
        normalized = [tuple(sorted(block)) for block in blocks]
        self.blocks = tuple(normalized)
        where: list[list[int]] = [[] for _ in range(size * size)]
        placements: list[list[tuple[int, int]]] = [
            [] for _ in range(size * size)
        ]
        for block_index, block in enumerate(self.blocks):
            for position, cell in enumerate(block):
                where[cell].append(block_index)
                placements[cell].append((block_index, position))
        self.where = tuple(tuple(value) for value in where)
        self.placements = tuple(tuple(value) for value in placements)
        self.peer_list = tuple(
            tuple(
                peer
                for block_index in self.where[cell]
                for peer in self.blocks[block_index]
                if peer != cell
            )
            for cell in range(size * size)
        )
        count = len(self.blocks)
        intersections: list[list[tuple[int, ...]]] = [
            [() for _ in range(count)] for _ in range(count)
        ]
        intersection_sets: list[list[frozenset[int]]] = [
            [frozenset() for _ in range(count)] for _ in range(count)
        ]
        intersection_list: list[tuple[int, int]] = []
        intersection_details: list[
            tuple[
                int, int, tuple[int, ...], frozenset[int], int, int
            ]
        ] = []
        for first in range(count):
            for second in range(first + 1, count):
                other = set(self.blocks[second])
                common = tuple(
                    cell for cell in self.blocks[first] if cell in other
                )
                intersections[first][second] = common
                intersection_sets[first][second] = frozenset(common)
                if len(common) >= 2:
                    intersection_list.append((first, second))
                    common_set = intersection_sets[first][second]
                    first_mask = sum(
                        1 << position
                        for position, cell in enumerate(self.blocks[first])
                        if cell in common_set
                    )
                    second_mask = sum(
                        1 << position
                        for position, cell in enumerate(self.blocks[second])
                        if cell in common_set
                    )
                    intersection_details.append(
                        (
                            first, second, common, common_set,
                            first_mask, second_mask,
                        )
                    )
        self.intersections = tuple(
            tuple(row) for row in intersections
        )
        self.intersection_sets = tuple(
            tuple(row) for row in intersection_sets
        )
        self.intersection_list = tuple(intersection_list)
        self.intersection_details = tuple(intersection_details)

    def intersection(self, first: int, second: int) -> tuple[int, ...]:
        if first > second:
            first, second = second, first
        return self.intersections[first][second]

    def intersection_set(
        self, first: int, second: int
    ) -> frozenset[int]:
        if first > second:
            first, second = second, first
        return self.intersection_sets[first][second]


def make_normal_block(size: int, width: int, height: int) -> BlockConstraint:
    blocks: list[list[int]] = []
    for column in range(size):
        blocks.append([row * size + column for row in range(size)])
    for row in range(size):
        blocks.append([row * size + column for column in range(size)])
    for left in range(0, size, width):
        for top in range(0, size, height):
            blocks.append([
                (top + y) * size + left + x
                for x in range(width)
                for y in range(height)
            ])
    return BlockConstraint(blocks, size)


class Status:
    def __init__(self, size: int, block: BlockConstraint) -> None:
        self.size = size
        self.block = block
        self.cell = [0] * (size * size)
        self.cand = [block.full_mask] * (size * size)
        self.cand_counts = [size] * (size * size)
        self.exist = [0] * len(block.blocks)
        self.cand_stride = size + 1
        self.cand_positions = (
            [0] + [block.full_mask] * size
        ) * len(block.blocks)
        self.space_count = size * size
        self.cand_count = size ** 3
        self.kind = KindOfAnswer.NO_JUDGE
        self.unique = UniqueMethod()

    def clone_into(self, result: Status) -> Status:
        result.cell[:] = self.cell
        result.cand[:] = self.cand
        result.cand_counts[:] = self.cand_counts
        result.exist[:] = self.exist
        result.cand_positions[:] = self.cand_positions
        result.space_count = self.space_count
        result.cand_count = self.cand_count
        result.kind = self.kind
        result.unique = self.unique
        return result

    def is_cand(self, cell: int, number: int) -> bool:
        return bool(self.cand[cell] & (1 << (number - 1)))

    def cand_count_cell(self, cell: int) -> int:
        return self.cand_counts[cell]

    def is_unique_candidate(self, cell: int) -> bool:
        value = self.cand[cell]
        return value != 0 and value & (value - 1) == 0

    def unique_candidate(self, cell: int) -> int:
        return (self.cand[cell] & -self.cand[cell]).bit_length()

    def nth_candidate(self, cell: int, index: int) -> int:
        values = self.block.mask_numbers[self.cand[cell]]
        return -1 if index >= len(values) else values[index]

    def candidate_list(self, cell: int) -> list[int]:
        return list(self.block.mask_numbers[self.cand[cell]])

    def cells_with_candidate(
        self, block: int, number: int
    ) -> list[int]:
        cells = self.block.blocks[block]
        return [
            cells[index - 1]
            for index in self.block.mask_numbers[
                self.cand_positions[block * self.cand_stride + number]
            ]
        ]

    def indices_with_candidate(self, block: int, number: int) -> list[int]:
        bits = self.cand_positions[block * self.cand_stride + number]
        return [number - 1 for number in self.block.mask_numbers[bits]]

    def is_no_answer(self) -> bool:
        return (
            self.kind is KindOfAnswer.NO_ANSWER
            or self.kind is KindOfAnswer.IRREGULAR_PROBLEM
        )

    def assign_value(self, cell: int, number: int) -> bool:
        if number == 0:
            return False
        if self.cell[cell] != 0:
            if number != self.cell[cell]:
                self.kind = KindOfAnswer.NO_ANSWER
            return False
        if not self.is_cand(cell, number):
            self.kind = KindOfAnswer.NO_ANSWER
            return False
        if self.is_no_answer():
            return False
        self.cell[cell] = number
        self.space_count -= 1
        bit = 1 << (number - 1)
        for block in self.block.where[cell]:
            if self.exist[block] & bit:
                self.kind = KindOfAnswer.NO_ANSWER
            else:
                self.exist[block] |= bit
        return True

    def delete_candidate(self, cell: int, number: int) -> bool:
        if number == 0:
            return False
        if (
            self.kind is KindOfAnswer.NO_ANSWER
            or self.kind is KindOfAnswer.IRREGULAR_PROBLEM
            or not self.is_cand(cell, number)
        ):
            return False
        if self.cand[cell] == 0:
            self.kind = KindOfAnswer.NO_ANSWER
            return False
        self.cand[cell] &= ~(1 << (number - 1))
        self.cand_counts[cell] -= 1
        self.cand_count -= 1
        for block, position in self.block.placements[cell]:
            index = block * self.cand_stride + number
            self.cand_positions[index] &= ~(1 << position)
            if self.cand_positions[index] == 0:
                self.kind = KindOfAnswer.NO_ANSWER
        return True
