# NPGenerator 2007 — Java 17 reference rewrite

This directory is the Java reference implementation of NPGenerator V2.0.2.
It preserves the original Solver, Evaluator, and Generator branch/loop order,
including the V2.0.2 `NO_ANSWER` behavior. The CLI supports the original
engine's 2–25 board sizes, rectangular and irregular blocks, diagonal
constraints, and `sudoku.rng`-compatible XML.

The original work is Copyright (C) 2007 Time Intermedia Corporation.
Credits: Director Hirofumi Fujiwara, Puzzler Naoki Inaba, Programmer Masaya
Kiwada. This derived rewrite is licensed under GPL-3.0-or-later; see
`LICENSE`.

## Build and run

```sh
./build.sh
./run.sh solve testdata/problem-heart.txt
./run.sh generate testdata/pattern-heart.txt --seed 42
./run.sh random --hints 20 --seed 1
./run.sh random --hints 20 --seed 1 --symmetry mirror-h
./run.sh bench --count 10 --seed 1
./run.sh generate testdata/variant-size6-pattern.txt \
  --size 6 --blocks 3x2 --seed 42
```

`solve`, `generate`, and `random` accept these variant options:

```text
--size N
--blocks WxH|random|@file.txt
--diagonal
--no-vertical
--no-horizontal
--format xml
--out file.xml
```

Sizes are limited to 2–25 and default to 9. If `--blocks` is omitted, the
size must be a perfect square and its square block layout is used. `WxH`
requires `W*H == N`. A block file is an N-row by N-column integer label grid;
every label must identify one of N groups containing N cells. `random` runs
the original `BlockSplit` algorithm and consumes the same `--seed` LCG stream
that generation subsequently uses.

Text problem and pattern files contain N whitespace-separated rows. Problems
use integers 0–N (`-` is also empty); patterns use `X`/`-` or nonzero/zero
integers.

An input name ending in `.xml` is detected automatically. `--format xml`
forces XML input for `solve`/`generate` and emits XML to stdout. `--out`
emits XML to that file instead (and may be used with text input without
`--format`). Generated XML includes the pattern, hidden cells, problem,
answer, block layout, diagonal setting, and integer difficulty in the same
vocabulary consumed by the original `NumberPlaceFile.Load`.

XML constraints default to vertical and horizontal enabled. The original
misspelled `horizonal` attribute is intentionally supported alongside
`vertical`; either may be set to `off`. Every `<group>` in an input XML file
is added to the constraint set. A `<seed>` is accepted as the Generator's
initial numeric seed grid but is not written, matching the original save
path. `<comment>` and an explicitly supplied `<hint>` are preserved when XML
is re-emitted; if solve input has no `<hint>`, one is derived from its filled
problem cells.

For XML input, constraint order follows the original
`Utility.makeBlockConstraint(NumberPlaceFile)` path: vertical, horizontal,
rectangle/groups, then diagonals. Text input and command-line variants retain
the GUI/`ProblemBuilder.build()` order: vertical, horizontal, diagonals, then
rectangle/groups.

All three commands accept these method options:

```text
--use localization,naked-pair,hidden-pair,naked-triple,hidden-triple,x-wing,swordfish
--unique vh,cell,block
```

`none` may be supplied alone to either option to disable every corresponding
flag. Omitting `--use` or `--unique` enables every listed method.

`generate` and `random` additionally accept:

```text
--dp-min N
--dp-max N
--forbidden N
```

Difficulty bounds are inclusive, default to 0 and unlimited, and are swapped
when the lower bound is greater than the upper bound. A negative `--dp-max` is
unlimited. These bounds are generation filters and are rejected by `solve`.
`--forbidden N` (1–N) keeps that digit out of explicit hint cells. Generated
puzzles outside the requested difficulty range are discarded and generation
continues with the same random stream.

Exit codes are 0 for success, 1 for no result/no answer, and 2 for invalid
input. A missing `--seed` uses seed 0. `random` accepts
`--symmetry rot4|rot2|mirror-h|mirror-v|none`. The default `rot4` mode follows
the four-way rotational symmetry used by the original
`sample/Random20.java`, so omitting `--symmetry` preserves the original output
and requires a multiple of four hints. `rot2`, `mirror-h`, and `mirror-v`
require a multiple of two and skip fixed cells on odd-sized boards; `none`
accepts any positive hint count smaller than the number of cells. Modes other
than `rot4` are rewrite-specific extensions and are not features of the
original NPGenerator V2.0.2.

The stable output protocol is:

```text
PROBLEM                 # generate/random only
<N rows, space-separated; 0 is empty>
SOLUTION
<N rows, space-separated>
DIFFICULTY <Double.toString value>
```

`random` prefixes this with `PATTERN` and N rows using `X`/`-`.

## Original-source verification

`./verify.sh` compiles the archived original core+xml source directly with
`javac -encoding SJIS`. Solve and difficulty output use this untouched build.
For seeded generation, the harness makes a second temporary build and changes
only the original `Math.random()` and argument-less `Collections.shuffle()`
call sites so the driver can inject `JavaRandom`'s 48-bit LCG. It compares
complete output files for the legacy cases and for 6x6, diagonal, and random
block variants. It also compares raw `BlockSplit` output and performs XML
round trips in both directions through the original `NumberPlaceFile`.
