package server.model.target;

import server.model.Coordinate;

public class FireTarget extends Target {
    public FireTarget(String id, Coordinate coordinate) {
        super(id, coordinate, Target.FIRE);
    }
}
