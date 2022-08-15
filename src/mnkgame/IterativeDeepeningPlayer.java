package mnkgame;

import java.util.Arrays;
import java.util.concurrent.TimeoutException;

public class IterativeDeepeningPlayer extends MyPlayer {

    public static final boolean DEBUG_SHOW_DECISION_INFO = Debug.Player.DEBUG_SHOW_DECISION_INFO;

    @Override
    public void initPlayer(int M, int N, int K, boolean first, int timeout_in_secs) {
        super.initPlayer(M, N, K, first, timeout_in_secs);
        this.maxDepthSearch = M * N;
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
    protected AlphaBetaOutcome _alphaBetaPruning(MNKBoard tree, boolean shouldMaximize, int a, int b, int depth, int depthLeft, long endTime) throws TimeoutException {
        // on last if condition may would be a match in a always win/lost configuration
        if (depthLeft == 0 || tree.gameState() != MNKGameState.OPEN) {
            return evaluate(tree, depth, shouldMaximize);
        }
        else {
            AlphaBetaOutcome
                    bestOutcome = null,
                    outcome = null;

            // Debug.println( getPlayerByIndex( shouldMaximize ? 0 : 1 )+ " Move " + depth );

            int i = 0;
            for ( MNKCell move : getCellCandidates(tree)) {


                mark(tree, move, depth);

                outcome = _alphaBetaPruning(tree, !shouldMaximize, a, b, depth + 1, depthLeft - 1, endTime);
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
                    throw new TimeoutException("Exiting quickly");
                }

                if (b <= a) { // a or b cutoff ( can't get better results )
                    break;
                }

                // Debug.println( getPlayerByIndex( isMyTurn ? 0 : 1 )+ " Move " + depth );
            }
            return bestOutcome;
        }
    }

    @Override
    public MNKCell selectCell(MNKCell[] FC, MNKCell[] MC) {
        AlphaBetaOutcome outcome = null;
        long elapsed = 0;
        long startTime = System.currentTimeMillis();
        long endTime = startTime + (long) ( timeout * (99.0/100.0));

        if( !isStateValid()) {
            if( DEBUG_SHOW_INFO )
                Debug.println(Utils.ConsoleColors.YELLOW + "Start Restoring current state");
            restore(FC, MC);
            if( DEBUG_SHOW_INFO )
                Debug.println(Utils.ConsoleColors.YELLOW + "End Restoring current state, time spent: " + (elapsed/1000.0) + Utils.ConsoleColors.RESET );
            setInValidState();
        }
        else {
            MNKCell choice = null;
            if (MC.length > 0) {
                choice = MC[MC.length - 1]; // Save the last move in the local MNKBoard
                mark(currentBoard, choice, 0);
                ++round;
                choice = null;
            }
        }
        elapsed += System.currentTimeMillis() - startTime;
        endTime -= elapsed;

        switch ( MC.length ){
            case 0: // move as first
                outcome = strategyAsFirst(FC, MC, endTime);
                break;
//            case 1: // move as second
//                choice = strategyAsSecond(FC, MC);
//                break;
            default:
                outcome = iterativeDeepening(
                        currentBoard,
                        true,
                        STANDARD_SCORES.get(STATE_LOSE),
                        STANDARD_SCORES.get(STATE_WIN),
                        this.maxDepthSearch,
                        endTime
                );
                break;
        }

        if( Debug.DEBUG_ENABLED ) {
            if(!isStateValid()){
                if( DEBUG_SHOW_INFO )
                    Debug.println(Utils.ConsoleColors.YELLOW + "Start Restoring current state" + Utils.ConsoleColors.RESET );
                startTime = System.currentTimeMillis();
                restore(FC, MC);
                elapsed = System.currentTimeMillis() - startTime;
                if( DEBUG_SHOW_INFO )
                    Debug.println(Utils.ConsoleColors.YELLOW + "End Restoring current state, time spent: " + (elapsed/1000.0) + Utils.ConsoleColors.RESET );
                setInValidState();
            }
        }

        if( isStateValid() )
            mark(currentBoard, outcome.move, 0);

        if( DEBUG_SHOW_BOARD )
            Debug.println( "after move:\n" + boardToString() );
        if( Debug.DEBUG_ENABLED && currentBoard.gameState() != MNKGameState.OPEN ){
            Debug.println( "Final board:\n" + boardToString() );
        }
        round++;
        return outcome.move;
    }

    @Override
    protected AlphaBetaOutcome strategyAsFirst(MNKCell[] FC, MNKCell[] MC, long endTime) {
        if( DEBUG_START_FIXED_MOVE ) {
            int[] coords = corners[ 1 ]; // constant for debug
            if( DEBUG_SHOW_INFO )
                Debug.println( "First Move: Move to a fixed corner");
            AlphaBetaOutcome outcome = new AlphaBetaOutcome();
            outcome.move = new MNKCell( coords[0], coords[1] ); outcome.depth = 0; outcome.eval = 0;
            return outcome;
        }
        else {
            return iterativeDeepening(
                    currentBoard,
                    true,
                    STANDARD_SCORES.get(STATE_LOSE),
                    STANDARD_SCORES.get(STATE_WIN),
                    this.maxDepthSearch,
                    endTime
            );
        }
    }

    public AlphaBetaOutcome iterativeDeepening(MNKBoard tree, boolean shouldMaximize, int a, int b, int maxDepthSearch, long endTime ) {
        long startTime = System.currentTimeMillis();
        long expectedTimeRequiredToExit = (long) (estimatedPercentOfTimeRequiredToExit * timeout);

        long partialStartTime = 0,
             partialEndTime = 0,
             partialElapsed = 0,
             partialWorkTime = 0;

        long leftTime = timeout;
        if ( DEBUG_SHOW_CANDIDATES )
            Debug.println("Candidates: " + getCellCandidates(currentBoard));

        // we assume are already in valid state
        // setInValidState();

        AlphaBetaOutcome outcome = null;

        boolean isOutOfTime = false;

        for (int maxDepth = 1; maxDepth < maxDepthSearch; maxDepth++) {

            partialStartTime = System.currentTimeMillis();
            partialWorkTime = partialStartTime + ( leftTime - expectedTimeRequiredToExit );

            try {
                if( DEBUG_SHOW_INFO ) {
                    Debug.println("Start searching up to depth " + maxDepth);
                }
                outcome = _alphaBetaPruning(
                        tree,
                        shouldMaximize,
                        a,
                        b,
                        0,
                        maxDepth,
                        partialWorkTime
                );
                if( DEBUG_SHOW_DECISION_INFO ){
                    Debug.println(Utils.ConsoleColors.CYAN +
                            "Search up to depth " + maxDepth + " end with choice :" + outcome
                            + Utils.ConsoleColors.RESET
                    );
                }

            }
            catch (TimeoutException e) {
                // set in invalid state, because if running out time, internal data structure are in in invalid state
                invalidateState();
                isOutOfTime = true;
            }

            partialEndTime = System.currentTimeMillis();
            partialElapsed = partialEndTime - partialStartTime;
            leftTime = endTime - partialEndTime;

            if (isOutOfTime) {
                if( DEBUG_SHOW_INFO ) {
                    Debug.println(Utils.ConsoleColors.RED +
                            "Exit quickly due to timeout on depth " + maxDepth +
                            ". Time left = " + leftTime + Utils.ConsoleColors.RESET
                    );
                }
                break;
            }
        }

        long end = partialEndTime;
        long elapsed = end-startTime;
        long timeLeft = (end-elapsed);

        if( DEBUG_SHOW_STATS )
            printStats(outcome, elapsed, timeLeft);

        return outcome;
    }

    @Override
    protected void restoreTrackingBoard(MNKCell[] FC, MNKCell[] MC) {
        initTrackingBoard(currentBoard.M, currentBoard.N, currentBoard.K);

        // mark to current state
        for (int i = 0; i < MC.length; i++) {
            mark(currentBoard, MC[ i ], -1);
        }
    }

    @Override
    public void restore(MNKCell[] FC, MNKCell[] MC) {
        initWeights(currentBoard.M, currentBoard.N, currentBoard.K);
        initCombo();
        initCells(currentBoard.M, currentBoard.N, currentBoard.K);
        restoreTrackingBoard(FC, MC);
        setInValidState();
    }

    /**
     * Returns the player name
     *
     * @return string
     */
    @Override
    public String playerName() {
        return "IterativeDeepeningPlayer";
    }
}
