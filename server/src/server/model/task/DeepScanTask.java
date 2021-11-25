package server.model.task;

import server.Simulator;
import server.model.Agent;
import server.model.Coordinate;

import java.util.ArrayList;

public class DeepScanTask extends Task {
    private Coordinate targetToScan;

    private boolean imageTaken = false;

    private ArrayList<Agent> workingAgents = new ArrayList<>();

    public DeepScanTask(String id, Coordinate coordinate) {
        super(id, Task.TASK_DEEP_SCAN, coordinate);
        targetToScan = coordinate;
    }

    public void addAgent(Agent agent) {
        //Only add agent if none of the existing agents have the same id.
        if (getAgents().stream().noneMatch(o -> o.getId().equalsIgnoreCase(agent.getId())))
            getAgents().add(agent);

        ArrayList<Coordinate> crds = new ArrayList<>();
        crds.add(getCoordinate());
        crds.add(Simulator.instance.getState().getHubLocation());
        agent.setTempRoute(crds);
    }

    @Override
    public boolean step() {
        for (Agent agent : getAgents()) {
            if (agent.isReached(targetToScan) && !imageTaken) {
                System.out.println("================");
                System.out.println("DEEP SCAN IMAGE TAKEN HERE");
                System.out.println("TODO - Here we will send a request to he controller to prepare the image");
                imageTaken = true;
            }
        }
        return super.step();

    }

    /**
     * Perform an in progress task.
     * @return True if task is complete.
     */
    @Override
    boolean perform() {
        for (Agent agent : getAgents()) {
            if (agent.isWorking() && !workingAgents.contains(agent)) {
                workingAgents.add(agent);
            }

            if(agent.isWorking()) {
                if(agent.isFinalDestinationReached()) {
                    System.out.println("    ----------");
                    System.out.println("TODO - Here we will show the DEEP SCAN image");
                    return true;
                }
            }

        }

        return false;
    }
}
