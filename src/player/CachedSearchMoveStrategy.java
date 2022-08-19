package player;

import java.math.BigInteger;
import java.util.HashMap;

public class CachedSearchMoveStrategy extends IterativeDeepeningSearchMoveStrategy {

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
            setBoard(new StatefulBoard(M, N, K));
        }
        catch (Throwable e ) {
            Debug.println("Error on init board " + e);
        }
    }

    protected void setBoard(StatefulBoard board) {
        this.currentBoard = board;
        super.setBoard(board);
    }

    @Override
    protected AlphaBetaOutcome alphaBetaPruning(boolean shouldMaximize, int alpha, int beta, int depth, int depthLeft, long endTime) {

        int a = alpha;
        int b = beta;
        BigInteger key = currentBoard.getCurrentState();
        AlphaBetaOutcome outcome;

        CachedResult bestOutcome = this.cachedResults.get( key );
        int score;
        if (bestOutcome != null && depthLeft <= bestOutcome.depth && key.equals(bestOutcome.boardState) ) {
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

        CachedResult( AlphaBetaOutcome o, int depth, ValueType type, BigInteger boardState ) {
            super(o);
            this.type = type;
            this.boardState = boardState;
            this.depth = depth;
        }
    }
}
