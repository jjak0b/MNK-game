package mnkgame;

import java.util.*;

public class Scan2ThreatDetectionLogic implements ThreatDetectionLogic<Scan2ThreatDetectionLogic.ThreatT>, Comparator<Scan2ThreatDetectionLogic.ThreatT> {

    protected int M, N, K;
    public int[] bonusScoreOnMovesLeft;

    // these are buckets of Trees, organized to make search fast O(log)
    public RowOfBlocks[][] blocksOnDirection;
    // these are buckets of a kind of modified Priority(Heap / queues) Stack
    public PriorityThreatsTracker[][] playerThreatsOnDirection;
    // coordinate Map from board to row indexes for direction
    public int[][][][] coordinatesMap;

    // pool of threats to remove per player
    protected List<ThreatT>[] threatsToRemove;
    // pool of threats to add per player
    protected List<ThreatT>[] threatsToAdd;

    public RowOfBlocks[] getRowsOfBlocksOnDirection( int directionType) {
        return blocksOnDirection[directionType];
    }

    @Override
    public ThreatT getBestThreat(int playerIndex, int directionType) {
        return playerThreatsOnDirection[playerIndex][directionType].peek();
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

        // On worst case a free block can be split to 3 blocks after a mark/unmark -> so 1 to remove and 3 to add or 3 to remove and 1 to add
        // but near blocks have to update their score, so needs to update their positions on priority queues
        // each block can be near in sequence to a "free" block, "another marked" block, and a "other free" block -> so up to 2 threats per side must be updated
        final int poolSize = (2*2) + 3;
        threatsToRemove = new List[]{ new ArrayList<ThreatT>( poolSize ), new ArrayList<ThreatT>( poolSize) };
        threatsToAdd = new List[]{ new ArrayList<ThreatT>( poolSize ), new ArrayList<ThreatT>( poolSize) };

        bonusScoreOnMovesLeft = new int[]{ 0, K*K*K, K*K };
        playerThreatsOnDirection = new PriorityThreatsTracker[2][Utils.DIRECTIONS.length];

        coordinatesMap = new int[Utils.DIRECTIONS.length][M][N][ 2 ];
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
                        }
                    }
                    break;
                case Utils.DIRECTION_TYPE_VERTICAL:
                    for ( j = 0; j < N; j++) {
                        for ( i = 0; i < M; i++) {
                            coordinatesMap[directionType][i][j][0] = j;
                            coordinatesMap[directionType][i][j][1] = i;
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
                        }
                    }
                    i = M-1; j = 1;
                    for(; j < N; j++) {
                        for (int k = 0; i - k >= 0 && j + k < N; k++) {
                            int r = i - k;
                            int c = j + k;
                            coordinatesMap[directionType][r][c][0] = i + j;
                            coordinatesMap[directionType][r][c][1] = k;
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
                        }
                    }
                    i = 1; j = 0;
                    for(; i < M; i++) {
                        for (int k = 0; i + k < M && j + k < N; k++) {
                            int r = i + k;
                            int c = j + k;
                            coordinatesMap[directionType][r][c][0] = i + (N -1 - j);
                            coordinatesMap[directionType][r][c][1] = k;
                        }
                    }
                    break;
            }
        }
    }

    @Override
    public void mark(MNKBoard tree, MNKCell marked, int markingPlayer, int depth) {
        int playerIndex = getPlayerIndex(marked.state);
        for ( int directionType : Utils.DIRECTIONS ) {
            updateBlockAndAdjacentOnDirection(playerIndex, marked.i, marked.j, directionType, marked.state, true );
        }
    }

    @Override
    public void unMark(MNKBoard tree, MNKCell oldMarked, int unMarkingPlayer, int depth) {
        int playerIndex = getPlayerIndex(oldMarked.state);
        for ( int directionType : Utils.DIRECTIONS ) {
            updateBlockAndAdjacentOnDirection(playerIndex, oldMarked.i, oldMarked.j, directionType, MNKCellState.FREE, false );
        }
    }

    public void updateBlockAndAdjacentOnDirection( int playerIndex, int i, int j, int directionType, MNKCellState color, boolean isMark) {

        int[] coords = matrixCoordsToDirectionTypeCoords(i, j, directionType);
        int row = coords[0]; int column = coords[1];

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

        if( isMark ) {
            myCellBlock = new ThreatT(new Streak(column, column, color));
            lowerOrEq = blocks.floor(myCellBlock);

            assert lowerOrEq != null;
            assert lowerOrEq.contains(column);
            assert !(lowerOrEq instanceof ThreatT);

            leftPartition = new FreeSegment(lowerOrEq.indexStart, myCellBlock.indexStart-1);
            rightPartition = new FreeSegment(myCellBlock.indexEnd+1, lowerOrEq.indexEnd);

            if( leftPartition.length() < 0 ) {
                lowers[0] = lowerOrEq.prev; // blocks.lower(myCellBlock);
                lower = lowers[0];
                shouldMergeLeft = lower instanceof ThreatT && ((ThreatT) lower).getColor() == color;
            }
            else {
                lowers[0] = leftPartition;
            }

            if( rightPartition.length() < 0 ) {
                highers[0] = lowerOrEq.next; // blocks.higher(myCellBlock);
                higher = highers[0];
                shouldMergeRight = higher instanceof ThreatT && ((ThreatT) higher).getColor() == color;
            }
            else {
                highers[0] = rightPartition;
            }
        }
        else {
            myCellBlock = new FreeSegment(column, column);
            lowerOrEq = blocks.floor(myCellBlock);

            assert lowerOrEq != null;
            assert lowerOrEq.contains(column);
            assert (lowerOrEq instanceof ThreatT);
            color = ((Streak) lowerOrEq).color;

            leftPartition = new ThreatT(((ThreatT)lowerOrEq));
            leftPartition.indexEnd = myCellBlock.indexStart - 1;

            rightPartition = new ThreatT(((ThreatT)lowerOrEq));
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
                threatsToRemove[playerIndex].add(new ThreatT( (ThreatT) lower));
                threatsToRemove[playerIndex].add(new ThreatT( (ThreatT) higher));
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
                threatsToAdd[playerIndex].add(new ThreatT( (ThreatT)streakBlock ));
            }
        }
        // (un)marked on the edge -> join edge
        else if( shouldMergeLeft ) {
            // old streak must be removed
            if( isMark ) {
                threatsToRemove[playerIndex].add(new ThreatT( (ThreatT) lower));
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
                threatsToAdd[playerIndex].add(new ThreatT( (ThreatT)streakBlock ));
            }
        }
        // (un)marked on the edge -> join edge
        else if( shouldMergeRight ){

            // old streak must be removed
            if( isMark ) {
                threatsToRemove[playerIndex].add(new ThreatT( (ThreatT) higher) );
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
                threatsToAdd[playerIndex].add(new ThreatT( (ThreatT)streakBlock ));
            }
        }
        // (un)marked on inside the block -> split in 3 blocks
        else {
            // NO old streak that must be removed

            // start merge operation
            blocks.remove(lowerOrEq);

            myCellBlock.insertPrev(lowerOrEq.prev);
            myCellBlock.insertNext(lowerOrEq.next);

            if( leftPartition.length() >= 0 ) {
                blocks.add(leftPartition);
                myCellBlock.insertPrev(leftPartition);
            }
            if( rightPartition.length() >= 0 ) {
                blocks.add(rightPartition);
                myCellBlock.insertNext(rightPartition);
            }

            myCellBlock.indexStart = column; myCellBlock.indexEnd = column;

            // case Threat -> will update the threat
            // case free -> will update adjacent threat, than will tell adjacent to update their respective sides
            myCellBlock.updateAdjacent();

            blocks.add(myCellBlock);

            if( isMark )
                streakBlock = ((Streak) myCellBlock);
            // end merge operation

            if( isMark ) {
                threatsToAdd[playerIndex].add(new ThreatT( (ThreatT)streakBlock ));
            }

        }
        // Now update block on edges that can be owned by opponent or player, because we updated the available free cells

        // creates a mapping of cached blocks
        if( lowers[0] != null && lowers[1] == null ) lowers[1] = blocks.lower(lowers[0]);
        if( highers[0] != null && highers[1] == null ) highers[1] = blocks.higher(highers[0]);


        for (int p = 0; p < 2; p++) {
            // Now update the threats, by replacing old threats
            if(isMark) {
                // Threats to add/remove (includes update) by current player
                playerThreatsOnDirection[p][directionType].push(
                        row,
                        !threatsToRemove[p].isEmpty() ? threatsToRemove[p].toArray(new ThreatT[0]) : null,
                        !threatsToAdd[p].isEmpty() ? threatsToAdd[p].toArray(new ThreatT[0]) : null
                );
            }
            else {
                playerThreatsOnDirection[p][directionType].pop(row);
            }
        }

        // Debug.println(" After Move on dir " + directionType + " " + isMark + " : " + (shouldMergeLeft) + " " + (shouldMergeRight) + "\n" + blocks.toString());
    }

    /**
     * @PreCondition oldThreat must have same color
     * @PostCondition add the old thread to the removes pool and the new threat to the adds pool of respective player owner
     * @param oldThreat
     * @param newThreat
     */
    public void addToUpdatePool(ThreatT oldThreat, ThreatT newThreat) {
        if( oldThreat.getColor() != newThreat.getColor() ) return;

        int ownerPlayerIndex = getPlayerIndex(oldThreat.getColor());
        threatsToRemove[ownerPlayerIndex].add(oldThreat);
        threatsToAdd[ownerPlayerIndex].add(newThreat);
    }

    /**
     *
     * @param threatCandidate
     * @return true if the streak can be completed or not
     */
    @Override
    public boolean isCandidate( ThreatInfo threatCandidate ) {

        if( !(threatCandidate instanceof ThreatT )) return false;
        ThreatT threat = (ThreatT) threatCandidate;

        int streakCount = threat.getStreakCount();
        int free = 0, other = 0, otherFree = 0;
        int freeOnTotal = 0;

        for (int side = 0; side < 2; side++) {
            free = threat.getFreeOnSide(side);
            freeOnTotal += free;

            // gradually check if can win due to a side properties[breadth], increasing by breadth each check
            for (int breadth = 1; breadth <= 3; breadth++) {
                switch (breadth){
                    case 1: // win range is <= breadth 1
                        if( (streakCount + free >= K || streakCount + freeOnTotal >= K) )
                            return true;
                        break;
                    case 2: // win range is <= breadth 2
                        other = threat.getOtherMarkedOnSide(side);
                        if( (streakCount + free + other >= K) )
                            return true;
                        break;
                    case 3: // win range is <= breadth 3
                        otherFree = threat.getOtherFreeOnSide(side);
                        if( (streakCount + free + other + otherFree >= K) )
                            return true;
                        break;
                }
            }
        }
        return false;
    }

    public int[] matrixCoordsToDirectionTypeCoords(int i, int j, int directionType) {
        final int[] coordinates = {0, 0};
        Vectors.vectorCopy(coordinates, coordinatesMap[ directionType ][ i ][ j ] );
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
    public int compare(ThreatT o1, ThreatT o2) {
        return Integer.compare(o2.getScore(), o1.getScore());
    }

class ThreatT extends Streak implements ThreatInfo, SideThreatInfo {
    final static int LEFT = 0;
    final static int RIGHT = 1;

    // score assigned to this streak, the higher its value, the closer it will be for win
    int score;

    // free segments on left and right
    int[] adjacentFree = {0, 0};
    int[] otherMarkedNearFree = {0, 0};
    int[] freeNearOtherMarked = {0, 0};

    public ThreatT(Streak streak) {
        super(streak.indexStart, streak.indexEnd, streak.color);
    }

    public ThreatT(ThreatT threat) {
        super( threat.indexStart, threat.indexEnd, threat.color);
        for (int side = 0; side < 2; side++) {
            this.adjacentFree[side] = threat.adjacentFree[side];
            this.otherMarkedNearFree[side] = threat.otherMarkedNearFree[side];
            this.freeNearOtherMarked[side] = threat.freeNearOtherMarked[side];
        }
        score = threat.score;
    }

    public int getScore() {
        return score;
    }

    /**
     * Update Threat score based on cached scan values after a {@link #updateAdjacent()} operation
     * @cost.time O(2)
     */
    void updateScore() {
        int streakCount = getStreakCount();
        int free = 0, other = 0, otherFree = 0;
        int freeOnTotal = getAdjacentFreeCount();
        final int[] leftOnSide = {K, K};
        int leftOnTotal = K - streakCount;
        // ways count to counter a streak on a side
        final int[] waysForOpponentToCounterMe = {0, 0};
        final int[] breadthRequiredForWinOnSide = {0, 0};
        final int[] canWinFactor = {0, 0};
        int[] bonusScore = { 0, 0 }; // bonus score per side
        int[] scenarios = { 0, 0 }; // scenario per side

        for (int side = 0; side < 2; side++) {
            free = getFreeOnSide(side);
            // gradually check if can win due to a side properties[breadth], increasing by breadth each check
            for (int breadth = 1; breadth <= 3 && canWinFactor[ side ] < 1; breadth++) {
                switch (breadth){
                    case 1: // win range is <= breadth 1
                        if( streakCount + free >= K
                        // || streakCount + freeOnTotal >= K
                        ) {
                            leftOnSide[ side ] = K - streakCount;
                            // risky scenario
                            if( streakCount + free >= K ) {
                                breadthRequiredForWinOnSide[side] = 1;
                                waysForOpponentToCounterMe[side] = 1;
                            }
                            // if both side = 1 then the opponent moves required are 2, so it's a better scenario
                            // because need an opponent mark on both side
                            // if( streakCount + freeOnTotal >= K ) {

                        }
                        break;
                    case 2: // win range is <= breadth 2
                        other = getOtherMarkedOnSide(side);
                        if( (streakCount + free + other >= K) ) {
                            breadthRequiredForWinOnSide[side] = 2;
                            leftOnSide[ side ] = free;
                            // risky scenario

                            // opponent just need to mark an adjacent free
                            waysForOpponentToCounterMe[side] = 1;
                        }
                        break;
                    case 3: // win range is <= breadth 3
                        otherFree = getOtherFreeOnSide(side);
                        if( (streakCount + free + other + otherFree >= K) ) {
                            breadthRequiredForWinOnSide[side] = 3;
                            leftOnSide[ side ] = free + Math.max(0, K - (streakCount + free + other) );

                            // very risky scenario
                            // opponent just need to mark an adjacent or other free to counter
                            waysForOpponentToCounterMe[side] = 2;
                        }
                        break;
                }
                canWinFactor[ side ] = breadthRequiredForWinOnSide[ side ] > 0 ? 1 : 0;
            }
        }

        score = 0;
        // add score based on the streak scenario per side
        for (int side = 0; side < 2; side++) {
            // scenario 1: 1 move left in any breadth
            if (leftOnSide[side] == 1 ) {
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

            score += canWinFactor[side] * streakCount;
            bonusScore[ side ] = canWinFactor[side] * (leftOnSide[side] < bonusScoreOnMovesLeft.length
                    ? bonusScoreOnMovesLeft[leftOnSide[side]] : 0);

            // Debug.println("debug streak side " + side + " of streak: " + this + "\n can win: " + canWinFactor[side] + "\n" + getFreeOnSide(side) + " - " + getOtherMarkedOnSide(side) + " - " + getOtherFreeOnSide(side) );
            // Debug.println("bonus score: \n scenario:" + scenarios[side] + "\nbonus:" + bonusScore[side]);
            switch (scenarios[side]) {
                case 1:
                case 2:
                    score += bonusScore[ side ];
                    break;
                default:
                    score += bonusScore[ side ] / (1 + waysForOpponentToCounterMe[side]);
                    break;
            }
        }

    }
    /**
     * Update data from adjacent link
     * @PostCondition call {@link #addToUpdatePool(ThreatT, ThreatT)} passing self clones as parameters before and after update
     * @cost.time {@link #updateAdjacentDataOnSide }
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

        ThreatT oldSegment = new ThreatT(this);
        // iterate over side and update data
        if( side >= 0)
            updateAdjacentDataOnSide(side);
        updateScore();

        addToUpdatePool(oldSegment, new ThreatT(this) );

        super.onLinkUpdate(adj, breadth);
    }

    /**
     * update self data and score and call {@link super#updateAdjacent(int)}
     */
    @Override
    public void updateAdjacent() {
        ThreatT oldSegment = new ThreatT(this);

        for (int side = 0; side < 2; side++)
            updateAdjacentDataOnSide(side);
        updateScore();

        addToUpdatePool(oldSegment, new ThreatT(this) );

        super.updateAdjacent(3);
    }

    /**
     * Iterate over n=3 segment on a side to update data
     * @cost.time O(3)
     * @param side 0 for prev, else for next
     */
    protected void updateAdjacentDataOnSide(int side) {
        ThreatT oldTHis = new ThreatT(this);
        Segment it = this;
        for (int i = 0; i < 3; i++) {
            if( it == null ) break;
            it = side == 0 ? it.prev : it.next;
            if( i == 0 ) {
                if(it != null && !(it instanceof Streak) )
                    adjacentFree[side] = 1 + it.length();
                else
                    adjacentFree[side] = 0;
            }
            else if( i == 1 && adjacentFree[side] > 0) {
                if(it != null && it instanceof Streak && ((Streak) it).color == color )
                    otherMarkedNearFree[side] = 1 + it.length();
                else
                    otherMarkedNearFree[side] = 0;
            }
            else if( i == 2 && otherMarkedNearFree[side] > 0 ) {
                if(it != null && !(it instanceof Streak) )
                    freeNearOtherMarked[side] = 1 + it.length();
                else
                    freeNearOtherMarked[side] = 0;
            }
        }
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
                super.toString() + "\n" +
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
        int row;
        int oldBestRow;
        int newBestRow;
        ThreatT[] added;
        ThreatT[] removed;

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
    protected PriorityQueue<ThreatT>[] rowsOFPQ;
    protected Comparator<ThreatT> threatTComparator;

    public PriorityThreatsTracker(RowOfBlocks[] rowsReference, Comparator<ThreatT> threatTComparator) {
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
     * @return
     */
    public ThreatT peek() {
        if( historyOnRow.isEmpty() ) return null;
        return rowsOFPQ[historyOnRow.peek().newBestRow].peek();
    }

    /**
     * remove items and add new ones to the priority queue.
     * @cost.time O(log d)
     * @param row
     * @param threatsToRemove
     * @param threatsToAdd
     * @return
     */
    public boolean push(int row, ThreatT[] threatsToRemove, ThreatT[] threatsToAdd ) {

        Stack<HistoryItem> history = historyOnRow;
        PriorityQueue<ThreatT> pq = rowsOFPQ[row];
        boolean resultR = true, resultA = true;

        HistoryItem item = new HistoryItem();
        item.added = threatsToAdd != null && threatsToAdd.length > 0 ? threatsToAdd : null;
        item.removed = threatsToRemove != null && threatsToRemove.length > 0 ? threatsToRemove : null;

        if(item.removed != null && item.removed.length > 0 )
            resultR = pq.removeAll(Set.of(item.removed));
        if(item.added != null && item.added.length > 0 )
            resultA = pq.addAll(Set.of(item.added));

        if( history.isEmpty() ) {
            item.oldBestRow = -1;
            item.newBestRow = row;
        }
        else {
            int oldBestRow = history.peek().newBestRow;
            int comp = threatTComparator.compare(rowsOFPQ[row].peek(), rowsOFPQ[oldBestRow].peek());
            item.newBestRow = comp >= 0 ? row : oldBestRow;
            item.oldBestRow = oldBestRow;
        }

        history.push(item);

        return resultR && resultA;
    }

    /**
     * Restore priority queue's order on a row, by removing the last added items, and re-adding removed items
     * @cost.time O(log d)
     * @param row
     * @return
     */
    public boolean pop(int row) {
        Stack<HistoryItem> history = historyOnRow;
        PriorityQueue<ThreatT> pq = rowsOFPQ[row];

        HistoryItem item = history.pop();

        boolean resultR = true, resultA = true;

        if(item.added != null && item.added.length > 0 )
            resultR = pq.removeAll(Set.of(item.added));
        if(item.removed != null && item.removed.length > 0 )
            resultA = pq.addAll(Set.of(item.removed));

        return resultR && resultA;
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
 * Class that works together with {@link ThreatT}
 * This class notify adjacent {@link ThreatT} when the adjacent on opposite side updates
 */
public class FreeSegment extends Segment {

    public FreeSegment(int indexStart, int indexEnd) {
        super(indexStart, indexEnd);
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