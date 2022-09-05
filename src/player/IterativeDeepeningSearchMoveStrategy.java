package player;

import mnkgame.MNKCell;

public class IterativeDeepeningSearchMoveStrategy extends ThreatSearchMoveStrategy {

    public static final boolean DEBUG_SHOW_DECISION_INFO = Debug.Player.DEBUG_SHOW_DECISION_INFO;
    protected int lastMaxDepth;


    @Override
    public void init(int M, int N, int K, boolean first, int timeout_in_secs) {
        super.init(M, N, K, first, timeout_in_secs);
        this.maxDepthSearch = M * N;
        this.USE_FAST_REWIND = true;
        this.lastMaxDepth = 0;
    }

    @Override
    public void initSearch(MNKCell[] FC, MNKCell[] MC) {
        // pre calculate expected work time
        super.initSearch(FC, MC);
        this.maxDepthSearch = currentBoard.getFreeCellsCount();

        float expectedTimeRequiredToExit = (estimatedPercentOfTimeRequiredToExit * timeout)
                + lastMaxDepth*(float)Math.log( Math.max(currentBoard.getFreeCellsCount(), currentBoard.getMarkedCellsCount()));
        expectedEndTime = realEndTime - (long)expectedTimeRequiredToExit;
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

    public AlphaBetaOutcome iterativeDeepening(boolean shouldMaximize, int a, int b, int maxDepthSearch ) {

        long partialStartTime = 0,
             partialEndTime = 0,
             partialElapsed = 0,
             partialWorkTime = 0;

        // we assume are already in valid state
        // setInValidState();

        AlphaBetaOutcome outcome = new AlphaBetaOutcome();
        outcome.move = threatDetectionLogic.getFree().peek();

        boolean isOutOfTime = false;
        lastMaxDepth = 1;
        for (int maxDepth = 1; maxDepth <= maxDepthSearch; maxDepth++) {

            partialStartTime = System.currentTimeMillis();

            try {
                if( DEBUG_SHOW_INFO ) {
                    Debug.println("Start searching up to depth " + maxDepth);
                }
                outcome = super.alphaBetaPruning(
                        shouldMaximize,
                        a,
                        b,
                        0,
                        maxDepth,
                        expectedEndTime
                );
                // keep last depth that don't cause an EarlyExit
                lastMaxDepth = maxDepth;
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
                    long timeLeftReal = realEndTime - partialEndTime;
                    long timeLeftFromPrediction = expectedEndTime - partialEndTime;
                    Debug.println(Utils.ConsoleColors.RED +
                            "Exit quickly due to timeout on depth " + maxDepth +
                            "; Time left from prediction (ms) = " + timeLeftFromPrediction +
                            "; Time left real (ms) = " + timeLeftReal + Utils.ConsoleColors.RESET
                    );
                }
                break;
            }
        }

        endTime = partialEndTime;

        return outcome;
    }

}
