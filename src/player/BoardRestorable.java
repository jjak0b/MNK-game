package player;

import mnkgame.MNKCell;

public interface BoardRestorable extends InvalidableState {

    void restore(MNKCell[] MC, MNKCell[] FC );
}
