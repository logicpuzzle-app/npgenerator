package jp.gr.puzzle.npgen2007;

import java.util.Random;

public final class RandomCompatibilityDriver {
    public static void main(String[] args) {
        long[] seeds = {0, 1, -1, 42, Long.MIN_VALUE, Long.MAX_VALUE};
        int[] bounds = {1, 2, 3, 7, 9, 16, 17, 1_000, Integer.MAX_VALUE};
        for (long seed : seeds) {
            Random expected = new Random(seed);
            JavaRandom actual = new JavaRandom(seed);
            for (int index = 0; index < 10_000; index++) {
                int bound = bounds[index % bounds.length];
                int expectedValue = expected.nextInt(bound);
                int actualValue = actual.nextInt(bound);
                if (expectedValue != actualValue) {
                    throw new AssertionError(
                            "seed=" + seed + ", bound=" + bound + ", index=" + index);
                }
            }
        }
    }
}
