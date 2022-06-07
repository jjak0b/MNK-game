package mnkgame;

public interface ThreatDetectionLogic<T> {

    void init(int M, int N, int K);

    void mark(MNKBoard tree, MNKCell marked, int markingPlayerIndex, int depth);

    void unMark(MNKBoard tree, MNKCell oldMarked, int markingPlayerIndex, int depth);

    ScanResult getThreatInDirection(MNKCell source, int directionType);

    T getBestThreat(int playerIndex, int directionType );

    boolean isCandidate(T threat);
}
