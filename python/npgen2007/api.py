# Copyright (C) 2007 Time Intermedia Corporation <puzzle@timedia.co.jp>
# Derived from NPGenerator V2.0.2. SPDX-License-Identifier: GPL-3.0-or-later
"""Public solve and generation API."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Literal

from .evaluator import evaluate
from .generator import Generator
from .model import (
    BlockConstraint, KindOfAnswer, SolverMethod, Status, make_normal_block,
)
from .random import JavaRandom
from .solver import add_number, answer
from .variant import Variant, rectangle_block_array

SIZE = 9
Symmetry = Literal["rot4", "rot2", "mirror-h", "mirror-v", "none"]
STANDARD_BLOCK: BlockConstraint = make_normal_block(SIZE, 3, 3)
STANDARD_VARIANT = Variant(
    SIZE,
    STANDARD_BLOCK,
    tuple(rectangle_block_array(SIZE, 3, 3)),
    False,
    True,
)


@dataclass(frozen=True)
class SolveResult:
    solution: list[int]
    difficulty: float
    status: KindOfAnswer


@dataclass(frozen=True)
class Generated:
    problem: list[int]
    solution: list[int]
    difficulty: float


def solve(
    problem: list[int],
    method: SolverMethod | None = None,
    variant: Variant = STANDARD_VARIANT,
) -> SolveResult:
    if method is None:
        method = SolverMethod.all()
    state = Status(variant.size, variant.block)
    state.unique = method.unique
    for cell, number in enumerate(problem):
        if number > 0:
            add_number(state, cell, number)
    state = answer(state, method)
    if state.is_no_answer():
        return SolveResult(state.cell.copy(), float("nan"), state.kind)
    difficulty = evaluate(variant.size, variant.block, problem)
    return SolveResult(state.cell.copy(), difficulty, state.kind)


def generate(
    pattern: list[int],
    random: JavaRandom,
    method: SolverMethod | None = None,
    dp_min: int = 0,
    dp_max: int = (1 << 31) - 1,
    forbidden: int = -1,
    variant: Variant = STANDARD_VARIANT,
    hidden: list[int] | None = None,
    initial_seed: list[int] | None = None,
) -> Generated | None:
    if method is None:
        method = SolverMethod.all()
    if hidden is None:
        hidden = [0] * (variant.size * variant.size)
    generator = Generator(
        variant.size,
        pattern.copy(),
        hidden.copy(),
        variant.block,
        random,
        initial_seed,
    )
    generator.set_method(method)
    generator.set_forbidden(forbidden)
    for _ in range(100):
        problem = generator.generate()
        if problem is not None:
            result = solve(problem, method, variant)
            if result.difficulty < dp_min or dp_max < result.difficulty:
                continue
            return Generated(
                problem.copy(), result.solution.copy(), result.difficulty
            )
    return None


def random_pattern(
    hints: int,
    random: JavaRandom,
    size: int = SIZE,
    symmetry: Symmetry = "rot4",
) -> list[int]:
    pattern = [0] * (size * size)
    count = 0
    while count < hints:
        x = random.next_int(size)
        y = random.next_int(size)
        middle = size // 2
        if size % 2 != 0 and (
            (
                symmetry in ("rot4", "rot2")
                and x == middle
                and y == middle
            )
            or (symmetry == "mirror-h" and x == middle)
            or (symmetry == "mirror-v" and y == middle)
        ):
            continue
        if pattern[y * size + x] != 0:
            continue
        pattern[y * size + x] = 1
        if symmetry == "rot4":
            pattern[(size - 1 - x) * size + y] = 1
            pattern[(size - 1 - y) * size + size - 1 - x] = 1
            pattern[x * size + size - 1 - y] = 1
            count += 4
        elif symmetry == "rot2":
            pattern[(size - 1 - y) * size + size - 1 - x] = 1
            count += 2
        elif symmetry == "mirror-h":
            pattern[y * size + size - 1 - x] = 1
            count += 2
        elif symmetry == "mirror-v":
            pattern[(size - 1 - y) * size + x] = 1
            count += 2
        elif symmetry == "none":
            count += 1
        else:
            raise ValueError(f"unknown symmetry: {symmetry}")
    return pattern


def generate_random(
    hints: int,
    random: JavaRandom,
    method: SolverMethod | None = None,
    dp_min: int = 0,
    dp_max: int = (1 << 31) - 1,
    forbidden: int = -1,
    variant: Variant = STANDARD_VARIANT,
    symmetry: Symmetry = "rot4",
) -> tuple[list[int], Generated] | None:
    if method is None:
        method = SolverMethod.all()
    for _ in range(100):
        pattern = random_pattern(hints, random, variant.size, symmetry)
        generated = generate(
            pattern,
            random,
            method,
            dp_min,
            dp_max,
            forbidden,
            variant,
        )
        if generated is not None:
            return pattern, generated
    return None
