package player;

import java.math.BigInteger;

public class HashableBoardState {

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

    public HashableBoardState() {
        this.currentState = BigInteger.ZERO;
    }

    public BigInteger getCurrentState() {
        return currentState;
    }

    public void toggle( int playerIndex, int cellIndex) {
        // mark / unmark bitfield at (i, j)
        currentState = currentState.xor(
                // set the mark/unmark, shifting left by X * 2 positions, where X is the array coordinate of the matrix
                CELL_STATE_BITS_FOR_PLAYER[playerIndex].shiftLeft( cellIndex * 2 )
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HashableBoardState)) return false;
        HashableBoardState that = (HashableBoardState) o;
        return currentState.equals(that.currentState);
    }

    @Override
    public int hashCode() {
        return currentState.hashCode();
    }
}
