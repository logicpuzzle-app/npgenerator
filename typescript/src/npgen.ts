/*
 * Copyright (C) 2007 Time Intermedia Corporation <puzzle@timedia.co.jp>
 * TypeScript rewrite derived from NPGenerator V2.0.2.
 * Director: Hirofumi Fujiwara / Puzzler: Naoki Inaba / Programmer: Masaya Kiwada
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import {
  BlockConstraint,
  KindOfAnswer,
  makeNormalBlock,
  SolverMethod,
  Status,
} from "./core.js";
import { evaluate } from "./evaluator.js";
import { Generator } from "./generator.js";
import { SIZE } from "./grid.js";
import { JavaRandom } from "./random.js";
import { addNumber, answer } from "./solver.js";
import type { Variant } from "./variant.js";

export interface SolveResult {
  solution: number[];
  difficulty: number;
  status: KindOfAnswer;
}

export interface Generated {
  problem: number[];
  solution: number[];
  difficulty: number;
}

export interface RandomGenerated {
  pattern: number[];
  generated: Generated;
}

export type Symmetry = "rot4" | "rot2" | "mirror-h" | "mirror-v" | "none";

export interface NpGenOptions {
  method: SolverMethod;
  dpMin: number;
  dpMax: number;
  forbidden: number;
  attempts?: number;
}

export const STANDARD_BLOCK = new BlockConstraint(
  makeNormalBlock(SIZE, 3, 3),
  SIZE,
);
export const STANDARD_VARIANT: Variant = {
  size: SIZE,
  block: STANDARD_BLOCK,
  blockArray: Array.from(
    { length: SIZE * SIZE },
    (_, cell) => Math.trunc(cell / 27) * 3 + Math.trunc((cell % SIZE) / 3) + 1,
  ),
  vertical: true,
  horizontal: true,
  diagonal: false,
  defaultBlock: true,
};

export function allMethods(): SolverMethod {
  const method = new SolverMethod();
  method.setAllUse();
  return method;
}

export function defaultOptions(): NpGenOptions {
  return {
    method: allMethods(),
    dpMin: 0,
    dpMax: 2147483647,
    forbidden: -1,
    attempts: 100,
  };
}

export function solve(
  problem: number[],
  method: SolverMethod = allMethods(),
  variant: Variant = STANDARD_VARIANT,
): SolveResult {
  const status = new Status(variant.size, variant.block);
  status.setUniqueMethod(method.unique);
  for (let cell = 0; cell < problem.length; cell++) {
    if (problem[cell]! > 0) addNumber(status, cell, problem[cell]!);
  }
  answer(status, method);
  if (status.isNoAnswer()) {
    return {
      solution: [...status.cells()],
      difficulty: Number.NaN,
      status: status.getKindOfAnswer(),
    };
  }
  return {
    solution: [...status.cells()],
    difficulty: evaluate(variant.size, variant.block, problem),
    status: status.getKindOfAnswer(),
  };
}

export function generate(
  pattern: number[],
  random: JavaRandom,
  options: NpGenOptions = defaultOptions(),
  variant: Variant = STANDARD_VARIANT,
  hidden: number[] = Array(variant.size * variant.size).fill(0) as number[],
  initialSeed?: number[],
): Generated | undefined {
  const generator = new Generator(
    variant.size,
    [...pattern],
    [...hidden],
    variant.block,
    random,
    initialSeed,
  );
  generator.setMethod(options.method);
  generator.setForbidden(options.forbidden);
  const attempts = options.attempts ?? 100;
  for (
    let attempt = 0;
    attempts === 0 || attempt < attempts;
    attempt++
  ) {
    const problem = generator.generate();
    if (problem !== undefined) {
      const solved = solve(problem, options.method, variant);
      if (solved.difficulty < options.dpMin || options.dpMax < solved.difficulty) {
        continue;
      }
      return {
        problem: [...problem],
        solution: solved.solution,
        difficulty: solved.difficulty,
      };
    }
  }
  return undefined;
}

export function randomPattern(
  size: number,
  hints: number,
  random: JavaRandom,
  symmetry: Symmetry = "rot4",
): number[] {
  const pattern = Array(size * size).fill(0) as number[];
  for (let count = 0; count < hints;) {
    const x = random.nextInt(size);
    const y = random.nextInt(size);
    const middle = Math.trunc(size / 2);
    if (
      size % 2 !== 0 &&
      (
        ((symmetry === "rot4" || symmetry === "rot2") &&
          x === middle && y === middle) ||
        (symmetry === "mirror-h" && x === middle) ||
        (symmetry === "mirror-v" && y === middle)
      )
    ) continue;
    if (pattern[y * size + x] !== 0) continue;
    pattern[y * size + x] = 1;
    switch (symmetry) {
      case "rot4":
        pattern[(size - 1 - x) * size + y] = 1;
        pattern[(size - 1 - y) * size + (size - 1 - x)] = 1;
        pattern[x * size + (size - 1 - y)] = 1;
        count += 4;
        break;
      case "rot2":
        pattern[(size - 1 - y) * size + (size - 1 - x)] = 1;
        count += 2;
        break;
      case "mirror-h":
        pattern[y * size + (size - 1 - x)] = 1;
        count += 2;
        break;
      case "mirror-v":
        pattern[(size - 1 - y) * size + x] = 1;
        count += 2;
        break;
      case "none":
        count++;
        break;
    }
  }
  return pattern;
}

export function generateRandom(
  hints: number,
  random: JavaRandom,
  options: NpGenOptions = defaultOptions(),
  variant: Variant = STANDARD_VARIANT,
  symmetry: Symmetry = "rot4",
): RandomGenerated | undefined {
  const attempts = options.attempts ?? 100;
  for (
    let attempt = 0;
    attempts === 0 || attempt < attempts;
    attempt++
  ) {
    const pattern = randomPattern(variant.size, hints, random, symmetry);
    const generated = generate(pattern, random, options, variant);
    if (generated !== undefined) return { pattern, generated };
  }
  return undefined;
}
