package mnkgame;

class AlphaBetaOutcome implements Comparable<AlphaBetaOutcome> {
    public int eval;
    public MNKCell move;
    public int depth;

    public int getWeightedValue() {
        return eval / (Math.max(1, depth));
    }

    public AlphaBetaOutcome(AlphaBetaOutcome value) {
        this.eval = value.eval;
        this.move = value.move;
        this.depth = value.depth;
    }

    public AlphaBetaOutcome() {

    }

    @Override
    public int compareTo(AlphaBetaOutcome o) {
        if (o == null) {
            return 1;
        } else {
            int thisWeight = getWeightedValue();
            int thatWeight = o.getWeightedValue();

            if (thisWeight > thatWeight) {
                // return 1;
                return thisWeight - thatWeight;
            } else if (thisWeight < thatWeight) {
                // return -1;
                return thisWeight - thatWeight;
            } else {
                if (depth < o.depth) {
                    return 1;
                } else if (depth > o.depth) {
                    return -1;
                } else {
                    return 0;
                }
            }
        }
    }
}
