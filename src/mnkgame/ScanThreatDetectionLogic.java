package mnkgame;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Stack;

public abstract class ScanThreatDetectionLogic implements ThreatDetectionLogic<Threat>, Comparator<Threat> {

    int M, N, K;

    // threat history stack for each player and for each direction
    protected Stack<Threat>[][] bestThreatHistory;

    // current streak power of each marked cell, first dimension is the player index
    protected Utils.Weight[][][][] streakWeights;

/*
    // legacy and unused stuff, here for future re-evaluation
    protected UnionFindUndo<MNKCell>[][] comboMap;
    protected boolean[][] isCellAddedToCombo;
 */

    public ScanThreatDetectionLogic() {

    }

    @Override
    public void init(int M, int N, int K) {
        this.M = M;
        this.N = N;
        this.K = K;

        // comboMap = new UnionFindUndo[2][Utils.DIRECTIONS.length];
        bestThreatHistory = new Stack[2][Utils.DIRECTIONS.length];
        for (int playerIndex = 0; playerIndex < 2; playerIndex++) {
            for ( int directionType : Utils.DIRECTIONS ) {
                // comboMap[playerIndex][directionType] = new UnionFindUndo<>();
                bestThreatHistory[playerIndex][directionType] = new Stack<>();
            }
        }
    }

    @Override
    public Threat getBestThreat(int playerIndex, int directionType) {
        Stack<Threat> playerHistory = bestThreatHistory[playerIndex][directionType];
        return !playerHistory.isEmpty() ? playerHistory.peek() : null;
    }

    @Override
    public void mark(MNKBoard tree, MNKCell marked, int markingPlayer, int depth) {

        // Link the adjacent cells
/*
        UnionFindUndo<MNKCell>[] comboMap = getPlayerCombos(markingPlayer);
        UnionFindUndo<MNKCell> combosInDirection;

        // first add this cell to all directions combos
        if( !isCellAddedToCombo[marked.i][marked.j] ) {
            for ( int directionType : Utils.DIRECTIONS ) {
                combosInDirection = comboMap[directionType];
                combosInDirection.addElement(marked);
            }
            isCellAddedToCombo[marked.i][marked.j] = true;
        }
*/

        for ( Map.Entry<Integer, List<MNKCell>> adjEntry : getBoard().adj(marked).entrySet() ) {
            int directionType = adjEntry.getKey();

/*
            combosInDirection = comboMap[directionType];
            for ( MNKCell adjCell : adjEntry.getValue() ) {
                // if state is same of marked cell, then it has already been added to UnionFind structure for sure
                if( adjCell.state == markState ) {
                    combosInDirection.union(marked, adjCell);
                }
            }
*/
            // evaluate if this streak is a threat
            ScanResult result = getThreatInDirection(marked, directionType);
            updateStreaksOnDirection(directionType, marked.i, marked.j, 1, markingPlayer, result );

            this.onScanCallback(result, directionType, marked, true, markingPlayer );

            evaluateThreat(result.threat, directionType);
        }
    }

    @Override
    public void unMark(MNKBoard tree, MNKCell marked, int unMarkingPlayer, int depth) {
        // Unlink the adjacent cells
/*
        UnionFindUndo<MNKCell>[] comboMap = getPlayerCombos(unMarkingPlayer);
        UnionFindUndo<MNKCell> combosInDirection;
*/
        for ( Map.Entry<Integer, List<MNKCell>> adjEntry : getBoard().adj(marked).entrySet() ) {
            int directionType = adjEntry.getKey();

//          combosInDirection = comboMap[directionType];
            // evaluate if this streak is a threat
            ScanResult result = getThreatInDirection(marked, directionType);
            updateStreaksOnDirection(directionType, marked.i, marked.j, -1, unMarkingPlayer, result );

            this.onScanCallback(result, directionType, marked, false, unMarkingPlayer );

            removeThreatOnUnmark(marked, directionType);

/*
            for ( MNKCell adjCell : adjEntry.getValue() ) {
                // if state is same of marked cell, then it has already been added to UnionFind structure for sure
                if( adjCell.state == markState ) {
                    combosInDirection.undo();
                }
            }

            // and after remove this cell from all directions combos
            combosInDirection.undo();
 */
        }
//        isCellAddedToCombo[marked.i][marked.j] = false;
    }

    protected void evaluateThreat(Threat threat, int directionType) {
        MNKCell source = threat.move;
        int playerIndex = Utils.getPlayerIndex(source.state);
        if( isCandidate(threat) ) {
            // this is a doable Threat for a player

            Stack<Threat> threatsHistory = bestThreatHistory[playerIndex][directionType];
            if( !threatsHistory.isEmpty() ) {
                int compare = compare(threatsHistory.peek(), threat);
                if( compare < 0) {
                    threatsHistory.push(threat);
                }
                else if( compare == 0 ){
                   /* boolean isSameSet = bestThreat[playerIndex][directionType] != null
                            && combosInDirection.inSameSet(marked, bestThreat[playerIndex][directionType].move );
                    if( !isSameSet ) {
                        // player have more threats of same weight,
                    }*/
                }
            }
            else {
                threatsHistory.push(threat);
            }
        }
    }

    protected Threat removeThreatOnUnmark(MNKCell source, int directionType) {
        int playerIndex = Utils.getPlayerIndex(source.state);
        Stack<Threat> threatsHistory = bestThreatHistory[playerIndex][directionType];
        if( !threatsHistory.isEmpty() ) {
            Threat threat = threatsHistory.peek();
            if( threat.round > getSimulatedRound() ) // Objects.equals(threat.move, source)
                return threatsHistory.pop();
        }
        return null;
    }

    /**
     * Scan threat info on both sides of a specified direction as BFS ( So there isn't a preferred side) <br>
     * Require O(K) to scan K cells. Can require less than K, if direction is interrupted by opponent cells on both sides
     * or reach a bound
     * @cost.time O(K)
     * @param source
     * @param directionType
     * @return
     */
    public ScanResult getThreatInDirection(MNKCell source, int directionType) {

        // if this streak can't increase, then ignore this, otherwise count it as threat
        int playerIndex = Utils.getPlayerIndex(source.state);
        final int[][] directionsOffsets = Utils.DIRECTIONS_OFFSETS[directionType];
        int[] markedCountOnSide = new int[directionsOffsets.length];
        int[] otherMarkedCountOnSide = new int[directionsOffsets.length];
        int[] freeCountOnSide = new int[directionsOffsets.length];
        // if true then iteration should stop on that side
        final boolean[] canIncreaseStreak = new boolean[2];

        Utils.Weight[][] directionStreakWeights = getStreakWeights(playerIndex, directionType);
        int[] direction;
        int[] distance = new int[2];

        // int i = currentBoard.K-1;
        int[] leftIterations = new int[directionsOffsets.length];

        // indexes for each side
        final int[][] index = new int[directionsOffsets.length][2];


        int availableSlots = 0;
        int streakCount = 0;
        if( source.state != MNKCellState.FREE )
            streakCount++;
        int totalFreeMovesAvailableInWinRange = 0;

        int iterationsPerSide = (K-1) / directionsOffsets.length;
        int rest = (K-1) % directionsOffsets.length;

        for (int side = 0; side < directionsOffsets.length; side++) {
            direction = directionsOffsets[side];
            index[side][0] = source.i;
            index[side][1] = source.j;

            // start from next
            Vectors.vectorSum(index[side], direction);
            leftIterations[side] = iterationsPerSide;

            canIncreaseStreak[side] = true;

            // distribute rest on other side
            if( rest > 0) {
                leftIterations[side]++;
                rest--;
            }
        }


        // iterate over the direction and check how many move are left for win
        // if can't iterate over a specific point because cell's state has beet already set by opponent,
        // then interrupt and iterate other side

        // iterate over direction, alternating direction sides
        // to distribute equally marked and free cells on both sides
        int side = 0;

        while ( (leftIterations[0] > 0 || leftIterations[1] > 0)
                && (canIncreaseStreak[0] || canIncreaseStreak[1]) ) {

            // Debug.println("dir " + directionType + " side " + side + " " + Arrays.toString(index[side]));
            direction = directionsOffsets[side];
            canIncreaseStreak[side] = canIncreaseStreak[side] && getBoard().isVectorInBounds(index[side]);

            if( leftIterations[side] > 0 && canIncreaseStreak[side] ) {
                MNKCellState state = getBoard().cellState( index[side][0], index[side][1] );
                // Debug.println("is " + state);
                if (state == source.state) {
                    // in current streak
                    if( freeCountOnSide[side] <= 0) {
                        streakCount++;
                        markedCountOnSide[side]++;

                        // ignore this iteration in iterations count, as we require the streak count
                        leftIterations[side]++;
                    }
                    // In other streak
                    // reach a cell owned by same player,
                    // so this case can happens only after counting free cells
                    else if( otherMarkedCountOnSide[side] <= 0){
                        otherMarkedCountOnSide[side] = directionStreakWeights[index[side][0]][index[side][1]].value;
                        if( leftIterations[side] > otherMarkedCountOnSide[side] ) {
                            leftIterations[side] -= otherMarkedCountOnSide[side]-1;
                            // skip the other marked streak
                            Vectors.vectorScale(Vectors.vectorCopy(distance, direction), otherMarkedCountOnSide[side]-1);
                            Vectors.vectorSum(index[side], distance);

                            // canIncreaseStreak[side] = false;
                        }
                        // else stop if other streak is too large
                    }
                    // else iterate on other marked streak, to see what happens next
                }
                else if (state == MNKCellState.FREE) {
                    totalFreeMovesAvailableInWinRange++;
                    if( otherMarkedCountOnSide[side] <= 0) {
                        availableSlots++;
                        freeCountOnSide[side]++;
                    }
                }
                // stop, we met an opponent streak
                else {
                    canIncreaseStreak[side] = false;
                    // ignore this iteration in iterations count
                    leftIterations[side]++;
                }
                // i--;
                leftIterations[side]--;
            }

            if( canIncreaseStreak[side] ) {
                Vectors.vectorSum(index[side], direction);
            }
            else {
                rest += leftIterations[side];
                leftIterations[side] = 0;
            }

            // alternate side, but will exit when all sides can't be iterated over again
            int attempts = directionsOffsets.length;
            do {
                side++;
                if( side >= directionsOffsets.length ) side = 0;

                // if a rest has been left from a last terminated side, then add to this new side
                if( canIncreaseStreak[side] && rest > 0 ) {
                    leftIterations[side] += rest;
                    rest = 0;
                }
                attempts--;
            }
            while( !( leftIterations[side] > 0 && canIncreaseStreak[side]) && attempts > 0);

        }

        Threat threat = new Threat();
        threat.streakCount = streakCount;
        threat.nearAvailableMoves = availableSlots;
        threat.totalAvailableMovesOnWinRange = totalFreeMovesAvailableInWinRange;
        threat.round = getSimulatedRound();
        threat.move = source;
        threat.otherClosestStreakCount = 0;
        threat.availableMovesFromOtherClosestStreak = 0;

        int closestSide = -1;
        if( otherMarkedCountOnSide[0] > 0 && otherMarkedCountOnSide[1] > 0) {
            closestSide = freeCountOnSide[0] < freeCountOnSide[1]
                    ? 0
                    : 1;
        }
        else if( otherMarkedCountOnSide[0] > 0 || otherMarkedCountOnSide[1] > 0) {
            closestSide = otherMarkedCountOnSide[0] > 0
                    ? 0
                    : 1;
        }

        if( closestSide >= 0) {
            threat.otherClosestStreakCount = otherMarkedCountOnSide[closestSide];
            threat.availableMovesFromOtherClosestStreak = freeCountOnSide[closestSide];
        }

        ScanResult result = new ScanResult();
        result.threat = threat;
        result.directionType = directionType;
        result.onSideFree = freeCountOnSide;
        result.onSideMarked = markedCountOnSide;
        result.onSideMarkedNear = otherMarkedCountOnSide;

        return result;
    }

    /**
     * Updates the streak weight given to each free cell connected to marked cells
     * based on the count of cells in a row, for each directions, which are in the same state as the supplied cell.
     * So each free cell that follow or precedes a row of in state S, has its weight set to the count of marked cells
     * in a row in the same state s, including cell(i,j) in the count
     * @cost.time O(K)
     * @param i
     * @param j
     * @param mod weight modifier used to scale the count applied to extremes cells, use > 0 after marking, < 0 after unmarking
     */
    protected void updateStreaksOnDirection(int directionType, int i, int j, int mod, int playerIndex, ScanResult scanResult ) {

        int[]   source = { 0, 0 },
                direction = {0, 0},
                distance = { 0, 0 },
                index = { 0, 0 };
        int countInDirection = 0, countInOppositeDirection = 0;

        int[][] direction_offsets = Utils.DIRECTIONS_OFFSETS[directionType];

        // add/remove streak for self
        addStreakWeight(playerIndex, directionType, i, j, mod);

        // update weight on both sides of this direction
        for (int side = 0; side < direction_offsets.length; side++) {
            int oppositeSide = (direction_offsets.length - 1) - side;

            source[0] = i; source[1] = j;

            // flip direction based on type
            Vectors.vectorCopy(direction, direction_offsets[side]);

            countInDirection = scanResult.onSideMarked[side];
            countInOppositeDirection = scanResult.onSideMarked[oppositeSide];

            // n = -1 + countBefore + countAfter; // source is counted twice
            // weights[ i ][ j ] is always >= n ( > because can be increased by other sides )
            // go to prev of first
            // and then go to next of last

            // distance = direction * (countBefore+1) ( + 1 for next cell )
            // Vectors.vectorScale(Vectors.vectorCopy(distance, direction), countInDirection);
            // index = source + distance
            // Vectors.vectorSum(Vectors.vectorCopy(index, source), distance);

            Vectors.vectorCopy(index, source);

            // start from adjacent
            Vectors.vectorSum(index, direction);

            // update streak power on all cells of the streak
            // each cell in a direction will add the streak count of the opposite direction ( (i, j)-th is included into the count)
            // the (i,j)-the cell add the same count-1  (because remove it self) since it's already been added on start of this loop
            addStreakWeight(playerIndex, directionType, i, j, countInOppositeDirection * mod);

            int left = countInDirection;
            while( left > 0 && getBoard().isVectorInBounds(index) ) {
                addStreakWeight(playerIndex, directionType, index[0], index[1], (1+countInOppositeDirection) * mod);
                Vectors.vectorSum(index, direction);
                left--;
            }
        }
    }

/*
    protected UnionFindUndo<MNKCell>[] getPlayerCombos(int playerIndex) {
        return comboMap[playerIndex];
    }
*/
    public Utils.Weight[][] getStreakWeights(int playerIndex, int directionType ) {
        return streakWeights[playerIndex][directionType];
    }

    public void addStreakWeight(int playerIndex, int directionType, int i, int j, int value ) {
        streakWeights[playerIndex][directionType][i][j].value += value;
    }
    public void setStreakWeight(int playerIndex, int directionType, int i, int j, int value ) {
        streakWeights[playerIndex][directionType][i][j].value = value;
    }

    /**
     *
     * @param threat
     * @return true if the streak can be completed or not
     */
    @Override
    public boolean isCandidate( Threat threat ) {
        return threat.streakCount + threat.totalAvailableMovesOnWinRange + threat.otherClosestStreakCount >= K;
    }

    @Override
    public int compare(Threat o1, Threat o2) {
        int comp = 0;

        boolean isCandidate1 = isCandidate(o1);
        boolean isCandidate2 = isCandidate(o2);

        if( isCandidate1 && isCandidate2 ) {
            int moveLeftToWin1 = Math.max(0, K - (o1.streakCount + o1.otherClosestStreakCount));
            int moveLeftToWin2 = Math.max(0, K - (o2.streakCount + o2.otherClosestStreakCount));

            // lesser the moves left are and more dangerous the threat is
            comp = Integer.compareUnsigned(moveLeftToWin2, moveLeftToWin1);
        }
        else if( isCandidate1 || isCandidate2 ) {
            if( isCandidate1 ) comp = 1;
            else comp = -1;
        }

        if( comp == 0 )
            // check which streak is more important
            comp = Integer.compareUnsigned(o1.streakCount, o2.streakCount);

        // have multiple streaks with same score, so check which have more chances to fill the streak
        if( comp == 0 )
            comp = Integer.compareUnsigned(o1.nearAvailableMoves, o2.nearAvailableMoves);

        return comp;
    }

    /**
     * Callback about a scan of threat near the source that has been computed on a direction
     * @param result
     * @param directionType
     * @param source
     * @param isMark
     * @param playerIndex
     */
    public abstract void onScanCallback(ScanResult result, int directionType, MNKCell source, boolean isMark, int playerIndex);

    /**
     * Board getter used to only ready board info
     * @return
     */
    public abstract StatefulBoard getBoard();

    /**
     * get about current round of the board
     * @return
     */
    public abstract int getSimulatedRound();

}
