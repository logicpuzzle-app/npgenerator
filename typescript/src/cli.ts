#!/usr/bin/env -S node --import tsx
/*
 * Copyright (C) 2007 Time Intermedia Corporation <puzzle@timedia.co.jp>
 * TypeScript rewrite derived from NPGenerator V2.0.2.
 * Director: Hirofumi Fujiwara / Puzzler: Naoki Inaba / Programmer: Masaya Kiwada
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import { KindOfAnswer } from "./core.js";
import { formatGrid, readGrid } from "./grid.js";
import {
  allMethods,
  generate,
  generateRandom,
  type Generated,
  type NpGenOptions,
  type Symmetry,
  solve,
} from "./npgen.js";
import { JavaRandom } from "./random.js";
import {
  buildVariant,
  buildXmlVariant,
  DEFAULT_SIZE,
  type Variant,
} from "./variant.js";
import {
  type NumberPlaceFile,
  readNumberPlaceFile,
  writeNumberPlaceFile,
} from "./xml.js";

const SOLVE_OPTIONS = new Set(["--use", "--unique"]);
const GENERATE_OPTIONS = new Set([
  "--use", "--unique", "--dp-min", "--dp-max",
]);
const VARIANT_OPTIONS = new Set([
  "--size", "--blocks", "--seed", "--format", "--out",
]);
const VARIANT_FLAGS = new Set([
  "--diagonal", "--no-vertical", "--no-horizontal",
]);

function union(...sets: Array<Set<string>>): Set<string> {
  const result = new Set<string>();
  for (const set of sets) for (const value of set) result.add(value);
  return result;
}

function parseSymmetry(value: string | undefined): Symmetry {
  switch (value) {
    case undefined:
    case "rot4":
      return "rot4";
    case "rot2":
    case "mirror-h":
    case "mirror-v":
    case "none":
      return value;
    default:
      throw new Error(
        "--symmetry must be rot4, rot2, mirror-h, mirror-v, or none",
      );
  }
}

function validateRandomHints(
  size: number,
  hints: number,
  symmetry: Symmetry,
): void {
  const orbitSize = symmetry === "rot4" ? 4 : symmetry === "none" ? 1 : 2;
  const maximumHints = symmetry === "none"
    ? size * size - 1
    : symmetry === "mirror-h" || symmetry === "mirror-v"
    ? size * size - (size % 2 === 0 ? 0 : size)
    : size * size - (size % 2);
  if (hints > 0 && hints <= maximumHints && hints % orbitSize === 0) return;
  if (orbitSize === 1) {
    throw new Error(
      `--hints must be between 1 and ${maximumHints} for --symmetry ${symmetry}`,
    );
  }
  throw new Error(
    `--hints must be a positive multiple of ${orbitSize} no greater than `
    + `${maximumHints} for --symmetry ${symmetry}`,
  );
}

class ParsedOptions {
  private readonly values = new Map<string, string>();

  static parse(
    args: string[],
    start: number,
    valueOptions: Set<string>,
    flags: Set<string>,
  ): ParsedOptions {
    const parsed = new ParsedOptions();
    for (let index = start; index < args.length; index++) {
      const option = args[index]!;
      if (flags.has(option)) {
        parsed.values.set(option, "");
        continue;
      }
      if (!valueOptions.has(option)) throw new Error(`unknown option: ${option}`);
      if (index + 1 === args.length) {
        throw new Error(`incomplete option: ${option}`);
      }
      parsed.values.set(option, args[++index]!);
    }
    return parsed;
  }

  has(option: string): boolean {
    return this.values.has(option);
  }

  value(option: string): string | undefined {
    return this.values.get(option);
  }

  bigint(option: string, defaultValue: bigint): bigint {
    const value = this.value(option);
    if (value === undefined) return defaultValue;
    try {
      if (!/^[+-]?\d+$/.test(value)) throw new Error();
      const parsed = BigInt(value);
      if (parsed < -(1n << 63n) || parsed > (1n << 63n) - 1n) {
        throw new Error();
      }
      return parsed;
    } catch {
      throw new Error(`${option} requires an integer`);
    }
  }

  integer(option: string, defaultValue: number): number {
    const value = this.bigint(option, BigInt(defaultValue));
    if (value < -2147483648n || value > 2147483647n) {
      throw new Error(`${option} requires an integer`);
    }
    return Number(value);
  }
}

function usage(): void {
  console.error("usage: npgen solve|generate|random|bench ...");
}

function optionList(value: string, option: string): string[] {
  if (value.length === 0) throw new Error(`${option} requires a non-empty list`);
  const list = value.split(",");
  if (list.some((item) => item.length === 0)) {
    throw new Error(`${option} contains an empty value`);
  }
  return list;
}

function requireNoneAlone(list: string[], option: string): void {
  if (list.includes("none") && list.length !== 1) {
    throw new Error(`${option} value none cannot be combined with other values`);
  }
}

function commandOptions(
  parsed: ParsedOptions,
  allowForbidden: boolean,
  size: number,
): NpGenOptions {
  const method = allMethods();
  const use = parsed.value("--use");
  if (use !== undefined) {
    method.localization = false;
    method.nakedPair = false;
    method.hiddenPair = false;
    method.nakedTriple = false;
    method.hiddenTriple = false;
    method.XWing = false;
    method.swordfish = false;
    const names = optionList(use, "--use");
    requireNoneAlone(names, "--use");
    for (const name of names) {
      switch (name) {
        case "none": break;
        case "localization": method.localization = true; break;
        case "naked-pair": method.nakedPair = true; break;
        case "hidden-pair": method.hiddenPair = true; break;
        case "naked-triple": method.nakedTriple = true; break;
        case "hidden-triple": method.hiddenTriple = true; break;
        case "x-wing": method.XWing = true; break;
        case "swordfish": method.swordfish = true; break;
        default: throw new Error(`unknown --use value: ${name}`);
      }
    }
  }
  const unique = parsed.value("--unique");
  if (unique !== undefined) {
    method.unique.vhUnique = false;
    method.unique.cellUnique = false;
    method.unique.blockUnique = false;
    const names = optionList(unique, "--unique");
    requireNoneAlone(names, "--unique");
    for (const name of names) {
      switch (name) {
        case "none": break;
        case "vh": method.unique.vhUnique = true; break;
        case "cell": method.unique.cellUnique = true; break;
        case "block": method.unique.blockUnique = true; break;
        default: throw new Error(`unknown --unique value: ${name}`);
      }
    }
  }
  let dpMin = Math.max(parsed.integer("--dp-min", 0), 0);
  let dpMax = parsed.integer("--dp-max", 2147483647);
  if (dpMax < 0) dpMax = 2147483647;
  if (dpMin > dpMax) [dpMin, dpMax] = [dpMax, dpMin];
  const forbidden = parsed.integer("--forbidden", -1);
  if (
    allowForbidden &&
    parsed.has("--forbidden") &&
    (forbidden < 1 || forbidden > size)
  ) {
    throw new Error(`--forbidden must be between 1 and ${size}`);
  }
  return { method, dpMin, dpMax, forbidden };
}

function validateValues(
  values: number[],
  size: number,
  pattern: boolean,
  name: string,
): void {
  if (values.length !== size * size) {
    throw new Error(`${name} must contain exactly ${size * size} cells`);
  }
  for (const value of values) {
    if (pattern) {
      if (value !== 0 && value !== 1) {
        throw new Error("pattern cells must be 0 or 1");
      }
    } else if (value < 0 || value > size) {
      throw new Error(`${name} cells must be between 0 and ${size}`);
    }
  }
}

function requireXmlFormat(parsed: ParsedOptions): void {
  const format = parsed.value("--format");
  if (format !== undefined && format.toLowerCase() !== "xml") {
    throw new Error("--format only supports xml");
  }
}

function xmlOutput(parsed: ParsedOptions): boolean {
  return parsed.has("--format") || parsed.has("--out");
}

function isXml(path: string): boolean {
  return path.toLowerCase().endsWith(".xml");
}

function resolveXmlSize(
  parsed: ParsedOptions,
  source: NumberPlaceFile,
): number {
  if (
    parsed.has("--size") &&
    parsed.integer("--size", source.numSize) !== source.numSize
  ) {
    throw new Error(`--size does not match XML problem size ${source.numSize}`);
  }
  return source.numSize;
}

async function resolveXmlVariant(
  source: NumberPlaceFile,
  parsed: ParsedOptions,
  random: JavaRandom,
): Promise<Variant> {
  const size = resolveXmlSize(parsed, source);
  return parsed.has("--blocks")
    ? buildVariant(
      size,
      parsed.value("--blocks"),
      source.vertical && !parsed.has("--no-vertical"),
      source.horizontal && !parsed.has("--no-horizontal"),
      source.diagonal || parsed.has("--diagonal"),
      random,
      true,
    )
    : buildXmlVariant(
      source,
      parsed.has("--diagonal"),
      parsed.has("--no-vertical"),
      parsed.has("--no-horizontal"),
    );
}

function xmlFile(
  variant: Variant,
  hint: number[],
  hidden: number[],
  problem: number[],
  answer: number[],
  difficulty: number,
  source?: NumberPlaceFile,
): NumberPlaceFile {
  return {
    numSize: variant.size,
    hint: hint.map((value) => value === 0 ? 0 : 1),
    hasHint: true,
    hidden: [...hidden],
    problem: [...problem],
    answer: [...answer],
    blockArray: [...variant.blockArray],
    groupArrays: variant.defaultBlock ? [] : [[...variant.blockArray]],
    ...(source?.comment === undefined ? {} : { comment: source.comment }),
    vertical: variant.vertical,
    horizontal: variant.horizontal,
    difficult: Math.trunc(difficulty),
    diagonal: variant.diagonal,
    defaultBlock: variant.defaultBlock,
  };
}

async function outputXml(
  parsed: ParsedOptions,
  file: NumberPlaceFile,
): Promise<void> {
  await writeNumberPlaceFile(parsed.value("--out"), file);
}

function printGenerated(result: Generated, size: number): void {
  process.stdout.write("PROBLEM\n");
  process.stdout.write(formatGrid(result.problem, size, false));
  process.stdout.write("SOLUTION\n");
  process.stdout.write(formatGrid(result.solution, size, false));
  process.stdout.write(`DIFFICULTY ${formatDifficulty(result.difficulty)}\n`);
}

function formatDifficulty(value: number): string {
  return Number.isFinite(value) && Number.isInteger(value)
    ? `${String(value)}.0`
    : String(value);
}

async function run(args: string[]): Promise<number> {
  if (args.length === 0) {
    usage();
    return 2;
  }
  switch (args[0]) {
    case "solve": {
      if (args.length < 2) {
        throw new Error(
          "usage: npgen solve <problem> [--size N] [--blocks spec]"
          + " [--diagonal] [--format xml] [--out file.xml]",
        );
      }
      const parsed = ParsedOptions.parse(
        args,
        2,
        union(SOLVE_OPTIONS, VARIANT_OPTIONS),
        VARIANT_FLAGS,
      );
      requireXmlFormat(parsed);
      const random = new JavaRandom(parsed.bigint("--seed", 0n));
      const inputPath = args[1]!;
      const xmlInput = parsed.has("--format") || isXml(inputPath);
      let source: NumberPlaceFile | undefined;
      let problem: number[];
      let variant: Variant;
      if (xmlInput) {
        source = await readNumberPlaceFile(inputPath);
        variant = await resolveXmlVariant(source, parsed, random);
        problem = source.problem;
      } else {
        const size = parsed.integer("--size", DEFAULT_SIZE);
        variant = await buildVariant(
          size,
          parsed.value("--blocks"),
          !parsed.has("--no-vertical"),
          !parsed.has("--no-horizontal"),
          parsed.has("--diagonal"),
          random,
          false,
        );
        problem = await readGrid(inputPath, size, false);
      }
      validateValues(problem, variant.size, false, "problem");
      const options = commandOptions(parsed, false, variant.size);
      const result = solve(problem, options.method, variant);
      if (
        result.status === KindOfAnswer.NO_ANSWER ||
        result.status === KindOfAnswer.IRREGULAR_PROBLEM ||
        (
          (parsed.has("--use") || parsed.has("--unique")) &&
          result.status !== KindOfAnswer.UNIQUE_ANSWER
        )
      ) return 1;
      if (xmlOutput(parsed)) {
        await outputXml(
          parsed,
          xmlFile(
            variant,
            source?.hasHint ? source.hint : problem,
            source?.hidden ?? Array(variant.size * variant.size).fill(0),
            problem,
            result.solution,
            result.difficulty,
            source,
          ),
        );
      } else {
        process.stdout.write("SOLUTION\n");
        process.stdout.write(formatGrid(result.solution, variant.size, false));
        process.stdout.write(`DIFFICULTY ${formatDifficulty(result.difficulty)}\n`);
      }
      return 0;
    }
    case "generate": {
      if (args.length < 2) {
        throw new Error(
          "usage: npgen generate <pattern> [--seed N] [--size N]"
          + " [--blocks spec] [--diagonal] [--format xml] [--out file.xml]",
        );
      }
      const parsed = ParsedOptions.parse(
        args,
        2,
        union(GENERATE_OPTIONS, VARIANT_OPTIONS, new Set(["--forbidden"])),
        VARIANT_FLAGS,
      );
      requireXmlFormat(parsed);
      const random = new JavaRandom(parsed.bigint("--seed", 0n));
      const inputPath = args[1]!;
      const xmlInput = parsed.has("--format") || isXml(inputPath);
      let pattern: number[];
      let hidden: number[];
      let initialSeed: number[] | undefined;
      let variant: Variant;
      let source: NumberPlaceFile | undefined;
      if (xmlInput) {
        source = await readNumberPlaceFile(inputPath);
        variant = await resolveXmlVariant(source, parsed, random);
        pattern = source.hint;
        hidden = source.hidden;
        initialSeed = source.seed;
      } else {
        const size = parsed.integer("--size", DEFAULT_SIZE);
        variant = await buildVariant(
          size,
          parsed.value("--blocks"),
          !parsed.has("--no-vertical"),
          !parsed.has("--no-horizontal"),
          parsed.has("--diagonal"),
          random,
          false,
        );
        pattern = await readGrid(inputPath, size, true);
        hidden = Array(size * size).fill(0) as number[];
      }
      validateValues(pattern, variant.size, true, "pattern");
      validateValues(hidden, variant.size, false, "hidden");
      const options = commandOptions(parsed, true, variant.size);
      const result = generate(
        pattern,
        random,
        options,
        variant,
        hidden,
        initialSeed,
      );
      if (result === undefined) return 1;
      if (xmlOutput(parsed)) {
        await outputXml(
          parsed,
          xmlFile(
            variant,
            pattern,
            hidden,
            result.problem,
            result.solution,
            result.difficulty,
            source,
          ),
        );
      } else {
        printGenerated(result, variant.size);
      }
      return 0;
    }
    case "random": {
      const parsed = ParsedOptions.parse(
        args,
        1,
        union(
          GENERATE_OPTIONS,
          VARIANT_OPTIONS,
          new Set(["--hints", "--forbidden", "--symmetry"]),
        ),
        VARIANT_FLAGS,
      );
      requireXmlFormat(parsed);
      const size = parsed.integer("--size", DEFAULT_SIZE);
      const hints = parsed.integer("--hints", 20);
      const symmetry = parseSymmetry(parsed.value("--symmetry"));
      const random = new JavaRandom(parsed.bigint("--seed", 0n));
      const variant = await buildVariant(
        size,
        parsed.value("--blocks"),
        !parsed.has("--no-vertical"),
        !parsed.has("--no-horizontal"),
        parsed.has("--diagonal"),
        random,
        false,
      );
      validateRandomHints(size, hints, symmetry);
      const options = commandOptions(parsed, true, size);
      const result = generateRandom(hints, random, options, variant, symmetry);
      if (result === undefined) return 1;
      if (xmlOutput(parsed)) {
        await outputXml(
          parsed,
          xmlFile(
            variant,
            result.pattern,
            Array(size * size).fill(0),
            result.generated.problem,
            result.generated.solution,
            result.generated.difficulty,
          ),
        );
      } else {
        process.stdout.write("PATTERN\n");
        process.stdout.write(formatGrid(result.pattern, size, true));
        printGenerated(result.generated, size);
      }
      return 0;
    }
    case "bench": {
      const parsed = ParsedOptions.parse(
        args,
        1,
        new Set(["--count", "--seed"]),
        new Set(),
      );
      const count = parsed.integer("--count", 10);
      const seed = parsed.bigint("--seed", 0n);
      if (count <= 0) throw new Error("--count must be positive");
      const random = new JavaRandom(seed);
      const start = process.hrtime.bigint();
      let succeeded = 0;
      for (let index = 0; index < count; index++) {
        if (generateRandom(20, random) !== undefined) succeeded++;
      }
      const elapsed = (process.hrtime.bigint() - start) / 1_000_000n;
      console.log(`COUNT ${count}`);
      console.log(`SUCCEEDED ${succeeded}`);
      console.log(`ELAPSED_MS ${elapsed}`);
      return succeeded === count ? 0 : 1;
    }
    default:
      usage();
      return 2;
  }
}

try {
  process.exitCode = await run(process.argv.slice(2));
} catch (error) {
  const message = error instanceof Error ? error.message : String(error);
  console.error(`input error: ${message}`);
  process.exitCode = 2;
}
