# Copyright (C) 2007 Time Intermedia Corporation <puzzle@timedia.co.jp>
# Derived from NPGenerator V2.0.2. SPDX-License-Identifier: GPL-3.0-or-later
"""Puzzle generator translated from the Java reference implementation."""

from __future__ import annotations

from collections import deque
from math import isqrt

from .model import BlockConstraint, KindOfAnswer, SolverMethod, Status
from .random import JavaRandom
from .solver import add_number, answer


class Generator:
    def __init__(
        self,
        size: int,
        hint: list[int],
        hidden: list[int],
        block: BlockConstraint,
        random: JavaRandom,
        seed: list[int] | None = None,
    ) -> None:
        self.size = size
        self.hint = hint
        self.hidden = hidden
        self.block = block
        self.random = random
        self.hidden_list = [
            index for index, value in enumerate(hidden) if value != 0
        ]
        self.hint_list = [
            index for index, value in enumerate(hint) if value != 0
        ]
        group_count = max(1, isqrt(len(self.hint_list)))
        unit, remainder = divmod(len(self.hint_list), group_count)
        self.groups: list[list[int]] = []
        cursor = 0
        for index in range(group_count):
            count = unit + (1 if index < remainder else 0)
            self.groups.append(self.hint_list[cursor:cursor + count])
            cursor += count
        self.seed_method = SolverMethod()
        self.seed_method.localization = True
        self.seed_method.naked_pair = True
        self.seed_method.hidden_pair = True
        self.method = SolverMethod()
        self.forbidden = -1
        self.seed = None if seed is None else seed.copy()

    def set_method(self, method: SolverMethod) -> None:
        self.method = method

    def set_forbidden(self, forbidden: int) -> None:
        self.forbidden = forbidden

    def _generate_seed_sub(self) -> list[int] | None:
        state = Status(self.size, self.block)
        for cell, number in enumerate(self.hidden):
            if number > 0:
                add_number(state, cell, number)
        state = answer(state, self.seed_method)
        for cell in range(len(self.hidden)):
            if state.cell[cell] != 0:
                continue
            count = state.cand_count_cell(cell)
            if count == 0 or state.is_no_answer():
                return None
            random_index = self.random.next_int(count)
            number = state.nth_candidate(cell, random_index)
            if self.hint[cell] != 0 and number == self.forbidden:
                if count == 1:
                    return None
                number = state.nth_candidate(
                    cell, (random_index + 1) % count
                )
            add_number(state, cell, number)
            state = answer(state, self.seed_method)
        if self.forbidden > 0 and any(
            state.cell[cell] == self.forbidden for cell in self.hint_list
        ):
            return None
        return state.cell if state.space_count == 0 else None

    def _generate_seed(self) -> list[int] | None:
        for _ in range(101):
            seed = self._generate_seed_sub()
            if seed is not None:
                return seed
        return None

    def _fits_hidden(self, cell: list[int]) -> bool:
        return all(cell[index] == self.hidden[index] for index in self.hidden_list)

    def generate(self) -> list[int] | None:
        generated_seed = self._generate_seed()
        if generated_seed is None:
            return None
        self.seed = generated_seed
        problem = [0] * len(self.hidden)
        for cell in self.hint_list:
            problem[cell] = generated_seed[cell]
        state = Status(self.size, self.block)
        state.unique = self.method.unique
        for cell, number in enumerate(problem):
            if number > 0:
                add_number(state, cell, number)
        state = answer(state, self.method)
        if state.kind == KindOfAnswer.UNIQUE_ANSWER:
            return problem
        self.random.shuffle(self.hint_list)
        zero = state.space_count
        if zero == 0:
            return problem
        original = Status(self.size, self.block)
        trial = Status(self.size, self.block)
        yet = True
        while yet:
            yet = False
            for group_index, group in enumerate(self.groups):
                group_state = Status(self.size, self.block)
                group_state.unique = self.method.unique
                for other_index, other_group in enumerate(self.groups):
                    if group_index != other_index:
                        for cell in other_group:
                            add_number(group_state, cell, problem[cell])
                for cell in group:
                    if self.hidden[cell] != 0:
                        continue
                    previous = problem[cell]
                    problem[cell] = 0
                    group_state.clone_into(original)
                    for other in group:
                        if other != cell:
                            add_number(original, other, problem[other])
                    if original.cand_count_cell(cell) <= 1:
                        problem[cell] = previous
                        continue
                    candidates = deque(original.candidate_list(cell))
                    if previous not in candidates:
                        candidates.append(previous)
                    problem[cell] = previous
                    while candidates[0] != previous:
                        candidates.rotate(-1)
                    candidates.popleft()
                    for number in candidates:
                        if number == previous or number == self.forbidden:
                            continue
                        original.clone_into(trial)
                        add_number(trial, cell, number)
                        trial = answer(trial, self.method)
                        space = trial.space_count
                        if (
                            not trial.is_no_answer()
                            and self._fits_hidden(trial.cell)
                            and zero > space
                        ):
                            zero = space
                            problem[cell] = number
                            if zero == 0:
                                return problem
                            yet = True
                            break
                    if yet:
                        break
        return problem if zero == 0 else None
