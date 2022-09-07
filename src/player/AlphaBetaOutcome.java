package player;

import mnkgame.MNKCell;
import mnkgame.MNKGameState;

public class AlphaBetaOutcome implements Comparable<AlphaBetaOutcome> {
    public int eval;
    public int depth;
    public MNKCell move;
    public MNKGameState state;

    public int getWeightedValue() {
        return eval / (Math.max(1, depth));
    }

    public AlphaBetaOutcome(AlphaBetaOutcome value) {
        this.eval = value.eval;
        this.move = value.move;
        this.depth = value.depth;
        this.state = value.state;
    }

    public AlphaBetaOutcome() {
        state = MNKGameState.OPEN;
    }

    @Override
    public int compareTo(AlphaBetaOutcome o) {
        return o == null ? 1 : Integer.compare(eval, o.eval);
    }

    public static AlphaBetaOutcome max(AlphaBetaOutcome o1, AlphaBetaOutcome o2) {
        int compare = o1.compareTo(o2);

        if( compare < 0 )
            return o2;
        else if( compare > 0 )
            return o1;
        else if( o1.depth < o2.depth )
            return o1;
        else if( o1.depth > o2.depth )
            return o2;
        else
            return o1;
    }

    public static AlphaBetaOutcome min(AlphaBetaOutcome o1, AlphaBetaOutcome o2) {
        int compare = o1.compareTo(o2);

        if( compare < 0 )
            return o1;
        else if( compare > 0 )
            return o2;
        else if( o1.depth < o2.depth )
            return o1;
        else if( o1.depth > o2.depth )
            return o2;
        else
            return o1;
    }

    @Override
    public String toString() {
        return "AlphaBetaOutcome{" +
                "eval=" + eval +
                ", move=" + move +
                ", depth=" + depth +
                ", state=" + state +
                '}';
    }
}
