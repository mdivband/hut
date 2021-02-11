package server.model.task;

import server.model.Coordinate;

public class MonitorTask extends Task {

    public MonitorTask(String id, Coordinate coordinate) {
        super(id, Task.TASK_MONITOR, coordinate);
    }

    @Override
    boolean perform() {
        //Always remain at task.
        return false;
    }

}