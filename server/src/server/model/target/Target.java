package server.model.target;

import server.model.Coordinate;
import server.model.MObject;
import server.model.task.Task;

import java.io.Serializable;

public abstract class Target extends MObject implements Serializable {

    private final int type;
    private boolean visible;
    public static final int HUMAN = 0;

    public Target(String id, Coordinate coordinate, int type) {
        super(id, coordinate);
        this.type = type;
        this.visible = true;
    }

    public int getType() {
        return type;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }


}
