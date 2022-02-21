package server.model.agents;

import server.Simulator;
import server.model.Coordinate;
import server.model.Sensor;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Non-programmed Hub agent
 */
public class AgentHub extends Agent implements Hub {
    private transient Logger LOGGER = Logger.getLogger(AgentHub.class.getName());
    private transient Sensor sensor;
    private int scheduledRemovals = 0;

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
        //System.out.println("Checking: " + Simulator.instance.getState().getAgents());
        for (Agent a : Simulator.instance.getState().getAgents()) {
            if (!(a instanceof Hub)) {
                if (((a.getCoordinate().getDistance(getCoordinate()) > thresholdDist) || (a.getTask() != null)) || Simulator.instance.getState().getTime() < 5) {
                    allHome = false;
                    break;
                }
            }
        }
        if (allHome) {
            System.out.println("ALL HOME");
            System.out.println("Removing " + scheduledRemovals + " agents");

            List<Agent> agentsCopy = new ArrayList<>(Simulator.instance.getState().getAgents());
            agentsCopy.removeIf(a -> a instanceof Hub);


            for (int i = 0; i < scheduledRemovals; i++) {
                Agent lastAgent = agentsCopy.get(agentsCopy.size() - (i + 1));
                System.out.println("removing " + lastAgent.getId());
                Simulator.instance.getState().getAgents().remove(lastAgent);
            }
            scheduledRemovals = 0;
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

    public int scheduleRemoval(int numRemovals) {
        scheduledRemovals += numRemovals;
        System.out.println("Scheduled to remove the next " + scheduledRemovals + " agents");
        return scheduledRemovals;
    }

    public int getScheduledRemovals() {
        return scheduledRemovals;
    }

    public boolean allAgentsNear() {
        boolean allHome = true;
        for (Agent a : Simulator.instance.getState().getAgents()) {
            if (!(a instanceof Hub)) {
                if ((a.getCoordinate().getDistance(getCoordinate()) > 100)) {
                    allHome = false;
                    break;
                }
            }
        }
        return allHome;
    }
}
