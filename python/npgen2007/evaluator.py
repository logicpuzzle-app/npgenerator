# Copyright (C) 2007 Time Intermedia Corporation <puzzle@timedia.co.jp>
# Derived from NPGenerator V2.0.2. SPDX-License-Identifier: GPL-3.0-or-later
"""NPGenerator V2.0.2 difficulty evaluator.

The evaluator tracks the cost of excluding every candidate, then repeatedly
chooses the cheapest forced placement.  Technique identifiers in the original
are diagnostic-only, so this port retains the point propagation and omits the
unused identifier sets.
"""

from __future__ import annotations

from math import sqrt

from .model import BlockConstraint, KindOfAnswer, Status

INFINITY = 1 << 29
BURIED_CELL_POINT = 1
DELETED_SAME_BLOCK = 2
DELETED_SAME_LINE = 3


class CandidateTable:
    def __init__(self, cells: int, numbers: int) -> None:
        self.point = [[INFINITY] * numbers for _ in range(cells)]


def _exclude(
    state: Status,
    point: list[list[int]],
    cell: int,
    number: int,
    value: int,
) -> bool:
    """Delete a candidate and lower its cost without hot-path method calls."""
    bit = 1 << (number - 1)
    cand = state.cand
    if (
        cand[cell] & bit
        and state.kind is not KindOfAnswer.NO_ANSWER
        and state.kind is not KindOfAnswer.IRREGULAR_PROBLEM
    ):
        cand[cell] &= ~bit
        state.cand_counts[cell] -= 1
        state.cand_count -= 1
        stride = state.cand_stride
        for block, position in state.block.placements[cell]:
            index = block * stride + number
            state.cand_positions[index] &= ~(1 << position)
            if state.cand_positions[index] == 0:
                state.kind = KindOfAnswer.NO_ANSWER
    row = point[cell]
    if row[number] > value:
        row[number] = value
        return True
    return False


def _delete_peers(
    state: Status, cell: int, number: int, points: CandidateTable
) -> None:
    point = points.point
    exclude = _exclude
    for other in range(1, state.size + 1):
        if other != number:
            exclude(state, point, cell, other, BURIED_CELL_POINT)
    for block in state.block.where[cell]:
        cost = DELETED_SAME_LINE if block < state.size * 2 else DELETED_SAME_BLOCK
        for peer in state.block.blocks[block]:
            if peer != cell:
                exclude(state, point, peer, number, cost)


def _add(
    state: Status, cell: int, number: int, points: CandidateTable
) -> None:
    if state.assign_value(cell, number):
        _delete_peers(state, cell, number, points)


def localization(state: Status, points: CandidateTable) -> bool:
    updated = False
    size = state.size
    block_data = state.block
    blocks = block_data.blocks
    cand_positions = state.cand_positions
    bit_counts = block_data.bit_counts
    point = points.point
    exclude = _exclude
    stride = state.cand_stride
    for (
        first, second, _common, common_set, first_mask, _second_mask
    ) in block_data.intersection_details:
        for number in range(1, size + 1):
            shared = bit_counts[
                cand_positions[first * stride + number] & first_mask
            ]
            if shared == 0:
                continue
            target = source = -1
            first_count = bit_counts[cand_positions[first * stride + number]]
            second_count = bit_counts[
                cand_positions[second * stride + number]
            ]
            if first_count > shared and second_count == shared:
                target, source = first, second
            elif first_count == shared and second_count > shared:
                target, source = second, first
            if target < 0:
                continue
            value = sum(
                point[cell][number]
                for cell in blocks[source]
                if cell not in common_set
            )
            for cell in blocks[target]:
                if cell in common_set:
                    continue
                updated |= exclude(state, point, cell, number, value)
    return updated


def naked_pair(state: Status, points: CandidateTable) -> bool:
    updated = False
    divisor = state.size + 1
    block_data = state.block
    cand = state.cand
    cand_counts = state.cand_counts
    mask_numbers = block_data.mask_numbers
    point = points.point
    exclude = _exclude
    size = state.size
    for block_cells in block_data.blocks:
        cells: list[int] = []
        pairs: list[int] = []
        for cell in block_cells:
            if cand_counts[cell] == 2:
                values = mask_numbers[cand[cell]]
                cells.append(cell)
                pairs.append(values[0] * divisor + values[1])
        for right in range(len(cells)):
            for left in range(right):
                if pairs[right] != pairs[left]:
                    continue
                first, second = divmod(pairs[right], divisor)
                value = 0
                for number in range(1, size + 1):
                    if number not in (first, second):
                        value += point[cells[right]][number]
                        value += point[cells[left]][number]
                for cell in block_cells:
                    if cell not in (cells[right], cells[left]):
                        updated |= exclude(
                            state, point, cell, first, value
                        )
                        updated |= exclude(
                            state, point, cell, second, value
                        )
    return updated


def hidden_pair(state: Status, points: CandidateTable) -> bool:
    updated = False
    block_data = state.block
    blocks = block_data.blocks
    bit_counts = block_data.bit_counts
    cand_positions = state.cand_positions
    point = points.point
    exclude = _exclude
    size = state.size
    stride = state.cand_stride
    for block, block_cells in enumerate(blocks):
        numbers: list[int] = []
        first: list[int] = []
        second: list[int] = []
        for number in range(1, size + 1):
            positions = cand_positions[block * stride + number]
            if bit_counts[positions] != 2:
                continue
            first_bit = positions & -positions
            second_bit = (positions ^ first_bit) & -(positions ^ first_bit)
            numbers.append(number)
            first.append(block_cells[first_bit.bit_length() - 1])
            second.append(block_cells[second_bit.bit_length() - 1])
        for right in range(len(numbers)):
            for left in range(right):
                if (
                    first[right] != first[left]
                    or second[right] != second[left]
                ):
                    continue
                cell1, cell2 = first[right], second[right]
                value = sum(
                    points.point[cell][numbers[right]]
                    + points.point[cell][numbers[left]]
                    for cell in block_cells
                    if cell not in (cell1, cell2)
                )
                for cell in (cell1, cell2):
                    for number in range(1, size + 1):
                        if number not in (numbers[right], numbers[left]):
                            updated |= exclude(
                                state, point, cell, number, value
                            )
    return updated


def naked_triple(state: Status, points: CandidateTable) -> bool:
    updated = False
    block_data = state.block
    cand = state.cand
    cand_counts = state.cand_counts
    bit_counts = block_data.bit_counts
    mask_numbers_desc = block_data.mask_numbers_desc
    point = points.point
    exclude = _exclude
    size = state.size
    for block_cells in block_data.blocks:
        cells: list[int] = []
        masks: list[int] = []
        for cell in block_cells:
            if cand_counts[cell] <= 3:
                cells.append(cell)
                masks.append(cand[cell])
        for right in range(len(cells)):
            for middle in range(right):
                pair_union = masks[right] | masks[middle]
                if bit_counts[pair_union] > 3:
                    continue
                for left in range(middle):
                    union = pair_union | masks[left]
                    if bit_counts[union] != 3:
                        continue
                    values = mask_numbers_desc[union]
                    value = 0
                    for number in range(1, size + 1):
                        if number not in values:
                            value += point[cells[right]][number]
                            value += point[cells[middle]][number]
                            value += point[cells[left]][number]
                    for cell in block_cells:
                        if cell in (cells[right], cells[middle], cells[left]):
                            continue
                        for number in values:
                            updated |= exclude(
                                state, point, cell, number, value
                            )
    return updated


def hidden_triple(state: Status, points: CandidateTable) -> bool:
    updated = False
    block_data = state.block
    blocks = block_data.blocks
    bit_counts = block_data.bit_counts
    mask_numbers_desc = block_data.mask_numbers_desc
    cand_positions = state.cand_positions
    point = points.point
    exclude = _exclude
    size = state.size
    stride = state.cand_stride
    for block, block_cells in enumerate(blocks):
        numbers: list[int] = []
        masks: list[int] = []
        for number in range(1, size + 1):
            mask = cand_positions[block * stride + number]
            if bit_counts[mask] <= 3:
                numbers.append(number)
                masks.append(mask)
        for right in range(len(numbers)):
            for middle in range(right):
                pair_union = masks[right] | masks[middle]
                if bit_counts[pair_union] > 3:
                    continue
                for left in range(middle):
                    union = pair_union | masks[left]
                    if bit_counts[union] != 3:
                        continue
                    cells = [
                        block_cells[position - 1]
                        for position in mask_numbers_desc[union]
                    ]
                    value = sum(
                        point[cell][numbers[right]]
                        + point[cell][numbers[middle]]
                        + point[cell][numbers[left]]
                        for cell in block_cells if cell not in cells
                    )
                    for cell in cells:
                        for number in range(1, size + 1):
                            if number not in (
                                numbers[right], numbers[middle], numbers[left]
                            ):
                                updated |= exclude(
                                    state, point, cell, number, value
                                )
    return updated


def xwing(state: Status, points: CandidateTable) -> bool:
    updated = False
    size = state.size
    divisor = size * size + 1
    block_data = state.block
    blocks = block_data.blocks
    bit_counts = block_data.bit_counts
    cand_positions = state.cand_positions
    point = points.point
    exclude = _exclude
    stride = state.cand_stride
    for number in range(1, size + 1):
        pairs: list[int] = []
        for block in range(size * 2):
            positions = cand_positions[block * stride + number]
            if bit_counts[positions] != 2:
                pairs.append(-1)
            else:
                first_bit = positions & -positions
                second_bit = (positions ^ first_bit) & -(positions ^ first_bit)
                block_cells = blocks[block]
                first_cell = block_cells[first_bit.bit_length() - 1]
                second_cell = block_cells[second_bit.bit_length() - 1]
                pairs.append(first_cell * divisor + second_cell)
        for horizontal in (False, True):
            start, stop = (size, 2 * size) if horizontal else (0, size)
            for first in range(start, stop):
                if pairs[first] <= 0:
                    continue
                for second in range(first + 1, stop):
                    if pairs[second] <= 0:
                        continue
                    a1, a2 = divmod(pairs[first], divisor)
                    b1, b2 = divmod(pairs[second], divisor)
                    if horizontal:
                        if a1 % size > a2 % size:
                            a1, a2 = a2, a1
                        if b1 % size > b2 % size:
                            b1, b2 = b2, b1
                        same = a1 % size == b1 % size and a2 % size == b2 % size
                        groups = (a1 % size, a2 % size)
                    else:
                        if a1 // size > a2 // size:
                            a1, a2 = a2, a1
                        if b1 // size > b2 // size:
                            b1, b2 = b2, b1
                        same = a1 // size == b1 // size and a2 // size == b2 // size
                        groups = (a1 // size + size, a2 // size + size)
                    if not same:
                        continue
                    value = sum(
                        point[cell][number]
                        for block, exclusions in (
                            (first, (a1, a2)), (second, (b1, b2))
                        )
                        for cell in blocks[block]
                        if cell not in exclusions
                    )
                    for group, one, two in (
                        (groups[0], a1, b1), (groups[1], a2, b2)
                    ):
                        for cell in blocks[group]:
                            if cell not in (one, two):
                                updated |= exclude(
                                    state, point, cell, number, value
                                )
    return updated


def swordfish(state: Status, points: CandidateTable) -> bool:
    updated = False
    size = state.size
    block_data = state.block
    blocks = block_data.blocks
    bit_counts = block_data.bit_counts
    mask_numbers_desc = block_data.mask_numbers_desc
    cand_positions = state.cand_positions
    point = points.point
    exclude = _exclude
    stride = state.cand_stride
    for number in range(1, size + 1):
        masks: list[int] = []
        for block in range(size * 2):
            positions = cand_positions[block * stride + number]
            if bit_counts[positions] > 3:
                masks.append(-1)
            else:
                masks.append(positions)
        for horizontal in (False, True):
            start, stop = (size, 2 * size) if horizontal else (0, size)
            for first in range(start, stop):
                if masks[first] <= 0:
                    continue
                for second in range(first + 1, stop):
                    if masks[second] <= 0:
                        continue
                    pair_union = masks[first] | masks[second]
                    if bit_counts[pair_union] > 3:
                        continue
                    for third in range(second + 1, stop):
                        if masks[third] <= 0:
                            continue
                        union = pair_union | masks[third]
                        if bit_counts[union] != 3:
                            continue
                        positions = [
                            position - 1
                            for position in mask_numbers_desc[union]
                        ]
                        value = 0
                        for source in (first, second, third):
                            for cell in blocks[source]:
                                coordinate = (
                                    cell % size if horizontal else cell // size
                                )
                                if coordinate not in positions:
                                    value += point[cell][number]
                        groups = [
                            position + (0 if horizontal else size)
                            for position in positions
                        ]
                        exclusions = (
                            (first - size, second - size, third - size)
                            if horizontal else (first, second, third)
                        )
                        for group in groups:
                            for cell in blocks[group]:
                                coordinate = (
                                    cell // size if horizontal else cell % size
                                )
                                if coordinate not in exclusions:
                                    updated |= exclude(
                                        state, point, cell, number, value
                                    )
    return updated


def _forced(
    state: Status, points: CandidateTable, previous: int
) -> tuple[float, int, int, int]:
    block_total = 0.0
    minimum = 1 << 28
    min_cell = -1
    min_number = -1
    size = state.size
    block_data = state.block
    cand = state.cand
    cand_counts = state.cand_counts
    cand_positions = state.cand_positions
    point = points.point
    stride = state.cand_stride
    for block, cells in enumerate(block_data.blocks):
        for number in range(1, size + 1):
            positions = cand_positions[block * stride + number]
            if not positions or positions & (positions - 1):
                continue
            position = (positions & -positions).bit_length() - 1
            cell = cells[position]
            if state.cell[cell] != 0:
                continue
            value = float(sum(
                point[other][number]
                for other in cells
                if not cand[other] & (1 << (number - 1))
            ))
            if number == previous:
                value /= 2.0
            if block < size * 2:
                block_total += 1.0 / value
                if minimum > value:
                    minimum = int(value)
                    min_cell, min_number = cell, number
            else:
                value *= 1.5
                block_total += 1.0 / value
                if minimum * sqrt(size) > value:
                    minimum = int(value / sqrt(size))
                    min_cell, min_number = cell, number
    cell_total = 0.0
    for cell in range(size * size):
        if state.cell[cell] != 0 or cand_counts[cell] != 1:
            continue
        number = (cand[cell] & -cand[cell]).bit_length()
        value = float(sum(
            point[cell][other]
            for other in range(1, size + 1)
            if not cand[cell] & (1 << (other - 1))
        ))
        if number == previous:
            value /= 2.0
        value *= 2.0
        cell_total += 1.0 / value
        if minimum > value:
            minimum = int(value)
            min_cell, min_number = cell, number
    # Java evaluates the two method calls independently, then adds their
    # results.  Keeping the accumulators separate preserves that double
    # rounding order and therefore the byte-for-byte DIFFICULTY output.
    return (
        block_total + cell_total,
        minimum,
        min_cell,
        min_number,
    )


def evaluate(size: int, block: BlockConstraint, problem: list[int]) -> float:
    state = Status(size, block)
    points = CandidateTable(size * size, size + 1)
    for cell, number in enumerate(problem):
        if number > 0:
            _add(state, cell, number, points)
    value = 0.0
    previous = -1
    while state.space_count > 0:
        found, _minimum, cell, number = _forced(state, points, previous)
        if found > 1e-8:
            value += state.space_count / found
        if cell >= 0 and number >= 1:
            previous = number
            _add(state, cell, number, points)
        if found < 1e-9:
            if localization(state, points):
                continue
            if naked_pair(state, points):
                hidden_pair(state, points)
                xwing(state, points)
                continue
            if hidden_pair(state, points):
                xwing(state, points)
                continue
            if xwing(state, points):
                continue
            if naked_triple(state, points):
                hidden_triple(state, points)
                swordfish(state, points)
                continue
            if hidden_triple(state, points):
                swordfish(state, points)
                continue
            if swordfish(state, points):
                continue
            break
    return value
