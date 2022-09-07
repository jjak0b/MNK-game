package player;

import mnkgame.MNKCell;
import mnkgame.MNKCellState;
import mnkgame.MNKGameState;

import java.util.*;

/**
 * This class consider as
 * - T(u) the time cost required for each update in {@link #mark} and {@link #unMark} operations as O(1)
 * - T(it) the time cost required for each iteration while iterating {@link #getMovesCandidates()} result as O(1)
 * so {@link #search()} find the best next move in
 * - O( (M*N)! ) on worst case
 * - O( √( (M*N)! ) ) on best case ( unlikely, as this class does not implement any move ordering heuristics
 */
public class AlphaBetaPruningSearchMoveStrategy implements SearchMoveStrategy<MNKCell>{
    protected Random rand;

    protected MNKGameState STATE_WIN;
    protected MNKGameState STATE_LOSE;
    protected MNKCellState MY_MARK_STATE;
    protected MNKCellState ENEMY_MARK_STATE;
    public EnumMap<MNKGameState, Integer> STANDARD_SCORES;
    protected int playerIndex;

    protected int maxDepthSearch;
    protected long timeout;
    protected int round;

    protected long startTime;
    protected long expectedEndTime;
    protected long endTime;
    protected long realEndTime;
    protected long startingExitTime;
    protected boolean isEarlyExitStarted;

    // stats
    protected float wTotalWorkTime;
    protected float wTotalWeights;
    protected float wAverageWorkTime;

    protected long totalWorkTime;
    protected long maxWorkTime;
    protected long minWorkTime;
    protected long averageWorkTime;

    protected EBoard currentBoard;
    protected AlphaBetaOutcome lastResult;
    /**
     * Used to rewind on a fast way (tricky) the recursive function of {@link #alphaBetaPruning(boolean, int, int, int, int, long)} through Exception when timeout has been reached.
     * this should be set to true only if its return value isn't needed, and the internal data structure supports a restore behavior
     * as the board and other data are in invalid state
     */
    public boolean USE_FAST_REWIND = false;

    // DEBUG
    public static final boolean DEBUG_SHOW_INFO = Debug.Player.DEBUG_SHOW_INFO;
    public static final boolean DEBUG_SHOW_BOARD = Debug.Player.DEBUG_SHOW_BOARD;
    public static final boolean DEBUG_SHOW_STATS = Debug.Player.DEBUG_SHOW_STATS;
    public static final boolean DEBUG_SHOW_MOVES_RESULT_ON_ROOT = Debug.Player.DEBUG_SHOW_MOVES_RESULT_ON_ROOT;

    public AlphaBetaPruningSearchMoveStrategy() {}

    /**
     * Initialize the SearchMove strategy
     *
     * @param M Board rows
     * @param N Board columns
     * @param K Number of symbols to be aligned (horizontally, vertically, diagonally) for a win
     * @param first True if it is the first player, False otherwise
     * @param timeout_in_secs Maximum amount of time (in seconds) allowed to find a move for {@link #search()}
     * @implNote Cost <ul>
     *      <li>Time: <code>O(M*N)</code></li>
     *      <li>Space: <code>O(M*N)</code></li>
     * </ul>
     */
    @Override
    public void init(int M, int N, int K, boolean first, int timeout_in_secs) {
        // New random seed for each game
        rand = new Random(System.currentTimeMillis());
        // prioritize cells to pick based on a sort of heatmap
        initTrackingBoard(M, N, K);

        if( first ) {
            STATE_WIN = MNKGameState.WINP1;
            STATE_LOSE = MNKGameState.WINP2;
            MY_MARK_STATE = MNKCellState.P1;
            ENEMY_MARK_STATE = MNKCellState.P2;
        }
        else {
            STATE_WIN = MNKGameState.WINP2;
            STATE_LOSE = MNKGameState.WINP1;
            MY_MARK_STATE = MNKCellState.P2;
            ENEMY_MARK_STATE = MNKCellState.P1;
        }
        STANDARD_SCORES = new EnumMap<>(MNKGameState.class);
        STANDARD_SCORES.put(STATE_WIN, Integer.MAX_VALUE);
        STANDARD_SCORES.put(STATE_LOSE, Integer.MIN_VALUE);
        STANDARD_SCORES.put(MNKGameState.DRAW, 0);
        STANDARD_SCORES.put(MNKGameState.OPEN, 0);

        timeout = timeout_in_secs * 1000L;
        playerIndex = first ? 0 : 1;

        // round index
        round = 0;

        // stats
        wTotalWorkTime = 0f;
        wTotalWeights = 0f;
        wAverageWorkTime = 0f;
        totalWorkTime = 0;
        averageWorkTime = 0;
        minWorkTime = Long.MAX_VALUE;
        maxWorkTime = 0;
        startingExitTime = 0;
        isEarlyExitStarted = false;
        maxDepthSearch = 6;

    }

    /**
     * Init and set the board use by this strategy
     * @param M
     * @param N
     * @param K
     * @implNote Cost <ul>
     *      <li>Time: <code>O(M*N)</code></li>
     *      <li>Space: <code>O(M*N)</code></li>
     * </ul>
     */
    protected void initTrackingBoard(int M, int N, int K) {
        setBoard(new EBoard(M,N,K));
    }

    protected void setBoard(EBoard board) {
        this.currentBoard = board;
    }

    /**
     * Init the search for {@link #search()}
     *
     * @param FC free cells for current board state
     * @param MC marked cells for current board state
     * @implNote Cost <ul>
     *      <li>Time: <code>T({@link #mark(MNKCell)})</code></li>
     *      <li>Space: <code>S({@link #mark(MNKCell)})</code></li>
     * </ul>
     */
    @Override
    public void initSearch(MNKCell[] FC, MNKCell[] MC) {
        startTime = System.currentTimeMillis();
        realEndTime = startTime + timeout;
        expectedEndTime = realEndTime;

        if (MC.length > 0) {
            MNKCell choice;
            choice = MC[MC.length - 1]; // Save the last move in the local MNKBoard
            currentBoard.markCell(choice.i, choice.j);
            if( DEBUG_SHOW_BOARD )
                Debug.println( "after move:\n" + boardToString() );
        }
        ++round;
        lastResult = null;
        isEarlyExitStarted = false;
    }

    /**
     * Search the best next move candidate
     * @PreCondition <ul>
     *      <li>Call {@link #initSearch(MNKCell[], MNKCell[])} before this method</li>
     *      <li>Call {@link #postSearch()} after this method</li></ul>
     * @implNote <pre>Cost: <code>{@link #alphaBetaPruning(boolean, int, int, int, int, long)}</code></pre>
     * @return the next best move for a player using this strategy
     */
    @Override
    public MNKCell search() {

        lastResult = alphaBetaPruning(
                true,
                STANDARD_SCORES.get(STATE_LOSE),
                STANDARD_SCORES.get(STATE_WIN),
                0,
                maxDepthSearch,
                expectedEndTime
        );
        endTime = System.currentTimeMillis();

        return lastResult.move;
    }

    /**
     * Submit and mark the move provided from {@link #search()}
     * @Precondition Must be called after {@link #search()}
     * @implNote Cost <ul>
     *      <li>Time: <code>T({@link #mark(MNKCell)})</code></li>
     *      <li>Space: <code>S({@link #mark(MNKCell)})</code></li>
     * </ul>
     */
    @Override
    public void postSearch() {
        mark(lastResult.move);

        if( DEBUG_SHOW_STATS )
            printStats(lastResult);
        if( DEBUG_SHOW_BOARD )
            Debug.println( "after move:\n" + boardToString() );
    }

    protected void printStats(final AlphaBetaOutcome outcome ) {

        long elapsed = endTime-startTime;
        long timeLeftFromPrediction = (realEndTime - expectedEndTime);
        long timeLeft = (realEndTime - endTime);

        float markedCount = round+1;
        float total = currentBoard.M * currentBoard.N;
        float weight = markedCount / total;

        totalWorkTime += elapsed;
        wTotalWorkTime += (elapsed/1000.0) * weight;
        wTotalWeights += weight;
        wAverageWorkTime = wTotalWorkTime / wTotalWeights;
        averageWorkTime = totalWorkTime / (round+1);
        maxWorkTime = Math.max(maxWorkTime, elapsed);
        minWorkTime = Math.min(minWorkTime, elapsed);

        List<String[]> rows = new LinkedList<>();
        rows.add(new String[]{
                "Decision Time (ms)",
                "Left Time (ms)",
                "Prediction Left time (ms)",
                "Exit Time (ms)",
                "MIN work time (s)",
                "AVG work time (s)",
                "MAX work time (s)",
                "TOTAL work time (s)",
                "Heuristic",
        });
        rows.add(new String[]{
                String.valueOf(elapsed),
                String.valueOf(timeLeft),
                String.valueOf(timeLeftFromPrediction),
                String.valueOf(endTime-startingExitTime),
                String.valueOf(minWorkTime/1000f),
                String.valueOf(averageWorkTime/1000f),
                String.valueOf(maxWorkTime/1000f),
                String.valueOf(totalWorkTime/1000f),
                String.valueOf(outcome != null ? (outcome.state + ": " + outcome.eval) : null),
        });
        Debug.println(Utils.tableToString(rows));
    }

    public int getSimulatedRound() {
        return currentBoard.getMarkedCellsCount();
    }

    /**
     * Evaluate the board state and return an outcome
     * @implNote Cost <ul>
     *      <li>Time: <code>O(1)</code></li>
     *      <li>Space: <code>O(1)</code></li>
     * </ul>
     * @param depth
     * @param isMyTurn
     * @return
     */
    protected AlphaBetaOutcome evaluate(int depth, boolean isMyTurn) {
        MNKGameState gameState = currentBoard.gameState();
        AlphaBetaOutcome outcome = new AlphaBetaOutcome();
        depth = getSimulatedRound();

        int score = STANDARD_SCORES.get(gameState);

        if (gameState == MNKGameState.OPEN) { // game is open
            // here we should do an Heuristic evaluation

            // score = score / Math.max(1, depth);
        }

        outcome.eval = score;
        outcome.depth = depth;
        outcome.state = gameState;
        return outcome;
    }

    /**
     * Search the most scored move ({@link AlphaBetaOutcome}) through the alpha-beta pruning algorithm.
     * The moves as children nodes of game tree are processed in the order provided by {@link #getMovesCandidates()}
     * @implNote
     * Cost <ul>
     *      <li>Time:
     *          <pre>Let T(u) = max{T({@link #mark(MNKCell)}), T({@link #unMark()}), T({@link #evaluate(int, boolean)})}</pre>
     *          <ul>
     *              <li>Worst case: <code> O( (M*N * (T(u) + T(it)) )! )</code></li>
     *              <li>Best case: <code>O( √( (M*N * (T(u) + T(it)) )! ) )</code> if the {@link #getMovesCandidates()} ideally returns always the best sorted moves</li>
     *          </ul>
     *      </li>
     *      <li>Space: <code>O(depthLeft * ( M*N - depthLeft )) = O( depthLeft * M*N )</code></li>
     * </ul>
     * @param shouldMaximize if true then will compute best move for this player else for enemy player
     * @param a              best score for this player
     * @param b              best score for simulated enemy
     * @param depth current depth level
     * @param depthLeft max depth to reach
     * @param endTime max time that the algorithm should run up to
     * @throws EarlyExitException if {@link #USE_FAST_REWIND} is true and current run time is greater than endTime
     * @see {@link #USE_FAST_REWIND}
     * @PostCondition if {@link EarlyExitException} has been triggered, the data structure below are in invalid state
     * @return
     */
    protected AlphaBetaOutcome alphaBetaPruning(boolean shouldMaximize, int a, int b, int depth, int depthLeft, long endTime) {
        // on last if condition may would be a match in a always win/lost configuration
        if (depthLeft == 0 || currentBoard.gameState() != MNKGameState.OPEN) {
            return evaluate(depth, shouldMaximize);
        }
        else {
            AlphaBetaOutcome
                    bestOutcome = null,
                    outcome = null;

            for ( MNKCell move : getMovesCandidates()) {
                // estimate 1ms per depth (Upperbound) required to exit
                if (USE_FAST_REWIND && ( isEarlyExitStarted || (isEarlyExitStarted = (startingExitTime = System.currentTimeMillis()) >= endTime) ) ) {
                    if( DEBUG_SHOW_INFO )
                        Debug.println(Utils.ConsoleColors.YELLOW + "Exiting quickly - depth " + depth + Utils.ConsoleColors.RESET);
                    // This is required here because if USE_FAST_REWIND, then we don't have to provide a return value
                    // NOTE: this behavior can be obtained converting this recursive function to an iterative one
                    // but in this way is more readable for documentation purpose
                    throw new EarlyExitException("Exiting quickly");
                }

                mark(move);

                outcome = alphaBetaPruning(!shouldMaximize, a, b, depth + 1, depthLeft - 1, endTime);
                outcome.move = move;

                unMark();

                // minimize
                if (!shouldMaximize) {
                    bestOutcome = bestOutcome != null ? min(bestOutcome, outcome) : outcome;
                    b = Math.min(b, outcome.eval);
                    if( USE_FAST_REWIND && depth == 0 ) lastResult = new AlphaBetaOutcome(bestOutcome);
                }
                // maximize
                else {
                    bestOutcome = bestOutcome != null ? max(bestOutcome, outcome) : outcome;
                    a = Math.max(a, outcome.eval);
                    if( USE_FAST_REWIND && depth == 0 ) lastResult = new AlphaBetaOutcome(bestOutcome);
                }

                if( DEBUG_SHOW_MOVES_RESULT_ON_ROOT && depth == 0 )
                    Debug.println("Move " + move + " bring to " + outcome + " - best: " + bestOutcome );

                // if first run or just override best outcome, then replace move
                if( bestOutcome.move == outcome.move || bestOutcome.move == null)
                    bestOutcome.move = move;

                if (b <= a) { // a or b cutoff ( can't get better results )
                    break;
                }

                if (!USE_FAST_REWIND && ( isEarlyExitStarted || (isEarlyExitStarted = (startingExitTime = System.currentTimeMillis()) >= endTime) ) ) {
                    // This is required here because if !USE_FAST_REWIND, then we can provide a valid best outcome
                    if( DEBUG_SHOW_INFO )
                        Debug.println(Utils.ConsoleColors.YELLOW + "Exiting quickly - depth " + depth  + Utils.ConsoleColors.RESET);
                    break;
                }

            }
            return bestOutcome;
        }
    }

    public AlphaBetaOutcome min(AlphaBetaOutcome o1, AlphaBetaOutcome o2) {
        if( o1.state == o2.state ) {
            return AlphaBetaOutcome.min(o1, o2);
        }
        else if( o1.state == STATE_LOSE ) {
            return o1;
        }
        else if( o2.state == STATE_LOSE) {
            return o2;
        }
        else {
            return AlphaBetaOutcome.min(o1, o2);
        }
    }

    public AlphaBetaOutcome max(AlphaBetaOutcome o1, AlphaBetaOutcome o2) {
        if( o1.state == o2.state ) {
            return AlphaBetaOutcome.max(o1, o2);
        }
        else if( o1.state == STATE_WIN ) {
            return o1;
        }
        else if( o2.state == STATE_WIN) {
            return o2;
        }
        else {
            return AlphaBetaOutcome.max(o1, o2);
        }
    }

    /**
     * Provide the moves candidates to process in {@link #alphaBetaPruning(boolean, int, int, int, int, long)} node
     * @implNote
     * Cost <ul>
     *      <li>Time: <code>O(N*M)</code> where N*M is the count of free cell left</li>
     *      <li>Space: <code>O(N*M)</code></li>
     * </ul>
     * @return free moves candidates to be processed at caller board status
     */
    @Override
    public Iterable<MNKCell> getMovesCandidates() {
        return Arrays.asList(currentBoard.getFreeCells());
    }

    /**
     * Unmark last move and update the tracking board
     * @implNote Cost <ul>
     *      <li>Time: <code>O(1)</code></li>
     *      <li>Space: <code>O(1)</code></li>
     * </ul>
     */
    public void unMark() {
        //Debug.println( "\t" + tree.currentPlayer + " Unmarking:" + tree.MC.getLast() + " @ " + depth );
        MNKCell unmarked = currentBoard.getLastMarked();
        currentBoard.unmarkCell();

        // Debug.println( getPlayerByIndex( tree.currentPlayer() ) + getTabForDepth( depth ) +  "Unmarking:" + unmarked + " @ " + depth);
        // it.add(MC.remove());
    }

    /**
     * Mark the move and update the tracking board
     * @param marked
     * @implNote Cost <ul>
     *      <li>Time: <code>O(1)</code></li>
     *      <li>Space: <code>O(1)</code></li>
     * </ul>
     */
    public void mark(MNKCell marked) {
        // MNKCell marked = it.next();
        int playerIndex = currentBoard.currentPlayer();
        // Debug.println( getPlayerByIndex( tree.currentPlayer() ) + getTabForDepth( depth ) +  "Marking:" + marked + " @ " + depth);
        currentBoard.markCell(marked.i, marked.j);
        // it.remove();
        // MC.add(marked);
    }

    public String boardToString() {
        return Utils.toString(currentBoard);
    }


    public static class EarlyExitException extends RuntimeException {
        public EarlyExitException(String msg) {
            super(msg);
        }
    }
}