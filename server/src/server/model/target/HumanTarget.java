package server.model.target;

import server.model.Coordinate;

/**
 * Human type target
 */
public class HumanTarget extends Target {

    public HumanTarget(String id, Coordinate coordinate) {
        super(id, coordinate, Target.HUMAN);
    }

}
