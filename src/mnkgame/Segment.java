package mnkgame;

import java.util.Objects;

public class Segment {
    int indexStart;
    int indexEnd;

    public Segment(int indexStart, int indexEnd) {
        this.indexStart = indexStart;
        this.indexEnd = indexEnd;
    }

    public void link(Segment to) {
        if( indexEnd <= to.indexEnd )
            this.indexEnd = to.indexEnd;
        else {
            this.indexStart = to.indexStart;
        }
    }

    public int length() {
        return indexStart >= 0 && indexEnd >= 0 ? indexEnd - indexStart : -1;
    }

    public boolean contains(int index) {
        return indexStart <= index && index <= indexEnd;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if( !(o instanceof Segment ) ) return false;
        Segment that = (Segment) o;
        return indexStart == that.indexStart && indexEnd == that.indexEnd;
    }

    @Override
    public int hashCode() {
        return Objects.hash(indexStart, indexEnd);
    }

    @Override
    public String toString() {
        return "Segment{" +
                "" + indexStart +
                ", "+ indexEnd +
                '}';
    }
}
