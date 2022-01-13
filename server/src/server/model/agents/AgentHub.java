package server.model.agents;

import server.model.Coordinate;
import server.model.Hub;
import server.model.Sensor;

import java.util.logging.Logger;

/**
 * Non-programmed Hub agent
 */
public class AgentHub extends Agent implements Hub {
    private transient Logger LOGGER = Logger.getLogger(AgentHub.class.getName());
    private transient Sensor sensor;

    public AgentHub(String id, Coordinate position, Sensor sensor) {
        super(id, position, true);
        this.sensor = sensor;
        type = "hub";
    }

    @Override
    public void step(Boolean flockingEnabled) {
        super.step(false);
        //Simulate things that would be done by a real drone
        if(!isTimedOut())
            heartbeat();
    }

    @Override
    void moveTowardsDestination() {
        // pass
    }

    @Override
    void performFlocking() {
        // pass
    }

}
