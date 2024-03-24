package server.model.agents;

import server.Simulator;
import server.controller.TaskController;
import server.model.Coordinate;
import server.model.Sensor;
import server.model.task.Task;

import java.util.List;
import java.util.Random;

/**
 * Programmed version of the hub
 * @author William Hunt
 */
public class AgentHubProgrammed extends AgentProgrammed implements Hub {

    private int scheduledRemovals = 0;

    public AgentHubProgrammed(String id, Coordinate position, Sensor sensor, Random random, TaskController taskController) {
        super(id, position, sensor, random, taskController, "GenericAgentPolicy");
        setMarker("FLAG");
        type = "hub";
        stop();
        visible = true;
        for (Task t : Simulator.instance.getState().getTasks()) {
            addTaskFromUser(t);
        }
    }

    /**
     * Passes through an added task so the hub can add this to its programmer and distribute this information
     * @param item Task added by the user
     */
    public void addTaskFromUser(Task item) {
        programmerHandler.addTask(item);
    }

    /**
     * Passes through a deleted task so the hub can add this deletion to its programmer and distribute this information
     * @param item Task removed by the user
     */
    public void removeTaskFromUser(Task item) {
        programmerHandler.removeTask(item);
    }

    // Called by the Simulator, we will use this to call the Programmer
    @Override
    public void step(Boolean flockingEnabled) {
        programmerHandler.baseStep();
        //Simulate things that would be done by a real drone
        if(!isTimedOut())
            heartbeat();
        this.battery = this.battery > 0 ? this.battery - unitTimeBatteryConsumption : 0;

        List<Agent> agents = sensor.senseNeighbours(this, programmerHandler.getSenseRange());
        agents.removeIf(agent -> !agent.isStopped());
        if (!agents.isEmpty()) {
            if (scheduledRemovals > 0) {
                Agent agentToRemove = agents.get(0);
                //System.out.println("removing " + agentToRemove.getId());
                Simulator.instance.getState().remove(agentToRemove);
                scheduledRemovals--;
            }
        }
    }

    /**
     * Checks whether this agent is connected to the hub. Intended as a backed method
     * @param agent
     * @return
     */
    public boolean checkForConnection(Agent agent) {
        return programmerHandler.checkForConnection(agent);
    }

    public int scheduleRemoval(int numRemovals) {
        scheduledRemovals += numRemovals;
        //System.out.println("Scheduled to remove the next " + scheduledRemovals + " agents");
        return scheduledRemovals;
    }
}
