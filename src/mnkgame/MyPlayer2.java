package mnkgame;

import java.util.Arrays;
import java.util.Iterator;
import java.util.PriorityQueue;

public class MyPlayer2 extends AlphaBetaPruningPlayer implements BoardRestorable {
    protected StatefulBoard currentBoard;
    boolean isCurrentBoardLeftInValidState;
    float estimatedPercentOfTimeRequiredToExit;
    int[][] corners;

    // DEBUG
    public static final boolean DEBUG_SHOW_STREAKS = Debug.Player.DEBUG_SHOW_STREAKS;
    public static final boolean DEBUG_SHOW_USEFUL = Debug.Player.DEBUG_SHOW_USEFUL;
    public static final boolean DEBUG_SHOW_WEIGHTS = Debug.Player.DEBUG_SHOW_WEIGHTS;
    public static final boolean DEBUG_SHOW_CANDIDATES = Debug.Player.DEBUG_SHOW_CANDIDATES;
    public static final boolean DEBUG_START_FIXED_MOVE = Debug.Player.DEBUG_START_FIXED_MOVE;

    protected final Scan2ThreatDetectionLogic threatDetectionLogic = new Scan2ThreatDetectionLogic();

    public MyPlayer2() {
        super();
    }

    public ThreatDetectionLogic<? extends ThreatInfo> getThreatDetectionLogic() {
        return threatDetectionLogic;
    }

    @Override
    public boolean isStateValid() {
        return isCurrentBoardLeftInValidState;
    }

    @Override
    public void setInValidState() {
        isCurrentBoardLeftInValidState = true;
    }

    @Override
    public void invalidateState() {
        isCurrentBoardLeftInValidState = false;
    }

    private int[][] getUsefulnessWeights(int p, int directionType) {
        return threatDetectionLogic.getMovePriority(p, directionType);
        // return threatDetectionLogic.getMovesLeft(p, directionType);
    }

    private int[][] getWeights() {
        // return threatDetectionLogic.getMinMovesLeft();
        return threatDetectionLogic.freeCellsPrioritiesCache;
    }

    @Override
    public void initPlayer(int M, int N, int K, boolean first, int timeout_in_secs) {
        super.initPlayer(M, N, K, first, timeout_in_secs);
        estimatedPercentOfTimeRequiredToExit = 5f/100f;
        corners = new int[][]{ {0, 0}, {0, N-1}, {M-1, 0}, {M-1, N-1} };
        maxDepthSearch = 6;

        threatDetectionLogic.init(M, N, K);

        initTrackingBoard(M, N, K);
        setInValidState();
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
    protected AlphaBetaOutcome evaluate(MNKBoard board, int depth, boolean isMyTurn) {
        MNKGameState gameState = board.gameState();
        AlphaBetaOutcome outcome = new AlphaBetaOutcome();

        int score = STANDARD_SCORES.get(gameState);

        int opponentIndex = 1-playerIndex;

        if (gameState == MNKGameState.OPEN) { // game is open
            final int[] playerScores = new int[2];
            final int[] maxPlayerThreatScores = {0, 0};
            // final int[] totalPlayerScores = {0, 0};
            final int[] weight = new int[]{ 100000, 100000, 1000, 1 };

            for (int indexPlayer = 0; indexPlayer < 2; indexPlayer++) {
                for ( int directionType : Utils.DIRECTIONS ) {
                    // computes players score based on moves left count and their weight
                    int[] movesLeft = threatDetectionLogic.getMovesLeftArrayCount(indexPlayer, directionType);
                    for (int c = 1; c < weight.length && c < movesLeft.length; c++) {
                        playerScores[ indexPlayer ] += movesLeft[ c ] * weight[c];
                    }
                    // computes players score based on best max threat's score
                    Scan2ThreatDetectionLogic.ThreatT bestPlayerThreat = threatDetectionLogic.getBestThreat(indexPlayer, directionType);
                    if( bestPlayerThreat != null ) {
                        // totalPlayerScores[indexPlayer] += bestPlayerThreat.getScore();
                        maxPlayerThreatScores[indexPlayer] = Math.max(maxPlayerThreatScores[indexPlayer], bestPlayerThreat.getScore());
                    }
                }
            }

            score += playerScores[ playerIndex ] - playerScores[ opponentIndex ];
            score += maxPlayerThreatScores[playerIndex] - maxPlayerThreatScores[opponentIndex];
            // this heuristic below is very similar and statistically it's just a little better
            // but because sometimes wins more if starting as second
            // score += totalPlayerScores[playerIndex] - totalPlayerScores[opponentIndex]
        }

        if( score >= depth)
            score -= depth-1;
        else if( score <= -depth)
            score += depth-1;

        outcome.eval = score;
        outcome.depth = depth;
        return outcome;
    }

    /*
        @Override
        protected AlphaBetaOutcome evaluate(MNKBoard board, int depth, boolean isMyTurn) {
            MNKGameState gameState = board.gameState();
            AlphaBetaOutcome outcome = new AlphaBetaOutcome();

            int score = STANDARD_SCORES.get(gameState);

            int opponentIndex = 1-playerIndex;

            final int[] maxPlayerThreatScores = {0, 0};
            if (gameState == MNKGameState.OPEN) { // game is open
                // final int[] totalPlayerScores = {0, 0};
                for (int indexPlayer = 0; indexPlayer < 2; indexPlayer++) {
                    for ( int directionType : Utils.DIRECTIONS ) {

                        Scan2ThreatDetectionLogic.ThreatT bestPlayerThreat = threatDetectionLogic.getBestThreat(indexPlayer, directionType);
                        if( bestPlayerThreat != null ) {
                            // totalPlayerScores[indexPlayer] += bestPlayerThreat.getScore();
                            maxPlayerThreatScores[indexPlayer] = Math.max(maxPlayerThreatScores[indexPlayer], bestPlayerThreat.getScore());
                        }
                    }
                }
            }

            score += maxPlayerThreatScores[playerIndex] - maxPlayerThreatScores[opponentIndex];
            // this heuristic below is very similar and statistically it's just a little better
            // but because sometimes wins more if starting as second
            // score += totalPlayerScores[playerIndex] - totalPlayerScores[opponentIndex]

            if( score >= depth)
                score -= depth-1;
            else if( score <= -depth)
                score += depth-1;

            outcome.eval = score;
            outcome.depth = depth;
            return outcome;
        }
    */


    /**
     * Select a position among those listed in the <code>FC</code> array
     *
     * @param FC Free Cells: array of free cells
     * @param MC Marked Cells: array of already marked cells, ordered with respect
     *           to the game moves (first move is in the first position, etc)
     * @return an element of <code>FC</code>
     */
    @Override
    public MNKCell selectCell(MNKCell[] FC, MNKCell[] MC) {
        AlphaBetaOutcome outcome = null;
        long elapsed = 0;
        long startTime = System.currentTimeMillis();
        long endTime = startTime + (long) ( timeout * (99.0/100.0));

        if( !isStateValid()) {
            if( DEBUG_SHOW_INFO )
                Debug.println(Utils.ConsoleColors.YELLOW + "Start Restoring current state");
            restore(FC, MC);
            if( DEBUG_SHOW_INFO )
                Debug.println(Utils.ConsoleColors.YELLOW + "End Restoring current state, time spent: " + (elapsed/1000.0) + Utils.ConsoleColors.RESET );
            setInValidState();
        }
        else {
            MNKCell choice = null;
            if (MC.length > 0) {
                choice = MC[MC.length - 1]; // Save the last move in the local MNKBoard
                mark(currentBoard, choice, 0);
                ++round;
                choice = null;
            }
        }
        if( DEBUG_SHOW_BOARD )
            Debug.println( "after opponent move:\n" + boardToString() );
        elapsed += System.currentTimeMillis() - startTime;

        startTime = System.currentTimeMillis();
        // pre calculate expected work time
        long expectedTimeRequiredToExit = (long) (estimatedPercentOfTimeRequiredToExit * timeout);
        long workTime = endTime;
        workTime -= elapsed;
        workTime -= expectedTimeRequiredToExit;

        // set in invalid state, because if running out time, this function may be terminated
        invalidateState();
        switch ( MC.length ){
            case 0: // move as first
                outcome = strategyAsFirst(FC, MC, workTime);
                break;
//            case 1: // move as second
//                choice = strategyAsSecond(FC, MC);
//                break;
            default:
                if ( DEBUG_SHOW_CANDIDATES )
                    Debug.println("Candidates: " + getCellCandidates(currentBoard));
                // TODO: check with start @ (5, 4 ) in 7 7 4
                // Good: 6 6 4, 7 7 4 -> moveleft = 5
                outcome = alphaBetaPruning(
                        currentBoard,
                        true,
                        STANDARD_SCORES.get(STATE_LOSE),
                        STANDARD_SCORES.get(STATE_WIN),
                        0,
                        maxDepthSearch,
                        workTime
                );
                break;
        }
        // we returned so assuming all right
        setInValidState();

        elapsed += System.currentTimeMillis()-startTime;
        long timeLeft = (endTime-elapsed);

        if( Debug.DEBUG_ENABLED ) {
            if(!isStateValid()){
                if( DEBUG_SHOW_INFO )
                    Debug.println(Utils.ConsoleColors.YELLOW + "Start Restoring current state");
                restore(FC, MC);
                if( DEBUG_SHOW_INFO )
                    Debug.println(Utils.ConsoleColors.YELLOW + "End Restoring current state" + Utils.ConsoleColors.RESET );
            }
        }

        if( isStateValid() )
            mark(currentBoard, outcome.move, 0);

        if( DEBUG_SHOW_STATS )
            printStats(outcome, elapsed, timeLeft);

        if( DEBUG_SHOW_BOARD )
            Debug.println( "after move:\n" + boardToString() );
        if( Debug.DEBUG_ENABLED && currentBoard.gameState() != MNKGameState.OPEN ){
            Debug.println( "Final board:\n" + boardToString() );
        }

        round++;

        return outcome.move;
    }

    @Override
    public void mark(MNKBoard tree, MNKCell marked, int depth) {
        if (tree == null) tree = currentBoard;

        int markingPlayer = tree.currentPlayer();
        super.mark(tree, marked, depth);

        marked = tree.MC.getLast();
        MNKCellState markState = marked.state;

        getThreatDetectionLogic().mark(currentBoard, marked, markingPlayer, depth);

    }

    @Override
    public void unMark(MNKBoard tree, int depth) {
        if (tree == null) tree = currentBoard;
        MNKCell marked = tree.MC.getLast();
        MNKCellState markState = currentBoard.cellState(marked.i, marked.j);

        super.unMark(tree, depth);
        int unMarkingPlayer = tree.currentPlayer();

        getThreatDetectionLogic().unMark(currentBoard, marked, unMarkingPlayer, depth);
    }

    @Override
    public Iterable<MNKCell> getCellCandidates(MNKBoard board) {
        return new Iterable<>() {
            final PriorityQueue<MNKCell> queue = threatDetectionLogic.getFree();
            final Iterator<MNKCell> iterator = new Iterator<>() {
                @Override
                public boolean hasNext() {
                    return !queue.isEmpty();
                }

                @Override
                public MNKCell next() {
                    return queue.poll();
                }
            };

            @Override
            public Iterator<MNKCell> iterator() {
                return iterator;
            }

            @Override
            public String toString() {
                return Arrays.toString(queue.toArray(new MNKCell[0]));
            }
        };
    }

    protected AlphaBetaOutcome strategyAsFirst(MNKCell[] FC, MNKCell[] MC, long endTime) {
        if( DEBUG_START_FIXED_MOVE ) {
            int[] coords = corners[ 1 ]; // constant for debug
            if( DEBUG_SHOW_INFO )
                Debug.println( "First Move: Move to a fixed corner");
            AlphaBetaOutcome outcome = new AlphaBetaOutcome();
            outcome.move = new MNKCell( coords[0], coords[1] ); outcome.depth = 0; outcome.eval = 0;
            return outcome;
        }
        else {
            if( DEBUG_SHOW_INFO )
                Debug.println( "First Move: Move to a fixed corner");
            AlphaBetaOutcome outcome = new AlphaBetaOutcome();
            outcome.move = new MNKCell( currentBoard.M / 2, currentBoard.N/2 ); outcome.depth = 0; outcome.eval = 0;
            return outcome;

            /*return alphaBetaPruning(
                    currentBoard,
                    true,
                    STANDARD_SCORES.get(STATE_LOSE),
                    STANDARD_SCORES.get(STATE_WIN),
                    0,
                    maxDepthSearch,
                    endTime
            );*/
        }
    }

    protected void restoreTrackingBoard(MNKCell[] FC, MNKCell[] MC) {
        // we suppose currentBoard.MC.size() >= MC.length

        // we have to restore last valid state, so without last enemy move and this player's last move
        int countToMark = 2;
        int countMCBeforeInvalid = MC.length - countToMark;
        int countToUnMark = currentBoard.MC.size()-countMCBeforeInvalid;

        // then un-mark all until we reach the old valid state.
        for (int i = 0; i < countToUnMark; i++) {
            unMark(currentBoard, -1);
        }
        // and then mark to current state
        for (int i = 0; i < countToMark; i++) {
            mark(currentBoard, MC[ countMCBeforeInvalid + i ], -1);
        }
    }

    @Override
    public void restore(MNKCell[] FC, MNKCell[] MC) {
        restoreTrackingBoard(FC, MC);
        setInValidState();
    }

    @Override
    public String boardToString() {
        String s = "";

        int[][] weights = getWeights();

        if( DEBUG_SHOW_USEFUL ) {
            for (int p = 0; p < 2; p++) {
                s += "Usefulness for p" + (p + 1) + ":\n";
                for (int i = 0; i < currentBoard.B.length; i++) {
                    for (int directionType : Utils.DIRECTIONS) {
                        s += boardToString(currentBoard.B[i], getUsefulnessWeights(p, directionType)[i], currentBoard.K) + "\t\t\t";
                    }
                    s += "\n";
                }
                s += "\n";
            }
        }

        for (int i = 0; i < currentBoard.B.length; i++) {
            s += Utils.toString(currentBoard.B[i]) + "\t\t\t";
            if( DEBUG_SHOW_WEIGHTS) {
                for (int p = 0; p < 2; p++) {
                    s += boardToString(currentBoard.B[i], weights[i], currentBoard.K) + "\t\t\t";
                }
            }
            s += "\n";
        }
        return s;
    }

    public static String boardToString(MNKCellState[] states, int[] weights, int max) {
        String[] cells = new String[weights.length];
        for (int i = 0; i < weights.length; i++) {
            cells[i] = "";
            int index;
            int aColorSpace = Math.max (1, max / Utils.ConsoleColors.RAINBOW.length);

            boolean shouldColor = Debug.DEBUG_USE_COLORS && (states == null || states[i] == MNKCellState.FREE);
            String color = Utils.ConsoleColors.RESET;
            if( shouldColor ) {
                index = weights[i] / aColorSpace;
                index = Math.max(0, (Utils.ConsoleColors.RAINBOW.length - 1) - index);
                index = Math.min(Utils.ConsoleColors.RAINBOW.length - 1, index);

                color = Utils.ConsoleColors.RAINBOW[index];
            }

            if(Debug.DEBUG_USE_COLORS)
                cells[i] += color + weights[i] + Utils.ConsoleColors.RESET;
            else
                cells[i] += weights[i];

        }
        return Arrays.toString(cells);
    }

    @Override
    public String playerName() {
        return "Hello2" ;
    }
}
