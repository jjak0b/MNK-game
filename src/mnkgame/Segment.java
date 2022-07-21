package mnkgame;

import java.util.Objects;

public class Segment {
    int indexStart;
    int indexEnd;
    Segment next;
    Segment prev;

    public Segment(int indexStart, int indexEnd) {
        this.indexStart = indexStart;
        this.indexEnd = indexEnd;
        this.next = null;
        this.prev = null;
    }

    public Segment(Segment prev, Segment next) {
        this.indexStart = prev.indexEnd + 1;
        this.indexEnd = next.indexStart - 1;
        this.prev = prev;
        this.next = next;
    }

    /**
     * Merge this and the provided segment.
     * The provided segment will be unlinked after this operation.
     * @PostCondition on return. the caller should call {@link #updateAdjacent(int)} to make adjacent aware of this change
     * @param to
     */
    public void merge(Segment to) {
        if( to == null) return;

        if( indexEnd <= to.indexEnd ){
            this.indexEnd = to.indexEnd;

            this.next = to.next;
            if( to.next != null )
                to.next.prev = this;
        }
        else {
            this.indexStart = to.indexStart;

            this.prev = to.prev;
            if( to.prev != null )
                to.prev.next = this;
        }
        to.unLink();
    }

    /**
     * @PostCondition on return, the caller must call {@link #onLinkUpdate(Segment, int)} for this and the item
     * @param item
     */
    public void insertPrev(Segment item) {
        // link prev <- new -> this
        if( item != null ) {
            indexStart = item.indexEnd+1;

            item.next = this;
            item.prev = prev;
        }
        // link prev -> new <- this
        if( prev != null )
            prev.next = item;
        prev = item;
    }

    /**
     * @PostCondition on return, the caller must call {@link #onLinkUpdate(Segment, int)} for this and the item
     * @param item
     */
    public void insertNext(Segment item) {
        // link this <- new -> next
        if( item != null ) {
            item.prev = this;
            item.next = next;
            indexEnd = item.indexStart-1;
        }
        // link this -> new <- next
        if( next != null )
            next.prev = item;
        next = item;
    }

    /**
     * newPrev is linked before this segment.
     * newNext is linked after this segment.
     * The coordinates of this segment are updated to (newPrev.indexEnd+1, newNext.indexStart-1)
     * @PostCondition on return, the caller must call {@link #onLinkUpdate(Segment, int)} for this, newPrev, and newNext
     * @param newPrev
     * @param newNext
     */
    public void updateSize(Segment newPrev, Segment newNext ) {

        insertPrev(newPrev);
        insertNext(newNext);

    }

    /**
     * Call the {@link #onLinkUpdate(Segment, int)} callback on adjacent of both sides with adj=this and breadth params
     * @param breadth
     */
    public void updateAdjacent( int breadth ) {
        if (prev != null)
            prev.onLinkUpdate(this, breadth);
        if (next != null)
            next.onLinkUpdate(this, breadth);
    }

    public void updateAdjacent() {
        updateAdjacent(0);
    }

    /**
     * Increase this segment on a side
     * @param side 0 for left, else for right
     */
    public void grow(int side) {
        if( side == 0) {
            if( prev != null ) {
                prev.indexEnd--;
                indexStart--;
                // Unlink and remove node
                if (prev.length() < 0) {
                    Segment old = prev;
                    Segment newPrev = (old.prev != null) ? old.prev : null;

                    // detach and dispose segment
                    old.unLink();
                    // link to the old's prev
                    insertPrev(newPrev);
                }
            }
        }
        else {
            if( next != null ) {
                next.indexStart++;
                indexEnd++;
                // Unlink and remove node
                if (next.length() < 0) {
                    Segment old = next;
                    Segment newNext = (old.next != null) ? old.next : null;

                    // detach and dispose segment
                    old.unLink();
                    // link to the old's next
                    insertNext(newNext);
                }
            }
        }
    }

    /**
     * Callback called when an adjacent link ot this segment has been updated for some reason
     * and recursively call {@link #onLinkUpdate(Segment, int)} breadth times
     * on same link direction update from which this method has been called for
     *
     * @param adj adjacent segment that trigger this method
     * @param breadth count of nodes to link
     */
    public void onLinkUpdate(Segment adj, int breadth ) {
        if( breadth < 0 ) return;

        if( adj == next ) {
            if( prev != null )
                prev.onLinkUpdate(this, breadth-1);
        }
        else if( adj == prev ) {
            if( next != null )
                next.onLinkUpdate(this, breadth-1);
        }
    }

    public void unLink() {
        if( prev != null ) {
            prev.next = null;
        }
        if( next != null ) {
            next.prev = null;
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
