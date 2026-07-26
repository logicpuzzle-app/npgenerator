/*
 * Copyright (C) 2007 Time Intermedia Corporation <puzzle@timedia.co.jp>
 * TypeScript rewrite derived from NPGenerator V2.0.2.
 * Director: Hirofumi Fujiwara / Puzzler: Naoki Inaba / Programmer: Masaya Kiwada
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import {
  BlockConstraint,
  KindOfAnswer,
  SolverMethod,
  Status,
  sqrt,
} from "./core.js";
import { JavaRandom } from "./random.js";
import { addNumber, answer } from "./solver.js";

export class Generator {
  readonly seedMethod = new SolverMethod();
  method = new SolverMethod();
  forbidden = -1;

  private readonly hiddenList: number[];
  private readonly hintList: number[];
  private readonly groups: number[][];
  private readonly seedStatus: Status;
  private readonly status: Status;
  private readonly groupStatus: Status;
  private readonly original: Status;
  private readonly trial: Status;
  private readonly candidates: number[] = [];
  private seed: number[];

  constructor(
    private readonly numSize: number,
    private readonly hint: number[],
    private readonly hidden: number[],
    private readonly block: BlockConstraint,
    private readonly random: JavaRandom,
    initialSeed?: number[],
  ) {
    this.seed = initialSeed === undefined ? [] : [...initialSeed];
    this.hiddenList = hidden.flatMap((value, index) => value !== 0 ? [index] : []);
    this.hintList = hint.flatMap((value, index) => value !== 0 ? [index] : []);
    this.seedStatus = new Status(numSize, block);
    this.status = new Status(numSize, block);
    this.groupStatus = new Status(numSize, block);
    this.original = new Status(numSize, block);
    this.trial = new Status(numSize, block);
    this.seedMethod.localization = true;
    this.seedMethod.nakedPair = true;
    this.seedMethod.hiddenPair = true;

    let groupCount = sqrt(this.hintList.length);
    if (groupCount === 0) groupCount = 1;
    const remainder = this.hintList.length % groupCount;
    const unit = Math.trunc(this.hintList.length / groupCount);
    let cursor = 0;
    this.groups = Array.from({ length: groupCount }, (_, index) => {
      const count = unit + (index < remainder ? 1 : 0);
      const group = this.hintList.slice(cursor, cursor + count);
      cursor += count;
      return group;
    });
  }

  setMethod(method: SolverMethod): void {
    this.method = method;
  }

  setForbidden(forbidden: number): void {
    this.forbidden = forbidden;
  }

  private generateSeedSub(): number[] | undefined {
    const state = this.seedStatus;
    state.clear();
    for (let cell = 0; cell < this.hidden.length; cell++) {
      const value = this.hidden[cell]!;
      if (value > 0) addNumber(state, cell, value);
    }
    answer(state, this.seedMethod);
    for (let cell = 0; cell < this.hidden.length; cell++) {
      if (!state.isEmptyCell(cell)) continue;
      const candidateCount = state.getCandCountOfCell(cell);
      if (candidateCount === 0 || state.isNoAnswer()) return undefined;
      const randomIndex = this.random.nextInt(candidateCount);
      let value = state.getNthCandOfCell(cell, randomIndex);
      if (this.hint[cell] !== 0 && value === this.forbidden) {
        if (candidateCount === 1) return undefined;
        value = state.getNthCandOfCell(cell, (randomIndex + 1) % candidateCount);
      }
      addNumber(state, cell, value);
      answer(state, this.seedMethod);
    }
    if (this.forbidden > 0) {
      for (const cell of this.hintList) {
        if (state.getCell(cell) === this.forbidden) return undefined;
      }
    }
    return state.getSpaceCount() === 0 ? [...state.cells()] : undefined;
  }

  private generateSeed(): number[] | undefined {
    for (let failed = 0; failed <= 100; failed++) {
      const value = this.generateSeedSub();
      if (value !== undefined) return value;
    }
    return undefined;
  }

  private fitsHidden(cells: ArrayLike<number>): boolean {
    return this.hiddenList.every((cell) => cells[cell] === this.hidden[cell]);
  }

  generate(): number[] | undefined {
    const generatedSeed = this.generateSeed();
    if (generatedSeed === undefined) return undefined;
    this.seed = generatedSeed;

    const problem = Array(this.hidden.length).fill(0) as number[];
    for (const cell of this.hintList) problem[cell] = this.seed[cell]!;

    const state = this.status;
    state.clear();
    state.setUniqueMethod(this.method.unique);
    for (let cell = 0; cell < problem.length; cell++) {
      if (problem[cell]! > 0) addNumber(state, cell, problem[cell]!);
    }
    answer(state, this.method);
    if (state.getKindOfAnswer() === KindOfAnswer.UNIQUE_ANSWER) return problem;

    this.random.shuffle(this.hintList);
    let zero = state.getSpaceCount();
    if (zero === 0) return problem;
    let yet = true;
    while (yet) {
      yet = false;
      for (let groupIndex = 0; groupIndex < this.groups.length; groupIndex++) {
        const group = this.groups[groupIndex]!;
        const groupStatus = this.groupStatus;
        groupStatus.clear();
        groupStatus.setUniqueMethod(this.method.unique);
        for (let otherIndex = 0; otherIndex < this.groups.length; otherIndex++) {
          if (groupIndex === otherIndex) continue;
          for (const cell of this.groups[otherIndex]!) {
            addNumber(groupStatus, cell, problem[cell]!);
          }
        }
        for (const cell of group) {
          if (this.hidden[cell] !== 0) continue;
          const previous = problem[cell]!;
          problem[cell] = 0;
          const original = this.original;
          original.copyStatusToThis(groupStatus);
          for (const other of group) {
            if (other !== cell) addNumber(original, other, problem[other]!);
          }
          if (original.getCandCountOfCell(cell) <= 1) {
            problem[cell] = previous;
            continue;
          }
          const candidates = this.candidates;
          candidates.length = 0;
          const candidateMask = original.getCandidateMask(cell);
          for (let number = 1; number <= this.numSize; number++) {
            if ((candidateMask & (1 << (number - 1))) !== 0) candidates.push(number);
          }
          if (!candidates.includes(previous)) candidates.push(previous);
          problem[cell] = previous;
          const previousIndex = candidates.indexOf(previous);
          for (let offset = 1; offset < candidates.length; offset++) {
            const next = candidates[(previousIndex + offset) % candidates.length]!;
            if (next === previous || next === this.forbidden) continue;
            const trial = this.trial;
            trial.copyStatusToThis(original);
            addNumber(trial, cell, next);
            answer(trial, this.method);
            const space = trial.getSpaceCount();
            if (!trial.isNoAnswer() && this.fitsHidden(trial.cells()) && zero > space) {
              zero = space;
              problem[cell] = next;
              if (zero === 0) return problem;
              yet = true;
              break;
            }
          }
          if (yet) break;
        }
      }
    }
    return zero === 0 ? problem : undefined;
  }
}
