package server.model.task;

import server.Simulator;
import server.model.Agent;
import server.model.Coordinate;

import java.util.ArrayList;

public class DeepScanTask extends Task {
    private Coordinate targetToScan;

    private boolean imageTaken = false;

    private ArrayList<Agent> workingAgents = new ArrayList<>();
    private ArrayList<Agent> scannedAgents = new ArrayList<>();  // agents who have scanned this task (typically singleton)

    public DeepScanTask(String id, Coordinate coordinate) {
        super(id, Task.TASK_DEEP_SCAN, coordinate);
        targetToScan = coordinate;
    }

    public void addAgent(Agent agent) {
        //Only add agent if none of the existing agents have the same id.
        if (getAgents().stream().noneMatch(o -> o.getId().equalsIgnoreCase(agent.getId())))
            getAgents().add(agent);

        ArrayList<Coordinate> crds = new ArrayList<>();

        if (!scannedAgents.contains(agent) && agent.getCoordinate().getDistance(targetToScan) > 3) {  // Not yet close
            crds.add(getCoordinate());
        }

        crds.add(Simulator.instance.getState().getHubLocation());
        agent.setTempRoute(crds);
    }

    /**
     * Step a task based on changes to its agents and progress.
     * @return True if the task has been completed, false otherwise.
     */
    public boolean step() {
        if (status == STATUS_TODO) {
            boolean hasAnyAgentArrived = false;
            for (Agent agent : getAgents()) {
                if (agent.isFinalDestinationReached() && agent.getRoute().size() < 2) {
                    agent.setWorking(true);
                    hasAnyAgentArrived = true;
                } else if (agent.getCoordinate().getDistance(targetToScan) < 3 && !scannedAgents.contains(agent)) {
                    // Arrived at this one
                    scannedAgents.add(agent);
                }
            }

            if (hasAnyAgentArrived) {
                setStatus(Task.STATUS_DOING);
                setStartTime(Simulator.instance.getState().getTime());
            }
        }

        if (status == Task.STATUS_DOING) {
            for (Agent agent : getAgents())
                if (agent.isFinalDestinationReached()) {
                    agent.setWorking(true);
                } else if (agent.isCurrentDestinationReached()) {
                    System.out.println("SCANNED");
                    scannedAgents.add(agent);
                }
            if(perform())
                setStatus(Task.STATUS_DONE);
            if(getAgents().isEmpty())
                setStatus(Task.STATUS_TODO);
        }

        return status == Task.STATUS_DONE;
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
            //if(agent.isWorking()) {
                if(agent.isFinalDestinationReached()) {
                    Simulator.instance.getImageController().takeImage(targetToScan, true);
                    return true;
                }
            //}
        }
        return false;
    }

    public Coordinate getTargetToScan() {
        return targetToScan;
    }

    public boolean hasAgentScanned(Agent agent) {
        return (scannedAgents.contains(agent));
    }

    /**
     * Ensures an agent is assigned AND has been scanned
     * @return
     */
    public boolean isBeingWorked() {
        for (Agent a : scannedAgents) {
            if (a.getTask()!= null && a.getTask().equals(this)) {
                return true;
            }
        }
        return false;
    }
}
