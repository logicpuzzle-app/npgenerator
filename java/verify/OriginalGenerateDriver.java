package jp.gr.puzzle.npv2.core;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public final class OriginalGenerateDriver {
    private static final int SIZE = 9;

    public static void main(String[] args) throws Exception {
        String mode = args[0];
        if (mode.equals("blocksplit")) {
            int size = Integer.parseInt(args[1]);
            ReferenceRandom.reset(Long.parseLong(args[2]));
            printArray(new BlockSplit(size, size).splitBlock());
            return;
        }
        long seed = Long.parseLong(args[2]);
        Options options = Options.parse(args, 3);
        ReferenceRandom.reset(seed);
        int[] pattern;
        if (mode.equals("generate")) {
            pattern = OriginalSolveDriver.readGrid(Path.of(args[1]), options.size, true);
            if (!generate(pattern, false, options)) {
                System.exit(1);
            }
        } else if (mode.equals("random")) {
            int hints = Integer.parseInt(args[1]);
            for (int patternAttempt = 0; patternAttempt < 100; patternAttempt++) {
                pattern = randomPattern(options.size, hints);
                if (generate(pattern, true, options)) {
                    return;
                }
            }
            System.exit(1);
        } else {
            throw new IllegalArgumentException("generate|random");
        }
    }

    private static boolean generate(
            int[] pattern, boolean printPattern, Options options) {
        BlockConstraint block = makeBlock(options);
        Generator generator =
                new Generator(options.size, pattern.clone(),
                        new int[options.size * options.size], block);
        SolverMethod method = options.method;
        generator.setMethod(method);
        generator.setForbidden(options.forbidden);
        for (int attempt = 0; attempt < 100; attempt++) {
            int[] problem = generator.generate();
            if (problem == null) {
                continue;
            }
            double difficulty = Evaluator.evaluate(options.size, block, problem);
            if (difficulty < options.dpMin || options.dpMax < difficulty) {
                continue;
            }
            Status status = new Status(options.size, block);
            status.setUniqueMethod(method.unique);
            for (int index = 0; index < problem.length; index++) {
                if (problem[index] > 0) {
                    Solver.addNumber(status, index, problem[index]);
                }
            }
            status = Solver.answer(status, method);
            if (printPattern) {
                System.out.println("PATTERN");
                OriginalSolveDriver.printGrid(pattern, options.size, true);
            }
            System.out.println("PROBLEM");
            OriginalSolveDriver.printGrid(problem, options.size, false);
            OriginalSolveDriver.printSolve(status.getCell(), options.size, difficulty);
            return true;
        }
        return false;
    }

    private static int[] randomPattern(int hints) {
        return randomPattern(SIZE, hints);
    }

    private static int[] randomPattern(int size, int hints) {
        int[] pattern = new int[size * size];
        for (int count = 0; count < hints; ) {
            int x = ReferenceRandom.bounded(size);
            int y = ReferenceRandom.bounded(size);
            if (size % 2 != 0 && x == size / 2 && y == size / 2) {
                continue;
            }
            if (pattern[y * size + x] != 0) {
                continue;
            }
            pattern[y * size + x] = 1;
            pattern[(size - 1 - x) * size + y] = 1;
            pattern[(size - 1 - y) * size + (size - 1 - x)] = 1;
            pattern[x * size + (size - 1 - y)] = 1;
            count += 4;
        }
        return pattern;
    }

    private static BlockConstraint makeBlock(Options options) {
        ArrayList<Integer[]> groups = new ArrayList<>();
        Utility.addBlockVertical(groups, options.size);
        Utility.addBlockHorizontal(groups, options.size);
        if (options.diagonal) {
            Utility.addBlockDiagonal(groups, options.size);
        }
        if (options.blocks.equals("random")) {
            int[] labels = normalize(
                    options.size, new BlockSplit(options.size, options.size).splitBlock());
            Utility.addBlockByArray(Utility.int2integer(labels), groups, options.size);
        } else if (options.blocks.startsWith("@")) {
            try {
                int[] labels = OriginalSolveDriver.readGrid(
                        Path.of(options.blocks.substring(1)), options.size, false);
                Utility.addBlockByArray(
                        Utility.int2integer(normalize(options.size, labels)),
                        groups, options.size);
            } catch (Exception error) {
                throw new IllegalArgumentException(error);
            }
        } else {
            String[] dimensions = options.blocks.split("x");
            Utility.addBlockRectangle(
                    Integer.parseInt(dimensions[0]),
                    Integer.parseInt(dimensions[1]), groups, options.size);
        }
        return new BlockConstraint(groups, options.size);
    }

    private static int[] normalize(int size, int[] labels) {
        Map<Integer, Integer> map = new LinkedHashMap<>();
        int[] result = new int[labels.length];
        for (int index = 0; index < labels.length; index++) {
            Integer value = map.get(labels[index]);
            if (value == null) {
                value = map.size() + 1;
                map.put(labels[index], value);
            }
            result[index] = value;
        }
        return result;
    }

    private static void printArray(int[] values) {
        for (int index = 0; index < values.length; index++) {
            if (index > 0) {
                System.out.print(' ');
            }
            System.out.print(values[index]);
        }
        System.out.println();
    }

    private static final class Options {
        final SolverMethod method;
        final int dpMin;
        final int dpMax;
        final int forbidden;
        final int size;
        final String blocks;
        final boolean diagonal;

        private Options(
                SolverMethod method, int dpMin, int dpMax, int forbidden,
                int size, String blocks, boolean diagonal) {
            this.method = method;
            this.dpMin = dpMin;
            this.dpMax = dpMax;
            this.forbidden = forbidden;
            this.size = size;
            this.blocks = blocks;
            this.diagonal = diagonal;
        }

        static Options parse(String[] args, int start) {
            SolverMethod method = new SolverMethod();
            method.setAllUse();
            String use = value(args, start, "--use");
            if (use != null) {
                method.localization = false;
                method.nakedPair = false;
                method.hiddenPair = false;
                method.nakedTriple = false;
                method.hiddenTriple = false;
                method.XWing = false;
                method.swordfish = false;
                for (String name : use.split(",")) {
                    switch (name) {
                        case "none" -> {
                        }
                        case "localization" -> method.localization = true;
                        case "naked-pair" -> method.nakedPair = true;
                        case "hidden-pair" -> method.hiddenPair = true;
                        case "naked-triple" -> method.nakedTriple = true;
                        case "hidden-triple" -> method.hiddenTriple = true;
                        case "x-wing" -> method.XWing = true;
                        case "swordfish" -> method.swordfish = true;
                        default -> throw new IllegalArgumentException(name);
                    }
                }
            }
            String unique = value(args, start, "--unique");
            if (unique != null) {
                method.unique.vhUnique = false;
                method.unique.cellUnique = false;
                method.unique.blockUnique = false;
                for (String name : unique.split(",")) {
                    switch (name) {
                        case "none" -> {
                        }
                        case "vh" -> method.unique.vhUnique = true;
                        case "cell" -> method.unique.cellUnique = true;
                        case "block" -> method.unique.blockUnique = true;
                        default -> throw new IllegalArgumentException(name);
                    }
                }
            }
            int lower = Math.max(integer(args, start, "--dp-min", 0), 0);
            int upper = integer(args, start, "--dp-max", Integer.MAX_VALUE);
            if (upper < 0) {
                upper = Integer.MAX_VALUE;
            }
            if (lower > upper) {
                int swap = lower;
                lower = upper;
                upper = swap;
            }
            int size = integer(args, start, "--size", SIZE);
            String blocks = value(args, start, "--blocks");
            if (blocks == null) {
                int square = Utility.sqrt(size);
                blocks = square + "x" + square;
            }
            return new Options(
                    method, lower, upper, integer(args, start, "--forbidden", -1),
                    size, blocks, flag(args, start, "--diagonal"));
        }

        private static int integer(
                String[] args, int start, String name, int defaultValue) {
            String value = value(args, start, name);
            return value == null ? defaultValue : Integer.parseInt(value);
        }

        private static String value(String[] args, int start, String name) {
            String value = null;
            for (int index = start; index < args.length; index++) {
                if (args[index].equals("--diagonal")) {
                    continue;
                }
                if (args[index].equals(name)) {
                    value = args[index + 1];
                }
                index++;
            }
            return value;
        }

        private static boolean flag(String[] args, int start, String name) {
            for (int index = start; index < args.length; index++) {
                if (args[index].equals(name)) {
                    return true;
                }
            }
            return false;
        }
    }
}
