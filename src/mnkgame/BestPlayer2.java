package mnkgame;


import java.util.Arrays;

// this class is a wrapper for a player implementation
public class BestPlayer2 implements MNKPlayer {

    MNKPlayer player = new MyPlayer2();


    @Override
    public void initPlayer(int M, int N, int K, boolean first, int timeout_in_secs) {
        player.initPlayer(M, N, K, first, timeout_in_secs);
        ((MyPlayer2)player).maxDepthSearch = 14;
    }

    @Override
    public MNKCell selectCell(MNKCell[] FC, MNKCell[] MC) {
        return player.selectCell(FC, MC);
    }

    @Override
    public String playerName() {
        return player.playerName();
    }

    public void test() {
        MNKCell[] candidates;
        MyPlayer2 player = new MyPlayer2();
        int M = 10, N=10, K=5;
        player.initPlayer(M, N, K, true, 15);

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
            player.mark(null, cell, 0);

            for(int directionType : Utils.DIRECTIONS) {
                Debug.println(Utils.toString(player.threatDetectionLogic.blocksOnDirection[directionType]));
            }

            Debug.println(player.boardToString());
        }

        for( MNKCell cell : cells ) {
            // candidates = player.getCellCandidates(null);
            // Debug.println(Arrays.toString(candidates));
            player.unMark(null, 0);
            Debug.println(player.boardToString());
        }
    }
}
