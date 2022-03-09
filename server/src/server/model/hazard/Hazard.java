package server.model.hazard;

import server.Simulator;
import server.model.Coordinate;
import server.model.MObject;
import server.model.agents.Agent;
import server.model.agents.AgentProgrammed;

public abstract class Hazard extends MObject {

    private final int type;
    public static final int NONE = -1;
    public static final int FIRE = 0;
    public static final int DEBRIS = 1;

    private int size;

    public Hazard(String id, Coordinate coordinate, int type, int size) {
        super(id, coordinate);
        this.type = type;
        this.size = size;
    }

    public abstract void step();

    public boolean inRange(Coordinate position) {
        return getCoordinate().getDistance(position) < size;
    }

    public boolean inRange(Coordinate position, double detectionRange) {
        return getCoordinate().getDistance(position) < (size + detectionRange);
    }

    public int getType() {
        return type;
    }

    public double getSize() {
        return size;
    }

    public void revealAround(Agent agent, double detectionRange) {
        for (int d=1; d<=10; d++) {
            for (int i = 0; i < 360; i += 10) {
                Coordinate rangeExtent = getCoordinate().getCoordinate(d * (size / 10.0), Math.toRadians(i));
                if (rangeExtent.getDistance(agent.getCoordinate()) < detectionRange) {
                    Simulator.instance.getState().addHazardHit(getType(), rangeExtent);
                    if (agent instanceof AgentProgrammed ap) {
                        ap.registerHazard(rangeExtent);
                    }
                }
            }
        }
    }

    public boolean agentIn(AgentProgrammed agent) {
        // Add 10, this is an estimate, as the heatmap library places hits with an unknown (to me) radius
        return getCoordinate().getDistance(agent.getCoordinate()) < (size + 25);
    }

    public boolean agentIn(AgentProgrammed agent, double detectionRange) {
        double xMax = getCoordinate().getLatitude() + detectionRange;
        double xMin = getCoordinate().getLatitude() - detectionRange;
        double yMax = getCoordinate().getLongitude() + detectionRange;
        double yMin = getCoordinate().getLongitude() - detectionRange;

        return agent.getCoordinate().getLatitude() < xMax && agent.getCoordinate().getLatitude() > xMin
                && agent.getCoordinate().getLongitude() < yMax && agent.getCoordinate().getLongitude() < yMin;

    }

}
