package server.model.task;

import server.Simulator;
import server.model.Coordinate;
import server.model.agents.Agent;
import server.model.agents.AgentCommunicating;
import server.model.agents.AgentVirtual;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Task that requires an agent to go there, and then return home. Like a "pickup" task
 * @author William Hunt
 */
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
        triggerReturnHome();
    }

    /**
     * Triggers that the agent has completed the first part of the task and must now return home to the HUB
     */
    public void triggerReturnHome() {
        for (Agent a : getArrivedAgents()) {
            if (a instanceof AgentVirtual av && getAgents().contains(a)) {
                av.goHome();
            }
        }
    }
}
