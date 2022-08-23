package player;

import mnkgame.MNKCell;
import mnkgame.MNKCellState;
import mnkgame.MNKGameState;

import java.util.Arrays;
import java.util.Iterator;
import java.util.PriorityQueue;

/**
 * This class consider
 *  <ul>
 *      <li>T(u) the time cost required for each update in {@link #mark} and {@link #unMark} operations
 *          <ul>
 *              <li>Using standard {@link PriorityQueue} API: <code>O( N*M )</code></li>
 *              <li>Using optimal API: <code>O( log(M+N) )</code></li>
 *          </ul>
 *      </li>
 *      <li>T(it) the time cost required for each iteration while iterating {@link #getMovesCandidates()} result as O( log( M*N )  )</li>
 *  </ul>
 * So {@link #search()} find the best next move in
 *  <ul>
 *      <li>Worst Case
 *          <ul>
 *              <li>Using standard {@link PriorityQueue} API: <code>O( ((M*N)^2 )! ) on worst case</code></li>
 *              <li>Using optimal API: <code>O( ( (M*N) * log (M+N) )! )</code></li>
 *          </ul>
 *      </li>
 *      <li>Best Case - can happen often, as this class use a move ordering heuristic (see {@link #getMovesCandidates()})
 *          <ul>
 *              <li>Using standard {@link PriorityQueue} API: <code>O( √( ((M*N)^2 )! ) )</code></li>
 *              <li>Using optimal API: <code>O( √( (M*N * log(M+N))! ) )</code></li>
 *          </ul>
 *      </li>
 *  </ul>
 */
public class ThreatSearchMoveStrategy extends AlphaBetaPruningSearchMoveStrategy implements BoardRestorable {
    boolean isCurrentBoardLeftInValidState;
    float estimatedPercentOfTimeRequiredToExit;
    int[][] corners;
    MNKCell[] startingRoundFC;
    MNKCell[] startingRoundMC;
    protected Iterable<MNKCell> overrideMovesIt;

    // DEBUG
    public static final boolean DEBUG_SHOW_STREAKS = Debug.Player.DEBUG_SHOW_STREAKS;
    public static final boolean DEBUG_SHOW_USEFUL = Debug.Player.DEBUG_SHOW_USEFUL;
    public static final boolean DEBUG_SHOW_WEIGHTS = Debug.Player.DEBUG_SHOW_WEIGHTS;
    public static final boolean DEBUG_SHOW_CANDIDATES = Debug.Player.DEBUG_SHOW_CANDIDATES;
    public static final boolean DEBUG_START_FIXED_MOVE = Debug.Player.DEBUG_START_FIXED_MOVE;

    protected final ScanThreatDetectionLogic threatDetectionLogic = new ScanThreatDetectionLogic();

    public ThreatSearchMoveStrategy() {
        super();
    }

    public ThreatDetectionLogic<? extends ThreatInfo> getThreatDetectionLogic() {
        return threatDetectionLogic;
    }

    @Override
    public boolean isStateValid() {
        return isCurrentBoardLeftInValidState;
    }

    @Override
    public void setInValidState() {
        isCurrentBoardLeftInValidState = true;
    }

    @Override
    public void invalidateState() {
        isCurrentBoardLeftInValidState = false;
    }

    private int[][] getUsefulnessWeights(int p, int directionType) {
        return threatDetectionLogic.getMovePriority(p, directionType);
        // return threatDetectionLogic.getMovesLeft(p, directionType);
    }

    private int[][] getWeights() {
        // return threatDetectionLogic.getMinMovesLeft();
        return threatDetectionLogic.freeCellsPrioritiesCache;
    }

    /**
     *
     * @param M Board rows
     * @param N Board columns
     * @param K Number of symbols to be aligned (horizontally, vertically, diagonally) for a win
     * @param first True if it is the first player, False otherwise
     * @param timeout_in_secs Maximum amount of time (in seconds) allowed to find a move for {@link #search()}
     * @implNote Cost <code>max{ O(M*N), {@link ThreatSearchMoveStrategy#init(int, int, int, boolean, int)}, {@link Arrays#sort(Object[])} }</code>
     */
    @Override
    public void init(int M, int N, int K, boolean first, int timeout_in_secs) {
        super.init(M, N, K, first, timeout_in_secs);
        estimatedPercentOfTimeRequiredToExit = 5f/100f;
        corners = new int[][]{ {0, 0}, {0, N-1}, {M-1, 0}, {M-1, N-1} };
        maxDepthSearch = 6;

        threatDetectionLogic.init(M, N, K);

        setInValidState();

        if( first ) {
            // set just a higher priority for first round on special positions
            for (int i = 1; i >= 0; i--) {
                threatDetectionLogic.updatePriority(playerIndex, M / 2, N / 2, i*2);
                for ( int[] coords : corners)
                    threatDetectionLogic.updatePriority(playerIndex, coords[0], coords[1], i);
                threatDetectionLogic.flushUpdatePool();

                if( i > 0 ) {
                    PriorityQueue<MNKCell> pq = threatDetectionLogic.getFree();
                    MNKCell[] ordered = pq.toArray(new MNKCell[0]);
                    Arrays.sort(ordered, pq.comparator()); // mostly already ordered -> Tim sort << O( MN log MN )
                    overrideMovesIt = Arrays.asList(ordered);
                    if( Debug.DEBUG_ENABLED && DEBUG_SHOW_CANDIDATES ) {
                        Debug.println("Override moves priority for first round:\n "+ overrideMovesIt + "\n" + boardToString());
                    }
                }
            }
        }
    }

    /**
     * Evaluate the board considering total moves left and best threat's score for both players
     * @param depth
     * @param isMyTurn
     * @return
     * @implNote Cost <ul>
     *      <li>Time: <code>O(1)</code></li>
     *      <li>Space: <code>O(1)</code></li>
     * </ul>
     */
    @Override
    protected AlphaBetaOutcome evaluate(int depth, boolean isMyTurn) {
        MNKGameState gameState = currentBoard.gameState();
        AlphaBetaOutcome outcome = new AlphaBetaOutcome();
        depth = getSimulatedRound();

        int score = STANDARD_SCORES.get(gameState);

        int opponentIndex = 1-playerIndex;

        if (gameState == MNKGameState.OPEN) { // game is open
            final int[] playerScores = new int[2];
            final int[] maxPlayerThreatScores = {0, 0};
            // final int[] totalPlayerScores = {0, 0};
            final int[] weight = new int[]{ 100000, 100000, 1000, 1 };
            // O( 8 * 3 )
            for (int indexPlayer = 0; indexPlayer < 2; indexPlayer++) {
                for ( int directionType : Utils.DIRECTIONS ) {
                    // computes players score based on moves left count and their weight
                    int[] movesLeft = threatDetectionLogic.getMovesLeftArrayCount(indexPlayer, directionType);
                    for (int c = 1; c < weight.length && c < movesLeft.length; c++) {
                        playerScores[ indexPlayer ] += movesLeft[ c ] * weight[c];
                    }
                    // computes players score based on best max threat's score
                    ScanThreatDetectionLogic.Threat bestPlayerThreat = threatDetectionLogic.getBestThreat(indexPlayer, directionType);
                    if( bestPlayerThreat != null ) {
                        // totalPlayerScores[indexPlayer] += bestPlayerThreat.getScore();
                        maxPlayerThreatScores[indexPlayer] = Math.max(maxPlayerThreatScores[indexPlayer], bestPlayerThreat.getScore());
                    }
                }
            }

            score += playerScores[ playerIndex ] - playerScores[ opponentIndex ];
            score += maxPlayerThreatScores[playerIndex] - maxPlayerThreatScores[opponentIndex];
            // this heuristic below is very similar and statistically it's just a little better
            // but because sometimes wins more if starting as second
            // score += totalPlayerScores[playerIndex] - totalPlayerScores[opponentIndex]
        }

        if( score >= depth)
            score -= depth-1;
        else if( score <= -depth)
            score += depth-1;

        outcome.eval = score;
        outcome.depth = depth;
        outcome.state = gameState;
        return outcome;
    }

    /*
        @Override
        protected AlphaBetaOutcome evaluate(MNKBoard board, int depth, boolean isMyTurn) {
            MNKGameState gameState = board.gameState();
            AlphaBetaOutcome outcome = new AlphaBetaOutcome();

            int score = STANDARD_SCORES.get(gameState);

            int opponentIndex = 1-playerIndex;

            final int[] maxPlayerThreatScores = {0, 0};
            if (gameState == MNKGameState.OPEN) { // game is open
                // final int[] totalPlayerScores = {0, 0};
                for (int indexPlayer = 0; indexPlayer < 2; indexPlayer++) {
                    for ( int directionType : Utils.DIRECTIONS ) {

                        Scan2ThreatDetectionLogic.ThreatT bestPlayerThreat = threatDetectionLogic.getBestThreat(indexPlayer, directionType);
                        if( bestPlayerThreat != null ) {
                            // totalPlayerScores[indexPlayer] += bestPlayerThreat.getScore();
                            maxPlayerThreatScores[indexPlayer] = Math.max(maxPlayerThreatScores[indexPlayer], bestPlayerThreat.getScore());
                        }
                    }
                }
            }

            score += maxPlayerThreatScores[playerIndex] - maxPlayerThreatScores[opponentIndex];
            // this heuristic below is very similar and statistically it's just a little better
            // but because sometimes wins more if starting as second
            // score += totalPlayerScores[playerIndex] - totalPlayerScores[opponentIndex]

            if( score >= depth)
                score -= depth-1;
            else if( score <= -depth)
                score += depth-1;

            outcome.eval = score;
            outcome.depth = depth;
            return outcome;
        }
    */


    /**
     * Search the best next move candidate
     * @PreCondition <ul>
     *      <li>Call {@link #initSearch(MNKCell[], MNKCell[])} before this method</li>
     *      <li>Call {@link #postSearch()} after this method</li></ul>
     * @implNote <pre>Cost: <code>{@link #alphaBetaPruning}</code> with <code>T(u) = T({@link #getMovesCandidates()}.next()) + max{ T({@link ScanThreatDetectionLogic#mark}), T({@link ScanThreatDetectionLogic#unMark} ) }</code>
     * </pre>
     * @return the next best move for a player using this strategy
     */
    @Override
    public MNKCell search() {
        // set in invalid state, because if running out time, this function may be terminated
        invalidateState();
        switch ( round ){
            case 0: // move as first
                lastResult = strategyAsFirst();
                break;
//            case 1: // move as second
//                choice = strategyAsSecond(FC, MC);
//                break;
            default:
                if ( DEBUG_SHOW_CANDIDATES )
                    Debug.println("Candidates: " + this.getMovesCandidates());
                super.search();
                break;
        }
        // we returned so assuming all right
        setInValidState();
        return lastResult.move;
    }

    /**
     *
     * @param FC Free Cells: array of free cells
     * @param MC Marked Cells: array of already marked cells, ordered with respect
     *           to the game moves (first move is in the first position, etc)
     */
    @Override
    public void initSearch(MNKCell[] FC, MNKCell[] MC) {

        long elapsed = 0;
        startingRoundFC = FC;
        startingRoundMC = MC;

        // pre calculate expected work time
        long expectedTimeRequiredToExit = (long) (estimatedPercentOfTimeRequiredToExit * timeout);
        startTime = System.currentTimeMillis();
        expectedEndTime = startTime + (long) ( timeout * (99.0/100.0)) - expectedTimeRequiredToExit;

        if( DEBUG_SHOW_INFO )
            Debug.println(Utils.ConsoleColors.YELLOW + "Start Restoring current state");

        restore(FC, MC);
        if( MC.length > 0)
            ++round;

        elapsed += System.currentTimeMillis() - startTime;
        if( DEBUG_SHOW_INFO )
            Debug.println(Utils.ConsoleColors.YELLOW + "End Restoring current state, time spent: " + (elapsed/1000.0) + Utils.ConsoleColors.RESET );
        if( DEBUG_SHOW_BOARD )
            Debug.println( "after opponent move:\n" + boardToString() );

        lastResult = null;
    }

    /**
     * @implNote Cost <ul>
     *      <li>Time: <code>max{ T({@link #mark}), T({@link #restore}) }</code></li>
     *      <li>Space: <code>max{ T({@link #mark}), T({@link #restore}) }</code></li>
     * </ul>
     */
    @Override
    public void postSearch() {
        if( Debug.DEBUG_ENABLED ) {
            if(!isStateValid()){
                if( DEBUG_SHOW_INFO )
                    Debug.println(Utils.ConsoleColors.YELLOW + "Start Restoring current state");
                restore(startingRoundFC, startingRoundMC);
                if( DEBUG_SHOW_INFO )
                    Debug.println(Utils.ConsoleColors.YELLOW + "End Restoring current state" + Utils.ConsoleColors.RESET );
            }
        }

        if( isStateValid() )
            mark(lastResult.move);

        if( DEBUG_SHOW_STATS )
            printStats(lastResult);
        if( DEBUG_SHOW_BOARD )
            Debug.println( "after move:\n" + boardToString() );
        if( Debug.DEBUG_ENABLED && currentBoard.gameState() != MNKGameState.OPEN ){
            Debug.println( "Final board:\n" + boardToString() );
        }

        round++;
    }

    /**
     * Mark the move and update the tracking board
     * @param marked the move to mark
     * @implNote Cost <code>T(u)=T({@link ScanThreatDetectionLogic#mark})</code>
     */
    @Override
    public void mark(MNKCell marked) {
        int markingPlayer = currentBoard.currentPlayer();
        super.mark(marked);

        marked = currentBoard.getLastMarked();

        getThreatDetectionLogic().mark(currentBoard, marked, markingPlayer, 0);

    }

    /**
     * Unmark last move and update the tracking board
     * @implNote Cost <code>T(u) = T({@link ScanThreatDetectionLogic#unMark})</code>
     */
    @Override
    public void unMark() {
        MNKCell marked = currentBoard.getLastMarked();

        super.unMark();
        int unMarkingPlayer = currentBoard.currentPlayer();

        getThreatDetectionLogic().unMark(currentBoard, marked, unMarkingPlayer, 0);
    }

    /**
     * Provide the moves candidates to process in {@link #alphaBetaPruning} node
     * @implNote
     * Call cost <ul>
     *      <li>Time: <code>O(N*M)</code> where N*M is the count of free cell left</li>
     *      <li>Space: <code>O(N*M)</code></li>
     * </ul>
     * per iteration Cost <ul>
     *      <li>Time: <code>O({@link PriorityQueue#poll()})=O( log(N*M) ))</code> where N*M is the count of free moves left</li>
     *      <li>Space: <code>O(N*M)</code></li>
     * </ul>
     * @return free moves candidates to be processed at caller board status
     */
    @Override
    public Iterable<MNKCell> getMovesCandidates() {
        if( overrideMovesIt != null ){
            Iterable<MNKCell> it = overrideMovesIt;
            overrideMovesIt = null;
            return it;
        }
        else return new Iterable<>() {
            final PriorityQueue<MNKCell> queue = new PriorityQueue<>(threatDetectionLogic.getFree()); // O(N*M) for copy
            final Iterator<MNKCell> iterator = new Iterator<>() {
                @Override
                public boolean hasNext() {
                    return !queue.isEmpty();
                }

                @Override
                public MNKCell next() {
                    return queue.poll(); // log (N*M)
                }
            };

            @Override
            public Iterator<MNKCell> iterator() {
                return iterator;
            }

            @Override
            public String toString() {
                return Arrays.toString(queue.toArray(new MNKCell[0]));
            }
        };
    }

    protected AlphaBetaOutcome strategyAsFirst() {
        if( DEBUG_START_FIXED_MOVE ) {
            int[] coords = corners[ 1 ]; // constant for debug
            if( DEBUG_SHOW_INFO )
                Debug.println( "First Move: Move to a fixed corner");
            AlphaBetaOutcome outcome = new AlphaBetaOutcome();
            outcome.move = new MNKCell( coords[0], coords[1] ); outcome.depth = 0; outcome.eval = 0;
            return outcome;
        }
        else {
            if( DEBUG_SHOW_INFO )
                Debug.println( "First Move: Move to a fixed corner");
            AlphaBetaOutcome outcome = new AlphaBetaOutcome();
            outcome.move = new MNKCell( currentBoard.M / 2, currentBoard.N/2 ); outcome.depth = 0; outcome.eval = 0;
            return outcome;

            /*return alphaBetaPruning(
                    currentBoard,
                    true,
                    STANDARD_SCORES.get(STATE_LOSE),
                    STANDARD_SCORES.get(STATE_WIN),
                    0,
                    maxDepthSearch,
                    endTime
            );*/
        }
    }

    protected void restoreTrackingBoard(MNKCell[] FC, MNKCell[] MC) {
        // we have to restore last valid state

        int validCount = MC.length - (isStateValid() ? 1 : 2);

        // unmark all after the last 2 MC's moves, including the last move at same index on our board
        // this because if (!isStateValid()) then the last our mark hasn't been recorded on our board (same as last opponent mark)
        // so rewind the board history to last opponent turn and so restore last our turn and last opponent turn
        int count = currentBoard.getMarkedCellsCount();
        for (; count > 0 && count > validCount ; count--)
            unMark();
        // so mark the moves up to the last move
        for (; count < MC.length; count++)
            mark(MC[count]);

    }

    /**
     * Restore the data structures the a valid state, restoring the board state
     * @implNote Cost: <code>T(u) * LD</code> where <code>LD = {@link #maxDepthSearch} = O(M*N)</code> is the maximum explored depth difference from current round.
     * if LD is fixed to maxDepthSearch, then it costs O( T(u) ), else O( T(u) * M*N )
     * @param FC
     * @param MC
     */
    @Override
    public void restore(MNKCell[] FC, MNKCell[] MC) {
        restoreTrackingBoard(FC, MC);
        setInValidState();
    }

    @Override
    public String boardToString() {
        String s = "";

        int[][] weights = getWeights();

        if( DEBUG_SHOW_USEFUL ) {
            for (int p = 0; p < 2; p++) {
                s += "Usefulness for p" + (p + 1) + ":\n";
                for (int i = 0; i < currentBoard.states().length; i++) {
                    for (int directionType : Utils.DIRECTIONS) {
                        s += boardToString(currentBoard.states()[i], getUsefulnessWeights(p, directionType)[i], currentBoard.K) + "\t\t\t";
                    }
                    s += "\n";
                }
                s += "\n";
            }
        }

        for (int i = 0; i < currentBoard.states().length; i++) {
            s += Utils.toString(currentBoard.states()[i]) + "\t\t\t";
            if( DEBUG_SHOW_WEIGHTS) {
                for (int p = 0; p < 2; p++) {
                    s += boardToString(currentBoard.states()[i], weights[i], currentBoard.K) + "\t\t\t";
                }
            }
            s += "\n";
        }
        return s;
    }

    public static String boardToString(MNKCellState[] states, int[] weights, int max) {
        String[] cells = new String[weights.length];
        for (int i = 0; i < weights.length; i++) {
            cells[i] = "";
            int index;
            int aColorSpace = Math.max (1, max / Utils.ConsoleColors.RAINBOW.length);

            boolean shouldColor = Debug.DEBUG_USE_COLORS && (states == null || states[i] == MNKCellState.FREE);
            String color = Utils.ConsoleColors.RESET;
            if( shouldColor ) {
                index = weights[i] / aColorSpace;
                index = Math.max(0, (Utils.ConsoleColors.RAINBOW.length - 1) - index);
                index = Math.min(Utils.ConsoleColors.RAINBOW.length - 1, index);

                color = Utils.ConsoleColors.RAINBOW[index];
            }

            if(Debug.DEBUG_USE_COLORS)
                cells[i] += color + weights[i] + Utils.ConsoleColors.RESET;
            else
                cells[i] += weights[i];

        }
        return Arrays.toString(cells);
    }
}
