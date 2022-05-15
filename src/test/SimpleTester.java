package test;

import mnkgame.GoodMemoryPlayer;
import mnkgame.MNKGameState;
import mnkgame.MNKPlayer;
import mnkgame.OptimisticPlayer;

public class SimpleTester extends AdvancedMNKPlayerTester {

    GameSetting[] configurations = new GameSetting[] {
            new GameSetting(3, 3, 3, MNKGameState.DRAW),
            new GameSetting(4, 3, 3, MNKGameState.WINP1),
            new GameSetting(4, 4, 3, MNKGameState.WINP1),
            new GameSetting(4, 4, 4, MNKGameState.DRAW),
            new GameSetting(5, 4, 4, MNKGameState.DRAW),
            new GameSetting(5, 5, 4, MNKGameState.DRAW),
            new GameSetting(5, 5, 5, MNKGameState.DRAW),
            new GameSetting(6, 4, 4, MNKGameState.DRAW),
            new GameSetting(6, 5, 4, MNKGameState.WINP1),
            new GameSetting(6, 6, 4, MNKGameState.WINP1),
            new GameSetting(6, 6, 5, MNKGameState.DRAW),
            new GameSetting(6, 6, 6, MNKGameState.DRAW),
            new GameSetting(7, 4, 4, MNKGameState.DRAW),
            new GameSetting(7, 5, 4, MNKGameState.WINP1),
            new GameSetting(7, 6, 4, MNKGameState.WINP1),
            new GameSetting(7, 7, 4, MNKGameState.WINP1),
            new GameSetting(7, 5, 5, MNKGameState.DRAW),
            new GameSetting(7, 6, 5, MNKGameState.DRAW),
            new GameSetting(7, 7, 5, MNKGameState.DRAW),
            new GameSetting(7, 7, 6, MNKGameState.DRAW),
            new GameSetting(7, 7, 7, MNKGameState.OPEN),
            new GameSetting(8, 8, 4, MNKGameState.WINP1),
            new GameSetting(10, 10, 5, MNKGameState.OPEN)
    };

    Class[] playerClasses = new Class[]{
            GoodMemoryPlayer.class,
            OptimisticPlayer.class
    };

    public SimpleTester(int TIMEOUT, boolean VERBOSE) {
        super(TIMEOUT, VERBOSE);
    }

    @Override
    public GameSetting[] getSettings() {
        return configurations;
    }

    @Override
    public Class<MNKPlayer>[] getPlayerClasses() {
        return playerClasses;
    }

    public static void main(String[] args) {
        AdvancedMNKPlayerTester playerTester = new SimpleTester(1, true);
        GameTester gameTester = new GameTester(1, playerTester );
        gameTester.main();
    }
}
