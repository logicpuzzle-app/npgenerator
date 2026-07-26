/*
 * Copyright (C) 2007 Time Intermedia Corporation <puzzle@timedia.co.jp>
 * TypeScript rewrite derived from NPGenerator V2.0.2.
 * Director: Hirofumi Fujiwara / Puzzler: Naoki Inaba / Programmer: Masaya Kiwada
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import { JavaRandom } from "./random.js";

export enum KindOfAnswer {
  UNIQUE_ANSWER,
  NO_ANSWER,
  MULTIPLE_ANSWER,
  IRREGULAR_PROBLEM,
  NO_JUDGE,
}

export class UniqueMethod {
  vhUnique = true;
  cellUnique = true;
  blockUnique = true;

  setAllUse(): void {
    this.vhUnique = this.cellUnique = this.blockUnique = true;
  }
}

export class SolverMethod {
  localization = false;
  nakedPair = false;
  hiddenPair = false;
  nakedTriple = false;
  hiddenTriple = false;
  XWing = false;
  swordfish = false;
  unique = new UniqueMethod();

  setAllUse(): void {
    this.localization = this.nakedPair = this.hiddenPair = true;
    this.nakedTriple = this.hiddenTriple = this.XWing = this.swordfish = true;
    this.unique.setAllUse();
  }
}

export const bit = {
  right1BitOff: (x: number): number => x & (x - 1),
  numberOf1Bits(x: number): number {
    let n = 0;
    while (x !== 0) {
      x &= x - 1;
      n++;
    }
    return n;
  },
  ntz: (x: number): number => Math.clz32(x & -x) ^ 31,
};

export class BlockConstraint {
  readonly blocks: Int32Array;
  readonly blockOffsets: Int32Array;
  readonly where: Int32Array;
  readonly whereOffsets: Int32Array;
  readonly wherePositions: Int32Array;
  readonly rowOfCell: Int32Array;
  readonly columnOfCell: Int32Array;
  readonly bitCounts: Uint8Array | undefined;
  readonly intersections: Array<Array<number[] | undefined>>;
  readonly intersectionList: Array<[number, number]> = [];
  readonly intersectionDetails: Array<{
    first: number;
    second: number;
    cells: number[];
    firstMask: number;
    secondMask: number;
  }> = [];
  private readonly blockViews: Int32Array[];
  private readonly whereViews: Int32Array[];
  private readonly wherePositionViews: Int32Array[];

  constructor(blocks: number[][], readonly numSize: number) {
    if (numSize <= 12) {
      this.bitCounts = new Uint8Array(1 << numSize);
      for (let mask = 1; mask < this.bitCounts.length; mask++) {
        this.bitCounts[mask] = this.bitCounts[mask >> 1]! + (mask & 1);
      }
    }
    const normalized = blocks.map((block) => [...block].sort((a, b) => a - b));
    this.blockOffsets = new Int32Array(normalized.length + 1);
    let blockCellCount = 0;
    for (let i = 0; i < normalized.length; i++) {
      this.blockOffsets[i] = blockCellCount;
      blockCellCount += normalized[i]!.length;
    }
    this.blockOffsets[normalized.length] = blockCellCount;
    this.blocks = new Int32Array(blockCellCount);
    this.blockViews = new Array<Int32Array>(normalized.length);
    for (let i = 0; i < normalized.length; i++) {
      const offset = this.blockOffsets[i]!;
      this.blocks.set(normalized[i]!, offset);
      this.blockViews[i] = this.blocks.subarray(offset, this.blockOffsets[i + 1]!);
    }

    const n = numSize * numSize;
    this.rowOfCell = new Int32Array(n);
    this.columnOfCell = new Int32Array(n);
    for (let cell = 0; cell < n; cell++) {
      this.rowOfCell[cell] = (cell / numSize) | 0;
      this.columnOfCell[cell] = cell % numSize;
    }
    const whereLists = Array.from({ length: n }, () => [] as number[]);
    const positionLists = Array.from({ length: n }, () => [] as number[]);
    for (let block = 0; block < normalized.length; block++) {
      const cells = normalized[block]!;
      for (let position = 0; position < cells.length; position++) {
        const cell = cells[position]!;
        whereLists[cell]!.push(block);
        positionLists[cell]!.push(position);
      }
    }
    this.whereOffsets = new Int32Array(n + 1);
    let membershipCount = 0;
    for (let cell = 0; cell < n; cell++) {
      this.whereOffsets[cell] = membershipCount;
      membershipCount += whereLists[cell]!.length;
    }
    this.whereOffsets[n] = membershipCount;
    this.where = new Int32Array(membershipCount);
    this.wherePositions = new Int32Array(membershipCount);
    this.whereViews = new Array<Int32Array>(n);
    this.wherePositionViews = new Array<Int32Array>(n);
    for (let cell = 0; cell < n; cell++) {
      const offset = this.whereOffsets[cell]!;
      this.where.set(whereLists[cell]!, offset);
      this.wherePositions.set(positionLists[cell]!, offset);
      const end = this.whereOffsets[cell + 1]!;
      this.whereViews[cell] = this.where.subarray(offset, end);
      this.wherePositionViews[cell] = this.wherePositions.subarray(offset, end);
    }
    this.intersections = Array.from({ length: normalized.length }, () =>
      Array<number[] | undefined>(normalized.length),
    );
    for (let i = 0; i < normalized.length; i++) {
      for (let j = i + 1; j < normalized.length; j++) {
        const other = new Set(normalized[j]);
        const intersection = normalized[i]!.filter((x) => other.has(x));
        this.intersections[i]![j] = intersection;
        if (intersection.length >= 2) {
          this.intersectionList.push([i, j]);
          const common = new Set(intersection);
          let firstMask = 0;
          let secondMask = 0;
          for (let position = 0; position < normalized[i]!.length; position++) {
            if (common.has(normalized[i]![position]!)) firstMask |= 1 << position;
          }
          for (let position = 0; position < normalized[j]!.length; position++) {
            if (common.has(normalized[j]![position]!)) secondMask |= 1 << position;
          }
          this.intersectionDetails.push({
            first: i,
            second: j,
            cells: intersection,
            firstMask,
            secondMask,
          });
        }
      }
    }
  }

  get blockCount(): number { return this.blockViews.length; }
  getBlock(index: number): Int32Array { return this.blockViews[index]!; }
  getWhere(cell: number): Int32Array { return this.whereViews[cell]!; }
  getWherePositions(cell: number): Int32Array { return this.wherePositionViews[cell]!; }

  getBlockInterSection(a: number, b: number): number[] | undefined {
    if (a === b) return undefined;
    return a < b ? this.intersections[a]![b] : this.intersections[b]![a];
  }
}

export function sqrt(x: number): number {
  return Math.trunc(Math.sqrt(x) + 1e-10);
}

export function makeNormalBlock(n: number, w: number, h: number): number[][] {
  const out: number[][] = [];
  for (let column = 0; column < n; column++) {
    out.push(Array.from({ length: n }, (_, row) => row * n + column));
  }
  for (let row = 0; row < n; row++) {
    out.push(Array.from({ length: n }, (_, column) => row * n + column));
  }
  for (let i = 0; i < n; i += w) {
    for (let j = 0; j < n; j += h) {
      const rect: number[] = [];
      for (let k = 0; k < w; k++) {
        for (let l = 0; l < h; l++) rect.push((j + l) * n + i + k);
      }
      out.push(rect);
    }
  }
  return out;
}

let utilityRandom = new JavaRandom(0);
export function setUtilityRandom(random: JavaRandom): void {
  utilityRandom = random;
}
export function utilityRandomInt(n: number): number {
  return utilityRandom.nextInt(n);
}

export class Status {
  private readonly cell: Int32Array;
  private readonly cand: Int32Array;
  private readonly candCounts: Int32Array;
  private readonly exist: Int32Array;
  private readonly candCountOfBlock: Int32Array;
  private readonly candPositions: Int32Array;
  private readonly blockStride: number;
  private spaceCount: number;
  private candCount: number;
  private kind = KindOfAnswer.NO_JUDGE;
  unique = new UniqueMethod();

  constructor(readonly numSize: number, readonly block: BlockConstraint) {
    const n2 = numSize * numSize;
    this.cell = new Int32Array(n2);
    this.cand = new Int32Array(n2);
    this.candCounts = new Int32Array(n2);
    this.exist = new Int32Array(block.blockCount);
    this.blockStride = numSize + 1;
    this.candCountOfBlock = new Int32Array(block.blockCount * this.blockStride);
    this.candPositions = new Int32Array(block.blockCount * this.blockStride);
    this.spaceCount = n2;
    this.candCount = n2 * numSize;
    this.clear();
  }

  clear(): void {
    this.kind = KindOfAnswer.NO_JUDGE;
    this.cell.fill(0);
    this.exist.fill(0);
    this.spaceCount = this.cell.length;
    this.candCount = this.numSize ** 3;
    const fullMask = (1 << this.numSize) - 1;
    this.cand.fill(fullMask);
    this.candCounts.fill(this.numSize);
    this.candCountOfBlock.fill(this.numSize);
    this.candPositions.fill(fullMask);
    for (let block = 0; block < this.block.blockCount; block++) {
      const unused = block * this.blockStride;
      this.candCountOfBlock[unused] = 0;
      this.candPositions[unused] = 0;
    }
  }

  copyStatusToThis(state: Status): void {
    this.unique = state.unique;
    this.kind = state.kind;
    this.spaceCount = state.spaceCount;
    this.candCount = state.candCount;
    this.cell.set(state.cell);
    this.cand.set(state.cand);
    this.candCounts.set(state.candCounts);
    this.exist.set(state.exist);
    this.candCountOfBlock.set(state.candCountOfBlock);
    this.candPositions.set(state.candPositions);
  }

  getCell(index?: number): Int32Array | number {
    return index === undefined ? this.cell : this.cell[index]!;
  }
  cells(): Int32Array { return this.cell; }
  isEmptyCell(index: number): boolean { return this.cell[index] === 0; }
  getCellSize(): number { return this.cell.length; }
  getSize(): number { return this.numSize; }
  getBlockNum(): number { return this.block.blockCount; }
  getBlock(index: number): Int32Array { return this.block.getBlock(index); }
  getSpaceCount(): number { return this.spaceCount; }
  getCandCount(): number { return this.candCount; }
  getKindOfAnswer(): KindOfAnswer { return this.kind; }
  setKindOfAnswer(kind: KindOfAnswer): void { this.kind = kind; }
  setUniqueMethod(method: UniqueMethod): void { this.unique = method; }
  isInvalid(): boolean {
    return this.kind === KindOfAnswer.NO_ANSWER || this.kind === KindOfAnswer.IRREGULAR_PROBLEM;
  }
  isNoAnswer(): boolean { return this.isInvalid(); }
  isVHBlock(index: number): boolean { return index < 2 * this.numSize; }
  isCand(cell: number, n: number): boolean { return (this.cand[cell]! & (1 << (n - 1))) !== 0; }
  isNoCandidate(cell: number): boolean { return this.cand[cell] === 0; }
  isUniqueCandidate(cell: number): boolean {
    const value = this.cand[cell]!;
    return value !== 0 && bit.right1BitOff(value) === 0;
  }
  getUniqueCandidate(cell: number): number { return bit.ntz(this.cand[cell]!) + 1; }
  getCandCountOfCell(cell: number): number { return this.candCounts[cell]!; }
  getCandCountOfBlock(block: number, n: number): number {
    return this.candCountOfBlock[block * this.blockStride + n]!;
  }
  getCandidateMask(cell: number): number { return this.cand[cell]!; }
  getCandidatePositionMask(block: number, n: number): number {
    return this.candPositions[block * this.blockStride + n]!;
  }
  isExistNumberOnZone(block: number, n: number): boolean {
    return (this.exist[block]! & (1 << (n - 1))) !== 0;
  }
  getCandidateList(cell: number): number[] {
    const list: number[] = [];
    for (let n = 1; n <= this.numSize; n++) if (this.isCand(cell, n)) list.push(n);
    return list;
  }
  getCellListHaveCandidateNumberInBlock(block: number, n: number): number[] {
    const result: number[] = [];
    const cells = this.getBlock(block);
    let positions = this.getCandidatePositionMask(block, n);
    while (positions !== 0) {
      const positionBit = positions & -positions;
      result.push(cells[bit.ntz(positionBit)]!);
      positions ^= positionBit;
    }
    return result;
  }
  getCellIndexListHavingCandidateNumberInBlock(block: number, n: number): number[] {
    const result: number[] = [];
    let positions = this.getCandidatePositionMask(block, n);
    while (positions !== 0) {
      const positionBit = positions & -positions;
      result.push(bit.ntz(positionBit));
      positions ^= positionBit;
    }
    return result;
  }
  getNthCandOfCell(cell: number, n: number): number {
    let value = this.cand[cell]!;
    while (n-- > 0) value = bit.right1BitOff(value);
    return value === 0 ? -1 : bit.ntz(value) + 1;
  }
  uniqueCandidateNumberOfCell(cell: number): number {
    if (this.isUniqueCandidate(cell)) return bit.ntz(this.cand[cell]!) + 1;
    const memberships = this.block.getWhere(cell);
    for (let index = 0; index < memberships.length; index++) {
      const block = memberships[index]!;
      for (let n = 1; n <= this.numSize; n++) {
        if (this.getCandCountOfBlock(block, n) === 1 && this.isCand(cell, n)) return n;
      }
    }
    return -1;
  }
  assignValue(cell: number, n: number): boolean {
    if (n === 0) return false;
    if (!this.isEmptyCell(cell)) {
      if (n !== this.cell[cell]) this.kind = KindOfAnswer.NO_ANSWER;
      return false;
    }
    if (!this.isCand(cell, n)) {
      this.kind = KindOfAnswer.NO_ANSWER;
      return false;
    }
    if (this.isNoAnswer()) return false;
    this.cell[cell] = n;
    this.spaceCount--;
    const memberships = this.block.getWhere(cell);
    for (let index = 0; index < memberships.length; index++) {
      const block = memberships[index]!;
      if (this.isExistNumberOnZone(block, n)) this.kind = KindOfAnswer.NO_ANSWER;
      else this.exist[block] = this.exist[block]! | (1 << (n - 1));
    }
    return true;
  }
  deleteCandidate(cell: number, n: number): boolean {
    if (n === 0 || this.isNoAnswer() || !this.isCand(cell, n)) return false;
    if (this.isNoCandidate(cell)) {
      this.kind = KindOfAnswer.NO_ANSWER;
      return false;
    }
    this.cand[cell] = this.cand[cell]! & ~(1 << (n - 1));
    this.candCounts[cell]!--;
    this.candCount--;
    const memberships = this.block.getWhere(cell);
    const positions = this.block.getWherePositions(cell);
    for (let index = 0; index < memberships.length; index++) {
      const candidateIndex = memberships[index]! * this.blockStride + n;
      this.candCountOfBlock[candidateIndex]!--;
      this.candPositions[candidateIndex] =
        this.candPositions[candidateIndex]! & ~(1 << positions[index]!);
      if (this.candCountOfBlock[candidateIndex] === 0) this.kind = KindOfAnswer.NO_ANSWER;
    }
    return true;
  }
}
