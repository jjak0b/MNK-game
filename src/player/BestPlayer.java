package player;


import mnkgame.MNKCell;
import mnkgame.MNKPlayer;

// this class is a wrapper for a player implementation
public class BestPlayer implements MNKPlayer {

    SearchMoveStrategy<MNKCell> moveStrategy;

    public BestPlayer() {}

    @Override
    public void initPlayer(int M, int N, int K, boolean first, int timeout_in_secs) {
        setSearchMoveStrategy(new CachedSearchMoveStrategy());

        moveStrategy.init(M, N, K, first, timeout_in_secs);
    }

    public void setSearchMoveStrategy(SearchMoveStrategy<MNKCell> moveStrategy) {
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
        return "Hotspot";
    }

    @Override
    public int hashCode() {
        return playerName().hashCode();
    }

    public void test() {

        MNKCell[] candidates;
        CachedSearchMoveStrategy player = (CachedSearchMoveStrategy) moveStrategy;
        int M = 10, N=10, K=5;
        player.init(M, N, K, true, 15);

        MNKCell[] cells = new MNKCell[]{
                new MNKCell(M/2, N/2),
                new MNKCell(0, M/2),
                new MNKCell(M/2, M/2 +2),
                new MNKCell(0, M/2 +1),
                new MNKCell(M/2, M/2 +1),
        };

        for(int directionType : Utils.DIRECTIONS) {
            Debug.println(Utils.toString(player.threatDetectionLogic.blocksOnDirection[directionType]));
        }
        for( MNKCell cell : cells ) {
            //candidates = player.getCellCandidates(null);
            // Debug.println(Arrays.toString(candidates));
            player.mark(cell);

            for(int directionType : Utils.DIRECTIONS) {
                Debug.println(Utils.toString(player.threatDetectionLogic.blocksOnDirection[directionType]));
            }

            Debug.println(player.boardToString());
        }

        for( MNKCell cell : cells ) {
            // candidates = player.getCellCandidates(null);
            // Debug.println(Arrays.toString(candidates));
            player.unMark();
            Debug.println(player.boardToString());
        }
    }
}
