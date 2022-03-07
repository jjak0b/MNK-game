package mnkgame;

public interface BoardRestorable extends InvalidableState {

    void restore( MNKCell[] MC, MNKCell[] FC );
}
