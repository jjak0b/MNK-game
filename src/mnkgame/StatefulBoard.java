package mnkgame;

import java.math.BigInteger;
import java.util.*;

public class StatefulBoard extends MNKBoard {

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

    // fast access to array index to matrix indexes map conversion, avoid use of a lot of runtime multiplications
    // maybe it's not faster... https://stackoverflow.com/a/21540469/18145895
    private final int[][] arrayToMatrixIndexMap;
    // fast access to matrix indexes to array index map conversion, avoid use of a lot of runtime multiplications
    // maybe it's not faster... https://stackoverflow.com/a/21540469/18145895
    private final int[][] matrixToArrayIndexMap;

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

        final int count = M * N;

        arrayToMatrixIndexMap = new int[count][2];
        matrixToArrayIndexMap = new int[M][N];
        initIndexConversionsMaps();

        currentState = BigInteger.ZERO;

    }

    private void initIndexConversionsMaps() {
        int arrayIndex = 0;
        for (int i = 0; i < M; i++) {
            for (int j = 0; j < N; j++) {
                matrixToArrayIndexMap[ i ][ j ] = arrayIndex;

                arrayToMatrixIndexMap[arrayIndex][0] = i;
                arrayToMatrixIndexMap[arrayIndex][1] = j;

                arrayIndex++;
            }
        }
    }
    public StatefulBoard(int M, int N, int K, MNKCell[] movesDone ) {
        this(M, N, K);
        for (MNKCell move : movesDone) markCell(move.i, move.j);
    }

    public int getArrayIndexFromMatrixIndexes(int i, int j) {
        // return (i * M) + j;
        return matrixToArrayIndexMap[i][j];
    }

    public int[] getMatrixIndexesFromArrayIndex(int index) {
        return arrayToMatrixIndexMap[index];
    }

    public int[] getMatrixIndexesFromArrayIndex(int index, int[] buffer ) {
        // buffer[0] = index / M;
        // buffer[1] = index % M;
        Vectors.vectorCopy(buffer, arrayToMatrixIndexMap[index] );
        return buffer;
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

    private int getCurrentPlayerMod( int currentPlayer) {
        if( currentPlayer > 1 ){
            return 1;
        }
        else {
            return -1;
        }
    }

    public HashMap<Integer, List<MNKCell>> adj(MNKCell cell ) {
        return adj( cell.i, cell.j );
    }

    public HashMap<Integer, List<MNKCell>> adj(int i, int j ) {

       HashMap<Integer, List<MNKCell>> adj = new HashMap<>(2 * Utils.DIRECTIONS.length );

        int[]
                origin = { i, j },
                position = { i, j };

        for ( int direction_type : Utils.DIRECTIONS ) {

            int[][] direction_offsets = Utils.DIRECTIONS_OFFSETS[direction_type];

            List<MNKCell> cells = new ArrayList<>(direction_offsets.length);

            for ( int side = 0; side < direction_offsets.length; side ++ ) {
                Vectors.vectorSum(Vectors.vectorCopy(position, origin), direction_offsets[side]);
                if( isVectorInBounds(position) ) {
                    cells.add(new MNKCell(position[0], position[1], cellState(position[0], position[1])) );
                }
            }

            adj.put(direction_type, cells );
        }

        return adj;
    }

    /**
     * Check if vector as point is inside the sizes of the board
     * @param v
     * @return true if is inside, false otherwise
     */
    public boolean isVectorInBounds( int[] v ) {
        return !(v[0] < 0 || v[0] >= M || v[1] < 0 || v[1] >= N);
    }


    @Override
    public int hashCode() {
        return currentState.hashCode();
    }

    public boolean equals( Object o ) {
        if( !(o instanceof MNKBoard) ) {
            return false;
        }

        MNKBoard b = (MNKBoard) o;
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
