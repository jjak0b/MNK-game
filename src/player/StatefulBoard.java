package player;

import mnkgame.MNKBoard;
import mnkgame.MNKCell;
import mnkgame.MNKCellState;
import mnkgame.MNKGameState;

import java.math.BigInteger;

public class StatefulBoard extends IndexedBoard {

    /**
     * Unique state, hash of the current board
     * Implemented as bitfield of L bits where L = 2 * M * N
     * Each 2 bits represent the state of a cell of the matrix, considering the matrix as array of length M * N
     * So the 2 least significant bits represent the cell state at (0,0),
     * the next 2 represent the cell state at (0,1), and so on ...
     * Cell States:
     * Free Cell:       00
     * First Player:    01
     * Second Player:   10
     *
     * Note:
     * <= 4x4 Matrix needs a minimum of 1 int ( 32 bit )
     * <= 6x6 Matrix needs a minimum of 2 int or 1 long ( 64 bit )
     * <= 8x8 Matrix needs a minimum of 4 int or 2 long ( 128bit )
     * <= 16x16 Matrix needs a minimum of 16 int or 8 long ( 512bit )
     * and so on ...
     */
    protected BigInteger currentState;

    // https://inst.eecs.berkeley.edu/~cs61bl/r//cur/hashing/hashing-ttt.html
    final BigInteger[] CELL_STATE_BITS_FOR_PLAYER = {
        BigInteger.ONE, // first player 01
        BigInteger.TWO // second player 10
    };

    public BigInteger getCurrentState() {
        return currentState;
    }

    /**
     * Create a board of size MxN and initialize the game parameters
     *
     * @param M Board rows
     * @param N Board columns
     * @param K Number of symbols to be aligned (horizontally, vertically, diagonally) for a win
     * @throws IllegalArgumentException If M,N,K are smaller than  1
     */
    public StatefulBoard(int M, int N, int K) throws IllegalArgumentException {
        super(M, N, K);

        currentState = BigInteger.ZERO;

    }

    public StatefulBoard(int M, int N, int K, MNKCell[] movesDone ) {
        this(M, N, K);
        for (MNKCell move : movesDone) markCell(move.i, move.j);
    }

    /**
     * Marks the selected cell for the current player
     *
     * @param i i-th row
     * @param j j-th column
     * @return State of the game after the move
     * @throws IndexOutOfBoundsException If <code>i,j</code> are out of matrix bounds
     * @throws IllegalStateException     If the game already ended or if <code>i,j</code> is not a free cell
     */
    @Override
    public MNKGameState markCell(int i, int j) throws IndexOutOfBoundsException, IllegalStateException {
        // Debug.println( "before mark move:\n" + toString() );
        int markingPlayer = currentPlayer();
        MNKCell oldc = new MNKCell(i,j,cellState(i,j) );

        // mark bitfield at (i, j)
        currentState = currentState.xor(
            // set the mark, shifting left by X * 2 positions, where X is the array coordinate of the matrix
            CELL_STATE_BITS_FOR_PLAYER[markingPlayer].shiftLeft( matrixToArrayIndexMap[i][j] * 2 )
        );

        MNKGameState gameState = super.markCell(i, j);
        MNKCell newc = MC.getLast();

/*
        for (int playerIndex = 0; playerIndex <2; playerIndex++) {
            cellsHandlers[playerIndex].remove(oldc)
                    .delete();
        }
*/
        // Debug.println( "after mark move: " + newc + "\n" + toString() );
        return gameState;
    }

    /**
     * Undoes last move
     *
     * @throws IllegalStateException If there is no move to undo
     */
    @Override
    public void unmarkCell() throws IllegalStateException {
        // Debug.println( "before unmark move:\n" + toString() );
        MNKCell newc = null;
        if(MC.size() > 0) {
            MNKCell oldc = MC.getLast();
            int unMarkingPlayer = Utils.getPlayerIndex(oldc.state);
            newc = new MNKCell(oldc.i, oldc.j, MNKCellState.FREE );

            // unmark bitfield at (i, j)
            currentState = currentState.xor(
                // set the mark, shifting left by X * 2 positions, where X is the array coordinate of the matrix
                CELL_STATE_BITS_FOR_PLAYER[unMarkingPlayer].shiftLeft( matrixToArrayIndexMap[oldc.i][oldc.j] * 2 )
            );
        }


        super.unmarkCell();
        // Debug.println( "after unmark move: " + newc + "\n" + toString() );
    }

    @Override
    public int hashCode() {
        return currentState.hashCode();
    }

    public boolean equals( Object o ) {
        if( !(o instanceof MNKBoard) ) {
            return false;
        }

        Board b = (Board) o;
        for (int i = 0; i < M; i++) {
            for (int j = 0; j < N; j++) {
                if( cellState( i, j ) != b.cellState( i, j ) ) {
                    return false;
                }
            }
        }

        return true;
    }
}
