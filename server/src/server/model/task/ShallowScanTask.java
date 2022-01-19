package server.model.task;

import server.Simulator;
import server.model.Agent;
import server.model.Coordinate;

import java.util.ArrayList;

public class ShallowScanTask extends Task {

    private ArrayList<Agent> workingAgents = new ArrayList<>();

    public ShallowScanTask(String id, Coordinate coordinate) {
        super(id, Task.TASK_SHALLOW_SCAN, coordinate);
    }

    public void addAgent(Agent agent) {
        //Only add agent if none of the existing agents have the same id.
        if (getAgents().stream().noneMatch(o -> o.getId().equalsIgnoreCase(agent.getId())))
            getAgents().add(agent);

        ArrayList<Coordinate> crds = new ArrayList<>();
        crds.add(getCoordinate());
        agent.setTempRoute(crds);
    }

    /**
     * Perform an in progress task.
     *
     * @return True if task is complete.
     */
    @Override
    boolean perform() {
        for (Agent agent : getAgents()) {
            if (agent.isWorking() && !workingAgents.contains(agent)) {
                workingAgents.add(agent);
            }

            if (agent.isWorking()) {
                if (agent.isFinalDestinationReached()) {
                    Simulator.instance.getImageController().takeImage(agent.getCoordinate(), false);

                    return true;
                }
            }

        }

        return false;
    }
}
