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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Scanner;

public class ProblemBuilder {
    boolean vertical = true;
    boolean horizontal = true;
    boolean diagonal = false;
    boolean defaultBlock = true;
    int rectangleWidth = -1;
    int rectangleHeight = -1;
    int[] cell;
    int numSize;
    ArrayList<Integer[]> group = new ArrayList<>();

    private void addBlockVertical(ProblemContent content) {
        Utility.addBlockVertical(content.getBlock(), numSize);
    }

    private void addBlockHorizontal(ProblemContent content) {
        Utility.addBlockHorizontal(content.getBlock(), numSize);
    }

    public void addBlockRectangle(int width, int height, ProblemContent content) {
        Utility.addBlockRectangle(width, height, content.getBlock(), numSize);
    }

    public void addBlockVerticalAndHorizontal(ProblemContent content) {
        addBlockVertical(content);
        addBlockHorizontal(content);
    }

    public void addBlockDiagonal(ProblemContent content) {
        Utility.addBlockDiagonal(content.getBlock(), numSize);
    }

    public void addBlockByArray(Integer[] array, ProblemContent content) {
        Utility.addBlockByArray(array, content.getBlock(), numSize);
    }

    public void addGroup(Integer[] array) {
        group.add(array);
    }

    public ProblemContent build() {
        return build(false);
    }

    public ProblemContent buildXmlOrder() {
        return build(true);
    }

    private ProblemContent build(boolean diagonalLast) {
        ProblemContent problem = new ProblemContent(numSize, cell);
        if (vertical) {
            addBlockVertical(problem);
        }
        if (horizontal) {
            addBlockHorizontal(problem);
        }
        if (diagonal && !diagonalLast) {
            addBlockDiagonal(problem);
        }
        if (defaultBlock) {
            int square = Utility.sqrt(numSize);
            addBlockRectangle(square, square, problem);
        } else if (rectangleWidth > 0 && rectangleHeight > 0) {
            addBlockRectangle(rectangleWidth, rectangleHeight, problem);
        }
        for (Integer[] array : group) {
            addBlockByArray(array, problem);
        }
        if (diagonal && diagonalLast) {
            addBlockDiagonal(problem);
        }
        return problem;
    }

    public static ProblemContent generateSudoku(int[][] grid) {
        int size = grid.length;
        int block = (int) (Math.sqrt(size) + 1e-10);
        int[] values = new int[size * size];
        for (int row = 0, index = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                values[index++] = grid[row][column];
            }
        }
        ProblemContent content = new ProblemContent(size, values);
        content.addBlockVerticalAndHorizonal();
        content.addBlockRectangle(block, block);
        return content;
    }

    LinkedList<Integer> toList(String value) {
        LinkedList<Integer> list = new LinkedList<>();
        try (Scanner scanner = new Scanner(value)) {
            while (scanner.hasNext()) {
                list.add(scanner.nextInt());
            }
        }
        return list;
    }

    public static ProblemContent loadText(String filename) throws IOException {
        return generateSudoku(TextGridIO.toRows(TextGridIO.readProblem(Path.of(filename))));
    }
}
