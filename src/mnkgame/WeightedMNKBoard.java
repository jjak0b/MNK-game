package mnkgame;

import java.math.BigInteger;
import java.util.*;

public class WeightedMNKBoard extends MNKBoard {
    protected final int[][][] weights;
    protected final PriorityQueue<MNKCell>[] freeCellsQueue;
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

    final int CELL_STATE_BITS_FOR_PLAYER[] = {
        1, // first player 01
        2 // second player 10
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
    public WeightedMNKBoard(int M, int N, int K ) throws IllegalArgumentException {
        super(M, N, K);

        currentState = BigInteger.ZERO;

        weights = new int[2][M][N];
        initWeights();

        freeCellsQueue = new PriorityQueue[] {
                // player 0 watch its weights
                new PriorityQueue<>(FC.size(), new Comparator<MNKCell>() {
                    @Override
                    public int compare(MNKCell o1, MNKCell o2) {
                        return weights[0][o2.i][o2.j] - weights[0][o1.i][o1.j];
                    }
                }),
                // player 1 watch its weights
                new PriorityQueue<>(FC.size(), new Comparator<MNKCell>() {
                    @Override
                    public int compare(MNKCell o1, MNKCell o2) {
                        return weights[1][o2.i][o2.j] - weights[1][o1.i][o1.j];
                    }
                })
        };

        freeCellsQueue[0].addAll( FC );
        freeCellsQueue[1].addAll( FC );
    }

    public WeightedMNKBoard(int M, int N, int K, MNKCell[] movesDone ) {
        this(M, N, K);
        for (MNKCell move : movesDone) markCell(move.i, move.j);
    }

    public int[][] getWeights(int playerIndex) {
        return weights[playerIndex];
    }

    // Sets to free all board cells
    private void initWeights() {
        for (int p = 0; p < 2; p++)
            for(int i = 0; i < M; i++)
                for(int j = 0; j < N; j++)
                    weights[p][i][j] = 0;
    }

    private int getArrayIndexFromMatrixIndexes(int i, int j ) {
        return (i * M) + j;
    }

// https://inst.eecs.berkeley.edu/~cs61bl/r//cur/hashing/hashing-ttt.html


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
        // System.out.println( "before mark move:\n" + toString() );
        int markingPlayer = currentPlayer();
        MNKCell oldc = new MNKCell(i,j,cellState(i,j) );

        // mark bitfield at (i, j)
        currentState = currentState.xor(
            // set the mark, shifting left by X * 2 positions, where X is the array coordinate of the matrix
            BigInteger.valueOf( CELL_STATE_BITS_FOR_PLAYER[markingPlayer] ).shiftLeft( getArrayIndexFromMatrixIndexes(i, j) * 2 )
        );

        MNKGameState gameState = super.markCell(i, j);
        MNKCell newc = MC.getLast();

        updateWeights( i, j, 1, markingPlayer);

        freeCellsQueue[0].remove( oldc );
        freeCellsQueue[1].remove( oldc );
        // System.out.println( "after mark move: " + newc + "\n" + toString() );
        return gameState;
    }

    /**
     * Undoes last move
     *
     * @throws IllegalStateException If there is no move to undo
     */
    public void unmarkCell() throws IllegalStateException {
        // System.out.println( "before unmark move:\n" + toString() );
        MNKCell newc = null;
        if(MC.size() > 0) {
            MNKCell oldc = MC.getLast();
            int unMarkingPlayer = Utils.getPlayerIndex(oldc.state);
            newc = new MNKCell(oldc.i, oldc.j, MNKCellState.FREE );

            updateWeights( newc.i, newc.j, -1 , unMarkingPlayer);

            freeCellsQueue[0].add( newc );
            freeCellsQueue[1].add( newc );

            // unmark bitfield at (i, j)
            currentState = currentState.xor(
                // set the mark, shifting left by X * 2 positions, where X is the array coordinate of the matrix
                BigInteger.valueOf( CELL_STATE_BITS_FOR_PLAYER[unMarkingPlayer] ).shiftLeft( getArrayIndexFromMatrixIndexes(oldc.i, oldc.j) * 2 )
            );
        }


        super.unmarkCell();
        // System.out.println( "after unmark move: " + newc + "\n" + toString() );
    }

    private int getCurrentPlayerMod( int currentPlayer) {
        if( currentPlayer > 1 ){
            return 1;
        }
        else {
            return -1;
        }
    }

    /**
     * Returns the free cells list in array format.
     * <p>There is not a predefined order for the free cells in the array</p>
     *
     * @return List of free cells
     */
    public PriorityQueue<MNKCell> getWeightedFreeCellsHeap(int playerIndex) {
        return freeCellsQueue[playerIndex];
    }

    public MNKCell[] getFreeCells(int playerIndex) {
        return (MNKCell[]) freeCellsQueue[playerIndex].toArray();
    }
    /**
     * Count how many consecutive cells are in the state s starting from source (included)  through the direction vector
     * @param s
     * @param source
     * @param direction
     * @return
     */
    private int countMatchesInARow(MNKCellState s, int[] source, int[] direction ) {
        int[] index = { source[ 0 ], source[ 1 ] };

        int n = 0;
        while( isVectorInBounds( index ) && cellState( index[0], index[1] ) == s ) {
            n++;
            Vectors.vectorSum(index, direction);
        }
        return n;
    }

    /**
     * Updates the weight given to each free cell connected to marked cells
     * based on the count of cells in a row, for each directions, which are in the same state as the supplied cell.
     * So each free cell that follow or precedes a row of in state S, has its weight set to the count of marked cells
     * in a row in the same state s, including cell(i,j) in the count
     * @param i
     * @param j
     * @param mod weight modifier used to scale the count applied to extremes cells, use > 0 after marking, < 0 after unmarking
     */
    private void updateWeights( int i, int j, int mod, int playerIndex) {
        MNKCellState s = cellState( i, j );
        MNKCell updatedCell;
        // first half adjacent direction vectors on clockwise ( starting from 00:00 )
        int[][] weights = getWeights(playerIndex);

        int[]   source = { 0, 0 },
                direction = {0, 0},
                counts = { 0, 0},
                distance = { 0, 0 },
                nextPointInSequence = { 0, 0 };
        int countInDirection = 0, countInOppositeDirection = 0;
        // update target's weight on remove
        weights[ i ][ j ] += 1 * mod;

        // update cell's weight on the both direction end
        // 4 * ( 2c * ( O( k ) + O( PriorityQueue.add ) + O( PriorityQueue.remove ) ) )

        // check adjacent direction vectors
        for ( int direction_type : Utils.DIRECTIONS ) {
            int[][] direction_offsets = Utils.DIRECTIONS_OFFSETS[direction_type];

            // calculate counts in both sides of this direction
            for (int side = 0; side < direction_offsets.length; side++) {
                source[ 0 ] = i; source[ 1 ] = j;

                // flip direction based on type
                Vectors.vectorCopy(direction, direction_offsets[side]);
                counts[side] = countMatchesInARow( s, source, direction );
            }
            // update weight on both sides of this direction
            for (int side = 0; side < direction_offsets.length; side++) {
                int oppositeSide = (direction_offsets.length-1) - side;

                source[ 0 ] = i; source[ 1 ] = j;

                // flip direction based on type
                Vectors.vectorCopy(direction, direction_offsets[side]);

                countInDirection = counts[side];
                countInOppositeDirection = counts[oppositeSide];

                // System.out.println("From " + source + " in direction " + direction + " we have " + countInDirection + " and in opposite " + countInOppositeDirection );

                // n = -1 + countBefore + countAfter; // source is counted twice
                // weights[ i ][ j ] is always >= n ( > because can be increased by other sides )
                // go to prev of first
                // and then go to next of last

                // distance = direction * (countBefore+1) ( + 1 for next cell )
                Vectors.vectorScale(Vectors.vectorCopy(distance, direction), countInDirection);
                // nextPointInSequence = source + distance
                Vectors.vectorSum(Vectors.vectorCopy(nextPointInSequence, source), distance);

                // if next is free then update its weight to total
                if( isVectorInBounds( nextPointInSequence ) ) {
                    weights[ nextPointInSequence[0] ][ nextPointInSequence[1] ] += countInOppositeDirection * mod;
                    // update cell position
                    MNKCellState cellState = cellState( nextPointInSequence[ 0 ], nextPointInSequence[ 1 ] );
                    if( cellState == MNKCellState.FREE ) {
                        updatedCell = new MNKCell( nextPointInSequence[0], nextPointInSequence[1], cellState );

                        freeCellsQueue[playerIndex].remove( updatedCell );
                        freeCellsQueue[playerIndex].add( updatedCell );
                    }
                }
            }
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

    public String toString() {
        String s = "";
        for (int i = 0; i < B.length; i++) {
            s += Utils.toString( B[ i ] ) + "\t\t\t" + Utils.toString( weights[0][ i ], K) + "\t\t\t" + Utils.toString( weights[1][ i ], K) + "\n";
        }
        return s;
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
