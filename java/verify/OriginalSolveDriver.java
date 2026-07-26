package jp.gr.puzzle.npv2.core;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class OriginalSolveDriver {
    private static final int SIZE = 9;

    public static void main(String[] args) throws Exception {
        int[] problem = readGrid(Path.of(args[0]), false);
        BlockConstraint block =
                new BlockConstraint(Utility.makeNormalBlock(SIZE, 3, 3), SIZE);
        Status status = new Status(SIZE, block);
        for (int index = 0; index < problem.length; index++) {
            if (problem[index] > 0) {
                Solver.addNumber(status, index, problem[index]);
            }
        }
        SolverMethod method = new SolverMethod();
        method.setAllUse();
        status = Solver.answer(status, method);
        if (status.isNoAnswer()) {
            System.exit(1);
        }
        printSolve(status.getCell(), Evaluator.evaluate(SIZE, block, problem));
    }

    static int[] readGrid(Path path, boolean pattern) throws Exception {
        return readGrid(path, SIZE, pattern);
    }

    static int[] readGrid(Path path, int size, boolean pattern) throws Exception {
        List<Integer> cells = new ArrayList<>();
        int rows = 0;
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            String[] tokens = line.trim().split("\\s+");
            if (tokens.length != size) {
                continue;
            }
            int[] parsed = new int[size];
            boolean valid = true;
            for (int index = 0; index < size; index++) {
                String token = tokens[index];
                if (token.equals("-") || token.equals("0")) {
                    parsed[index] = 0;
                } else if (pattern && token.equalsIgnoreCase("X")) {
                    parsed[index] = 1;
                } else {
                    try {
                        int value = Integer.parseInt(token);
                        if (value < 1 || value > size) {
                            valid = false;
                            break;
                        }
                        parsed[index] = pattern ? 1 : value;
                    } catch (NumberFormatException error) {
                        valid = false;
                        break;
                    }
                }
            }
            if (valid) {
                for (int value : parsed) {
                    cells.add(value);
                }
                if (++rows == size) {
                    break;
                }
            }
        }
        if (cells.size() != size * size) {
            throw new IllegalArgumentException("invalid " + size + "x" + size + " grid: " + path);
        }
        return cells.stream().mapToInt(Integer::intValue).toArray();
    }

    static void printGrid(int[] grid, boolean pattern) {
        printGrid(grid, SIZE, pattern);
    }

    static void printGrid(int[] grid, int size, boolean pattern) {
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                if (column > 0) {
                    System.out.print(' ');
                }
                int value = grid[row * size + column];
                System.out.print(pattern ? (value == 0 ? "-" : "X") : value);
            }
            System.out.println();
        }
    }

    static void printSolve(int[] solution, double difficulty) {
        printSolve(solution, SIZE, difficulty);
    }

    static void printSolve(int[] solution, int size, double difficulty) {
        System.out.println("SOLUTION");
        printGrid(solution, size, false);
        System.out.println("DIFFICULTY " + Double.toString(difficulty));
    }
}
