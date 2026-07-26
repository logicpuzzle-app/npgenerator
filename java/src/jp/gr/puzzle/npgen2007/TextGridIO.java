/*
 * Copyright (C) 2007 Time Intermedia Corporation <puzzle@timedia.co.jp>
 * Java 17 reference rewrite derived from NPGenerator V2.0.2.
 *
 * Number Place Generator Version 2.0
 * Director: Hirofumi Fujiwara / Puzzler: Naoki Inaba
 * Programmer: Masaya Kiwada
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package jp.gr.puzzle.npgen2007;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class TextGridIO {
    public static final int SIZE = 9;
    public static final int CELL_COUNT = SIZE * SIZE;

    private TextGridIO() {
    }

    public static int[] readProblem(Path path) throws IOException {
        return readProblem(path, SIZE);
    }

    public static int[] readProblem(Path path, int size) throws IOException {
        return readGrid(path, size, false);
    }

    public static int[] readPattern(Path path) throws IOException {
        return readPattern(path, SIZE);
    }

    public static int[] readPattern(Path path, int size) throws IOException {
        return readGrid(path, size, true);
    }

    public static int[] readBlockArray(Path path, int size) throws IOException {
        return readIntegerGrid(path, size, "block");
    }

    private static int[] readGrid(Path path, int size, boolean pattern) throws IOException {
        List<int[]> rows = new ArrayList<>();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            String[] tokens = trimmed.split("\\s+");
            if (tokens.length != size) {
                continue; // permits the headers in the original data/*.txt files
            }
            int[] row = new int[size];
            boolean gridLine = true;
            for (int index = 0; index < size; index++) {
                try {
                    row[index] = parseCell(tokens[index], pattern, size);
                } catch (IllegalArgumentException error) {
                    gridLine = false;
                    break;
                }
            }
            if (gridLine) {
                rows.add(row);
                if (rows.size() == size) {
                    break; // original files may contain several named boards
                }
            }
        }
        if (rows.size() != size) {
            throw new IllegalArgumentException(
                    path + ": expected " + size + " grid rows, found " + rows.size());
        }
        int[] result = new int[size * size];
        for (int row = 0; row < size; row++) {
            System.arraycopy(rows.get(row), 0, result, row * size, size);
        }
        return result;
    }

    private static int[] readIntegerGrid(Path path, int size, String kind)
            throws IOException {
        List<int[]> rows = new ArrayList<>();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            String[] tokens = trimmed.split("\\s+");
            if (tokens.length != size) {
                throw new IllegalArgumentException(
                        path + ": every " + kind + " row must contain " + size + " values");
            }
            int[] row = new int[size];
            for (int index = 0; index < size; index++) {
                try {
                    row[index] = Integer.parseInt(tokens[index]);
                } catch (NumberFormatException error) {
                    throw new IllegalArgumentException(
                            path + ": invalid " + kind + " label: " + tokens[index]);
                }
            }
            rows.add(row);
        }
        if (rows.size() != size) {
            throw new IllegalArgumentException(
                    path + ": expected " + size + " " + kind
                            + " rows, found " + rows.size());
        }
        int[] result = new int[size * size];
        for (int row = 0; row < size; row++) {
            System.arraycopy(rows.get(row), 0, result, row * size, size);
        }
        return result;
    }

    private static int parseCell(String token, boolean pattern, int size) {
        if (token.equals("-") || token.equals("0")) {
            return 0;
        }
        if (pattern && token.equalsIgnoreCase("X")) {
            return 1;
        }
        if (!pattern) {
            try {
                int value = Integer.parseInt(token);
                if (value >= 1 && value <= size) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
                // Report the common invalid-cell error below.
            }
        } else {
            try {
                int value = Integer.parseInt(token);
                if (value >= 1 && value <= size) {
                    return 1;
                }
            } catch (NumberFormatException ignored) {
                // Report the common invalid-cell error below.
            }
        }
        throw new IllegalArgumentException("invalid cell: " + token);
    }

    public static int[][] toRows(int[] grid) {
        int size = squareSize(grid);
        return toRows(grid, size);
    }

    public static int[][] toRows(int[] grid, int size) {
        requireGrid(grid, size);
        int[][] rows = new int[size][size];
        for (int row = 0; row < size; row++) {
            System.arraycopy(grid, row * size, rows[row], 0, size);
        }
        return rows;
    }

    public static String formatGrid(int[] grid, boolean pattern) {
        return formatGrid(grid, squareSize(grid), pattern);
    }

    public static String formatGrid(int[] grid, int size, boolean pattern) {
        requireGrid(grid, size);
        StringBuilder output = new StringBuilder();
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                if (column > 0) {
                    output.append(' ');
                }
                int value = grid[row * size + column];
                output.append(pattern ? (value == 0 ? "-" : "X") : Integer.toString(value));
            }
            output.append('\n');
        }
        return output.toString();
    }

    public static void writeGrid(Path path, int[] grid, boolean pattern) throws IOException {
        writeGrid(path, grid, squareSize(grid), pattern);
    }

    public static void writeGrid(Path path, int[] grid, int size, boolean pattern)
            throws IOException {
        Files.writeString(path, formatGrid(grid, size, pattern), StandardCharsets.UTF_8);
    }

    private static int squareSize(int[] grid) {
        if (grid == null) {
            throw new IllegalArgumentException("grid must not be null");
        }
        int size = (int) Math.sqrt(grid.length);
        if (size * size != grid.length) {
            throw new IllegalArgumentException("grid length must be a perfect square");
        }
        return size;
    }

    private static void requireGrid(int[] grid, int size) {
        if (grid == null || grid.length != size * size) {
            throw new IllegalArgumentException(
                    "grid must contain exactly " + (size * size) + " cells");
        }
    }
}
