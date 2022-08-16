package player.legacy;

import mnkgame.MNKCell;
import mnkgame.MNKCellState;
import mnkgame.MNKGameState;
import player.*;

import java.util.*;

public class ThreatSearchMoveStrategyLegacy extends AlphaBetaPruningSearchMoveStrategy implements BoardRestorable {

    protected float estimatedPercentOfTimeRequiredToExit;

    protected IndexedBoard currentBoard;

    protected int[][] corners;

    boolean isCurrentBoardLeftInValidState;

    public int[] bonusScoreOnMovesLeft;

    // players comparators to store and keep sorted player cells ids in candidate order
    protected Comparator<Integer>[] freeCellsIdsComparators;

    // weight, if 0, is cell is not useful, otherwise is useful for a direction = count of directions which cell is useful
    protected Utils.Weight[][][][] usefulness;

    // threat weights for free cells, first dimension is the player index
    protected Utils.Weight[][][] weights;

    // cellsIds[ i ] = id
    protected Integer[] cellsIds;
    // flag that indicates free cells buffer must be sorted
    protected boolean shouldSortCellsIds;
    // cellsIdsPositions[ cellID ] = position = i of cellID = cellsIds[ i ]
    protected Integer[] cellsIdsPositions;
    protected int freeCellsCount;

    protected Integer[] dirtyCellsIds;
    protected int dirtyCellsIndexesCount;
    protected int DIRTY_CELLS_SIZE;

    MNKCell[] startingRoundFC;
    MNKCell[] startingRoundMC;

    // debug
    public static final boolean DEBUG_SHOW_STREAKS = Debug.Player.DEBUG_SHOW_STREAKS;
    public static final boolean DEBUG_SHOW_USEFUL = Debug.Player.DEBUG_SHOW_USEFUL;
    public static final boolean DEBUG_SHOW_WEIGHTS = Debug.Player.DEBUG_SHOW_WEIGHTS;
    public static final boolean DEBUG_SHOW_CANDIDATES = Debug.Player.DEBUG_SHOW_CANDIDATES;
    public static final boolean DEBUG_START_FIXED_MOVE = Debug.Player.DEBUG_START_FIXED_MOVE;

    private ScanThreatDetectionLogicLegacy threatDetectionLogic;

    public ThreatDetectionLogic<ThreatInfo> getThreatDetectionLogic() {
        return threatDetectionLogic;
    }

    public class PlayerMoveComparator implements Comparator<Integer> {
        private final int indexPlayer;
        private final int indexOpponent;
        private Utils.Weight[] wMax;

        public PlayerMoveComparator(int playerIndex) {
            this.indexPlayer = playerIndex;
            this.indexOpponent = 1 - playerIndex;
            this.wMax = new Utils.Weight[2];
        }

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
    }

    public ThreatSearchMoveStrategyLegacy() {

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
    public void init(int M, int N, int K, boolean first, int timeout_in_secs) {
        // New random seed for each game

        super.init(M, N, K, first, timeout_in_secs);

        threatDetectionLogic = new ScanThreatDetectionLogicLegacy() {

            @Override
            public void onScanCallback(DirectionThreatInfo result, int directionType, MNKCell source, boolean isMark, int playerIndex) {
                int mod = isMark ? 1 : -1;
                updateWeightsOnDirection(directionType, source.i, source.j,  mod, playerIndex, result );
            }
            @Override
            public EBoard getBoard() {
                return currentBoard;
            }

            @Override
            public int getSimulatedRound() {
                return ThreatSearchMoveStrategyLegacy.this.getSimulatedRound();
            }
        };

        bonusScoreOnMovesLeft = new int[]{ 0, 100000, 1000, 10 };

        corners = new int[][]{ {0, 0}, {0, currentBoard.N-1}, {currentBoard.M-1, 0}, {currentBoard.M-1, currentBoard.N-1} };
        maxDepthSearch = 6;
        estimatedPercentOfTimeRequiredToExit = 5f/100f;

        initWeights(M, N, K);
        threatDetectionLogic.init(M, N, K);
        initComparators();
        initCells(M, N, K);

        setInValidState();

        if( first )
            initOnFirst(M, N, K);
    }

    protected void setBoard(IndexedBoard board) {
        this.currentBoard = board;
        super.setBoard(board);
    }

    /**
     * Callback called on #initPlayer if start as first player.
     * This implementation re-arrange the candidates of root tree ( depth 0 ) to analyze first center and on corners cells
     * @param M
     * @param N
     * @param K
     */
    protected void initOnFirst(int M, int N, int K) {
        if( DEBUG_START_FIXED_MOVE ) return;

        final int[][] prevValues = new int[Utils.DIRECTIONS.length][5];
        for( int d = 0; d < Utils.DIRECTIONS.length; d++ ) {
            prevValues[d][0] = 2;
            for (int j = 1; j < 5; j++) prevValues[d][j] = 1;
        }
        int c, temp;
        // 2 scans:
        // first: assign new weights and sort cells with new weights
        // second: restore weights without re-sort
        for (int i = 0; i < 2; i++) {
            Utils.Weight[][] usefulWeights;
            for( int directionType : Utils.DIRECTIONS ) {
                c = 0;
                usefulWeights = getUsefulnessWeights(playerIndex, directionType);
                temp = usefulWeights[M / 2][ N / 2].value;
                setUsefulness(playerIndex, directionType, M / 2, N / 2, prevValues[directionType][c]);
                prevValues[directionType][c] = temp;
                c++;
                for (int[] corner : corners) {
                    temp = usefulWeights[corner[0]][corner[1]].value;
                    setUsefulness(playerIndex, directionType, corner[0], corner[1], prevValues[directionType][c]);
                    prevValues[directionType][c] = temp;
                    c++;
                }
            }
            if( i == 0 )
                sortDirtyWeights(playerIndex);
        }
        // This will prevent to re-sort free cells candidates
        shouldSortCellsIds = false;
    }

    protected void initCells(int M, int N, int K) {
        final int count = M * N;

        DIRTY_CELLS_SIZE = 1+ K * Utils.DIRECTIONS.length; // TEMP
        dirtyCellsIds = new Integer[DIRTY_CELLS_SIZE];
        dirtyCellsIndexesCount = 0;
        cellsIds = new Integer[ count ];
        cellsIdsPositions = new Integer[ count ];
        for (int i = 0; i < count; i++) {
            cellsIds[i] = i;
            cellsIdsPositions[i] = i;
        }
        freeCellsCount = cellsIds.length;
        shouldSortCellsIds = false;
        // this isn't required since cell are sorted automatically when are requested for candidates
        // sortDirtyWeights(playerIndex);
    }

    // Sets to free all board cells
    protected void initWeights(int M, int N, int K) {
        weights = new Utils.Weight[2][M][N];
        usefulness = new Utils.Weight[2][Utils.DIRECTIONS.length][M][N];
        for (int p = 0; p < 2; p++) {
            for (int i = 0; i < currentBoard.M; i++) {
                for (int j = 0; j < currentBoard.N; j++) {
                    MNKCell c = new MNKCell(i, j);


                    weights[p][i][j] = new Utils.Weight(c, 0);

                    for (int directionType : Utils.DIRECTIONS ) {
                        usefulness[p][directionType][i][j] = new Utils.Weight(c, 0);
                    }
                }
            }
        }

        // mark with an high useless score the diagonals on corners that can't be used for streaks
        final int totallyUselessValue = -1; //Integer.MIN_VALUE / 2;
        for (int i = 0; i < K-1; i++) {
            for (int j = 0; j < (K-1)-i; j++) {
                for (int p = 0; p < 2; p++) {
                    setUsefulness(p, Utils.DIRECTION_TYPE_OBLIQUE_LR, i, j, totallyUselessValue );
                    setUsefulness(p, Utils.DIRECTION_TYPE_OBLIQUE_LR, (M-1)-i, (N-1)-j, totallyUselessValue );
                    setUsefulness(p, Utils.DIRECTION_TYPE_OBLIQUE_RL, (M-1)-i, j, totallyUselessValue );
                    setUsefulness(p, Utils.DIRECTION_TYPE_OBLIQUE_RL, i, (N-1)-j, totallyUselessValue );
                }
            }
        }
    }

    protected void initComparators() {
        freeCellsIdsComparators = new PlayerMoveComparator[2];
        for (int indexPlayer = 0; indexPlayer < 2; indexPlayer++) {
            // heatmap comparators for descending order
            freeCellsIdsComparators[indexPlayer] = new PlayerMoveComparator(indexPlayer);
        }
    }

    // prioritize cells to pick based on a sort of heatmap
    @Override
    protected void initTrackingBoard(int M, int N, int K) {
        try {
            setBoard(new IndexedBoard(M,N,K));
        }
        catch (Throwable e ) {
            Debug.println("Error on init board " + e);
        }
    }

    protected void initCombo() {

    }

    protected void restoreTrackingBoard(MNKCell[] FC, MNKCell[] MC) {
        initTrackingBoard(currentBoard.M, currentBoard.N, currentBoard.K);

        // mark to current state
        for (int i = 0; i < MC.length; i++) {
            mark(MC[ i ]);
        }
    }

    @Override
    public void restore(MNKCell[] FC, MNKCell[] MC) {
        initWeights(currentBoard.M, currentBoard.N, currentBoard.K);
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

    protected AlphaBetaOutcome strategyAsFirst() {
        if( DEBUG_START_FIXED_MOVE ) {
            int[] coords = corners[ 1 ]; // constant for debug
            if( DEBUG_SHOW_INFO )
                Debug.println( "First Move: Move to a fixed corner");
            AlphaBetaOutcome outcome = new AlphaBetaOutcome();
            outcome.move = new MNKCell( coords[0], coords[1] ); outcome.depth = 0; outcome.eval = 0;
            return outcome;
        }
        else {
            return alphaBetaPruning(
                    true,
                    STANDARD_SCORES.get(STATE_LOSE),
                    STANDARD_SCORES.get(STATE_WIN),
                    0,
                    maxDepthSearch,
                    endTime
            );
        }
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
        return currentBoard.getMarkedCellsCount();
    }

    /**
     * Select a position among those listed in the <code>FC</code> array
     * @return an element of <code>FC</code>
     */
    @Override
    public MNKCell search() {
        // set in invalid state, because if running out time, this function may be terminated
        invalidateState();
        switch ( round ){
            case 0: // move as first
                lastResult = strategyAsFirst();
                break;
//            case 1: // move as second
//                choice = strategyAsSecond(FC, MC);
//                break;
            default:
                if ( DEBUG_SHOW_CANDIDATES )
                    Debug.println("Candidates: " + this.getMovesCandidates());
                super.search();
                break;
        }
        // we returned so assuming all right
        setInValidState();
        return lastResult.move;
    }

    /**
     *
     * @param FC Free Cells: array of free cells
     * @param MC Marked Cells: array of already marked cells, ordered with respect
     *           to the game moves (first move is in the first position, etc)
     */
    @Override
    public void initSearch(MNKCell[] FC, MNKCell[] MC) {
        long elapsed = 0;
        startingRoundFC = FC;
        startingRoundMC = MC;

        // pre calculate expected work time
        long expectedTimeRequiredToExit = (long) (estimatedPercentOfTimeRequiredToExit * timeout);
        startTime = System.currentTimeMillis();
        expectedEndTime = startTime + (long) ( timeout * (99.0/100.0)) - expectedTimeRequiredToExit;

        if( DEBUG_SHOW_INFO )
            Debug.println(Utils.ConsoleColors.YELLOW + "Start Restoring current state");

        restore(FC, MC);
        ++round;

        elapsed += System.currentTimeMillis() - startTime;
        if( DEBUG_SHOW_INFO )
            Debug.println(Utils.ConsoleColors.YELLOW + "End Restoring current state, time spent: " + (elapsed/1000.0) + Utils.ConsoleColors.RESET );
        if( DEBUG_SHOW_BOARD )
            Debug.println( "after opponent move:\n" + boardToString() );

        lastResult = null;
    }

    @Override
    public void postSearch() {
        if( Debug.DEBUG_ENABLED ) {
            if(!isStateValid()){
                if( DEBUG_SHOW_INFO )
                    Debug.println(Utils.ConsoleColors.YELLOW + "Start Restoring current state");
                restore(startingRoundFC, startingRoundMC);
                if( DEBUG_SHOW_INFO )
                    Debug.println(Utils.ConsoleColors.YELLOW + "End Restoring current state" + Utils.ConsoleColors.RESET );
            }
        }

        if( isStateValid() )
            mark(lastResult.move);

        if( DEBUG_SHOW_STATS )
            printStats(lastResult);
        if( DEBUG_SHOW_BOARD )
            Debug.println( "after move:\n" + boardToString() );
        if( Debug.DEBUG_ENABLED && currentBoard.gameState() != MNKGameState.OPEN ){
            Debug.println( "Final board:\n" + boardToString() );
        }

        round++;
    }

    @Override
    protected AlphaBetaOutcome evaluate(int depth, boolean isMyTurn) {
        MNKGameState gameState = currentBoard.gameState();
        AlphaBetaOutcome outcome = new AlphaBetaOutcome();

        int score = STANDARD_SCORES.get(gameState);


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
            // here we should do an Heuristic evaluation

            // newStimeScore = scoreWeight * oldScore  + ( 1 - scoreWeight ) * oldStimeScore

            // int playerIndex = Utils.getPlayerIndex(MY_MARK_STATE);
            int opponentIndex = 1-playerIndex;

            final int[] maxPlayerStreak = {0, 0};
            final int[] minPlayerMoveLeft = {currentBoard.K, currentBoard.K};
            final int[] waysToBeCountered = {1, 1};

            for (int indexPlayer = 0; indexPlayer < 2; indexPlayer++) {
                for ( int directionType : Utils.DIRECTIONS ) {

                    ThreatInfo bestPlayerThreat = getThreatDetectionLogic().getBestThreat(indexPlayer, directionType);

                    if( bestPlayerThreat != null) {
                        maxPlayerStreak[indexPlayer] = Math.max(maxPlayerStreak[indexPlayer], bestPlayerThreat.getStreakCount());
                        int old = minPlayerMoveLeft[indexPlayer];
                        minPlayerMoveLeft[indexPlayer] = Math.min(old, Math.max(1, currentBoard.K - (bestPlayerThreat.getStreakCount() + bestPlayerThreat.getOtherMarkedCount())));

                        // apply these modifiers only on closest streak to win
                        if( old > minPlayerMoveLeft[indexPlayer] ) {

                            // need just one of adjacent
                            if( bestPlayerThreat.getStreakCount() + bestPlayerThreat.getAdjacentFreeCount() >= currentBoard.K )
                                waysToBeCountered[indexPlayer] = bestPlayerThreat.getAdjacentFreeCount();

                            // need a link between 2 streak
                            else if( bestPlayerThreat.getStreakCount() + bestPlayerThreat.getOtherMarkedCount() + bestPlayerThreat.getOtherFreeCount() >= currentBoard.K)
                                waysToBeCountered[indexPlayer] = 1;
                        }
                    }
                }
            }


            // division by zero should never happen, because in that case the gamestate won't allow to enter in this branch
            if(minPlayerMoveLeft[playerIndex] < minPlayerMoveLeft[opponentIndex]) {
                score += maxPlayerStreak[playerIndex];// STANDARD_SCORES.get(STATE_WIN) / (playerLeft);
                if( minPlayerMoveLeft[playerIndex] < bonusScoreOnMovesLeft.length )
                    score += bonusScoreOnMovesLeft[minPlayerMoveLeft[playerIndex]] / waysToBeCountered[playerIndex];
            }
            else if( minPlayerMoveLeft[playerIndex] > minPlayerMoveLeft[opponentIndex] ) {
                score += -maxPlayerStreak[opponentIndex]; //STANDARD_SCORES.get(STATE_LOSE) / (opponentLeft);
                if( minPlayerMoveLeft[opponentIndex] < bonusScoreOnMovesLeft.length )
                    score += -bonusScoreOnMovesLeft[minPlayerMoveLeft[opponentIndex]] / waysToBeCountered[opponentIndex];
            }

/*
            Debug.println("Predicting: " + score + "\n" +
                    "streak of " + playerMax + " for player\n" +
                    "streak of " + opponentMax + " for opponent\n"
            );
*/
            // score = score / Math.max(1, depth);
        }

        if( score >= depth)
            score -= depth-1;
        else if( score <= -depth)
            score += depth-1;

        outcome.eval = score;
        outcome.depth = depth;
        return outcome;
    }

    /**
     * Updates the weight given to each free cell connected to marked cells
     * based on the count of cells in a row, for each directions, which are in the same state as the supplied cell.
     * So each free cell that follow or precedes a row of in state S, has its weight set to the count of marked cells
     * in a row in the same state s, including cell(i,j) in the count
     * @param directionType
     * @param i
     * @param j
     * @param mod weight modifier used to scale the count applied to extremes cells, use > 0 after marking, < 0 after unmarking
     * @param playerIndex
     * @param scanResult
     */
    protected void updateWeightsOnDirection(int directionType, int i, int j, int mod, int playerIndex, DirectionThreatInfo scanResult ) {
        // first half adjacent direction vectors on clockwise ( starting from 00:00 )

        int[]   source = { 0, 0 },
                direction = {0, 0},
                distance = { 0, 0 },
                index = { 0, 0 };
        int countInDirection = 0, countInOppositeDirection = 0;

        int[][] direction_offsets = Utils.DIRECTIONS_OFFSETS[directionType];


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

            // skip after the streak and start from the adjacent to the streak
            Vectors.vectorCopy(distance, direction);
            Vectors.vectorScale(distance, 1+countInDirection);
            Vectors.vectorSum(index, distance);


            int modSelector = ( threatDetectionLogic.isCandidate(scanResult) ? mod : -mod);
            // update cell's weight on the both direction end
            // if next is free then update its weight to total
            int left = scanResult.onSideFree[side];
            int importance = scanResult.getStreakCount();
            int it = 0;
            while ( left > 0 && currentBoard.isVectorInBounds(index) ) {
                addWarningWeight(playerIndex, index[0], index[1], (1+countInOppositeDirection + importance) * modSelector);
                if( importance >= scanResult.getStreakCount())
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
    public void mark(MNKCell marked) {

        int markingPlayer = currentBoard.currentPlayer();
        super.mark(marked);

        marked = currentBoard.getLastMarked();
        MNKCellState markState = marked.state;

        initDirty();

        // update target's weight on remove
        addDirty(marked.i, marked.j);

        getThreatDetectionLogic().mark(currentBoard, marked, markingPlayer, 0);

        finishDirty(true);
    }

    @Override
    public void unMark() {
        MNKCell marked = currentBoard.getLastMarked();
        MNKCellState markState = currentBoard.cellState(marked.i, marked.j);

        super.unMark();
        int unMarkingPlayer = currentBoard.currentPlayer();

        initDirty();

        addDirty( marked.i, marked.j);

        getThreatDetectionLogic().unMark(currentBoard, marked, unMarkingPlayer, 0);

        finishDirty(false);
    }

    @Override
    public Iterable<MNKCell> getMovesCandidates() {
        if( shouldSortCellsIds ) {
            sortDirtyWeights(currentBoard.currentPlayer());
            shouldSortCellsIds = false;
        }
        MNKCell[] buffer = new MNKCell[freeCellsCount];
        for (int i = 0; i < freeCellsCount; i++) {
            int[] position = currentBoard.getMatrixIndexesFromArrayIndex(cellsIds[i]);
            buffer[i] = new MNKCell(position[0], position[1]);
        }

        // Debug.println(Arrays.toString(buffer));
        return Arrays.asList(buffer);
    }

    private void initDirty() {
        dirtyCellsIndexesCount = 0;
    }

    private void addDirty( int i , int j) {
        dirtyCellsIds[dirtyCellsIndexesCount] = currentBoard.getArrayIndexFromMatrixIndexes(i, j);
        dirtyCellsIndexesCount++;
        shouldSortCellsIds = true;

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
    private void sortDirtyWeights(int playerIndex) {
        // Using sorting algorithm optimized for almost sorted arrays
        // Utils.Sort.insertionSort(cellsIds, 0, freeCellsCount-1, freeCellsIdsComparators[currentBoard.currentPlayer()]);
        // Using Tim sort since very fast in this case
        Arrays.sort(cellsIds, 0, freeCellsCount, freeCellsIdsComparators[playerIndex]);

        for (int i = 0; i < freeCellsCount; i++) {
            cellsIdsPositions[cellsIds[i]] = i;
        }

    }

    protected void addWarningWeight(int playerIndex, int i, int j, int value) {
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
    public int getUsefulness(int playerIndex, int directionType, int i, int j) {
        return usefulness[playerIndex][directionType][ i ][ j ].value;
    }


    @Override
    public String boardToString() {
        String s = "";
        if( DEBUG_SHOW_STREAKS ) {
            for (int p = 0; p < 2; p++) {
                s += "Streaks for p" + (p + 1) + ":\n";
                for (int i = 0; i < currentBoard.states().length; i++) {
                    for (int directionType : Utils.DIRECTIONS) {
                        s += boardToString(null, (threatDetectionLogic).getStreakWeights(p, directionType)[i], currentBoard.K) + "\t\t\t";
                    }
                    s += "\n";
                }
                s += "\n";
            }
        }
        if( DEBUG_SHOW_USEFUL ) {
            for (int p = 0; p < 2; p++) {
                s += "Usefulness for p" + (p + 1) + ":\n";
                for (int i = 0; i < currentBoard.states().length; i++) {
                    for (int directionType : Utils.DIRECTIONS) {
                        s += boardToString(currentBoard.states()[i], getUsefulnessWeights(p, directionType)[i], currentBoard.K) + "\t\t\t";
                    }
                    s += "\n";
                }
                s += "\n";
            }
        }

        for (int i = 0; i < currentBoard.states().length; i++) {
            s += Utils.toString(currentBoard.states()[i]) + "\t\t\t";
            if( DEBUG_SHOW_WEIGHTS) {
                for (int p = 0; p < 2; p++) {
                    s += boardToString(currentBoard.states()[i], weights[p][i], currentBoard.K) + "\t\t\t";
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
}