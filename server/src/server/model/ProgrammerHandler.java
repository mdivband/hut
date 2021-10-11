package server.model;

import server.model.task.Task;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;

public class ProgrammerHandler implements Serializable {
    AgentProgrammed agent;

    private final transient Logger LOGGER = Logger.getLogger(AgentProgrammed.class.getName());

    private final transient AgentProgrammer agentProgrammer;

    private HashMap<String, Coordinate> neighbours;

    private HashMap<String, Coordinate> tasks;

    /***
     * Constructor. Connects the agent to the programmer s.t. this class behaves akin to an MVC controller
     * @param connectedAgent The agent that this handler controls
     */
    public ProgrammerHandler(AgentProgrammed connectedAgent){
        this.agent = connectedAgent;
        neighbours = new HashMap<>();
        tasks = new HashMap<>();
        agentProgrammer = new AgentProgrammer(this);
    }

    /***
     * Called at every time step, we pass through to the programmer
     */
    public void step() {
        if (agent.getNetworkId().equals("")) {
            // Must perform setup on the first step, otherwise they can't find each other
            //agent.generateID();
            agent.setNetworkID(agent.generateRandomTag());
            agentProgrammer.setup();
        } else {
            agentProgrammer.step();
            declareSelf(100);
        }
    }

    /***
     * Returns the ID of this agent
     * @return ID
     */
    protected String getId(){
        return agent.getId();
    }

    /***
     * Moves the agent along its current heading for the given distance
     * @param i The distance to move
     */
    protected void moveAlongHeading(int i) {
        agent.moveAlongHeading(i);
    }

    /***
     * Default move command; assumes distance to move is 1 unit
     */
    protected void moveAlongHeading() {
        agent.moveAlongHeading(1);
    }

    /***
     * Stop the agent when following a route
     */
    protected void stop() {
        agent.stop();
    }

    /***
     * Resumes the agent when following a route
     */
    protected void resume(){
        agent.resume();
    }

    /***
     * Checks whether the agent is currently stopped when following a route
     * @return stopped
     */
    protected boolean isStopped(){
        return agent.isStopped();
    }

    /***
     * Estimates the time remaining for this agent to complete the passed route
     * @param start Start Coordinate
     * @param target Target Coordinate
     * @return
     */
    protected double getTimeRemaining(Coordinate start, Coordinate target){
        return agent.getTime(start, target);
    }

    /***
     * Returns the current destination set for this agent (useful when following a route)
     * @return Destination as a coordinate
     */
    protected Coordinate getCurrentDestination(){
        return agent.getCurrentDestination();
    }

    /***
     * Returns the final destination set for this agent (useful when following a route)
     * @return Destination as a coordinate
     */
    protected Coordinate getFinalDestination(){
        return agent.getFinalDestination();
    }

    /***
     * Sets the speed of the agent
     * @param speed Speed of the agent
     */
    protected void setSpeed(double speed){
        agent.setSpeed(speed);
    }

    /***
     * Returns the speed of the agent
     * @return Speed of the agent
     */
    protected double getSpeed(){
        return agent.getSpeed();
    }

    /***
     * Sets the altitude of the agent
     * @param altitude Altitude of the agent
     */
    protected void setAltitude(double altitude){
        agent.setAltitude(altitude);
    }

    /***
     * Returns the altitude of this agent
     * @return Altitude of the agent
     */
    protected double getAltitude(){
        return agent.getAltitude();
    }

    /***
     * Sets the heading of this agent
     * NOTE: this may need editing, as the simulator typically makes rotation take time, while this bypasses that req'ment
     * @param heading The angle in degrees
     */
    protected void setHeading(double heading) {
        agent.setHeading(heading);
    }

    /***
     * Returns the current heading of this agent in degrees
     * @return Heading in degrees
     */
    protected double getHeading() {
        return agent.getHeading();
    }

    /***
     * Sets the "searching" status of this agent
     * @param state Whether the agent is searching
     */
    protected void setSearching(Boolean state){
        agent.setSearching(state);
    }

    /***
     * Returns the "searching" status of this agent
     * @return Whether the agent is searching
     */
    protected Boolean getSearching(){
        return agent.getSearching();
    }

    //TODO consider whether the programmer should handle route movement, or if that should be automatic
    /***
     * Sets the route for this agent.
     * @param coords The list of waypoints in the planned route
     */
    protected void setRoute(List<Coordinate> coords){
        agent.setRoute(coords);
    }


    protected void addToRoute(Coordinate coord){
        if (coord != null) {
            agent.addToRoute(coord);
        }
    }

    /***
     * Returns the route of the current agent
     * @return Routes as a list of coordinates
     */
    protected List<Coordinate> getRoute(){
        return agent.getRoute();
    }

    public void followRoute() {
        setSearching(true);
        agent.moveTowardsDestination();

    }


    /***
     * Senses all other agents within the given radius, and returns their IDs
     * @param radius The radius to check within
     * @return Neighbours as a list of Ids
     */
    /*
    protected List<String> getNeighboursAsIds(double radius){
        List<Agent> neighbours = agent.senseNeighbours(radius);
        List<String> ids = new ArrayList<>();
        for(Agent n : neighbours) {
            ids.add(n.getId());
        }
        return ids;
    }

     */


    /***
     * Senses all other agents within the given radius, and returns their locations
     * @param radius The radius to check within
     * @return Neighbours as a list of coordinates
     */
    protected List<Coordinate> getNeighboursAsAbsoluteCoords(double radius){
        List<Agent> neighbours = agent.senseNeighbours(radius);
        List<Coordinate> coords = new ArrayList<>();
        for(Agent n : neighbours) {
            coords.add(n.getCoordinate());
        }
        return coords;
    }

    /***
     * Senses all other agents within the given radius, and returns their locations relative to this agent
     * @param radius The radius to check within
     * @return Neighbours as a list of coordinates, relative to this agent
     */
    protected List<Coordinate> getNeighboursAsRelativeCoords(double radius){
        List<Agent> neighbours = agent.senseNeighbours(radius);
        List<Coordinate> coords = new ArrayList<>();
        for(Agent n : neighbours) {
            double relLat = n.getCoordinate().getLatitude() - agent.getCoordinate().getLatitude();
            double relLng = n.getCoordinate().getLongitude() - agent.getCoordinate().getLongitude();

            coords.add(new Coordinate(relLat, relLng));
        }
        return coords;
    }


    /***
     * Senses all other agents within the given radius, and returns them as agents
     * This allows you to use the Agent object, although bear in mind that this may be less clear
     //* @param radius The radius to check within
     * @return Neighbours as a list of Agents
     */
    /*
    protected List<Agent> getNeighboursAsAgentObjects(double radius){
        return agent.senseNeighbours(radius);
    }

     */

    /*
    private double getEuclideanDistanceToAgent(String agentID){
        Agent target = getAgentById(agentID);
        if(target != null) {
            return (agent.getCoordinate().getDistance(target.getCoordinate()));  // Calculates real distance in metres
        } else {
            LOGGER.severe("Attempted to calculate distance to Agent with ID \"" + agentID + "\". This was not found");
            return -1;  // This is treated as an error condition
        }
    }

    protected double getEuclideanDistanceToCoordinate(Coordinate coord){
        return agent.getCoordinate().getDistance(coord);
    }

    protected double getAngleToAgent(String agentID){
        Agent target = getAgentById(agentID);
        if (target!=null) {
            return agent.getCoordinate().getAngle(target.getCoordinate());
        } else {
            LOGGER.severe("Attempted to calculate angle to Agent with ID \"" + agentID + "\". This was not found");
            return -1;  // This is treated as an error condition
        }
    }

     */

    /***
     * It is important that this remains private. We shouldn't allow simulated agents to access other agents directly
     * This is needed for "under the hood" functionality but don't use it otherwise
     * @return
     */
    private Agent agentAt(Coordinate coordinate){
        List<Agent> neighbours = agent.senseNeighbours(1000000);
        for (Agent n : neighbours){
            if (n.getCoordinate().equals(coordinate)){
                return n;
            }
        }
        return null;
    }

    /***
     * It is important that this remains private. We shouldn't allow simulated agents to access other agents directly
     * This is needed for "under the hood" functionality but don't use it otherwise
     * @return
     */
    private Agent getAgentByNetworkId(String networkID){
        List<Agent> neighbours = agent.senseNeighbours(100000);  // Note that this is not infinite, perhaps it should be
        for(Agent n : neighbours) {
            try {
                // For now, we just cast it to a programmed agent. In future, we may need to implement message handling for all agents
                AgentProgrammed ap = (AgentProgrammed) n;
                if (ap.getNetworkId().equals(networkID)){
                    return n;
                }
            } catch (Exception e) {
                LOGGER.severe("Could not find agent: \"" + networkID + "\"");
            }

        }
        return null;
    }

    public void printNeighbours(){
        LOGGER.severe("I am " + agent.getNetworkId());
        for (var entry : neighbours.entrySet()) {
            LOGGER.severe("    ->"+entry.getKey() + "/" + entry.getValue().toString());
        }
        LOGGER.severe("");
    }

    public void declareSelf(int radius) {
        String msg = "HS_GREET;"+agent.getNetworkId()+";"+agent.getCoordinate().getLatitude()+","+agent.getCoordinate().getLongitude();
        broadcast(msg, radius);
    }

    protected void broadcast(String message, int radius) {
        //updateNeighbours(radius);
        List<Agent> neighbours = agent.senseNeighbours(radius);
        for (Agent n : neighbours) {
            try {
                // For now, we just cast it to a programmed agent. In future, we may need to implement message handling for all agents
                AgentProgrammed ap = (AgentProgrammed) n;
                ap.receiveMessage(message);
            } catch (Exception e) {
                LOGGER.severe("Unreceived message. Probably due to this not being a programmed agent.");
            }
        }
    }

    private void updateNeighbours(int radius) {
        for (Agent n : agent.senseNeighbours(radius)) {
            try {
                // For now, we just cast it to a programmed agent. In future, we may need to implement message handling for all agents
                AgentProgrammed ap = (AgentProgrammed) n;
                String id = ap.getNetworkId();
                if(!id.equals("")) {
                    Coordinate coord = ap.getCoordinate();
                    neighbours.put(id, coord);
                }
            } catch (Exception e) {
                LOGGER.severe("Unreceived message. Probably due to this not being a programmed agent.");
            }
        }

    }

    protected void receiveMessage(String message) {
        // TODO as of a recent java update, we can use regex-based case statements here in future
        try {
            if (message.contains("HS_GREET;")){
                // Handshake introduction
                String id = message.split(";")[1];
                String x = message.split(";")[2].split(",")[0];
                String y = message.split(";")[2].split(",")[1];
                //Coordinate coord = getAgentByNetworkId(id).getCoordinate();
                neighbours.put(id, new Coordinate(Double.parseDouble(x), Double.parseDouble(y)));
                // Note that we should only register neighbours based on messages. This includes coordinates, just in
                // case we want to model error etc

            } else if (message.contains("HS_REPLY")){
                // Handshake response, currently ignored

            } else if (message.contains("TASK_WAYPOINT")) {
                String x = message.split(";")[1].split(",")[0];
                String y = message.split(";")[1].split(",")[1];
                Coordinate thisCoord = new Coordinate(Double.parseDouble(x), Double.parseDouble(y));
                if (!tasks.containsValue(thisCoord)) {
                    tasks.put(agent.generateRandomTag(), thisCoord);
                }


            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public HashMap<String, Coordinate> getTasks() {
        return tasks;
    }

    public String getNearestTask() {
        String bestId = "";
        double shortestDist = 100000;

        for (var entry : tasks.entrySet()) {
            double dist = agent.getCoordinate().getDistance(entry.getValue());
            if (dist < shortestDist) {
                shortestDist = dist;
                bestId = entry.getKey();
            }
        }

        return bestId;
    }

    public Coordinate getTaskById(String nearestTask) {
        return tasks.get(nearestTask);
    }

    public void setTask(Coordinate taskCoord) {
        agent.setAllocatedTaskByCoord(taskCoord);
    }

    public void tempPlaceNewTask(Coordinate coordinate) {
        agent.tempPlaceNewTask(coordinate);
    }


    /*
    public List<ATask> getTasks() {
        List<ATask> tasks = new ArrayList<>();
        for(Task t: agent.getAllTasks()){
            tasks.add(new ATask(t));
        }
        return tasks;
    }

    public ATask getNearestTask() {
        Task nearest = null;
        double minDist = 100000;
        for(Task t: agent.getAllTasks()){
            double thisDist = agent.getCoordinate().getDistance(t.getCoordinate());
            if (thisDist < minDist) {
                nearest = t;
                minDist = thisDist;
            }
        }
        if (nearest != null) {
            return new ATask(nearest);
        } else {
            return null;
        }

    }



    public void setTask(ATask t) {
        agent.setAllocatedTaskId(t.task.getId());
        LOGGER.severe("Set " + agent.getId() + " to " + agent.getTask());
        List<Coordinate> route = new ArrayList<>();
        route.add(t.task.getCoordinate());
        agent.setRoute(route);


        putInTempAllocation(String agentId, String taskId)
        //agent.setSearching(true);
        //agent.setWorking(true);
        //agent.resume();
        //t.task.addAgent(agent);
    }

*/
    class ATask {
        Task task;

        public ATask(Task task){
            this.task = task;
        }
    }



}
