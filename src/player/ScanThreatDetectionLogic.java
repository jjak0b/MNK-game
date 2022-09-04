package player;

import mnkgame.MNKCell;
import mnkgame.MNKCellState;

import java.util.*;

public class ScanThreatDetectionLogic implements ThreatDetectionLogic<ScanThreatDetectionLogic.Threat>, Comparator<ScanThreatDetectionLogic.Threat> {

    protected int M, N, K;
    public int[] bonusScoreOnMovesLeft;

    // these are buckets of Trees, organized to make search fast O(log)
    public RowOfBlocks[][] blocksOnDirection;
    // these are buckets of a kind of modified Priority(Heap / queues) Stack
    public PriorityThreatsTracker[][] playerThreatsOnDirection;
    // coordinate Map from board to row indexes for direction
    public int[][][][] coordinatesMap;
    public int[][][][] invCoordinatesMap;

    // pool of threats to remove per player
    protected List<Threat>[] threatsToRemove;
    // pool of threats to add per player
    protected List<Threat>[] threatsToAdd;
    protected Set<MNKCell> freeToUpdate;

    protected int[][][] playerMovesLeftsCount;
    protected int[][][][] playerMovesLeft;
    protected int[][] minPlayerMovesLeftCache;
    protected int[][][][] freeCellsPriorities;
    protected int[][] freeCellsPrioritiesCache;
    protected PriorityQueue<MNKCell> freePriorityQueue;

    protected final WorkingCellInfo workingCellInfo = new WorkingCellInfo();

    static class WorkingCellInfo {
        public final int[] matrixCoords;
        public final int[] directionTypeCoords;
        public MNKCellState color;
        public boolean isMark;
        public int directionType;

        public WorkingCellInfo() {
            matrixCoords = new int[2];
            directionTypeCoords = new int[2];
            color = null;
            isMark = false;
            directionType = 0;
        }

        public void update(final int[] matrixCoords, final int[] directionTypeCoords, MNKCellState color, int directionType, boolean isMark) {
            this.color = color;
            this.directionType = directionType;
            this.isMark = isMark;
            Vectors.vectorCopy(this.matrixCoords, matrixCoords);
            Vectors.vectorCopy(this.directionTypeCoords, directionTypeCoords);
        }

        @Override
        public String toString() {
            return "WorkingCellInfo{" +
                    "matrixCoords=" + Arrays.toString(matrixCoords) +
                    ", directionTypeCoords=" + Arrays.toString(directionTypeCoords) +
                    ", color=" + color +
                    ", isMark=" + isMark +
                    ", directionType=" + directionType +
                    '}';
        }
    }

    /**
     * Check if vector as point is inside the sizes of row on a direction
     * @param v
     * @return true if is inside, false otherwise
     */
    protected boolean isVectorInBounds( int[] v, int directionType) {
        return 0 <= v[0] && v[0] < blocksOnDirection[ directionType ].length
            && 0 <= v[1] && v[1] < blocksOnDirection[ directionType ][ v[0] ].rowSize;
    }

    public PriorityQueue<MNKCell> getFree() {
        return freePriorityQueue;
    }

    public void setMovesLeftAt(int playerIndex, int directionType, int i, int j, int movesLeftCount ) {
        playerMovesLeft[playerIndex][directionType][i][j] = movesLeftCount;
    }

    public int getMovePriority(int playerIndex, int directionType, int i, int j) {
        return freeCellsPriorities[playerIndex][directionType][i][j];
    }
    public void setMovePriority(int playerIndex, int directionType, int i, int j, int priority ) {
        freeCellsPriorities[playerIndex][directionType][i][j] = priority;
    }

    public int getMovesLeftAt(int playerIndex, int directionType, int i, int j) {
        return playerMovesLeft[playerIndex][directionType][i][j];
    }

    public int[][] getMovePriority(int playerIndex, int directionType) {
        return freeCellsPriorities[playerIndex][directionType];
    }
    public int[][] getMovesLeft(int playerIndex, int directionType) {
        return playerMovesLeft[playerIndex][directionType];
    }

    public int[][] getMinMovesLeft() {
        return minPlayerMovesLeftCache;
    }

    public RowOfBlocks[] getRowsOfBlocksOnDirection(int directionType) {
        return blocksOnDirection[directionType];
    }

    /**
     * get the count of streaks that have "i" left moves, where is any index of the returned array
     * @PostCondition use the returned array as read-only
     * @param playerIndex
     * @param directionType
     * @return an internal reference of the array
     */
    public int[] getMovesLeftArrayCount(int playerIndex, int directionType) {
        return playerMovesLeftsCount[ playerIndex ][ directionType ];
    }

    @Override
    public Threat getBestThreat(int playerIndex, int directionType) {
        return playerThreatsOnDirection[playerIndex][directionType].peek();
    }

    public RowOfBlocks getBlocksOnDirection(int i, int j, int directionType) {
        final int[] coordinates = matrixCoordsToDirectionTypeCoords(i, j, directionType);
        return blocksOnDirection[directionType][ coordinates[0] ];
    }
    public Segment getBlockAt(int i, int j, int directionType) {
        final int[] coordinates = matrixCoordsToDirectionTypeCoords(i, j, directionType);
        return blocksOnDirection[directionType][ coordinates[0] ].floor(new Segment(coordinates[1], coordinates[1]));
    }
    public Segment toBlock(int i, int j, int directionType) {
        final int[] coordinates = matrixCoordsToDirectionTypeCoords(i, j, directionType);
        return new Segment(coordinates[1], coordinates[1]);
    }

    public Threat getThreatAt(int i, int j, int directionType) {
        Segment block = getBlockAt(i,j,directionType);
        if( block instanceof Threat) return (Threat) block;
        else return null;
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

        // on each side. does up to 3 breadth updates
        freeToUpdate = new HashSet<>(3*2*(Utils.DIRECTIONS.length));
        playerMovesLeftsCount = new int[2][Utils.DIRECTIONS.length][K];
        playerMovesLeft = new int[2][Utils.DIRECTIONS.length][M][N];
        freeCellsPriorities = new int[2][Utils.DIRECTIONS.length][M][N];
        minPlayerMovesLeftCache = new int[M][N];
        freeCellsPrioritiesCache = new int[M][N];
        freePriorityQueue = new PriorityQueue<>(M * N, new Comparator<MNKCell>() {
            @Override
            public int compare(MNKCell o1, MNKCell o2) {

                int cmp;
                cmp = Integer.compare(freeCellsPrioritiesCache[ o1.i ][ o1.j ], freeCellsPrioritiesCache[ o2.i ][ o2.j ]);
                // cmp = Integer.compare(minPlayerMovesLeftCache[ o1.i ][ o1.j ], minPlayerMovesLeftCache[ o2.i ][ o2.j ]);
                if( cmp == 0 ) Utils.compare(o1, o2); // make it stable
                return cmp;
                // return
            }
        }.reversed());
        for (int i = 0; i < M; i++) {
            for (int j = 0; j < N; j++) {
                for (int p = 0; p < 2; p++) {
                    for (int directionType : Utils.DIRECTIONS) {
                        setMovesLeftAt(p, directionType, i, j, K);
                        setMovePriority(p, directionType, i, j, 0);
                    }
                }
                minPlayerMovesLeftCache[ i ][ j ] = K;
                freeCellsPrioritiesCache[ i ][ j ] = 0;
                freePriorityQueue.add(new MNKCell(i, j));
            }
        }


        // Init Not needed on java
        // for (int i = 0; i < playerMovesLeftsCount.length; i++) playerMovesLeftsCount[i] = 0;

        // On worst case a free block can be split to 3 blocks after a mark/unmark -> so 1 to remove and 3 to add or 3 to remove and 1 to add
        // but near blocks have to update their score, so needs to update their positions on priority queues
        // each block can be near in sequence to a "free" block, "another marked" block, and a "other free" block -> so up to 2 threats per side must be updated
        final int poolSize = (2*2) + 3;
        threatsToRemove = new List[]{ new ArrayList<Threat>( poolSize ), new ArrayList<Threat>( poolSize) };
        threatsToAdd = new List[]{ new ArrayList<Threat>( poolSize ), new ArrayList<Threat>( poolSize) };

        bonusScoreOnMovesLeft = new int[]{ 0, 100000, 1000, 10 };
        playerThreatsOnDirection = new PriorityThreatsTracker[2][Utils.DIRECTIONS.length];

        coordinatesMap = new int[Utils.DIRECTIONS.length][M][N][ 2 ];
        invCoordinatesMap = new int[Utils.DIRECTIONS.length][][][];

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
                    invCoordinatesMap[ directionType ] = new int[rowsCount][columnsCount][2];
                    for (int i = 0; i < rowsCount; i++) {
                        blocksOnDirection[ directionType ][i] = new RowOfBlocks(segmentsComparator, columnsCount);
                    }
                    break;
                case Utils.DIRECTION_TYPE_OBLIQUE_LR:
                case Utils.DIRECTION_TYPE_OBLIQUE_RL:
                    invCoordinatesMap[ directionType ] = new int[rowsCount][][];
                    int bound = Math.min(M, N);
                    // these rows have different lengths: 1 ... bound^(rows-bound +1 times ) ... 1
                    int i;
                    for (i = 0; i < bound ; i++) {
                        columnsCount = i+1;

                        invCoordinatesMap[ directionType ][ i ] = new int[columnsCount][2];
                        invCoordinatesMap[ directionType ][(rowsCount-1)-i ] = new int[columnsCount][2];

                        // add a "big" block long as the columns count of this direction
                        blocksOnDirection[ directionType ][i] = new RowOfBlocks(segmentsComparator, columnsCount);
                        blocksOnDirection[ directionType ][(rowsCount-1)-i] = new RowOfBlocks(segmentsComparator, columnsCount);
                    }
                    columnsCount = bound;
                    bound = (rowsCount) - bound + 1;
                    for (; i < bound; i++) {
                        invCoordinatesMap[ directionType ][i] = new int[columnsCount][2];

                        // add a "big" block long as the columns count of this direction
                        blocksOnDirection[ directionType ][i] = new RowOfBlocks(segmentsComparator, columnsCount);
                    }
                    break;
            }

            for (int playerIndex = 0; playerIndex < 2; playerIndex++) {
                playerThreatsOnDirection[playerIndex][directionType] = new PriorityThreatsTracker(blocksOnDirection[ directionType ], this);
            }

            int i = 0, j = 0;
            // init coordinates mapping
            switch(directionType) {
                case Utils.DIRECTION_TYPE_HORIZONTAL:
                    for ( i = 0; i < M; i++) {
                        for ( j = 0; j < N; j++) {
                            coordinatesMap[directionType][i][j][0] = i;
                            coordinatesMap[directionType][i][j][1] = j;
                            invCoordinatesMap[directionType][i][j][0] = i;
                            invCoordinatesMap[directionType][i][j][1] = j;
                        }
                    }
                    break;
                case Utils.DIRECTION_TYPE_VERTICAL:
                    for ( j = 0; j < N; j++) {
                        for ( i = 0; i < M; i++) {
                            coordinatesMap[directionType][i][j][0] = j;
                            coordinatesMap[directionType][i][j][1] = i;

                            invCoordinatesMap[directionType][j][i][0] = i;
                            invCoordinatesMap[directionType][j][i][1] = j;
                        }
                    }
                    break;
                case Utils.DIRECTION_TYPE_OBLIQUE_LR:
                    i = 0; j = 0;
                    for (; i < M; i++) {
                        for (int k = 0; i - k >= 0 && j + k < N; k++) {
                            int r = i - k;
                            int c = j + k;
                            coordinatesMap[directionType][r][c][0] = i + j;
                            coordinatesMap[directionType][r][c][1] = k;

                            invCoordinatesMap[directionType][i + j][k][0] = r;
                            invCoordinatesMap[directionType][i + j][k][1] = c;
                        }
                    }
                    i = M-1; j = 1;
                    for(; j < N; j++) {
                        for (int k = 0; i - k >= 0 && j + k < N; k++) {
                            int r = i - k;
                            int c = j + k;
                            coordinatesMap[directionType][r][c][0] = i + j;
                            coordinatesMap[directionType][r][c][1] = k;

                            invCoordinatesMap[directionType][i + j][k][0] = r;
                            invCoordinatesMap[directionType][i + j][k][1] = c;
                        }
                    }
                    break;
                case Utils.DIRECTION_TYPE_OBLIQUE_RL:
                    i = 0; j = N-1;
                    for(; j >= 0; j--) {
                        for (int k = 0; i + k < M && j + k < N; k++) {
                            int r = i + k;
                            int c = j + k;
                            coordinatesMap[directionType][r][c][0] = i + (N -1 - j);
                            coordinatesMap[directionType][r][c][1] = k;

                            invCoordinatesMap[directionType][i + (N -1 - j)][k][0] = r;
                            invCoordinatesMap[directionType][i + (N -1 - j)][k][1] = c;
                        }
                    }
                    i = 1; j = 0;
                    for(; i < M; i++) {
                        for (int k = 0; i + k < M && j + k < N; k++) {
                            int r = i + k;
                            int c = j + k;
                            coordinatesMap[directionType][r][c][0] = i + (N -1 - j);
                            coordinatesMap[directionType][r][c][1] = k;

                            invCoordinatesMap[directionType][i + (N -1 - j)][k][0] = r;
                            invCoordinatesMap[directionType][i + (N -1 - j)][k][1] = c;
                        }
                    }
                    break;
            }
        }
    }

    /**
     * Mark the provided move and update the threats and free cells weights for each direction from the marked move
     * @implNote Cost <ul>
     *      <li>Time: <code>O( T({@link #updateBlockAndAdjacentOnDirection}) + T({@link PriorityQueue#remove()}, N*M) + T({@link #flushUpdatePool()}) )</code>
     *      <ul>
     *          <li>Time using standard {@link PriorityQueue} API: <code>O( N*M )</code></li>
     *          <li>Time using optimal API: <pre>O( log(M+N) + log(M*N) ) = O( log(M+N) + log(M) + log(N)) = O( log(M+N) )</pre></li>
     *      </ul></li>
     * </ul>
     *
     * @implNote Because of {@link PriorityQueue#remove} API, it require linear time, but same operation on different {@link PriorityQueue} API could be done in log time
     * @param marked
     * @param markingPlayer
     * @param depth
     */
    @Override
    public void mark(MNKCell marked, int markingPlayer, int depth) {
        int playerIndex = getPlayerIndex(marked.state);
        for ( int directionType : Utils.DIRECTIONS ) {
            Threat result = updateBlockAndAdjacentOnDirection(playerIndex, marked, directionType, marked.state, true );
            onScanCallback(result, directionType, marked, true, playerIndex);
        }
        freePriorityQueue.remove(new MNKCell(marked.i, marked.j)); // in theory O(log N*M), as java api O(N*M)
        flushUpdatePool(); // in theory O(log N*M), as java api O(N*M)
    }

    /**
     * unMark the provided move and update the threats and free cells weights for each direction from the marked move
     * @implNote Cost <ul>
     *      <li>Time: <code>O( T({@link #updateBlockAndAdjacentOnDirection}) + T({@link PriorityQueue#add}, N*M) + T({@link #flushUpdatePool()}) )</code>
     *      <ul>
     *          <li>Time using standard {@link PriorityQueue} API: <code>O( N*M )</code></li>
     *          <li>Time using optimal API: <pre>O( log(M+N) + log(M*N) ) = O( log(M+N) + log(M) + log(N)) = O( log(M+N) )</pre></li>
     *      </ul></li>
     * </ul>
     * @implNote Because of {@link PriorityQueue#remove} API, it require linear time, but same operation on different {@link PriorityQueue} API could be done in log time
     * @param oldMarked
     * @param unMarkingPlayer
     * @param depth
     */
    @Override
    public void unMark(MNKCell oldMarked, int unMarkingPlayer, int depth) {
        int playerIndex = getPlayerIndex(oldMarked.state);
        for ( int directionType : Utils.DIRECTIONS ) {
            Threat result = updateBlockAndAdjacentOnDirection(playerIndex, oldMarked, directionType, MNKCellState.FREE, false );
            onScanCallback(result, directionType, oldMarked, false, playerIndex);
        }
        freePriorityQueue.add(new MNKCell(oldMarked.i, oldMarked.j)); // O(log N*M)
        flushUpdatePool(); // in theory O(log N*M), as java api O(N*M)
    }

    /**
     * mark or unMark the provided move and update the adjacent threats and free segments an a direction
     * from the segment that contains the move to other 3 adjacent segments and in sequence per side by breadth .
     * @implNote The amount of updated segments are 1 + 3 per side for each direction: O( 7 * c ) = O(1).
     * The segments updates involves only as multiplication constant, but it requires O(log N+M) time cost in other operations.
     * Cost : <ul>
     *     <li>Time: <code>O(log N+M)</code></li>
     * </ul>
     * @param playerIndex
     * @param oldMarked
     * @param directionType
     * @param color
     * @param isMark
     * @return
     */
    public Threat updateBlockAndAdjacentOnDirection(int playerIndex, MNKCell oldMarked, int directionType, MNKCellState color, boolean isMark) {

        int i = oldMarked.i, j = oldMarked.j;
        final int[] source = {i, j};
        int[] coords = matrixCoordsToDirectionTypeCoords(i, j, directionType);
        int row = coords[0]; int column = coords[1];

        workingCellInfo.update(source, coords, oldMarked.state, directionType, isMark);

        for (int p = 0; p < 2; p++) {
            threatsToRemove[p].clear();
            threatsToAdd[p].clear();
        }

        RowOfBlocks blocks = blocksOnDirection[directionType][row];
        // Debug.println(" Before Move "+ (i + "," + j + "-> " + row + "," + column) + " on dir " + directionType + " " + isMark + " : \n" + blocks.toString());
        Segment leftPartition, rightPartition;
        Segment lower = null, lowerOrEq, higher = null;
        // relative to marked block
        Segment[] lowers = new Segment[3];
        Segment[] highers = new Segment[3];

        // which side must be merged
        boolean shouldMergeLeft = false;
        boolean shouldMergeRight = false;

        Segment myCellBlock;
        Streak streakBlock = null;

        /**
         * Need to find the segment that contains the updated move, and update other segment into the tree
         * - find through a tree search -> O(log size )
         * - update the tree -> add and remove: O(log size)
         * but size depends by max possible count of segments on a specific row.
         * From {@link #init(int, int, int)} we have {@link RowOfBlocks} of size = O(max( M+N, M, N) ) = O(M+N)
         */
        if( isMark ) {
            myCellBlock = new Threat(new Streak(column, column, color));
            lowerOrEq = blocks.floor(myCellBlock);

            assert lowerOrEq != null;
            assert lowerOrEq.contains(column);
            assert !(lowerOrEq instanceof Threat);

            leftPartition = new FreeSegment(lowerOrEq.indexStart, myCellBlock.indexStart-1);
            rightPartition = new FreeSegment(myCellBlock.indexEnd+1, lowerOrEq.indexEnd);

            if( leftPartition.length() < 0 ) {
                lowers[0] = lowerOrEq.prev; // blocks.lower(myCellBlock);
                lower = lowers[0];
                shouldMergeLeft = lower instanceof Threat && ((Threat) lower).getColor() == color;
            }
            else {
                lowers[0] = leftPartition;
            }

            if( rightPartition.length() < 0 ) {
                highers[0] = lowerOrEq.next; // blocks.higher(myCellBlock);
                higher = highers[0];
                shouldMergeRight = higher instanceof Threat && ((Threat) higher).getColor() == color;
            }
            else {
                highers[0] = rightPartition;
            }
        }
        else {
            myCellBlock = new FreeSegment(column, column);
            lowerOrEq = blocks.floor(myCellBlock);
            // backup threat to remove
            streakBlock = new Threat((Threat) lowerOrEq);

            assert lowerOrEq != null;
            assert lowerOrEq.contains(column);
            assert (lowerOrEq instanceof Threat);
            color = ((Streak) lowerOrEq).color;

            leftPartition = new Threat(((Threat)lowerOrEq));
            leftPartition.indexEnd = myCellBlock.indexStart - 1;

            rightPartition = new Threat(((Threat)lowerOrEq));
            rightPartition.indexStart = myCellBlock.indexEnd + 1;

            if( leftPartition.length() < 0 ) {
                lowers[0] = blocks.lower(myCellBlock);
                lower = lowers[0];

                shouldMergeLeft = lower instanceof FreeSegment;
            }
            else {
                lowers[0] = leftPartition;
            }

            if( rightPartition.length() < 0 ) {
                highers[0] = blocks.higher(myCellBlock);
                higher = highers[0];
                shouldMergeRight = higher instanceof FreeSegment;
            }
            else {
                highers[0] = rightPartition;
            }
        }

        // (un)marked unitary cell -> try join adjacent
        if( shouldMergeLeft && shouldMergeRight) {
            // old streaks must be removed
            if( isMark ) {
                threatsToRemove[playerIndex].add(new Threat( (Threat) lower));
                threatsToRemove[playerIndex].add(new Threat( (Threat) higher));
            }

            // start merge operation
            blocks.remove(lowerOrEq);
            blocks.remove(higher);

            lower.merge(lowerOrEq);
            lower.merge(higher);

            lower.updateAdjacent();

            if(isMark) // the streak must be added / updated
                streakBlock = ((Streak) lower);
            // end merge operation

            if( isMark ) {
                threatsToAdd[playerIndex].add(new Threat( (Threat)streakBlock ));
            }
        }
        // (un)marked on the edge -> join edge
        else if( shouldMergeLeft ) {
            // old streak must be removed
            if( isMark ) {
                threatsToRemove[playerIndex].add(new Threat( (Threat) lower));
            }

            // start merge operation
            if( lowerOrEq.length() < 1 )
                blocks.remove(lowerOrEq);
            lower.grow(1);
            lower.updateAdjacent();

            if(isMark) // the streak must be added / updated
                streakBlock = ((Streak) lower);
            // end merge operation

            if( isMark ) {
                threatsToAdd[playerIndex].add(new Threat( (Threat)streakBlock ));
            }
        }
        // (un)marked on the edge -> join edge
        else if( shouldMergeRight ){

            // old streak must be removed
            if( isMark ) {
                threatsToRemove[playerIndex].add(new Threat( (Threat) higher) );
            }

            // start merge operation
            if( lowerOrEq.length() < 1 )
                blocks.remove(lowerOrEq);
            higher.grow(0);
            higher.updateAdjacent();

            // the streak must be added / updated
            if(isMark)
                streakBlock = ((Streak) higher);
            // end merge operation

            if( isMark ) {
                threatsToAdd[playerIndex].add(new Threat( (Threat)streakBlock ));
            }
        }
        // (un)marked on inside the block -> split in 3 blocks
        else {
            // NO old streak that must be removed

            // start merge operation
            blocks.remove(lowerOrEq);

            // link both as lowerOrEq.prev <-> my <-> lowerOrEq.next
            myCellBlock.linkPrev(lowerOrEq.prev); lowerOrEq.linkPrev(null);
            myCellBlock.linkNext(lowerOrEq.next); lowerOrEq.linkNext(null);

            // link both as lowerOrEq.prev <-> (left partition if any) <-> my <-> (right partition if any) <-> lowerOrEq.next
            if( leftPartition.length() >= 0 ) {
                blocks.add(leftPartition);
                myCellBlock.insertPrev(leftPartition);
            }
            if( rightPartition.length() >= 0 ) {
                blocks.add(rightPartition);
                myCellBlock.insertNext(rightPartition);
            }

            myCellBlock.indexStart = column; myCellBlock.indexEnd = column;

            blocks.add(myCellBlock);
            // case Threat -> will update the threat
            // case free -> will update adjacent threat, than will tell adjacent to update their respective sides
            myCellBlock.updateAdjacent();

            if( isMark )
                streakBlock = ((Streak) myCellBlock);
            // end merge operation

            if( isMark ) {
                threatsToAdd[playerIndex].add(new Threat( (Threat)streakBlock ));
            }

        }
        /**
         * myCellBlock.updateAdjacent() triggers updated segments that are O(7) -> O(1)
         * - {@link #addToUpdatePool(Threat, Threat)} is O(1)
         * - {@link #addToUpdatePool(FreeSegment)} is O(1)
         * - {@link Threat#updateScore()} is O(1)
         * A lot of constants but total per segment update is O(1)
         * A lot of constants but myCellBlock.updateAdjacent() total is O(1)
         */
        // Now update block on edges that can be owned by opponent or player, because we updated the available free cells

        // creates a mapping of cached blocks
        if( lowers[0] != null && lowers[1] == null ) lowers[1] = blocks.lower(lowers[0]);
        if( highers[0] != null && highers[1] == null ) highers[1] = blocks.higher(highers[0]);


        Threat[] toAdd, toRemove;
        for (int p = 0; p < 2; p++) {
            // Now update the threats, by replacing old threats
            if(isMark) {
                toRemove = !threatsToRemove[p].isEmpty() ? threatsToRemove[p].toArray(new Threat[0]) : null;
                toAdd = !threatsToAdd[p].isEmpty() ? threatsToAdd[p].toArray(new Threat[0]) : null;
                // Threats to add/remove (includes update) by current player
                // O( log round ) in theory, O( round ) by api
                playerThreatsOnDirection[p][directionType].push(row, toRemove, toAdd );
            }
            else {
                // O( log round ) in theory, O( round ) by api
                PriorityThreatsTracker.HistoryItem oldData = playerThreatsOnDirection[p][directionType].pop(row);
                toAdd = oldData != null ? oldData.removed : null;
                toRemove = oldData != null ? oldData.added : null;
            }

            if( toRemove != null ){
                for ( Threat threat : toRemove ) {
                    int movesLeft = threat.getMovesLeftCount(); // O(1)
                    if(movesLeft < K && isCandidate(threat) )
                        playerMovesLeftsCount[ p ][ directionType ][ movesLeft ] -= 1;
                }
            }
            if( toAdd != null ) {
                for (Threat threat : toAdd) {
                    int movesLeft = threat.getMovesLeftCount(); // O(1)
                    if (movesLeft < K && isCandidate(threat) )
                        playerMovesLeftsCount[ p ][ directionType ][ movesLeft ] += 1;
                }
            }
        }

        // Debug.println(" After Move on dir " + directionType + " " + isMark + " : " + (shouldMergeLeft) + " " + (shouldMergeRight) + "\n" + blocks.toString());

        return (Threat) streakBlock;
    }

    /**
     * @PreCondition oldThreat must have same color
     * @PostCondition add the old thread to the removes pool and the new threat to the adds pool of respective player owner
     * @implNote Cost <ul>
     *      <li>Time: <code>O( 1 )</code></li>
     * </ul>
     * @param oldThreat
     * @param newThreat
     */
    public void addToUpdatePool(Threat oldThreat, Threat newThreat) {
        if( oldThreat.getColor() != newThreat.getColor() ) return;

        int ownerPlayerIndex = getPlayerIndex(oldThreat.getColor());
        threatsToRemove[ownerPlayerIndex].add(oldThreat);
        threatsToAdd[ownerPlayerIndex].add(newThreat);
    }

    /**
     * @param freeBlock
     * @implNote Cost <ul>
     *      <li>Time: <code>O( 1 )</code></li>
     * </ul>
     */
    public void addToUpdatePool(FreeSegment freeBlock) {
        int[] movesLeft = { K, K};
        int[] coords;
        boolean isMark = workingCellInfo.isMark;
        // free cell columns to update
        final int[] columns = { freeBlock.indexStart, freeBlock.indexEnd};
        final int[] fixRemoveColumns = { workingCellInfo.directionTypeCoords[1] - 1, workingCellInfo.directionTypeCoords[1] + 1};
        int row = workingCellInfo.directionTypeCoords[0];
        int directionType = workingCellInfo.directionType;

        // boolean is1Cell = freeBlock.length() == 0;

        int playerIndex;
        for (int side = 0; side < 2; side++) {
            Segment adj = freeBlock.getLinkOnSide(side, 1); // O(1)

            /**
             * this handle on unmark the following (and its specular) issue:
             *      # # X X # #     ( 0 3 X X 3 0 )
             * ->   # # # X # #     ( 0 3 4 X 4 0 )
             *  so doesn't update an old adjacent free cell
             *  Solution: reset the moves left count of adjacent cells to the unmarked one
             */
            if( !isMark
                    // && isVectorInBounds( new int[]{row, fixRemoveColumns[side]}, directionType ) // not needed as free blocks respect this condition
                    && freeBlock.contains(workingCellInfo.directionTypeCoords[1])
                    && freeBlock.contains(fixRemoveColumns[side]) ) {
                playerIndex = getPlayerIndex(workingCellInfo.color);
                coords = directionTypeCoordsToMatrixCoords( row, fixRemoveColumns[side], directionType );
                MNKCell cell = new MNKCell(coords[0], coords[1]); // or a reference just to make it to update later
                freeToUpdate.add(cell); // O(1)
                setMovesLeftAt(playerIndex, directionType, coords[0], coords[1], K); // O(1)
                setMovePriority(playerIndex, directionType, coords[0], coords[1], 0 ); // O(1)
            }

            if( adj instanceof Threat) {
                Threat threat = (Threat) adj;
                movesLeft[ side ] = threat.getMovesLeftOnSide(1-side); // O(1)
                playerIndex = getPlayerIndex(threat.getColor());
                coords = directionTypeCoordsToMatrixCoords( row, columns[side], directionType );

                int newScore = threat.getScoreOnSide(1-side);
                int newMovesLeft = movesLeft[ side ];
/*
                if( is1Cell ) {
                    if( side == 0 ) { // init on first side
                        setMovePriority(playerIndex, directionType, coords[0], coords[1], 0 );
                    }
                    else {
                        newScore = Math.max(newScore, getMovePriority(playerIndex, directionType, coords[0], coords[1]));
                    }
                }
 */
                MNKCell cell = new MNKCell(coords[0], coords[1]); // or a reference just to make it to update later
                freeToUpdate.add(cell); // O(1)
                setMovesLeftAt(playerIndex, directionType, coords[0], coords[1], newMovesLeft); // O(1)
                setMovePriority(playerIndex, directionType, coords[0], coords[1], newScore ); // O(1)
            }
        }


    }

    /**
     * Update the free cells that needs to be updated into {@link #freePriorityQueue} based on max priority by each player
     * Keep traced the max priority of either players.
     * Keep traced the min moves left of either players, from each updated free cell.
     * @implNote Cost <ul>
     *      <li>Time: <code>max{ T({@link PriorityQueue#remove}, N*M), T({@link PriorityQueue#add}, N*M) }</code></li>
     * </ul>
     * @see #updateBlockAndAdjacentOnDirection
     * @see #addToUpdatePool(FreeSegment)
     */
    public void flushUpdatePool() {
        int min, max, maxS;
        // freeToUpdate.size is O(c) because is a constant count of cells for each directions
        // (but cells are unique since it's a Set)
        for( MNKCell cell : freeToUpdate ) {
            min = K;
            max = 0;
            for (int playerIndex = 0; playerIndex < 2; playerIndex++) {
                for (int directionType : Utils.DIRECTIONS ) {
                    min = Math.min(min, playerMovesLeft[playerIndex][directionType][ cell.i ][ cell.j ]);
                    max = Math.max(max, freeCellsPriorities[playerIndex][directionType][ cell.i ][ cell.j ]);
                }
            }
            minPlayerMovesLeftCache[ cell.i ][ cell.j ] = min;
            freeCellsPrioritiesCache[ cell.i ][ cell.j ] = max;
            // update cell priority by using available API
            freePriorityQueue.remove(cell); // in theory O(log N*M), as java api O(N*M)
            freePriorityQueue.add(cell); // in theory O(log N*M)
        }
        freeToUpdate.clear();
    }

    /**
     *
     * @param threatCandidate
     * @return true if the streak can be completed or not
     */
    @Override
    public boolean isCandidate( ThreatInfo threatCandidate ) {

        if( !(threatCandidate instanceof Threat)) return false;
        Threat threat = (Threat) threatCandidate;

        /**
         * {@link #updateBlockAndAdjacentOnDirection(int, int, int, int, MNKCellState, boolean)}  }
         * {@link Threat#updateScore()}  }
         */
        return threat.getScore() > 0;
    }

    /**
     * Callback about a scan of threat near the source that has been computed on a direction
     * @param result
     * @param directionType
     * @param source
     * @param isMark
     * @param playerIndex
     */
    public void onScanCallback(Threat result, int directionType, MNKCell source, boolean isMark, int playerIndex) {};

    public int[] matrixCoordsToDirectionTypeCoords(int i, int j, int directionType) {
        final int[] coordinates = {0, 0};
        Vectors.vectorCopy(coordinates, coordinatesMap[ directionType ][ i ][ j ] );
        return coordinates;
    }
    public int[] directionTypeCoordsToMatrixCoords(int r, int c, int directionType) {
        final int[] coordinates = {0, 0};
        Vectors.vectorCopy(coordinates, invCoordinatesMap[ directionType ][ r ][ c ] );
        return coordinates;
    }

    /**
     * Assign and Index for player based on color
     * @param color
     * @return
     */
    protected int getPlayerIndex(MNKCellState color) {
        return Utils.getPlayerIndex(color);
    }

    /**
     * compare 2 threats and return the compare result of {@link Comparator#compare(Object, Object)}
     * but in descending order based on threat's score
     * @param o1
     * @param o2
     * @return
     */
    public int compare(Threat o1, Threat o2) {
        return Integer.compare(o2.getScore(), o1.getScore());
    }

public class Threat extends Streak implements ThreatInfo, SideThreatInfo {
    final static int LEFT = 0;
    final static int RIGHT = 1;

    // score assigned to this streak, the higher its value, the closer it will be for win
    int score;
    int leftOnTotal = K;
    int[] leftOnSide = {K, K};
    int[] scoreOnSide = {0, 0};

    public Threat(Streak streak) {
        super(streak.indexStart, streak.indexEnd, streak.color);
    }

    public Threat(Threat threat) {
        super( threat.indexStart, threat.indexEnd, threat.color);
        for (int side = 0; side < 2; side++) {
            this.scoreOnSide[side] = threat.scoreOnSide[side];
        }
        score = threat.score;
        leftOnTotal = threat.leftOnTotal;
    }

    public int getMovesLeftOnSide(int side) {
        return leftOnSide[side];
    }

    public int getMovesLeftCount() {
        return leftOnTotal;
    }

    public int getScore() {
        return score;
    }

    public int getScoreOnSide( int side ) {
        return scoreOnSide[ side ];
    }

    /**
     * Update Threat score based on cached scan values after a {@link #updateAdjacent()} operation
     * @implNote Cost <ul>
     *      <li>Time: <code>O( constant ) = O( 1 )</code></li>
     *      <li>Space: <code>O( constant ) = O( 1 )</code></li>
     * </ul>
     */
    void updateScore() {
        int streakCount = getStreakCount();
        int free = 0, other = 0, otherFree = 0;
        int freeOnTotal = getAdjacentFreeCount();
        // final int[] leftOnSide = {K, K};
        leftOnTotal = Math.max(0, K - streakCount);
        // ways count to counter a streak on a side
        final int[] waysForOpponentToCounterMe = {0, 0};
        final int[] breadthRequiredForWinOnSide = {0, 0};
        final int[] canWinFactor = {0, 0};
        int canCompleteStreakFactor = 0;
        int[] bonusScore = { 0, 0 }; // bonus score per side
        int[] scenarios = { 0, 0 }; // scenario per side

        for (int side = 0; side < 2; side++) {
            free = getFreeOnSide(side);
            // gradually check if can win due to a side properties[breadth], increasing by breadth each check
            for (int breadth = 1; breadth <= 3 && canWinFactor[ side ] < 1; breadth++) {
                switch (breadth){
                    case 1: // win range is <= breadth 1
                        if( streakCount + free >= K
                        || streakCount + freeOnTotal >= K
                        ) {
                            canCompleteStreakFactor = 1;
                            leftOnSide[ side ] = Math.max(0, K - streakCount);
                            leftOnTotal = Math.min( leftOnTotal, leftOnSide[ side ] );
                            // risky scenario
                            if( streakCount + free >= K ) {
                                breadthRequiredForWinOnSide[side] = 1;
                                waysForOpponentToCounterMe[side] = leftOnSide[ side ];
                            }
                            // if both side = 1 then the opponent moves required are 2, so it's a better scenario
                            // because need an opponent mark on both side
                            // if( streakCount + freeOnTotal >= K ) {

                        }
                        break;
                    case 2: // win range is <= breadth 2
                        other = getOtherMarkedOnSide(side);
                        if( (streakCount + free + other >= K) ) {
                            canCompleteStreakFactor = 1;
                            breadthRequiredForWinOnSide[side] = 2;
                            leftOnSide[ side ] = free;
                            // risky scenario
                            leftOnTotal = Math.min( leftOnTotal, leftOnSide[ side ] );
                            // opponent just need to mark an adjacent free
                            waysForOpponentToCounterMe[side] = leftOnSide[ side ];
                        }
                        break;
                    case 3: // win range is <= breadth 3
                        otherFree = getOtherFreeOnSide(side);
                        if( (streakCount + free + other + otherFree >= K) ) {
                            canCompleteStreakFactor = 1;
                            breadthRequiredForWinOnSide[side] = 3;
                            leftOnSide[ side ] = free + Math.max(0, K - (streakCount + free + other) );
                            leftOnTotal = Math.min( leftOnTotal, leftOnSide[ side ] );
                            // very risky scenario
                            // opponent just need to mark an adjacent or other free to counter
                            waysForOpponentToCounterMe[side] = leftOnSide[ side ];
                        }
                        break;
                }
                canWinFactor[ side ] = breadthRequiredForWinOnSide[ side ] > 0 ? 1 : 0;
            }
        }

        score = 0;
        // add score based on the streak scenario per side
        for (int side = 0; side < 2; side++) {
            scoreOnSide[ side ] = 0;
            // scenario 0;: win
            if (leftOnTotal <= 0 ) {
                scenarios[side] = 0;
            }
            // scenario 1: 1 move left in any breadth
            else if (leftOnSide[side] == 1 ) {
                scenarios[side] = 1;
            }
            // scenario 2: 2 moves left and breadth[side] == 1
            //      can complete streak using adjacent free and can be countered in at least 2 moves on worst case
            //      if player X is in this scenario with K = 4: F F X X F or  F X X F F
            //      and player X go to: F X X X F
            //      then the opponent O can't counter this scenario, and player X wins after the O turn
            else if( breadthRequiredForWinOnSide[side] == 1 && leftOnTotal == 2 && getFreeOnSide(side) >= 2 && getFreeOnSide(1 - side) >= 1 ){
                scenarios[side] = 2;
                waysForOpponentToCounterMe[side] = 0;
                waysForOpponentToCounterMe[1-side] = 0;
            }
            // scenario 3: some moves left and can be counters in some ways
            else {
                scenarios[side] = 3;
            }

            scoreOnSide[side] += canCompleteStreakFactor * streakCount;
            bonusScore[ side ] = canWinFactor[side] * (leftOnSide[side] < bonusScoreOnMovesLeft.length
                    ? bonusScoreOnMovesLeft[leftOnSide[side]] : 0);

            // Debug.println("debug streak side " + side + " of streak: " + this + "\n can win: " + canWinFactor[side] + "\n" + getFreeOnSide(side) + " - " + getOtherMarkedOnSide(side) + " - " + getOtherFreeOnSide(side) );
            // Debug.println("bonus score: \n scenario:" + scenarios[side] + "\nbonus:" + bonusScore[side]);
            switch (scenarios[side]) {
                case 0:
                    scoreOnSide[side] = Integer.MAX_VALUE / 2;
                    break;
                case 1:
                case 2:
                    scoreOnSide[side] += bonusScore[ side ];
                    break;
                default:
                    scoreOnSide[side] += bonusScore[ side ] / (1 + waysForOpponentToCounterMe[side]);
                    break;
            }

            score += scoreOnSide[ side ];
        }

    }
    /**
     * Update data from adjacent link
     * @PostCondition call {@link #addToUpdatePool(Threat, Threat)} passing self clones as parameters before and after update
     * @cost.time {@link #updateScore()}
     * @param adj
     */
    @Override
    public void onLinkUpdate(Segment adj, int breadth) {
        if( breadth < 0) return;

        int side = -1;

        if( adj == prev ){
            side = 0; // left
        }
        else if( adj == next ) {
            side = 1; // right
        }

        Threat oldSegment = new Threat(this);
        // iterate over side and update data
        int oldScore = getScore();

        updateScore();
        int newScore = getScore();
        if( newScore != oldScore )
            addToUpdatePool(oldSegment, new Threat(this) );

        super.onLinkUpdate(adj, breadth);
    }

    /**
     * update self data and score and call {@link super#updateAdjacent(int)}
     */
    @Override
    public void updateAdjacent() {
        updateScore();

        super.updateAdjacent(3);
    }

    @Override
    public int[] getLocation() {
        return new int[]{ indexStart, indexEnd };
    }

    @Override
    public MNKCellState getColor() {
        return super.color;
    }

    public int getStreakCount() {
        return 1+length();
    }

    @Override
    public int getAdjacentFreeCount() {
        return getFreeOnSide(0) + getFreeOnSide(1);
    }

    @Override
    public int getOtherMarkedCount() {
        return getOtherMarkedOnSide(0) + getOtherMarkedOnSide(1);
    }

    @Override
    public int getOtherFreeCount() {
        return getOtherFreeOnSide(0) + getOtherFreeOnSide(1);
    }

    @Override
    public int getFreeOnSide(int side) {
        int count = 0;
        Segment base = side == 0 ? prev : next;
        if( base instanceof FreeSegment ) {
            count = 1+base.length();
        }
        return count;
    }

    @Override
    public int getOtherMarkedOnSide(int side) {
        int count = 0;
        Segment base = side == 0 ? prev : next;;

        for (int i = 0; i < 2; i++) {
            if( i == 0 && base instanceof FreeSegment ) {
                base = side == 0 ? base.prev : base.next;
            }
            else if( i == 1 && base instanceof Streak && ((Streak) base).color == getColor() ) {
                count = 1+base.length();
            }
            else {
                break;
            }
        }
        return count;
    }

    @Override
    public int getOtherFreeOnSide(int side) {
        int count = 0;
        Segment base = side == 0 ? prev : next;

        for (int i = 0; i < 3; i++) {
            if( (i == 0 && base instanceof FreeSegment)
                    || (i == 1 && base instanceof Streak && ((Streak) base).color == getColor() ) ) {
                base = side == 0 ? base.prev : base.next;
            }
            else if (i == 2 && base instanceof FreeSegment){
                count = 1+base.length();
            }
            else {
                break;
            }
        }
        return count;
    }

    @Override
    public String toString() {
        return "ThreatT{" +
                super.toString() +
                ", score=" + score +
                '}';
    }
}

/**
 * Class as kind of PriorityBucketsHeapStack, combine multiple data structure properties.
 * Allow to get a "best item" as "item with most priority value through all Priority Queues using only push and pop operations.
 *
 * Buckets of Priority queues because multiple index can be associated to groups of items
 * An Heap because can add, remove and update items in O(log d) time (each) providing also an index of the bucket on push
 *  -  This allow to access to the most important item in O(1)
 * A stack because it behaves like a stack:  each push can be undone with a pop
 *  - This allow tracking of items insert / deletion order
 *  - This allow tracking the best bucket's index in O(1) ( so the priority queue with the best item)
 */
public class PriorityThreatsTracker {

    class HistoryItem {
        // counter used to specify how many round this item has been unchanged (no update operation occurred)
        int unchangedRoundsCounter;
        int row;
        int oldBestRow;
        int newBestRow;
        Threat[] added;
        Threat[] removed;

        @Override
        public String toString() {
            return "HistoryItem{" +
                    "row=" + row +
                    ", oldBestRow=" + oldBestRow +
                    ", newBestRow=" + newBestRow +
                    ", added=" + Arrays.toString(added) +
                    ", removed=" + Arrays.toString(removed) +
                    '}';
        }
    }

    protected Stack<HistoryItem> historyOnRow;
    protected PriorityQueue<Threat>[] rowsOFPQ;
    protected Comparator<Threat> threatTComparator;

    public PriorityThreatsTracker(RowOfBlocks[] rowsReference, Comparator<Threat> threatTComparator) {
        int rowCount = rowsReference.length;
        this.threatTComparator = threatTComparator;
        this.rowsOFPQ = new PriorityQueue[rowCount];
        // On worst case the amount of space occupied by the player in a row is hals of row size
        // and that happens when all columns are owned in alternate way by player, opponent, player, opponent, etc ...
        // in this case there can be at most (rowSize + 1/2 columns owned by each player per row
        for (int i = 0; i < rowCount; i++) {
            this.rowsOFPQ[i] = new PriorityQueue<>((1 + rowsReference[i].rowSize) / 2 , threatTComparator);
        }
        this.historyOnRow = new Stack<>();
    }

    /**
     * Peek the greatest threat, or null if doesn't exists
     * @implNote Cost Time: <code>O(1)</code>
     * @return
     */
    public Threat peek() {
        if( historyOnRow.isEmpty() ) return null;
        return rowsOFPQ[historyOnRow.peek().newBestRow].peek();
    }

    /**
     * remove items and add new ones to the priority queue.
     * @implNote Cost Time: <code>O( log(player marked cell count)) = O( round )</code>
     * @param row
     * @param threatsToRemove
     * @param threatsToAdd
     * @return
     */
    public boolean push(int row, Threat[] threatsToRemove, Threat[] threatsToAdd ) {

        Stack<HistoryItem> history = historyOnRow;
        PriorityQueue<Threat> pq = rowsOFPQ[row];
        boolean isUnchanged = (threatsToAdd == null || threatsToAdd.length < 1 ) && (threatsToRemove == null || threatsToRemove.length < 1);
        boolean resultR = true, resultA = true;

        if( !(history.isEmpty() && isUnchanged) ) {
            HistoryItem item;

            if( isUnchanged ) {
                // reuse last item
                item = history.peek();
                item.unchangedRoundsCounter++;
            }
            else {
                item = new HistoryItem();
                item.added = threatsToAdd;
                item.removed = threatsToRemove;
                if(item.removed != null && item.removed.length > 0 )
                    resultR = pq.removeAll(Set.of(item.removed)); // O( log size ) in theory, O( size ) by api
                if(item.added != null && item.added.length > 0 )
                    resultA = pq.addAll(Set.of(item.added));  // O( log size )

                if (history.isEmpty()) {
                    item.oldBestRow = -1;
                    item.newBestRow = row;
                }
                else {
                    int oldBestRow = history.peek().newBestRow;
                    int comp = threatTComparator.compare(pq.peek(), rowsOFPQ[oldBestRow].peek());
                    item.newBestRow = comp >= 0 ? row : oldBestRow;
                    item.oldBestRow = oldBestRow;
                }

                history.push(item);
            }
        }

        return resultR && resultA;
    }

    /**
     * Restore priority queue's order on a row, by removing the last added items, and re-adding removed items
     * @implNote Cost Time: <code>O( log(player marked cell count)) = O( round )</code>
     * @param row
     * @return old record of popped items (can be null)
     */
    public HistoryItem pop(int row) {
        Stack<HistoryItem> history = historyOnRow;
        PriorityQueue<Threat> pq = rowsOFPQ[row];
        HistoryItem item = null;
        if( !history.isEmpty() ) {
            item = history.peek();
            item.unchangedRoundsCounter--;
            item = item.unchangedRoundsCounter < 0 ? history.pop() : null;
        }

        boolean resultR = true, resultA = true;

        if( item != null ) {
            if (item.added != null && item.added.length > 0)
                resultR = pq.removeAll(Set.of(item.added)); // O( log size ) in theory, O( size ) by api
            if (item.removed != null && item.removed.length > 0)
                resultA = pq.addAll(Set.of(item.removed));  // O( log size )
        }
        // item == null case happen only when on first time adding item on a row, but there are no changes, so no rows are added
        // so when push has been called with no addition / deletion

        return item;
    }

    @Override
    public String toString() {
        return "PriorityThreatsTracker{" +
                "historyOnRow=" + historyOnRow +
                ",\n rowsOFPQ=" + Arrays.toString(rowsOFPQ) +
                '}';
    }
}

public class RowOfBlocks extends TreeSet<Segment> {
    int rowSize;

    public RowOfBlocks(Comparator<Segment> comparator) {
        super(comparator);
    }

    public RowOfBlocks(Comparator<Segment> comparator, int size) {
        this(comparator);
        this.rowSize = size;
        this.add(new FreeSegment(0, this.rowSize-1));
    }
}

public class Streak extends Segment {
    public MNKCellState color;

    public Streak(int indexStart, int indexEnd, MNKCellState color) {
        super(indexStart, indexEnd);
        this.color = color;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if( !(o instanceof Streak) ) return false;
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

    @Override
    protected Streak clone() {
        return new Streak(indexStart, indexEnd, color);
    }
}

/**
 * Class that works together with {@link Threat}
 * This class notify adjacent {@link Threat} when the adjacent on opposite side updates
 */
public class FreeSegment extends Segment {

    public FreeSegment(int indexStart, int indexEnd) {
        super(indexStart, indexEnd);
    }

    @Override
    public void updateAdjacent() {
        super.updateAdjacent(3);
        addToUpdatePool(this);
    }

    /**
     * Update data from adjacent link
     * @param adj
     */
    @Override
    public void onLinkUpdate(Segment adj, int breadth) {
       if( breadth < 0) return;

        int side = -1;

        if( adj == prev ){
            side = 0; // left
        }
        else if( adj == next ) {
            side = 1; // right
        }

        super.onLinkUpdate(adj, breadth);

        if( adj instanceof Threat) {
            addToUpdatePool(this);
        }
    }

    @Override
    public String toString() {
        return "FreeSegment{" +
                "" + indexStart +
                ", "+ indexEnd +
                '}';
    }
}
}