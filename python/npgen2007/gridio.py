# Copyright (C) 2007 Time Intermedia Corporation <puzzle@timedia.co.jp>
# Derived from NPGenerator V2.0.2. SPDX-License-Identifier: GPL-3.0-or-later
"""Variable-size text grid input and stable output formatting."""

from __future__ import annotations

import re
from pathlib import Path

SIZE = 9
CELL_COUNT = SIZE * SIZE


def _parse_cell(token: str, pattern: bool, size: int) -> int:
    if token in ("-", "0"):
        return 0
    if pattern and token.upper() == "X":
        return 1
    if INTEGER_PATTERN.fullmatch(token) is not None:
        value = int(token, 10)
        if 1 <= value <= size:
            return 1 if pattern else value
    raise ValueError(f"invalid cell: {token}")


INTEGER_PATTERN = re.compile(r"[+-]?[0-9]+")


def read_grid(
    path: str | Path, pattern: bool, size: int = SIZE
) -> list[int]:
    source = Path(path)
    rows: list[list[int]] = []
    for line in source.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            continue
        tokens = stripped.split()
        if len(tokens) != size:
            continue
        try:
            row = [_parse_cell(token, pattern, size) for token in tokens]
        except ValueError:
            continue
        if len(row) == size:
            rows.append(row)
            if len(rows) == size:
                break
    if len(rows) != size:
        raise ValueError(
            f"{source}: expected {size} grid rows, found {len(rows)}"
        )
    return [cell for row in rows for cell in row]


def read_block_array(path: str | Path, size: int) -> list[int]:
    source = Path(path)
    rows: list[list[int]] = []
    for line in source.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            continue
        tokens = stripped.split()
        if len(tokens) != size:
            raise ValueError(
                f"{source}: every block row must contain {size} values"
            )
        row: list[int] = []
        for token in tokens:
            if INTEGER_PATTERN.fullmatch(token) is None:
                raise ValueError(f"{source}: invalid block label: {token}")
            value = int(token, 10)
            if not -(1 << 31) <= value <= (1 << 31) - 1:
                raise ValueError(f"{source}: invalid block label: {token}")
            row.append(value)
        rows.append(row)
    if len(rows) != size:
        raise ValueError(
            f"{source}: expected {size} block rows, found {len(rows)}"
        )
    return [cell for row in rows for cell in row]


def format_grid(
    grid: list[int], pattern: bool = False, size: int = SIZE
) -> str:
    if len(grid) != size * size:
        raise ValueError(
            f"grid must contain exactly {size * size} cells"
        )
    lines: list[str] = []
    for row in range(size):
        values = grid[row * size:(row + 1) * size]
        if pattern:
            lines.append(" ".join("X" if value else "-" for value in values))
        else:
            lines.append(" ".join(str(value) for value in values))
    return "\n".join(lines) + "\n"
