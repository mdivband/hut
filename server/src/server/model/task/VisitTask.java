package server.model.task;

import server.Simulator;
import server.model.Coordinate;
import server.model.agents.Agent;
import server.model.agents.AgentCommunicating;
import server.model.agents.AgentVirtual;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class VisitTask extends Task {

    public VisitTask(String id, Coordinate coordinate) {
        super(id, Task.TASK_VISIT, coordinate);
    }

    @Override
    boolean perform() {
        return true;
    }

    @Override
    public void complete() {
        super.complete();
        for (Agent a : getArrivedAgents()) {
            if (a instanceof AgentVirtual av) {
                av.goHome();
            }
        }
    }
}
