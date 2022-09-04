package player.legacy;

import mnkgame.MNKCell;
import player.AlphaBetaOutcome;
import player.HashableBoardState;
import player.Utils;

import java.math.BigInteger;
import java.util.HashMap;

public class CachedSearchMoveStrategyLegacy extends IterativeDeepeningSearchMoveStrategyLegacy {

    protected HashableBoardState currentState;
    protected Utils.MatrixRowMap matrixMap;
    private HashMap<BigInteger, CachedResult> cachedResults;

    @Override
    public void init(int M, int N, int K, boolean first, int timeout_in_secs) {
        super.init(M, N, K, first, timeout_in_secs);
        final int TABLE_SIZE = (int) Math.ceil((M * N * K) / 0.75);
        cachedResults = new HashMap<>(TABLE_SIZE);
        currentState = new HashableBoardState();
        matrixMap = new Utils.MatrixRowMap(M, N);
    }

    @Override
    public void mark(MNKCell marked) {
        int markingPlayer = currentBoard.currentPlayer();
        currentState.toggle(markingPlayer, matrixMap.getArrayIndexFromMatrixIndexes(marked.i, marked.j));
        super.mark(marked);
    }

    @Override
    public void unMark() {
        if (currentBoard.getMarkedCellsCount() > 0) {
            MNKCell oldc = currentBoard.getLastMarked();
            int unMarkingPlayer = Utils.getPlayerIndex(oldc.state);
            currentState.toggle(unMarkingPlayer, matrixMap.getArrayIndexFromMatrixIndexes(oldc.i, oldc.j));
        }

        super.unMark();
    }

    @Override
    protected AlphaBetaOutcome strategyAsFirst() {
        AlphaBetaOutcome outcome = super.strategyAsFirst();
        // Run and gather info on future moves
        AlphaBetaOutcome outcome2 = iterativeDeepening(
                true,
                STANDARD_SCORES.get(STATE_LOSE),
                STANDARD_SCORES.get(STATE_WIN),
                this.maxDepthSearch
        );

        if (outcome2.state == STATE_WIN)
            return outcome2;
        else
            return outcome;
    }

    @Override
    protected AlphaBetaOutcome alphaBetaPruning(boolean shouldMaximize, int alpha, int beta, int depth, int depthLeft, long endTime) {
        int a = alpha;
        int b = beta;
        BigInteger key = currentState.getCurrentState();
        AlphaBetaOutcome outcome;

        CachedResult bestOutcome = this.cachedResults.get(key);
        int score;
        if (bestOutcome != null && depthLeft <= bestOutcome.depth && key.equals(bestOutcome.boardState)) {
            score = bestOutcome.eval;

            switch (bestOutcome.type) {
                case EXACT:
                    // transposition has a deeper or equal search depth
                    // we can stop here as we already know the value
                    // returned by the evaluation function
                    return new AlphaBetaOutcome(bestOutcome);
                case LOWER_BOUND:
                    a = Math.max(a, score);
                    break;
                case UPPER_BOUND:
                    b = Math.min(b, score);
                    break;
            }

            if (b <= a) {
                // cut-off
                return new AlphaBetaOutcome(bestOutcome);
            }
        }

        outcome = super.alphaBetaPruning(shouldMaximize, a, b, depth, depthLeft, endTime);

        // if( true ) {
        // minimize
        if (outcome.eval <= a) {
            cachedResults.put(key, new CachedResult(outcome, depthLeft, CachedResult.ValueType.UPPER_BOUND, key));
        }
        // maximize
        else if (outcome.eval >= b) {
            cachedResults.put(key, new CachedResult(outcome, depthLeft, CachedResult.ValueType.LOWER_BOUND, key));
        }
        else {
            cachedResults.put(key, new CachedResult(outcome, depthLeft, CachedResult.ValueType.EXACT, key));
        }
        // }

        return outcome;
    }

    public static class CachedResult extends AlphaBetaOutcome {

        public enum ValueType {
            EXACT,
            UPPER_BOUND,
            LOWER_BOUND
        }

        public ValueType type;
        public BigInteger boardState;
        public int depth;

        CachedResult(AlphaBetaOutcome o, int depth, ValueType type, BigInteger boardState) {
            super(o);
            this.type = type;
            this.boardState = boardState;
            this.depth = depth;
        }
    }
}
