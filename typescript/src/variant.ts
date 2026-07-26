/*
 * Copyright (C) 2007 Time Intermedia Corporation <puzzle@timedia.co.jp>
 * TypeScript rewrite derived from NPGenerator V2.0.2.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import { BlockConstraint, sqrt } from "./core.js";
import { readBlockArray } from "./grid.js";
import { JavaRandom } from "./random.js";
import type { NumberPlaceFile } from "./xml.js";

export const DEFAULT_SIZE = 9;
export const MIN_SIZE = 2;
export const MAX_SIZE = 25;

export interface Variant {
  size: number;
  block: BlockConstraint;
  blockArray: number[];
  vertical: boolean;
  horizontal: boolean;
  diagonal: boolean;
  defaultBlock: boolean;
}

class ProblemBuilder {
  vertical = true;
  horizontal = true;
  diagonal = false;
  defaultBlock = true;
  rectangleWidth = -1;
  rectangleHeight = -1;
  readonly groups: number[][] = [];

  constructor(readonly size: number) {}

  addGroup(labels: number[]): void {
    this.groups.push(labels);
  }

  build(diagonalLast = false): number[][] {
    const blocks: number[][] = [];
    if (this.vertical) addVertical(blocks, this.size);
    if (this.horizontal) addHorizontal(blocks, this.size);
    if (this.diagonal && !diagonalLast) addDiagonal(blocks, this.size);
    if (this.defaultBlock) {
      const square = sqrt(this.size);
      addRectangle(blocks, this.size, square, square);
    } else if (this.rectangleWidth > 0 && this.rectangleHeight > 0) {
      addRectangle(
        blocks,
        this.size,
        this.rectangleWidth,
        this.rectangleHeight,
      );
    }
    for (const labels of this.groups) addByArray(blocks, labels, this.size);
    if (this.diagonal && diagonalLast) addDiagonal(blocks, this.size);
    return blocks;
  }
}

function addVertical(blocks: number[][], size: number): void {
  for (let column = 0; column < size; column++) {
    blocks.push(Array.from({ length: size }, (_, row) => row * size + column));
  }
}

function addHorizontal(blocks: number[][], size: number): void {
  for (let row = 0; row < size; row++) {
    blocks.push(Array.from({ length: size }, (_, column) => row * size + column));
  }
}

function addDiagonal(blocks: number[][], size: number): void {
  blocks.push(Array.from({ length: size }, (_, index) => index * size + index));
  blocks.push(
    Array.from(
      { length: size },
      (_, index) => (size - 1 - index) * size + index,
    ),
  );
}

function addRectangle(
  blocks: number[][],
  size: number,
  width: number,
  height: number,
): void {
  for (let column = 0; column < size; column += width) {
    for (let row = 0; row < size; row += height) {
      const block: number[] = [];
      for (let x = 0; x < width; x++) {
        for (let y = 0; y < height; y++) {
          block.push((row + y) * size + column + x);
        }
      }
      blocks.push(block);
    }
  }
}

function addByArray(blocks: number[][], labels: number[], size: number): void {
  const labelToIndex = new Map<number, number>();
  for (const label of labels) {
    if (label !== 0 && !labelToIndex.has(label)) {
      labelToIndex.set(label, labelToIndex.size);
    }
  }
  const groups = Array.from({ length: labelToIndex.size }, () => [] as number[]);
  for (let cell = 0; cell < labels.length; cell++) {
    const label = labels[cell]!;
    if (label !== 0) groups[labelToIndex.get(label)!]!.push(cell);
  }
  for (const group of groups) {
    if (group.length !== size) {
      throw new Error(`every block must contain exactly ${size} cells`);
    }
    blocks.push(group);
  }
}

class DisjointSet {
  private readonly parent: number[];

  constructor(size: number) {
    this.parent = Array.from({ length: size }, (_, index) => index);
  }

  find(value: number): number {
    const parent = this.parent[value]!;
    if (parent === value) return value;
    const root = this.find(parent);
    this.parent[value] = root;
    return root;
  }

  union(first: number, second: number): void {
    this.parent[this.find(first)] = this.find(second);
  }

  isSameGroup(first: number, second: number): boolean {
    return this.find(first) === this.find(second);
  }
}

/** Exact port of the original core/BlockSplit traversal and random draws. */
class BlockSplit {
  private verticalWall: boolean[][] = [];
  private horizontalWall: boolean[][] = [];

  constructor(
    private readonly width: number,
    private readonly height: number,
    private readonly random: JavaRandom,
  ) {}

  private index(x: number, y: number, width: number): number {
    return y * width + x;
  }

  private generateMaze(width: number, height: number): void {
    this.verticalWall = Array.from(
      { length: width + 1 },
      () => Array(height + 1).fill(false) as boolean[],
    );
    this.horizontalWall = Array.from(
      { length: width + 1 },
      () => Array(height + 1).fill(false) as boolean[],
    );
    const groups = new DisjointSet(width * height);
    const edges: Array<[number, number]> = [];
    for (let x = 0; x < width; x++) {
      for (let y = 0; y < height; y++) {
        this.verticalWall[x]![y] = true;
        this.horizontalWall[x]![y] = true;
        const cell = this.index(x, y, width);
        if (x + 1 < width) edges.push([cell, this.index(x + 1, y, width)]);
        if (y + 1 < height) edges.push([cell, this.index(x, y + 1, width)]);
      }
    }
    this.random.shuffle(edges);
    let remaining = width * height - 1;
    for (const [first, second] of edges) {
      if (remaining <= 0) break;
      const firstX = first % width;
      const firstY = Math.trunc(first / width);
      const secondX = second % width;
      const secondY = Math.trunc(second / width);
      if (groups.isSameGroup(first, second)) continue;
      groups.union(first, second);
      if (firstX === secondX) {
        this.verticalWall[firstX]![firstY] = false;
      } else if (firstY === secondY) {
        this.horizontalWall[firstX]![firstY] = false;
      }
      remaining--;
    }
  }

  private isMovable(px: number, py: number, qx: number, qy: number): boolean {
    if (px === qx) {
      if (py > qy) return this.isMovable(px, qy, qx, py);
      if (py + 1 !== qy) return false;
      if ((py & 1) === 0) return true;
      return !this.verticalWall[Math.trunc(px / 2)]![Math.trunc(py / 2)];
    }
    if (py === qy) {
      if (px > qx) return this.isMovable(qx, py, px, qy);
      if (px + 1 !== qx) return false;
      if ((px & 1) === 0) return true;
      return !this.horizontalWall[Math.trunc(px / 2)]![Math.trunc(py / 2)];
    }
    return false;
  }

  private walkMaze(): number[] {
    const dx = [0, 1, 0, -1];
    const dy = [1, 0, -1, 0];
    let x = this.random.nextInt(Math.trunc(this.width / 2)) * 2;
    let y = this.random.nextInt(Math.trunc(this.height / 2)) * 2;
    let direction = 0;
    const result = Array(this.width * this.height).fill(-1) as number[];
    let id = 0;
    let count = 0;
    let yet = true;
    while (yet) {
      yet = false;
      result[this.index(x, y, this.width)] = id;
      count++;
      if (count === this.width) {
        id++;
        count = 0;
      }
      for (let index = 3; index < 7; index++) {
        const nextDirection = (index + direction) % 4;
        const nextX = x + dx[nextDirection]!;
        const nextY = y + dy[nextDirection]!;
        if (
          this.width % 2 !== 0 &&
          this.height % 2 !== 0 &&
          nextX === this.width - 1 &&
          nextY === this.height - 1 &&
          count !== 0 &&
          result[this.index(nextX, nextX, this.width)] === -1
        ) {
          // The original intentionally uses nextX for both coordinates here.
          result[this.index(nextX, nextX, this.width)] = id;
          count++;
          if (count === this.width) {
            id++;
            count = 0;
          }
        }
        if (
          nextX >= 0 && nextX < this.width &&
          nextY >= 0 && nextY < this.height &&
          this.isMovable(x, y, nextX, nextY) &&
          result[this.index(nextX, nextY, this.width)] === -1
        ) {
          yet = true;
          direction = nextDirection;
          x = nextX;
          y = nextY;
          break;
        }
      }
    }
    return result;
  }

  splitBlock(): number[] {
    const width = Math.trunc(this.width / 2);
    const height = Math.trunc(this.height / 2);
    this.generateMaze(width, height);
    if (this.width % 2 !== 0) {
      for (let index = 0; index < height; index++) {
        this.verticalWall[width]![index] = true;
        this.horizontalWall[width - 1]![index] = false;
      }
    }
    if (this.height % 2 !== 0) {
      for (let index = 0; index < width; index++) {
        this.verticalWall[index]![height - 1] = false;
        this.horizontalWall[index]![height] = true;
      }
    }
    return this.walkMaze();
  }
}

export function rectangleBlockArray(
  size: number,
  width: number,
  height: number,
): number[] {
  const labels = Array(size * size).fill(0) as number[];
  const blocksAcross = Math.trunc(size / width);
  for (let row = 0; row < size; row++) {
    for (let column = 0; column < size; column++) {
      labels[row * size + column] =
        Math.trunc(row / height) * blocksAcross + Math.trunc(column / width) + 1;
    }
  }
  return labels;
}

function normalizeBlockArray(size: number, labels: number[]): number[] {
  if (labels.length !== size * size) {
    throw new Error(`block grid must contain exactly ${size * size} cells`);
  }
  const normalizedLabels = new Map<number, number>();
  const result: number[] = [];
  const counts = Array(size).fill(0) as number[];
  for (const label of labels) {
    let normalized = normalizedLabels.get(label);
    if (normalized === undefined) {
      if (normalizedLabels.size === size) {
        throw new Error(`block grid must contain exactly ${size} blocks`);
      }
      normalized = normalizedLabels.size + 1;
      normalizedLabels.set(label, normalized);
    }
    result.push(normalized);
    counts[normalized - 1]!++;
  }
  if (normalizedLabels.size !== size) {
    throw new Error(`block grid must contain exactly ${size} blocks`);
  }
  if (counts.some((count) => count !== size)) {
    throw new Error(`every block must contain exactly ${size} cells`);
  }
  return result;
}

function finishVariant(
  builder: ProblemBuilder,
  blockArray: number[],
  defaultBlock: boolean,
  xmlOrder: boolean,
): Variant {
  return {
    size: builder.size,
    block: new BlockConstraint(builder.build(xmlOrder), builder.size),
    blockArray: [...blockArray],
    vertical: builder.vertical,
    horizontal: builder.horizontal,
    diagonal: builder.diagonal,
    defaultBlock,
  };
}

function baseBuilder(
  size: number,
  vertical: boolean,
  horizontal: boolean,
  diagonal: boolean,
): ProblemBuilder {
  const builder = new ProblemBuilder(size);
  builder.vertical = vertical;
  builder.horizontal = horizontal;
  builder.diagonal = diagonal;
  return builder;
}

function buildArrayVariant(
  size: number,
  labels: number[],
  vertical: boolean,
  horizontal: boolean,
  diagonal: boolean,
  xmlOrder: boolean,
): Variant {
  const normalized = normalizeBlockArray(size, labels);
  const builder = baseBuilder(size, vertical, horizontal, diagonal);
  builder.defaultBlock = false;
  builder.addGroup(normalized);
  return finishVariant(builder, normalized, false, xmlOrder);
}

export async function buildVariant(
  size: number,
  blockSpec: string | undefined,
  vertical: boolean,
  horizontal: boolean,
  diagonal: boolean,
  random: JavaRandom,
  xmlOrder: boolean,
): Promise<Variant> {
  requireSize(size);
  if (blockSpec === undefined) {
    const square = sqrt(size);
    if (square * square !== size) {
      throw new Error("--blocks is required when --size is not a perfect square");
    }
    const builder = baseBuilder(size, vertical, horizontal, diagonal);
    builder.defaultBlock = true;
    return finishVariant(
      builder,
      rectangleBlockArray(size, square, square),
      true,
      xmlOrder,
    );
  }
  if (blockSpec === "random") {
    return buildArrayVariant(
      size,
      new BlockSplit(size, size, random).splitBlock(),
      vertical,
      horizontal,
      diagonal,
      xmlOrder,
    );
  }
  if (blockSpec.startsWith("@")) {
    if (blockSpec.length === 1) {
      throw new Error("--blocks @file requires a file name");
    }
    return buildArrayVariant(
      size,
      await readBlockArray(blockSpec.slice(1), size),
      vertical,
      horizontal,
      diagonal,
      xmlOrder,
    );
  }
  const dimensions = blockSpec.toLowerCase().split("x");
  if (dimensions.length !== 2) {
    throw new Error("--blocks must be WxH, random, or @file.txt");
  }
  if (
    !/^[+-]?\d+$/.test(dimensions[0]!) ||
    !/^[+-]?\d+$/.test(dimensions[1]!)
  ) {
    throw new Error("--blocks WxH requires integer dimensions");
  }
  const width = Number(dimensions[0]);
  const height = Number(dimensions[1]);
  if (
    !Number.isSafeInteger(width) ||
    !Number.isSafeInteger(height) ||
    width <= 0 ||
    height <= 0 ||
    width * height !== size ||
    size % width !== 0 ||
    size % height !== 0
  ) {
    throw new Error("--blocks WxH requires W*H == size");
  }
  const builder = baseBuilder(size, vertical, horizontal, diagonal);
  builder.defaultBlock = false;
  builder.rectangleWidth = width;
  builder.rectangleHeight = height;
  return finishVariant(
    builder,
    rectangleBlockArray(size, width, height),
    false,
    xmlOrder,
  );
}

export function buildXmlVariant(
  source: NumberPlaceFile,
  forceDiagonal: boolean,
  noVertical: boolean,
  noHorizontal: boolean,
): Variant {
  const size = source.numSize;
  requireSize(size);
  const vertical = source.vertical && !noVertical;
  const horizontal = source.horizontal && !noHorizontal;
  const diagonal = source.diagonal || forceDiagonal;
  const builder = baseBuilder(size, vertical, horizontal, diagonal);
  let blockArray: number[];
  if (source.defaultBlock) {
    const square = sqrt(size);
    if (square * square !== size) {
      throw new Error("XML default-block requires a perfect-square size");
    }
    builder.defaultBlock = true;
    blockArray = rectangleBlockArray(size, square, square);
  } else {
    if (source.groupArrays.length === 0) {
      throw new Error("XML custom block constraint is missing <group>");
    }
    builder.defaultBlock = false;
    blockArray = [...source.groupArrays[0]!];
  }
  for (const group of source.groupArrays) builder.addGroup(group);
  return finishVariant(builder, blockArray, source.defaultBlock, true);
}

export function requireSize(size: number): void {
  if (size < MIN_SIZE || size > MAX_SIZE) {
    throw new Error("--size must be between 2 and 25");
  }
}
