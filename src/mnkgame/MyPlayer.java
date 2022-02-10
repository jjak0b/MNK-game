package mnkgame;

import java.util.*;

public class MyPlayer implements MNKPlayer {
    protected Random rand;
    protected int timeout;
    protected int maxDepthSearch;
    protected float estimatedPercentOfTimeRequiredToExit;

    protected WeightedMNKBoard currentBoard;
    // protected UnionFind<MNKCell>[][] directionCombosOfPlayer;

    protected MNKGameState STATE_WIN;
    protected MNKGameState STATE_LOSE;
    protected MNKCellState MY_MARK_STATE;
    protected MNKCellState ENEMY_MARK_STATE;
    protected int playerIndex;
    protected int[][] corners;

    boolean isCurrentBoardLeftInValidState;

    public EnumMap<MNKGameState, Integer> STANDARD_SCORES;


//     PriorityQueue<MNKCell> bestMoves;

//    private float[][] movesScores;

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

        rand    = new Random(System.currentTimeMillis());
        // prioritize cells to pick based on a sort of heatmap
        initTrackingBoard(M, N, K);

        if( first ) {
            STATE_WIN = MNKGameState.WINP1;
            STATE_LOSE = MNKGameState.WINP2;
            MY_MARK_STATE = MNKCellState.P1;
            ENEMY_MARK_STATE = MNKCellState.P2;
        }
        else {
            STATE_WIN = MNKGameState.WINP2;
            STATE_LOSE = MNKGameState.WINP1;
            MY_MARK_STATE = MNKCellState.P2;
            ENEMY_MARK_STATE = MNKCellState.P1;
        }

        timeout = timeout_in_secs * 1000;
        playerIndex = first ? 0 : 1;
        corners = new int[][]{ {0, 0}, {0, currentBoard.N-1}, {currentBoard.M-1, 0}, {currentBoard.M-1, currentBoard.N-1} };
        // movesScores = new float[M][N];
        maxDepthSearch = 6;
        estimatedPercentOfTimeRequiredToExit = 5f/100f;

        STANDARD_SCORES = new EnumMap<>(MNKGameState.class);
        STANDARD_SCORES.put(STATE_WIN, Integer.MAX_VALUE);
        STANDARD_SCORES.put(STATE_LOSE, Integer.MIN_VALUE);
        STANDARD_SCORES.put(MNKGameState.DRAW, 0);
        STANDARD_SCORES.put(MNKGameState.OPEN, 0);

    }

    public void initTrackingBoard(int M, int N, int K) {
        currentBoard = new WeightedMNKBoard(M,N,K);
        isCurrentBoardLeftInValidState = true;
    }

    protected MNKCell strategyAsFirst(MNKCell[] FC, MNKCell[] MC) {
//      return FC[rand.nextInt(FC.length)]; // random
        int[] coords = corners[ rand.nextInt( corners.length ) ];
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

        System.out.println( "Euristic: " + outcome.getWeightedValue() + "\tDecision made in " + (elapsed/1000.0) + "\tLeft Time: " + (timeLeft/1000.0) );
        // System.out.println( Arrays.toString( currentBoard.getFreeCells() ) );
        // System.out.println( "if " + currentBoard.currentPlayer()  + " choose " +  outcome.move + " -> " + outcome.eval );
        currentBoard.markCell( outcome.move.i, outcome.move.j );
        System.out.println( "after move:\n" + currentBoard.toString() );
        isCurrentBoardLeftInValidState = true;
        return outcome.move;
    }

    private AlphaBetaOutcome evaluate( WeightedMNKBoard board, int depth, boolean isMyTurn ) {
        MNKGameState gameState =  board.gameState();
        AlphaBetaOutcome outcome = new AlphaBetaOutcome();

        int score = STANDARD_SCORES.get(gameState);
/* // keep as reference
        if( gameState == this.STATE_WIN ) {
            // System.out.println( getTabForDepth( depth-1 ) + MY_MARK_STATE +  "-> Win state");
            // score = 1f;
        }
        else if( gameState == this.STATE_LOSE ) {
            // System.out.println( getTabForDepth( depth-1 ) + MY_MARK_STATE +  "-> Lose state");
            // score = -1f;
        }
        else if( gameState == MNKGameState.DRAW ) {
            // System.out.println( getTabForDepth( depth-1 ) + "Draw state");
            // score = 0f;
        }
        else
*/
        if( gameState == MNKGameState.OPEN ) { // game is open
            // System.out.println( getTabForDepth( depth-1 ) + "Heuristic state");
            // TODO: here we should do an Heuristic evaluation

            // newStimeScore = scoreWeight * oldScore  + ( 1 - scoreWeight ) * oldStimeScore

            // score += evaluateHeuristicWeights( board );

            if( score != 0f ) score = 1f / score;

        }

        outcome.eval = score;
        outcome.depth = depth;
        return outcome;
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


    private void unMark( MNKBoard tree, int depth ) {
        //System.out.println( "\t" + tree.currentPlayer + " Unmarking:" + tree.MC.getLast() + " @ " + depth );
        MNKCell unmarked = tree.MC.getLast();
        tree.unmarkCell();
        // System.out.println( getPlayerByIndex( tree.currentPlayer() ) + getTabForDepth( depth ) +  "Unmarking:" + unmarked + " @ " + depth);
        // it.add(MC.remove());
    }

    private void mark( MNKBoard tree, MNKCell marked, int depth ) {
        // MNKCell marked = it.next();
        int playerIndex = tree.currentPlayer();
        // System.out.println( getPlayerByIndex( tree.currentPlayer() ) + getTabForDepth( depth ) +  "Marking:" + marked + " @ " + depth);
        tree.markCell(marked.i, marked.j);
        // it.remove();
        // MC.add(marked);
    }


    protected AlphaBetaOutcome alphaBetaPruning(WeightedMNKBoard tree, boolean shouldMaximize, int a, int b, int depth, int depthLeft, long endTime ) {
        // on last if condition may would be a match in a always win/lost configuration
        if( depthLeft == 0 || tree.gameState() != MNKGameState.OPEN ) {
            return evaluate( tree, depth+1, shouldMaximize );
        }
        else {
            AlphaBetaOutcome bestOutcome = null, outcome = null;
            // System.out.println( getPlayerByIndex( shouldMaximize ? 0 : 1 )+ " Move " + depth );

            Iterator<MNKCell> moves = getCellCandidates( tree ).iterator();

            while( moves.hasNext() ) {

                MNKCell move = moves.next();
                mark( tree, move, depth);

                outcome = alphaBetaPruning(tree, !shouldMaximize, a, b, depth + 1, depthLeft - 1, endTime);

                unMark( tree, depth );


                // minimize
                if (!shouldMaximize && (bestOutcome == null || outcome.compareTo(bestOutcome) < 0) ) {
                    bestOutcome = outcome;
                    bestOutcome.move = move;
                    b = Math.min( b, outcome.getWeightedValue() );
                }
                // maximize
                else if (shouldMaximize && (bestOutcome == null || outcome.compareTo(bestOutcome) > 0) ) {
                    bestOutcome = outcome;
                    bestOutcome.move = move;
                    a = Math.max( a, outcome.getWeightedValue() );
                }

                if( System.currentTimeMillis() > endTime ) {
                    System.out.println( "Exiting quickly" );
                    break;
                }

                if (b <= a) { // a or b cutoff ( can't get better results )
                    break;
                }

                // System.out.println( getPlayerByIndex( isMyTurn ? 0 : 1 )+ " Move " + depth );
            }
            return bestOutcome;
        }
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
     * Return
     * @param board
     * @return
     */
    private List<MNKCell> getCellCandidates(WeightedMNKBoard board ) {
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

class AlphaBetaOutcome implements Comparable<AlphaBetaOutcome> {
    public int eval;
    public MNKCell move;
    public int depth;

    public int getWeightedValue() {
        return eval / (Math.max( 1, depth ) );
    }

    public AlphaBetaOutcome( AlphaBetaOutcome value ) {
        this.eval = value.eval;
        this.move = value.move;
        this.depth = value.depth;
    }

    public AlphaBetaOutcome() {

    }

    @Override
    public int compareTo(AlphaBetaOutcome o) {
        if( o == null ) {
            return 1;
        }
        else {
            float thisWeight = getWeightedValue();
            float thatWeight = o.getWeightedValue();

            if( thisWeight > thatWeight ){
                return 1;
            }
            else if( thisWeight < thatWeight ){
                return -1;
            }
            else {
                if( depth < o.depth ){
                    return 1;
                }
                else if( depth > o.depth){
                    return -1;
                }
                else {
                    return 0;
                }
            }
        }
    }
}