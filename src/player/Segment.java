package player;

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
     * if segment is inside this segment will be used {@link #innerDistance(Segment, int)}
     * other wuse return the outer distance from segment's end to this segment start if it's before this segment
     * or from segment's start to this segment end otherwise
     * @param segment
     * @param side
     * @return
     */
    public int distance(Segment segment, int side) {
        if(segment.indexEnd <= indexStart) return indexStart - segment.indexEnd;
        else if(indexEnd <= segment.indexStart) return segment.indexStart - indexEnd;
        else return innerDistance(segment, side);
    }

    public int innerDistance(Segment segment, int fromSide) {
        if( indexStart <= segment.indexStart && segment.indexEnd <= indexEnd ) {
            if( fromSide == 0 ) {
                return segment.indexStart - indexStart;
            }
            else {
                return indexEnd - segment.indexEnd;
            }
        }
        return 0;
    }

    public Segment getLinkOnSide(int side, int count) {
        Segment it = this;

        for (int i = 0; i < count && it != null; i++)
            it = side <= 0 ? it.prev : it.next;

        return it;
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

            linkNext(to.next);
        }
        else {
            this.indexStart = to.indexStart;

            linkPrev(to.prev);
        }
        to.linkPrev(null);to.linkNext(null);
    }

    /**
     * Set the next segment and link this as item's previous; the other sides/links are unchanged.
     * @PostCondition on return. the caller should call {@link #updateAdjacent(int)} to make adjacent aware of this change
     * @param item
     */
    public void linkPrev(Segment item) {
        if( item != null )
            item.next = this;
        this.prev = item;
    }

    /**
     * Set the previous segment and link this as item's next; the other sides/links are unchanged.
     * @PostCondition on return. the caller should call {@link #updateAdjacent(int)} to make adjacent aware of this change
     * @param item
     */
    public void linkNext(Segment item) {
        if( item != null )
            item.prev = this;
        this.next = item;
    }

    /**
     * Links the item as previous if this segment and as next of old previous
     * @PostCondition on return, the caller must call {@link #onLinkUpdate(Segment, int)} for this and the item
     * @param item
     */
    public void insertPrev(Segment item) {
        Segment newItemPrev = prev;
        if( item != null )
            indexStart = item.indexEnd+1;
        // link prev <- new -> this
        linkPrev(item);
        if( newItemPrev != null )
            newItemPrev.linkNext(item);
    }

    /**
     * Links the item as previous if this segment and as next of old previous
     * @PostCondition on return, the caller must call {@link #onLinkUpdate(Segment, int)} for this and the item
     * @param item
     */
    public void insertNext(Segment item) {
        Segment newItemNext = next;
        if( item != null )
            indexEnd = item.indexStart-1;
        // link this -> new <- next
        linkNext(item);
        if( newItemNext != null )
            newItemNext.linkPrev(item);
    }

    /**
     * Call the {@link #onLinkUpdate(Segment, int)} callback on adjacent of both sides with adj=this and breadth params
     * @implNote Cost <ul>
     *      <li>Time: <code>O( breadth )</code></li>
     * </ul>
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
