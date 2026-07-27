/*
 * Copyright (C) 2007 Time Intermedia Corporation <puzzle@timedia.co.jp>
 * Java 17 reference rewrite derived from NPGenerator V2.0.2.
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package jp.gr.puzzle.npgen2007;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class NpGen {
    private static final int DEFAULT_SIZE = 9;
    private static final int MIN_SIZE = 2;
    private static final int MAX_SIZE = 25;
    private static final Set<String> SOLVE_OPTIONS =
            Set.of("--use", "--unique");
    private static final Set<String> GENERATE_OPTIONS =
            Set.of("--use", "--unique", "--dp-min", "--dp-max", "--attempts");
    private static final Set<String> VARIANT_OPTIONS =
            Set.of("--size", "--blocks", "--seed", "--format", "--out");
    private static final Set<String> VARIANT_FLAGS =
            Set.of("--diagonal", "--no-vertical", "--no-horizontal");

    private NpGen() {
    }

    public static void main(String[] args) {
        int exitCode;
        try {
            exitCode = run(args);
        } catch (IllegalArgumentException | IOException error) {
            System.err.println("input error: " + error.getMessage());
            exitCode = 2;
        }
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int run(String[] args) throws IOException {
        if (args.length == 0) {
            usage();
            return 2;
        }
        return switch (args[0]) {
            case "solve" -> solveCommand(args);
            case "generate" -> generateCommand(args);
            case "random" -> randomCommand(args);
            case "bench" -> benchCommand(args);
            default -> {
                usage();
                yield 2;
            }
        };
    }

    private static int solveCommand(String[] args) throws IOException {
        if (args.length < 2) {
            throw new IllegalArgumentException(
                    "usage: npgen solve <problem> [--size N] [--blocks spec]"
                            + " [--diagonal] [--format xml] [--out file.xml]");
        }
        ParsedOptions parsed = ParsedOptions.parse(
                args, 2, union(SOLVE_OPTIONS, VARIANT_OPTIONS), VARIANT_FLAGS);
        requireXmlFormat(parsed);
        JavaRandom random = new JavaRandom(parsed.longValue("--seed", 0));
        Path inputPath = Path.of(args[1]);
        boolean xmlInput = parsed.has("--format") || isXml(inputPath);

        int[] problem;
        Variant variant;
        NumberPlaceFile source = null;
        if (xmlInput) {
            source = new NumberPlaceFile(inputPath.toFile());
            int size = resolveXmlSize(parsed, source);
            variant = parsed.has("--blocks")
                    ? buildVariant(
                            size, parsed.value("--blocks"),
                            source.isVertical() && !parsed.has("--no-vertical"),
                            source.isHorizontal() && !parsed.has("--no-horizontal"),
                            source.isDiagonal() || parsed.has("--diagonal"), random, true)
                    : buildXmlVariant(
                            source, parsed.has("--diagonal"),
                            parsed.has("--no-vertical"), parsed.has("--no-horizontal"));
            problem = source.getProblem();
        } else {
            int size = parsed.intValue("--size", DEFAULT_SIZE);
            variant = buildVariant(
                    size, parsed.value("--blocks"),
                    !parsed.has("--no-vertical"), !parsed.has("--no-horizontal"),
                    parsed.has("--diagonal"), random, false);
            problem = TextGridIO.readProblem(inputPath, size);
        }
        validateValues(problem, variant.size, false, "problem");

        CommandOptions options = commandOptions(parsed, false, variant.size);
        SolveResult result = solve(problem, options.method, variant);
        if (result.status == Solver.KindOfAnswer.NO_ANSWER
                || result.status == Solver.KindOfAnswer.IRREGULAR_PROBLEM
                || ((parsed.has("--use") || parsed.has("--unique"))
                        && result.status != Solver.KindOfAnswer.UNIQUE_ANSWER)) {
            System.err.println("RESULT " + result.status.name());
            return 1;
        }
        if (xmlOutput(parsed)) {
            int[] hidden = source == null ? new int[variant.size * variant.size]
                    : source.getHidden();
            boolean[] hint = source != null && source.hasHint()
                    ? source.getHint() : Utility.int2boolean(problem);
            writeXml(parsed, xmlFile(
                    variant, hint, hidden, problem,
                    result.solution, result.difficulty, source));
        } else {
            printSolve(result, variant.size);
        }
        return 0;
    }

    private static int generateCommand(String[] args) throws IOException {
        if (args.length < 2) {
            throw new IllegalArgumentException(
                    "usage: npgen generate <pattern> [--seed N] [--size N]"
                            + " [--blocks spec] [--diagonal] [--format xml]"
                            + " [--out file.xml]");
        }
        ParsedOptions parsed = ParsedOptions.parse(
                args, 2,
                union(GENERATE_OPTIONS, VARIANT_OPTIONS, Set.of("--forbidden")),
                VARIANT_FLAGS);
        requireXmlFormat(parsed);
        JavaRandom random = new JavaRandom(parsed.longValue("--seed", 0));
        Path inputPath = Path.of(args[1]);
        boolean xmlInput = parsed.has("--format") || isXml(inputPath);

        int[] pattern;
        int[] hidden;
        int[] initialSeed = null;
        Variant variant;
        NumberPlaceFile source = null;
        if (xmlInput) {
            source = new NumberPlaceFile(inputPath.toFile());
            int size = resolveXmlSize(parsed, source);
            variant = parsed.has("--blocks")
                    ? buildVariant(
                            size, parsed.value("--blocks"),
                            source.isVertical() && !parsed.has("--no-vertical"),
                            source.isHorizontal() && !parsed.has("--no-horizontal"),
                            source.isDiagonal() || parsed.has("--diagonal"), random, true)
                    : buildXmlVariant(
                            source, parsed.has("--diagonal"),
                            parsed.has("--no-vertical"), parsed.has("--no-horizontal"));
            pattern = Utility.boolean2int(source.getHint());
            hidden = source.getHidden();
            initialSeed = source.getSeed();
        } else {
            int size = parsed.intValue("--size", DEFAULT_SIZE);
            variant = buildVariant(
                    size, parsed.value("--blocks"),
                    !parsed.has("--no-vertical"), !parsed.has("--no-horizontal"),
                    parsed.has("--diagonal"), random, false);
            pattern = TextGridIO.readPattern(inputPath, size);
            hidden = new int[size * size];
        }
        validateValues(pattern, variant.size, true, "pattern");
        validateValues(hidden, variant.size, false, "hidden");

        CommandOptions options = commandOptions(parsed, true, variant.size);
        Generated result = generate(
                pattern, hidden, initialSeed, random, options, variant);
        if (result == null) {
            System.err.println(
                    "RESULT GENERATE_FAILED attempts=" + options.attempts);
            return 1;
        }
        if (xmlOutput(parsed)) {
            writeXml(parsed, xmlFile(
                    variant, Utility.int2boolean(pattern), hidden, result.problem,
                    result.solution, result.difficulty, source));
        } else {
            printGenerated(result, variant.size);
        }
        return 0;
    }

    private static int randomCommand(String[] args) throws IOException {
        ParsedOptions parsed = ParsedOptions.parse(
                args, 1,
                union(GENERATE_OPTIONS, VARIANT_OPTIONS,
                        Set.of("--hints", "--forbidden", "--symmetry")),
                VARIANT_FLAGS);
        requireXmlFormat(parsed);
        int size = parsed.intValue("--size", DEFAULT_SIZE);
        int hints = parsed.intValue("--hints", 20);
        Symmetry symmetry = Symmetry.parse(parsed.value("--symmetry"));
        JavaRandom random = new JavaRandom(parsed.longValue("--seed", 0));
        Variant variant = buildVariant(
                size, parsed.value("--blocks"),
                !parsed.has("--no-vertical"), !parsed.has("--no-horizontal"),
                parsed.has("--diagonal"), random, false);
        validateRandomHints(size, hints, symmetry);
        CommandOptions options = commandOptions(parsed, true, size);
        RandomGenerated randomGenerated =
                generateRandom(hints, random, options, variant, symmetry);
        if (randomGenerated == null) {
            System.err.println(
                    "RESULT GENERATE_FAILED attempts=" + options.attempts);
            return 1;
        }
        if (xmlOutput(parsed)) {
            writeXml(parsed, xmlFile(
                    variant, Utility.int2boolean(randomGenerated.pattern),
                    new int[size * size], randomGenerated.generated.problem,
                    randomGenerated.generated.solution,
                    randomGenerated.generated.difficulty, null));
        } else {
            System.out.print("PATTERN\n");
            System.out.print(TextGridIO.formatGrid(randomGenerated.pattern, size, true));
            printGenerated(randomGenerated.generated, size);
        }
        return 0;
    }

    private static int benchCommand(String[] args) {
        ParsedOptions parsed = ParsedOptions.parse(
                args, 1, Set.of("--count", "--seed"), Set.of());
        int count = parsed.intValue("--count", 10);
        long seed = parsed.longValue("--seed", 0);
        if (count <= 0) {
            throw new IllegalArgumentException("--count must be positive");
        }
        JavaRandom random = new JavaRandom(seed);
        Variant variant = buildVariant(
                DEFAULT_SIZE, null, true, true, false, random, false);
        long start = System.nanoTime();
        int succeeded = 0;
        for (int index = 0; index < count; index++) {
            if (generateRandom(
                    20, random,
                    new CommandOptions(
                            allMethods(), 0, Integer.MAX_VALUE, -1, 100),
                    variant, Symmetry.ROT4) != null) {
                succeeded++;
            }
        }
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;
        System.out.println("COUNT " + count);
        System.out.println("SUCCEEDED " + succeeded);
        System.out.println("ELAPSED_MS " + elapsedMillis);
        return succeeded == count ? 0 : 1;
    }

    public static SolveResult solve(int[] problem) {
        Variant variant = buildVariant(
                DEFAULT_SIZE, null, true, true, false, new JavaRandom(0), false);
        return solve(problem, allMethods(), variant);
    }

    public static SolveResult solve(int[] problem, SolverMethod method) {
        Variant variant = buildVariant(
                DEFAULT_SIZE, null, true, true, false, new JavaRandom(0), false);
        return solve(problem, method, variant);
    }

    private static SolveResult solve(
            int[] problem, SolverMethod method, Variant variant) {
        Status status = new Status(variant.size, variant.block);
        status.setUniqueMethod(method.unique);
        for (int cell = 0; cell < problem.length; cell++) {
            if (problem[cell] > 0) {
                Solver.addNumber(status, cell, problem[cell]);
            }
        }
        status = Solver.answer(status, method);
        if (status.isNoAnswer()) {
            return new SolveResult(
                    status.getCell().clone(), Double.NaN, status.getKindOfAnswer());
        }
        double difficulty =
                Evaluator.evaluate(variant.size, variant.block, problem);
        return new SolveResult(
                status.getCell().clone(), difficulty, status.getKindOfAnswer());
    }

    public static Generated generate(int[] pattern, JavaRandom random) {
        Variant variant = buildVariant(
                DEFAULT_SIZE, null, true, true, false, random, false);
        return generate(
                pattern, new int[DEFAULT_SIZE * DEFAULT_SIZE], null, random,
                new CommandOptions(
                        allMethods(), 0, Integer.MAX_VALUE, -1, 100),
                variant);
    }

    private static Generated generate(
            int[] pattern, int[] hidden, int[] initialSeed, JavaRandom random,
            CommandOptions options, Variant variant) {
        Generator generator = new Generator(
                variant.size, pattern.clone(), hidden.clone(), variant.block,
                random, initialSeed);
        generator.setMethod(options.method);
        generator.setForbidden(options.forbidden);
        for (int attempt = 0;
                options.attempts == 0 || attempt < options.attempts;
                attempt++) {
            int[] problem = generator.generate();
            if (problem != null) {
                SolveResult solved = solve(problem, options.method, variant);
                if (solved.difficulty < options.dpMin
                        || options.dpMax < solved.difficulty) {
                    continue;
                }
                return new Generated(
                        problem.clone(), solved.solution, solved.difficulty);
            }
        }
        return null;
    }

    private static RandomGenerated generateRandom(
            int hints, JavaRandom random, CommandOptions options, Variant variant,
            Symmetry symmetry) {
        for (int patternAttempt = 0;
                options.attempts == 0 || patternAttempt < options.attempts;
                patternAttempt++) {
            int[] pattern = randomPattern(variant.size, hints, random, symmetry);
            Generated generated = generate(
                    pattern, new int[variant.size * variant.size], null,
                    random, options, variant);
            if (generated != null) {
                return new RandomGenerated(pattern, generated);
            }
        }
        return null;
    }

    /**
     * Reproduces sample/Random20.java's four-way rotational orbit selection.
     */
    public static int[] random20Pattern(int hints, JavaRandom random) {
        return randomPattern(DEFAULT_SIZE, hints, random);
    }

    public static int[] randomPattern(int size, int hints, JavaRandom random) {
        return randomPattern(size, hints, random, Symmetry.ROT4);
    }

    private static int[] randomPattern(
            int size, int hints, JavaRandom random, Symmetry symmetry) {
        int[] pattern = new int[size * size];
        for (int count = 0; count < hints; ) {
            int x = random.nextInt(size);
            int y = random.nextInt(size);
            if (symmetry.isFixedPoint(size, x, y)) {
                continue;
            }
            if (pattern[y * size + x] != 0) {
                continue;
            }
            pattern[y * size + x] = 1;
            switch (symmetry) {
                case ROT4 -> {
                    pattern[(size - 1 - x) * size + y] = 1;
                    pattern[(size - 1 - y) * size + (size - 1 - x)] = 1;
                    pattern[x * size + (size - 1 - y)] = 1;
                }
                case ROT2 ->
                    pattern[(size - 1 - y) * size + (size - 1 - x)] = 1;
                case MIRROR_H ->
                    pattern[y * size + (size - 1 - x)] = 1;
                case MIRROR_V ->
                    pattern[(size - 1 - y) * size + x] = 1;
                case NONE -> {
                }
            }
            count += symmetry.orbitSize;
        }
        return pattern;
    }

    private static void validateRandomHints(
            int size, int hints, Symmetry symmetry) {
        int maximumHints = symmetry.maximumHints(size);
        if (hints <= 0 || hints > maximumHints
                || hints % symmetry.orbitSize != 0) {
            if (symmetry.orbitSize == 1) {
                throw new IllegalArgumentException(
                        "--hints must be between 1 and " + maximumHints
                                + " for --symmetry " + symmetry.optionName);
            }
            throw new IllegalArgumentException(
                    "--hints must be a positive multiple of "
                            + symmetry.orbitSize + " no greater than "
                            + maximumHints + " for --symmetry "
                            + symmetry.optionName);
        }
    }

    private static Variant buildVariant(
            int size, String blockSpec, boolean vertical, boolean horizontal,
            boolean diagonal, JavaRandom random, boolean xmlOrder) {
        requireSize(size);
        if (blockSpec == null) {
            int square = Utility.sqrt(size);
            if (square * square != size) {
                throw new IllegalArgumentException(
                        "--blocks is required when --size is not a perfect square");
            }
            int[] blockArray = rectangleBlockArray(size, square, square);
            ProblemBuilder builder =
                    baseBuilder(size, vertical, horizontal, diagonal);
            builder.defaultBlock = true;
            return finishVariant(builder, blockArray, true, xmlOrder);
        }
        if (blockSpec.equals("random")) {
            int[] raw = new BlockSplit(size, size, random).splitBlock();
            return buildArrayVariant(
                    size, raw, vertical, horizontal, diagonal, xmlOrder);
        }
        if (blockSpec.startsWith("@")) {
            if (blockSpec.length() == 1) {
                throw new IllegalArgumentException("--blocks @file requires a file name");
            }
            try {
                return buildArrayVariant(
                        size,
                        TextGridIO.readBlockArray(Path.of(blockSpec.substring(1)), size),
                        vertical, horizontal, diagonal, xmlOrder);
            } catch (IOException error) {
                throw new IllegalArgumentException(error.getMessage(), error);
            }
        }
        String[] dimensions = blockSpec.toLowerCase().split("x", -1);
        if (dimensions.length != 2) {
            throw new IllegalArgumentException(
                    "--blocks must be WxH, random, or @file.txt");
        }
        int width;
        int height;
        try {
            width = Integer.parseInt(dimensions[0]);
            height = Integer.parseInt(dimensions[1]);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("--blocks WxH requires integer dimensions");
        }
        if (width <= 0 || height <= 0 || (long) width * height != size
                || size % width != 0 || size % height != 0) {
            throw new IllegalArgumentException("--blocks WxH requires W*H == size");
        }
        ProblemBuilder builder =
                baseBuilder(size, vertical, horizontal, diagonal);
        builder.defaultBlock = false;
        builder.rectangleWidth = width;
        builder.rectangleHeight = height;
        return finishVariant(
                builder, rectangleBlockArray(size, width, height), false, xmlOrder);
    }

    private static Variant buildXmlVariant(
            NumberPlaceFile source, boolean forceDiagonal,
            boolean noVertical, boolean noHorizontal) {
        int size = source.getNumSize();
        requireSize(size);
        boolean vertical = source.isVertical() && !noVertical;
        boolean horizontal = source.isHorizontal() && !noHorizontal;
        boolean diagonal = source.isDiagonal() || forceDiagonal;
        ProblemBuilder builder =
                baseBuilder(size, vertical, horizontal, diagonal);
        int[] blockArray;
        if (source.isDefaultBlock()) {
            int square = Utility.sqrt(size);
            if (square * square != size) {
                throw new IllegalArgumentException(
                        "XML default-block requires a perfect-square size");
            }
            builder.defaultBlock = true;
            blockArray = rectangleBlockArray(size, square, square);
        } else {
            if (source.getGroupArrays().isEmpty()) {
                throw new IllegalArgumentException(
                        "XML custom block constraint is missing <group>");
            }
            builder.defaultBlock = false;
            blockArray = source.getGroupArrays().get(0);
        }
        for (int[] group : source.getGroupArrays()) {
            builder.addGroup(Utility.int2integer(group));
        }
        return finishVariant(builder, blockArray, source.isDefaultBlock(), true);
    }

    private static Variant buildArrayVariant(
            int size, int[] labels, boolean vertical, boolean horizontal,
            boolean diagonal, boolean xmlOrder) {
        int[] normalized = normalizeBlockArray(size, labels);
        ProblemBuilder builder =
                baseBuilder(size, vertical, horizontal, diagonal);
        builder.defaultBlock = false;
        builder.addGroup(Utility.int2integer(normalized));
        return finishVariant(builder, normalized, false, xmlOrder);
    }

    private static ProblemBuilder baseBuilder(
            int size, boolean vertical, boolean horizontal, boolean diagonal) {
        ProblemBuilder builder = new ProblemBuilder();
        builder.numSize = size;
        builder.cell = new int[size * size];
        builder.vertical = vertical;
        builder.horizontal = horizontal;
        builder.diagonal = diagonal;
        return builder;
    }

    private static Variant finishVariant(
            ProblemBuilder builder, int[] blockArray,
            boolean defaultBlock, boolean xmlOrder) {
        ProblemContent content =
                xmlOrder ? builder.buildXmlOrder() : builder.build();
        return new Variant(
                builder.numSize,
                new BlockConstraint(content.getBlock(), builder.numSize),
                blockArray.clone(), builder.vertical, builder.horizontal,
                builder.diagonal, defaultBlock);
    }

    private static int[] rectangleBlockArray(int size, int width, int height) {
        int[] labels = new int[size * size];
        int blocksAcross = size / width;
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                labels[row * size + column] =
                        (row / height) * blocksAcross + column / width + 1;
            }
        }
        return labels;
    }

    private static int[] normalizeBlockArray(int size, int[] labels) {
        if (labels == null || labels.length != size * size) {
            throw new IllegalArgumentException(
                    "block grid must contain exactly " + (size * size) + " cells");
        }
        Map<Integer, Integer> normalizedLabels = new LinkedHashMap<>();
        int[] result = new int[labels.length];
        int[] counts = new int[size];
        for (int index = 0; index < labels.length; index++) {
            Integer normalized = normalizedLabels.get(labels[index]);
            if (normalized == null) {
                if (normalizedLabels.size() == size) {
                    throw new IllegalArgumentException(
                            "block grid must contain exactly " + size + " blocks");
                }
                normalized = normalizedLabels.size() + 1;
                normalizedLabels.put(labels[index], normalized);
            }
            result[index] = normalized;
            counts[normalized - 1]++;
        }
        if (normalizedLabels.size() != size) {
            throw new IllegalArgumentException(
                    "block grid must contain exactly " + size + " blocks");
        }
        for (int count : counts) {
            if (count != size) {
                throw new IllegalArgumentException(
                        "every block must contain exactly " + size + " cells");
            }
        }
        return result;
    }

    private static CommandOptions commandOptions(
            ParsedOptions parsed, boolean allowForbidden, int size) {
        SolverMethod method = solverMethod(parsed);
        int lower = Math.max(parsed.intValue("--dp-min", 0), 0);
        int upper = parsed.intValue("--dp-max", Integer.MAX_VALUE);
        if (upper < 0) {
            upper = Integer.MAX_VALUE;
        }
        if (lower > upper) {
            int swap = lower;
            lower = upper;
            upper = swap;
        }
        int forbidden = parsed.intValue("--forbidden", -1);
        if (allowForbidden && parsed.has("--forbidden")
                && (forbidden < 1 || forbidden > size)) {
            throw new IllegalArgumentException(
                    "--forbidden must be between 1 and " + size);
        }
        int attempts = parsed.intValue("--attempts", 100);
        if (attempts < 0) {
            throw new IllegalArgumentException("--attempts must be non-negative");
        }
        return new CommandOptions(method, lower, upper, forbidden, attempts);
    }

    private static SolverMethod solverMethod(ParsedOptions parsed) {
        SolverMethod method = allMethods();
        String use = parsed.value("--use");
        if (use != null) {
            method.localization = false;
            method.nakedPair = false;
            method.hiddenPair = false;
            method.nakedTriple = false;
            method.hiddenTriple = false;
            method.XWing = false;
            method.swordfish = false;
            String[] names = optionList(use, "--use");
            if (containsNone(names)) {
                requireNoneAlone(names, "--use");
            }
            for (String name : names) {
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
                    default -> throw new IllegalArgumentException(
                            "unknown --use value: " + name);
                }
            }
        }
        String unique = parsed.value("--unique");
        if (unique != null) {
            method.unique.vhUnique = false;
            method.unique.cellUnique = false;
            method.unique.blockUnique = false;
            String[] names = optionList(unique, "--unique");
            if (containsNone(names)) {
                requireNoneAlone(names, "--unique");
            }
            for (String name : names) {
                switch (name) {
                    case "none" -> {
                    }
                    case "vh" -> method.unique.vhUnique = true;
                    case "cell" -> method.unique.cellUnique = true;
                    case "block" -> method.unique.blockUnique = true;
                    default -> throw new IllegalArgumentException(
                            "unknown --unique value: " + name);
                }
            }
        }
        return method;
    }

    private static String[] optionList(String value, String option) {
        if (value.isEmpty()) {
            throw new IllegalArgumentException(option + " requires a non-empty list");
        }
        String[] list = value.split(",", -1);
        for (String item : list) {
            if (item.isEmpty()) {
                throw new IllegalArgumentException(option + " contains an empty value");
            }
        }
        return list;
    }

    private static boolean containsNone(String[] values) {
        return Arrays.asList(values).contains("none");
    }

    private static void requireNoneAlone(String[] values, String option) {
        if (values.length != 1) {
            throw new IllegalArgumentException(
                    option + " value none cannot be combined with other values");
        }
    }

    private static SolverMethod allMethods() {
        SolverMethod method = new SolverMethod();
        method.setAllUse();
        return method;
    }

    private static void validateValues(
            int[] values, int size, boolean pattern, String name) {
        if (values == null || values.length != size * size) {
            throw new IllegalArgumentException(
                    name + " must contain exactly " + (size * size) + " cells");
        }
        for (int value : values) {
            if (pattern) {
                if (value != 0 && value != 1) {
                    throw new IllegalArgumentException(
                            "pattern cells must be 0 or 1");
                }
            } else if (value < 0 || value > size) {
                throw new IllegalArgumentException(
                        name + " cells must be between 0 and " + size);
            }
        }
    }

    private static NumberPlaceFile xmlFile(
            Variant variant, boolean[] hint, int[] hidden,
            int[] problem, int[] answer, double difficulty,
            NumberPlaceFile source) {
        NumberPlaceFile file = new NumberPlaceFile();
        file.setNumSize(variant.size);
        file.setHint(hint.clone());
        file.setHidden(hidden.clone());
        file.setProblem(problem.clone());
        file.setAnswer(answer.clone());
        file.setDifficult((int) difficulty);
        file.setVertical(variant.vertical);
        file.setHorizontal(variant.horizontal);
        file.setIsDiagonal(variant.diagonal);
        file.setDefaultBlock(variant.defaultBlock);
        file.setBlockArray(variant.blockArray.clone());
        if (source != null) {
            file.setComment(source.getComment());
        }
        return file;
    }

    private static boolean xmlOutput(ParsedOptions parsed) {
        return parsed.has("--format") || parsed.has("--out");
    }

    private static void writeXml(ParsedOptions parsed, NumberPlaceFile file)
            throws IOException {
        String output = parsed.value("--out");
        if (output == null) {
            System.out.print(file.toXmlString());
        } else {
            file.save(Path.of(output).toFile());
        }
    }

    private static void requireXmlFormat(ParsedOptions parsed) {
        String format = parsed.value("--format");
        if (format != null && !format.equalsIgnoreCase("xml")) {
            throw new IllegalArgumentException("--format only supports xml");
        }
    }

    private static int resolveXmlSize(
            ParsedOptions parsed, NumberPlaceFile source) {
        int size = source.getNumSize();
        if (parsed.has("--size") && parsed.intValue("--size", size) != size) {
            throw new IllegalArgumentException(
                    "--size does not match XML problem size " + size);
        }
        return size;
    }

    private static boolean isXml(Path path) {
        return path.getFileName().toString().toLowerCase().endsWith(".xml");
    }

    private static void requireSize(int size) {
        if (size < MIN_SIZE || size > MAX_SIZE) {
            throw new IllegalArgumentException("--size must be between 2 and 25");
        }
    }

    @SafeVarargs
    private static Set<String> union(Set<String>... sets) {
        java.util.HashSet<String> result = new java.util.HashSet<>();
        for (Set<String> set : sets) {
            result.addAll(set);
        }
        return Set.copyOf(result);
    }

    private static void printSolve(SolveResult result, int size) {
        System.out.print("SOLUTION\n");
        System.out.print(TextGridIO.formatGrid(result.solution, size, false));
        System.out.println("DIFFICULTY " + Double.toString(result.difficulty));
    }

    private static void printGenerated(Generated result, int size) {
        System.out.print("PROBLEM\n");
        System.out.print(TextGridIO.formatGrid(result.problem, size, false));
        printSolve(new SolveResult(
                result.solution, result.difficulty,
                Solver.KindOfAnswer.UNIQUE_ANSWER), size);
    }

    private static void usage() {
        System.err.println("usage: npgen solve|generate|random|bench ...");
    }

    private enum Symmetry {
        ROT4("rot4", 4),
        ROT2("rot2", 2),
        MIRROR_H("mirror-h", 2),
        MIRROR_V("mirror-v", 2),
        NONE("none", 1);

        private final String optionName;
        private final int orbitSize;

        Symmetry(String optionName, int orbitSize) {
            this.optionName = optionName;
            this.orbitSize = orbitSize;
        }

        static Symmetry parse(String value) {
            if (value == null) {
                return ROT4;
            }
            for (Symmetry symmetry : values()) {
                if (symmetry.optionName.equals(value)) {
                    return symmetry;
                }
            }
            throw new IllegalArgumentException(
                    "--symmetry must be rot4, rot2, mirror-h, mirror-v, or none");
        }

        boolean isFixedPoint(int size, int x, int y) {
            if (size % 2 == 0 || this == NONE) {
                return false;
            }
            return switch (this) {
                case ROT4, ROT2 -> x == size / 2 && y == size / 2;
                case MIRROR_H -> x == size / 2;
                case MIRROR_V -> y == size / 2;
                case NONE -> false;
            };
        }

        int maximumHints(int size) {
            return switch (this) {
                case ROT4, ROT2 -> size * size - size % 2;
                case MIRROR_H, MIRROR_V ->
                    size * size - (size % 2 == 0 ? 0 : size);
                case NONE -> size * size - 1;
            };
        }
    }

    public record SolveResult(
            int[] solution, double difficulty, Solver.KindOfAnswer status) {
        public SolveResult {
            solution = Arrays.copyOf(solution, solution.length);
        }
    }

    public record Generated(int[] problem, int[] solution, double difficulty) {
        public Generated {
            problem = Arrays.copyOf(problem, problem.length);
            solution = Arrays.copyOf(solution, solution.length);
        }
    }

    private record RandomGenerated(int[] pattern, Generated generated) {
    }

    private record CommandOptions(
            SolverMethod method, int dpMin, int dpMax, int forbidden,
            int attempts) {
    }

    private record Variant(
            int size, BlockConstraint block, int[] blockArray,
            boolean vertical, boolean horizontal,
            boolean diagonal, boolean defaultBlock) {
    }

    private static final class ParsedOptions {
        private final Map<String, String> values = new LinkedHashMap<>();

        static ParsedOptions parse(
                String[] args, int start,
                Set<String> valueOptions, Set<String> flags) {
            ParsedOptions parsed = new ParsedOptions();
            for (int index = start; index < args.length; index++) {
                String option = args[index];
                if (flags.contains(option)) {
                    parsed.values.put(option, "");
                    continue;
                }
                if (!valueOptions.contains(option)) {
                    throw new IllegalArgumentException("unknown option: " + option);
                }
                if (index + 1 == args.length) {
                    throw new IllegalArgumentException("incomplete option: " + option);
                }
                parsed.values.put(option, args[++index]);
            }
            return parsed;
        }

        boolean has(String option) {
            return values.containsKey(option);
        }

        String value(String option) {
            return values.get(option);
        }

        int intValue(String option, int defaultValue) {
            String value = value(option);
            if (value == null) {
                return defaultValue;
            }
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException error) {
                throw new IllegalArgumentException(option + " requires an integer");
            }
        }

        long longValue(String option, long defaultValue) {
            String value = value(option);
            if (value == null) {
                return defaultValue;
            }
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException error) {
                throw new IllegalArgumentException(option + " requires an integer");
            }
        }
    }
}
