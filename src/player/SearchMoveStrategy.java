package player;

public interface SearchMoveStrategy<MOVE> {

    void init(int M, int N, int K, boolean first, int timeout_in_secs);

    void initSearch(MOVE[] FC, MOVE[] MC);

    MOVE search();

    void postSearch();

    Iterable<MOVE> getMovesCandidates();
}
