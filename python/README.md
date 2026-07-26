# NPGenerator 2007 — Python 3.12 rewrite

This is a standard-library-only Python rewrite of NPGenerator V2.0.2. It
preserves the Java reference implementation's solver strategies, difficulty
evaluation, generator traversal, text protocol, and `java.util.Random`
compatible 48-bit LCG.

```sh
python3 npgen.py solve ../java/testdata/problem-heart.txt
python3 npgen.py generate ../java/testdata/pattern-heart.txt --seed 42
python3 npgen.py random --hints 20 --seed 1
python3 npgen.py random --hints 20 --seed 1 --symmetry mirror-v
python3 npgen.py bench --count 1 --seed 1
python3 -m compileall .
./verify.sh
```

`solve`, `generate`, and `random` support boards from size 2 through 25 with
`--size N`. Blocks can be selected with `--blocks WxH`, generated with
`--blocks random`, or loaded from an `N` by `N` integer grid with
`--blocks @file.txt`. If `--blocks` is omitted, the size must be a perfect
square. `--diagonal` adds both diagonal constraints.
`--no-vertical` and `--no-horizontal` disable the corresponding line
constraints.

Use `--format xml` for the original `sudoku.rng`-compatible XML format, or
pass an input name ending in `.xml` for automatic input detection. Generated
XML can be saved with `--out file.xml`.

XML constraints default to vertical and horizontal enabled. The original
misspelled `horizonal` attribute is supported together with `vertical`;
either may be set to `off`. All input `<group>` elements are constraints.
Input `<seed>` data is supplied to the original Generator initialization
path but is not written back. `<comment>` and an explicit `<hint>` are
preserved on XML output; solve derives a hint from filled problem cells only
when the input has no `<hint>`.

XML input uses the original constraint order: vertical, horizontal,
rectangle/groups, then diagonals. Text and command-line variants keep the
GUI order: vertical, horizontal, diagonals, then rectangle/groups.

`solve`, `generate`, and `random` accept `--use <list>` and
`--unique <list>`. Technique names are `localization`,
`naked-pair`, `hidden-pair`, `naked-triple`, `hidden-triple`, `x-wing`, and
`swordfish`; uniqueness names are `vh`, `cell`, and `block`. Lists are
comma-separated. Omitting these options enables every technique and
uniqueness rule. `none` may be supplied alone to disable all corresponding
flags.

`generate` and `random` also accept `--dp-min N`, `--dp-max N`, and
`--forbidden N` (1 through the board size). A negative `--dp-max` means no
upper limit, and reversed difficulty bounds are swapped. Difficulty bounds
are rejected by `solve`; out-of-range forbidden values are input errors.

`random` accepts `--symmetry rot4|rot2|mirror-h|mirror-v|none`. The default
`rot4` mode exactly preserves the original `Random20.java` four-way
rotational output. `rot2`, `mirror-h`, and `mirror-v` require an even hint
count and skip fixed cells on odd-sized boards; `none` accepts any positive
hint count smaller than the total cell count. Modes other than `rot4` are
rewrite-specific extensions, not features of the original NPGenerator
V2.0.2.

Original work Copyright (C) 2007 Time Intermedia Corporation.
Director: Hirofumi Fujiwara; Puzzler: Naoki Inaba; Programmer: Masaya Kiwada.
This derived rewrite is licensed under GPL-3.0-or-later.
