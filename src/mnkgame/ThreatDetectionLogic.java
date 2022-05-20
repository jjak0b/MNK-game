package mnkgame;

public interface ThreatDetectionLogic<T> {

    void init(int M, int N, int K);

    void mark(MNKBoard tree, MNKCell marked, int depth);

    void unMark(MNKBoard tree, MNKCell oldMarked, int depth);

    ScanResult getThreatInDirection(MNKCell source, int directionType);

    boolean isCandidate(T threat);
}
