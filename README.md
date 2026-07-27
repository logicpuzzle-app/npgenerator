# NPGenerator 2007 — Four-Language Rewrite

A faithful rewrite of **NPGenerator V2.0.2** — the Number Place (Sudoku) generation engine published in 2007 by PUZZLE GeneRator JaPan (Time Intermedia Corporation; Director: Hirofumi Fujiwara / Puzzler: Naoki Inaba / Programmer: Masaya Kiwada) — in **Java, Rust, Python, and TypeScript**.

The original work is licensed under GPL-3; this derived rewrite is licensed under **GPL-3.0-or-later** (see `LICENSE` in each implementation).

日本語版: [README_JA.md](README_JA.md)

## Highlights

- **Byte-identical outputs across all four languages.** A shared re-implementation of the `java.util.Random` 48-bit LCG means the same seed produces exactly the same puzzle in every language.
- **Verified against the original.** The Java implementation is compared directly with the original 2007 source (recovered from the Wayback Machine, compiled as-is with `javac -encoding SJIS`) across solving, generation, difficulty evaluation, variants, and XML round-trips. The other three languages are then verified byte-for-byte against Java. Each implementation ships a `verify.sh`.
- **Feature-complete engine.** Everything the original GUI exposed at the engine level is available via a common CLI; only the interactive Swing UI and the Applet were left behind.
- **Optimized.** 3–6x faster than the initial faithful port, with output equality preserved (candidate-position bitmasks, in-place state copies, flattened arrays).

## Layout

| Directory | Implementation | Build / run |
|---|---|---|
| `java/` | Java 17 reference implementation (with original-source verification harness) | `./build.sh` → `./run.sh <cmd>` |
| `rust/` | Rust (edition 2021, std only) | `cargo build --release` → `target/release/npgen <cmd>` |
| `python/` | Python 3.12 (stdlib only; runs unmodified on PyPy) | `python3 npgen.py <cmd>` |
| `typescript/` | TypeScript (strict, run via tsx) | `npm install` → `node --import tsx src/cli.ts <cmd>` |
| `bench/` | Cross-language benchmark | `bench/run-bench.sh [count] [seed]` |

## CLI (identical in all four languages)

```
npgen solve <problem.txt|.xml>                 # print solution + difficulty (exit 1 if unsolvable)
npgen generate <pattern.txt|.xml> [--seed N]   # generate a problem from a hint pattern
npgen random [--hints K] [--seed N] [--symmetry rot4|rot2|mirror-h|mirror-v|none]
npgen bench [--count N] [--seed N]

Common options:
  --use <list|none>       solving techniques (localization,naked-pair,hidden-pair,
                          naked-triple,hidden-triple,x-wing,swordfish); default: all
  --unique <list|none>    uniqueness checks (vh,cell,block)
  --dp-min N / --dp-max N difficulty range (out-of-range results are regenerated;
                          rejected for solve = exit 2)
  --forbidden N           forbidden digit (generate/random)
  --attempts N            retry limit (default 100; 0 = unlimited, as in the original GUI)
  --size N                board size 2..25
  --blocks WxH|random|@file  rectangular / random split (BlockSplit) / free-form label grid
  --diagonal              diagonal constraints
  --no-vertical / --no-horizontal  disable row/column constraints
  --format xml / --out f.xml       original-compatible XML (seed, multiple groups,
                                   comment, and hint are preserved)
```

Notes:

- CLI defaults differ from the original GUI in two places: all techniques are ON by default (the GUI default was all OFF — reproduce it with `--use none`), and `--dp-min` defaults to 0 (GUI: 1).
- On failure, `solve` writes `RESULT NO_ANSWER` / `RESULT MULTIPLE_ANSWER` / `RESULT IRREGULAR_PROBLEM` to stderr; generation failure writes `RESULT GENERATE_FAILED attempts=N`.
- `random --symmetry` defaults to `rot4`, which reproduces the original `Random20.java` behavior; the other symmetry modes are extensions of this rewrite.
- Diagonal-block ordering matches the original in both of its variants: XML input follows `Utility.makeBlockConstraint` (diagonal last), while CLI `--diagonal` follows the GUI / `ProblemBuilder.build` (diagonal first).

## Difficulty scale

The engine reports a raw difficulty score ("DP"). The original code defines no thresholds, but the original site provides anchors: a 20-hint sample scored **4,398 pt** and was described as *"upper-intermediate"*, and the community "fiendish" collection ranged from **6 to 11 million pt**. A practical log-scale banding: <1.5k intro, 1.5k–4k easy, 4k–10k medium, 10k–100k hard, 100k–1M expert, ≥1M fiendish.

## Verification

Each implementation has a `verify.sh`:

- `java/verify.sh` compiles the archived original source (core + xml, Shift_JIS) and compares solve results, seeded generation, variants (6x6 with 3x2 blocks, diagonal, random/free-form blocks, raw BlockSplit output), technique/difficulty/forbidden options, and bidirectional XML round-trips. The original source tree is not part of this repository; it was recovered from the Wayback Machine archive of puzzle.gr.jp.
- `rust/`, `python/`, `typescript/` verify byte-equality against the Java reference for every case, plus determinism checks.

## Benchmark (Apple M4 Pro, `bench --count 5 --seed 1`)

| Implementation | Initial port | Optimized | Speedup |
|---|---|---|---|
| Java (openjdk 17) | 15.2 s | **4.1 s** | 3.7x |
| Rust (release) | 26.2 s | **4.6 s** | 5.8x |
| TypeScript (node 22 + tsx) | 25.4 s | **5.6 s** | 4.6x |
| Python (CPython 3.12) | 437.7 s | **151.1 s** | 2.9x |
| Python (PyPy 7.3) | — | **15.9 s** | 27.5x |

All implementations generate the same five puzzles from the same seed (byte-identical output). `ELAPSED_MS` is measured internally, excluding process startup. All optimizations are representation-only changes; search order and RNG consumption are untouched.

## Provenance

The original NPGenerator V2.0.2 was distributed on puzzle.gr.jp (PUZZLE GeneRator JaPan), which is no longer online. The source and binary distributions (V1.0.1 through V2.0.2, with sources) were recovered from the Internet Archive's Wayback Machine, and this rewrite was built and verified against that recovered source. Time Intermedia's later educational re-implementation is available at [timedia/puzzle-generator](https://github.com/timedia/puzzle-generator).
