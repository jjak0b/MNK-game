package player;

public class IndexedBoard extends EBoard {

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
    public IndexedBoard(int M, int N, int K) throws IllegalArgumentException {
        super(M, N, K);

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
}
