package mnkgame;

import java.util.*;

public class AlphaBetaPruningPlayer implements MNKPlayer{
    protected Random rand;

    protected MNKGameState STATE_WIN;
    protected MNKGameState STATE_LOSE;
    protected MNKCellState MY_MARK_STATE;
    protected MNKCellState ENEMY_MARK_STATE;
    public EnumMap<MNKGameState, Integer> STANDARD_SCORES;
    protected int playerIndex;

    protected int maxDepthSearch;
    protected int timeout;
    protected int round;

    // stats
    protected float wTotalWorkTime;
    protected float wTotalWeights;
    protected float wAverageWorkTime;

    protected long totalWorkTime;
    protected long maxWorkTime;
    protected long minWorkTime;
    protected long averageWorkTime;

    protected MNKBoard currentBoard;
    protected Stack<AlphaBetaOutcome> bestOutcomes;

    // DEBUG
    public static final boolean DEBUG_SHOW_INFO = Debug.Player.DEBUG_SHOW_INFO;
    public static final boolean DEBUG_SHOW_BOARD = Debug.Player.DEBUG_SHOW_BOARD;
    public static final boolean DEBUG_SHOW_STATS = Debug.Player.DEBUG_SHOW_STATS;
    public static final boolean DEBUG_SHOW_MOVES_RESULT_ON_ROOT = Debug.Player.DEBUG_SHOW_MOVES_RESULT_ON_ROOT;

    public AlphaBetaPruningPlayer() {

    }

    @Override
    public void initPlayer(int M, int N, int K, boolean first, int timeout_in_secs) {
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

        timeout = timeout_in_secs * 1000;
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

        maxDepthSearch = 6;

    }

    protected void initTrackingBoard(int M, int N, int K) {
        currentBoard = new MNKBoard(M,N,K);
    }

    @Override
    public MNKCell selectCell(MNKCell[] FC, MNKCell[] MC) {

        long start = System.currentTimeMillis();
        long endTime = start + (long) ( timeout * (99.0/100.0));
        long workTime = start + timeout;

        if (MC.length > 0) {
            MNKCell choice;
            choice = MC[MC.length - 1]; // Save the last move in the local MNKBoard
            currentBoard.markCell(choice.i, choice.j);
            ++round;

            if( DEBUG_SHOW_BOARD )
                Debug.println( "after move:\n" + boardToString() );
        }

        AlphaBetaOutcome outcome = alphaBetaPruning(
                currentBoard,
                true,
                STANDARD_SCORES.get(STATE_LOSE),
                STANDARD_SCORES.get(STATE_WIN),
                0,
                maxDepthSearch,
                workTime
        );
        long end = System.currentTimeMillis();
        long elapsed = end-start;
        long timeLeft = (endTime-end);

        if( DEBUG_SHOW_STATS )
            printStats(outcome, elapsed, timeLeft);

        // Debug.println( Arrays.toString( currentBoard.getFreeCells() ) );
        // Debug.println( "if " + currentBoard.currentPlayer()  + " choose " +  outcome.move + " -> " + outcome.eval );
        currentBoard.markCell( outcome.move.i, outcome.move.j );

        if( DEBUG_SHOW_BOARD )
            Debug.println( "after move:\n" + boardToString() );

        ++round;
        return outcome.move;
    }

    protected void printStats(final AlphaBetaOutcome outcome, long elapsed, long timeLeft ) {
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

        Debug.println( "Euristic: " +  (outcome != null ? outcome.eval : null) + "\t" +
                "Decision made in " + (elapsed/1000.0) + "\t" +
                "Left Time: " + (timeLeft/1000.0) + "\t" +
                "Weighted Average Time: " + (wAverageWorkTime) + "\t" +
                "Average Time: " + (averageWorkTime/1000.0) + "\t" +
                "Total Time: " + (totalWorkTime/1000.0) + "\t" +
                "Max time: " + (maxWorkTime/1000.0) + "\t" +
                "Min Time: " + (minWorkTime/1000.0)
        );
    }

    protected AlphaBetaOutcome evaluate(MNKBoard board, int depth, boolean isMyTurn) {
        MNKGameState gameState = board.gameState();
        AlphaBetaOutcome outcome = new AlphaBetaOutcome();

        int score = STANDARD_SCORES.get(gameState);
/* // keep as reference
        if( gameState == this.STATE_WIN ) {
            // Debug.println( getTabForDepth( depth-1 ) + MY_MARK_STATE +  "-> Win state");
            // score = 1f;
        }
        else if( gameState == this.STATE_LOSE ) {
            // Debug.println( getTabForDepth( depth-1 ) + MY_MARK_STATE +  "-> Lose state");
            // score = -1f;
        }
        else if( gameState == MNKGameState.DRAW ) {
            // Debug.println( getTabForDepth( depth-1 ) + "Draw state");
            // score = 0f;
        }
        else
*/
        if (gameState == MNKGameState.OPEN) { // game is open
            // Debug.println( getTabForDepth( depth-1 ) + "Heuristic state");
            // TODO: here we should do an Heuristic evaluation

            // score = score / Math.max(1, depth);
        }

        outcome.eval = score;
        outcome.depth = depth;
        return outcome;
    }

    /**
     * @param tree
     * @param shouldMaximize if true then will compute best move for this player else for enemy player
     * @param a              best score for this player
     * @param b              best score for simulated enemy
     * @param depth
     * @param depthLeft
     * @param endTime
     * @return
     */
    protected AlphaBetaOutcome alphaBetaPruning(MNKBoard tree, boolean shouldMaximize, int a, int b, int depth, int depthLeft, long endTime) {
        // on last if condition may would be a match in a always win/lost configuration
        if (depthLeft == 0 || tree.gameState() != MNKGameState.OPEN) {
            return evaluate(tree, depth, shouldMaximize);
        }
        else {
            AlphaBetaOutcome
                    bestOutcome = null,
                    outcome = null;

            // Debug.println( getPlayerByIndex( shouldMaximize ? 0 : 1 )+ " Move " + depth );

            for ( MNKCell move : getCellCandidates(tree)) {

                mark(tree, move, depth);

                outcome = alphaBetaPruning(tree, !shouldMaximize, a, b, depth + 1, depthLeft - 1, endTime);
                // outcome.move = move;

                unMark(tree, depth);

                // minimize
                if (!shouldMaximize) {
                    bestOutcome = bestOutcome != null ? min(bestOutcome, outcome) : outcome;
                    b = Math.min(b, outcome.eval);
                }
                // maximize
                else {
                    bestOutcome = bestOutcome != null ? max(bestOutcome, outcome) : outcome;
                    a = Math.max(a, outcome.eval);
                }

                if( DEBUG_SHOW_MOVES_RESULT_ON_ROOT && depth == 0 )
                    Debug.println("Move " + move + " bring to " + outcome + " - best: " + bestOutcome );

                // if first run or just override best outcome, then replace move
                if( bestOutcome.move == outcome.move || bestOutcome.move == null)
                    bestOutcome.move = move;

                if (System.currentTimeMillis() > endTime) {
                    if( DEBUG_SHOW_INFO )
                        Debug.println(Utils.ConsoleColors.YELLOW + "Exiting quickly");
                    break;
                }

                if (b <= a) { // a or b cutoff ( can't get better results )
                    break;
                }

                // Debug.println( getPlayerByIndex( isMyTurn ? 0 : 1 )+ " Move " + depth );
            }
            return bestOutcome;
        }
    }

    public AlphaBetaOutcome min(AlphaBetaOutcome o1, AlphaBetaOutcome o2) {
        return AlphaBetaOutcome.min(o1, o2);
    }

    public AlphaBetaOutcome max(AlphaBetaOutcome o1, AlphaBetaOutcome o2) {
        return AlphaBetaOutcome.max(o1, o2);
    }

    /**
     * @return moves candidates
     */
    public MNKCell[] getCellCandidates(MNKBoard board) {
        return board.getFreeCells();
    }

    public void unMark(MNKBoard tree, int depth) {
        //Debug.println( "\t" + tree.currentPlayer + " Unmarking:" + tree.MC.getLast() + " @ " + depth );
        MNKCell unmarked = tree.MC.getLast();
        tree.unmarkCell();
        // Debug.println( getPlayerByIndex( tree.currentPlayer() ) + getTabForDepth( depth ) +  "Unmarking:" + unmarked + " @ " + depth);
        // it.add(MC.remove());
    }

    public void mark(MNKBoard tree, MNKCell marked, int depth) {
        // MNKCell marked = it.next();
        int playerIndex = tree.currentPlayer();
        // Debug.println( getPlayerByIndex( tree.currentPlayer() ) + getTabForDepth( depth ) +  "Marking:" + marked + " @ " + depth);
        tree.markCell(marked.i, marked.j);
        // it.remove();
        // MC.add(marked);
    }

    public String boardToString() {
        return Utils.toString(currentBoard);
    }

    /**
     * Returns the player name
     *
     * @return string
     */
    @Override
    public String playerName() {
        return "PruningPlayer";
    }
}