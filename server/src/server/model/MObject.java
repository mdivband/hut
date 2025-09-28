package server.model;

import java.io.Serializable;

/**
 * @author Feng Wu
 */
public abstract class MObject extends IdObject implements Comparable<MObject>, Serializable {

    private static final long serialVersionUID = 1L;

    private Coordinate coordinate;

    private int targetType;

    public MObject(String id, Coordinate coordinate) {
        super(id);
        this.coordinate = coordinate;
    }

    public MObject(String id, Coordinate coordinate, int targetType) {
        super(id);
        this.coordinate = coordinate;
        this.targetType = targetType;
    }

    public Coordinate getCoordinate() {
        return coordinate;
    }

    public void setCoordinate(Coordinate coordinate) {
        this.coordinate = coordinate;
    }

    public void setTargetType(int targetType) {
        this.targetType = targetType;
    }

    public int getTargetType() {
        return this.targetType;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof MObject) {
            return getId().equals(((MObject) o).getId());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return getId().hashCode();
    }

    @Override
    public int compareTo(MObject o) {
        return getId().compareTo(o.getId());
    }
}
