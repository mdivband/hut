package server.model.hazard;

import server.model.Coordinate;

public class DebrisHazard extends Hazard {

    public DebrisHazard(String id, Coordinate coordinate) {
        super(id, coordinate, Hazard.DEBRIS, 150);
    }

    public DebrisHazard(String id, Coordinate coordinate, int size) {
        super(id, coordinate, Hazard.DEBRIS, size);
    }

    @Override
    public void step() {}

}
