# Copyright (C) 2007 Time Intermedia Corporation <puzzle@timedia.co.jp>
# Derived from NPGenerator V2.0.2. SPDX-License-Identifier: GPL-3.0-or-later
"""Variant constraint construction and the original random block splitter."""

from __future__ import annotations

from dataclasses import dataclass, field
from math import isqrt

from .gridio import read_block_array
from .model import BlockConstraint
from .random import JavaRandom

DEFAULT_SIZE = 9
MIN_SIZE = 2
MAX_SIZE = 25


@dataclass(frozen=True)
class Variant:
    size: int
    block: BlockConstraint
    block_array: tuple[int, ...]
    diagonal: bool
    default_block: bool
    vertical: bool = True
    horizontal: bool = True


@dataclass
class ProblemBuilder:
    """Python counterpart of the original ProblemBuilder assembly order."""

    size: int
    vertical: bool = True
    horizontal: bool = True
    diagonal: bool = False
    default_block: bool = True
    rectangle_width: int = -1
    rectangle_height: int = -1
    groups: list[list[int]] = field(default_factory=list)

    def build(self, diagonal_last: bool = False) -> list[list[int]]:
        blocks: list[list[int]] = []
        if self.vertical:
            _add_vertical(blocks, self.size)
        if self.horizontal:
            _add_horizontal(blocks, self.size)
        if self.diagonal and not diagonal_last:
            _add_diagonal(blocks, self.size)
        if self.default_block:
            square = isqrt(self.size)
            _add_rectangle(blocks, self.size, square, square)
        elif self.rectangle_width > 0 and self.rectangle_height > 0:
            _add_rectangle(
                blocks,
                self.size,
                self.rectangle_width,
                self.rectangle_height,
            )
        for labels in self.groups:
            _add_by_array(blocks, labels, self.size)
        if self.diagonal and diagonal_last:
            _add_diagonal(blocks, self.size)
        return blocks


def _add_vertical(blocks: list[list[int]], size: int) -> None:
    for column in range(size):
        blocks.append([row * size + column for row in range(size)])


def _add_horizontal(blocks: list[list[int]], size: int) -> None:
    for row in range(size):
        blocks.append([row * size + column for column in range(size)])


def _add_diagonal(blocks: list[list[int]], size: int) -> None:
    blocks.append([index * size + index for index in range(size)])
    blocks.append([
        (size - 1 - index) * size + index for index in range(size)
    ])


def _add_rectangle(
    blocks: list[list[int]], size: int, width: int, height: int
) -> None:
    for left in range(0, size, width):
        for top in range(0, size, height):
            blocks.append([
                (top + y) * size + left + x
                for x in range(width)
                for y in range(height)
            ])


def _add_by_array(
    blocks: list[list[int]], labels: list[int], size: int
) -> None:
    label_to_index: dict[int, int] = {}
    for label in labels:
        if label != 0 and label not in label_to_index:
            label_to_index[label] = len(label_to_index)
    groups = [[] for _ in label_to_index]
    for cell, label in enumerate(labels):
        if label != 0:
            groups[label_to_index[label]].append(cell)
    if any(len(group) != size for group in groups):
        raise ValueError(
            f"every block must contain exactly {size} cells"
        )
    blocks.extend(groups)


class _DisjointSet:
    def __init__(self, size: int) -> None:
        self.parent = list(range(size))

    def find(self, value: int) -> int:
        parent = self.parent[value]
        if parent == value:
            return value
        root = self.find(parent)
        self.parent[value] = root
        return root

    def union(self, first: int, second: int) -> None:
        self.parent[self.find(first)] = self.find(second)

    def is_same_group(self, first: int, second: int) -> bool:
        return self.find(first) == self.find(second)


class BlockSplit:
    """Exact port of core/BlockSplit, including its traversal quirk."""

    def __init__(
        self, width: int, height: int, random: JavaRandom
    ) -> None:
        self.width = width
        self.height = height
        self.random = random
        self.vertical_wall: list[list[bool]] = []
        self.horizontal_wall: list[list[bool]] = []

    @staticmethod
    def _index(x: int, y: int, width: int) -> int:
        return y * width + x

    def _generate_maze(self, width: int, height: int) -> None:
        self.vertical_wall = [
            [False] * (height + 1) for _ in range(width + 1)
        ]
        self.horizontal_wall = [
            [False] * (height + 1) for _ in range(width + 1)
        ]
        groups = _DisjointSet(width * height)
        edges: list[tuple[int, int]] = []
        for x in range(width):
            for y in range(height):
                self.vertical_wall[x][y] = True
                self.horizontal_wall[x][y] = True
                cell = self._index(x, y, width)
                if x + 1 < width:
                    edges.append((cell, self._index(x + 1, y, width)))
                if y + 1 < height:
                    edges.append((cell, self._index(x, y + 1, width)))
        self.random.shuffle(edges)
        remaining = width * height - 1
        for first, second in edges:
            if remaining <= 0:
                break
            first_x, first_y = first % width, first // width
            second_x, second_y = second % width, second // width
            if groups.is_same_group(first, second):
                continue
            groups.union(first, second)
            if first_x == second_x:
                self.vertical_wall[first_x][first_y] = False
            elif first_y == second_y:
                self.horizontal_wall[first_x][first_y] = False
            remaining -= 1

    def _is_movable(
        self, px: int, py: int, qx: int, qy: int
    ) -> bool:
        if px == qx:
            if py > qy:
                return self._is_movable(px, qy, qx, py)
            if py + 1 != qy:
                return False
            if py & 1 == 0:
                return True
            return not self.vertical_wall[px // 2][py // 2]
        if py == qy:
            if px > qx:
                return self._is_movable(qx, py, px, qy)
            if px + 1 != qx:
                return False
            if px & 1 == 0:
                return True
            return not self.horizontal_wall[px // 2][py // 2]
        return False

    def _walk_maze(self) -> list[int]:
        dx = (0, 1, 0, -1)
        dy = (1, 0, -1, 0)
        x = self.random.next_int(self.width // 2) * 2
        y = self.random.next_int(self.height // 2) * 2
        direction = 0
        result = [-1] * (self.width * self.height)
        block_id = 0
        count = 0
        yet = True
        while yet:
            yet = False
            result[self._index(x, y, self.width)] = block_id
            count += 1
            if count == self.width:
                block_id += 1
                count = 0
            for index in range(3, 7):
                next_direction = (index + direction) % 4
                next_x = x + dx[next_direction]
                next_y = y + dy[next_direction]
                if (
                    self.width % 2 != 0
                    and self.height % 2 != 0
                    and next_x == self.width - 1
                    and next_y == self.height - 1
                    and count != 0
                    and result[
                        self._index(next_x, next_x, self.width)
                    ] == -1
                ):
                    # The original intentionally uses next_x twice.
                    result[
                        self._index(next_x, next_x, self.width)
                    ] = block_id
                    count += 1
                    if count == self.width:
                        block_id += 1
                        count = 0
                if (
                    0 <= next_x < self.width
                    and 0 <= next_y < self.height
                    and self._is_movable(x, y, next_x, next_y)
                    and result[
                        self._index(next_x, next_y, self.width)
                    ] == -1
                ):
                    yet = True
                    direction = next_direction
                    x, y = next_x, next_y
                    break
        return result

    def split_block(self) -> list[int]:
        width = self.width // 2
        height = self.height // 2
        self._generate_maze(width, height)
        if self.width % 2 != 0:
            for index in range(height):
                self.vertical_wall[width][index] = True
                self.horizontal_wall[width - 1][index] = False
        if self.height % 2 != 0:
            for index in range(width):
                self.vertical_wall[index][height - 1] = False
                self.horizontal_wall[index][height] = True
        return self._walk_maze()


def rectangle_block_array(
    size: int, width: int, height: int
) -> list[int]:
    blocks_across = size // width
    return [
        (row // height) * blocks_across + column // width + 1
        for row in range(size)
        for column in range(size)
    ]


def _normalize_block_array(size: int, labels: list[int]) -> list[int]:
    if len(labels) != size * size:
        raise ValueError(
            f"block grid must contain exactly {size * size} cells"
        )
    normalized_labels: dict[int, int] = {}
    result: list[int] = []
    counts = [0] * size
    for label in labels:
        normalized = normalized_labels.get(label)
        if normalized is None:
            if len(normalized_labels) == size:
                raise ValueError(
                    f"block grid must contain exactly {size} blocks"
                )
            normalized = len(normalized_labels) + 1
            normalized_labels[label] = normalized
        result.append(normalized)
        counts[normalized - 1] += 1
    if len(normalized_labels) != size:
        raise ValueError(
            f"block grid must contain exactly {size} blocks"
        )
    if any(count != size for count in counts):
        raise ValueError(
            f"every block must contain exactly {size} cells"
        )
    return result


def _base_builder(
    size: int, vertical: bool, horizontal: bool, diagonal: bool
) -> ProblemBuilder:
    return ProblemBuilder(
        size=size,
        vertical=vertical,
        horizontal=horizontal,
        diagonal=diagonal,
    )


def _finish_variant(
    builder: ProblemBuilder,
    block_array: list[int],
    default_block: bool,
    xml_order: bool = False,
) -> Variant:
    return Variant(
        size=builder.size,
        block=BlockConstraint(builder.build(xml_order), builder.size),
        block_array=tuple(block_array),
        diagonal=builder.diagonal,
        default_block=default_block,
        vertical=builder.vertical,
        horizontal=builder.horizontal,
    )


def _build_array_variant(
    size: int,
    labels: list[int],
    diagonal: bool,
    vertical: bool = True,
    horizontal: bool = True,
    xml_order: bool = False,
) -> Variant:
    normalized = _normalize_block_array(size, labels)
    builder = _base_builder(size, vertical, horizontal, diagonal)
    builder.default_block = False
    builder.groups.append(normalized)
    return _finish_variant(builder, normalized, False, xml_order)


def build_variant(
    size: int,
    block_spec: str | None,
    diagonal: bool,
    random: JavaRandom,
    vertical: bool = True,
    horizontal: bool = True,
    xml_order: bool = False,
) -> Variant:
    require_size(size)
    if block_spec is None:
        square = isqrt(size)
        if square * square != size:
            raise ValueError(
                "--blocks is required when --size is not a perfect square"
            )
        builder = _base_builder(size, vertical, horizontal, diagonal)
        builder.default_block = True
        return _finish_variant(
            builder,
            rectangle_block_array(size, square, square),
            True,
            xml_order,
        )
    if block_spec == "random":
        return _build_array_variant(
            size,
            BlockSplit(size, size, random).split_block(),
            diagonal,
            vertical,
            horizontal,
            xml_order,
        )
    if block_spec.startswith("@"):
        if len(block_spec) == 1:
            raise ValueError("--blocks @file requires a file name")
        return _build_array_variant(
            size,
            read_block_array(block_spec[1:], size),
            diagonal,
            vertical,
            horizontal,
            xml_order,
        )
    dimensions = block_spec.lower().split("x")
    if len(dimensions) != 2:
        raise ValueError("--blocks must be WxH, random, or @file.txt")
    try:
        width, height = (int(value, 10) for value in dimensions)
    except ValueError as error:
        raise ValueError(
            "--blocks WxH requires integer dimensions"
        ) from error
    if (
        width <= 0
        or height <= 0
        or width * height != size
        or size % width != 0
        or size % height != 0
    ):
        raise ValueError("--blocks WxH requires W*H == size")
    builder = _base_builder(size, vertical, horizontal, diagonal)
    builder.default_block = False
    builder.rectangle_width = width
    builder.rectangle_height = height
    return _finish_variant(
        builder,
        rectangle_block_array(size, width, height),
        False,
        xml_order,
    )


def build_xml_variant(
    source: object,
    force_diagonal: bool,
    no_vertical: bool = False,
    no_horizontal: bool = False,
) -> Variant:
    size = getattr(source, "num_size")
    require_size(size)
    vertical = getattr(source, "vertical") and not no_vertical
    horizontal = getattr(source, "horizontal") and not no_horizontal
    diagonal = getattr(source, "diagonal") or force_diagonal
    builder = _base_builder(size, vertical, horizontal, diagonal)
    group_arrays = getattr(source, "group_arrays")
    if getattr(source, "default_block"):
        square = isqrt(size)
        if square * square != size:
            raise ValueError(
                "XML default-block requires a perfect-square size"
            )
        builder.default_block = True
        block_array = list(
            rectangle_block_array(size, square, square)
        )
    else:
        if not group_arrays:
            raise ValueError(
                "XML custom block constraint is missing <group>"
            )
        builder.default_block = False
        block_array = list(group_arrays[0])
    for labels in group_arrays:
        builder.groups.append(list(labels))
    return _finish_variant(
        builder,
        block_array,
        getattr(source, "default_block"),
        True,
    )


def require_size(size: int) -> None:
    if not MIN_SIZE <= size <= MAX_SIZE:
        raise ValueError("--size must be between 2 and 25")
