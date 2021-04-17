package mnkgame;

import java.util.*;

public class WeightedMNKBoard extends MNKBoard {
    protected final int[][] weights;
    protected final PriorityQueue<MNKCell> freeCellsQueue;

    /**
     * Create a board of size MxN and initialize the game parameters
     *
     * @param M Board rows
     * @param N Board columns
     * @param K Number of symbols to be aligned (horizontally, vertically, diagonally) for a win
     * @throws IllegalArgumentException If M,N,K are smaller than  1
     */
    public WeightedMNKBoard(int M, int N, int K) throws IllegalArgumentException {
        super(M, N, K);

        weights = new int[M][N];
        initWeights();

        freeCellsQueue = new PriorityQueue<MNKCell>(FC.size(), new Comparator<MNKCell>() {
            @Override
            public int compare(MNKCell o1, MNKCell o2) {
                return weights[ o2.j ][ o2.j ] - weights[ o1.i ][ o1.j ];
            }
        });

        freeCellsQueue.addAll( FC );
    }

    public int[][] getWeights() {
        return weights;
    }

    // Sets to free all board cells
    private void initWeights() {
        for(int i = 0; i < M; i++)
            for(int j = 0; j < N; j++)
                weights[i][j] = 0;
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
        // System.out.println( "before mark move:\n" + toString() );
        int markingPlayer = this.currentPlayer();
        MNKCell oldc = new MNKCell(i,j,cellState(i,j) );
        MNKGameState gameState = super.markCell(i, j);
        MNKCell newc = MC.getLast();

        updateWeights( i, j, 1 );
        freeCellsQueue.remove( oldc );
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
            newc = new MNKCell(oldc.i, oldc.j, MNKCellState.FREE );
            updateWeights( oldc.i, oldc.j, -1 );
            freeCellsQueue.add( newc );
        }

        super.unmarkCell();
        // System.out.println( "after unmark move: " + newc + "\n" + toString() );
    }

    /**
     * Returns the free cells list in array format.
     * <p>There is not a predefined order for the free cells in the array</p>
     *
     * @return List of free cells
     */
    public PriorityQueue<MNKCell> getWeightedFreeCellsHeap() {
        return freeCellsQueue;
    }

    public MNKCell[] getFreeCells() {
        return (MNKCell[]) freeCellsQueue.toArray();
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
            vectorSum( index, direction );
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
    private void updateWeights( int i, int j, int mod ) {
        MNKCellState s = cellState( i, j );
        MNKCell updatedCell;
        // first half adjacent direction vectors on clockwise ( starting from 00:00 )
        int[][] halfOffsets = { { -1, 0 }, { -1, 1 }, { 0, 1 }, { 1, 1 } },
                weights = getWeights();

        int[]   source = { 0, 0 },
                distance = { 0, 0 },
                nextPointInSequence = { 0, 0 };
        int countBefore = 0, countAfter = 0;

        // update target's weight on remove
        weights[ i ][ j ] += 1 * mod;

        // update cell's weight on the both direction end
        // 4 * ( 2c * ( O( k ) + O( PriorityQueue.add ) + O( PriorityQueue.remove ) ) )
        for ( int[] direction : halfOffsets ) {
            source[ 0 ] = i; source[ 1 ] = j;
            countAfter = countMatchesInARow( s, source, direction );
            countBefore = countMatchesInARow( s, source, vectorScale( direction, -1 ) );
            // n = -1 + countBefore + countAfter; // source is counted twice
            // weights[ i ][ j ] is always >= n ( > because can be increased by other sides )

            // go to prev of first

            // distance = direction * (countBefore+1) ( + 1 for next cell )
            vectorScale( vectorCopy( distance, direction ), countBefore );
            // nextPointInSequence = source + distance
            vectorSum( vectorCopy( nextPointInSequence, source ), distance );

            // if next is free then update its weight to total
            if( isVectorInBounds( nextPointInSequence ) ) {
                weights[ nextPointInSequence[0] ][ nextPointInSequence[1] ] += countAfter * mod;
                // update cell position
                MNKCellState cellState = cellState( nextPointInSequence[ 0 ], nextPointInSequence[ 1 ] );
                if( cellState == MNKCellState.FREE ) {
                    updatedCell = new MNKCell( nextPointInSequence[0], nextPointInSequence[1], cellState );
                    freeCellsQueue.remove( updatedCell );
                    freeCellsQueue.add( updatedCell );
                }
            }

            // go to next of last
            vectorScale( direction, -1 ); // flip direction

            // distance = direction * (countAfter+1) ( + 1 for next cell )
            vectorScale( vectorCopy( distance, direction ), countAfter );
            // nextPointInSequence = source + distance
            vectorSum( vectorCopy( nextPointInSequence, source ), distance );

            // if next is free then update its weight to total
            if( isVectorInBounds( nextPointInSequence ) ) {
                updatedCell = new MNKCell( nextPointInSequence[0], nextPointInSequence[1], cellState( nextPointInSequence[ 0 ], nextPointInSequence[ 1 ] ) );
                weights[ nextPointInSequence[0] ][ nextPointInSequence[1] ] += countBefore * mod;
                // update cell position
                MNKCellState cellState = cellState( nextPointInSequence[ 0 ], nextPointInSequence[ 1 ] );
                if( cellState == MNKCellState.FREE ) {
                    updatedCell = new MNKCell( nextPointInSequence[0], nextPointInSequence[1], cellState );
                    freeCellsQueue.remove( updatedCell );
                    freeCellsQueue.add( updatedCell );
                }
            }
        }
    }


    public Set<MNKCell> adj(MNKCell cell ) {
        return adj( cell.i, cell.j );
    }

    public Set<MNKCell> adj(int i, int j ) {

       HashSet<MNKCell> adj = new HashSet<MNKCell>( 8);

        int minR = Math.max( 0, i-1);
        int maxR = Math.min( M, i+1);
        int minC = Math.max( 0, j-1);
        int maxC = Math.min( N, j+1);

        for (int r = minR; r < maxR; r++) {
            for (int c = minC; c < maxC; c++) {
                if( !(r == i && c == j) ) {
                    adj.add(new MNKCell(r, c, cellState(r, c)));
                }
            }
        }

        return adj;
    }

    // Utility functions

    /**
     * Scale vector of some scalar
     * @param v
     * @param scalar
     * @return the v vector scaled by the amount of the scalar
     */
    private static int[] vectorScale( int[] v, int scalar ) {
        for (int i = 0; i < v.length; i++) v[ i ] *= scalar;
        return v;
    }

    /**
     * Copy source's values in dest
     * @param dest
     * @param source
     * @return dest with source's values
     */
    private static int[] vectorCopy( int[] dest, int[] source ) {
        for (int i = 0; i < dest.length; i++) dest[ i ] = source[ i ];
        return dest;
    }
    /**
     * Sum the direction to source vector
     * @param source
     * @param direction
     * @return the source vector with the sum applied
     */
    private static int[] vectorSum( int[] source, int[] direction ) {
        for (int i = 0; i < source.length; i++) source[ i ] += direction[ i ];
        return source;
    }

    /**
     * Check if vector as point is inside the sizes of the board
     * @param v
     * @return true if is inside, false otherwise
     */
    private boolean isVectorInBounds( int[] v ) {
        return !(v[0] < 0 || v[0] >= M || v[1] < 0 || v[1] >= N);
    }

    public String toString() {
        String s = "";
        for (int i = 0; i < B.length; i++) {
            s += Arrays.toString( B[ i ] ) + "\t\t" + Arrays.toString( weights[ i ] ) + "\n";
        }
        return s;
    }
}
