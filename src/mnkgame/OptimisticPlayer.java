package mnkgame;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.concurrent.TimeoutException;

public class OptimisticPlayer extends MyPlayer{

    private HashMap<BigInteger, mnkgame.GoodMemoryPlayer.CachedResult> cachedResults;

    @Override
    public void initPlayer(int M, int N, int K, boolean first, int timeout_in_secs) {
        super.initPlayer(M, N, K, first, timeout_in_secs);
        cachedResults = new HashMap<>((int) Math.ceil((M*N*K) / 0.75));
        // maxDepthSearch = 20;
    }

    @Override
    protected AlphaBetaOutcome alphaBetaPruning(MNKBoard board, boolean shouldMaximize, int alpha, int beta, int depth, int depthLeft, long endTime) {

        StatefulBoard tree = (StatefulBoard) board;
        int a = alpha;
        int b = beta;
        BigInteger key = tree.getCurrentState();
        AlphaBetaOutcome outcome;

        mnkgame.GoodMemoryPlayer.CachedResult bestOutcome = this.cachedResults.get( key );
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

        outcome = super.alphaBetaPruning(tree, shouldMaximize, a, b, depth, depthLeft, endTime);
/*
        if (System.currentTimeMillis() > endTime) {
            if( DEBUG_SHOW_INFO )
                Debug.println("Exiting quickly");
            return outcome;
        }
*/
        if( bestOutcome != null && depth >= bestOutcome.depth ) {
            // minimize
            if (outcome.eval <= a) {
                cachedResults.put(key, new mnkgame.GoodMemoryPlayer.CachedResult(outcome, mnkgame.GoodMemoryPlayer.CachedResult.ValueType.UPPER_BOUND, key));
            }
            // maximize
            else if (outcome.eval >= b) {
                cachedResults.put(key, new mnkgame.GoodMemoryPlayer.CachedResult(outcome, mnkgame.GoodMemoryPlayer.CachedResult.ValueType.LOWER_BOUND, key));
            }
            else {
                cachedResults.put(key, new mnkgame.GoodMemoryPlayer.CachedResult(outcome, mnkgame.GoodMemoryPlayer.CachedResult.ValueType.EXACT, key));
            }
        }

        return outcome;
    }

    @Override
    public String playerName() {
        return "Optimistic Cacher";
    }
}
