package player;

import mnkgame.MNKCell;
import mnkgame.MNKPlayer;

// this class is a wrapper for a player implementation
public class BestPlayerLegacy implements MNKPlayer {

    SearchMoveStrategy<MNKCell> moveStrategy;

    public BestPlayerLegacy() {}

    @Override
    public void initPlayer(int M, int N, int K, boolean first, int timeout_in_secs) {
        setMoveStrategy(new CachedSearchMoveStrategyLegacy());

        moveStrategy.init(M, N, K, first, timeout_in_secs);
    }

    public void setMoveStrategy(SearchMoveStrategy<MNKCell> moveStrategy) {
        this.moveStrategy = moveStrategy;
    }

    @Override
    public MNKCell selectCell(MNKCell[] FC, MNKCell[] MC) {

        moveStrategy.initSearch(FC, MC);
        MNKCell move = moveStrategy.search();
        moveStrategy.postSearch();

        return move;
    }

    @Override
    public String playerName() {
        return "Cacher legacy";
    }
}
