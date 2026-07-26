package jp.gr.puzzle.npv2.core;

import java.util.Random;

/**
 * Test-only adapter that feeds the shared Java-compatible LCG to the original
 * Utility.random and Collections.shuffle call sites.
 */
public final class ReferenceRandom extends Random {
    private static final long serialVersionUID = 1L;
    private static final long MULTIPLIER = 0x5DEECE66DL;
    private static final long ADDEND = 0xBL;
    private static final long MASK = (1L << 48) - 1;
    private static long state;
    private static final ReferenceRandom ADAPTER = new ReferenceRandom();

    private ReferenceRandom() {
        super(0);
    }

    public static void reset(long seed) {
        state = (seed ^ MULTIPLIER) & MASK;
    }

    private static int nextBits(int bits) {
        state = (state * MULTIPLIER + ADDEND) & MASK;
        return (int) (state >>> (48 - bits));
    }

    public static int bounded(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive");
        }
        if ((bound & -bound) == bound) {
            return (int) ((bound * (long) nextBits(31)) >> 31);
        }
        int bits;
        int value;
        do {
            bits = nextBits(31);
            value = bits % bound;
        } while (bits - value + (bound - 1) < 0);
        return value;
    }

    public static Random adapter() {
        return ADAPTER;
    }

    @Override
    public int nextInt(int bound) {
        return bounded(bound);
    }

    @Override
    protected int next(int bits) {
        return nextBits(bits);
    }
}
