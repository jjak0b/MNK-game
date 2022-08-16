package player;

public interface InvalidableState {

    boolean isStateValid();
    void invalidateState();
    void setInValidState();

}
