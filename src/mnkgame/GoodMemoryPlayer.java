package mnkgame;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.concurrent.TimeoutException;

public class GoodMemoryPlayer extends IterativeDeepeningPlayer {

    private HashMap<BigInteger, CachedResult> cachedResults;

    @Override
    public void initPlayer(int M, int N, int K, boolean first, int timeout_in_secs) {
        super.initPlayer(M, N, K, first, timeout_in_secs);
        cachedResults = new HashMap<>((int) Math.ceil((M*N*K) / 0.75));
    }

    @Override
    protected AlphaBetaOutcome _alphaBetaPruning(MNKBoard board, boolean shouldMaximize, int alpha, int beta, int depth, int depthLeft, long endTime) throws TimeoutException {

        StatefulBoard tree = (StatefulBoard) board;
        int a = alpha;
        int b = beta;
        BigInteger key = tree.getCurrentState();
        AlphaBetaOutcome outcome;

        CachedResult bestOutcome = this.cachedResults.get( key );
        int weightedValue;
        if (bestOutcome != null && depth >= bestOutcome.depth && key.equals(bestOutcome.boardState) ) {
            weightedValue = bestOutcome.eval;

            switch (bestOutcome.type) {
                case EXACT:
                    // transposition has a deeper or equal search depth
                    // we can stop here as we already know the value
                    // returned by the evaluation function
                    return bestOutcome;
                case LOWER_BOUND:
                    a = Math.max(a, weightedValue);
                    break;
                case UPPER_BOUND:
                    b = Math.min(b, weightedValue);
                    break;
            }

            if (b <= a) {
                return bestOutcome;
            }
        }

        outcome = super._alphaBetaPruning(tree, shouldMaximize, a, b, depth, depthLeft, endTime);
        weightedValue = outcome.getWeightedValue();

        if (System.currentTimeMillis() > endTime) {
            if( DEBUG_SHOW_INFO )
                Debug.println("Exiting quickly");
            throw new TimeoutException("Exiting quickly");
        }

        if( bestOutcome != null && depth >= bestOutcome.depth ) {
            // minimize
            if (outcome.eval <= a) { // weightedValue <= a
                // b = outcome.getWeightedValue();
                cachedResults.put(key, new CachedResult(outcome, CachedResult.ValueType.UPPER_BOUND, key));
            }
            // maximize
            else if (outcome.eval >= b) { // weightedValue >= b
                // a = outcome.getWeightedValue();
                cachedResults.put(key, new CachedResult(outcome, CachedResult.ValueType.LOWER_BOUND, key));
            } else {
                cachedResults.put(key, new CachedResult(outcome, CachedResult.ValueType.EXACT, key));
            }
        }

/*
        if( depth == 0 ) {
            cachedResults.remove( key );
        }

 */
        return outcome;
    }

    @Override
    public String playerName() {
        return "Cacher";
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
