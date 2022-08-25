package server.model.task;

import server.model.Coordinate;

/**
 * Standard task that requires an agent to go to this waypoint
 */
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
