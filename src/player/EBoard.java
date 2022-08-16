package player;

import mnkgame.MNKBoard;
import mnkgame.MNKCell;
import mnkgame.MNKCellState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

// extended MNKBoard class with some utilities and accessors
public class EBoard extends MNKBoard implements Board {

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
    }

    // allow access for debug purpose
    public MNKCellState[][] states() {
        return B;
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
