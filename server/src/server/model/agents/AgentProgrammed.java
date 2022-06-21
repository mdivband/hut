package server.model.agents;

import server.Simulator;
import server.controller.TaskController;
import server.model.Coordinate;
import server.model.Sensor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;

/**
 * Programmed agent that enacts behaviours as programmed by the user
 */
public class AgentProgrammed extends Agent {
    private transient String networkID = "";
    private transient Random random;
    private transient Logger LOGGER = Logger.getLogger(AgentVirtual.class.getName());

    protected transient Sensor sensor;
    protected transient TaskController taskController;
    protected transient ProgrammerHandler programmerHandler;

    public AgentProgrammed(String id, Coordinate position, Sensor sensor) {
        super(id, position, true);
        this.programmerHandler = new ProgrammerHandler(this);
        this.sensor = sensor;
        type = "programmed";
    }

    public AgentProgrammed(String id, Coordinate position, Sensor sensor, Random random, TaskController taskController) {
        super(id, position, true);
        this.random = random;
        this.sensor = sensor;
        this.taskController = taskController;
        this.programmerHandler = new ProgrammerHandler(this);
        type = "programmed";
    }

    public void softReset() {
        if (!(this instanceof Hub)) {
            super.softReset();
            int level = programmerHandler.getAgentProgrammer().getLevel();
            List<AgentProgrammed> subs = programmerHandler.getAgentProgrammer().getSubordinates();
            LearningAllocator la = programmerHandler.getAgentProgrammer().getLearningAllocator();
            programmerHandler = new ProgrammerHandler(this);
            programmerHandler.getAgentProgrammer().setLevel(level);
            programmerHandler.getAgentProgrammer().setAllocator(la);
            programmerHandler.getAgentProgrammer().setSubordinates(subs);
            programmerHandler.getAgentProgrammer().setup();
        }
    }

    /***
     * Generates a random tag for a network ID. We use a global random object, but in real life these may be based on
     * unique information such as serial numbers PUFs.
     *
     * Collision chance (approx):
     * 26 letters = 4 bits (plus another for remaining 8, but we'll assume the worst)
     * 4*16 = 64, a 64 bit hash for e.g. 100 agents has collision probability
     * 2.22e-16
     * According to https://everydayinternetstuff.com/2015/04/hash-collision-probability-calculator/
     *
     * @return a 16-char alphanumeric ID
     */
    protected String generateRandomTag(){
        // from https://www.baeldung.com/java-random-string
        int leftLimit = 97; // letter 'a'
        int rightLimit = 122; // letter 'z'
        int targetStringLength = 16;

        return random.ints(leftLimit, rightLimit + 1)
                .limit(targetStringLength)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }

    public void setNetworkID(String id) {
        networkID = id;
    }

    public String getNetworkId(){
        return networkID;
    }

    protected void receiveMessage(String message){
        programmerHandler.receiveMessage(message);
    }

    // Called by the Simulator, we will use this to call the Programmer
    @Override
    public void step(Boolean flockingEnabled) {
        programmerHandler.step();
        //Simulate things that would be done by a real drone
        if(!isTimedOut())
            heartbeat();
        this.battery = this.battery > 0 ? this.battery - unitTimeBatteryConsumption : 0;
        //manualCheckTaskComplete();
    }

    /**
     * Manually searches for nearby tasks to register as complete
     */
    private void manualCheckTaskComplete(){
        Coordinate completeNearbyTask = taskController.checkIfNearbyTaskComplete(getCoordinate(), getEPS());
        if (completeNearbyTask != null) {
            // We have checked, and found a task near to this drone, so we will complete it
            programmerHandler.completeTask(completeNearbyTask);
        }
    }

    /**
     * Register a task completion when the task triggers it.
     * @param coordinate Coordinate of the completed task
     */
    public void registerCompleteTask(Coordinate coordinate) {
        programmerHandler.completeTask(coordinate);
    }

    @Override
    void moveTowardsDestination() {
        //Move agents
        if (!getRoute().isEmpty() && !isCurrentDestinationReached()) {
            if (!getSearching()) {

                //Align agent, if aligned then moved towards target
                if(!isStopped() && this.adjustHeadingTowardsGoal()) {
                    // From ms/s, but instead of dividing by 1 second, it's by one game step (fraction of a second)
                    // We also check if we are closer than 1 move step; in which case
                    double distToMove = Math.min(speed / Simulator.instance.getGameSpeed(), getCoordinate().getDistance(getCurrentDestination()));
                    this.moveAlongHeading(distToMove);
                    //this.moveAlongHeading(1);
                }

                incrementTimeInAir();
            }
            if (isCurrentDestinationReached() && this.getRoute().size() > 1) {
                this.getRoute().remove(0);
            }
        }

        if (isFinalDestinationReached() && !isStopped()) {
            stop();
            programmerHandler.completeTask();
        }
    }

    /**
     * Progresses agent movement along a patrol task
     */
    public void moveAlongPatrol() {
        if (isCurrentDestinationReached() && !isStopped()) {
            // Progress to next
            List<Coordinate> newRoute = new ArrayList<>(getRoute().size());
            // Skips the start item, so shifts all items left by one
            newRoute.addAll(getRoute().subList(1, getRoute().size()));
            newRoute.add(getRoute().get(0));
            setRoute(newRoute);
        } else {
            //Align agent, if aligned then moved towards target
            if(!isStopped() && this.adjustHeadingTowardsGoal()) {
                // From ms/s, but instead of dividing by 1 second, it's by one game step (fraction of a second)
                // We also check if we are closer than 1 move step; in which case
                double distToMove = Math.min(speed / Simulator.instance.getGameSpeed(), getCoordinate().getDistance(getCurrentDestination()));
                this.moveAlongHeading(distToMove);
                //this.moveAlongHeading(1);
            }
        }
    }

    @Override
    //In theory this shouldn't ever be called
    void performFlocking() {

    }

    /***
     * Finds the task with these coordinates, and assigns it to this agent
     * @param coords
     */
    public void setAllocatedTaskByCoords(List<Coordinate> coords) {
        Coordinate coordToUse;
        if (coords.size() == 1) {
            // Singleton => waypoint task, so represented as its only coord
            coordToUse = coords.get(0);
            try {
                setTempRoute(Collections.singletonList(taskController.findTaskByCoord(coordToUse).getCoordinate()));
            } catch (Exception e) {
                setRoute(coords);
                return;
            }
        } else {
            // Region or patrol task, represented by its vertices
            coordToUse = Coordinate.findCentre(coords.subList(0, coords.size() - 1));
            setTempRoute(coords);
        }
        setAllocatedTaskId(taskController.findTaskByCoord(coordToUse).getId());
    }

    /**
     * Allows an agent (Hub only) to remove a task from the programmer
     * @param coord Coordinate of the task to remove
     */
    public void tempRemoveTask(Coordinate coord){
        taskController.deleteTaskByCoords(coord);
    }

    public void manualSetTask(Coordinate c) {
        programmerHandler.manualSetTask(c);
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
     * Adjust heading of agent.
     * @return isAligned - Whether the agent is aligned or needs to continue rotating.
     */
    protected boolean adjustHeading(double angleToGoal) {
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

    protected List<Agent> senseNeighbours(double sensingRadius){
        return this.sensor.senseNeighbours(this, sensingRadius);
    }



    /**
     * Adjust heading of agent towards the average heading of its neighbours.
     * @return isAligned - Whether the agent is aligned or needs to continue rotating.
     */
    protected boolean adjustFlockingHeading(double senseRadius, double tooCloseRadius) {
        // I lifted this straight out of the AgentVirtual
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

        List<Agent> neighbours = this.sensor.senseNeighbours(this, senseRadius);

        if (neighbours.size() > 0) {

            for (Agent neighbour : neighbours) {
                double multiplier = 1;
                boolean isLeader = false;
                // This line below works for the programmed mode, checking if it's a leader
                if (neighbour instanceof AgentProgrammed) {
                    AgentProgrammed ap = (AgentProgrammed) neighbour;
                    if (ap.getType().equals("leader")) {
                        isLeader = true;
                    }
                }
                if (neighbour.getTask() != null || isLeader) {
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

            List<Agent> tooCloseNeighbours = this.sensor.senseNeighbours(this, tooCloseRadius);
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

    public String getBelievedModel() {
        return programmerHandler.getModel();
    }

    public boolean isLeader() {
        return programmerHandler.isLeader();
    }

    public Coordinate getHubLocation() {
        return programmerHandler.getHubPosition();
    }

    public double getSenseRange() {
        return programmerHandler.getSenseRange();
    }

    public double getNextRandom() {
        return random.nextDouble();
    }

    public void setCommunicationRange(double communicationRange) {
        programmerHandler.setCommunicationRange(communicationRange);
    }

    public ProgrammerHandler getProgrammerHandler() {
        return programmerHandler;
    }
}
