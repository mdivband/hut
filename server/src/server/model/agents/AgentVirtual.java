package server.model.agents;

import server.Simulator;
import server.model.Coordinate;
import server.model.Sensor;
import server.model.task.Task;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

public class AgentVirtual extends Agent {
    protected transient Logger LOGGER = Logger.getLogger(AgentVirtual.class.getName());
    protected transient Sensor sensor;

    private transient boolean alive = true;
    protected boolean goingHome = false;
    private double batteryVariance;

    private List<Task> taskQueue = new ArrayList<>();

    public AgentVirtual(String id, Coordinate position, Sensor sensor) {
        super(id, position, true);
        batteryVariance = 0.0002 - (Simulator.instance.getRandom().nextDouble() / 2500);
        //System.out.println("var = " + batteryVariance);
        this.sensor = sensor;
        setType("standard");
    }

    @Override
    public void step(Boolean flockingEnabled) {
        if (goingHome) {
            moveTowardsDestination();
            for (Agent a : sensor.senseNeighbours(this, 10.0)) {
                if (a instanceof Hub) {
                    goingHome = false;
                    System.out.println("Setting withpack now");
                    setType("withpack");
                    if (getRoute().size() <= 0) {
                        System.out.println("Stopping at home");
                        System.out.println(this);
                        stop();
                    }

                }
            }
            this.battery = this.battery > 0 ? this.battery - (unitTimeBatteryConsumption + batteryVariance + Simulator.instance.getRandom().nextDouble() / 5000) : 0;
        } else if (alive) {
            super.step(flockingEnabled);

            // TEMP - Incorporate some randomness  (base 0.0005
            this.battery = this.battery > 0 ? this.battery - (unitTimeBatteryConsumption + batteryVariance + Simulator.instance.getRandom().nextDouble() / 5000) : 0;
        } else {
            System.out.println("last cond " + this);
        }

        //Simulate things that would be done by a real drone
        if (!isTimedOut())
            heartbeat();
    }

    @Override
    void moveTowardsDestination() {
        //Align agent, if aligned then moved towards target
        if(!isStopped() && this.adjustHeadingTowardsGoal())
            this.moveAlongHeading(1);
    }

    @Override
    void performFlocking() {
        //Align agent, if aligned then moved towards target
        if(!isStopped() && this.adjustFlockingHeading())
            this.moveAlongHeading(1);
    }

    /**
     * Adjust heading of agent towards the heading that will take it towards its goal.
     * @return isAligned - Whether the agent is aligned or needs to continue rotating.
     */
    protected boolean adjustHeadingTowardsGoal() {
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

        List<Agent> neighbours = this.sensor.senseNeighbours(this, 50.0);

        if (neighbours.size() > 0) {

            for (Agent neighbour : neighbours) {
                double multiplier = 1;
                if (neighbour.getTask() != null) {
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

            List<Agent> tooCloseNeighbours = this.sensor.senseNeighbours(this, 5.0);
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

    public void goHome() {
        setWorking(false);
        goingHome = true;
        setRoute(Collections.singletonList(Simulator.instance.getState().getHubLocation()));
    }

    public boolean isGoingHome() {
        return goingHome;
    }

    public void setGoingHome(boolean goingHome) {
        this.goingHome = goingHome;
    }

    public void stopGoingHome() {
        goingHome = false;
    }

    @Override
    public String toString() {
        return "Agent{" +
                "id=" + getId() +
                "heading=" + heading +
                ", route=" + route +
                ", allocatedTaskId='" + getAllocatedTaskId() + '\'' +
                ", working=" + isWorking() +
                ", stopped=" + isStopped() +
                ", isSearching=" + getSearching() +
                '}';
    }

    public boolean isHome() {
        return getCoordinate().getDistance(Simulator.instance.getState().getHubLocation()) < 15;

    }

    public void kill() {
        goHome();
        if (getAllocatedTaskId() != null && !getAllocatedTaskId().equals("")) {
            getTask().getAgents().remove(this);
        }
        Simulator.instance.getAllocator().removeFromTempAllocation(getId());
        setAllocatedTaskId(null);
        setSearching(false);
        alive = false;
        setType("leader");
    }

    public boolean isAlive() {
        return alive;
    }

    public void charge() {
        battery += unitTimeBatteryConsumption * 10;
        if (battery >= 1) {
            battery = 1;
            alive = true;
            setWorking(false);
            //setSearching(true);
            resume();
            setType("standard");
        }
        if (isTimedOut()) {
            heartbeat();
        }
    }

    public void addTaskToQueue(Task task) {
        taskQueue.add(task);
    }

    public Task getNextTaskFromQueue() {
        if (!taskQueue.isEmpty()) {
            Task taskToReturn = taskQueue.get(0);
            taskQueue.remove(0);
            return taskToReturn;
        }
        return null;
    }

    public List<Task> getTaskQueue() {
        return taskQueue;
    }
}
