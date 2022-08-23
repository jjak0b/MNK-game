package player;

import mnkgame.MNKCell;

public class IterativeDeepeningSearchMoveStrategy extends ThreatSearchMoveStrategy {

    public static final boolean DEBUG_SHOW_DECISION_INFO = Debug.Player.DEBUG_SHOW_DECISION_INFO;

    @Override
    public void init(int M, int N, int K, boolean first, int timeout_in_secs) {
        super.init(M, N, K, first, timeout_in_secs);
        this.maxDepthSearch = M * N;
        this.USE_FAST_REWIND = true;
    }

    @Override
    public void initSearch(MNKCell[] FC, MNKCell[] MC) {
        super.initSearch(FC, MC);
        this.maxDepthSearch = currentBoard.getFreeCellsCount();
    }

    @Override
    public MNKCell search() {
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

                lastResult = iterativeDeepening(
                        true,
                        STANDARD_SCORES.get(STATE_LOSE),
                        STANDARD_SCORES.get(STATE_WIN),
                        this.maxDepthSearch
                );
                break;
        }

        return lastResult.move;
    }

    @Override
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
            return iterativeDeepening(
                    true,
                    STANDARD_SCORES.get(STATE_LOSE),
                    STANDARD_SCORES.get(STATE_WIN),
                    this.maxDepthSearch
            );
        }
    }

    public AlphaBetaOutcome iterativeDeepening(boolean shouldMaximize, int a, int b, int maxDepthSearch ) {

        long partialStartTime = 0,
             partialEndTime = 0,
             partialElapsed = 0,
             partialWorkTime = 0;

        // we assume are already in valid state
        // setInValidState();

        AlphaBetaOutcome outcome = null;

        boolean isOutOfTime = false;

        for (int maxDepth = 1; maxDepth <= maxDepthSearch; maxDepth++) {

            partialStartTime = System.currentTimeMillis();

            try {
                if( DEBUG_SHOW_INFO ) {
                    Debug.println("Start searching up to depth " + maxDepth);
                }
                outcome = alphaBetaPruning(
                        shouldMaximize,
                        a,
                        b,
                        0,
                        maxDepth,
                        expectedEndTime
                );
                if( DEBUG_SHOW_DECISION_INFO ){
                    Debug.println(Utils.ConsoleColors.CYAN +
                            "Search up to depth " + maxDepth + " end with choice :" + outcome
                            + Utils.ConsoleColors.RESET
                    );
                }

            }
            catch (EarlyExitException e) {
                // set in invalid state, because if running out time, internal data structure are in in invalid state
                invalidateState();
                isOutOfTime = true;
            }

            partialEndTime = System.currentTimeMillis();
            partialElapsed = partialEndTime - partialStartTime;

            if (isOutOfTime) {
                if( DEBUG_SHOW_INFO ) {
                    long leftTime = expectedEndTime - partialEndTime;
                    Debug.println(Utils.ConsoleColors.RED +
                            "Exit quickly due to timeout on depth " + maxDepth +
                            ". Time left = " + leftTime + Utils.ConsoleColors.RESET
                    );
                }
                break;
            }
        }

        endTime = partialEndTime;

        return outcome;
    }

}
