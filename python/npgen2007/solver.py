# Copyright (C) 2007 Time Intermedia Corporation <puzzle@timedia.co.jp>
# Derived from NPGenerator V2.0.2. SPDX-License-Identifier: GPL-3.0-or-later
"""Strategy solver preserving NPGenerator V2.0.2 traversal order."""

from __future__ import annotations

from .model import KindOfAnswer, SolverMethod, Status


def add_number(state: Status, cell: int, number: int) -> bool:
    if not state.assign_value(cell, number):
        return False
    _delete_cand_peer(state, cell, number)
    return True


def _delete_cand_peer(state: Status, cell: int, number: int) -> None:
    cand = state.cand
    remove = remove_cand
    for other in range(1, state.size + 1):
        if other != number and cand[cell] & (1 << (other - 1)):
            remove(state, cell, other)
    bit = 1 << (number - 1)
    for peer in state.block.peer_list[cell]:
        if cand[peer] & bit:
            remove(state, peer, number)


def remove_cand(state: Status, cell: int, number: int) -> bool:
    # Technique scans intentionally revisit many already-eliminated candidates.
    # This fast path is equivalent to Status.deleteCandidate's first checks and
    # is important for Python generator throughput.
    candidate_bit = 1 << (number - 1)
    cand = state.cand
    if not cand[cell] & candidate_bit:
        return False
    if (
        state.kind is KindOfAnswer.NO_ANSWER
        or state.kind is KindOfAnswer.IRREGULAR_PROBLEM
    ):
        return False
    cand[cell] &= ~candidate_bit
    state.cand_counts[cell] -= 1
    state.cand_count -= 1
    cand_positions = state.cand_positions
    placements = state.block.placements[cell]
    stride = state.cand_stride
    for block, position in placements:
        index = block * stride + number
        cand_positions[index] &= ~(1 << position)
        if cand_positions[index] == 0:
            state.kind = KindOfAnswer.NO_ANSWER
    if (
        state.unique.cell_unique and state.cand_counts[cell] == 1
        and state.cell[cell] == 0
    ):
        add_number(state, cell, (cand[cell] & -cand[cell]).bit_length())
    size2 = state.size * 2
    blocks = state.block.blocks
    for block, _position in placements:
        if not state.unique.vh_unique and block < state.size * 2:
            continue
        if not state.unique.block_unique and block >= size2:
            continue
        positions = cand_positions[block * stride + number]
        if positions and positions & (positions - 1) == 0:
            position = (positions & -positions).bit_length() - 1
            add_number(state, blocks[block][position], number)
    return True


def localization(state: Status) -> bool:
    updated = False
    block_data = state.block
    blocks = block_data.blocks
    cand_positions = state.cand_positions
    bit_counts = block_data.bit_counts
    remove = remove_cand
    size = state.size
    stride = state.cand_stride
    for (
        first, second, _common, _common_set, first_mask, second_mask
    ) in block_data.intersection_details:
        for number in range(1, size + 1):
            shared = bit_counts[
                cand_positions[first * stride + number] & first_mask
            ]
            if shared == 0:
                continue
            first_count = bit_counts[cand_positions[first * stride + number]]
            second_count = bit_counts[
                cand_positions[second * stride + number]
            ]
            target = -1
            common_mask = 0
            if first_count > shared and second_count == shared:
                target = first
                common_mask = first_mask
            elif first_count == shared and second_count > shared:
                target = second
                common_mask = second_mask
            if target >= 0:
                targets = (
                    cand_positions[target * stride + number] & ~common_mask
                )
                while targets:
                    target_bit = targets & -targets
                    position = target_bit.bit_length() - 1
                    updated |= remove(
                        state, blocks[target][position], number
                    )
                    targets ^= target_bit
    return updated


def naked_pair(state: Status) -> bool:
    updated = False
    divisor = state.size + 1
    blocks = state.block.blocks
    cand = state.cand
    cand_counts = state.cand_counts
    remove = remove_cand
    for block_cells in blocks:
        cells: list[int] = []
        pairs: list[int] = []
        for cell in block_cells:
            if cand_counts[cell] == 2:
                bits = cand[cell]
                first_bit = bits & -bits
                second_bit = (bits ^ first_bit) & -(bits ^ first_bit)
                cells.append(cell)
                pairs.append(
                    first_bit.bit_length() * divisor
                    + second_bit.bit_length()
                )
        for right in range(len(cells)):
            for left in range(right):
                if pairs[right] != pairs[left]:
                    continue
                one, two = divmod(pairs[right], divisor)
                one_bit = 1 << (one - 1)
                two_bit = 1 << (two - 1)
                for cell in block_cells:
                    if cell != cells[right] and cell != cells[left]:
                        if cand[cell] & one_bit:
                            updated |= remove(state, cell, one)
                        if cand[cell] & two_bit:
                            updated |= remove(state, cell, two)
    return updated


def hidden_pair(state: Status) -> bool:
    updated = False
    block_data = state.block
    blocks = block_data.blocks
    bit_counts = block_data.bit_counts
    cand_positions = state.cand_positions
    cand_counts = state.cand_counts
    remove = remove_cand
    size = state.size
    stride = state.cand_stride
    for block, block_cells in enumerate(blocks):
        numbers: list[int] = []
        first: list[int] = []
        second: list[int] = []
        for number in range(1, size + 1):
            positions = cand_positions[block * stride + number]
            if bit_counts[positions] == 2:
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
                right_number = numbers[right]
                left_number = numbers[left]
                for cell in (first[right], second[right]):
                    if cand_counts[cell] > 2:
                        for number in range(1, size + 1):
                            if (
                                number != right_number
                                and number != left_number
                                and state.cand[cell] & (1 << (number - 1))
                            ):
                                updated |= remove(state, cell, number)
    return updated


def naked_triple(state: Status) -> bool:
    updated = False
    block_data = state.block
    blocks = block_data.blocks
    cand = state.cand
    cand_counts = state.cand_counts
    bit_counts = block_data.bit_counts
    mask_numbers_desc = block_data.mask_numbers_desc
    remove = remove_cand
    for block_cells in blocks:
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
                    right_cell = cells[right]
                    middle_cell = cells[middle]
                    left_cell = cells[left]
                    has_target = False
                    for cell in block_cells:
                        if (
                            cell != right_cell
                            and cell != middle_cell
                            and cell != left_cell
                            and cand[cell] & union
                        ):
                            has_target = True
                            break
                    if not has_target:
                        continue
                    values = mask_numbers_desc[union]
                    for cell in block_cells:
                        if (
                            cell != right_cell
                            and cell != middle_cell
                            and cell != left_cell
                        ):
                            for number in values:
                                if cand[cell] & (1 << (number - 1)):
                                    updated |= remove(state, cell, number)
    return updated


def hidden_triple(state: Status) -> bool:
    updated = False
    block_data = state.block
    blocks = block_data.blocks
    bit_counts = block_data.bit_counts
    mask_numbers_desc = block_data.mask_numbers_desc
    cand_positions = state.cand_positions
    cand = state.cand
    cand_counts = state.cand_counts
    remove = remove_cand
    size = state.size
    stride = state.cand_stride
    for block, block_cells in enumerate(blocks):
        numbers: list[int] = []
        masks: list[int] = []
        for number in range(1, size + 1):
            value = cand_positions[block * stride + number]
            if bit_counts[value] <= 3:
                numbers.append(number)
                masks.append(value)
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
                    number_mask = (
                        1 << (numbers[right] - 1)
                        | 1 << (numbers[middle] - 1)
                        | 1 << (numbers[left] - 1)
                    )
                    has_target = False
                    for cell in cells:
                        if (
                            cand_counts[cell] > 2
                            and cand[cell] & ~number_mask
                        ):
                            has_target = True
                            break
                    if not has_target:
                        continue
                    right_number = numbers[right]
                    middle_number = numbers[middle]
                    left_number = numbers[left]
                    for cell in cells:
                        if cand_counts[cell] > 2:
                            for number in range(1, size + 1):
                                if (
                                    number != right_number
                                    and number != middle_number
                                    and number != left_number
                                    and cand[cell] & (1 << (number - 1))
                                ):
                                    updated |= remove(state, cell, number)
    return updated


def xwing(state: Status) -> bool:
    updated = False
    size = state.size
    divisor = size * size + 1
    block_data = state.block
    blocks = block_data.blocks
    bit_counts = block_data.bit_counts
    cand_positions = state.cand_positions
    cand = state.cand
    remove = remove_cand
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
            start, stop = (size, size * 2) if horizontal else (0, size)
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
                        same = (
                            a1 % size == b1 % size
                            and a2 % size == b2 % size
                        )
                        groups = (a1 % size, a2 % size)
                    else:
                        if a1 // size > a2 // size:
                            a1, a2 = a2, a1
                        if b1 // size > b2 // size:
                            b1, b2 = b2, b1
                        same = (
                            a1 // size == b1 // size
                            and a2 // size == b2 // size
                        )
                        groups = (a1 // size + size, a2 // size + size)
                    if same:
                        for group, one, two in (
                            (groups[0], a1, b1), (groups[1], a2, b2)
                        ):
                            for cell in blocks[group]:
                                if cell != one and cell != two:
                                    if cand[cell] & (
                                        1 << (number - 1)
                                    ):
                                        updated |= remove(state, cell, number)
    return updated


def swordfish(state: Status) -> bool:
    updated = False
    size = state.size
    block_data = state.block
    blocks = block_data.blocks
    bit_counts = block_data.bit_counts
    mask_numbers_desc = block_data.mask_numbers_desc
    cand_positions = state.cand_positions
    remove = remove_cand
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
            start, stop = (size, size * 2) if horizontal else (0, size)
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
                        exclusions = (
                            first - size, second - size, third - size
                        ) if horizontal else (first, second, third)
                        exclusion_mask = (
                            1 << exclusions[0]
                            | 1 << exclusions[1]
                            | 1 << exclusions[2]
                        )
                        offset = 0 if horizontal else size
                        for position in mask_numbers_desc[union]:
                            group = position - 1 + offset
                            targets = (
                                cand_positions[group * stride + number]
                                & ~exclusion_mask
                            )
                            while targets:
                                target_bit = targets & -targets
                                target_position = (
                                    target_bit.bit_length() - 1
                                )
                                updated |= remove(
                                    state,
                                    blocks[group][target_position],
                                    number,
                                )
                                targets ^= target_bit
    return updated


def answer(state: Status, method: SolverMethod) -> Status:
    state.unique = method.unique
    for cell, number in enumerate(state.cell):
        if number:
            if not state.is_cand(cell, number):
                state.kind = KindOfAnswer.IRREGULAR_PROBLEM
            _delete_cand_peer(state, cell, number)
    if state.is_no_answer():
        return state
    updated = True
    while updated:
        updated = False
        if state.space_count == 0:
            break
        if method.localization and not updated:
            updated = localization(state)
        if method.naked_pair and not updated:
            updated = naked_pair(state)
        if method.hidden_pair and not updated:
            updated = hidden_pair(state)
        if method.xwing and not updated:
            updated = xwing(state)
        if method.naked_triple and not updated:
            updated = naked_triple(state)
        if method.hidden_triple and not updated:
            updated = hidden_triple(state)
        if method.swordfish and not updated:
            updated = swordfish(state)
        if state.is_no_answer():
            return state
    state.kind = (
        KindOfAnswer.MULTIPLE_ANSWER
        if state.space_count else KindOfAnswer.UNIQUE_ANSWER
    )
    return state
