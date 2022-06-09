package mnkgame;

import java.util.*;

public class Scan2ThreatDetectionLogic implements ThreatDetectionLogic<Scan2ThreatDetectionLogic.ThreatT>, Comparator<Scan2ThreatDetectionLogic.ThreatT> {

    protected int M, N, K;

    // these are buckets of Trees, organized to make search fast O(log)
    public RowOfBlocks[][] blocksOnDirection;
    // these are buckets of a kind of modified Priority(Heap / queues) Stack
    public PriorityThreatsTracker[][] playerThreatsOnDirection;

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



        playerThreatsOnDirection = new PriorityThreatsTracker[2][Utils.DIRECTIONS.length];

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
            for (int playerIndex = 0; playerIndex < 2; playerIndex++) {
                playerThreatsOnDirection[playerIndex][directionType] = new PriorityThreatsTracker(rowsCount, this);
            }

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
    public void mark(MNKBoard tree, MNKCell marked, int markingPlayer, int depth) {
        for ( int directionType : Utils.DIRECTIONS ) {
            updateBlockAndAdjacentOnDirection(markingPlayer,  marked.i, marked.j, directionType, marked.state, true );
        }
    }

    @Override
    public void unMark(MNKBoard tree, MNKCell oldMarked, int unMarkingPlayer, int depth) {
        for ( int directionType : Utils.DIRECTIONS ) {
            updateBlockAndAdjacentOnDirection(unMarkingPlayer, oldMarked.i, oldMarked.j, directionType, MNKCellState.FREE, false );
        }
    }

    protected void fillBlocksInSequence(Segment[] highersOrLowers, RowOfBlocks blocks, boolean isHigher) {
        for (int d = 1; d < highersOrLowers.length; d++) {
            if( highersOrLowers[d-1] != null ){
                if( highersOrLowers[d] == null )
                    highersOrLowers[d] = isHigher ? blocks.higher(highersOrLowers[d-1]) : blocks.lower(highersOrLowers[d-1]);
            }
            else {
                return;
            }
        }
    }

    public void updateBlockAndAdjacentOnDirection( int playerIndex, int i, int j, int directionType, MNKCellState color, boolean isMark) {
        int opponentIndex = 1-playerIndex;
        // HashMap<Segment, Segment> lowerThan = new HashMap<>();
        // HashMap<Segment, Segment> higherThan = new HashMap<>();


        int[] coords = matrixCoordsToDirectionTypeCoords(i, j, directionType);
        int row = coords[0]; int column = coords[1];

        List<ThreatT>[] threatsToRemove = new List[2];
        List<ThreatT>[] threatsToAdd = new List[2];
        for (int p = 0; p < 2; p++) {
            threatsToRemove[p] = new ArrayList<>(2);
            threatsToAdd[p] = new ArrayList<>(2);
        }

        RowOfBlocks blocks = blocksOnDirection[directionType][row];
        // Debug.println(" Before Move "+ (i + "," + j + "-> " + row + "," + column) + " on dir " + directionType + " " + isMark + " : \n" + blocks.toString());
        Segment leftPartition, rightPartition;
        Segment lower = null, lowerOrEq, higher = null;
        // relative to marked block
        Segment[] lowers = new Segment[3];
        Segment[] highers = new Segment[3];
        // relative to untouched block but needs to be updated
        Segment[] lowersFromTop = new Segment[3];
        Segment[] highersFromBottom = new Segment[3];

        // which side must be merged
        boolean shouldMergeLeft = false;
        boolean shouldMergeRight = false;

        Segment[] opponentOrPlayerBlockToUpdate = new Segment[2];
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
                shouldMergeLeft = lower instanceof ThreatT && ((Streak) lower).color == color;
            }
            else {
                lowers[0] = leftPartition;
            }

            if( rightPartition.length() < 0 ) {
                highers[0] = lowerOrEq.next; // blocks.higher(myCellBlock);
                higher = highers[0];
                shouldMergeRight = higher instanceof ThreatT && ((Streak) higher).color == color;
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

                shouldMergeLeft = lower != null && !(lower instanceof Streak);
            }
            else {
                lowers[0] = leftPartition;
            }

            if( rightPartition.length() < 0 ) {
                highers[0] = blocks.higher(myCellBlock);
                higher = highers[0];
                shouldMergeRight = higher != null && !(higher instanceof Streak);
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
                threatsToRemove[playerIndex].add(new ThreatT(((Streak) lower).clone()));
            }

            // start merge operation
            lower.grow(1);
            if( lowerOrEq.length() < 0 )
                blocks.remove(lowerOrEq);
            lower.updateAdjacent();

            if(isMark) // the streak must be added / updated
                streakBlock = ((Streak) lower);
            // end merge operation

        }
        // (un)marked on the edge -> join edge
        else if( shouldMergeRight ){

            // old streak must be removed
            if( isMark ) {
                threatsToRemove[playerIndex].add(new ThreatT(((Streak) higher).clone()));
            }

            // start merge operation
            higher.grow(0);
            if( lowerOrEq.length() < 0 )
                blocks.remove(lowerOrEq);
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
            if( lowerOrEq.prev != null )
                lowerOrEq.prev.updateAdjacent();
            if( lowerOrEq.next != null )
                lowerOrEq.next.updateAdjacent();

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


        // Now update the threats, by replacing old threats
        if(isMark) {
            if( !shouldMergeLeft || !shouldMergeRight ) {
                Segment[] sequenceCache;
                for (int side = 0; side < 2; side++) {
                    sequenceCache = side == 0 ? highersFromBottom : lowersFromTop;

                    if (opponentOrPlayerBlockToUpdate[side] instanceof ThreatT) {

                        int targetPlayer = ((ThreatT) opponentOrPlayerBlockToUpdate[side]).color == color ? playerIndex : opponentIndex;
                        // add a copy of the removed item
                        threatsToRemove[targetPlayer].add(new ThreatT((ThreatT) opponentOrPlayerBlockToUpdate[side]));

                        // this will update the reference in RowOfBlocks because opponentOrPlayerBlockToUpdate[side] is a reference
                        updateThreatOnSide((ThreatT) opponentOrPlayerBlockToUpdate[side], blocks, sequenceCache, 1 - side);

                        // add a copy of the new updated reference
                        threatsToAdd[targetPlayer].add(new ThreatT((ThreatT) opponentOrPlayerBlockToUpdate[side]));
                    }
                }
            }
            playerThreatsOnDirection[playerIndex][directionType].push(
                    row,
                    threatsToRemove[playerIndex].toArray(new ThreatT[0]),
                    threatsToAdd[playerIndex].toArray(new ThreatT[0])
            );

            if( (opponentOrPlayerBlockToUpdate[0] instanceof ThreatT && ((ThreatT) opponentOrPlayerBlockToUpdate[0]).color != color)
                || (opponentOrPlayerBlockToUpdate[1] instanceof ThreatT  && ((ThreatT) opponentOrPlayerBlockToUpdate[1]).color != color) ) {
                playerThreatsOnDirection[opponentIndex][directionType].push(
                        row,
                        threatsToRemove[opponentIndex].toArray(new ThreatT[0]),
                        threatsToAdd[opponentIndex].toArray(new ThreatT[0])
                );
            }
        }
        else {
            playerThreatsOnDirection[playerIndex][directionType].pop(row);

            if( (opponentOrPlayerBlockToUpdate[0] instanceof ThreatT && ((ThreatT) opponentOrPlayerBlockToUpdate[0]).color != color)
                || (opponentOrPlayerBlockToUpdate[1] instanceof ThreatT  && ((ThreatT) opponentOrPlayerBlockToUpdate[1]).color != color) ) {
                playerThreatsOnDirection[opponentIndex][directionType].pop(row);
            }
        }

        // Debug.println(" After Move on dir " + directionType + " " + isMark + " : " + (shouldMergeLeft) + " " + (shouldMergeRight) + "\n" + blocks.toString());
    }

    /**
     * Update threat's data for both sides
     * @param streak segment of a streak
     * @param lowers buffer of 3 segments in lower positions than streak
     * @param highers buffer of 3 segments in higher positions than streak
     * @return
     */
    protected ThreatT createThreat(Streak streak, Segment[] lowers, Segment[] highers ) {
        ThreatT threat = new ThreatT(streak);
        updateThreat(threat, lowers, highers);
        return threat;
    }

    /**
     * Update threat's data for a specified side
     *
     * @param threat the block reference to start from
     * @param blocks structure to get the higher or lower if not defined in sequenceCache
     * @param sequenceCache buffer of next 3 segments in sequence, other wise
     * @param side 0 (lower) or 1 (higher)
     */
    protected void updateThreatOnSide(ThreatT threat, RowOfBlocks blocks, Segment[] sequenceCache, int side) {
        Segment block = threat;
        boolean shouldReset = false;
        for (int d = 0; d < 3; d++) {
            if( shouldReset )
                block = null;
            else if (sequenceCache != null && sequenceCache[d] != null) {
                block = sequenceCache[d];
            }
            else {
                block = side == 0 ? blocks.lower(block) : blocks.higher(block);
                if (sequenceCache != null)
                    sequenceCache[d] = block;
            }

            switch (d) {
                case 0:
                    shouldReset = shouldReset || !( block != null && !(block instanceof Streak) );
                    threat.adjacentFree[side] = shouldReset ? 0 : 1 + block.length();
                    break;
                case 1:
                    shouldReset = shouldReset || !( block != null && block instanceof Streak && ((Streak)block).color == threat.color );
                    threat.otherMarkedNearFree[side] = shouldReset ? 0 : 1 + block.length();
                    break;
                case 2:
                    shouldReset = shouldReset || !( block != null && !(block instanceof Streak));
                    threat.freeNearOtherMarked[side] = shouldReset ? 0 : 1 + block.length();
                    break;
            }
        }
    }

    protected void updateThreat(ThreatT threat, RowOfBlocks blocks, Segment[] lowers, Segment[] highers ) {
        for (int side = 0; side < 2; side++) {
            if( side == 0 )
                updateThreatOnSide(threat, blocks, lowers, side);
            else
                updateThreatOnSide(threat, blocks, highers, side);
        }
    }

    protected void updateThreat(ThreatT threat, Segment[] lowers, Segment[] highers) {
        for (int side = 0; side < 2; side++) {
            Segment[] base = side == 0 ? lowers : highers;
            if( base == null ) continue;

            if( base[0] != null && !(base[0] instanceof Streak) ) {
                threat.adjacentFree[0] = 1 + base[0].length();
                if( base[1] != null && base[1] instanceof Streak && ((Streak) base[1]).color == threat.color ) {
                    threat.otherMarkedNearFree[0] = 1 + base[1].length();
                    if( base[2] != null && !(base[2] instanceof Streak) ) {
                        threat.freeNearOtherMarked[0] = 1 + base[2].length();
                    }
                }
            }
        }
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


    public int compare(ThreatT o1, ThreatT o2) {
        return 0;
    }

class ThreatT extends Streak implements ThreatInfo, SideThreatInfo {
    // this is the breadth of this streak allow to win for
    int score;
    int breadthRequiredForWin;

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
    }

    public int getScore() {
        return score;
    }

    void updateScore() {
        int streakCount = (1+length());
        int free = 0, other = 0, otherFree = 0;
        int freeOnTotal = 0;
        final int[] leftOnSide = {0, 0};
        final int[] opponentMovesRequiredToCounterThis = {0, 0};
        final int[] waysForOpponentToCounterMe = {0, 0};
        final int[] breadthRequiredForWinOnSide = {0, 0};

        for (int side = 0; side < 2; side++) {
            free = getFreeOnSide(side);
            freeOnTotal += free;

            // gradually check if can win due to a side properties[breadth], increasing by breadth each check
            for (int breadth = 1; breadth <= 3; breadth++) {
                switch (breadth){
                    case 1: // win range is <= breadth 1
                        if( (streakCount + free >= K || streakCount + freeOnTotal >= K) ) {
                            breadthRequiredForWinOnSide[side] = 1;

                            leftOnSide[ side ] = K - streakCount;
                            // risky scenario
                            if( streakCount + free >= K ) {
                                opponentMovesRequiredToCounterThis[side] = 1; // a side
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
                            leftOnSide[ side ] = K - streakCount;
                            // risky scenario

                            // opponent just need to mark an adjacent free
                            waysForOpponentToCounterMe[side] = 1;
                            opponentMovesRequiredToCounterThis[side] = 1;
                        }
                        break;
                    case 3: // win range is <= breadth 3
                        otherFree = getOtherFreeOnSide(side);
                        if( (streakCount + free + other + otherFree >= K) ) {
                            breadthRequiredForWinOnSide[side] = 3;
                            leftOnSide[ side ] = K - streakCount;

                            // very risky scenario
                            // opponent just need to mark an adjacent or other free to counter
                            waysForOpponentToCounterMe[side] = 2;
                            opponentMovesRequiredToCounterThis[side] = 1;
                        }
                        break;
                }
            }
        }

    }
    /**
     * Update data from adjacent link
     * @cost.time {@link #updateDataOnSide }
     * @param adj
     */
    @Override
    public void onLinkUpdate(Segment adj) {
        int side = -1;

        if( adj == prev ){
            side = 0; // left
        }
        else if( adj == next ) {
            side = 1; // right
        }

        // iterate over side and update data
        if( side >= 0)
            updateDataOnSide(side);
        updateScore();
    }

    /**
     * Iterate over n=3 segment on a side to update data
     * @cost.time O(3)
     * @param side 0 for prev, else for next
     */
    protected void updateDataOnSide( int side) {

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
class PriorityThreatsTracker {

    class HistoryItem {
        int row;
        int oldBestRow;
        int newBestRow;
        ThreatT[] added;
        ThreatT[] removed;
    }

    protected Stack<HistoryItem> historyOnRow;
    protected PriorityQueue<ThreatT>[] rowsOFPQ;
    protected Comparator<ThreatT> threatTComparator;

    public PriorityThreatsTracker(int rowCount, Comparator<ThreatT> threatTComparator) {
        this.threatTComparator = threatTComparator;
        this.rowsOFPQ = new PriorityQueue[rowCount];
        for (int i = 0; i < rowCount; i++) {
            this.rowsOFPQ[i] = new PriorityQueue<>(rowCount, threatTComparator);
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
        boolean result = true;

        HistoryItem item = new HistoryItem();
        item.added = threatsToAdd;
        item.removed = threatsToRemove;

        if(item.removed != null && item.removed.length > 0 )
            result = result && pq.removeAll(Set.of(item.removed));
        if(item.added != null && item.added.length > 0 )
            result = result && pq.addAll(Set.of(item.added));

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

        return result;
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

        boolean result = true;

        if(item.added != null && item.added.length > 0 )
            result = result && pq.removeAll(Set.of(item.added));
        if(item.removed != null && item.removed.length > 0 )
            result = result && pq.addAll(Set.of(item.removed));

        return result;
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
    public void onLinkUpdate(Segment adj) {
        if( adj instanceof ThreatT ) {
            if( adj == next ) {
                if( prev != null )
                    prev.onLinkUpdate(this);
            }
            else if( adj == prev ) {
                if( next != null )
                    next.onLinkUpdate(this);
            }
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