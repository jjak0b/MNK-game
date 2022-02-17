package mnkgame;

import java.util.Arrays;

public class Utils {

    public final static int DIRECTION_TYPE_VERTICAL= 0;
    public final static int DIRECTION_TYPE_OBLIQUE_RL = 1;
    public final static int DIRECTION_TYPE_HORIZONTAL= 2;
    public final static int DIRECTION_TYPE_OBLIQUE_LR = 3;

    // clock wise
    public final static int[] DIRECTIONS = {
            DIRECTION_TYPE_VERTICAL,
            DIRECTION_TYPE_OBLIQUE_RL,
            DIRECTION_TYPE_HORIZONTAL,
            DIRECTION_TYPE_OBLIQUE_LR
    };
    // clockwise offsets ( center to left, center to right)
    public final static int[][][] DIRECTIONS_OFFSETS = {
            // DIRECTION_TYPE_VERTICAL
            { { 0, -1 }, { 0, 1 } },
            // DIRECTION_TYPE_OBLIQUE_RL
            { { -1, -1 }, { 1, 1 } },
            // DIRECTION_TYPE_HORIZONTAL
            { { -1, 0 }, { 1, 0 } },
            // DIRECTION_TYPE_OBLIQUE_LR
            { { -1, 1 }, { 1, -1 } }
    };

    public static int getPlayerIndex(MNKCellState state) {
        switch (state) {
            case P1: return 0;
            case P2: return 1;
            default: return -1;
        }
    }

    public static String toString(MNKBoard board) {
        if( board instanceof WeightedMNKBoard) {
            return board.toString();
        }
        else {
            String s = "";
            for (int i = 0; i < board.B.length; i++) {
                s += Utils.toString( board.B[ i ] ) + "\n";
            }
            return s;
        }
    }

    public static String toString(int[] weights, int max) {
        String[] cells = new String[weights.length];
        for (int i = 0; i < weights.length; i++) {
            cells[i] = "";
            int index;
            int aColorSpace = Math.max (1, max / ConsoleColors.RAINBOW.length);

            index = weights[i] / aColorSpace;
            index = Math.max (0, (ConsoleColors.RAINBOW.length-1) - index );
            index = Math.min(ConsoleColors.RAINBOW.length-1, index );

            String color = ConsoleColors.RAINBOW[index];

            cells[i] += color + weights[i] + ConsoleColors.RESET;

        }
        return Arrays.toString(cells);
    }
    public static String toString(MNKCellState[] cellStates) {
        String[] cells = new String[cellStates.length];
        for (int i = 0; i < cellStates.length; i++) {
            cells[i] = "";
            switch (cellStates[i]) {
                case P1:
                    cells[i] = ConsoleColors.RED;
                    cells[i] += "  ";
                    break;
                case P2:
                    cells[i] = ConsoleColors.BLUE;
                    cells[i] += "  ";
                    break;
                case FREE:
                    break;
            }
            cells[i] += cellStates[i] + ConsoleColors.RESET;

        }
        return Arrays.toString(cells);
    }

    public static class ConsoleColors {
        // Reset
        public static final String RESET = "\033[0m";  // Text Reset

        // Regular Colors
        public static final String BLACK = "\033[0;30m";   // BLACK
        public static final String RED = "\033[0;31m";     // RED
        public static final String GREEN = "\033[0;32m";   // GREEN
        public static final String YELLOW = "\033[0;33m";  // YELLOW
        public static final String BLUE = "\033[0;34m";    // BLUE
        public static final String PURPLE = "\033[0;35m";  // PURPLE
        public static final String CYAN = "\033[0;36m";    // CYAN
        public static final String WHITE = "\033[0;37m";   // WHITE


        public static final String[] RAINBOW = {
                RED,
                YELLOW,
                PURPLE,
                GREEN,
                CYAN,
                BLUE,
                WHITE,
                RESET
        };


    }
}
