package player;

import mnkgame.MNKCell;

public interface ThreatDetectionLogic<T extends ThreatInfo> {

    void init(int M, int N, int K);

    void mark(MNKCell marked, int markingPlayerIndex, int depth);

    void unMark(MNKCell oldMarked, int markingPlayerIndex, int depth);

    T getBestThreat(int playerIndex, int directionType );

    boolean isCandidate( ThreatInfo threat);
}
