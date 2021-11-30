package server.model.target;

import server.model.Coordinate;

public class AdjustableTarget extends Target {

    //private int status = 0;
    private boolean isReal;

    public AdjustableTarget(String id, Coordinate coordinate, boolean isReal) {
        super(id, coordinate, Target.ADJUSTABLE);
        this.isReal = isReal;
    }

    public boolean isReal() {
        return isReal;
    }
}
