package server.model;

import java.util.logging.Logger;

public class AgentHub extends Agent implements Hub{
    private transient Logger LOGGER = Logger.getLogger(AgentHub.class.getName());
    private transient Sensor sensor;


    public AgentHub(String id, Coordinate position, Sensor sensor) {
        super(id, position, true);
        this.sensor = sensor;
        visualType = "hub";
    }

    @Override
    public void step(Boolean flockingEnabled) {
        super.step(false);
        //Simulate things that would be done by a real drone
        if(!isTimedOut())
            heartbeat();

        // TODO code to see a drone with a returning flag, finish its task, and trigger the image controller to send

    }

    @Override
    void moveTowardsDestination() {
        // pass
    }

    @Override
    void performFlocking() {
        //pass
    }
}