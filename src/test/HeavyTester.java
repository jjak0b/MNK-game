package test;

import mnkgame.GoodMemoryPlayer;
import mnkgame.MNKGameState;
import mnkgame.MNKPlayer;
import mnkgame.OptimisticPlayer;

public class HeavyTester extends AdvancedMNKPlayerTester {

    GameSetting[] configurations = new GameSetting[] {
            new GameSetting(50, 50, 10, MNKGameState.OPEN),
            new GameSetting(70, 70, 10, MNKGameState.OPEN)
    };

    Class[] playerClasses = new Class[]{
            GoodMemoryPlayer.class,
            OptimisticPlayer.class
    };

    public HeavyTester(int TIMEOUT, boolean VERBOSE) {
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
        AdvancedMNKPlayerTester playerTester = new HeavyTester(1, true);
        GameTester gameTester = new GameTester(1, playerTester );
        gameTester.main();
    }
}