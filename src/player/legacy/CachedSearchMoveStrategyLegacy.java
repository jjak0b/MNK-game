package player.legacy;

import player.AlphaBetaOutcome;
import player.Debug;
import player.StatefulBoard;

import java.math.BigInteger;
import java.util.HashMap;

public class CachedSearchMoveStrategyLegacy extends IterativeDeepeningSearchMoveStrategyLegacy {

    protected StatefulBoard currentBoard;
    private HashMap<BigInteger, CachedResult> cachedResults;

    @Override
    public void init(int M, int N, int K, boolean first, int timeout_in_secs) {
        super.init(M, N, K, first, timeout_in_secs);
        cachedResults = new HashMap<>((int) Math.ceil((M*N*K) / 0.75));
    }

    @Override
    protected void initTrackingBoard(int M, int N, int K) {
        try {
            currentBoard = new StatefulBoard(M, N, K);
            super.currentBoard = currentBoard;
        }
        catch (Throwable e ) {
            Debug.println("Error on init board " + e);
        }
    }

    @Override
    protected AlphaBetaOutcome alphaBetaPruning(boolean shouldMaximize, int alpha, int beta, int depth, int depthLeft, long endTime) {
        int a = alpha;
        int b = beta;
        BigInteger key = currentBoard.getCurrentState();
        AlphaBetaOutcome outcome;

        CachedResult bestOutcome = this.cachedResults.get( key );
        int score;
        if (bestOutcome != null && depth >= bestOutcome.depth && key.equals(bestOutcome.boardState) ) {
            score = bestOutcome.eval;

            switch (bestOutcome.type) {
                case EXACT:
                    // transposition has a deeper or equal search depth
                    // we can stop here as we already know the value
                    // returned by the evaluation function
                    return bestOutcome;
                case LOWER_BOUND:
                    a = Math.max(a, score);
                    break;
                case UPPER_BOUND:
                    b = Math.min(b, score);
                    break;
            }

            if (b <= a) {
                return bestOutcome;
            }
        }

        outcome = super.alphaBetaPruning(shouldMaximize, a, b, depth, depthLeft, endTime);
/*
        if (System.currentTimeMillis() > endTime) {
            if( DEBUG_SHOW_INFO )
                Debug.println("Exiting quickly");
            throw new TimeoutException("Exiting quickly");
        }
*/
        if( bestOutcome != null && depth >= bestOutcome.depth ) {
            // minimize
            if (outcome.eval <= a) {
                cachedResults.put(key, new CachedResult(outcome, CachedResult.ValueType.UPPER_BOUND, key));
            }
            // maximize
            else if (outcome.eval >= b) {
                cachedResults.put(key, new CachedResult(outcome, CachedResult.ValueType.LOWER_BOUND, key));
            }
            else {
                cachedResults.put(key, new CachedResult(outcome, CachedResult.ValueType.EXACT, key));
            }
        }

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

        CachedResult( AlphaBetaOutcome o, ValueType type, BigInteger boardState ) {
            super(o);
            this.type = type;
            this.boardState = boardState;
        }

        CachedResult( int value, ValueType type, int depth, BigInteger boardState ) {
            super();
            this.type = type;
            this.depth = depth;
            this.eval = value;
            this.boardState = boardState;
        }
    }
}
