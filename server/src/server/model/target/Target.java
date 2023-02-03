package server.model.target;

import server.model.Coordinate;
import server.model.MObject;
import server.model.task.Task;

import java.io.Serializable;

public abstract class Target extends MObject implements Serializable {

    private int type;
    private boolean visible;
    public static final int HUMAN = 0;
    public static final int ADJUSTABLE = 1;

    //private int status = 0;

    //public static final int ADJ_UNKNOWN = 0;
    public static final int ADJ_DEEP_SCAN = 2;
    public static final int ADJ_SHALLOW_SCAN = 3;
    public static final int ADJ_CASUALTY = 4;
    public static final int ADJ_NO_CASUALTY = 5;


    public Target(String id, Coordinate coordinate, int type) {
        super(id, coordinate);
        this.type = type;
        this.visible = true;
    }

    public void setType(int type) {
        this.type = type;
        //this.type = 0;
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


    //public void setStatus(int status) {
    //    this.status = status;
    //    System.out.println("Set this target's status to " + status);
    //}
}
