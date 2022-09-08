package player;

import mnkgame.MNKCell;
import mnkgame.MNKCellState;
import mnkgame.MNKGameState;

public abstract class EBoardWithGameEnd extends EBoard {

    protected final MNKCellState[] Player = {MNKCellState.P1,MNKCellState.P2};

    /**
     * Create a board of size MxN and initialize the game parameters
     *
     * @param M Board rows
     * @param N Board columns
     * @param K Number of symbols to be aligned (horizontally, vertically, diagonally) for a win
     * @throws IllegalArgumentException If M,N,K are smaller than  1
     */
    public EBoardWithGameEnd(int M, int N, int K) throws IllegalArgumentException {
        super(M, N, K);
    }

    /**
     * Marks the selected cell for the current player
     *
     * @param i i-th row
     * @param j j-th column
     *
     * @return State of the game after the move
     *
     * @throws IndexOutOfBoundsException If <code>i,j</code> are out of matrix bounds
     * @throws IllegalStateException If the game already ended or if <code>i,j</code> is not a free cell
     */

    @Override
    public MNKGameState markCell(int i, int j) throws IndexOutOfBoundsException, IllegalStateException {
        if(gameState != MNKGameState.OPEN) {
            throw new IllegalStateException("Game ended!");
        } else if(i < 0 || i >= M || j < 0 || j >= N) {
            throw new IndexOutOfBoundsException("Indexes " + i +"," + j + " out of matrix bounds");
        } else if(B[i][j] != MNKCellState.FREE) {
            throw new IllegalStateException("Cell " + i +"," + j + " is not free");
        } else {
            MNKCell oldc = new MNKCell(i,j,B[i][j]);
            MNKCell newc = new MNKCell(i,j,Player[currentPlayer]);

            B[i][j] = Player[currentPlayer];

            FC.remove(oldc);
            MC.add(newc);

            boolean isGameEnd = isGameEnded();

            currentPlayer = (currentPlayer + 1) % 2;

            if( isGameEnd )
                gameState =  B[i][j] == MNKCellState.P1 ? MNKGameState.WINP1 : MNKGameState.WINP2;
            else if(FC.isEmpty())
                gameState = MNKGameState.DRAW;

            return gameState;
        }
    }

    public abstract boolean isGameEnded();
}
