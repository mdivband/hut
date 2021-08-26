package server.model.hazard;

import server.model.Coordinate;
import server.model.MObject;

public abstract class Hazard extends MObject {

    private final int type;
    public static final int NONE = -1;
    public static final int FIRE = 0;
    public static final int DEBRIS = 1;

    private int size;

    public Hazard(String id, Coordinate coordinate, int type, int size) {
        super(id, coordinate);
        this.type = type;
        this.size = size;
    }

    public abstract void step();

    public boolean inRange(Coordinate position) {
        return getCoordinate().getDistance(position) < size;
    }

    public int getType() {
        return type;
    }

}
