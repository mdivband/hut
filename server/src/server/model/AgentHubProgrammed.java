package server.model;

import server.controller.TaskController;
import server.model.task.Task;

import java.util.List;
import java.util.Random;

public class AgentHubProgrammed extends AgentProgrammed implements Hub {

    public AgentHubProgrammed(String id, Coordinate position, Sensor sensor, Random random, TaskController taskController) {
        super(id, position, sensor, random, taskController);
        type = "hub";
        stop();
        visible = true;
    }

    public void addTaskFromUser(Task item) {
        programmerHandler.addTask(item);
        //System.out.println("Adding task from user at: " + item.getCoordinate());
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
