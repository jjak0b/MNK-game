package player;

import mnkgame.MNKCell;
import mnkgame.MNKCellState;
import mnkgame.MNKGameState;

public interface Board {

    MNKCellState cellState(int i, int j) throws IndexOutOfBoundsException;

    int currentPlayer();

    MNKGameState gameState();

    void unmarkCell() throws IllegalStateException;

    MNKGameState markCell(int i, int j) throws IndexOutOfBoundsException, IllegalStateException;

    MNKCell getLastMarked();

    MNKCell[] getFreeCells();

    int getFreeCellsCount();

    MNKCell[] getMarkedCells();

    int getMarkedCellsCount();
}
