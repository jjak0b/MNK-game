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
     * @PostCondition on return. the caller should call {@link #updateAdjacent()} to make adjacent aware of this change
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
     * @PostCondition on return, the caller must call {@link #onLinkUpdate(Segment)} for this and the item
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
     * @PostCondition on return, the caller must call {@link #onLinkUpdate(Segment)} for this and the item
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
     * @PostCondition on return, the caller must call {@link #onLinkUpdate(Segment)} for this, newPrev, and newNext
     * @param newPrev
     * @param newNext
     */
    public void updateSize(Segment newPrev, Segment newNext ) {

        insertPrev(newPrev);
        insertNext(newNext);

    }

    /**
     * Call the {@link #onLinkUpdate(Segment)} callback on adjacent
     */
    public void updateAdjacent() {
        if( prev != null )
            prev.onLinkUpdate( this );
        if( next != null )
            next.onLinkUpdate( this );
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
                if (prev.length() < 0) {
                    // unlink and link to the prev0s prev
                    Segment old = prev;
                    if (old.prev != null) {
                        old.prev.next = this;
                    }
                    prev = old.prev;
                    old.unLink();
                }
            }
        }
        else {
            if( next != null ) {
                next.indexStart++;
                indexEnd++;
                if (next.length() < 0) {
                    // unlink and link to the next's next
                    Segment old = next;
                    if (old.next != null)
                        old.next.prev = this;
                    next = old.next;
                    old.unLink();
                }
            }
        }
    }

    /**
     * Callback called when an adjacent link ot this segment ahs been updated for some reason
     * @param adj
     */
    public void onLinkUpdate(Segment adj ) { }

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
