package server.model;

import server.controller.TaskController;
import server.model.task.Task;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;

public class AgentProgrammed extends Agent {
    private transient String networkID = "";
    private transient Random random;
    private transient Logger LOGGER = Logger.getLogger(AgentVirtual.class.getName());

    private transient Sensor sensor;
    private transient TaskController taskController;
    private transient ProgrammerHandler programmerHandler;

    public AgentProgrammed(String id, Coordinate position, Sensor sensor) {
        super(id, position, true);
        this.programmerHandler = new ProgrammerHandler(this);
        this.sensor = sensor;
    }

    public AgentProgrammed(String id, Coordinate position, Sensor sensor, Random random, TaskController taskController) {
        super(id, position, true);
        this.random = random;
        this.sensor = sensor;
        this.taskController = taskController;
        this.programmerHandler = new ProgrammerHandler(this);
    }

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
    }

    @Override
    //In theory this shouldn't ever be called
    void moveTowardsDestination() {
        //Align agent, if aligned then moved towards target
        if(!isStopped() && this.adjustHeadingTowardsGoal())
            this.moveAlongHeading(1);
    }

    @Override
    //In theory this shouldn't ever be called
    void performFlocking() {

    }

    public void setAllocatedTaskByCoord(Coordinate coord) {
        // TODO Does this assign it in the task handling??
        setAllocatedTaskId(taskController.findTaskByCoord(coord).getId());
    }

    public void tempPlaceNewTask(Coordinate coord) {
        // TODO this is temporary and is a bit messy. It allows us to create tasks from the programmer, but in future
        // the user will probably directly create these tasks from the main view
        taskController.createTask(Task.TASK_WAYPOINT, coord.latitude, coord.longitude);
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


}
