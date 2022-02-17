package mnkgame;

import java.math.BigInteger;
import java.util.HashMap;

public class GoodMemoryPlayer extends MyPlayer {

    private HashMap<BigInteger, CachedResult> cachedResults;

    @Override
    public void initPlayer(int M, int N, int K, boolean first, int timeout_in_secs) {
        super.initPlayer(M, N, K, first, timeout_in_secs);
        cachedResults = new HashMap<>((int) Math.ceil((M*N) / 0.75));
    }

    @Override
    public MNKCell selectCell(MNKCell[] FC, MNKCell[] MC) {
        return super.selectCell(FC, MC);
    }


    @Override
    protected AlphaBetaOutcome alphaBetaPruning(MNKBoard board, boolean shouldMaximize, int alpha, int beta, int depth, int depthLeft, long endTime) {

        WeightedMNKBoard tree = (WeightedMNKBoard) board;
        int a = alpha;
        int b = beta;
        BigInteger key = tree.getCurrentState();
        AlphaBetaOutcome outcome;

        CachedResult bestOutcome = this.cachedResults.get( key );
        int weightedValue;
        if (bestOutcome != null && depth >= bestOutcome.depth ) {
            weightedValue = bestOutcome.getWeightedValue();

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

        outcome = super.alphaBetaPruning(tree, shouldMaximize, a, b, depth, depthLeft, endTime);
        weightedValue = outcome.getWeightedValue();

        // minimize
        if ( (bestOutcome == null || outcome.compareTo(bestOutcome) < 0) ) { // weightedValue <= a
            // b = outcome.getWeightedValue();
            cachedResults.put( key, new CachedResult( outcome, CachedResult.ValueType.UPPER_BOUND ) );
        }
        // maximize
        else if ((bestOutcome == null || outcome.compareTo(bestOutcome) > 0) ) { // weightedValue >= b
            // a = outcome.getWeightedValue();
            cachedResults.put( key, new CachedResult( outcome, CachedResult.ValueType.LOWER_BOUND ) );
        }
        else {
            cachedResults.put( key, new CachedResult( outcome, CachedResult.ValueType.EXACT ) );
        }


        if( depth == 0 ) {
            cachedResults.remove( key );
        }
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

        CachedResult( AlphaBetaOutcome o, ValueType type ) {
            super(o);
            this.type = type;
        }

        CachedResult( float value, ValueType type, int depth ) {
            super();
            this.type = type;
            this.depth = depth;
        }
    }
}
