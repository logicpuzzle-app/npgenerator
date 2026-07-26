package jp.gr.puzzle.npgen2007;

import java.io.File;

public final class RewriteXmlDriver {
    public static void main(String[] args) throws Exception {
        File file = new File(args[1]);
        if (args[0].equals("write")) {
            write(file);
        } else if (args[0].equals("read")) {
            print(new NumberPlaceFile(file));
        } else {
            throw new IllegalArgumentException("write|read");
        }
    }

    private static void write(File file) throws Exception {
        int size = 6;
        NumberPlaceFile data = new NumberPlaceFile();
        data.setNumSize(size);
        data.setHint(Utility.int2boolean(sequence(size * size, 2)));
        data.setHidden(sequence(size * size, 7));
        data.setProblem(sequence(size * size, 5));
        data.setAnswer(sequence(size * size, size));
        data.setBlockArray(rectangleBlocks(size, 3, 2));
        data.setDefaultBlock(false);
        data.setIsDiagonal(true);
        data.setDifficult(2468);
        data.save(file);
    }

    private static void print(NumberPlaceFile data) {
        System.out.println("SIZE " + data.getNumSize());
        System.out.println("HINT " + Utility.toStringFromArray(data.getHint()));
        System.out.println("HIDDEN " + Utility.toStringFromArray(data.getHidden()));
        System.out.println("PROBLEM " + Utility.toStringFromArray(data.getProblem()));
        System.out.println("ANSWER " + Utility.toStringFromArray(data.getAnswer()));
        System.out.println("BLOCK " + (data.getBlockArray() == null
                ? "NULL" : Utility.toStringFromArray(data.getBlockArray())));
        System.out.println("DIAGONAL " + data.isDiagonal());
        System.out.println("DEFAULT " + data.isDefaultBlock());
    }

    private static int[] sequence(int length, int modulus) {
        int[] values = new int[length];
        for (int index = 0; index < length; index++) {
            values[index] = index % modulus;
        }
        return values;
    }

    private static int[] rectangleBlocks(int size, int width, int height) {
        int[] labels = new int[size * size];
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                labels[row * size + column] =
                        (row / height) * (size / width) + column / width + 1;
            }
        }
        return labels;
    }
}
