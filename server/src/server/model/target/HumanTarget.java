package server.model.target;

import server.model.Coordinate;

public class HumanTarget extends Target {

    public HumanTarget(String id, Coordinate coordinate) {
        super(id, coordinate, Target.HUMAN);
    }

}
