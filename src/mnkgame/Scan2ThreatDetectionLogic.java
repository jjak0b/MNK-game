package mnkgame;

import java.util.Comparator;
import java.util.Objects;
import java.util.TreeSet;

public class Scan2ThreatDetectionLogic implements ThreatDetectionLogic<ThreatT> {

    protected int M, N, K;

    protected RowOfBlocks[][] blocksOnDirection;

    public RowOfBlocks[] getRowsOfBlocksOnDirection( int directionType) {
        return blocksOnDirection[directionType];
    }

    public RowOfBlocks getBlocksOnDirection(int i, int j, int directionType) {
        final int[] coordinates = matrixCoordsToDirectionTypeCoords(i, j, directionType);
        return blocksOnDirection[directionType][ coordinates[0] ];
    }

    /**
     * Init the free and streaks blocks: Init for each @{Utils.DIRECTIONS}, rows of Blocks
     * Each row is initialized as a long "big" free block
     * for
     * @param M rows count
     * @param N columns count
     * @param K streak count
     */
    @Override
    public void init(int M, int N, int K) {
        this.M = M;
        this.N = N;
        this.K = K;

        final Comparator<Segment> segmentsComparator = new Comparator<Segment>() {
            @Override
            public int compare(Segment o1, Segment o2) {
                return Integer.compare(o1.indexStart, o2.indexStart);
            }
        };

        blocksOnDirection = new RowOfBlocks[Utils.DIRECTIONS.length][];
        int rowsCount = 0;
        int columnsCount = 0;
        for ( int directionType :Utils.DIRECTIONS ) {
            // rows and columns sizes
            switch (directionType) {
                case Utils.DIRECTION_TYPE_VERTICAL:
                    rowsCount = N;
                    columnsCount = M;
                    break;
                case Utils.DIRECTION_TYPE_HORIZONTAL:
                    rowsCount = M;
                    columnsCount = N;
                    break;
                case Utils.DIRECTION_TYPE_OBLIQUE_LR:
                case Utils.DIRECTION_TYPE_OBLIQUE_RL:
                    rowsCount = N + M - 1;
                    break;
            }
            blocksOnDirection[ directionType ] = new RowOfBlocks[rowsCount];

            // init each row as a "big" long free block
            switch (directionType) {
                case Utils.DIRECTION_TYPE_VERTICAL:
                case Utils.DIRECTION_TYPE_HORIZONTAL:
                    for (int i = 0; i < rowsCount; i++) {
                        blocksOnDirection[ directionType ][i] = new RowOfBlocks(segmentsComparator, columnsCount);
                    }
                    break;
                case Utils.DIRECTION_TYPE_OBLIQUE_LR:
                case Utils.DIRECTION_TYPE_OBLIQUE_RL:
                    int bound = Math.min(M, N);
                    // these rows have different lengths: 1 ... bound^(rows-bound +1 times ) ... 1
                    int i;
                    for (i = 0; i < bound ; i++) {
                        columnsCount = i+1;
                        // add a "big" block long as the columns count of this direction
                        blocksOnDirection[ directionType ][i] = new RowOfBlocks(segmentsComparator, columnsCount);
                        blocksOnDirection[ directionType ][(rowsCount-1)-i] = new RowOfBlocks(segmentsComparator, columnsCount);
                    }
                    columnsCount = bound;
                    bound = (rowsCount) - bound + 1;
                    for (; i < bound; i++) {
                        // add a "big" block long as the columns count of this direction
                        blocksOnDirection[ directionType ][i] = new RowOfBlocks(segmentsComparator, columnsCount);
                    }
                    break;
            }

        }
    }

    @Override
    public void mark(MNKBoard tree, MNKCell marked, int depth) {
        for ( int directionType : Utils.DIRECTIONS ) {
            updateBlockAndAdjacentOnDirection(marked.i, marked.j, directionType, marked.state, true );
        }
    }

    @Override
    public void unMark(MNKBoard tree, MNKCell oldMarked, int depth) {
        for ( int directionType : Utils.DIRECTIONS ) {
            updateBlockAndAdjacentOnDirection(oldMarked.i, oldMarked.j, directionType, MNKCellState.FREE, false );
        }
    }

    @Override
    public ScanResult getThreatInDirection(MNKCell source, int directionType) {
        return null;
    }

    public void updateBlockAndAdjacentOnDirection(int i, int j, int directionType, MNKCellState color, boolean isMark) {
        int[] coords = matrixCoordsToDirectionTypeCoords(i, j, directionType);
        int row = coords[0]; int column = coords[1];

        ScanResult scanResult = new ScanResult();
        scanResult.directionType = directionType;
        scanResult.onSideMarked = new int[2];
        scanResult.onSideFree = new int[2];
        scanResult.onSideMarkedNear = new int[2];

        RowOfBlocks blocks = blocksOnDirection[directionType][row];
        // Debug.println(" Before Move "+ (i + "," + j + "-> " + row + "," + column) + " on dir " + directionType + " " + isMark + " : \n" + blocks.toString());
        Segment leftPartition, rightPartition;
        Segment lower = null, lowerOrEq, higher = null;
        boolean shouldMergeLeft = false;
        boolean shouldMergeRight = false;
        Segment myCellBlock;
        if( isMark ) {
            myCellBlock = new Streak(column, column, color);
            lowerOrEq = blocks.floor(myCellBlock);

            assert lowerOrEq != null;
            assert lowerOrEq.contains(column);
            assert !(lowerOrEq instanceof Streak);

            leftPartition = new Segment(lowerOrEq.indexStart, myCellBlock.indexStart-1);
            rightPartition = new Segment(myCellBlock.indexEnd+1, lowerOrEq.indexEnd);

            if( leftPartition.length() < 0 ) {
                lower = blocks.lower(myCellBlock);
                shouldMergeLeft = lower instanceof Streak && ((Streak) lower).color == color;

                scanResult.onSideFree[0] = 0;
            }
            else {
                scanResult.onSideFree[0] = 1 + leftPartition.length();
            }

            if( rightPartition.length() < 0 ) {
                higher = blocks.higher(myCellBlock);
                shouldMergeRight = higher instanceof Streak && ((Streak) higher).color == color;

                scanResult.onSideFree[1] = 0;
            }
            else {
                scanResult.onSideFree[1] = 1 + rightPartition.length();
            }
        }
        else {
            myCellBlock = new Segment(column, column);
            lowerOrEq = blocks.floor(myCellBlock);

            assert lowerOrEq != null;
            assert lowerOrEq.contains(column);
            assert (lowerOrEq instanceof Streak);
            color = ((Streak) lowerOrEq).color;
            leftPartition = new Streak(lowerOrEq.indexStart, myCellBlock.indexStart - 1, color);
            rightPartition = new Streak(myCellBlock.indexEnd + 1, lowerOrEq.indexEnd, color);

            if( leftPartition.length() < 0 ) {
                lower = blocks.lower(myCellBlock);
                shouldMergeLeft = lower != null && !(lower instanceof Streak);
            }
            if( rightPartition.length() < 0 ) {
                higher = blocks.higher(myCellBlock);
                shouldMergeRight = higher != null && !(higher instanceof Streak);
            }
        }

        // (un)marked unitary cell -> try join adjacent
        if( shouldMergeLeft && shouldMergeRight) {
            blocks.remove(lowerOrEq);
            blocks.remove(higher);

            if( isMark ) {
                scanResult.onSideMarked[0] = 1 + lower.length();
                scanResult.onSideMarked[1] = 1 + higher.length();
            }
            else {
                scanResult.onSideFree[0] = 1 + lower.length();
                scanResult.onSideFree[1] = 1 + higher.length();
            }

            lower.indexEnd = higher.indexEnd;

            // now evaluate threat
            higher = lower;
            Segment base;
            for (int side = 0; side < 2; side++) {
                base = side == 0 ? blocks.lower(lower) : blocks.higher(higher);

                if( isMark ) {
                    // if lower is a Streak then is always a different color
                    if( base != null && !(base instanceof Streak ) )
                        scanResult.onSideFree[side] = 1 + base.length();
                }


                // if isn't enough for a threat
                // then check more lower if there is a possible greater streak that can be completed
                if( scanResult.onSideFree[side] + scanResult.onSideMarked[0] + scanResult.onSideMarked[1] < K ) {
                    base = side == 0 ? blocks.lower(lower) : blocks.higher(higher);
                    if( base instanceof Streak && ((Streak) base).color == color )
                        scanResult.onSideMarkedNear[side] = 1+base.length();
                }
            }
        }
        // (un)marked on the edge -> join edge
        else if( shouldMergeLeft ) {
            lowerOrEq.indexStart++;
            if( lowerOrEq.length() < 0 )
                blocks.remove(lowerOrEq);
            lower.indexEnd++;
        }
        // (un)marked on the edge -> join edge
        else if( shouldMergeRight ){
            lowerOrEq.indexEnd--;
            if( lowerOrEq.length() < 0 )
                blocks.remove(lowerOrEq);
            higher.indexStart--;
        }
        // (un)marked on inside the block -> split in 3 blocks
        else {
            blocks.remove(lowerOrEq);
            if( leftPartition.length() >= 0 )
                blocks.add(leftPartition);
            blocks.add(myCellBlock);
            if( rightPartition.length() >= 0 )
                blocks.add(rightPartition);
        }

        // Debug.println(" After Move on dir " + directionType + " " + isMark + " : " + (shouldMergeLeft) + " " + (shouldMergeRight) + "\n" + blocks.toString());
    }

    /**
     *
     * @param threat
     * @return true if the streak can be completed or not
     */
    @Override
    public boolean isCandidate( ThreatT threat ) {
        int streakCount = (1+threat.length());
        int free;
        int freeOnTotal = 0;

        int other = 0;
        for (int side = 0; side < 2; side++) {
            free = threat.free[side];
            other = threat.other[side];
            freeOnTotal += free;

            if( streakCount + free + other >= K
            || streakCount + freeOnTotal + other >= K )
                return true;
        }
        return false;
    }

    public int[] matrixCoordsToDirectionTypeCoords(int i, int j, int directionType) {
        final int[] coordinates = {0, 0};
        switch (directionType){
            case Utils.DIRECTION_TYPE_HORIZONTAL:
                coordinates[0] = i;
                coordinates[1] = j;
                break;
            case Utils.DIRECTION_TYPE_VERTICAL:
                coordinates[0] = j;
                coordinates[1] = i;
                break;
            case Utils.DIRECTION_TYPE_OBLIQUE_LR:
                // conceptual note: "0" index is in (i, 0) and (M-1, j)
                coordinates[0] =  i + j;
                coordinates[1] =  Math.min(i, (N-1) - j);
                break;
            case Utils.DIRECTION_TYPE_OBLIQUE_RL:
                // conceptual note: "0" index is in (i, N-1) and (0, j)
                coordinates[0] = i + ((N-1) - j);
                coordinates[1] = Math.min(i, j);
                break;
        }
        return coordinates;
    }
}

class RowOfBlocks extends TreeSet<Segment> {
    int rowSize;

    public RowOfBlocks(Comparator<Segment> comparator) {
        super(comparator);
    }

    public RowOfBlocks(Comparator<Segment> comparator, int size) {
        this(comparator);
        this.rowSize = size;
        this.add(new Segment(0, this.rowSize-1));
    }
}

class Streak extends Segment {
    public MNKCellState color;

    public Streak(int indexStart, int indexEnd, MNKCellState color) {
        super(indexStart, indexEnd);
        this.color = color;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        Streak streak = (Streak) o;
        return color == streak.color;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), color);
    }

    @Override
    public String toString() {
        return "Streak{" +
                super.toString() +
                ",color=" + color +
                '}';
    }
}

class ThreatT extends Streak {
    // free segments on left and right
    int[] free = {0, 0};
    int[] other = {0, 0};;

    public ThreatT(Streak streak) {
        super(streak.indexStart, streak.indexEnd, streak.color);
    }

    public int getStreakCount() {
        return 1+length();
    }
}