package mnkgame;


// this class is a wrapper for a player implementation
public class BestPlayer2 extends BestPlayer {


    @Override
    public void initPlayer(int M, int N, int K, boolean first, int timeout_in_secs) {
        setMoveStrategy(new CachedSearchMoveStrategy());
        moveStrategy.init(M, N, K, first, timeout_in_secs);
    }

    @Override
    public String playerName() {
        return "Cacher2";
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
