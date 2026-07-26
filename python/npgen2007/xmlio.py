# Copyright (C) 2007 Time Intermedia Corporation <puzzle@timedia.co.jp>
# Derived from NPGenerator V2.0.2. SPDX-License-Identifier: GPL-3.0-or-later
"""Original NumberPlaceFile XML vocabulary and stable serialization."""

from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path
from xml.sax.saxutils import escape
from xml.etree import ElementTree

INTEGER_PATTERN = re.compile(r"[+-]?[0-9]+")
INT_MIN = -(1 << 31)
INT_MAX = (1 << 31) - 1


@dataclass(frozen=True)
class NumberPlaceFile:
    num_size: int
    hint: tuple[int, ...]
    hidden: tuple[int, ...]
    answer: tuple[int, ...]
    problem: tuple[int, ...]
    block_array: tuple[int, ...] | None
    diagonal: bool
    default_block: bool
    difficult: int
    group_arrays: tuple[tuple[int, ...], ...] = ()
    seed: tuple[int, ...] | None = None
    comment: str | None = None
    has_hint: bool = True
    vertical: bool = True
    horizontal: bool = True


def _parse_int_array(text: str | None, length: int) -> tuple[int, ...]:
    result = [0] * length
    if text is None or not text.strip():
        return tuple(result)
    for index, token in enumerate(text.split()[:length]):
        if INTEGER_PATTERN.fullmatch(token) is None:
            raise ValueError(f"invalid integer in XML: {token}")
        value = int(token, 10)
        if not INT_MIN <= value <= INT_MAX:
            raise ValueError(f"invalid integer in XML: {token}")
        result[index] = value
    return tuple(result)


def read_number_place_file(path: str | Path) -> NumberPlaceFile:
    source = Path(path)
    try:
        root = ElementTree.parse(source).getroot()
    except ElementTree.ParseError as error:
        raise ValueError(f"cannot parse XML {source}: {error}") from error
    if root.tag != "problem":
        raise ValueError(f"{source}: root element must be <problem>")
    size_text = root.get("size")
    if (
        size_text is None
        or INTEGER_PATTERN.fullmatch(size_text) is None
    ):
        raise ValueError(f"{source}: invalid problem size")
    size = int(size_text, 10)
    if not 2 <= size <= 25:
        raise ValueError(f"{source}: size must be between 2 and 25")
    cells = size * size
    question = root.find(".//question")
    constraint = root.find(".//constraint")
    if constraint is None:
        raise ValueError(f"{source}: missing <constraint>")
    default_block = constraint.get("default-block") == "on"
    groups = constraint.findall(".//group")
    difficult = -1
    if question is not None:
        difficult_text = question.get("difficult")
        if (
            difficult_text is not None
            and INTEGER_PATTERN.fullmatch(difficult_text) is not None
        ):
            parsed = int(difficult_text, 10)
            if INT_MIN <= parsed <= INT_MAX:
                difficult = parsed
    hint_element = root.find(".//hint")
    seed_element = root.find(".//seed")
    comment_element = root.find(".//comment")
    hint = _parse_int_array(
        None if hint_element is None else hint_element.text, cells
    )
    group_arrays = tuple(
        _parse_int_array(group.text, cells) for group in groups
    )
    return NumberPlaceFile(
        num_size=size,
        problem=_parse_int_array(
            None if question is None else question.text, cells
        ),
        hint=tuple(0 if value == 0 else 1 for value in hint),
        hidden=_parse_int_array(
            None if root.find(".//hidden") is None
            else root.find(".//hidden").text,
            cells,
        ),
        answer=_parse_int_array(
            None if root.find(".//answer") is None
            else root.find(".//answer").text,
            cells,
        ),
        block_array=(
            None
            if default_block or not group_arrays
            else group_arrays[0]
        ),
        group_arrays=group_arrays,
        seed=(
            None
            if seed_element is None
            else _parse_int_array(seed_element.text, cells)
        ),
        comment=(
            None
            if comment_element is None
            else "".join(comment_element.itertext())
        ),
        has_hint=hint_element is not None,
        vertical=(
            constraint.get("vertical") is None
            or constraint.get("vertical") == "on"
        ),
        horizontal=(
            constraint.get("horizonal") is None
            or constraint.get("horizonal") == "on"
        ),
        diagonal=constraint.get("diagonal") == "on",
        default_block=default_block,
        difficult=difficult,
    )


def _require_length(
    values: tuple[int, ...] | list[int] | None,
    length: int,
    name: str,
) -> None:
    if values is not None and len(values) != length:
        raise ValueError(f"{name} must contain {length} cells")


def _join(values: tuple[int, ...] | list[int]) -> str:
    return " ".join(str(value) for value in values)


def to_xml_string(file: NumberPlaceFile) -> str:
    if not 2 <= file.num_size <= 25:
        raise ValueError("size must be between 2 and 25")
    cells = file.num_size * file.num_size
    _require_length(file.problem, cells, "problem")
    _require_length(file.answer, cells, "answer")
    _require_length(file.hidden, cells, "hidden")
    _require_length(file.hint, cells, "hint")
    _require_length(file.seed, cells, "seed")
    if not file.default_block:
        _require_length(file.block_array, cells, "block array")
        if file.block_array is None:
            raise ValueError(f"block array must contain {cells} cells")

    xml = '<?xml version="1.0" encoding="UTF-8" ?>\n'
    xml += (
        f'<problem size="{file.num_size}" name="Number Place" '
        'author="Number Place Generator">'
    )
    xml += (
        f'<question difficult="{file.difficult}">'
        f"{_join(file.problem)}</question>"
    )
    xml += (
        '<constraint default-block="'
        f'{"on" if file.default_block else "off"}" diagonal="'
        f'{"on" if file.diagonal else "off"}"'
    )
    if not file.vertical:
        xml += ' vertical="off"'
    if not file.horizontal:
        xml += ' horizonal="off"'
    xml += ">"
    if not file.default_block:
        assert file.block_array is not None
        xml += f'<group block="on">{_join(file.block_array)}</group>'
    xml += "</constraint>"
    xml += f"<answer>{_join(file.answer)}</answer>"
    xml += (
        "<hint>"
        + _join([0 if value == 0 else 1 for value in file.hint])
        + "</hint>"
    )
    xml += f"<hidden>{_join(file.hidden)}</hidden>"
    if file.comment is not None:
        xml += f"<comment>{escape(file.comment)}</comment>"
    xml += "</problem>\n"
    return xml


def write_number_place_file(
    path: str | Path | None, file: NumberPlaceFile
) -> str | None:
    xml = to_xml_string(file)
    if path is None:
        return xml
    Path(path).write_text(xml, encoding="utf-8")
    return None
