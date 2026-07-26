/*
 * Copyright (C) 2007 Time Intermedia Corporation <puzzle@timedia.co.jp>
 * TypeScript rewrite derived from NPGenerator V2.0.2.
 * Director: Hirofumi Fujiwara / Puzzler: Naoki Inaba / Programmer: Masaya Kiwada
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

const MULTIPLIER = 0x5deece66dn;
const ADDEND = 0xbn;
const MASK = (1n << 48n) - 1n;

/** java.util.Random-compatible 48-bit LCG. */
export class JavaRandom {
  private state = 0n;

  constructor(seed: bigint | number) {
    this.setSeed(seed);
  }

  setSeed(seed: bigint | number): void {
    this.state = (BigInt(seed) ^ MULTIPLIER) & MASK;
  }

  next(bits: number): number {
    if (bits < 0 || bits > 32) throw new RangeError("bits must be between 0 and 32");
    this.state = (this.state * MULTIPLIER + ADDEND) & MASK;
    return Number(this.state >> BigInt(48 - bits));
  }

  nextInt(bound: number): number {
    if (!Number.isInteger(bound) || bound <= 0 || bound > 0x7fffffff) {
      throw new RangeError("bound must be positive");
    }
    if ((bound & -bound) === bound) {
      return Number((BigInt(bound) * BigInt(this.next(31))) >> 31n);
    }
    let bits: number;
    let value: number;
    do {
      bits = this.next(31);
      value = bits % bound;
      // Reproduce signed Java int overflow in the rejection test.
    } while (((bits - value + (bound - 1)) | 0) < 0);
    return value;
  }

  shuffle<T>(values: T[]): void {
    for (let i = values.length; i > 1; i--) {
      const j = this.nextInt(i);
      [values[i - 1], values[j]] = [values[j]!, values[i - 1]!];
    }
  }
}
