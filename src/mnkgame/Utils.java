package mnkgame;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

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

    public static class QuickSortVariant {

        public static final <T> void swap (T[] a, int i, int j) {
            T t = a[i];
            a[i] = a[j];
            a[j] = t;
        }

        public static final <T> void swap (List<T> l, int i, int j) {
            Collections.swap(l, i, j);
        }

        /**
         * Algorithm of National flag
         * @param A
         * @param start
         * @param end
         * @param x
         * @param comparator
         * @param <T>
         * @return 2 indexes: first and last index of values equals to x
         */
        public static <T> int[] partition(T[] A, int start, int end, T x , Comparator<T> comparator) {
            int i = start,
                j = start,
                k = end;
            while( j <= k ) {
                int comparison = comparator.compare(A[j], x);
                if( comparison < 0 ) {
                    if( i < j ) {
                        swap(A, i, j);
                    }
                    ++i;
                    ++j;
                }
                else if( comparison > 0 ) {
                    swap(A, j, k);
                    --k;
                }
                else {
                    ++j;
                }
            }

            // System.out.println(" 0 - " + i + " - " + j);
            return new int[]{i, j-1};
        }

        /**
         * Sort the last k elements in ascending order according to the provided comparator
         * where the last k are from e, so in reverse direction.
         * The other elements not in range s-e are untouched.
         *
         * @param s usually = 0
         * @param e usually = v.lenght-1
         * @param k count of elements to sort
         * @param v
         * @param comparator
         * @param <T>
         */
        public static <T> void sortMaxK( int s, int e, int k, T[] v, Comparator<T> comparator) {
            int
                n = e + 1 -s, // vector size to consider
                w,
                startX, endX;

            T x; // v[ w ]
            while (s >= 0 && e < v.length ) {
                w =  Math.floorDiv(e+s, 2);
                x = v[w];
                // System.out.println("Partitioning from " + s + " to " + e + " with val " + x );
                int[] indexes = partition(v, s, e, x, comparator );
                startX = indexes[0];
                endX = indexes[1];
                // System.out.println(Arrays.toString(v));
                // System.out.println("From 0 to " + (startX-1) + " are < " + x );
                // System.out.println("From " + startX + " to " + endX +" are = " + x );
                // System.out.println("From " + (endX+1) + " to " + e +" are > " + x );
                // the picked element is > of (n-k)-th element
                if( n - k < startX )
                    e = startX-1;
                // the picked element is < of (n-k)-th element
                else if( n - k > endX )
                    s = endX + 1;
                // the picked element is the (n-k)-th element
                else
                    break;
            }
        }
    }


    public static class BufferSorter<T> {
        int unsortedCount;
        T[] buffer;
        Comparator<T> comparator;

        public BufferSorter(T[] buffer, Comparator<T> comparator ) {
            this.buffer = buffer;
            this.comparator = comparator;
            this.unsortedCount = this.buffer.length;
        }

        public T[] getBuffer() {
            return buffer;
        }

        public int getUnsortedCount() {
            return unsortedCount;
        }
        /**
         * Partially Sort the greatest $count elements in ascending order in the last $count positions of the unsorted buffer
         * In other words sort next $count elements starting from index 0 to last unsorted index
         * after this call last unsorted index decrease by $count.
         * Note a pure ascending order is not respected, but it's granted that the $count elements are the greatest in the unsorted left set
         * A use case of this function can be represented from the following code:
         * <pre><code>
         *  int count = 5;
         *  Integer[] buffer = bs.getBuffer();
         *
         *  int major, minor;
         *  do {
         *      major = bs.sortNext(count);
         *      minor = Math.max(0, 1 + major - count);
         *      for (int i = major; i >= minor ; i-- ) {
         *          print( buffer[i]);
         *      }
         *
         *  } while( bs.getUnsortedCount() > 0);
         * </code></pre>
         * @param count
         * @return the end index of last element ordered in ascending order,
         * so it's associated to the greatest value in the considered set of ordered elements
         */
        public int sortNext(int count) {
            count = Math.min(count, this.unsortedCount);
            if(count <= 0) return -1;

            int endIndex = this.unsortedCount-1;

            QuickSortVariant.sortMaxK(0, endIndex, count, buffer, comparator);

            this.unsortedCount -= count;

            return endIndex;
        }
    }
}
