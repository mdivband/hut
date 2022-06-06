package server.model.task;

import server.model.Coordinate;

public class GroundTask extends Task {
    public GroundTask(String id, Coordinate coordinate) {
        super(id, Task.TASK_GROUND, coordinate);
    }

    @Override
    boolean perform() {
        //Nothing to perform once task location has been reached.
        return true;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

}
