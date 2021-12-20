package server.model;

import server.Simulator;

import java.util.Collections;
import java.util.List;

/**
 * This is the representation of where the Hub believes agents to be. It will not be interactive
 */
public class AgentGhost extends Agent{

    public AgentGhost(String id, Coordinate position, boolean simulated) {
        super(id, position, simulated);
    }

    /**
     * Copy constructor to create a ghost for the provided agent
     * @param agent The provided agent
     */
    public AgentGhost(Agent agent) {
        super(agent.getId()+"_ghost", agent.getCoordinate(), agent.isSimulated());
        this.setRoute(agent.getTempRoute());
        this.speed = agent.speed;
        this.heading = agent.heading;
        type = "ghost";
    }

    /**
     * Step an agent for this tick
     */
    public void step(Boolean flockingEnabled) {
        if (!route.isEmpty() && !isCurrentDestinationReached()) {
            moveTowardsDestination();
            if (isCurrentDestinationReached() && this.route.size() > 1)
                this.route.remove(0);
        } else {
            // TODO when we have a route queue this will step differently
            setRoute(Collections.singletonList(Simulator.instance.getState().getHubLocation()));
        }

    }

    @Override
    void moveTowardsDestination() {
        if(!isStopped() && this.adjustHeadingTowardsGoal())
            this.moveAlongHeading(1);
    }


    @Override
    void performFlocking() {
        // TODO This is an interesting concern, will flocking agents need to be simulated in prediction?

    }

    /**
     * Adjust heading of agent towards the heading that will take it towards its goal.
     * @return isAligned - Whether the agent is aligned or needs to continue rotating.
     */
    private boolean adjustHeadingTowardsGoal() {
        double lat1 = Math.toRadians(this.getCoordinate().getLatitude());
        double lng1 = Math.toRadians(this.getCoordinate().getLongitude());
        double lat2 = Math.toRadians(this.getCurrentDestination().getLatitude());
        double lng2 = Math.toRadians(this.getCurrentDestination().getLongitude());
        double dLng = (lng2 - lng1);
        double y = Math.sin(dLng) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1)
                * Math.cos(lat2) * Math.cos(dLng);
        double angleToGoal = Math.atan2(y, x);
        return adjustHeading(angleToGoal);
    }

    /**
     * Move agent in direction it is currently facing.
     * @param distance - Distance to move in m.
     */
    protected void moveAlongHeading(double distance) {
        double r = 6379.1; //Radius of earth in km
        double d = (distance/1000)/r;
        double hdg = Math.toRadians(this.heading);
        double lat1 = Math.toRadians(this.getCoordinate().getLatitude());
        double lng1 = Math.toRadians(this.getCoordinate().getLongitude());

        double latDest = Math.asin(
                Math.sin(lat1) * Math.cos(d) +
                        Math.cos(lat1) * Math.sin(d) * Math.cos(hdg));
        double lngDest = lng1 + Math.atan2(
                Math.sin(hdg) * Math.sin(d) * Math.cos(lat1),
                Math.cos(d) - Math.sin(lat1) * Math.sin(latDest));
        latDest = Math.toDegrees(latDest);
        lngDest = Math.toDegrees(lngDest);
        this.setCoordinate(new Coordinate(latDest, lngDest));
    }

    /**
     * Adjust heading of agent.
     * @return isAligned - Whether the agent is aligned or needs to continue rotating.
     */
    private boolean adjustHeading(double angleToGoal) {
        Boolean isAligned;
        double hdgRad = Math.toRadians(this.heading);

        //Calculate difference in clockwise (CW) and counter clockwise (CCW) directions.
        double diffCW, diffCCW;
        if(hdgRad < angleToGoal) {
            diffCW = Math.abs(angleToGoal - hdgRad);
            diffCCW = 2*Math.PI - diffCW;
        }
        else if(hdgRad > angleToGoal) {
            diffCCW = Math.abs(angleToGoal - hdgRad);
            diffCW = 2*Math.PI - diffCCW;
        }
        else
            diffCW = diffCCW = 0;

        if(Math.min(diffCW, diffCCW) <= unitTurningAngle) {
            hdgRad = angleToGoal;
            isAligned = true;
        }
        else {
            //Move in direction with smallest difference
            if(diffCW < diffCCW)
                hdgRad += unitTurningAngle;
            else
                hdgRad -= unitTurningAngle;
            isAligned = false;
        }

        //Account for crossing -pi/pi threshold.
        if(hdgRad > Math.PI)
            hdgRad -= 2*Math.PI;
        else if(hdgRad < -Math.PI)
            hdgRad += 2*Math.PI;

        this.heading = Math.toDegrees(hdgRad);
        return isAligned;
    }

}
