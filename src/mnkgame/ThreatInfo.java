package mnkgame;

public interface ThreatInfo {

    int[] getLocation();

    MNKCellState getColor();

    int getStreakCount();

    int getAdjacentFreeCount();

    int getOtherMarkedCount();

    int getOtherFreeCount();
}
