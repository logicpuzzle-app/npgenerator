/*
 * Copyright (C) 2007 Time Intermedia Corporation <puzzle@timedia.co.jp>
 * TypeScript rewrite derived from NPGenerator V2.0.2.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import { readFile, writeFile } from "node:fs/promises";

export interface NumberPlaceFile {
  numSize: number;
  hint: number[];
  hasHint: boolean;
  hidden: number[];
  answer: number[];
  problem: number[];
  blockArray?: number[];
  groupArrays: number[][];
  seed?: number[];
  comment?: string;
  vertical: boolean;
  horizontal: boolean;
  diagonal: boolean;
  defaultBlock: boolean;
  difficult: number;
}

function attribute(source: string, name: string): string | undefined {
  const match = new RegExp(
    `(?:^|\\s)${name}\\s*=\\s*(?:"([^"]*)"|'([^']*)')`,
    "i",
  ).exec(source);
  return match?.[1] ?? match?.[2];
}

function element(xml: string, name: string): { attributes: string; text: string } | undefined {
  const match = new RegExp(
    `<${name}\\b([^>]*)>([\\s\\S]*?)<\\/${name}\\s*>`,
    "i",
  ).exec(xml);
  if (match === null) return undefined;
  return { attributes: match[1]!, text: match[2]! };
}

function elements(xml: string, name: string): Array<{ attributes: string; text: string }> {
  const result: Array<{ attributes: string; text: string }> = [];
  const expression = new RegExp(
    `<${name}\\b([^>]*)>([\\s\\S]*?)<\\/${name}\\s*>`,
    "gi",
  );
  for (let match = expression.exec(xml); match !== null; match = expression.exec(xml)) {
    result.push({ attributes: match[1]!, text: match[2]! });
  }
  return result;
}

function decodeXmlText(value: string): string {
  return value.replace(
    /&(?:#(\d+)|#x([0-9a-f]+)|lt|gt|amp|quot|apos);/gi,
    (entity, decimal: string | undefined, hexadecimal: string | undefined) => {
      if (decimal !== undefined) return String.fromCodePoint(Number(decimal));
      if (hexadecimal !== undefined) {
        return String.fromCodePoint(Number.parseInt(hexadecimal, 16));
      }
      switch (entity.toLowerCase()) {
        case "&lt;": return "<";
        case "&gt;": return ">";
        case "&amp;": return "&";
        case "&quot;": return "\"";
        case "&apos;": return "'";
        default: return entity;
      }
    },
  );
}

function escapeXmlText(value: string): string {
  return value.replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;");
}

function parseIntArray(value: string | undefined, length: number): number[] {
  const result = Array(length).fill(0) as number[];
  if (value === undefined || value.trim() === "") return result;
  const tokens = decodeXmlText(value).trim().split(/\s+/);
  for (let index = 0; index < Math.min(tokens.length, length); index++) {
    const token = tokens[index]!;
    if (!/^[+-]?\d+$/.test(token)) {
      throw new Error(`invalid integer in XML: ${token}`);
    }
    const parsed = Number(token);
    if (!Number.isSafeInteger(parsed) || parsed < -2147483648 || parsed > 2147483647) {
      throw new Error(`invalid integer in XML: ${token}`);
    }
    result[index] = parsed;
  }
  return result;
}

export async function readNumberPlaceFile(path: string): Promise<NumberPlaceFile> {
  const xml = await readFile(path, "utf8");
  const root = /<problem\b([^>]*)>/i.exec(xml);
  if (root === null) throw new Error(`${path}: root element must be <problem>`);
  const sizeText = attribute(root[1]!, "size");
  if (sizeText === undefined || !/^[+-]?\d+$/.test(sizeText)) {
    throw new Error(`${path}: invalid problem size`);
  }
  const numSize = Number(sizeText);
  if (!Number.isInteger(numSize) || numSize < 2 || numSize > 25) {
    throw new Error(`${path}: size must be between 2 and 25`);
  }
  const cells = numSize * numSize;
  const question = element(xml, "question");
  const constraint = element(xml, "constraint");
  if (constraint === undefined) throw new Error(`${path}: missing <constraint>`);
  const groups = elements(constraint.text, "group");
  const hintElement = element(xml, "hint");
  const seedElement = element(xml, "seed");
  const commentElement = element(xml, "comment");
  const difficultText = question === undefined
    ? undefined
    : attribute(question.attributes, "difficult");
  let difficult = -1;
  if (difficultText !== undefined && /^[+-]?\d+$/.test(difficultText)) {
    const parsed = Number(difficultText);
    if (
      Number.isSafeInteger(parsed) &&
      parsed >= -2147483648 &&
      parsed <= 2147483647
    ) {
      difficult = parsed;
    }
  }
  const defaultBlock = attribute(constraint.attributes, "default-block") === "on";
  const groupArrays = groups.map((group) => parseIntArray(group.text, cells));
  const vertical = attribute(constraint.attributes, "vertical");
  const horizontal = attribute(constraint.attributes, "horizonal");
  return {
    numSize,
    problem: parseIntArray(question?.text, cells),
    hint: parseIntArray(hintElement?.text, cells)
      .map((value) => value === 0 ? 0 : 1),
    hasHint: hintElement !== undefined,
    hidden: parseIntArray(element(xml, "hidden")?.text, cells),
    answer: parseIntArray(element(xml, "answer")?.text, cells),
    ...(defaultBlock || groupArrays.length === 0
      ? {}
      : { blockArray: [...groupArrays[0]!] }),
    groupArrays,
    ...(seedElement === undefined
      ? {}
      : { seed: parseIntArray(seedElement.text, cells) }),
    ...(commentElement === undefined
      ? {}
      : { comment: decodeXmlText(commentElement.text) }),
    vertical: vertical === undefined || vertical === "on",
    horizontal: horizontal === undefined || horizontal === "on",
    diagonal: attribute(constraint.attributes, "diagonal") === "on",
    defaultBlock,
    difficult,
  };
}

function requireLength(
  values: number[] | undefined,
  length: number,
  name: string,
): void {
  if (values !== undefined && values.length !== length) {
    throw new Error(`${name} must contain ${length} cells`);
  }
}

export function toXmlString(file: NumberPlaceFile): string {
  if (file.numSize < 2 || file.numSize > 25) {
    throw new Error("size must be between 2 and 25");
  }
  const cells = file.numSize * file.numSize;
  requireLength(file.problem, cells, "problem");
  requireLength(file.answer, cells, "answer");
  requireLength(file.hidden, cells, "hidden");
  requireLength(file.hint, cells, "hint");
  requireLength(file.seed, cells, "seed");
  if (!file.defaultBlock) requireLength(file.blockArray, cells, "block array");

  let xml = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n";
  xml += `<problem size="${file.numSize}" name="Number Place" author="Number Place Generator">`;
  xml += `<question difficult="${file.difficult}">${file.problem.join(" ")}</question>`;
  xml += `<constraint default-block="${file.defaultBlock ? "on" : "off"}"`;
  xml += ` diagonal="${file.diagonal ? "on" : "off"}">`;
  if (!file.vertical) xml = xml.slice(0, -1) + " vertical=\"off\">";
  if (!file.horizontal) xml = xml.slice(0, -1) + " horizonal=\"off\">";
  if (!file.defaultBlock) {
    if (file.blockArray === undefined) {
      throw new Error(`block array must contain ${cells} cells`);
    }
    xml += `<group block="on">${file.blockArray.join(" ")}</group>`;
  }
  xml += "</constraint>";
  xml += `<answer>${file.answer.join(" ")}</answer>`;
  xml += `<hint>${file.hint.map((value) => value === 0 ? 0 : 1).join(" ")}</hint>`;
  xml += `<hidden>${file.hidden.join(" ")}</hidden>`;
  if (file.comment !== undefined) {
    xml += `<comment>${escapeXmlText(file.comment)}</comment>`;
  }
  xml += "</problem>\n";
  return xml;
}

export async function writeNumberPlaceFile(
  path: string | undefined,
  file: NumberPlaceFile,
): Promise<void> {
  const xml = toXmlString(file);
  if (path === undefined) process.stdout.write(xml);
  else await writeFile(path, xml, "utf8");
}
