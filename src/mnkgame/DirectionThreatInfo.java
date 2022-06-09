package mnkgame;

import java.util.Arrays;

/**
 * index 0 is side left (or back)
 * index 1 is side right (or forward)
 */
public abstract class DirectionThreatInfo implements SideThreatInfo {

    int directionType;

    // marked count on each side, breadth 0
    int[] onSideMarked = {0, 0};
    // free count on each side, following the marked ones, breadth 1
    int[] onSideFree = {0, 0};
    // marked count on each side, following the free ones, breadth 2 ( so this is the count of others streaks near the move reference)
    int[] onSideMarkedNear = {0, 0};
    // free count on each side, following the other marked ones, breadth 3
    int[] onSideOtherFree = {0, 0};

    @Override
    public String toString() {
        return "ScanResult{" +
                "directionType=" + directionType +
                ", onSideMarked=" + Arrays.toString(onSideMarked) +
                ", onSideFree=" + Arrays.toString(onSideFree) +
                ", onSideMarkedNear=" + Arrays.toString(onSideMarkedNear) +
                ", onSideOtherFree=" + Arrays.toString(onSideOtherFree) +
                '}';
    }

    @Override
    public int getFreeOnSide(int side) {
        return onSideFree[side];
    }

    @Override
    public int getOtherMarkedOnSide(int side) {
        return onSideMarkedNear[side];
    }

    @Override
    public int getOtherFreeOnSide(int side) {
        return onSideOtherFree[side];
    }
}
