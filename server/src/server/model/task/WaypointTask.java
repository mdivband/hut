package server.model.task;

import server.model.Coordinate;

public class WaypointTask extends Task {

    public WaypointTask(String id, Coordinate coordinate) {
        super(id, Task.TASK_WAYPOINT, coordinate);
    }

    @Override
    boolean perform() {
        //Nothing to perform once task location has been reached.
        return true;
    }

}
