package jp.gr.puzzle.npv2.core;

import java.util.ArrayList;

public final class OriginalXmlFeatureDriver {
    public static void main(String[] args) throws Exception {
        switch (args[0]) {
            case "solve" -> solve(args[1]);
            case "generate" -> generate(args[1], Long.parseLong(args[2]));
            case "blocks" -> printBlocks(
                    Utility.makeBlockConstraint(
                            new NumberPlaceFile(new java.io.File(args[1]))));
            default -> throw new IllegalArgumentException("solve|generate|blocks");
        }
    }

    private static void solve(String filename) {
        ProblemContent content = ProblemBuilder.loadXML(filename);
        int size = content.getNumberSize();
        BlockConstraint block = new BlockConstraint(content.getBlock(), size);
        printSolved(content.getCell(), size, block, allMethods());
    }

    private static void generate(String filename, long randomSeed) {
        ReferenceRandom.reset(randomSeed);
        Generator generator = new Generator(filename);
        SolverMethod method = allMethods();
        generator.setMethod(method);
        for (int attempt = 0; attempt < 100; attempt++) {
            int[] problem = generator.generate();
            if (problem != null) {
                int size = (int) Math.sqrt(generator.getSeed().length);
                System.out.println("PROBLEM");
                OriginalSolveDriver.printGrid(problem, size, false);
                printSolved(
                        problem, size, generator.block, method);
                return;
            }
        }
        System.exit(1);
    }

    private static void printSolved(
            int[] problem, int size, BlockConstraint block, SolverMethod method) {
        Status status = new Status(size, block);
        status.setUniqueMethod(method.unique);
        for (int index = 0; index < problem.length; index++) {
            if (problem[index] > 0) {
                Solver.addNumber(status, index, problem[index]);
            }
        }
        status = Solver.answer(status, method);
        if (status.isNoAnswer()) {
            System.exit(1);
        }
        OriginalSolveDriver.printSolve(
                status.getCell(), size, Evaluator.evaluate(size, block, problem));
    }

    private static SolverMethod allMethods() {
        SolverMethod method = new SolverMethod();
        method.setAllUse();
        return method;
    }

    private static void printBlocks(BlockConstraint constraint) {
        ArrayList<Integer[]> blocks = constraint.getBlock();
        System.out.println("BLOCKS " + blocks.size());
        for (Integer[] block : blocks) {
            System.out.println(Utility.toStringFromArray(block));
        }
    }
}
