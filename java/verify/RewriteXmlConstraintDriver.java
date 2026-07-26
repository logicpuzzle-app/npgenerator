package jp.gr.puzzle.npgen2007;

import java.io.File;
import java.util.ArrayList;

public final class RewriteXmlConstraintDriver {
    public static void main(String[] args) throws Exception {
        NumberPlaceFile file = new NumberPlaceFile(new File(args[1]));
        if (args[0].equals("metadata")) {
            printMetadata(file);
            return;
        }
        if (!args[0].equals("blocks")) {
            throw new IllegalArgumentException("blocks|metadata");
        }
        BlockConstraint constraint = Utility.makeBlockConstraint(file);
        ArrayList<Integer[]> blocks = constraint.getBlock();
        System.out.println("BLOCKS " + blocks.size());
        for (Integer[] block : blocks) {
            System.out.println(Utility.toStringFromArray(block));
        }
    }

    private static void printMetadata(NumberPlaceFile file) {
        int hints = 0;
        for (boolean hint : file.getHint()) {
            if (hint) {
                hints++;
            }
        }
        System.out.println("HINT_PRESENT " + file.hasHint());
        System.out.println("HINT_COUNT " + hints);
        System.out.println("COMMENT " + file.getComment());
        System.out.println("VERTICAL " + file.isVertical());
        System.out.println("HORIZONTAL " + file.isHorizontal());
    }
}
