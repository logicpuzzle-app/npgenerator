/*
 * Copyright (C) 2007 Time Intermedia Corporation <puzzle@timedia.co.jp>
 * TypeScript rewrite derived from NPGenerator V2.0.2.
 * Director: Hirofumi Fujiwara / Puzzler: Naoki Inaba / Programmer: Masaya Kiwada
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import { bit, KindOfAnswer, SolverMethod, Status } from "./core.js";

export function swordfish(state: Status): boolean {
  let updated = false;
  const size = state.getSize();
  const pairs = new Int32Array(size * 2);
  const bitCounts = state.block.bitCounts;
  for (let number = 1; number <= size; number++) {
    for (let block = 0; block < size * 2; block++) {
      pairs[block] = state.getCandCountOfBlock(block, number) > 3
        ? -1
        : state.getCandidatePositionMask(block, number);
    }
    for (let first = 0; first < size; first++) {
      if (pairs[first]! <= 0) continue;
      for (let second = first + 1; second < size; second++) {
        if (pairs[second]! <= 0) continue;
        for (let third = second + 1; third < size; third++) {
          if (pairs[third]! <= 0) continue;
          let union = pairs[first]! | pairs[second]! | pairs[third]!;
          if (
            (bitCounts === undefined ? bit.numberOf1Bits(union) : bitCounts[union]!) !== 3
          ) continue;
          const excluded = (1 << first) | (1 << second) | (1 << third);
          while (union !== 0) {
            const position = 31 - Math.clz32(union);
            const group = position + size;
            const cells = state.getBlock(group);
            let targets = state.getCandidatePositionMask(group, number) & ~excluded;
            while (targets !== 0) {
              const targetBit = targets & -targets;
              if (removeCand(state, cells[bit.ntz(targetBit)]!, number)) updated = true;
              targets ^= targetBit;
            }
            union &= ~(1 << position);
          }
        }
      }
    }
    for (let first = size; first < size * 2; first++) {
      if (pairs[first]! <= 0) continue;
      for (let second = first + 1; second < size * 2; second++) {
        if (pairs[second]! <= 0) continue;
        for (let third = second + 1; third < size * 2; third++) {
          if (pairs[third]! <= 0) continue;
          let union = pairs[first]! | pairs[second]! | pairs[third]!;
          if (
            (bitCounts === undefined ? bit.numberOf1Bits(union) : bitCounts[union]!) !== 3
          ) continue;
          const excluded =
            (1 << (first - size)) |
            (1 << (second - size)) |
            (1 << (third - size));
          while (union !== 0) {
            const position = 31 - Math.clz32(union);
            const cells = state.getBlock(position);
            let targets = state.getCandidatePositionMask(position, number) & ~excluded;
            while (targets !== 0) {
              const targetBit = targets & -targets;
              if (removeCand(state, cells[bit.ntz(targetBit)]!, number)) updated = true;
              targets ^= targetBit;
            }
            union &= ~(1 << position);
          }
        }
      }
    }
  }
  return updated;
}

export function xWing(state: Status): boolean {
  let updated = false;
  const size = state.getSize();
  const divisor = size * size + 1;
  const pairs = new Int32Array(size * 2);
  const rows = state.block.rowOfCell;
  const columns = state.block.columnOfCell;
  for (let number = 1; number <= size; number++) {
    for (let block = 0; block < size * 2; block++) {
      if (state.getCandCountOfBlock(block, number) !== 2) {
        pairs[block] = -1;
        continue;
      }
      let positions = state.getCandidatePositionMask(block, number);
      const firstPosition = bit.ntz(positions);
      positions &= positions - 1;
      const secondPosition = bit.ntz(positions);
      const cells = state.getBlock(block);
      pairs[block] = cells[firstPosition]! * divisor + cells[secondPosition]!;
    }
    for (let first = 0; first < size; first++) {
      if (pairs[first]! <= 0) continue;
      for (let second = first + 1; second < size; second++) {
        if (pairs[second]! <= 0) continue;
        const a1 = (pairs[first]! / divisor) | 0;
        const a2 = pairs[first]! % divisor;
        const b1 = (pairs[second]! / divisor) | 0;
        const b2 = pairs[second]! % divisor;
        const row1 = rows[a1]!;
        const row2 = rows[a2]!;
        if (row1 !== rows[b1] || row2 !== rows[b2]) continue;
        let cells = state.getBlock(row1 + size);
        for (let index = 0; index < cells.length; index++) {
          const cell = cells[index]!;
          if (cell !== a1 && cell !== b1 && removeCand(state, cell, number)) updated = true;
        }
        cells = state.getBlock(row2 + size);
        for (let index = 0; index < cells.length; index++) {
          const cell = cells[index]!;
          if (cell !== a2 && cell !== b2 && removeCand(state, cell, number)) updated = true;
        }
      }
    }
    for (let first = size; first < size * 2; first++) {
      if (pairs[first]! <= 0) continue;
      for (let second = first + 1; second < size * 2; second++) {
        if (pairs[second]! <= 0) continue;
        const a1 = (pairs[first]! / divisor) | 0;
        const a2 = pairs[first]! % divisor;
        const b1 = (pairs[second]! / divisor) | 0;
        const b2 = pairs[second]! % divisor;
        const column1 = columns[a1]!;
        const column2 = columns[a2]!;
        if (column1 !== columns[b1] || column2 !== columns[b2]) continue;
        let cells = state.getBlock(column1);
        for (let index = 0; index < cells.length; index++) {
          const cell = cells[index]!;
          if (cell !== a1 && cell !== b1 && removeCand(state, cell, number)) updated = true;
        }
        cells = state.getBlock(column2);
        for (let index = 0; index < cells.length; index++) {
          const cell = cells[index]!;
          if (cell !== a2 && cell !== b2 && removeCand(state, cell, number)) updated = true;
        }
      }
    }
  }
  return updated;
}

export function nakedTriple(state: Status): boolean {
  let updated = false;
  const size = state.getSize();
  const ids = new Int32Array(size);
  const masks = new Int32Array(size);
  const bitCounts = state.block.bitCounts;
  for (let block = 0; block < state.getBlockNum(); block++) {
    const blockCells = state.getBlock(block);
    let count = 0;
    for (let index = 0; index < blockCells.length; index++) {
      const cell = blockCells[index]!;
      if (state.getCandCountOfCell(cell) <= 3) {
        ids[count] = cell;
        masks[count] = state.getCandidateMask(cell) << 1;
        count++;
      }
    }
    for (let first = 0; first < count; first++) {
      for (let second = 0; second < first; second++) {
        for (let third = 0; third < second; third++) {
          const union = masks[first]! | masks[second]! | masks[third]!;
          if (
            (bitCounts === undefined ? bit.numberOf1Bits(union) : bitCounts[union >> 1]!) !== 3
          ) continue;
          for (let cellIndex = 0; cellIndex < blockCells.length; cellIndex++) {
            const cell = blockCells[cellIndex]!;
            if (cell === ids[first] || cell === ids[second] || cell === ids[third]) continue;
            let values = union & (state.getCandidateMask(cell) << 1);
            while (values !== 0) {
              const number = 31 - Math.clz32(values);
              if (removeCand(state, cell, number)) updated = true;
              values &= ~(1 << number);
            }
          }
        }
      }
    }
  }
  return updated;
}

export function hiddenTriple(state: Status): boolean {
  let updated = false;
  const size = state.getSize();
  const ids = new Int32Array(size);
  const masks = new Int32Array(size);
  const bitCounts = state.block.bitCounts;
  for (let block = 0; block < state.getBlockNum(); block++) {
    const blockCells = state.getBlock(block);
    let count = 0;
    for (let number = 1; number <= size; number++) {
      if (state.getCandCountOfBlock(block, number) <= 3) {
        ids[count] = number;
        masks[count] = state.getCandidatePositionMask(block, number);
        count++;
      }
    }
    for (let first = 0; first < count; first++) {
      for (let second = 0; second < first; second++) {
        for (let third = 0; third < second; third++) {
          let positions = masks[first]! | masks[second]! | masks[third]!;
          if (
            (bitCounts === undefined ? bit.numberOf1Bits(positions) : bitCounts[positions]!) !== 3
          ) continue;
          while (positions !== 0) {
            const position = 31 - Math.clz32(positions);
            const cell = blockCells[position]!;
            if (state.getCandCountOfCell(cell) > 2) {
              for (let number = 1; number <= size; number++) {
                if (
                  number !== ids[first] &&
                  number !== ids[second] &&
                  number !== ids[third] &&
                  removeCand(state, cell, number)
                ) updated = true;
              }
            }
            positions &= ~(1 << position);
          }
        }
      }
    }
  }
  return updated;
}

export function localization(state: Status): boolean {
  let updated = false;
  const details = state.block.intersectionDetails;
  const size = state.getSize();
  const bitCounts = state.block.bitCounts;
  for (let detailIndex = 0; detailIndex < details.length; detailIndex++) {
    const detail = details[detailIndex]!;
    for (let number = 1; number <= size; number++) {
      const firstPositions = state.getCandidatePositionMask(detail.first, number);
      const secondPositions = state.getCandidatePositionMask(detail.second, number);
      const sharedMask = firstPositions & detail.firstMask;
      const shared = bitCounts === undefined
        ? bit.numberOf1Bits(sharedMask)
        : bitCounts[sharedMask]!;
      if (shared === 0) continue;
      let target = -1;
      let commonMask = 0;
      if (
        state.getCandCountOfBlock(detail.first, number) > shared &&
        state.getCandCountOfBlock(detail.second, number) === shared
      ) {
        target = detail.first;
        commonMask = detail.firstMask;
      } else if (
        state.getCandCountOfBlock(detail.first, number) === shared &&
        state.getCandCountOfBlock(detail.second, number) > shared
      ) {
        target = detail.second;
        commonMask = detail.secondMask;
      }
      if (target < 0) continue;
      let targets = state.getCandidatePositionMask(target, number) & ~commonMask;
      const cells = state.getBlock(target);
      while (targets !== 0) {
        const targetBit = targets & -targets;
        if (removeCand(state, cells[bit.ntz(targetBit)]!, number)) updated = true;
        targets ^= targetBit;
      }
    }
  }
  return updated;
}

export function nakedPair(state: Status): boolean {
  let updated = false;
  const size = state.getSize();
  const divisor = size + 1;
  const ids = new Int32Array(size);
  const pairs = new Int32Array(size);
  for (let block = 0; block < state.getBlockNum(); block++) {
    const blockCells = state.getBlock(block);
    let count = 0;
    for (let index = 0; index < blockCells.length; index++) {
      const cell = blockCells[index]!;
      if (state.getCandCountOfCell(cell) !== 2) continue;
      let candidates = state.getCandidateMask(cell);
      const first = bit.ntz(candidates) + 1;
      candidates &= candidates - 1;
      const second = bit.ntz(candidates) + 1;
      ids[count] = cell;
      pairs[count] = first * divisor + second;
      count++;
    }
    for (let first = 0; first < count; first++) {
      for (let second = 0; second < first; second++) {
        if (pairs[first] !== pairs[second]) continue;
        const number1 = (pairs[first]! / divisor) | 0;
        const number2 = pairs[first]! % divisor;
        for (let index = 0; index < blockCells.length; index++) {
          const cell = blockCells[index]!;
          if (cell === ids[first] || cell === ids[second]) continue;
          if (removeCand(state, cell, number1)) updated = true;
          if (removeCand(state, cell, number2)) updated = true;
        }
      }
    }
  }
  return updated;
}

export function hiddenPair(state: Status): boolean {
  let updated = false;
  const size = state.getSize();
  const ids = new Int32Array(size);
  const firstCells = new Int32Array(size);
  const secondCells = new Int32Array(size);
  for (let block = 0; block < state.getBlockNum(); block++) {
    const blockCells = state.getBlock(block);
    let count = 0;
    for (let number = 1; number <= size; number++) {
      if (state.getCandCountOfBlock(block, number) !== 2) continue;
      let positions = state.getCandidatePositionMask(block, number);
      const firstPosition = bit.ntz(positions);
      positions &= positions - 1;
      const secondPosition = bit.ntz(positions);
      ids[count] = number;
      firstCells[count] = blockCells[firstPosition]!;
      secondCells[count] = blockCells[secondPosition]!;
      count++;
    }
    for (let first = 0; first < count; first++) {
      for (let second = 0; second < first; second++) {
        if (
          firstCells[first] !== firstCells[second] ||
          secondCells[first] !== secondCells[second]
        ) continue;
        let cell = firstCells[first]!;
        for (let cellIndex = 0; cellIndex < 2; cellIndex++) {
          if (state.getCandCountOfCell(cell) > 2) {
            for (let number = 1; number <= size; number++) {
              if (
                number !== ids[first] &&
                number !== ids[second] &&
                removeCand(state, cell, number)
              ) updated = true;
            }
          }
          cell = secondCells[first]!;
        }
      }
    }
  }
  return updated;
}

export function removeCand(state: Status, cell: number, number: number): boolean {
  if (!state.deleteCandidate(cell, number)) return false;
  if (
    state.unique.cellUnique &&
    state.getCandCountOfCell(cell) === 1 &&
    state.isEmptyCell(cell)
  ) {
    addNumber(state, cell, state.getUniqueCandidate(cell));
  }
  const memberships = state.block.getWhere(cell);
  for (let index = 0; index < memberships.length; index++) {
    const block = memberships[index]!;
    if (!state.unique.vhUnique && state.isVHBlock(block)) continue;
    if (!state.unique.blockUnique && !state.isVHBlock(block)) continue;
    const positions = state.getCandidatePositionMask(block, number);
    if (positions !== 0 && (positions & (positions - 1)) === 0) {
      addNumber(state, state.getBlock(block)[bit.ntz(positions)]!, number);
    }
  }
  return true;
}

function deleteCandPeer(state: Status, cell: number, number: number): void {
  const size = state.getSize();
  for (let candidate = 1; candidate <= size; candidate++) {
    if (candidate !== number) removeCand(state, cell, candidate);
  }
  const memberships = state.block.getWhere(cell);
  for (let index = 0; index < memberships.length; index++) {
    const peers = state.getBlock(memberships[index]!);
    for (let peerIndex = 0; peerIndex < peers.length; peerIndex++) {
      const peer = peers[peerIndex]!;
      if (cell !== peer) removeCand(state, peer, number);
    }
  }
}

export function addNumber(state: Status, cell: number, number: number): boolean {
  if (!state.assignValue(cell, number)) return false;
  deleteCandPeer(state, cell, number);
  return true;
}

export function answer(state: Status, method: SolverMethod): Status {
  state.unique = method.unique;
  const cells = state.cells();
  for (let cell = 0; cell < cells.length; cell++) {
    const number = cells[cell]!;
    if (number === 0) continue;
    if (!state.isCand(cell, number)) {
      state.setKindOfAnswer(KindOfAnswer.IRREGULAR_PROBLEM);
    }
    deleteCandPeer(state, cell, number);
  }
  if (state.isInvalid()) return state;
  let updated = true;
  while (updated) {
    updated = false;
    if (state.getSpaceCount() === 0) break;
    if (method.localization && !updated) updated = localization(state);
    if (method.nakedPair && !updated) updated = nakedPair(state);
    if (method.hiddenPair && !updated) updated = hiddenPair(state);
    if (method.XWing && !updated) updated = xWing(state);
    if (method.nakedTriple && !updated) updated = nakedTriple(state);
    if (method.hiddenTriple && !updated) updated = hiddenTriple(state);
    if (method.swordfish && !updated) updated = swordfish(state);
    if (state.isNoAnswer()) return state;
  }
  state.setKindOfAnswer(
    state.getSpaceCount() > 0
      ? KindOfAnswer.MULTIPLE_ANSWER
      : KindOfAnswer.UNIQUE_ANSWER,
  );
  return state;
}
