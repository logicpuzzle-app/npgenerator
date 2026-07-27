# NPGenerator 2007 — TypeScript rewrite

This is a strict TypeScript port of the Java 17 reference implementation in
`../java`. It preserves the original solver strategy order, difficulty
evaluation, generator traversal, and `java.util.Random`-compatible 48-bit LCG.

Copyright (C) 2007 Time Intermedia Corporation. Original credits:
Director Hirofumi Fujiwara, Puzzler Naoki Inaba, Programmer Masaya Kiwada.
This derived implementation is licensed under GPL-3.0-or-later.

## Requirements

- Node.js with ES2022 support
- Development dependencies from `package.json`

Install the dependencies when setting up a fresh checkout:

```sh
npm install
```

## Commands

```sh
node --import tsx src/cli.ts solve <problem.txt|problem.xml>
node --import tsx src/cli.ts generate <pattern.txt|problem.xml> [--seed N]
node --import tsx src/cli.ts random [--hints K] [--seed N] \
  [--symmetry rot4|rot2|mirror-h|mirror-v|none]
node --import tsx src/cli.ts bench [--count N] [--seed N]
```

`solve`, `generate`, and `random` accept `--use <list>` and
`--unique <list>`. Technique names are `localization`, `naked-pair`,
`hidden-pair`, `naked-triple`, `hidden-triple`, `x-wing`, and `swordfish`;
uniqueness names are `vh`, `cell`, and `block`. Omitting either list enables
all of its entries. Supplying `none` alone disables all entries in that list.

`generate` and `random` additionally accept `--dp-min N`, `--dp-max N`,
`--forbidden N` (1–size), and `--attempts N`. Difficulty bounds are inclusive and are swapped
when given in reverse order; a negative maximum means unlimited. Difficulty
bounds are generation filters and are rejected by `solve`. Out-of-range
generated puzzles are discarded without resetting the random stream.
`--attempts` defaults to 100 and accepts 0 for unlimited retries. Exit-1
failures write a single `RESULT ...` detail line to stderr.

`random` defaults to the original `Random20.java` four-way rotational
symmetry (`rot4`), and explicitly selecting `rot4` produces the same bytes as
omitting the option. `rot2`, `mirror-h`, and `mirror-v` require an even hint
count and skip their fixed cells on odd-sized boards. `none` accepts any
positive hint count smaller than the board's cell count. These four additional
modes are rewrite-specific extensions; the original NPGenerator V2.0.2 only
provided the `rot4` behavior.

The variant options are `--size N` (2–25), `--blocks WxH`,
`--blocks random`, `--blocks @file.txt`, `--diagonal`, `--no-vertical`, and
`--no-horizontal`. A block grid has `N` rows of `N` integer labels and each
of its `N` blocks must contain `N` cells. Without `--blocks`, a square block
layout is selected when `N` is a perfect square.

Use `--format xml` for the original `sudoku.rng`-compatible XML format.
XML input is also detected from a `.xml` extension. `generate` and `random`
can save XML with `--out file.xml`; omitting `--out` writes XML to standard
output.

XML constraint attributes default to vertical and horizontal enabled. The
original misspelling `horizonal` is intentional; `vertical="off"` and
`horizonal="off"` disable those constraint families. Every input `<group>` is
added to the constraint set. An input `<seed>` initializes the generator's
numeric seed grid but is not written back, matching the original save path.
An input `<comment>` and an explicitly present `<hint>` are preserved when
XML is emitted; if a solved XML input has no `<hint>`, hint cells are derived
from its filled problem cells.

For XML input, constraint order follows the original
`Utility.makeBlockConstraint(NumberPlaceFile)` path: vertical, horizontal,
rectangle/groups, then diagonals. Text input and command-line variants retain
the GUI/`ProblemBuilder.build()` order: vertical, horizontal, diagonals, then
rectangle/groups.

Type-check with `npx tsc --noEmit`. Run `npm run verify` to compare every
shared Java test fixture byte-for-byte.
