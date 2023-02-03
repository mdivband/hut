package server.model.task;

import server.Simulator;
import server.model.Agent;
import server.model.Coordinate;
import server.model.target.AdjustableTarget;
import server.model.target.Target;

import java.util.ArrayList;

public class ScoutTask extends Task {
    private Coordinate targetToScan;
    private AdjustableTarget associatedTarget;
    private ArrayList<Agent> workingAgents = new ArrayList<>();

    public ScoutTask(String id, Coordinate coordinate, AdjustableTarget target) {
        super(id, Task.TASK_SCOUT, coordinate);
        associatedTarget = target;
        targetToScan = coordinate;
        Simulator.instance.getState().getPendingIds().add(Simulator.instance.getTargetController().getTargetAt(coordinate).getId());
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
     * Step a task based on changes to its agents and progress.
     * @return True if the task has been completed, false otherwise.
     */
    /*
    public boolean step() {
        // TODO step towards it, and check if image needs to be updated
        if (status == STATUS_TODO) {
            boolean hasAnyAgentArrived = false;
            for (Agent agent : getAgents()) {
                if (agent.isFinalDestinationReached() && agent.getRoute().size() < 2) {
                    agent.setWorking(true);
                    hasAnyAgentArrived = true;
                } else if (agent.getCoordinate().getDistance(targetToScan) < 3) {
                    // Arrived at this one

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
                }
            if(perform())
                setStatus(Task.STATUS_DONE);
            if(getAgents().isEmpty())
                setStatus(Task.STATUS_TODO);
        }

        return status == Task.STATUS_DONE;
    }

     */

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
            //if (agent.isWorking()) {
            if (agent.isFinalDestinationReached()) {
                //Simulator.instance.getImageController().takeImage(agent.getCoordinate(), false);
                //associatedTarget.setType();
                return true;
            }
            //}
        }
        return false;
    }

    @Override
    public void complete() {
        if (associatedTarget.isReal()) {
            associatedTarget.setType(Target.ADJ_CASUALTY);
        } else {
            associatedTarget.setType(Target.ADJ_NO_CASUALTY);
        }
        Simulator.instance.getImageController().agentClassify(this, associatedTarget);
        super.complete();
    }

    public Coordinate getTargetToScan() {
        return targetToScan;
    }


    public AdjustableTarget getAssociatedTarget() {
        return associatedTarget;
    }
}
