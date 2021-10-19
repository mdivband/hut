package server.model;

import server.controller.TaskController;
import server.model.task.Task;

import java.util.Random;
import java.util.logging.Logger;

public class AgentReceiver extends AgentProgrammed {
    private transient String networkID = "";
    private transient Random random;
    private transient Logger LOGGER = Logger.getLogger(AgentVirtual.class.getName());
    private transient Boolean isStationary = false;

    private transient Sensor sensor;
    private transient TaskController taskController;
    private transient ProgrammerHandler programmerHandler;

    public AgentReceiver(String id, Coordinate position, Sensor sensor) {
        super(id, position, sensor);
        this.programmerHandler = new ProgrammerHandler(this);
        this.sensor = sensor;
    }

    public AgentReceiver(String id, Coordinate position, Sensor sensor, Random random, TaskController taskController, boolean isStationary) {
        super(id, position, sensor, random, taskController);
        this.isStationary = isStationary;
        //this.random = random;
        //this.sensor = sensor;
        //this.taskController = taskController;
        this.programmerHandler = new ProgrammerHandler(this);
    }

    @Override
    void moveTowardsDestination() {
        if (!isStationary) {
            super.moveTowardsDestination();
        }

    }

    @Override
    void performFlocking() {
        if (!isStationary) {
            super.performFlocking();
        }
    }

    @Override
    public void step(Boolean flockingEnabled) {
        if(!isTimedOut())
            heartbeat();

        if (!isStationary) {
            super.step(flockingEnabled);
            //super.moveTowardsDestination();
        }

        programmerHandler.baseStep();
    }

    public void addTaskFromUser(Task item) {
        programmerHandler.addTask(item);
    }

}
