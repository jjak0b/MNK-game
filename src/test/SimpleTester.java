package test;

import mnkgame.*;
import player.BestPlayer;
import player.legacy.BestPlayerLegacy;

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
            BestPlayer.class,
            BestPlayerLegacy.class
    };

    public SimpleTester(int TIMEOUT, boolean VERBOSE, int graceTime) {
        super(TIMEOUT, VERBOSE, graceTime);
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
        AdvancedMNKPlayerTester playerTester = new SimpleTester(2, true, 0);
        GameTester gameTester = new GameTester(1, true, playerTester );
        gameTester.main();
    }
}
