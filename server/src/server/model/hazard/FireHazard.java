package server.model.hazard;

import server.model.Coordinate;

public class FireHazard extends Hazard {

    public FireHazard(String id, Coordinate coordinate) {
        super(id, coordinate, Hazard.FIRE, 150);
    }

    @Override
    public void step() {}

}
