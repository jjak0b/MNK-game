package mnkgame;

public interface SideThreatInfo extends ThreatInfo {


    /**
     * Breadth 1
     * free adjacent on a side of Breadth 0 - so adjacent of this
     *
     * @param side
     * @return
     */
    int getFreeOnSide(int side);


    /**
     * Breadth 2
     * marked adjacent on a side of Breadth 1 if color is the same of this
     *
     * @param side
     * @return
     */
    int getOtherMarkedOnSide(int side);

    /**
     * Breadth 3
     * free adjacent on a side of Breadth 2
     *
     * @param side
     * @return
     */
    int getOtherFreeOnSide(int side);
}
