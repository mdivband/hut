package server.model.agents;

import server.Simulator;
import server.model.Coordinate;
import server.model.Sensor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
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

        boolean allHome = true;
        double thresholdDist = 100;
        for (Agent a : Simulator.instance.getState().getAgents()) {
            if (!(a instanceof Hub)) {
                if ((a.getCoordinate().getDistance(getCoordinate()) > thresholdDist) || (a.getTask() != null)) {
                    allHome = false;
                    break;
                }
            }
        }

        if (allHome) {
            System.out.println("ALL HOME");
            Simulator.instance.getAgentController().stopAllAgents();
            Simulator.instance.getAllocator().clearAgents();
            Simulator.instance.getAllocator().runAutoAllocation();
            Simulator.instance.getAllocator().confirmAllocation(Simulator.instance.getState().getTempAllocation());
        }
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
