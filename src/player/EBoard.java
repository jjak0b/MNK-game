package player;

import mnkgame.MNKBoard;
import mnkgame.MNKCell;
import mnkgame.MNKCellState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

// extended MNKBoard class with some utilities and accessors
public class EBoard extends MNKBoard implements Board {
    // allow access for debug purpose
    protected final MNKCellState[][]    B;

    // fast access to array index to matrix indexes map conversion, avoid use of a lot of runtime multiplications
    protected final int[][] arrayToMatrixIndexMap;
    protected final int[][] matrixToArrayIndexMap;

    /**
     * Create a board of size MxN and initialize the game parameters
     *
     * @param M Board rows
     * @param N Board columns
     * @param K Number of symbols to be aligned (horizontally, vertically, diagonally) for a win
     * @throws IllegalArgumentException If M,N,K are smaller than  1
     */
    public EBoard(int M, int N, int K) throws IllegalArgumentException {
        super(M, N, K);
        this.B = super.B;

        final int count = M * N;
        arrayToMatrixIndexMap = new int[count][2];
        matrixToArrayIndexMap = new int[M][N];
        initIndexConversionsMaps();
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

    @Override
    public MNKCell getLastMarked() {
        return MC.getLast();
    }

    @Override
    public int getFreeCellsCount() {
        return FC.size();
    }

    @Override
    public int getMarkedCellsCount() {
        return MC.size();
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

}
