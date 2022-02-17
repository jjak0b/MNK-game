package mnkgame;

import java.util.*;

public class MyPlayer extends AlphaBetaPruningPlayer {

    protected float estimatedPercentOfTimeRequiredToExit;

    protected WeightedMNKBoard currentBoard;
    // protected UnionFind<MNKCell>[][] directionCombosOfPlayer;

    protected int[][] corners;

    boolean isCurrentBoardLeftInValidState;


    MyPlayer() {

    }

    /**
     * Initialize the (M,N,K) Player
     *
     * @param M               Board rows
     * @param N               Board columns
     * @param K               Number of symbols to be aligned (horizontally, vertically, diagonally) for a win
     * @param first           True if it is the first player, False otherwise
     * @param timeout_in_secs Maximum amount of time (in seconds) for selectCell
     */
    public void initPlayer(int M, int N, int K, boolean first, int timeout_in_secs) {
        // New random seed for each game

        super.initPlayer(M, N, K, first, timeout_in_secs);


        corners = new int[][]{ {0, 0}, {0, currentBoard.N-1}, {currentBoard.M-1, 0}, {currentBoard.M-1, currentBoard.N-1} };
        maxDepthSearch = 6;
        estimatedPercentOfTimeRequiredToExit = 5f/100f;

    }
    // prioritize cells to pick based on a sort of heatmap
    @Override
    protected void initTrackingBoard(int M, int N, int K) {
        currentBoard = new WeightedMNKBoard(M,N,K);
        super.currentBoard = currentBoard;
        isCurrentBoardLeftInValidState = true;
    }

    protected MNKCell strategyAsFirst(MNKCell[] FC, MNKCell[] MC) {
//      return FC[rand.nextInt(FC.length)]; // random
        int[] coords = corners[rand.nextInt( corners.length ) ];
        System.out.println( "First Move: Move to a corner");
        coords = corners[ 1 ]; // constant for debug
        return new MNKCell( coords[0], coords[1] );
    }

    protected MNKCell strategyAsSecond(MNKCell[] FC, MNKCell[] MC) {
        for (int i = 0; i < corners.length; i++) {
            if( MC[0].i == corners[i][0] && MC[0].j == corners[i][1] ) {
                System.out.println( "Detected Corner strategy for enemy, use middle position");
                return new MNKCell( (currentBoard.M >> 1), (currentBoard.N >> 1) );
            }
        }
        return null;
    }

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
        MNKCell choice = null;
        if( !isCurrentBoardLeftInValidState) {
            currentBoard = new WeightedMNKBoard(currentBoard.M, currentBoard.N, currentBoard.K, MC );
            isCurrentBoardLeftInValidState = true;
        }
        else {
            if (MC.length > 0) {
                choice = MC[MC.length - 1]; // Save the last move in the local MNKBoard
                currentBoard.markCell(choice.i, choice.j);
                ++round;
                choice = null;
            }
        }

        switch ( MC.length ){
            case 0: // move as first
                choice = strategyAsFirst(FC, MC);
                break;
//            case 1: // move as second
//                choice = strategyAsSecond(FC, MC);
//                break;
        }

        if( choice != null ) {
            System.out.println("test" + choice);
            currentBoard.markCell(choice.i, choice.j);
            return choice;
        }

        // System.out.println( "before move:\n" + currentBoard.toString() );
        long start = System.currentTimeMillis();
        long endTime = start + (long) ( timeout * (99.0/100.0));
        long expectedTimeRequiredToExit = (long) (estimatedPercentOfTimeRequiredToExit * timeout);
        long workTime = start + ( timeout - expectedTimeRequiredToExit );

        // TODO: check with start @ (5, 4 ) in 7 7 4
        isCurrentBoardLeftInValidState = false;
                // Good: 6 6 4, 7 7 4 -> moveleft = 5
        AlphaBetaOutcome outcome = alphaBetaPruning(
                currentBoard,
                true,
                STANDARD_SCORES.get(STATE_LOSE),
                STANDARD_SCORES.get(STATE_WIN),
                0,
                maxDepthSearch,
                workTime
        );
        long end = System.currentTimeMillis();
        long elapsed = end-start;
        long timeLeft = (endTime-end);

        printStats(outcome, elapsed, timeLeft);

        // System.out.println( Arrays.toString( currentBoard.getFreeCells() ) );
        // System.out.println( "if " + currentBoard.currentPlayer()  + " choose " +  outcome.move + " -> " + outcome.eval );
        currentBoard.markCell( outcome.move.i, outcome.move.j );
        System.out.println( "after move:\n" + currentBoard.toString() );

        round++;
        isCurrentBoardLeftInValidState = true;
        return outcome.move;
    }

    private float evaluateHeuristicWeights( WeightedMNKBoard board ) {
        float score = 0f;
        MNKCell mostWeightedCell = board.getWeightedFreeCellsHeap(board.currentPlayer()).peek();
        MNKCell lastMark = board.MC.peekLast();
        int [][] weights = board.getWeights(board.currentPlayer());
        int mod = lastMark.state == MY_MARK_STATE ? 1 : -1;
        int diff = weights[ mostWeightedCell.i ][ mostWeightedCell.j ] - weights[ lastMark.i ][ lastMark.j ];
        // diff >= 0 ? last picked most important choice : the least choice

        score += -diff;
        score *= mod;

        return score;
    }

    @Override
    protected List<MNKCell> getCellCandidates(MNKBoard currentBoard) {
        WeightedMNKBoard board = (WeightedMNKBoard) currentBoard;
        // first should be cells that are in sequence that have 1 move left to win
        // after should be cells that are in sequence that have 2 move left to win
        PriorityQueue<MNKCell> pq = board.getWeightedFreeCellsHeap(board.currentPlayer());

        ArrayList<MNKCell> list = new ArrayList<>( pq );
        Collections.sort( list, pq.comparator() );

        return list;
    }

    static String getPlayerByIndex( int index ) {
        if( index == 0 ) {
            return "X";
        }
        else {
            return "O";
        }
    }

    static String getTabForDepth( int depth ) {

        String tabs = "";
        for (int i = 0; i < depth; i++) {
            tabs += "\t";
        }
        return tabs;
    }

    private MNKCell[] getMyMarkedCells( MNKCell[] MC ) {
        return (MNKCell[]) Arrays.stream(MC).filter(c -> c.state == MY_MARK_STATE).toArray();
    }

    static void printUntil( Object o, Object[] ao ) {
        ArrayList<Object> filter = new ArrayList<Object>();

        String s = "";
        for ( Object e : ao ) {
            filter.add( e );
            if( e == o) {
                break;
            }
        }
        // System.out.println( Arrays.toString( filter.toArray() ) );
    }
    /**
     * Returns the player name
     *
     * @return string
     */
    @Override
    public String playerName() {
        return "MyPlayer";
    }
}

