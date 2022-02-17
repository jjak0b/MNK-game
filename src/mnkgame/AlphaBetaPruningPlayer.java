package mnkgame;

import java.util.*;
import java.util.stream.Collectors;

public class AlphaBetaPruningPlayer implements MNKPlayer{
    protected Random rand;

    protected MNKGameState STATE_WIN;
    protected MNKGameState STATE_LOSE;
    protected MNKCellState MY_MARK_STATE;
    protected MNKCellState ENEMY_MARK_STATE;
    public EnumMap<MNKGameState, Integer> STANDARD_SCORES;
    protected int playerIndex;

    protected int maxDepthSearch;
    protected int timeout;
    protected int round;

    // stats
    protected long totalWorkTime;
    protected long maxWorkTime;
    protected long minWorkTime;
    protected long averageWorkTime;

    protected MNKBoard currentBoard;

    public AlphaBetaPruningPlayer() {

    }

    @Override
    public void initPlayer(int M, int N, int K, boolean first, int timeout_in_secs) {
        // New random seed for each game
        rand = new Random(System.currentTimeMillis());
        // prioritize cells to pick based on a sort of heatmap
        initTrackingBoard(M, N, K);

        if( first ) {
            STATE_WIN = MNKGameState.WINP1;
            STATE_LOSE = MNKGameState.WINP2;
            MY_MARK_STATE = MNKCellState.P1;
            ENEMY_MARK_STATE = MNKCellState.P2;
        }
        else {
            STATE_WIN = MNKGameState.WINP2;
            STATE_LOSE = MNKGameState.WINP1;
            MY_MARK_STATE = MNKCellState.P2;
            ENEMY_MARK_STATE = MNKCellState.P1;
        }
        STANDARD_SCORES = new EnumMap<>(MNKGameState.class);
        STANDARD_SCORES.put(STATE_WIN, Integer.MAX_VALUE);
        STANDARD_SCORES.put(STATE_LOSE, Integer.MIN_VALUE);
        STANDARD_SCORES.put(MNKGameState.DRAW, 0);
        STANDARD_SCORES.put(MNKGameState.OPEN, 0);

        timeout = timeout_in_secs * 1000;
        playerIndex = first ? 0 : 1;

        // round index
        round = 0;

        // stats
        totalWorkTime = 0;
        averageWorkTime = 0;
        minWorkTime = Long.MAX_VALUE;
        maxWorkTime = 0;

        maxDepthSearch = 6;

    }

    protected void initTrackingBoard(int M, int N, int K) {
        currentBoard = new MNKBoard(M,N,K);
    }

    @Override
    public MNKCell selectCell(MNKCell[] FC, MNKCell[] MC) {

        long start = System.currentTimeMillis();
        long endTime = start + (long) ( timeout * (99.0/100.0));
        long workTime = start + timeout;

        if (MC.length > 0) {
            MNKCell choice;
            choice = MC[MC.length - 1]; // Save the last move in the local MNKBoard
            currentBoard.markCell(choice.i, choice.j);
            ++round;
        }

        AlphaBetaOutcome outcome = alphaBetaPruning(
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

        printStats(outcome, elapsed, timeLeft);

        // System.out.println( Arrays.toString( currentBoard.getFreeCells() ) );
        // System.out.println( "if " + currentBoard.currentPlayer()  + " choose " +  outcome.move + " -> " + outcome.eval );
        currentBoard.markCell( outcome.move.i, outcome.move.j );
        System.out.println( "after move:\n" + Utils.toString(currentBoard) );

        ++round;
        return outcome.move;
    }

    protected void printStats(final AlphaBetaOutcome outcome, long elapsed, long timeLeft ) {
        totalWorkTime += elapsed;
        averageWorkTime = totalWorkTime / (round+1);
        maxWorkTime = Math.max(maxWorkTime, elapsed);
        minWorkTime = Math.min(minWorkTime, elapsed);

        System.out.println( "Euristic: " + outcome.getWeightedValue() + "\t" +
                "Decision made in " + (elapsed/1000.0) + "\t" +
                "Left Time: " + (timeLeft/1000.0) + "\t" +
                "Average Time: " + (averageWorkTime/1000.0) + "\t" +
                "Total Time: " + (totalWorkTime/1000.0) + "\t" +
                "Max time: " + (maxWorkTime/1000.0) + "\t" +
                "Min Time: " + (minWorkTime/1000.0)
        );
    }

    protected AlphaBetaOutcome evaluate(MNKBoard board, int depth, boolean isMyTurn) {
        MNKGameState gameState = board.gameState();
        AlphaBetaOutcome outcome = new AlphaBetaOutcome();

        int score = STANDARD_SCORES.get(gameState);
/* // keep as reference
        if( gameState == this.STATE_WIN ) {
            // System.out.println( getTabForDepth( depth-1 ) + MY_MARK_STATE +  "-> Win state");
            // score = 1f;
        }
        else if( gameState == this.STATE_LOSE ) {
            // System.out.println( getTabForDepth( depth-1 ) + MY_MARK_STATE +  "-> Lose state");
            // score = -1f;
        }
        else if( gameState == MNKGameState.DRAW ) {
            // System.out.println( getTabForDepth( depth-1 ) + "Draw state");
            // score = 0f;
        }
        else
*/
        if (gameState == MNKGameState.OPEN) { // game is open
            // System.out.println( getTabForDepth( depth-1 ) + "Heuristic state");
            // TODO: here we should do an Heuristic evaluation

            score = score / Math.max(1, depth);
        }

        outcome.eval = score;
        outcome.depth = depth;
        return outcome;
    }

    /**
     * @param tree
     * @param shouldMaximize if true then will compute best move for this player else for enemy player
     * @param a              best score for this player
     * @param b              best score for simulated enemy
     * @param depth
     * @param depthLeft
     * @param endTime
     * @return
     */
    protected AlphaBetaOutcome alphaBetaPruning(MNKBoard tree, boolean shouldMaximize, int a, int b, int depth, int depthLeft, long endTime) {
        // on last if condition may would be a match in a always win/lost configuration
        if (depthLeft == 0 || tree.gameState() != MNKGameState.OPEN) {
            return evaluate(tree, depth, shouldMaximize);
        } else {
            AlphaBetaOutcome bestOutcome = null, outcome = null;
            // System.out.println( getPlayerByIndex( shouldMaximize ? 0 : 1 )+ " Move " + depth );

            Iterator<MNKCell> moves = getCellCandidates(tree).iterator();

            while (moves.hasNext()) {

                MNKCell move = moves.next();
                mark(tree, move, depth);

                outcome = alphaBetaPruning(tree, !shouldMaximize, a, b, depth + 1, depthLeft - 1, endTime);

                unMark(tree, depth);


                // minimize
                if (!shouldMaximize && (bestOutcome == null || outcome.compareTo(bestOutcome) < 0)) {
                    bestOutcome = outcome;
                    bestOutcome.move = move;
                    b = Math.min(b, outcome.eval);
                }
                // maximize
                else if (shouldMaximize && (bestOutcome == null || outcome.compareTo(bestOutcome) > 0)) {
                    bestOutcome = outcome;
                    bestOutcome.move = move;
                    a = Math.max(a, outcome.eval);
                }

                if (System.currentTimeMillis() > endTime) {
                    System.out.println("Exiting quickly");
                    break;
                }

                if (b <= a) { // a or b cutoff ( can't get better results )
                    break;
                }

                // System.out.println( getPlayerByIndex( isMyTurn ? 0 : 1 )+ " Move " + depth );
            }
            return bestOutcome;
        }
    }



    /**
     * @return moves candidates
     */
    protected List<MNKCell> getCellCandidates(MNKBoard board) {
        return Arrays.stream(board.getFreeCells())
                .collect(Collectors.toList());
    }

    protected void unMark(MNKBoard tree, int depth) {
        //System.out.println( "\t" + tree.currentPlayer + " Unmarking:" + tree.MC.getLast() + " @ " + depth );
        MNKCell unmarked = tree.MC.getLast();
        tree.unmarkCell();
        // System.out.println( getPlayerByIndex( tree.currentPlayer() ) + getTabForDepth( depth ) +  "Unmarking:" + unmarked + " @ " + depth);
        // it.add(MC.remove());
    }

    protected void mark(MNKBoard tree, MNKCell marked, int depth) {
        // MNKCell marked = it.next();
        int playerIndex = tree.currentPlayer();
        // System.out.println( getPlayerByIndex( tree.currentPlayer() ) + getTabForDepth( depth ) +  "Marking:" + marked + " @ " + depth);
        tree.markCell(marked.i, marked.j);
        // it.remove();
        // MC.add(marked);
    }

    /**
     * Returns the player name
     *
     * @return string
     */
    @Override
    public String playerName() {
        return "PruningPlayer";
    }
}