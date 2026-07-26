/*
 * Copyright (C) 2007 Time Intermedia Corporation <puzzle@timedia.co.jp>
 * TypeScript rewrite derived from NPGenerator V2.0.2.
 * Director: Hirofumi Fujiwara / Puzzler: Naoki Inaba / Programmer: Masaya Kiwada
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import { readFile } from "node:fs/promises";

export const SIZE = 9;
export const CELL_COUNT = SIZE * SIZE;

function parseCell(token: string, pattern: boolean, size: number): number {
  if (token === "-" || token === "0") return 0;
  if (pattern && token.toUpperCase() === "X") return 1;
  if (/^[+-]?\d+$/.test(token)) {
    const value = Number(token);
    if (Number.isSafeInteger(value) && value >= 1 && value <= size) {
      return pattern ? 1 : value;
    }
  }
  throw new Error(`invalid cell: ${token}`);
}

export async function readGrid(
  path: string,
  size: number,
  pattern: boolean,
): Promise<number[]> {
  const rows: number[][] = [];
  const input = await readFile(path, "utf8");
  for (const line of input.split(/\r?\n/)) {
    const trimmed = line.trim();
    if (trimmed === "" || trimmed.startsWith("#")) continue;
    const tokens = trimmed.split(/\s+/);
    if (tokens.length !== size) continue;
    try {
      rows.push(tokens.map((token) => parseCell(token, pattern, size)));
    } catch {
      continue;
    }
    if (rows.length === size) break;
  }
  if (rows.length !== size) {
    throw new Error(`${path}: expected ${size} grid rows, found ${rows.length}`);
  }
  return rows.flat();
}

export async function readBlockArray(path: string, size: number): Promise<number[]> {
  const rows: number[][] = [];
  const input = await readFile(path, "utf8");
  for (const line of input.split(/\r?\n/)) {
    const trimmed = line.trim();
    if (trimmed === "" || trimmed.startsWith("#")) continue;
    const tokens = trimmed.split(/\s+/);
    if (tokens.length !== size) {
      throw new Error(`${path}: every block row must contain ${size} values`);
    }
    const row = tokens.map((token) => {
      if (!/^[+-]?\d+$/.test(token)) {
        throw new Error(`${path}: invalid block label: ${token}`);
      }
      const value = Number(token);
      if (!Number.isSafeInteger(value) || value < -2147483648 || value > 2147483647) {
        throw new Error(`${path}: invalid block label: ${token}`);
      }
      return value;
    });
    rows.push(row);
  }
  if (rows.length !== size) {
    throw new Error(`${path}: expected ${size} block rows, found ${rows.length}`);
  }
  return rows.flat();
}

export function formatGrid(grid: number[], size: number, pattern: boolean): string {
  if (grid.length !== size * size) {
    throw new Error(`grid must contain exactly ${size * size} cells`);
  }
  const lines: string[] = [];
  for (let row = 0; row < size; row++) {
    lines.push(
      grid.slice(row * size, (row + 1) * size)
        .map((value) => pattern ? (value === 0 ? "-" : "X") : String(value))
        .join(" "),
    );
  }
  return `${lines.join("\n")}\n`;
}
