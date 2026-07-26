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

import java.util.List;

/**
 * Byte-for-byte compatible implementation of java.util.Random's 48-bit LCG.
 *
 * <p>This class deliberately does not delegate to {@code java.util.Random};
 * it is the small reference implementation used by the other language ports.</p>
 */
public final class JavaRandom {
    private static final long MULTIPLIER = 0x5DEECE66DL;
    private static final long ADDEND = 0xBL;
    private static final long MASK = (1L << 48) - 1;

    private long state;

    public JavaRandom(long seed) {
        setSeed(seed);
    }

    public void setSeed(long seed) {
        state = (seed ^ MULTIPLIER) & MASK;
    }

    public int next(int bits) {
        if (bits < 0 || bits > 32) {
            throw new IllegalArgumentException("bits must be between 0 and 32");
        }
        state = (state * MULTIPLIER + ADDEND) & MASK;
        return (int) (state >>> (48 - bits));
    }

    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive");
        }
        if ((bound & -bound) == bound) {
            return (int) ((bound * (long) next(31)) >> 31);
        }
        int bits;
        int value;
        do {
            bits = next(31);
            value = bits % bound;
        } while (bits - value + (bound - 1) < 0);
        return value;
    }

    public <T> void shuffle(List<T> values) {
        for (int i = values.size(); i > 1; i--) {
            int j = nextInt(i);
            T previous = values.set(i - 1, values.get(j));
            values.set(j, previous);
        }
    }

    public void shuffle(int[] values) {
        for (int i = values.length; i > 1; i--) {
            int j = nextInt(i);
            int previous = values[i - 1];
            values[i - 1] = values[j];
            values[j] = previous;
        }
    }
}
