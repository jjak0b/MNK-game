package test;

import mnkgame.*;

import java.util.concurrent.*;

public abstract class AdvancedMNKPlayerTester {
    private int      SAFE_TIMEOUT;
    private int     TIMEOUT = 10;
    private int     ROUNDS  = 1;
    private boolean VERBOSE = false;

    private MNKBoard B;
    private MNKPlayer[] Player = new MNKPlayer[2];

    public static class GameSetting {
        int M, N, K;
        MNKGameState expectedResult;
        public GameSetting(int m, int n, int k, MNKGameState expectedResult ) {
            this.M = m;
            this.N = n;
            this.K = k;
            this.expectedResult = expectedResult;
        }
    }

    private static class StoppablePlayer implements Callable<MNKCell> {
        private final MNKPlayer P;
        private final MNKBoard  B;

        public StoppablePlayer(MNKPlayer P, MNKBoard B) {
            this.P = P;
            this.B = B;
        }

        public MNKCell call()  throws InterruptedException {
            return P.selectCell(B.getFreeCells(),B.getMarkedCells());
        }
    }

    public enum GameState {
        WINP1, WINP2, DRAW, ERRP1, ERRP2;
    }

    public AdvancedMNKPlayerTester(int timeout, boolean verbose, int graceTime) {
        this.TIMEOUT = timeout;
        this.SAFE_TIMEOUT = TIMEOUT + graceTime;
        this.VERBOSE = verbose;
    }

    public abstract GameSetting[] getSettings();

    public abstract Class<MNKPlayer>[] getPlayerClasses();

    public void initGame( GameSetting setting, MNKPlayer[] players) {
        int M = setting.M, N=setting.N, K=setting.K;
        for (int i = 0; i < 2; i++) {
            Player[i] = players[i];
        }

        if(VERBOSE) System.out.println("Initializing " + M + "," + N + "," + K + " board");
        B = new MNKBoard(M,N,K);
        // Timed-out initializaton of the MNKPlayers
        for(int k = 0; k < 2; k++) {
            if(VERBOSE) if(VERBOSE) System.out.println("Initializing " + Player[k].playerName() + " as Player " + (k+1));
            final int i = k; // need to have a final variable here
            final Runnable initPlayer = new Thread() {
                @Override
                public void run() {
                    Player[i].initPlayer(B.M,B.N,B.K,i == 0,TIMEOUT);
                }
            };

            final ExecutorService executor = Executors.newSingleThreadExecutor();
            final Future future = executor.submit(initPlayer);
            executor.shutdown();
            try {
                future.get(SAFE_TIMEOUT, TimeUnit.SECONDS);
            }
            catch (TimeoutException e) {
                System.err.println("Error: " + Player[i].playerName() + " interrupted: initialization takes too much time");
                System.exit(1);
            }
            catch (Exception e) {
                System.err.println(e);
                System.exit(1);
            }
            if (!executor.isTerminated())
                executor.shutdownNow();
        }
        if(VERBOSE) System.out.println();
    }

    public GameState runGame() {
        while(B.gameState() == MNKGameState.OPEN) {
            int  curr = B.currentPlayer();
            final ExecutorService executor = Executors.newSingleThreadExecutor();
            final Future<MNKCell> task     = executor.submit(new StoppablePlayer(Player[curr],B));
            executor.shutdown(); // Makes the  ExecutorService stop accepting new tasks

            MNKCell c = null;

            try {
                c = task.get(SAFE_TIMEOUT, TimeUnit.SECONDS);
            }
            catch(TimeoutException ex) {
                int n = 3; // Wait some more time to see if it stops
                System.err.println("Player " + (curr+1) + " (" + Player[curr].playerName() + ") interrupted due to timeout");
                while(!task.isDone() && n > 0) {
                    System.err.println("Waiting for " + Player[curr].playerName() + " to stop ... (" + n + ")");
                    try {Thread.sleep(SAFE_TIMEOUT*1000);} catch(InterruptedException e) {}
                    n--;
                }

                if(n == 0) {
                    System.err.println("Player " + (curr+1) + " (" +Player[curr].playerName() + ") still running: game closed");
                    System.exit(1);
                } else {
                    System.err.println("Player " + (curr+1) + " (" + Player[curr].playerName() + ") eventually stopped: round closed");
                    return curr == 0 ? GameState.ERRP1 : GameState.ERRP2;
                }
            }
            catch (Exception ex) {
                int n = 3; // Wait some more time to see if it stops
                System.err.println("Player " + (curr+1) + " (" + Player[curr].playerName() + ") interrupted due to exception");
                System.err.println(" " + ex);
                while(!task.isDone() && n > 0) {
                    System.err.println("Waiting for " + Player[curr].playerName() + " to stop ... (" + n + ")");
                    try {Thread.sleep(SAFE_TIMEOUT*1000);} catch(InterruptedException e) {}
                    n--;
                }

                if(n == 0) {
                    System.err.println("Player " + (curr+1) + " (" + Player[curr].playerName() + ") still running: game closed");
                    System.exit(1);
                } else {
                    System.err.println("Player " + (curr+1) + " (" + Player[curr].playerName() + ") eventually stopped: round closed");
                    return curr == 0 ? GameState.ERRP1 : GameState.ERRP2;
                }
            }

            if (!executor.isTerminated())
                executor.shutdownNow();

            if(B.cellState(c.i,c.j) == MNKCellState.FREE) {
                if(VERBOSE) System.out.println("Player " + (curr+1) + " (" + Player[curr].playerName() + ") -> [" + c.i + "," + c.j + "]");
                B.markCell(c.i,c.j);
            } else {
                System.err.println("Player " + (curr+1) + " (" + Player[curr].playerName() + ")  selected an illegal move [" + c.i + "," + c.j + "]: round closed");
                return curr == 0 ? GameState.ERRP1 : GameState.ERRP2;
            }
        }

        return B.gameState() == MNKGameState.DRAW ? GameState.DRAW : (B.gameState() == MNKGameState.WINP1 ? GameState.WINP1 : GameState.WINP2);
    }
}
