package server.model;

import server.Simulator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This is the representation of where the Hub believes agents to be. It will not be interactive
 */
public class AgentGhost extends Agent{
    private boolean goingHome = false;
    private boolean atHome = false;
    private boolean leader;
    private Coordinate hubLocation;
    private double communicationRange;

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
        this.speed = agent.getSpeed();
        this.heading = agent.getHeading();
        if (agent instanceof AgentProgrammed ap) {  // Should always be true
            leader = ap.isLeader();
            hubLocation = ap.getHubLocation();
            communicationRange = ap.getSenseRange();
        }
        type = "ghost";
    }

    /**
     * Step an agent for this tick
     */
    public void step(Boolean flockingEnabled) {
        if (!route.isEmpty() && !isCurrentDestinationReached()) {
            // Handle tasks ourselves
            moveTowardsDestination();
            if (isCurrentDestinationReached() && this.route.size() > 1)
                this.route.remove(0);
        } else if (flockingEnabled && hasEnoughNeighbours(50) && !isLeader()) {
            // Try to flock based on other ghosts. Only if enabled, has neighbours to flock with and isn't a leader
            performFlocking();
        } else {
            //System.out.println(getId() + " returning home");
            // Nothing near and no tasks in route, head home because this is theoretically an isolated task (in practise
            //  it may not be, but we can't assume it will jump to the next nearest task due to there being so much
            //  uncertainty
            if (!goingHome && !atHome) {
                // TODO when we have a route queue this will step differently
                //setRoute(Collections.singletonList(Simulator.instance.getState().getHubLocation()));
                setRoute(Collections.singletonList(calculateNearestHomeLocation()));
                goingHome = true;
            } else {
                atHome = true;
                goingHome = false;
            }

        }
    }

    private Coordinate calculateNearestHomeLocation() {
        double xDist = getCoordinate().getLongitude() - hubLocation.longitude;
        double yDist = getCoordinate().getLatitude() - hubLocation.latitude;
        // using tan rule to find angle from hub to agent
        double theta = Math.atan(yDist / xDist);
        //double radius = 0.8 * (((double) SENSE_RANGE /1000)/6379.1);  // 20% into the radius of the hub (includes metre to
        //  latlng calc

        // approx 111,111m = 1deg latlng
        double radius =  0.8 * communicationRange / 111111;

        // x,y = rcos(theta), rsin(theta); If in the negative x, we must invert (=== adding pi rads)
        double xRes;
        double yRes;
        if (xDist < 0) {
            xRes = hubLocation.longitude - (radius * Math.cos(theta));
            yRes = hubLocation.latitude - (radius * Math.sin(theta));
        } else {
            xRes = hubLocation.longitude + (radius * Math.cos(theta));
            yRes = hubLocation.latitude + (radius * Math.sin(theta));
        }

        return new Coordinate(yRes, xRes);
    }

    @Override
    void moveTowardsDestination() {
        if(!isStopped() && this.adjustHeadingTowardsGoal())
            this.moveAlongHeading(1);
    }


    @Override
    void performFlocking() {
        // TODO This is an interesting concern, will flocking agents need to be simulated in prediction?
        if(!isStopped() && this.adjustFlockingHeading())
            this.moveAlongHeading(1);
    }

    /**
     * This could be bundled into the flocking method in future, but that method should be more inherited anyway
     * @param radius
     * @return
     */
    private boolean hasEnoughNeighbours(int radius){
        List<Agent> neighbours = Simulator.instance.getState().senseNeighbouringGhosts(this, radius);
        return neighbours.size() > 1;
    }

    /**
     * Adjust heading of agent towards the average heading of its neighbours.
     * @return isAligned - Whether the agent is aligned or needs to continue rotating.
     */
    private boolean adjustFlockingHeading() {
        double xSum = 0.0;
        double ySum = 0.0;
        double magnitude = 0.0;
        double xAlign = 0.0;
        double yAlign = 0.0;
        double xRepulse = 0.0;
        double yRepulse = 0.0;
        double xAttract = 0.0;
        double yAttract = 0.0;
        double targetHeading = Math.toRadians(this.heading);

        List<Agent> neighbours = Simulator.instance.getState().senseNeighbouringGhosts(this, 50);

        if (neighbours.size() > 0) {

            for (Agent n : neighbours) {
                AgentGhost neighbour = (AgentGhost) n;
                double multiplier = 1;
                if (neighbour.isLeader()) {
                    multiplier = 100;
                }
                else {
                    multiplier = 1;
                }
                double neighbourHeading = Math.toRadians(neighbour.getHeading());
                xSum += Math.cos(neighbourHeading) * multiplier;
                ySum += Math.sin(neighbourHeading) * multiplier;
            }
            magnitude = Math.sqrt(xSum * xSum + ySum * ySum);
            xAlign = xSum/magnitude;
            yAlign = ySum/magnitude;

            List<Agent> tooCloseNeighbours = Simulator.instance.getState().senseNeighbouringGhosts(this, 5);
            List<Agent> notTooClose = new ArrayList<>(neighbours);

            if (tooCloseNeighbours.size() > 0) {
                notTooClose.removeAll(tooCloseNeighbours);

                xSum = 0.0;
                ySum = 0.0;

                for(Agent neighbour : tooCloseNeighbours) {
                    double lat1 = Math.toRadians(this.getCoordinate().getLatitude());
                    double lng1 = Math.toRadians(this.getCoordinate().getLongitude());
                    double lat2 = Math.toRadians(neighbour.getCoordinate().getLatitude());
                    double lng2 = Math.toRadians(neighbour.getCoordinate().getLongitude());
                    double dLng = (lng2 - lng1);
                    ySum -= Math.sin(dLng) * Math.cos(lat2);
                    xSum -= Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1)
                            * Math.cos(lat2) * Math.cos(dLng);
                    magnitude = Math.sqrt(xSum * xSum + ySum * ySum);
                    xRepulse = xSum/magnitude;
                    yRepulse = ySum/magnitude;
                }
            }

            xSum = 0.0;
            ySum = 0.0;

            for(Agent neighbour : notTooClose) {
                double lat1 = Math.toRadians(this.getCoordinate().getLatitude());
                double lng1 = Math.toRadians(this.getCoordinate().getLongitude());
                double lat2 = Math.toRadians(neighbour.getCoordinate().getLatitude());
                double lng2 = Math.toRadians(neighbour.getCoordinate().getLongitude());
                double dLng = (lng2 - lng1);
                ySum += Math.sin(dLng) * Math.cos(lat2);
                xSum += Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1)
                        * Math.cos(lat2) * Math.cos(dLng);
                magnitude = Math.sqrt(xSum * xSum + ySum * ySum);
                xAttract = xSum/magnitude;
                yAttract = ySum/magnitude;
            }

            targetHeading = Math.atan2(
                    yAlign + 0.5 * yAttract + yRepulse,
                    xAlign + 0.5 * xAttract + xRepulse
            );
        }

        adjustHeading(targetHeading);
        return true;
    }

    private boolean isLeader() {
        return leader;
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

    public boolean isGoingHome() {
        return goingHome;
    }

    public void setGoingHome(boolean reachedHome) {
        this.goingHome = reachedHome;
    }

    public boolean isAtHome() {
        return atHome;
    }

    public void setAtHome(boolean atHome) {
        this.atHome = atHome;
    }
}
