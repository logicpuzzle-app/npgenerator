package jp.gr.puzzle.npgen2007;

public final class RewriteBlockSplitDriver {
    public static void main(String[] args) {
        int size = Integer.parseInt(args[0]);
        long seed = Long.parseLong(args[1]);
        int[] values = new BlockSplit(size, size, new JavaRandom(seed)).splitBlock();
        for (int index = 0; index < values.length; index++) {
            if (index > 0) {
                System.out.print(' ');
            }
            System.out.print(values[index]);
        }
        System.out.println();
    }
}
