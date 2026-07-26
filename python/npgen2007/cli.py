# Copyright (C) 2007 Time Intermedia Corporation <puzzle@timedia.co.jp>
# Derived from NPGenerator V2.0.2. SPDX-License-Identifier: GPL-3.0-or-later
"""Command-line interface shared with the Java reference implementation."""

from __future__ import annotations

import sys
import time
import re
from pathlib import Path
from collections.abc import Sequence
from dataclasses import dataclass
from typing import cast

from .api import Generated, Symmetry, generate, generate_random, solve
from .gridio import format_grid, read_grid
from .model import KindOfAnswer, SolverMethod
from .random import JavaRandom
from .variant import (
    DEFAULT_SIZE,
    Variant,
    build_variant,
    build_xml_variant,
)
from .xmlio import (
    NumberPlaceFile,
    read_number_place_file,
    write_number_place_file,
)

INT_MAX = (1 << 31) - 1
LONG_MIN = -(1 << 63)
LONG_MAX = (1 << 63) - 1
INTEGER_PATTERN = re.compile(r"[+-]?[0-9]+")
SOLVER_OPTIONS = {"--use", "--unique"}
GENERATOR_OPTIONS = SOLVER_OPTIONS | {"--dp-min", "--dp-max"}
VARIANT_OPTIONS = {"--size", "--blocks", "--seed", "--format", "--out"}
FLAG_OPTIONS = {"--diagonal", "--no-vertical", "--no-horizontal"}
SYMMETRIES = {"rot4", "rot2", "mirror-h", "mirror-v", "none"}


@dataclass(frozen=True)
class CommandOptions:
    method: SolverMethod
    dp_min: int
    dp_max: int
    forbidden: int


def _parse_symmetry(value: str | None) -> Symmetry:
    if value is None:
        return "rot4"
    if value not in SYMMETRIES:
        raise ValueError(
            "--symmetry must be rot4, rot2, mirror-h, mirror-v, or none"
        )
    return cast(Symmetry, value)


def _validate_random_hints(
    size: int, hints: int, symmetry: Symmetry
) -> None:
    orbit_size = 4 if symmetry == "rot4" else 1 if symmetry == "none" else 2
    if symmetry == "none":
        maximum_hints = size * size - 1
    elif symmetry in ("mirror-h", "mirror-v"):
        maximum_hints = size * size - (0 if size % 2 == 0 else size)
    else:
        maximum_hints = size * size - size % 2
    if hints > 0 and hints <= maximum_hints and hints % orbit_size == 0:
        return
    if orbit_size == 1:
        raise ValueError(
            f"--hints must be between 1 and {maximum_hints} "
            f"for --symmetry {symmetry}"
        )
    raise ValueError(
        f"--hints must be a positive multiple of {orbit_size} "
        f"no greater than {maximum_hints} for --symmetry {symmetry}"
    )


def _validate_options(
    args: Sequence[str],
    start: int,
    allowed: set[str],
    flags: set[str] = frozenset(),
) -> None:
    index = start
    while index < len(args):
        option = args[index]
        if option in flags:
            index += 1
            continue
        if index + 1 == len(args):
            raise ValueError(f"incomplete option: {option}")
        if option not in allowed:
            raise ValueError(f"unknown option: {option}")
        index += 2


def _option_value(
    args: Sequence[str], start: int, option: str
) -> str | None:
    value = None
    index = start
    while index < len(args):
        current = args[index]
        if current in FLAG_OPTIONS:
            index += 1
            continue
        if index + 1 >= len(args):
            break
        if current == option:
            value = args[index + 1]
        index += 2
    return value


def _has_option(
    args: Sequence[str], start: int, option: str
) -> bool:
    if option in FLAG_OPTIONS:
        return option in args[start:]
    return _option_value(args, start, option) is not None


def _integer_option(
    args: Sequence[str],
    start: int,
    option: str,
    default: int,
    minimum: int,
    maximum: int,
) -> int:
    text = _option_value(args, start, option)
    if text is None:
        return default
    if INTEGER_PATTERN.fullmatch(text) is None:
        raise ValueError(f"{option} requires an integer")
    value = int(text, 10)
    if value < minimum or value > maximum:
        raise ValueError(f"{option} requires an integer")
    return value


def _long_option(
    args: Sequence[str], start: int, option: str, default: int
) -> int:
    return _integer_option(
        args, start, option, default, LONG_MIN, LONG_MAX
    )


def _int_option(
    args: Sequence[str], start: int, option: str, default: int
) -> int:
    return _integer_option(
        args, start, option, default, -(1 << 31), INT_MAX
    )


def _option_list(value: str, option: str) -> list[str]:
    if not value:
        raise ValueError(f"{option} requires a non-empty list")
    values = value.split(",")
    if any(not item for item in values):
        raise ValueError(f"{option} contains an empty value")
    return values


def _solver_method(args: Sequence[str], start: int) -> SolverMethod:
    method = SolverMethod.all()
    use = _option_value(args, start, "--use")
    if use is not None:
        method.localization = False
        method.naked_pair = False
        method.hidden_pair = False
        method.naked_triple = False
        method.hidden_triple = False
        method.xwing = False
        method.swordfish = False
        names = {
            "localization": "localization",
            "naked-pair": "naked_pair",
            "hidden-pair": "hidden_pair",
            "naked-triple": "naked_triple",
            "hidden-triple": "hidden_triple",
            "x-wing": "xwing",
            "swordfish": "swordfish",
        }
        values = _option_list(use, "--use")
        if "none" in values and len(values) != 1:
            raise ValueError(
                "--use value none cannot be combined with other values"
            )
        for name in values:
            if name == "none":
                continue
            attribute = names.get(name)
            if attribute is None:
                raise ValueError(f"unknown --use value: {name}")
            setattr(method, attribute, True)
    unique = _option_value(args, start, "--unique")
    if unique is not None:
        method.unique.vh_unique = False
        method.unique.cell_unique = False
        method.unique.block_unique = False
        names = {
            "vh": "vh_unique",
            "cell": "cell_unique",
            "block": "block_unique",
        }
        values = _option_list(unique, "--unique")
        if "none" in values and len(values) != 1:
            raise ValueError(
                "--unique value none cannot be combined with other values"
            )
        for name in values:
            if name == "none":
                continue
            attribute = names.get(name)
            if attribute is None:
                raise ValueError(f"unknown --unique value: {name}")
            setattr(method.unique, attribute, True)
    return method


def _command_options(
    args: Sequence[str], start: int, allow_forbidden: bool, size: int = 9
) -> CommandOptions:
    method = _solver_method(args, start)
    lower = max(_int_option(args, start, "--dp-min", 0), 0)
    upper = _int_option(args, start, "--dp-max", INT_MAX)
    if upper < 0:
        upper = INT_MAX
    if lower > upper:
        lower, upper = upper, lower
    forbidden = _int_option(args, start, "--forbidden", -1)
    if (
        allow_forbidden
        and _has_option(args, start, "--forbidden")
        and not 1 <= forbidden <= size
    ):
        raise ValueError(f"--forbidden must be between 1 and {size}")
    return CommandOptions(method, lower, upper, forbidden)


def _float_text(value: float) -> str:
    if value != value:
        return "NaN"
    return repr(value)


def _print_solve(
    solution: list[int], difficulty: float, size: int = DEFAULT_SIZE
) -> None:
    sys.stdout.write("SOLUTION\n")
    sys.stdout.write(format_grid(solution, size=size))
    sys.stdout.write(f"DIFFICULTY {_float_text(difficulty)}\n")


def _print_generated(
    result: Generated, size: int = DEFAULT_SIZE
) -> None:
    sys.stdout.write("PROBLEM\n")
    sys.stdout.write(format_grid(result.problem, size=size))
    _print_solve(result.solution, result.difficulty, size)


def _require_xml_format(args: Sequence[str], start: int) -> None:
    value = _option_value(args, start, "--format")
    if value is not None and value.lower() != "xml":
        raise ValueError("--format only supports xml")


def _xml_output(args: Sequence[str], start: int) -> bool:
    return (
        _has_option(args, start, "--format")
        or _has_option(args, start, "--out")
    )


def _is_xml(path: str) -> bool:
    return Path(path).name.lower().endswith(".xml")


def _resolve_xml_size(
    args: Sequence[str], start: int, source: NumberPlaceFile
) -> int:
    size = source.num_size
    if (
        _has_option(args, start, "--size")
        and _int_option(args, start, "--size", size) != size
    ):
        raise ValueError(
            f"--size does not match XML problem size {size}"
        )
    return size


def _resolve_xml_variant(
    args: Sequence[str],
    start: int,
    source: NumberPlaceFile,
    random: JavaRandom,
) -> Variant:
    size = _resolve_xml_size(args, start, source)
    if _has_option(args, start, "--blocks"):
        return build_variant(
            size,
            _option_value(args, start, "--blocks"),
            source.diagonal or _has_option(args, start, "--diagonal"),
            random,
            source.vertical
            and not _has_option(args, start, "--no-vertical"),
            source.horizontal
            and not _has_option(args, start, "--no-horizontal"),
            True,
        )
    return build_xml_variant(
        source,
        _has_option(args, start, "--diagonal"),
        _has_option(args, start, "--no-vertical"),
        _has_option(args, start, "--no-horizontal"),
    )


def _validate_values(
    values: Sequence[int], size: int, pattern: bool, name: str
) -> None:
    if len(values) != size * size:
        raise ValueError(
            f"{name} must contain exactly {size * size} cells"
        )
    for value in values:
        if pattern:
            if value not in (0, 1):
                raise ValueError("pattern cells must be 0 or 1")
        elif not 0 <= value <= size:
            raise ValueError(
                f"{name} cells must be between 0 and {size}"
            )


def _xml_file(
    variant: Variant,
    hint: Sequence[int],
    hidden: Sequence[int],
    problem: Sequence[int],
    answer: Sequence[int],
    difficulty: float,
    source: NumberPlaceFile | None = None,
) -> NumberPlaceFile:
    return NumberPlaceFile(
        num_size=variant.size,
        hint=tuple(0 if value == 0 else 1 for value in hint),
        hidden=tuple(hidden),
        problem=tuple(problem),
        answer=tuple(answer),
        block_array=variant.block_array,
        group_arrays=(variant.block_array,),
        seed=None,
        comment=None if source is None else source.comment,
        has_hint=True,
        vertical=variant.vertical,
        horizontal=variant.horizontal,
        difficult=int(difficulty),
        diagonal=variant.diagonal,
        default_block=variant.default_block,
    )


def _write_xml(
    args: Sequence[str], start: int, file: NumberPlaceFile
) -> None:
    output = _option_value(args, start, "--out")
    xml = write_number_place_file(output, file)
    if xml is not None:
        sys.stdout.write(xml)


def run(args: Sequence[str]) -> int:
    if not args:
        sys.stderr.write("usage: npgen solve|generate|random|bench ...\n")
        return 2
    command = args[0]
    if command == "solve":
        if len(args) < 2:
            raise ValueError(
                "usage: npgen solve <problem> [--size N] [--blocks spec] "
                "[--diagonal] [--format xml] [--out file.xml]"
            )
        _validate_options(
            args,
            2,
            SOLVER_OPTIONS | VARIANT_OPTIONS,
            FLAG_OPTIONS,
        )
        _require_xml_format(args, 2)
        random = JavaRandom(_long_option(args, 2, "--seed", 0))
        xml_input = (
            _has_option(args, 2, "--format") or _is_xml(args[1])
        )
        source: NumberPlaceFile | None = None
        if xml_input:
            source = read_number_place_file(args[1])
            variant = _resolve_xml_variant(args, 2, source, random)
            problem = list(source.problem)
        else:
            size = _int_option(args, 2, "--size", DEFAULT_SIZE)
            variant = build_variant(
                size,
                _option_value(args, 2, "--blocks"),
                _has_option(args, 2, "--diagonal"),
                random,
                not _has_option(args, 2, "--no-vertical"),
                not _has_option(args, 2, "--no-horizontal"),
            )
            problem = read_grid(args[1], False, size)
        _validate_values(problem, variant.size, False, "problem")
        options = _command_options(args, 2, False, variant.size)
        result = solve(problem, options.method, variant)
        if result.status in (
            KindOfAnswer.NO_ANSWER, KindOfAnswer.IRREGULAR_PROBLEM
        ) or (
            (
                _has_option(args, 2, "--use")
                or _has_option(args, 2, "--unique")
            )
            and result.status is not KindOfAnswer.UNIQUE_ANSWER
        ):
            return 1
        if _xml_output(args, 2):
            hidden = (
                [0] * (variant.size * variant.size)
                if source is None
                else list(source.hidden)
            )
            hint = (
                list(source.hint)
                if source is not None and source.has_hint
                else problem
            )
            _write_xml(
                args,
                2,
                _xml_file(
                    variant,
                    hint,
                    hidden,
                    problem,
                    result.solution,
                    result.difficulty,
                    source,
                ),
            )
        else:
            _print_solve(
                result.solution, result.difficulty, variant.size
            )
        return 0
    if command == "generate":
        if len(args) < 2:
            raise ValueError(
                "usage: npgen generate <pattern> [--seed N] [--size N] "
                "[--blocks spec] [--diagonal] [--format xml] "
                "[--out file.xml]"
            )
        _validate_options(
            args,
            2,
            GENERATOR_OPTIONS | VARIANT_OPTIONS | {"--forbidden"},
            FLAG_OPTIONS,
        )
        _require_xml_format(args, 2)
        seed = _long_option(args, 2, "--seed", 0)
        random = JavaRandom(seed)
        xml_input = (
            _has_option(args, 2, "--format") or _is_xml(args[1])
        )
        if xml_input:
            source = read_number_place_file(args[1])
            variant = _resolve_xml_variant(args, 2, source, random)
            pattern = list(source.hint)
            hidden = list(source.hidden)
        else:
            size = _int_option(args, 2, "--size", DEFAULT_SIZE)
            variant = build_variant(
                size,
                _option_value(args, 2, "--blocks"),
                _has_option(args, 2, "--diagonal"),
                random,
                not _has_option(args, 2, "--no-vertical"),
                not _has_option(args, 2, "--no-horizontal"),
            )
            pattern = read_grid(args[1], True, size)
            hidden = [0] * (size * size)
        _validate_values(pattern, variant.size, True, "pattern")
        _validate_values(hidden, variant.size, False, "hidden")
        options = _command_options(args, 2, True, variant.size)
        result = generate(
            pattern,
            random,
            options.method,
            options.dp_min,
            options.dp_max,
            options.forbidden,
            variant,
            hidden,
            None if not xml_input else (
                None if source.seed is None else list(source.seed)
            ),
        )
        if result is None:
            return 1
        if _xml_output(args, 2):
            _write_xml(
                args,
                2,
                _xml_file(
                    variant,
                    pattern,
                    hidden,
                    result.problem,
                    result.solution,
                    result.difficulty,
                    source if xml_input else None,
                ),
            )
        else:
            _print_generated(result, variant.size)
        return 0
    if command == "random":
        _validate_options(
            args,
            1,
            (
                GENERATOR_OPTIONS
                | VARIANT_OPTIONS
                | {"--hints", "--forbidden", "--symmetry"}
            ),
            FLAG_OPTIONS,
        )
        _require_xml_format(args, 1)
        size = _int_option(args, 1, "--size", DEFAULT_SIZE)
        hints = _int_option(args, 1, "--hints", 20)
        symmetry = _parse_symmetry(_option_value(args, 1, "--symmetry"))
        seed = _long_option(args, 1, "--seed", 0)
        random = JavaRandom(seed)
        variant = build_variant(
            size,
            _option_value(args, 1, "--blocks"),
            _has_option(args, 1, "--diagonal"),
            random,
            not _has_option(args, 1, "--no-vertical"),
            not _has_option(args, 1, "--no-horizontal"),
        )
        options = _command_options(args, 1, True, size)
        _validate_random_hints(size, hints, symmetry)
        result = generate_random(
            hints,
            random,
            options.method,
            options.dp_min,
            options.dp_max,
            options.forbidden,
            variant,
            symmetry,
        )
        if result is None:
            return 1
        pattern, generated = result
        if _xml_output(args, 1):
            _write_xml(
                args,
                1,
                _xml_file(
                    variant,
                    pattern,
                    [0] * (size * size),
                    generated.problem,
                    generated.solution,
                    generated.difficulty,
                ),
            )
        else:
            sys.stdout.write("PATTERN\n")
            sys.stdout.write(format_grid(pattern, True, size))
            _print_generated(generated, size)
        return 0
    if command == "bench":
        _validate_options(args, 1, {"--count", "--seed"})
        count = _long_option(args, 1, "--count", 10)
        seed = _long_option(args, 1, "--seed", 0)
        if count <= 0:
            raise ValueError("--count must be positive")
        random = JavaRandom(seed)
        started = time.perf_counter_ns()
        succeeded = sum(
            generate_random(20, random) is not None for _ in range(count)
        )
        elapsed = (time.perf_counter_ns() - started) // 1_000_000
        sys.stdout.write(
            f"COUNT {count}\nSUCCEEDED {succeeded}\nELAPSED_MS {elapsed}\n"
        )
        return 0 if succeeded == count else 1
    sys.stderr.write("usage: npgen solve|generate|random|bench ...\n")
    return 2


def main() -> None:
    try:
        code = run(sys.argv[1:])
    except (OSError, ValueError) as error:
        sys.stderr.write(f"input error: {error}\n")
        code = 2
    raise SystemExit(code)
