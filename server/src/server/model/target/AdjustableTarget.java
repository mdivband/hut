package server.model.target;

import server.model.Coordinate;

public class AdjustableTarget extends Target {

    //private int status = 0;

    public AdjustableTarget(String id, Coordinate coordinate) {
        super(id, coordinate, Target.ADJUSTABLE);
    }

}
