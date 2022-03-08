package mnkgame;

import java.util.*;

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

    public static class Weight extends MNKCell implements Comparable<Weight> {
        public int value;

        public MNKCell getCell() {
            return this;
        }

        public Weight( MNKCell cell, int value ) {
            super(cell.i, cell.j, cell.state);
            this.value = value;
        }

        @Override
        public int compareTo(Weight o) {
            int compare = Integer.compare(value, o.value);
            if( compare == 0 ) {
                return Utils.compare(this, o);
            }
            return compare;
        }
    }

    public static int getPlayerIndex(MNKCellState state) {
        switch (state) {
            case P1: return 0;
            case P2: return 1;
            default: return -1;
        }
    }

    public static String toString(MNKBoard board) {
        if( board instanceof StatefulBoard) {
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

    public static String toString(MNKCellState[] cellStates) {
        String[] cells = new String[cellStates.length];
        for (int i = 0; i < cellStates.length; i++) {
            cells[i] = "";
            switch (cellStates[i]) {
                case P1:
                    if( Debug.DEBUG_USE_COLORS )
                        cells[i] = ConsoleColors.RED;
                    cells[i] += "  ";
                    break;
                case P2:
                    if( Debug.DEBUG_USE_COLORS )
                        cells[i] = ConsoleColors.BLUE;
                    cells[i] += "  ";
                    break;
                case FREE:
                    break;
            }

            if( Debug.DEBUG_USE_COLORS )
                cells[i] += cellStates[i] + ConsoleColors.RESET;
            else
                cells[i] += cellStates[i];

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
                PURPLE,
                YELLOW,
                GREEN,
                CYAN,
                BLUE,
                WHITE,
                BLACK
        };


    }

    public static final <T> void swap (T[] a, int i, int j) {
        T t = a[i];
        a[i] = a[j];
        a[j] = t;
    }

    public static final <T> void swap (List<T> l, int i, int j) {
        Collections.swap(l, i, j);
    }

    public static class QuickSortVariant {

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

            // Debug.println(" 0 - " + i + " - " + j);
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

            final int lowerBoundIndex = n-k;
            boolean keepSort = true;
            T x; // v[ w ]
            while ( keepSort ) {
                // pivot
                w =  Math.floorDiv(e+s, 2);
                x = v[w];

                // Debug.println("Partitioning from " + s + " to " + e + " with val " + x );
                int[] indexes = partition(v, s, e, x, comparator );
                startX = indexes[0];
                endX = indexes[1];
                // Debug.println(Arrays.toString(v));
                // Debug.println("From 0 to " + (startX-1) + " are < " + x );
                // Debug.println("From " + startX + " to " + endX +" are = " + x );
                // Debug.println("From " + (endX+1) + " to " + e +" are > " + x );
                // the picked element is > of (n-k)-th element
                if( lowerBoundIndex < startX )
                    e = startX-1;
                // the picked element is < of (n-k)-th element
                else if( lowerBoundIndex > endX )
                    s = endX + 1;
                // the picked element is the (n-k)-th element
                else
                    keepSort = false;
            }
        }
    }

    public static class Sort {
        public static <T> void insertionSort(T[] A, int fromIndex, int toIndex, Comparator<T> comparator) {
            if( toIndex <= fromIndex) return;

            final int count = toIndex+1;
            for (int i = fromIndex+1; i < count; i++) {
                T value = A[i];
                int j = i-1;
                while( j >= 0 && comparator.compare(A[j], value) > 0 ) {
                    A[j+1] = A[j];
                    j--;
                }
                A[j+1] = value;
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

    public static int compare(MNKCell o1, MNKCell o2) {
        int compare = Integer.compare(o1.i, o2.i);
        if( compare == 0) compare = Integer.compare(o1.j, o2.j );
        return compare;
    }


    public static <T> int min( T[] buffer, Comparator<T> comparator ) {
        int min = 0;
        for (int i = 1; i < buffer.length; i++) {
            int comp = comparator.compare(buffer[i], buffer[min]);
            if( comp < 0 ) {
                min = i;
            }
        }
        return min;
    }

    public static <T> int max( T[] buffer, Comparator<T> comparator ) {
        int max = 0;
        for (int i = 1; i < buffer.length; i++) {
            int comp = comparator.compare(buffer[i], buffer[max]);
            if( comp > 0 ) {
                max = i;
            }
        }
        return max;
    }
}
