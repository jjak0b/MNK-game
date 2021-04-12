package mnkgame;

import java.util.*;

public class MyPlayer implements MNKPlayer {
    private Random rand;
    private MNKBoard currentBoard;
    private MNKGameState STATE_WIN;
    private MNKGameState STATE_LOSE;
    private int timeout;
    private MNKCell lastCell;
    private MNKCellState MY_MARK_STATE;
    private int playerIndex;

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
        currentBoard = new MNKBoard(M,N,K);
        STATE_WIN = first ? MNKGameState.WINP1 : MNKGameState.WINP2;
        STATE_LOSE = first ? MNKGameState.WINP2 : MNKGameState.WINP1;
        MY_MARK_STATE = first ? MNKCellState.P1 : MNKCellState.P2;
        timeout = timeout_in_secs;
        playerIndex = first ? 0 : 1;
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
        if(MC.length > 0) {
            choice = MC[MC.length-1]; // Recover the last move from MC
            currentBoard.markCell(choice.i,choice.j);         // Save the last move in the local MNKBoard
        }
        else { // first move !
            choice = FC[rand.nextInt(FC.length)];
            currentBoard.markCell(choice.i,choice.j);
            return choice;
        }

        // If there is just one possible move, return immediately
        if(FC.length == 1){
            choice = FC[0];
            currentBoard.markCell(choice.i,choice.j);
            return choice;
        }

        this.lastCell = FC[ 0 ];
        AlphaBetaOutcome outcome = alphaBetaPruning(
                currentBoard,
                false,
                10,
                0,
                -1f,
                1f,
                FC,
                MC
        );


        // System.out.println( Arrays.toString( currentBoard.getFreeCells() ) );
        // System.out.println( "if " + currentBoard.currentPlayer()  + " choose " +  outcome.move + " -> " + outcome.eval );
        currentBoard.markCell( outcome.move.i, outcome.move.j );
        return outcome.move;
    }

    private float evaluate( MNKBoard board, int depth, boolean isMyTurn ) {
        MNKGameState gameState =  board.gameState();

        float score = 0;
        if( gameState == this.STATE_WIN ) {
            // System.out.println( getTabForDepth( depth-1 ) + MY_MARK_STATE +  "-> Win state");
            score = 1f;
        }
        else if( gameState == this.STATE_LOSE ) {
            // System.out.println( getTabForDepth( depth-1 ) + MY_MARK_STATE +  "-> Lose state");
            score = -1f;
        }
        else if( gameState == MNKGameState.DRAW ) {
            // System.out.println( getTabForDepth( depth-1 ) + "Draw state");
            score = 0f;
        }
        else { // game is open
            // System.out.println( getTabForDepth( depth-1 ) + "Heuristic state");
            // TODO: here we should do an Heuristic evaluation
            score = 0f;
        }

        return score / (Math.max( 1f, depth ) );
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

        // System.out.println( getPlayerByIndex( tree.currentPlayer() ) + getTabForDepth( depth ) +  "Marking:" + marked + " @ " + depth);
        tree.markCell(marked.i, marked.j);
        // it.remove();
        // MC.add(marked);
    }
    private AlphaBetaOutcome alphaBetaPruning(MNKBoard tree, boolean isMyTurn, int leftMoves, int depth, float a, float b, MNKCell[] FC, MNKCell[] MC ) {

        AlphaBetaOutcome outcome = null;
        if( leftMoves == 0 || tree.gameState() != MNKGameState.OPEN ) {
            outcome = new AlphaBetaOutcome();
            outcome.eval = evaluate( tree, depth, isMyTurn );
            return outcome;
        }
        else {
            AlphaBetaOutcome bestOutcome = null;
            float eval = isMyTurn ? Float.POSITIVE_INFINITY : Float.NEGATIVE_INFINITY;
            // System.out.println( getPlayerByIndex( isMyTurn ? 0 : 1 )+ " Move " + depth );

            MNKCell[] candidates = getCellCandidates( FC );
            // System.out.println( Arrays.toString( candidates ) );
            for ( MNKCell unmarkedCell : candidates ) {
                mark( tree, unmarkedCell, depth);
                if (isMyTurn) {
                    outcome = alphaBetaPruning(tree, false, leftMoves - 1, depth+1, a, b, tree.getFreeCells(), tree.getMarkedCells() );
                    if( bestOutcome == null || outcome.eval < bestOutcome.eval ) {
                        bestOutcome = outcome;
                        bestOutcome.move = unmarkedCell;
                    }
                    eval = Math.min(eval, outcome.eval );
                    b = Math.min(eval, b);

                    if (b <= a) { // a cutoff ( can't get better results )
                        // System.out.println( getPlayerByIndex( isMyTurn ? 0 : 1 ) + getTabForDepth( depth ) +  "a cutoff:" + "a:" + a + "\teval:" + eval + "\tb:" + b );
                        unMark( tree, depth );
                        printUntil( unmarkedCell, candidates );
                        break;
                    }
                }
                else {
                    outcome = alphaBetaPruning(tree, true, leftMoves - 1, depth+1, a, b, tree.getFreeCells(), tree.getMarkedCells() );
                    if( bestOutcome == null || outcome.eval > bestOutcome.eval ) {
                        bestOutcome = outcome;
                        bestOutcome.move = unmarkedCell;
                    }
                    eval = Math.max(eval, outcome.eval );
                    a = Math.max(eval, a);

                    if (b <= a) { // b cutoff ( can't get better results )
                        // System.out.println( getPlayerByIndex( tree.currentPlayer() ) + getTabForDepth( depth ) +  "b cutoff:" + "a:" + a + "\teval:" + eval + "\tb:" + b );
                        unMark( tree, depth );
                        printUntil( unmarkedCell, candidates );
                        break;
                    }
                }
                unMark( tree, depth );
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
    private MNKCell[] getCellCandidates( MNKCell[] FC ) {
        // first should be cells that are in sequence that have 1 move left to win
        // after should be cells that are in sequence that have 2 move left to win
        // TODO: use priority queue for this
        return FC;
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

class AlphaBetaOutcome {
    public float eval;
    public MNKCell move;
}