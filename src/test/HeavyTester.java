package test;

import player.legacy.CachedSearchMoveStrategyLegacy;
import mnkgame.MNKGameState;
import mnkgame.MNKPlayer;

public class HeavyTester extends AdvancedMNKPlayerTester {

    GameSetting[] configurations = new GameSetting[] {
            new GameSetting(50, 50, 10, MNKGameState.OPEN),
            new GameSetting(70, 70, 10, MNKGameState.OPEN)
    };

    Class[] playerClasses = new Class[]{
            CachedSearchMoveStrategyLegacy.class
    };

    public HeavyTester(int TIMEOUT, boolean VERBOSE, int graceTime) {
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
        AdvancedMNKPlayerTester playerTester = new HeavyTester(1, true, 0);
        GameTester gameTester = new GameTester(1, true, playerTester );
        gameTester.main();
    }
}
