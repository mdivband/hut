package server.model.agents;

import server.controller.TaskController;
import server.model.Coordinate;
import server.model.Hub;
import server.model.Sensor;
import server.model.task.Task;

import java.util.Random;

/**
 * Programmed version of the hub
 */
public class AgentHubProgrammed extends AgentProgrammed implements Hub {

    public AgentHubProgrammed(String id, Coordinate position, Sensor sensor, Random random, TaskController taskController) {
        super(id, position, sensor, random, taskController);
        type = "hub";
        stop();
        visible = true;
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
    }

    public boolean checkForConnection(Agent agent) {
        return programmerHandler.checkForConnection(agent);
    }

}
