package player;

import mnkgame.MNKCell;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.TreeMap;

/**
 * This class assign a transposition table for each bucket.
 * The max buckets count is n = M * N.
 * Each bucket contains all possible tree's nodes states per round (tree's depth).
 * The k-th bucket could have up to n!/(n-k)! evaluated nodes (called "Transposition"). ( k=1: n; k=2: n(n-1); ...; k=n: n! )-
 * This class implements an optimization: removes unnecessary buckets so free up memory.
 *
 * On round k, the  k-1 buckets, which store the old transpositions, are useless.
 * So the (k-1)-th bucket will be removed
 * on {@link #postSearch()} if {@link #isStateValid()} == false
 * or on {@link #restore} otherwise
 */
public class TranspositionSearchMoveStrategy extends IterativeDeepeningSearchMoveStrategy {

    protected HashableBoardState currentState;
    protected Utils.MatrixRowMap matrixMap;
    protected TreeMap<Integer, HashMap<BigInteger, TranspositionData>> transpositionsMap;
    protected int currentTableSize;
    protected int maxTableSize = 1 << 20; // fixed

    @Override
    public void init(int M, int N, int K, boolean first, int timeout_in_secs) {
        super.init(M, N, K, first, timeout_in_secs);

        currentTableSize = (M * N);

        matrixMap = new Utils.MatrixRowMap(M, N);
        currentState = new HashableBoardState();
        transpositionsMap = new TreeMap<>(Integer::compare);

        final int initialTablesCountToInit = M * N;
        for( int round = 1; round <= initialTablesCountToInit; round++ )
            initTranspositionTableForRoundIfAbsent( round );
    }

    /**
     * Return the k-th transposition table associated to the k-th bucket, relative to k-th round.
     * If doesn't exists, then this will create and assign it first with a starting capacity.
     * @implNote Cost <ul>
     *      <li>Time: <code>T(#{@link TreeMap#get})=</code>
     *      <ul>
     *          <li><code>O( log( M*N-{@link #round}) ) = O({@link #maxDepthSearch})</code> if {@link #flushTranspositionTable()} is called before the next {@link #alphaBetaPruning} call</li>
     *          <li><code>O( log( M*N ) )</code> otherwise</li>
     *      </ul>
     *      </li>
     * </ul>
     * @see IterativeDeepeningSearchMoveStrategy#initSearch
     * @return The k-th transposition table
     */
    protected HashMap<BigInteger, TranspositionData> getTranspositionTable() {
        return initTranspositionTableForRoundIfAbsent(getSimulatedRound());
    }

    private HashMap<BigInteger, TranspositionData> initTranspositionTableForRoundIfAbsent( int round ) {
        final int movesLeft = (currentBoard.M * currentBoard.N) - round;
        return transpositionsMap.computeIfAbsent(round, age -> {
            // limit table size growing // n * (n-1)! ... up to maxTableSize
            if( currentTableSize < maxTableSize ) {
                try {
                    currentTableSize = Math.multiplyExact(currentTableSize, movesLeft);
                    if( currentTableSize > maxTableSize ) currentTableSize = maxTableSize;
                }
                catch (ArithmeticException ex) {
                    currentTableSize = maxTableSize;
                }
                return new HashMap<>(currentTableSize+1, 1);
            }
            else {
                return new HashMap<>(maxTableSize+1, 1);
            }
        });
    }

    /**
     * Clear, remove and free up the transposition table associated up to the k-th bucket, relative to the real k-th round
     * @PreCondition: {{@link #isStateValid()}} must be true before calling this
     * @implNote Cost <ul>
     *      <li>Time: <code>T(max { {@link TreeMap#headMap}, {@link TreeMap#remove} }) = O( log( M*N ) )</code></li>
     * </ul>
     */
    protected void flushTranspositionTable() {
        transpositionsMap.headMap(round, true).clear();
    }

    @Override
    public void initSearch(MNKCell[] FC, MNKCell[] MC) {
        super.initSearch(FC, MC);

        flushTranspositionTable();
    }

    @Override
    public void postSearch() {
        super.postSearch();

        // The flushing may require some time, so it will be done on next round if we just have been out of time
        if( isStateValid() ) {
            flushTranspositionTable();
        }
    }

    @Override
    public void mark(MNKCell marked) {
        int markingPlayer = currentBoard.currentPlayer();
        currentState.toggle(markingPlayer, matrixMap.getArrayIndexFromMatrixIndexes(marked.i, marked.j) );
        super.mark(marked);
    }

    @Override
    public void unMark() {
        if(currentBoard.getMarkedCellsCount() > 0) {
            MNKCell oldc = currentBoard.getLastMarked();
            int unMarkingPlayer = Utils.getPlayerIndex(oldc.state);
            currentState.toggle(unMarkingPlayer, matrixMap.getArrayIndexFromMatrixIndexes(oldc.i, oldc.j) );
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

        if( outcome2.state == STATE_WIN )
            return outcome2;
        else
            return outcome;
    }

    @Override
    protected AlphaBetaOutcome alphaBetaPruning(boolean shouldMaximize, int a, int b, int depth, int depthLeft, long endTime) {
        // int aOriginal = a, bOriginal = b;

        BigInteger key = currentState.getCurrentState();
        AlphaBetaOutcome outcome;
        HashMap<BigInteger, TranspositionData> table = getTranspositionTable();
        TranspositionData bestOutcome = table.get( key );

        if (bestOutcome != null && depthLeft <= bestOutcome.depth && key.equals(bestOutcome.boardState) ) {
            int score = bestOutcome.eval;

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

        if( isEarlyExitStarted ) return outcome;

        // minimize
        if (outcome.eval <= a) {
            table.put(key, new TranspositionData(outcome, depthLeft, TranspositionData.ValueType.UPPER_BOUND, key));
        }
        // maximize
        else if (b <= outcome.eval) {
            table.put(key, new TranspositionData(outcome, depthLeft, TranspositionData.ValueType.LOWER_BOUND, key));
        }
        else {
            table.put(key, new TranspositionData(outcome, depthLeft, TranspositionData.ValueType.EXACT, key));
        }

        return outcome;
    }

    public static class TranspositionData extends AlphaBetaOutcome {

        public enum ValueType {
            EXACT,
            UPPER_BOUND,
            LOWER_BOUND
        }

        public ValueType type;
        public BigInteger boardState;
        public int depth;

        TranspositionData(AlphaBetaOutcome o, int depth, ValueType type, BigInteger boardState) {
            super(o);
            this.type = type;
            this.boardState = boardState;
            this.depth = depth;
        }
    }
}
