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
    protected AlphaBetaOutcome alphaBetaPruning(WeightedMNKBoard tree, boolean shouldMaximize, float alpha, float beta, int depth, int depthLeft, long endTime) {

        float a = alpha;
        float b = beta;
        BigInteger key = tree.getCurrentState();
        AlphaBetaOutcome outcome;

        CachedResult result = this.cachedResults.get( key );
        float weightedValue;
        if (result != null && depth <= result.depth ) {
            weightedValue = result.getWeightedValue();
            switch (result.type) {
                case EXACT:
                    // transposition has a deeper or equal search depth
                    // we can stop here as we already know the value
                    // returned by the evaluation function
                    return result;
                case LOWER_BOUND:
                    if ( weightedValue > alpha) {
                        a = weightedValue;
                    }
                    break;
                case UPPER_BOUND:
                    if ( weightedValue < beta ) {
                        b = weightedValue;
                    }
                    break;
            }

            if (b <= a) {
                return result;
            }
        }

        outcome = super.alphaBetaPruning(tree, shouldMaximize, a, b, depth, depthLeft, endTime);
        weightedValue = outcome.getWeightedValue();
        if (weightedValue <= a) {
            cachedResults.put( key, new CachedResult( outcome, CachedResult.ValueType.UPPER_BOUND ) );
            // saveTransposition(key, transposition, outcome.eval, depth, FLAG_UPPERBOUND);
        }
        else if (weightedValue >= beta) {
            cachedResults.put( key, new CachedResult( outcome, CachedResult.ValueType.LOWER_BOUND ) );
            // saveTransposition(key, transposition, score, depth, FLAG_LOWERBOUND);
        }
        else {
            cachedResults.put( key, new CachedResult( outcome, CachedResult.ValueType.EXACT ) );
            // saveTransposition(key, transposition, score, depth, FLAG_EXACT);
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
