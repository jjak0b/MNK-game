package player;

public interface SearchMoveStrategy<MOVE> {

    /**
     * Initialize the SearchMove strategy
     *
     * @param M Board rows
     * @param N Board columns
     * @param K Number of symbols to be aligned (horizontally, vertically, diagonally) for a win
     * @param first True if it is the first player, False otherwise
     * @param timeout_in_secs Maximum amount of time (in seconds) allowed to find a move for {@link #search()}
     */
    void init(int M, int N, int K, boolean first, int timeout_in_secs);

    /**
     *
     * @param FC Free moves: array of free moves
     * @param MC Marked moves: ordered array of already marked moves (first move done on first turn is in the first position, etc)
     */
    void initSearch(MOVE[] FC, MOVE[] MC);

    /**
     * Search the best next move candidate
     * @PreCondition <ul>
     *      <li>Call {@link #initSearch} before this method</li>
     *      <li>Call {@link #postSearch} after this method</li></ul>
     * @return the next best move for a player using this strategy
     */
    MOVE search();

    /**
     * Submit the move provided from {@link #search}
     * @Precondition Must be called after {@link #search()}
     */
    void postSearch();

    /**
     * Provide the moves to process in {@link #search} while searching the best next move
     * @return free moves candidates to be processed at caller
     */
    Iterable<MOVE> getMovesCandidates();
}
