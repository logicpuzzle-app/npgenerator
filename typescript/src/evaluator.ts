/*
 * Copyright (C) 2007 Time Intermedia Corporation <puzzle@timedia.co.jp>
 * TypeScript rewrite derived from NPGenerator V2.0.2.
 * Director: Hirofumi Fujiwara / Puzzler: Naoki Inaba / Programmer: Masaya Kiwada
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import { bit, BlockConstraint, Status } from "./core.js";

const BURIED_CELL_POINT = 1;
const DELETED_SAME_BLOCK = 2;
const DELETED_SAME_LINE = 3;
const UNIQUE_BLOCK = 1.0;
const UNIQUE_LINE = 1.5;
const UNIQUE_CELL = 2.0;

class CandidateData {
  point = 1 << 29;
}

class CandidateTable {
  private readonly table: CandidateData[][];

  constructor(cellCount: number, numberCount: number) {
    this.table = Array.from(
      { length: cellCount },
      () => Array.from({ length: numberCount }, () => new CandidateData()),
    );
  }

  get(cell: number, value: number): CandidateData {
    return this.table[cell]![value]!;
  }
}

function add32(value: number, addition: number): number {
  return (value + addition) | 0;
}

function binaryContains(sorted: number[], value: number): boolean {
  let low = 0;
  let high = sorted.length - 1;
  while (low <= high) {
    const middle = (low + high) >> 1;
    const found = sorted[middle]!;
    if (found === value) return true;
    if (found < value) low = middle + 1;
    else high = middle - 1;
  }
  return false;
}

function lowerPoint(
  state: Status,
  points: CandidateTable,
  cell: number,
  value: number,
  point: number,
): boolean {
  state.deleteCandidate(cell, value);
  const candidate = points.get(cell, value);
  if (candidate.point <= point) return false;
  candidate.point = point;
  return true;
}

function deleteCandidatePeers(
  state: Status,
  cell: number,
  value: number,
  points: CandidateTable,
): void {
  for (let other = 1; other <= state.getSize(); other++) {
    if (other === value) continue;
    state.deleteCandidate(cell, other);
    const candidate = points.get(cell, other);
    if (candidate.point > BURIED_CELL_POINT) candidate.point = BURIED_CELL_POINT;
  }
  for (const block of state.block.getWhere(cell)) {
    for (const peer of state.getBlock(block)) {
      if (peer === cell) continue;
      state.deleteCandidate(peer, value);
      const point = state.isVHBlock(block) ? DELETED_SAME_LINE : DELETED_SAME_BLOCK;
      const candidate = points.get(peer, value);
      if (candidate.point > point) candidate.point = point;
    }
  }
}

function addNumber(
  state: Status,
  cell: number,
  value: number,
  points: CandidateTable,
): void {
  if (state.assignValue(cell, value)) deleteCandidatePeers(state, cell, value, points);
}

function localization(state: Status, points: CandidateTable): boolean {
  let updated = false;
  const size = state.getSize();
  const c1 = Array(size + 1).fill(0) as number[];
  const c2 = Array(size + 1).fill(0) as number[];
  const c12 = Array(size + 1).fill(0) as number[];
  for (const [first, second] of state.block.intersectionList) {
    const intersection = state.block.getBlockInterSection(first, second)!;
    if (intersection.length <= 1) continue;
    for (let value = 1; value <= size; value++) {
      c1[value] = state.getCandCountOfBlock(first, value);
      c2[value] = state.getCandCountOfBlock(second, value);
      c12[value] = 0;
    }
    for (const cell of intersection) {
      for (let value = 1; value <= size; value++) {
        if (state.isCand(cell, value)) {
          c1[value]!--;
          c2[value]!--;
          c12[value]!++;
        }
      }
    }
    for (let value = 1; value <= size; value++) {
      if (c12[value] === 0) continue;
      let target = -1;
      let source = -1;
      if (c1[value]! > 0 && c2[value] === 0) {
        target = first;
        source = second;
      } else if (c1[value] === 0 && c2[value]! > 0) {
        target = second;
        source = first;
      }
      if (target < 0) continue;
      for (const cell of state.getBlock(target)) {
        if (binaryContains(intersection, cell)) continue;
        let point = 0;
        for (const sourceCell of state.getBlock(source)) {
          if (!binaryContains(intersection, sourceCell)) {
            point = add32(point, points.get(sourceCell, value).point);
          }
        }
        if (lowerPoint(state, points, cell, value, point)) updated = true;
      }
    }
  }
  return updated;
}

function nakedPair(state: Status, points: CandidateTable): boolean {
  let updated = false;
  const divisor = state.getSize() + 1;
  for (let block = 0; block < state.getBlockNum(); block++) {
    const cells: number[] = [];
    const pairs: number[] = [];
    for (const cell of state.getBlock(block)) {
      if (state.getCandCountOfCell(cell) === 2) {
        const candidates = state.getCandidateList(cell);
        cells.push(cell);
        pairs.push(candidates[0]! * divisor + candidates[1]!);
      }
    }
    for (let first = 0; first < cells.length; first++) {
      for (let second = 0; second < first; second++) {
        if (pairs[first] !== pairs[second]) continue;
        const p1 = Math.trunc(pairs[first]! / divisor);
        const p2 = pairs[first]! % divisor;
        let point = 0;
        for (let value = 1; value <= state.getSize(); value++) {
          if (value === p1 || value === p2) continue;
          point = add32(point, points.get(cells[first]!, value).point);
          point = add32(point, points.get(cells[second]!, value).point);
        }
        for (const cell of state.getBlock(block)) {
          if (cell === cells[first] || cell === cells[second]) continue;
          if (lowerPoint(state, points, cell, p1, point)) updated = true;
          if (lowerPoint(state, points, cell, p2, point)) updated = true;
        }
      }
    }
  }
  return updated;
}

function hiddenPair(state: Status, points: CandidateTable): boolean {
  let updated = false;
  for (let block = 0; block < state.getBlockNum(); block++) {
    const values: number[] = [];
    const firstCells: number[] = [];
    const secondCells: number[] = [];
    for (let value = 1; value <= state.getSize(); value++) {
      if (state.getCandCountOfBlock(block, value) !== 2) continue;
      const cells = state.getCellListHaveCandidateNumberInBlock(block, value);
      if (cells.length !== 2) continue;
      values.push(value);
      firstCells.push(cells[0]!);
      secondCells.push(cells[1]!);
    }
    for (let first = 0; first < values.length; first++) {
      for (let second = 0; second < first; second++) {
        if (
          firstCells[first] !== firstCells[second] ||
          secondCells[first] !== secondCells[second]
        ) continue;
        const cell1 = firstCells[first]!;
        const cell2 = secondCells[first]!;
        let point = 0;
        for (const cell of state.getBlock(block)) {
          if (cell === cell1 || cell === cell2) continue;
          point = add32(point, points.get(cell, values[first]!).point);
          point = add32(point, points.get(cell, values[second]!).point);
        }
        for (const cell of [cell1, cell2]) {
          for (let value = 1; value <= state.getSize(); value++) {
            if (value === values[first] || value === values[second]) continue;
            if (lowerPoint(state, points, cell, value, point)) updated = true;
          }
        }
      }
    }
  }
  return updated;
}

function nakedTriple(state: Status, points: CandidateTable): boolean {
  let updated = false;
  for (let block = 0; block < state.getBlockNum(); block++) {
    const cells: number[] = [];
    const masks: number[] = [];
    for (const cell of state.getBlock(block)) {
      if (state.getCandCountOfCell(cell) <= 3) {
        let mask = 0;
        for (const value of state.getCandidateList(cell)) mask |= 1 << value;
        cells.push(cell);
        masks.push(mask);
      }
    }
    for (let first = 0; first < cells.length; first++) {
      for (let second = 0; second < first; second++) {
        for (let third = 0; third < second; third++) {
          const mask = masks[first]! | masks[second]! | masks[third]!;
          if (bit.numberOf1Bits(mask) !== 3) continue;
          const values: number[] = [];
          for (let value = 1; value <= state.getSize(); value++) {
            if ((mask & (1 << value)) !== 0) values.unshift(value);
          }
          let point = 0;
          for (let value = 1; value <= state.getSize(); value++) {
            if (values.includes(value)) continue;
            point = add32(point, points.get(cells[first]!, value).point);
            point = add32(point, points.get(cells[second]!, value).point);
            point = add32(point, points.get(cells[third]!, value).point);
          }
          for (const cell of state.getBlock(block)) {
            if (cell === cells[first] || cell === cells[second] || cell === cells[third]) {
              continue;
            }
            for (const value of values) {
              if (lowerPoint(state, points, cell, value, point)) updated = true;
            }
          }
        }
      }
    }
  }
  return updated;
}

function hiddenTriple(state: Status, points: CandidateTable): boolean {
  let updated = false;
  for (let block = 0; block < state.getBlockNum(); block++) {
    const values: number[] = [];
    const masks: number[] = [];
    for (let value = 1; value <= state.getSize(); value++) {
      if (state.getCandCountOfBlock(block, value) <= 3) {
        let mask = 0;
        for (const index of state.getCellIndexListHavingCandidateNumberInBlock(block, value)) {
          mask |= 1 << index;
        }
        values.push(value);
        masks.push(mask);
      }
    }
    for (let first = 0; first < values.length; first++) {
      for (let second = 0; second < first; second++) {
        for (let third = 0; third < second; third++) {
          const mask = masks[first]! | masks[second]! | masks[third]!;
          if (bit.numberOf1Bits(mask) !== 3) continue;
          const cells: number[] = [];
          for (let index = 0; index < state.getSize(); index++) {
            if ((mask & (1 << index)) !== 0) {
              cells.unshift(state.getBlock(block)[index]!);
            }
          }
          let point = 0;
          for (const cell of state.getBlock(block)) {
            if (cells.includes(cell)) continue;
            point = add32(point, points.get(cell, values[first]!).point);
            point = add32(point, points.get(cell, values[second]!).point);
            point = add32(point, points.get(cell, values[third]!).point);
          }
          for (const cell of cells) {
            for (let value = 1; value <= state.getSize(); value++) {
              if (
                value === values[first] ||
                value === values[second] ||
                value === values[third]
              ) continue;
              if (lowerPoint(state, points, cell, value, point)) updated = true;
            }
          }
        }
      }
    }
  }
  return updated;
}

function xWing(state: Status, points: CandidateTable): boolean {
  let updated = false;
  const size = state.getSize();
  const divisor = size * size + 1;
  for (let value = 1; value <= size; value++) {
    const pairs: number[] = [];
    for (let block = 0; block < size * 2; block++) {
      if (state.getCandCountOfBlock(block, value) !== 2) {
        pairs.push(-1);
      } else {
        const cells = state.getCellListHaveCandidateNumberInBlock(block, value);
        pairs.push(cells[0]! * divisor + cells[1]!);
      }
    }
    for (let first = 0; first < size; first++) {
      if (pairs[first]! <= 0) continue;
      for (let second = first + 1; second < size; second++) {
        if (pairs[second]! <= 0) continue;
        let a1 = Math.trunc(pairs[first]! / divisor);
        let a2 = pairs[first]! % divisor;
        let b1 = Math.trunc(pairs[second]! / divisor);
        let b2 = pairs[second]! % divisor;
        if (Math.trunc(a1 / size) > Math.trunc(a2 / size)) [a1, a2] = [a2, a1];
        if (Math.trunc(b1 / size) > Math.trunc(b2 / size)) [b1, b2] = [b2, b1];
        let point = 0;
        for (const cell of state.getBlock(first)) {
          if (cell !== a1 && cell !== a2) point = add32(point, points.get(cell, value).point);
        }
        for (const cell of state.getBlock(second)) {
          if (cell !== b1 && cell !== b2) point = add32(point, points.get(cell, value).point);
        }
        if (
          Math.trunc(a1 / size) === Math.trunc(b1 / size) &&
          Math.trunc(a2 / size) === Math.trunc(b2 / size)
        ) {
          for (const [block, x, y] of [
            [Math.trunc(a1 / size) + size, a1, b1],
            [Math.trunc(a2 / size) + size, a2, b2],
          ] as const) {
            for (const cell of state.getBlock(block)) {
              if (cell !== x && cell !== y && lowerPoint(state, points, cell, value, point)) {
                updated = true;
              }
            }
          }
        }
      }
    }
    for (let first = size; first < size * 2; first++) {
      if (pairs[first]! <= 0) continue;
      for (let second = first + 1; second < size * 2; second++) {
        if (pairs[second]! <= 0) continue;
        let a1 = Math.trunc(pairs[first]! / divisor);
        let a2 = pairs[first]! % divisor;
        let b1 = Math.trunc(pairs[second]! / divisor);
        let b2 = pairs[second]! % divisor;
        if (a1 % size > a2 % size) [a1, a2] = [a2, a1];
        if (b1 % size > b2 % size) [b1, b2] = [b2, b1];
        let point = 0;
        for (const cell of state.getBlock(first)) {
          if (cell !== a1 && cell !== a2) point = add32(point, points.get(cell, value).point);
        }
        for (const cell of state.getBlock(second)) {
          if (cell !== b1 && cell !== b2) point = add32(point, points.get(cell, value).point);
        }
        if (a1 % size === b1 % size && a2 % size === b2 % size) {
          for (const [block, x, y] of [[a1 % size, a1, b1], [a2 % size, a2, b2]] as const) {
            for (const cell of state.getBlock(block)) {
              if (cell !== x && cell !== y && lowerPoint(state, points, cell, value, point)) {
                updated = true;
              }
            }
          }
        }
      }
    }
  }
  return updated;
}

function swordfish(state: Status, points: CandidateTable): boolean {
  let updated = false;
  const size = state.getSize();
  for (let value = 1; value <= size; value++) {
    const masks: number[] = [];
    for (let block = 0; block < size * 2; block++) {
      if (state.getCandCountOfBlock(block, value) > 3) {
        masks.push(-1);
      } else {
        let mask = 0;
        for (const index of state.getCellIndexListHavingCandidateNumberInBlock(block, value)) {
          mask |= 1 << index;
        }
        masks.push(mask);
      }
    }
    for (let first = 0; first < size; first++) {
      if (masks[first]! <= 0) continue;
      for (let second = first + 1; second < size; second++) {
        if (masks[second]! <= 0) continue;
        for (let third = second + 1; third < size; third++) {
          if (masks[third]! <= 0) continue;
          const mask = masks[first]! | masks[second]! | masks[third]!;
          if (bit.numberOf1Bits(mask) !== 3) continue;
          const targetBlocks: number[] = [];
          for (let index = 0; index < size; index++) {
            if ((mask & (1 << index)) !== 0) targetBlocks.unshift(index + size);
          }
          let point = 0;
          for (const source of [first, second, third]) {
            for (const cell of state.getBlock(source)) {
              const row = Math.trunc(cell / size);
              if (!targetBlocks.includes(row + size)) {
                point = add32(point, points.get(cell, value).point);
              }
            }
          }
          for (const target of targetBlocks) {
            for (const cell of state.getBlock(target)) {
              const column = cell % size;
              if (
                column !== first &&
                column !== second &&
                column !== third &&
                lowerPoint(state, points, cell, value, point)
              ) updated = true;
            }
          }
        }
      }
    }
    for (let first = size; first < size * 2; first++) {
      if (masks[first]! <= 0) continue;
      for (let second = first + 1; second < size * 2; second++) {
        if (masks[second]! <= 0) continue;
        for (let third = second + 1; third < size * 2; third++) {
          if (masks[third]! <= 0) continue;
          const mask = masks[first]! | masks[second]! | masks[third]!;
          if (bit.numberOf1Bits(mask) !== 3) continue;
          const targetBlocks: number[] = [];
          for (let index = 0; index < size; index++) {
            if ((mask & (1 << index)) !== 0) targetBlocks.unshift(index);
          }
          let point = 0;
          for (const source of [first, second, third]) {
            for (const cell of state.getBlock(source)) {
              if (!targetBlocks.includes(cell % size)) {
                point = add32(point, points.get(cell, value).point);
              }
            }
          }
          for (const target of targetBlocks) {
            for (const cell of state.getBlock(target)) {
              const row = Math.trunc(cell / size) + size;
              if (
                row !== first &&
                row !== second &&
                row !== third &&
                lowerPoint(state, points, cell, value, point)
              ) updated = true;
            }
          }
        }
      }
    }
  }
  return updated;
}

let previousNumber = -1;
let minimumCandidate = 1 << 28;
let minimumCell = -1;
let minimumNumber = -1;

function countUniqueBlockWithWeight(state: Status, points: CandidateTable): number {
  let result = 0;
  const size = state.getSize();
  for (let block = 0; block < state.getBlockNum(); block++) {
    for (let value = 1; value <= size; value++) {
      if (state.getCandCountOfBlock(block, value) !== 1) continue;
      let valid = false;
      let cell = -1;
      for (const candidateCell of state.getBlock(block)) {
        if (state.isCand(candidateCell, value) && state.isEmptyCell(candidateCell)) valid = true;
      }
      if (!valid) continue;
      let point = 0;
      for (const candidateCell of state.getBlock(block)) {
        if (!state.isCand(candidateCell, value)) {
          point += points.get(candidateCell, value).point;
        } else {
          cell = candidateCell;
        }
      }
      if (value === previousNumber) point /= 2.0;
      if (state.isVHBlock(block)) {
        point *= UNIQUE_BLOCK;
        result += 1.0 / point;
        if (minimumCandidate > point) {
          minimumCandidate = Math.trunc(point);
          minimumCell = cell;
          minimumNumber = value;
        }
      } else {
        point *= UNIQUE_LINE;
        result += 1.0 / point;
        if (minimumCandidate * Math.sqrt(size) > point) {
          minimumCandidate = Math.trunc(point / Math.sqrt(size));
          minimumCell = cell;
          minimumNumber = value;
        }
      }
    }
  }
  return result;
}

function countUniqueCellWithWeight(state: Status, points: CandidateTable): number {
  let result = 0;
  const size = state.getSize();
  for (let cell = 0; cell < size * size; cell++) {
    if (!state.isEmptyCell(cell) || state.getCandCountOfCell(cell) !== 1) continue;
    let point = 0;
    let value = -1;
    for (let candidate = 1; candidate <= size; candidate++) {
      if (!state.isCand(cell, candidate)) point += points.get(cell, candidate).point;
      else value = candidate;
    }
    if (value === previousNumber) point /= 2.0;
    point *= UNIQUE_CELL;
    result += 1.0 / point;
    if (minimumCandidate > point) {
      minimumCandidate = Math.trunc(point);
      minimumCell = cell;
      minimumNumber = value;
    }
  }
  return result;
}

function evaluateStatus(state: Status, points: CandidateTable): number {
  let result = 0;
  previousNumber = -1;
  while (state.getSpaceCount() > 0) {
    minimumCandidate = 1 << 28;
    minimumCell = -1;
    minimumNumber = -1;
    const found =
      countUniqueBlockWithWeight(state, points) +
      countUniqueCellWithWeight(state, points);
    if (found > 1e-8) result += state.getSpaceCount() / found;
    if (minimumCell >= 0 && minimumNumber >= 1) {
      previousNumber = minimumNumber;
      addNumber(state, minimumCell, minimumNumber, points);
    }
    if (found < 1e-9) {
      if (localization(state, points)) continue;
      if (nakedPair(state, points)) {
        hiddenPair(state, points);
        xWing(state, points);
        continue;
      }
      if (hiddenPair(state, points)) {
        xWing(state, points);
        continue;
      }
      if (xWing(state, points)) continue;
      if (nakedTriple(state, points)) {
        hiddenTriple(state, points);
        swordfish(state, points);
        continue;
      }
      if (hiddenTriple(state, points)) {
        swordfish(state, points);
        continue;
      }
      if (swordfish(state, points)) continue;
      console.log("NOT SOLVED");
      break;
    }
  }
  return result;
}

export function evaluate(
  numberSize: number,
  block: BlockConstraint,
  cells: number[],
): number {
  const state = new Status(numberSize, block);
  const points = new CandidateTable(state.getCellSize(), state.getSize() + 1);
  for (let cell = 0; cell < state.getCellSize(); cell++) {
    if (cells[cell]! > 0) addNumber(state, cell, cells[cell]!, points);
  }
  return evaluateStatus(state, points);
}
