package server.model.task;

import org.checkerframework.checker.units.qual.C;
import server.Simulator;
import server.controller.TaskController;
import server.model.agents.Agent;
import server.model.Coordinate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;

/**
 * Task to perform a deep (high resolution) scan of a target
 * @author William Hunt
 */
public class DeepScanTask extends Task {
    private final Coordinate targetToScan;

    private final boolean imageTaken = false;

    private final ArrayList<Agent> workingAgents = new ArrayList<>();
    private final ArrayList<Agent> scannedAgents = new ArrayList<>();  // agents who have scanned this task (typically singleton)

    public DeepScanTask(String id, Coordinate coordinate) {
        super(id, Task.TASK_DEEP_SCAN, coordinate);
        targetToScan = coordinate;
        //Simulator.instance.getTaskController().smartAllocForDeep();
        Simulator.instance.getState().getPendingIds().add(Simulator.instance.getTargetController().getTargetAt(coordinate).getId());
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
        agent.setRoute(crds);
    }

    /**
     * Step a task based on changes to its agents and progress.
     * @return True if the task has been completed, false otherwise.
     */
    public boolean step() {
        if (status == STATUS_TODO) {
            boolean hasAnyAgentArrived = false;
            for (Agent agent : getAgents()) {
                // TODO This still typically throws an error based on UI not accepting the route (probably empty).
                //  I'm not sure if they are being stopped properly. Either the agent or the task does not finish
                //  properly.

                try {
                    if (agent.isFinalDestinationReached() && agent.getRoute().size() < 2) {
                        agent.setWorking(true);
                        hasAnyAgentArrived = true;
                    } else if (agent.getCoordinate().getDistance(targetToScan) < 3 && !scannedAgents.contains(agent)) {
                        // Arrived at this one
                        scannedAgents.add(agent);
                    }
                } catch (Exception e) {
                    System.out.println("Error in step of DeepScanTask: " + e.getMessage());
                }
            }

            if (hasAnyAgentArrived) {
                setStatus(Task.STATUS_DOING);
                setStartTime(Simulator.instance.getState().getTime());
            }
        }

        if (status == Task.STATUS_DOING) {
            for (Agent agent : getAgents()) {
                if (agent.isFinalDestinationReached()) {
                    agent.setWorking(true);
                } else if (agent.isCurrentDestinationReached()) {
                    scannedAgents.add(agent);
                } else {
                    agent.step(false);
                }
            }
            if(perform()) {
                setStatus(Task.STATUS_DONE);
            }
            if(getAgents().isEmpty()) {
                setStatus(Task.STATUS_TODO);
            }
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
            if (agent.getCoordinate().getDistance(Simulator.instance.getState().getHubLocation()) < 3 && scannedAgents.contains(agent)) {
                // Has been scanned and we are home
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
