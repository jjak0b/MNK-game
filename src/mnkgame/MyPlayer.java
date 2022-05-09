package mnkgame;

import java.util.*;

public class MyPlayer extends AlphaBetaPruningPlayer implements BoardRestorable, Comparator<Threat> {

    protected float estimatedPercentOfTimeRequiredToExit;

    protected StatefulBoard currentBoard;

    // protected UnionFindUndo<MNKCell>[][] comboMap;
    // protected boolean[][] isCellAddedToCombo;

    protected int[][] corners;

    boolean isCurrentBoardLeftInValidState;

    public int[] bonusScoreOnMovesLeft;

    // threat history stack for each player and for each direction
    protected Stack<Threat>[][] bestThreatHistory;

    // players comparators to store and keep sorted player cells ids in candidate order
    protected Comparator<Integer>[] freeCellsIdsComparators;

    // weight, if 0, is cell is not useful, otherwise is useful for a direction = count of directions which cell is useful
    protected Utils.Weight[][][][] usefulness;

    // threat weights for free cells, first dimension is the player index
    protected Utils.Weight[][][] weights;

    // current streak power of each marked cell, first dimension is the player index
    protected Utils.Weight[][][][] streakWeights;

    // cellsIds[ i ] = id
    protected Integer[] cellsIds;

    // cellsIdsPositions[ cellID ] = position = i of cellID = cellsIds[ i ]
    protected Integer[] cellsIdsPositions;
    protected int freeCellsCount;

    protected Integer[] dirtyCellsIds;
    protected int dirtyCellsIndexesCount;
    protected int DIRTY_CELLS_SIZE;

    // debug
    public static final boolean DEBUG_SHOW_STREAKS = Debug.Player.DEBUG_SHOW_STREAKS;
    public static final boolean DEBUG_SHOW_USEFUL = Debug.Player.DEBUG_SHOW_USEFUL;
    public static final boolean DEBUG_SHOW_WEIGHTS = Debug.Player.DEBUG_SHOW_WEIGHTS;
    public static final boolean DEBUG_SHOW_CANDIDATES = Debug.Player.DEBUG_SHOW_CANDIDATES;

    public MyPlayer() {

    }
    /**
     *
     * @param threat
     * @return true if the streak can be completed or not
     */
    public boolean isCandidate( Threat threat ) {
        return threat.streakCount + threat.totalAvailableMovesOnWinRange + threat.otherClosestStreakCount >= currentBoard.K;
    }

    /**
     * Initialize the (M,N,K) Player
     *
     * @param M               Board rows
     * @param N               Board columns
     * @param K               Number of symbols to be aligned (horizontally, vertically, diagonally) for a win
     * @param first           True if it is the first player, False otherwise
     * @param timeout_in_secs Maximum amount of time (in seconds) for selectCell
     */
    public void initPlayer(int M, int N, int K, boolean first, int timeout_in_secs) {
        // New random seed for each game

        super.initPlayer(M, N, K, first, timeout_in_secs);

        bonusScoreOnMovesLeft = new int[]{ 0, K, K*K };

        corners = new int[][]{ {0, 0}, {0, currentBoard.N-1}, {currentBoard.M-1, 0}, {currentBoard.M-1, currentBoard.N-1} };
        maxDepthSearch = 6;
        estimatedPercentOfTimeRequiredToExit = 5f/100f;

        initWeights(M, N);
        initCombo();
        initComparators();
        initCells(M, N, K);

        setInValidState();
    }

    protected void initCells(int M, int N, int K) {
        final int count = M * N;

        DIRTY_CELLS_SIZE = K * Utils.DIRECTIONS.length;
        dirtyCellsIds = new Integer[DIRTY_CELLS_SIZE];
        dirtyCellsIndexesCount = 0;
        cellsIds = new Integer[ count ];
        cellsIdsPositions = new Integer[ count ];
        for (int i = 0; i < count; i++) {
            cellsIds[i] = i;
            cellsIdsPositions[i] = i;
        }
        freeCellsCount = cellsIds.length;
    }

    // Sets to free all board cells
    protected void initWeights(int M, int N) {
        weights = new Utils.Weight[2][M][N];
        usefulness = new Utils.Weight[2][Utils.DIRECTIONS.length][M][N];
        streakWeights = new Utils.Weight[2][Utils.DIRECTIONS.length][M][N];

        for (int p = 0; p < 2; p++) {
            for (int i = 0; i < currentBoard.M; i++) {
                for (int j = 0; j < currentBoard.N; j++) {
                    MNKCell c = new MNKCell(i, j);


                    weights[p][i][j] = new Utils.Weight(c, 0);

                    for (int directionType : Utils.DIRECTIONS ) {
                        usefulness[p][directionType][i][j] = new Utils.Weight(c, 0);
                        streakWeights[p][directionType][i][j] = new Utils.Weight(c, 0);
                    }
                }
            }
        }
    }

    protected void initComparators() {
        freeCellsIdsComparators = new Comparator[2];
        for (int i = 0; i < 2; i++) {
            int index = i;
            // heatmap comparators for descending order
            freeCellsIdsComparators[i] = new Comparator<>() {
                private final int indexPlayer = index;
                private final int indexOpponent = 1 - index;

                Utils.Weight[] wMax = new Utils.Weight[2];

                @Override
                public int compare(Integer id1, Integer id2) {
                    final int[] p1 = currentBoard.getMatrixIndexesFromArrayIndex(id1);
                    final int[] p2 = currentBoard.getMatrixIndexesFromArrayIndex(id2);
                    int diff = 0;

                    for (int j = 0; j < 2; j++) {
                        wMax[j] = null;
                        int[] index;
                        if( j == 0 ) index = p1;
                        else index = p2;

                        for (int d = 0; d < Utils.DIRECTIONS.length; d++) {
                            int directionType = Utils.DIRECTIONS[d];
                            Utils.Weight w;

                            Utils.Weight wP = usefulness[indexPlayer][directionType][ index[0] ][ index[1] ];
                            Utils.Weight wO = usefulness[indexOpponent][directionType][ index[0] ][ index[1] ];

                            int comp = wP.compareTo(wO);

                            if( comp >= 0) w = wP;
                            else w = wO;

                            if( wMax[j] == null || w.compareTo(wMax[j]) > 0) wMax[j] = w;
                        }
                    }

                    diff = wMax[1].compareTo(wMax[0]);

                    // make it stable
                    if (diff == 0) {
                        diff = Integer.compare(id2, id1);
                    }
                    return diff;
                }
            };
        }
    }

    // prioritize cells to pick based on a sort of heatmap
    @Override
    protected void initTrackingBoard(int M, int N, int K) {
        try {
            currentBoard = new StatefulBoard(M, N, K);
            super.currentBoard = currentBoard;
        }
        catch (Throwable e ) {
            Debug.println("Error on init board " + e);
        }
    }

    protected void initCombo() {
        bestThreatHistory = new Stack[2][Utils.DIRECTIONS.length];
        for (int playerIndex = 0; playerIndex < 2; playerIndex++) {
            for ( int directionType : Utils.DIRECTIONS ) {
                bestThreatHistory[playerIndex][directionType] = new Stack<>();
            }
        }
/*
        comboMap = new UnionFindUndo[2][Utils.DIRECTIONS.length];
        for (int playerIndex = 0; playerIndex < 2; playerIndex++) {
            for ( int directionType : Utils.DIRECTIONS ) {
                comboMap[playerIndex][directionType] = new UnionFindUndo<>();
            }
        }
 */
    }

    protected void restoreTrackingBoard(MNKCell[] FC, MNKCell[] MC) {
        // we suppose currentBoard.MC.size() >= MC.length

        // we have to restore last valid state, so without last enemy move and this player's last move
        int countToMark = 2;
        int countMCBeforeInvalid = MC.length - countToMark;
        int countToUnMark = currentBoard.MC.size()-countMCBeforeInvalid;

        // then un-mark all until we reach the old valid state.
        for (int i = 0; i < countToUnMark; i++) {
            unMark(currentBoard, -1);
        }
        // and then mark to current state
        for (int i = 0; i < countToMark; i++) {
            mark(currentBoard, MC[ countMCBeforeInvalid + i ], -1);
        }
    }

    @Override
    public void restore(MNKCell[] FC, MNKCell[] MC) {
        initWeights(currentBoard.M, currentBoard.N);
        initCombo();
        initCells(currentBoard.M, currentBoard.N, currentBoard.K);
        restoreTrackingBoard(FC, MC);
        setInValidState();
    }

    @Override
    public boolean isStateValid() {
        return isCurrentBoardLeftInValidState;
    }

    @Override
    public void setInValidState() {
        isCurrentBoardLeftInValidState = true;
    }

    @Override
    public void invalidateState() {
        isCurrentBoardLeftInValidState = false;
    }

    /*
    protected UnionFindUndo<MNKCell>[] getPlayerCombos(int playerIndex) {
        return comboMap[playerIndex];
    }
*/
    protected MNKCell strategyAsFirst(MNKCell[] FC, MNKCell[] MC) {
//      return FC[rand.nextInt(FC.length)]; // random
        int[] coords = corners[rand.nextInt( corners.length ) ];
        Debug.println( "First Move: Move to a corner");
        coords = corners[ 1 ]; // constant for debug
        return new MNKCell( coords[0], coords[1] );
    }

    protected MNKCell strategyAsSecond(MNKCell[] FC, MNKCell[] MC) {
        for (int i = 0; i < corners.length; i++) {
            if( MC[0].i == corners[i][0] && MC[0].j == corners[i][1] ) {
                Debug.println( "Detected Corner strategy for enemy, use middle position");
                return new MNKCell( (currentBoard.M >> 1), (currentBoard.N >> 1) );
            }
        }
        return null;
    }

    public int getSimulatedRound() {
        return currentBoard.MC.size();
    }

    /**
     * Select a position among those listed in the <code>FC</code> array
     *
     * @param FC Free Cells: array of free cells
     * @param MC Marked Cells: array of already marked cells, ordered with respect
     *           to the game moves (first move is in the first position, etc)
     * @return an element of <code>FC</code>
     */
    @Override
    public MNKCell selectCell(MNKCell[] FC, MNKCell[] MC) {
        MNKCell choice = null;
        if( !isStateValid()) {
            restore(FC, MC);
        }
        else {
            if (MC.length > 0) {
                choice = MC[MC.length - 1]; // Save the last move in the local MNKBoard
                mark(currentBoard, choice, 0);
                ++round;
                choice = null;
            }
        }

        switch ( MC.length ){
            case 0: // move as first
                choice = strategyAsFirst(FC, MC);
                break;
//            case 1: // move as second
//                choice = strategyAsSecond(FC, MC);
//                break;
        }

        if( choice != null ) {
            mark(currentBoard, choice, 0);
            round++;

            if( DEBUG_SHOW_BOARD )
                Debug.println( "after move:\n" + boardToString() );
            return choice;
        }

        long start = System.currentTimeMillis();
        long endTime = start + (long) ( timeout * (99.0/100.0));
        long expectedTimeRequiredToExit = (long) (estimatedPercentOfTimeRequiredToExit * timeout);
        long workTime = start + ( timeout - expectedTimeRequiredToExit );

        if ( DEBUG_SHOW_CANDIDATES )
            Debug.println("Candidates: " + Arrays.toString(getCellCandidates(currentBoard)));
        // set in invalid state, because if running out time, this function may be terminated
        invalidateState();

        // TODO: check with start @ (5, 4 ) in 7 7 4
        // Good: 6 6 4, 7 7 4 -> moveleft = 5
        AlphaBetaOutcome outcome = null;

         outcome = alphaBetaPruning(
                    currentBoard,
                    true,
                    STANDARD_SCORES.get(STATE_LOSE),
                    STANDARD_SCORES.get(STATE_WIN),
                    0,
                    maxDepthSearch,
                    workTime
            );
        long end = System.currentTimeMillis();
        long elapsed = end-start;
        long timeLeft = (endTime-end);

        // we returned so assuming all right
        setInValidState();

        if( DEBUG_SHOW_STATS )
            printStats(outcome, elapsed, timeLeft);

        if( Debug.DEBUG_ENABLED ) {
            if(!isStateValid()){
                restore(FC, MC);
            }
        }

        mark(currentBoard, outcome.move, 0);

        if( DEBUG_SHOW_BOARD )
            Debug.println( "after move:\n" + boardToString() );
        if( Debug.DEBUG_ENABLED && currentBoard.gameState() != MNKGameState.OPEN ){
            Debug.println( "Final board:\n" + boardToString() );
        }

        round++;

        return outcome.move;
    }

    @Override
    protected AlphaBetaOutcome evaluate(MNKBoard board, int depth, boolean isMyTurn) {
        MNKGameState gameState = board.gameState();
        AlphaBetaOutcome outcome = new AlphaBetaOutcome();

        int score = STANDARD_SCORES.get(gameState);

        if( score > 0)
            score -= depth-1;
        else if( score < 0)
            score += depth-1;

/* // keep as reference
        if( gameState == this.STATE_WIN ) {
            // Debug.println( getTabForDepth( depth-1 ) + MY_MARK_STATE +  "-> Win state");
            // score = 1f;
        }
        else if( gameState == this.STATE_LOSE ) {
            // Debug.println( getTabForDepth( depth-1 ) + MY_MARK_STATE +  "-> Lose state");
            // score = -1f;
        }
        else if( gameState == MNKGameState.DRAW ) {
            // Debug.println( getTabForDepth( depth-1 ) + "Draw state");
            // score = 0f;
        }
        else
*/
        if (gameState == MNKGameState.OPEN) { // game is open
            // Debug.println( getTabForDepth( depth-1 ) + "Heuristic state");
            // TODO: here we should do an Heuristic evaluation

            // newStimeScore = scoreWeight * oldScore  + ( 1 - scoreWeight ) * oldStimeScore

            int playerIndex = Utils.getPlayerIndex(MY_MARK_STATE);
            int opponentIndex = 1-playerIndex;

            int playerMax = 0;
            int opponentMax = 0;
            int minPlayerLeft = board.K;
            int minOpponentLeft = board.K;
            int[] waysToBeCountered = {1, 1};

            for ( int directionType : Utils.DIRECTIONS ) {
                Stack<Threat> playerHistory = bestThreatHistory[playerIndex][directionType];
                Stack<Threat> opponentHistory = bestThreatHistory[opponentIndex][directionType];

                Threat bestPlayerThreat = !playerHistory.isEmpty() ? playerHistory.peek() : null;
                Threat bestOpponentThreat = !opponentHistory.isEmpty() ? opponentHistory.peek() : null;

                if( bestPlayerThreat != null) {
                    playerMax = Math.max(playerMax, bestPlayerThreat.streakCount);
                    int old = minPlayerLeft;
                    minPlayerLeft = Math.max(1, currentBoard.K - (bestPlayerThreat.streakCount + bestPlayerThreat.otherClosestStreakCount));

                    if( old > minPlayerLeft ) {

                        // need just one of adjacent
                        if( bestPlayerThreat.streakCount + bestPlayerThreat.nearAvailableMoves >= board.K )
                            waysToBeCountered[playerIndex] = 2;

                        // need a link between 2 streak
                        else if( bestPlayerThreat.streakCount + bestPlayerThreat.otherClosestStreakCount + bestPlayerThreat.availableMovesFromOtherClosestStreak >= board.K)
                            waysToBeCountered[playerIndex] = 1;
                    }
                }

                if( bestOpponentThreat != null) {
                    opponentMax = Math.max(opponentMax, bestOpponentThreat.streakCount);
                    int old = minOpponentLeft;
                    minOpponentLeft = Math.max(1, currentBoard.K - (bestOpponentThreat.streakCount + bestOpponentThreat.otherClosestStreakCount));

                    if( old > minOpponentLeft && minOpponentLeft <= 2) {

                        // need just one of adjacent
                        if( bestOpponentThreat.streakCount + bestOpponentThreat.nearAvailableMoves >= board.K )
                            waysToBeCountered[opponentIndex] = bestOpponentThreat.nearAvailableMoves;

                        // need a link between 2 streaks
                        else if( bestOpponentThreat.streakCount + bestOpponentThreat.otherClosestStreakCount + bestOpponentThreat.availableMovesFromOtherClosestStreak >= board.K)
                            waysToBeCountered[opponentIndex] = 1;
                    }
                }
            }


            // division by zero should never happen, because in that case the gamestate won't allow to enter in this branch
            if( minPlayerLeft < minOpponentLeft ) {
                score += playerMax;// STANDARD_SCORES.get(STATE_WIN) / (playerLeft);
                if( minPlayerLeft < bonusScoreOnMovesLeft.length )
                    score += bonusScoreOnMovesLeft[minPlayerLeft] / waysToBeCountered[playerIndex];
            }
            else if( minPlayerLeft > minOpponentLeft ) {
                score += -opponentMax; //STANDARD_SCORES.get(STATE_LOSE) / (opponentLeft);
                if( minOpponentLeft < bonusScoreOnMovesLeft.length )
                    score += -bonusScoreOnMovesLeft[minOpponentLeft] / waysToBeCountered[opponentIndex];
            }

/*
            Debug.println("Predicting: " + score + "\n" +
                    "streak of " + playerMax + " for player\n" +
                    "streak of " + opponentMax + " for opponent\n"
            );
*/
            // score = score / Math.max(1, depth);
        }

        outcome.eval = score;
        outcome.depth = depth;
        return outcome;
    }

    /**
     * Updates the weight given to each free cell connected to marked cells
     * based on the count of cells in a row, for each directions, which are in the same state as the supplied cell.
     * So each free cell that follow or precedes a row of in state S, has its weight set to the count of marked cells
     * in a row in the same state s, including cell(i,j) in the count
     * @param i
     * @param j
     * @param mod weight modifier used to scale the count applied to extremes cells, use > 0 after marking, < 0 after unmarking
     */
    private void updateWeightsOnDirection(int directionType, int i, int j, int mod, int playerIndex, ScanResult scanResult ) {
        // first half adjacent direction vectors on clockwise ( starting from 00:00 )

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
            int oppositeSide = (direction_offsets.length-1) - side;

            source[ 0 ] = i; source[ 1 ] = j;

            // flip direction based on type
            Vectors.vectorCopy(direction, direction_offsets[side]);

            countInDirection = scanResult.onSideMarked[side];
            countInOppositeDirection = scanResult.onSideMarked[oppositeSide];

            // Debug.println("From " + source + " in direction " + direction + " we have " + countInDirection + " and in opposite " + countInOppositeDirection );

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
            while( left > 0 && currentBoard.isVectorInBounds(index) ) {
                addStreakWeight(playerIndex, directionType, index[0], index[1], (1+countInOppositeDirection) * mod);
                Vectors.vectorSum(index, direction);
                left--;
            }

            int modSelector = (isCandidate(scanResult.threat) ? mod : -mod);
            // update cell's weight on the both direction end
            // if next is free then update its weight to total
            left = scanResult.onSideFree[side];
            // Math.min(1, scanResult.onSideFree[side]);
            int importance = scanResult.threat.streakCount;
            int it = 0;
            while ( left > 0 && currentBoard.isVectorInBounds(index) ) {
                addWarningWeight(playerIndex, index[0], index[1], (1+countInOppositeDirection + importance) * modSelector);
                if( importance >= scanResult.threat.streakCount)
                    addUsefulness(playerIndex, directionType, index[0], index[1], (1+countInOppositeDirection) * modSelector );
                Vectors.vectorSum(index, direction);
                left--;
                if( importance > 0 )
                    importance--;
                it++;
            }
        }
    }

    @Override
    public void mark(MNKBoard tree, MNKCell marked, int depth) {
        if (tree == null) tree = currentBoard;

        int markingPlayer = tree.currentPlayer();
        super.mark(tree, marked, depth);

        marked = tree.MC.getLast();
        MNKCellState markState = marked.state;

        initDirty();

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
        }*/

        // update target's weight on remove
        addDirty(marked.i, marked.j);

        for ( Map.Entry<Integer, List<MNKCell>> adjEntry : currentBoard.adj(marked).entrySet() ) {
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
            // Debug.println("scan:" + result);
            // remove or nullify
            updateWeightsOnDirection(directionType, marked.i, marked.j,  1, markingPlayer, result );
            updateThreats(result.threat, directionType);
        }

        finishDirty(true);
    }

    protected void updateThreats(Threat threat, int directionType) {
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

    @Override
    public void unMark(MNKBoard tree, int depth) {
        if (tree == null) tree = currentBoard;
        MNKCell marked = tree.MC.getLast();
        MNKCellState markState = currentBoard.cellState(marked.i, marked.j);

        super.unMark(tree, depth);
        int unMarkingPlayer = tree.currentPlayer();

        initDirty();

        addDirty( marked.i, marked.j);

        // Unlink the adjacent cells
/*
        UnionFindUndo<MNKCell>[] comboMap = getPlayerCombos(unMarkingPlayer);
        UnionFindUndo<MNKCell> combosInDirection;
*/
        for ( Map.Entry<Integer, List<MNKCell>> adjEntry : currentBoard.adj(marked).entrySet() ) {
            int directionType = adjEntry.getKey();

//          combosInDirection = comboMap[directionType];


            // evaluate if this streak is a threat
            ScanResult result = getThreatInDirection(marked, directionType);
            // remove or nullify

            updateWeightsOnDirection(directionType, marked.i, marked.j, -1, unMarkingPlayer, result );

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

        finishDirty(false);
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
     *
     * @param source
     * @param directionType
     * @return
     */
    protected ScanResult getThreatInDirection(MNKCell source, int directionType) {

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

        int iterationsPerSide = (currentBoard.K-1) / directionsOffsets.length;
        int rest = (currentBoard.K-1) % directionsOffsets.length;

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
            canIncreaseStreak[side] = canIncreaseStreak[side] && currentBoard.isVectorInBounds(index[side]);

            if( leftIterations[side] > 0 && canIncreaseStreak[side] ) {
                MNKCellState state = currentBoard.cellState( index[side][0], index[side][1] );
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

    @Override
    public int compare(Threat o1, Threat o2) {
        int comp = 0;

        boolean isCandidate1 = isCandidate(o1);
        boolean isCandidate2 = isCandidate(o2);

        if( isCandidate1 && isCandidate2 ) {
            int moveLeftToWin1 = Math.max(0, currentBoard.K - (o1.streakCount + o1.otherClosestStreakCount));
            int moveLeftToWin2 = Math.max(0, currentBoard.K - (o2.streakCount + o2.otherClosestStreakCount));

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

    @Override
    public MNKCell[] getCellCandidates(MNKBoard board) {
        sortDirtyWeights();
        MNKCell[] buffer = new MNKCell[freeCellsCount];
        for (int i = 0; i < freeCellsCount; i++) {
            int[] position = currentBoard.getMatrixIndexesFromArrayIndex(cellsIds[i]);
            buffer[i] = new MNKCell(position[0], position[1]);
        }

        // Debug.println(Arrays.toString(buffer));
        return buffer;
    }

    private void initDirty() {
        dirtyCellsIndexesCount = 0;
    }

    private void addDirty( int i , int j) {
        dirtyCellsIds[dirtyCellsIndexesCount] = currentBoard.getArrayIndexFromMatrixIndexes(i, j);
        dirtyCellsIndexesCount++;

    }

    private void finishDirty(boolean isMark) {
        // first is marked / unmarked element
        // others are updated
        int indexFromBottom = freeCellsCount-1;

        if( isMark ) {
            // endFreeCellsI = --freeCellsCount; // freeCellsCount-1

            int markedID = dirtyCellsIds[0];
            int dirtyID = cellsIds[ indexFromBottom ];
            // swap positions and move the marked cell to the "not free section"
            Utils.swap(cellsIdsPositions, markedID, dirtyID );
            Utils.swap(cellsIds, cellsIdsPositions[dirtyID], cellsIdsPositions[markedID] );
            dirtyCellsIds[0] = dirtyID;

            freeCellsCount--;
        }
        else {
            freeCellsCount++;
        }
    }

    /**
     * Sort cellsIds in O(n*log n) on worst case
     */
    private void sortDirtyWeights() {
        // Using sorting algorithm optimized for almost sorted arrays
        // Utils.Sort.insertionSort(cellsIds, 0, freeCellsCount-1, freeCellsIdsComparators[currentBoard.currentPlayer()]);
        // Using Tim sort since very fast in this case
        Arrays.sort(cellsIds, 0, freeCellsCount, freeCellsIdsComparators[currentBoard.currentPlayer()]);

        for (int i = 0; i < freeCellsCount; i++) {
            cellsIdsPositions[cellsIds[i]] = i;
        }

    }

    private void addWarningWeight(int playerIndex, int i, int j, int value) {
        MNKCell updatedCell;
        weights[playerIndex][ i ][ j ].value += value;
        // update cell position
/*
        MNKCellState cellState = cellState( i, j );
        if( cellState == MNKCellState.FREE ) {
            updatedCell = new MNKCell( i, j, cellState );
            Utils.updateHeapHandlerKey(
                    cellsHandlers[playerIndex].get(updatedCell),
                    weights[ nextPointInSequence[0] ][ nextPointInSequence[1] ],
                    freeCellsQueue[playerIndex].comparator()
            );
        }
 */

        addDirty(i, j);
    }

    public Utils.Weight[][] getWeights(int playerIndex) {
        return weights[playerIndex];
    }

    public Utils.Weight[][] getUsefulnessWeights(int playerIndex, int directionType ) {
        return usefulness[playerIndex][directionType];
    }

    public void addUsefulness(int playerIndex, int directionType, int i, int j, int value ) {
        usefulness[playerIndex][directionType][ i ][ j ].value += value;
    }
    public void setUsefulness(int playerIndex, int directionType, int i, int j, int value ) {
        usefulness[playerIndex][directionType][ i ][ j ].value = value;
    }

    public Utils.Weight[][] getStreakWeights(int playerIndex, int directionType ) {
        return streakWeights[playerIndex][directionType];
    }

    public void addStreakWeight(int playerIndex, int directionType, int i, int j, int value ) {
        streakWeights[playerIndex][directionType][i][j].value += value;
    }
    public void setStreakWeight(int playerIndex, int directionType, int i, int j, int value ) {
        streakWeights[playerIndex][directionType][i][j].value = value;
    }

    @Override
    public String boardToString() {
        String s = "";
        if( DEBUG_SHOW_STREAKS ) {
            for (int p = 0; p < 2; p++) {
                s += "Streaks for p" + (p + 1) + ":\n";
                for (int i = 0; i < currentBoard.B.length; i++) {
                    for (int directionType : Utils.DIRECTIONS) {
                        s += boardToString(null, getStreakWeights(p, directionType)[i], currentBoard.K) + "\t\t\t";
                    }
                    s += "\n";
                }
                s += "\n";
            }
        }
        if( DEBUG_SHOW_USEFUL ) {
            for (int p = 0; p < 2; p++) {
                s += "Usefulness for p" + (p + 1) + ":\n";
                for (int i = 0; i < currentBoard.B.length; i++) {
                    for (int directionType : Utils.DIRECTIONS) {
                        s += boardToString(currentBoard.B[i], getUsefulnessWeights(p, directionType)[i], currentBoard.K) + "\t\t\t";
                    }
                    s += "\n";
                }
                s += "\n";
            }
        }

        for (int i = 0; i < currentBoard.B.length; i++) {
            s += Utils.toString(currentBoard.B[i]) + "\t\t\t";
            if( DEBUG_SHOW_WEIGHTS) {
                for (int p = 0; p < 2; p++) {
                    s += boardToString(currentBoard.B[i], weights[p][i], currentBoard.K) + "\t\t\t";
                }
            }
            s += "\n";
        }
        return s;
    }

    public static String boardToString(MNKCellState[] states, Utils.Weight[] weights, int max) {
        String[] cells = new String[weights.length];
        for (int i = 0; i < weights.length; i++) {
            cells[i] = "";
            int index;
            int aColorSpace = Math.max (1, max / Utils.ConsoleColors.RAINBOW.length);

            boolean shouldColor = Debug.DEBUG_USE_COLORS && (states == null || states[i] == MNKCellState.FREE);
            String color = Utils.ConsoleColors.RESET;
            if( shouldColor ) {
                index = weights[i].value / aColorSpace;
                index = Math.max(0, (Utils.ConsoleColors.RAINBOW.length - 1) - index);
                index = Math.min(Utils.ConsoleColors.RAINBOW.length - 1, index);

                color = Utils.ConsoleColors.RAINBOW[index];
            }

            if(Debug.DEBUG_USE_COLORS)
                cells[i] += color + weights[i].value + Utils.ConsoleColors.RESET;
            else
                cells[i] += weights[i].value;

        }
        return Arrays.toString(cells);
    }

    /**
     * Returns the player name
     *
     * @return string
     */
    @Override
    public String playerName() {
        return "MyPlayer";
    }
}

class Threat {

    // round of evaluation of this threat
    int round;

    // current streak at this round
    int streakCount;

    // amount of slots left available adjacent to the streak
    int nearAvailableMoves;

    // amount of slot left in winnable range that are available, this consider also near streaks
    int totalAvailableMovesOnWinRange;

    // closest streak count near available moves left, that can be reached through free cells
    int otherClosestStreakCount;

    // amount of slots left to link the streak from current streak to the other
    int availableMovesFromOtherClosestStreak;

    // move reference of the streak
    MNKCell move;

    @Override
    public String toString() {
        return "Threat{" +
                "round=" + round +
                ", streakCount=" + streakCount +
                ", freeMovesLeftToCompleteStreak=" + totalAvailableMovesOnWinRange +
                ", nearAvailableMoves=" + nearAvailableMoves +
                ", otherClosestStreakCount=" + otherClosestStreakCount +
                ", availableMovesFromOtherClosestStreak=" + availableMovesFromOtherClosestStreak +
                ", move=" + move +
                '}';
    }
}

class ScanResult {
    Threat threat;
    int directionType;

    // marked count on each side
    int[] onSideMarked;
    // free count on each side, following the marked ones
    int[] onSideFree;
    // marked count on each side, following the free ones ( so this is the count of others streaks near the move reference)
    int[] onSideMarkedNear;


    public int[] getStartMarkedIndex() {
        int[] index = new int[]{ threat.move.i, threat.move.j };
        return index;
    }

    public int[] getStartFreeIndex(int side) {
        int[] index = getStartMarkedIndex();
        final int[] direction = Utils.DIRECTIONS_OFFSETS[directionType][side];
        final int[] distance = new int[direction.length];

        Vectors.vectorCopy(distance, direction);
        Vectors.vectorScale(distance, onSideMarked[side]);
        Vectors.vectorSum(index, distance);

        return index;
    }

    public int[] getStarOtherMarked(int side) {
        int[] index = getStartMarkedIndex();
        final int[] direction = Utils.DIRECTIONS_OFFSETS[directionType][side];
        final int[] distance = new int[direction.length];

        Vectors.vectorCopy(distance, direction);
        Vectors.vectorScale(distance,  onSideMarked[side] + onSideFree[side]);
        Vectors.vectorSum(index, distance);

        return index;
    }

    @Override
    public String toString() {
        return "ScanResult{" +
                "directionType=" + directionType +
                ", onSideMarked=" + Arrays.toString(onSideMarked) +
                ", onSideFree=" + Arrays.toString(onSideFree) +
                ", onSideMarkedNear=" + Arrays.toString(onSideMarkedNear) +
                ", threat=" + threat +
                '}';
    }
}