package mnkgame;

import java.util.*;
import java.util.concurrent.TimeoutException;

public class AdaptiveMyPlayer extends MyPlayer {
    protected int maxDepthSearch = 5;
    protected float estimatedPercentOfTimeRequiredToExit = 0.5f/100f;
    protected Stack<AlphaBetaOutcome> bestOutcomes;

    protected void restoreTrackingBoard(MNKCell[] FC, MNKCell[] MC) {
        // we suppose currentBoard.MC.size() >= MC.length

        // we have to restore last valid state, so without last enemy move and this player's last move
        int countToMark = 2;
        int countMCBeforeInvalid = MC.length - countToMark;
        int countToUnMark = currentBoard.MC.size()-countMCBeforeInvalid;

        // un-mark n=difference times and mark next 2 moves (old this player move and the next opponent move )
        int requiredOperationsToRestoreWay1 = countToUnMark + countToMark;
        int requiredOperationsToRestoreWay2 = MC.length;


        if( requiredOperationsToRestoreWay1 <= requiredOperationsToRestoreWay2 ) {
            // then un-mark all until we reach the old valid state.
            for (int i = 0; i < countToUnMark; i++) {
                unMark(currentBoard, -1);
            }
            // and then mark to current state
            for (int i = 0; i < countToMark; i++) {
                mark(currentBoard, MC[ countMCBeforeInvalid + i ], -1);
            }
        }
        // otherwise is more convenient a new instance
        else {
            currentBoard = new WeightedMNKBoard(currentBoard.M, currentBoard.N, currentBoard.K, MC );
        }

        isCurrentBoardLeftInValidState = true;
    }
    public MNKCell selectCell(MNKCell[] FC, MNKCell[] MC) {
        MNKCell choice = null;

        // if last computation terminated in a bad way, then reset board as new
        if( !isCurrentBoardLeftInValidState ) {
            // this shouldn't take too much
            restoreTrackingBoard(FC, MC);
        }
        else {
            if (MC.length > 0) {
                choice = MC[MC.length - 1]; // Recover the last move from MC
                currentBoard.markCell(choice.i, choice.j);
                choice = null;
                ++round;
            }
        }

        switch ( MC.length ){
            case 0: // move as first
                choice = strategyAsFirst(FC, MC);
//                 choice = FC[rand.nextInt(FC.length)]; // random
//                 choice = new MNKCell(3,0);
                break;
//            case 1: // move as second
//                choice = strategyAsSecond(FC, MC);
//                break;
        }

        if( choice != null ) {
            currentBoard.markCell(choice.i, choice.j);         // Save the last move in the local MNKBoard
            return choice;
        }

        // System.out.println( "before move:\n" + currentBoard.toString() );
        long start = System.currentTimeMillis();
        long expectedTimeRequiredToExit = (long) (estimatedPercentOfTimeRequiredToExit * timeout);
        long workTime = start + ( timeout - expectedTimeRequiredToExit );

        // float markRatio = currentBoard.FC.size() != 0 ? (currentBoard.MC.size() / (float) (currentBoard.M * currentBoard.N ) ) : 0f;
        // int average = Math.round(currentBoard.M + currentBoard.N + currentBoard.K) / 3;
        // int movesLeft = Math.max( 5, Math.round( currentBoard.K * markRatio ) );

        // TODO: check with start @ (5, 4 ) in 7 7 4
        AlphaBetaOutcome outcome;
        bestOutcomes = new Stack<>();
        try {
            // Good: 6 6 4, 7 7 4 -> moveleft = 5
            outcome = alphaBetaPruning_(
                    currentBoard,
                    true,
                    STANDARD_SCORES.get(STATE_LOSE),
                    STANDARD_SCORES.get(STATE_WIN),
                    0,
                    maxDepthSearch,
                    workTime
            );
        }
        catch (TimeoutException e) {
            isCurrentBoardLeftInValidState = false;
            // get best fallback outcome
            outcome = bestOutcomes.firstElement();
            System.out.println("Exit quickly");
        }
        bestOutcomes = null;

        long end = System.currentTimeMillis();
        long elapsed = end-start;
        long timeLeft = timeout - elapsed;
        long exitTime = end - workTime;
        long realTimeRequiredToExit = end - workTime;

        if( timeLeft > timeout * estimatedPercentOfTimeRequiredToExit ){
            maxDepthSearch += 1;
        }
        else if( timeLeft < 0 ) {
            maxDepthSearch -= 1;
        }

        printStats(outcome, elapsed, timeLeft);

        // System.out.println( Arrays.toString( currentBoard.getFreeCells() ) );
        // System.out.println( "if " + currentBoard.currentPlayer()  + " choose " +  outcome.move + " -> " + outcome.eval );
        if( isCurrentBoardLeftInValidState )
            currentBoard.markCell( outcome.move.i, outcome.move.j );
        ++round;

        System.out.println( "after move:\n" + currentBoard.toString() );
        return outcome.move;
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

    protected AlphaBetaOutcome alphaBetaPruning_(MNKBoard tree, boolean shouldMaximize, int a, int b, int depth, int depthLeft, long endTime) throws TimeoutException {

        boolean isRootMove = depth == 0;
        // on last if condition may would be a match in a always win/lost configuration
        if (depthLeft == 0 || tree.gameState() != MNKGameState.OPEN) {
            return evaluate(tree, depth, shouldMaximize);
        } else {
            AlphaBetaOutcome bestOutcome = null, outcome = null;
            // System.out.println( getPlayerByIndex( shouldMaximize ? 0 : 1 )+ " Move " + depth );

            Iterator<MNKCell> moves = getCellCandidates(tree).iterator();

            while (moves.hasNext()) {

                MNKCell move = moves.next();

                // if this is the first simulated move of this player, then set this as fallback as best move
                if( isRootMove && bestOutcomes.isEmpty() ) {
                    outcome = new AlphaBetaOutcome(); outcome.move = move; outcome.eval = 0; outcome.depth = depth;
                    bestOutcomes.push(outcome);
                }

                mark(tree, move, depth);

                outcome = alphaBetaPruning_(tree, !shouldMaximize, a, b, depth + 1, depthLeft - 1, endTime);

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

                    // update the fallback best move
                    if( isRootMove ) {
                        bestOutcomes.pop();
                        bestOutcomes.push(bestOutcome);
                    }
                }

                if (System.currentTimeMillis() > endTime) {
                    throw new TimeoutException("Exiting quickly");
                    // break;
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
     * Returns the player name
     *
     * @return string
     */
    @Override
    public String playerName() {
        return "AdaptiveMyPlayer";
    }
}
