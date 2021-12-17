package server.model;

import server.controller.TaskController;
import server.model.task.Task;

import java.util.Random;

public class AgentHubProgrammed extends AgentProgrammed implements Hub {

    public AgentHubProgrammed(String id, Coordinate position, Sensor sensor, Random random, TaskController taskController) {
        super(id, position, sensor, random, taskController);
        visualType = "hub";
    }

    public void addTaskFromUser(Task item) {
        programmerHandler.addTask(item);
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

}
