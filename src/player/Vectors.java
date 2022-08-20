package player;

import java.util.function.BiFunction;

// Utility functions
public class Vectors {
    /**
     * Scale vector of some scalar
     *
     * @param v
     * @param scalar
     * @return the v vector scaled by the amount of the scalar
     */
    public static int[] vectorScale(int[] v, int scalar) {
        for (int i = 0; i < v.length; i++) v[i] *= scalar;
        return v;
    }

    /**
     * Copy source's values in dest
     *
     * @param dest
     * @param source
     * @return dest with source's values
     */
    public static int[] vectorCopy(int[] dest, int[] source) {
        for (int i = 0; i < dest.length; i++) dest[i] = source[i];
        return dest;
    }

    /**
     * Sum the direction to source vector
     *
     * @param source
     * @param direction
     * @return the source vector with the sum applied
     */
    public static int[] vectorSum(int[] source, int[] direction) {
        for (int i = 0; i < source.length; i++) source[i] += direction[i];
        return source;
    }

    /**
     * Iterate over both sides of a direction while callback return true (source is navigated once per side)
     * @param source
     * @param directionType
     * @param callback
     *
     */
    public static void iterate(int[] source, int directionType, BiFunction<int[], Integer, Boolean> callback ) {

        final int[] index = new int[source.length];
        int[][] directionsOffsets = Utils.DIRECTIONS_OFFSETS[directionType];
        for (int side = 0; side < directionsOffsets.length; side++) {
            int[] step = new int[directionsOffsets[side].length];
            Vectors.vectorCopy(index, source);

            while (callback.apply(index, side)) {
                Vectors.vectorSum(source, step);
            }
        }
    }

}