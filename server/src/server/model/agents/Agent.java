package server.model.agents;

import server.Simulator;
import server.model.Coordinate;
import server.model.MObject;
import server.model.hazard.Hazard;
import server.model.task.Task;

import java.io.Serializable;
import java.util.*;
import java.util.logging.Logger;

/**
 * @author Feng Wu, Yuai Liu
 */
public abstract class Agent extends MObject implements Serializable {

    private static final Logger LOGGER = Logger.getLogger(Agent.class.getName());

    private static final long serialVersionUID = 5561040348988016571L;
    static final float unitTurningAngle = 0.1F; //Radians
    //static final float unitTimeBatteryConsumption = 0.00025F;
    static final float unitTimeBatteryConsumption = 0.000025F;
    private static final double EPS = 1e-5;

    //Used in client
    protected double altitude;
    protected double battery;
    protected double heading;
    private boolean manuallyControlled = false;
    protected final List<Coordinate> route;
    private final List<Coordinate> tempRoute;
    protected double speed;
    private String allocatedTaskId;
    private double timeInAir;
    private boolean simulated;
    private boolean timedOut;
    private boolean working;

    protected String type;
    protected boolean visible = true;

    //Used in server but not in client
    private transient long lastHeartbeat;
    private transient boolean startSearching;
    private transient boolean stopped;
    protected List<Task> taskQueue =  new ArrayList<>();
    protected List<Coordinate> coordQueue = new ArrayList<>();

    public Agent(String id, Coordinate position, boolean simulated) {
        super(id, position);

        this.simulated = simulated;

        // Sensible range 5-20m/s e.g.:
        // DJI Phantom 4 Pro V2.0 S-Mode = 20.1168 m/s
        // DJI Mavic 3 C-Mode = 5 m/s
        speed = 5.0;
        heading = 0.0;
        battery = 1.0;
        altitude = 3.0;
        timeInAir = 0.0;
        route = new Vector<>();
        tempRoute = new Vector<>();
        startSearching = false;
        working = false;
        allocatedTaskId = "";
        timedOut = false;
        type = "standard";

        this.lastHeartbeat = System.currentTimeMillis();
    }

    /**
     * Move an agent towards its current destination.
     */
    abstract void moveTowardsDestination();

    /**
     * Move an agent based on the average headings of its neighbours.
     */
    abstract void performFlocking();

    /**
     * Step an agent for this tick
     */
    public void step(Boolean flockingEnabled) {
        Task task = this.getTask();
        if (task != null) {
            //Ensure agent's goal is set to task coordinate in case task is moved
            if(task.getType() == Task.TASK_PATROL || task.getType() == Task.TASK_REGION) {
                if (!isWorking() && route.size() > 0) {
                    //TODO this is very inefficient so have removed it for now. Only need to recalculate point if task moves.
                    //route.set(route.size() - 1, ((PatrolTask) task).getNearestPointAbsolute(this));
                }
            } else if(route.size() > 0) {
                route.set(route.size() - 1, task.getCoordinate());
            }

            //Move agents
            if (!route.isEmpty() && !isCurrentDestinationReached()) {
                if (!getSearching()) {
                    moveTowardsDestination();
                    timeInAir += 0.2;
                }
                if (isCurrentDestinationReached() && this.route.size() > 1)
                    this.route.remove(0);
            }
        } else if (flockingEnabled){
            performFlocking();
        }

        //Check for hazard hits
        for(Hazard hazard : Simulator.instance.getState().getHazards()) {
            if(hazard.inRange(this.getCoordinate()))
                Simulator.instance.getState().addHazardHit(hazard.getType(), this.getCoordinate());
        }

        //Always add 'no hazard' to track explored areas.
        Simulator.instance.getState().addHazardHit(Hazard.NONE, this.getCoordinate());
    }

    public void incrementTimeInAir(){
        timeInAir += 0.2;
    }

    /**
     * Stop an agent - should maintain its current position.
     */
    public void stop() {
        this.stopped = true;
    }

    /**
     * Un-stop an agent - should now move towards its goal.
     */
    public void resume() {
        this.stopped = false;
    }

    public boolean isStopped() {
        return this.stopped;
    }

    public boolean isSimulated() {
        return simulated;
    }

    public void heartbeat() {
        this.lastHeartbeat = System.currentTimeMillis();
        if(this.isTimedOut()) {
            LOGGER.info("Reconnected with agent " + this.getId());
            this.setTimedOut(false);
        }
    }

    public long getMillisSinceLastHeartbeat() {
        return System.currentTimeMillis() - this.lastHeartbeat;
    }

    public boolean isManuallyControlled() {
        return manuallyControlled;
    }

    public void toggleManualControl() {
        manuallyControlled = !manuallyControlled;
    }

    public void setWorking(boolean workingStatus) {
        this.working = workingStatus;
    }

    public boolean isWorking() {
        return this.working;
    }

    public double getTime(Coordinate start, Coordinate target) {
        return this.predictPathLength(start, target, this.getSpeed()) / (this.speed + 1e-6);
    }

    public double getEnergyConsumption(Coordinate start, Coordinate target) {
        double time = this.getTime(start, target);
        return time * unitTimeBatteryConsumption;
    }

    public void setHeading(double heading) {
        this.heading = heading;
    }

    Coordinate getCurrentDestination() {
        return this.route.get(0);
    }

    Coordinate getFinalDestination() {
        return this.route.get(this.route.size() - 1);
    }

    public String getAllocatedTaskId() {
        return allocatedTaskId;
    }

    public void setAllocatedTaskId(String taskId) {
        this.allocatedTaskId = taskId;
    }

    public Task getTask() {
        return allocatedTaskId != null ? Simulator.instance.getState().getTask(this.allocatedTaskId) : null;
    }

    public Collection<Task> getAllTasks(){
        return Simulator.instance.getState().getTasks();
    }

    public double getSpeed() {
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    public double getAltitude() {
        return altitude;
    }

    public void setAltitude(double altitude) {
        this.altitude = altitude;
    }

    public double getHeading() {
        return heading;
    }

    public void setSearching(boolean searching) {
        this.startSearching = searching;
    }

    public boolean getSearching() {
        return this.startSearching;
    }

    public List<Coordinate> getRoute() {
        return route;
    }

    public void addToRoute(Coordinate coord) {
        List<Coordinate> rt;
        if (this.route.isEmpty()) {
            rt = new ArrayList<>();
        } else {
            rt = getRoute();
        }
        rt.add(coord);
        setRoute(rt);
    }

    public void setRoute(List<Coordinate> route) {
        synchronized (this.route) {
            this.route.clear();
            this.route.addAll(route);
        }
    }

    public void prependToRoute(Coordinate location) {
        synchronized (this.route) {
            List<Coordinate> currentRoute = new ArrayList<>(route);
            this.route.clear();
            if (!currentRoute.get(0).equals(location)) {
                this.route.add(location);
            }
            this.route.addAll(currentRoute);
        }
    }

    public void clearRoute() {
        synchronized (this.route) {
            this.route.clear();
        }
    }

    public List<Coordinate> getTempRoute() {
        return tempRoute;
    }

    public void setTempRoute(List<Coordinate> route) {
        synchronized (this.tempRoute) {
            this.tempRoute.clear();
            this.tempRoute.addAll(route);
        }
    }

    public void clearTempRoute() {
        synchronized (this.tempRoute) {
            this.tempRoute.clear();
        }
    }

    protected double getEPS(){
        return EPS;
    }

    public boolean isCurrentDestinationReached() {
        return isReached(this.getCurrentDestination());
    }

    public boolean isFinalDestinationReached() {
        synchronized (this.route) {
            return isReached(this.getFinalDestination());
        }
    }

    protected boolean isReached(Coordinate goal) {
        if(goal == null)
            return true;
        Coordinate position = this.getCoordinate();
        return Math.abs(goal.getLatitude() - position.getLatitude()) < EPS &&
                Math.abs(goal.getLongitude() - position.getLongitude()) < EPS;
    }

    private void onTimeOut() {
        Simulator.instance.getAllocator().moveToDroppedAllocation(this.getId());
        //Simulator.instance.changeView(true);
    }

    public boolean isTimedOut() {
        return timedOut;
    }

    public void setTimedOut(boolean timedOut) {
        if(!this.timedOut && timedOut)
            this.onTimeOut();
        this.timedOut = timedOut;
        if(!timedOut)
            heartbeat();
    }

    public void setBattery(double battery) {
        this.battery = battery;
    }

    public double getBattery() {
        return battery;
    }

    // This method returns an approximation of the length of a planned path
    // It only calcualtes the coordinates that the agent will cover when it turnning its angle
    // Then the method just estimates the euclidean distance between the point at which the agent stops turning and the goal
    public double predictPathLength(Coordinate start, Coordinate goal, double speed) {
        int turningRouteLength = 0;

        Coordinate aCoordinate = new Coordinate(start.getLatitude(), start.getLongitude());
        double aHeading = this.heading;

        double targetDir = aCoordinate.getAngle(goal);
        double angle = aHeading - targetDir;

        // Currently the speed of the agent is fixed, so when the goal is very close to the agent,
        // the path is very likely to be a circle around the goal so the turnning loop becomes infinity.
        // So when the goal is very close to the agent, then just estimate the euclidean distance
        // (this could affect the quality of the allocation plan)
        if (((Math.abs(goal.getLatitude() - start.getLatitude()) > 0.01) &&
                (Math.abs(goal.getLongitude() - start.getLongitude()) < 0.01))) {

            while (Math.abs(angle) > 0.02) {

                targetDir = aCoordinate.getAngle(goal);

                angle = aHeading - targetDir;

                Double updateLatitude;
                Double updateLongitude;

                if (Math.abs(angle) > 0.02) {
                    if (targetDir > 0) {
                        if (aHeading > 0) {
                            if (targetDir > aHeading) {
                                aHeading = aHeading + unitTurningAngle;
                            } else {
                                aHeading = aHeading - unitTurningAngle;
                            }
                        } else if (aHeading < 0 && (targetDir - aHeading > Math.PI)) {
                            aHeading = (aHeading - unitTurningAngle);
                            if (aHeading < 0 - Math.PI) {
                                aHeading = Math.PI + (aHeading % Math.PI);
                            }
                        } else {
                            aHeading = aHeading + unitTurningAngle;
                        }
                    } else if (targetDir <= 0) {
                        if (aHeading < 0) {
                            if (targetDir > aHeading) {
                                aHeading = aHeading + unitTurningAngle;
                            } else {
                                aHeading = aHeading - unitTurningAngle;
                            }
                        } else if (aHeading > 0 && (aHeading - targetDir > Math.PI)) {
                            aHeading = aHeading + unitTurningAngle;
                            if (aHeading > Math.PI) {
                                aHeading = 0 - Math.PI + (aHeading % Math.PI);
                            }
                        } else {
                            aHeading = aHeading - unitTurningAngle;
                        }
                    }


                    updateLatitude = aCoordinate.getLatitude() + speed * (Math.sin(aHeading) / (60.0 * 1852.0));
                    updateLongitude = aCoordinate.getLongitude() + speed * (Math.cos(aHeading) / (60.0 * 1852.0));

                    turningRouteLength += 1;
                    aCoordinate.setLatitude(updateLatitude);
                    aCoordinate.setLongitude(updateLongitude);
                }

            }

            return turningRouteLength * speed + aCoordinate.getDistance(goal);
        } else {
            return start.getDistance(goal);
        }
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    @Override
    public String toString() {
        return "Agent{" +
                "id=" + getId() +
                "heading=" + heading +
                ", route=" + route +
                ", allocatedTaskId='" + allocatedTaskId + '\'' +
                ", working=" + working +
                ", stopped=" + stopped +
                '}';
    }
}
