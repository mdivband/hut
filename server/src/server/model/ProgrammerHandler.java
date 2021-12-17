package server.model;

import com.google.gson.Gson;
import server.model.task.Task;
import tool.GsonUtils;

import java.io.Serializable;
import java.util.*;
import java.util.logging.Logger;

/***
 * Handles the drone controller, like an API. Mostly helper functions to pass throughout the intricacies of the code
 * structure and convert things to more easily understandable formats
 */
public class ProgrammerHandler implements Serializable {
    // To make JSON output more usable. These may be too much info
    private final String simID;
    private String networkID;
    private Coordinate believedPosition;

    private final int SENSE_RANGE = 200; // The max (and default) radius used for sensing neighbours etc
    private int pingCounter = 0;
    private int resetPingCounter = 0;
    private final int resetTimeout = 300; // (50x200ms = every 60 seconds)
    private final int pingTimeout = 50; // (5x200ms = every 10 seconds)

    private final transient Logger LOGGER = Logger.getLogger(AgentProgrammed.class.getName());
    protected transient AgentProgrammed agent;
    private final transient AgentProgrammer agentProgrammer;

    private HashMap<String, Position> neighbours;
    private List<Coordinate> currentTask = new ArrayList<>();
    private final HashMap<List<Coordinate>, List<String>> tasks;
    private final List<List<Coordinate>> completedTasks;

    private Coordinate home = new Coordinate(50.9295, -1.409); // TODO make this inferred from a hub announcement
    private boolean isGoingHome = false;

    private int dbgCounter = 0;
    private int forgivenessCounter = 0;

    /***
     * Constructor. Connects the agent to the programmer s.t. this class behaves akin to an MVC controller
     * @param connectedAgent The agent that this handler controls
     */
    public ProgrammerHandler(AgentProgrammed connectedAgent){
        this.agent = connectedAgent;
        neighbours = new HashMap<>();
        tasks = new HashMap<>();
        completedTasks = new ArrayList<>();
        agentProgrammer = new AgentProgrammer(this);
        simID = agent.getId();
    }

    /***
     * Called at every time step, we set up on the first run, then pass through to the programmer after
     */
    public void step() {
        believedPosition = agent.getCoordinate();
        if (agent.getNetworkId().equals("")) {
            // Must perform setup on the first step, otherwise they can't find each other
            agent.setNetworkID(agent.generateRandomTag());
            networkID = agent.getNetworkId();
            declareSelf(SENSE_RANGE);
            agentProgrammer.setup();
        } else if (pingCounter >= pingTimeout) {
            declareSelf(SENSE_RANGE);
            pingCounter = 0;
        }
        pingCounter++;
        if (resetPingCounter >= resetTimeout) {
            clearNeighbours();
            //declareSelf(SENSE_RANGE);
            resetPingCounter = 0;
        }
        resetPingCounter++;


        agentProgrammer.step();
    }

    /***
     * Because the receiver (base station) inherits from this class, we use a function here just for that class
     */
    public void baseStep() {
        if (agent.getNetworkId().equals("")) {
            agent.visualType = "hub";
            // Must perform setup on the first step, otherwise they can't find each other
            agent.setNetworkID(agent.generateRandomTag());
            declareSelf(SENSE_RANGE);
            agentProgrammer.setup();
        } else if (pingCounter >= pingTimeout) {
            declareSelf(SENSE_RANGE); // This must be separate, as order matters for the first case
            broadcastTasks();
            pingCounter = 0;
        }
        pingCounter++;

    }

    protected Coordinate getPosition(){
        return agent.getCoordinate();
    }

    /***
     * Broadcasts all known tasks, including completed ones to the network
     * There is a known issue here with concurrent modification when adding tasks during runtime. It should be handled
     * and will be fixed in future
     */
    private void broadcastTasks(){
        // TODO fix this concurrent modification exception that triggers when task is placed during a completion step
        try {
            for (var entry : tasks.entrySet()) {
                // Assuming that completion has been correctly pruned out
                StringBuilder sb = new StringBuilder();

                if (entry.getKey().size() == 1) {
                    sb.append("TASK_WAYPOINT");
                } else {
                    sb.append("TASK_REGION");
                }

                List<Coordinate> coords = entry.getKey();
                for (Coordinate c : coords) {
                    sb.append(";");
                    sb.append(c.latitude);
                    sb.append(",");
                    sb.append(c.longitude);
                }
                broadcast(sb.toString(), SENSE_RANGE);

            }

            for (List<Coordinate> tsk : completedTasks) {
                // Assuming that completion has been correctly pruned out
                StringBuilder sb = new StringBuilder();

                if (tsk.size() == 1) {
                    sb.append("COMPLETED_WAYPOINT");
                } else {
                    sb.append("COMPLETED_REGION");
                }

                for (Coordinate c : tsk) {
                    sb.append(";");
                    sb.append(c.latitude);
                    sb.append(",");
                    sb.append(c.longitude);
                }
                broadcast(sb.toString(), SENSE_RANGE);
            }
        } catch (Exception e) {
            LOGGER.severe("Concurrent modification exception for the task list. This is a known error and needs fixing");
        }
    }

    /***
     * Returns the ID of this agent
     * @return ID
     */
    protected String getId(){
        return agent.getNetworkId();
    }

    /***
     * Moves the agent along its current heading for the given distance
     * @param i The distance to move
     */
    protected void moveAlongHeading(double i) {
        agent.moveAlongHeading(i);
    }

    /***
     * Default move command; assumes distance to move is 1 unit
     */
    protected void moveAlongHeading() {
        agent.moveAlongHeading(1);
    }

    /***
     * Stop the agent
     */
    protected void stop() {
        agent.stop();
    }

    /***
     * Resumes the agent
     */
    protected void resume(){
        agent.resume();
    }

    protected void cancel(){
        tasks.get(currentTask).remove(getId());
        currentTask = new ArrayList<>();
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
     * @return time remaining
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

    /***
     * Sets the route for this agent.
     * @param coords The list of waypoints in the planned route
     */
    protected void setRoute(List<Coordinate> coords){
        agent.setRoute(coords);
    }

    private void setTempRoute(List<Coordinate> coords) {
        agent.setTempRoute(coords);
    }


    /***
     * Adds the given coordinate to the route planned for this agent
     * @param coord The coordinate object to add
     */
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

    /***
     * Makes the agent follow the route that is currently set
     */
    public void followRoute() {
        agent.moveTowardsDestination();
    }



    /***
     * Completes the task currently set. Also sends this completion message to the network automatically, and adds it
     * to the set of tasks that it will report as complete from now on
     */
    protected void completeTask(){

        List<Coordinate> coords = currentTask;

        tasks.remove(currentTask);

        if (agent instanceof Hub) {
            agent.tempRemoveTask(currentTask);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("COMPLETED");
        for (Coordinate c : coords) {
            sb.append(";");
            sb.append(c.latitude);
            sb.append(",");
            sb.append(c.longitude);

        }
        broadcast(sb.toString(), SENSE_RANGE);
    }

    protected void completeTask(Coordinate task){
        List<Coordinate> thisTask = new ArrayList<>();
        thisTask.add(task);
        tasks.remove(thisTask);

        //if (agent instanceof AgentReceiver) {
        //    agent.tempRemoveTask(thisTask);
        //}

        StringBuilder sb = new StringBuilder();
        sb.append("COMPLETED");
        for (Coordinate c : thisTask) {
            sb.append(";");
            sb.append(c.latitude);
            sb.append(",");
            sb.append(c.longitude);

        }
        broadcast(sb.toString(), SENSE_RANGE);
    }

    /***
     * Handles a completion message. Adds this task to the completed list
     * @param coords
     */
    private void receiveCompleteTask(List<Coordinate> coords){
        if (!completedTasks.contains(coords)) {
            completedTasks.add(coords);
        }

        tasks.remove(coords);
        //if (agent instanceof AgentReceiver) {
        //    agent.tempRemoveTask(coords);
        //}
        if (currentTask.equals(coords)) {
            currentTask = new ArrayList<>();
            stop();
        }
    }

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
     * It is important that this remains private. We shouldn't allow simulated agents to access other agents directly
     * This may be needed for "under the hood" functionality but don't use it otherwise
     * @return The agent at the given coordinate
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
     * This may be needed for "under the hood" functionality but don't use it otherwise
     * @return The agent at with the given networkID
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

    /***
     * Just for diagnostics. Prints the neighbours of this agent to the log
     */
    public void printNeighbours(){
        LOGGER.severe("I am " + agent.getNetworkId());
        for (var entry : neighbours.entrySet()) {
            LOGGER.severe("    ->"+entry.getKey() + "/" + entry.getValue().toString());
        }
        LOGGER.severe("");
    }

    /***
     * Declares this agent to its neighbours. Sends its full positional information (to the best of its knowledge),
     * and, if it has a current task assigned, shares that too.
     * @param radius the radius to broadcast too
     */
    public void declareSelf(int radius) {
        if (currentTask.size() == 0) {
            String msg = "HS_GREET;" + agent.getNetworkId() + ";"
                    + agent.getCoordinate().getLatitude() + ","
                    + agent.getCoordinate().getLongitude() + ","
                    + agent.getHeading() + ","
                    + agent.isStopped();
            broadcast(msg, radius);

        } else {
            StringBuilder sb = new StringBuilder();
            if (currentTask.size() == 1) {
                sb.append("TASK_WAYPOINT");
            } else {
                sb.append("TASK_REGION");
            }

            for (Coordinate c : currentTask) {
                sb.append(";");
                sb.append(c.latitude);
                sb.append(",");
                sb.append(c.longitude);

            }
            String msg = "HS_GREET;" + agent.getNetworkId() + ";"
                    + agent.getCoordinate().getLatitude() + ","
                    + agent.getCoordinate().getLongitude() + ","
                    + agent.getHeading() + ","
                    + agent.isStopped()
                    + ";" + sb;
            broadcast(msg, radius);
        }

    }

    /***
     * Broadcasts the given message (in a standard format) to the network across a given radius
     * @param message The message to send
     * @param radius The range of communication
     */
    protected void broadcast(String message, int radius) {
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

    /***
     * Checks that a task is possible to add. Ensures it has not been completed and is not already added
     * @param coords The coordinates of the gieven task
     * @return Whether the task is possible to add
     */
    private boolean checkTaskPossible(List<Coordinate> coords) {
        //Coordinate centre = Coordinate.findCentre(coords);

        for (List<Coordinate> tsk : completedTasks) {
            boolean match = true;
            for (Coordinate c : tsk) {
                if (!coords.contains(c)) {
                    // any difference in this set means it's not in completedTasks
                    match = false;
                    break;
                }
            }
            if (match) {
                // Complete match, so already done
                return false;
            }
        }

        for (var entry : tasks.entrySet()) {
            //Coordinate thisTaskCentre = Coordinate.findCentre(entry.getKey());
            boolean match = true;
            for (Coordinate c : entry.getKey()) {
                //if (centre.equals(thisTaskCentre)) {
                if (!coords.contains(c)) {
                    // If any coordinate is different, this task can't be a match
                    match = false;
                    break;
                }
            }
            if (match) {
                // Already in the task list
                return false;
            }
        }
        return true;
    }

    /***
     * The main communication protocol. Handles the given message, in a form of operator and operands, then acts on the
     * given information
     * @param message The entire message given
     */
    protected void receiveMessage(String message) {
        //if (agent instanceof Hub) {
        //    System.out.println("Receiving: " + message);
        //}


        // TODO as of a recent java update, we can use regex-based case statements here in future
        try {
            if (message.contains("CUSTOM")) {
                String opCode = message.split(";")[1];
                String payload = message.substring((message.indexOf(";") + opCode.length() + 2));
                // First semicolon, plus length of the opcode, plus both semicolons themselves (2)
                //String payload = message.substring(message.indexOf(";"));  // Always gets the first occurrence
                agentProgrammer.onMessageReceived(opCode, payload);

            } else if (message.contains("HS_GREET;")) {
                // Handshake introduction
                String id = message.split(";")[1];
                String locX = message.split(";")[2].split(",")[0];
                String locY = message.split(";")[2].split(",")[1];
                String heading = message.split(";")[2].split(",")[2];
                String stopped = message.split(";")[2].split(",")[3];
                //Coordinate coord = getAgentByNetworkId(id).getCoordinate();
                Position newPos = new Position(new Coordinate(Double.parseDouble(locX), Double.parseDouble(locY)), Double.parseDouble(heading), Boolean.parseBoolean(stopped));
                neighbours.put(id, newPos);
                if (neighbours.containsKey(id)) {
                    broadcast("GET_TASKS;" + id, SENSE_RANGE);
                }
                // Note that we should only register neighbours based on messages. This includes coordinates, just in
                // case we want to model error etc

                String[] msgArray = message.split(";");
                if (msgArray.length > 3) {
                    Iterator<String> msgIt = Arrays.stream(msgArray).iterator();
                    msgIt.next();  // Discard the operand ("HS_GREET")
                    msgIt.next();  // Discard the ID
                    msgIt.next();  // Discard the x,y,h part
                    msgIt.next();  // Discard the operand ("TASK_[SOMETHING]")
                    List<Coordinate> thisTask = new ArrayList<>();
                    while (msgIt.hasNext()) {
                        String thisLine = msgIt.next();
                        String x = thisLine.split(",")[0];
                        String y = thisLine.split(",")[1];
                        Coordinate coord = new Coordinate(Double.parseDouble(x), Double.parseDouble(y));
                        thisTask.add(coord);
                    }
                    if (tasks.get(thisTask) != null && !tasks.get(thisTask).contains(id)) {
                        tasks.get(thisTask).add(id);
                    } else if (tasks.get(thisTask) == null) {
                        tasks.put(thisTask, new ArrayList<>());
                        tasks.get(thisTask).add(id);


                    }
                }

            } else if (message.contains("GET_TASKS")) {
                String id = message.split(";")[1];
                if (id.equals(agent.getNetworkId())) {
                    // We have been asked to broadcast task info
                    broadcastTasks();
                }
            } else if (message.contains("COMPLETED_WAYPOINT")) {
                String x = message.split(";")[1].split(",")[0];
                String y = message.split(";")[1].split(",")[1];
                Coordinate thisCoord = new Coordinate(Double.parseDouble(x), Double.parseDouble(y));
                List<Coordinate> thisTask = new ArrayList<>();
                thisTask.add(thisCoord);
                if (checkTaskPossible(thisTask)) {
                    completedTasks.add(thisTask);
                    if (agent instanceof Hub) {
                        System.out.println("Receiving completed task from message: " + message);
                    }
                    // We don't need to worry about adding it twice as checkPossibleTask() ensures it's not there yet
                }


            } else if (message.contains("TASK_WAYPOINT")) {
                String x = message.split(";")[1].split(",")[0];
                String y = message.split(";")[1].split(",")[1];
                Coordinate thisCoord = new Coordinate(Double.parseDouble(x), Double.parseDouble(y));
                List<Coordinate> thisTask = new ArrayList<>();
                thisTask.add(thisCoord);
                if (checkTaskPossible(thisTask)) {
                    tasks.put(thisTask, new ArrayList<>());
                    if (agent instanceof Hub) {
                        System.out.println("Receiving new task from message: " + message);
                    }
                }
            } else if (message.contains("TASK_REGION")) {
                String nwX = message.split(";")[1].split(",")[0];
                String nwY = message.split(";")[1].split(",")[1];
                String neX = message.split(";")[2].split(",")[0];
                String neY = message.split(";")[2].split(",")[1];
                String seX = message.split(";")[3].split(",")[0];
                String seY = message.split(";")[3].split(",")[1];
                String swX = message.split(";")[4].split(",")[0];
                String swY = message.split(";")[4].split(",")[1];
                Coordinate nw = new Coordinate(Double.parseDouble(nwX), Double.parseDouble(nwY));
                Coordinate ne = new Coordinate(Double.parseDouble(neX), Double.parseDouble(neY));
                Coordinate se = new Coordinate(Double.parseDouble(seX), Double.parseDouble(seY));
                Coordinate sw = new Coordinate(Double.parseDouble(swX), Double.parseDouble(swY));
                List<Coordinate> thisTask = new ArrayList<>();
                thisTask.add(nw);
                thisTask.add(ne);
                thisTask.add(se);
                thisTask.add(sw);
                if (checkTaskPossible(thisTask)) {
                    tasks.put(thisTask, new ArrayList<>());
                    if (agent instanceof Hub) {
                        System.out.println("Receiving new task from message: " + message);
                    }
                }
            } else if (message.contains("COMPLETED")) {
                String[] msgArray = message.split(";");
                Iterator<String> msgIt = Arrays.stream(msgArray).iterator();
                msgIt.next();  // Discard the operand ("COMPLETED")
                List<Coordinate> thisTask = new ArrayList<>();
                while (msgIt.hasNext()) {
                    String thisLine = msgIt.next();
                    String x = thisLine.split(",")[0];
                    String y = thisLine.split(",")[1];
                    Coordinate coord = new Coordinate(Double.parseDouble(x), Double.parseDouble(y));
                    thisTask.add(coord);
                }
                receiveCompleteTask(thisTask);
                if (agent instanceof Hub) {
                    System.out.println("Receiving completed task from message: " + message);
                }

            } else if(message.contains("DIAG")) {
                LOGGER.severe("Diagnostic message received: \"" + message.split(";")[1] + "\"");

            } else {
                LOGGER.severe("Uncategorized message received: \"" + message + "\"");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    /***
     * Sends a message to a given NetworkID that will be printed in the log. Mostly for diagnostics etc
     * @param targetID The NetworkID to send to
     * @param message The message to send
     */
    protected void sendDiagnosticMessage(String targetID, String message){
        List<Agent> neighbours = agent.senseNeighbours(SENSE_RANGE);
        for (Agent n : neighbours) {
            try {
                // For now, we just cast it to a programmed agent. In future, we may need to implement message handling
                // for all agents
                AgentProgrammed ap = (AgentProgrammed) n;
                if (ap.getNetworkId().equals(targetID)) {
                    ap.receiveMessage(message);
                }
            } catch (Exception e) {
                LOGGER.severe("Unreceived message. Probably due to this not being a programmed agent.");
            }
        }

    }

    /***
     * The list of tasks. This is an unusual hashmap format:
     * Key = Task (by list of coordinates, usually a singleton)
     * Entry = (List of Strings) NetworkIDs of agents working on this task
     * @return tasks
     */
    public HashMap<List<Coordinate>, List<String>> getTasks() {
        return tasks;
    }

    /***
     * Get the nearest task known
     * @return nearest task
     */
    public List<Coordinate> getNearestTask() {
        List<Coordinate> bestTask = null;
        double shortestDist = 100000;

        for (var entry : tasks.entrySet()) {
            for(Coordinate c : entry.getKey()) {
                double dist = agent.getCoordinate().getDistance(c);
                if (dist < shortestDist) {
                    shortestDist = dist;
                    bestTask = entry.getKey();
                }
            }
        }
        return bestTask;
    }

    /***
     * Get the nearest known task that has no agents assigned to it yet
     * @return The nearest task with no agents assigned
     */
    public List<Coordinate> getNearestEmptyTask(){
        List<Coordinate> bestTask = null;
        double shortestDist = 100000;

        for (var entry : tasks.entrySet()) {
            if (entry.getValue().isEmpty()) {
                for (Coordinate c : entry.getKey()) {
                    double dist = agent.getCoordinate().getDistance(c);
                    if (dist < shortestDist) {
                        shortestDist = dist;
                        bestTask = entry.getKey();
                    }
                }
            }
        }
        return bestTask;
    }

    /***
     * Calculates the average heading of all known neighbours
     * @param includeStationary Whether to include agents that are stationary (usually you don't want to, as they aren't
     *                          doing anything)
     * @return Average heading of neighbours
     */
    public double calculateAverageNeighbourHeading(boolean includeStationary) {
        double totalHeading = 0;
        for (var entry : neighbours.entrySet()) {
                if(!includeStationary || !entry.getValue().getStopped()) {
                    // Includes it if we aren't including stationary, or if it's stopped otherwise
                    totalHeading += entry.getValue().heading;
                }
        }
        return totalHeading / neighbours.size();
    }

    /***
     * Default handler for this method, assumes you don't want to include stationary agents
     * @return Average heading of neighbours
     */
    public double calculateAverageNeighbourHeading() {
        return calculateAverageNeighbourHeading(false);
    }

    /***
     * Sets this agents task to the given coordinate set
     * @param taskCoords The coordinates of this task
     */
    public void setTask(List<Coordinate> taskCoords) {
        try {
            agent.setAllocatedTaskByCoords(taskCoords);
            currentTask = taskCoords;
            agent.setRoute(taskCoords);
            resume();
        } catch (Exception e) {
            // Failed to do this, probably due to incorrect information. We have to allow this mistake to happen,
            // as otherwise we are letting globally known information leak into the process
            tempPlaceNewTask("waypoint", taskCoords);
            //agent.setAllocatedTaskByCoords(taskCoords);
            currentTask = taskCoords;
            agent.setRoute(taskCoords);
            resume();
        }

    }

    /***
     * Gets the list of all agents (by network id) assigned ot the given task
     * @param task The task to check
     * @return The list of NetworkIDs for the agents assigned
     */
    protected List<String> getAgentsAssigned(List<Coordinate> task) {
        return tasks.get(task);
    }

    /***
     * Unused currently; This is a clumsy way of adding tasks from the programmer. It does work, but the receiver method
     * is better
     * @param type Type of task
     * @param coords Task's coordinate
     */
    protected void tempPlaceNewTask(String type, List<Coordinate> coords) {
        //agent.tempPlaceNewTask(type, coords);
        tasks.put(coords, new ArrayList<>());
    }

    /***
     * Returns whether this agent has any known neighbours
     * @return if this agnet has neighbours
     */
    public boolean hasNeighbours() {
        return (neighbours.size() != 0);
    }

    /**
     * Returns the number of neighbours this drone has
     * @return Number of neighbours
     */
    public int getNumberOfNeighbours() {
        return neighbours.size();
    }

    /***
     * Clears the list of neighbours
     */
    public void clearNeighbours() {
        neighbours = new HashMap<>();
    }

    /***
     * This shouldn't be used by controller, as it handles Task objects. You can create tasks as coordinate lists and
     * use the TempPlaceTask, or you can set a route for an agent, but probably best not to use this.
     * Adds a task to this agent. Passed through to receivers, when adding tasks from the user
     * @param item Task to add
     */
    protected void addTask(Task item) {
        // For now, assume it's a waypoint task. In future we may need to check what type of task (instanceof) and go from there
        List<Coordinate> thisTask = new ArrayList<>();
        thisTask.add(item.getCoordinate());
        tasks.put(thisTask, new ArrayList<>());
    }

    /***
     * Uses the pre-existing flocking algorithm based on attraction and repulsion
     */
    protected void flockWithAttractionRepulsion(){
        agent.adjustFlockingHeading();
    }

    /***
     * Gets average movement based on if agents are stopped or moving, NOT their "speed" stat as that doesn't change
     * @return Average movement
     */
    public double getAverageNeighbourSpeed() {
        int n = 0;
        int total = 0;
        for (var entry : neighbours.entrySet()) {
            if (!entry.getValue().getStopped()){
                total++;
            }
            n++;
        }
        if (total == 0) {
            // The case when all are stationary (div/0)
            return 0;
        } else {
            return (double) total / (double) n;
        }

    }

    /**
     * Checks whether any neighbours are moving. This is the easiest way to stop them flying off at the start
     * @return Whether any neighbours are moving
     */
    protected boolean checkForNeighbourMovement(){
        for (var entry : neighbours.entrySet()) {
            if (!entry.getValue().getStopped()){
                return true;
            }
        }
        return false;
    }


    /**
     * Allows the programmer to handle custom messages that may be passed through normally
     * @param opCode A code to allow you to differentiate messages for different functionalities
     * @param payload The useful part of the message you want to handle
     */
    public void sendCustomMessage(String opCode, String payload) {
        broadcast("CUSTOM;"+opCode+";"+payload, SENSE_RANGE);
    }

    public String getRandomNeighbour() {
        try {
            int index = (int) Math.floor(Math.random() * neighbours.size());

            return neighbours.keySet().toArray()[index].toString();
        } catch (Exception e) {
            return null;
        }
    }

    public List<Position> getNeighbourPositions() {
        List<Position> positions = new ArrayList<>();
        for(var entry : neighbours.entrySet()) {
            positions.add(entry.getValue());
        }
        return positions;
    }

    public HashMap<String, Position> getNeighbours() {
        return neighbours;
    }

    public boolean checkNeighbourHasTask(String key) {
        for (var entry : tasks.entrySet()) {
            if (entry.getValue().contains(key)){
                return true;
            }
        }
        return false;
    }

    public void setVisual(String type) {
        agent.setVisual(type);
    }

    public void goHome(){
        agent.clearRoute();
        //LOGGER.severe(agent.getId() + " is going home");
        //tempPlaceNewTask("waypoint", Collections.singletonList(home));
        //setTask(Collections.singletonList(home));
        setRoute(Collections.singletonList(home));
        isGoingHome = true;
        resume();
    }

    public boolean isGoingHome() {
        return isGoingHome;
    }

    public List<List<Coordinate>> getCompletedTasks() {
        return completedTasks;
    }

    public Coordinate getHome() {
        return home;
    }

    public void stopGoingHome() {
        isGoingHome = false;
        agent.clearRoute();
        //agent.clearTempRoute();
        stop();
    }

    public String getModel(){
        return GsonUtils.toJson(this);
    }

    public boolean isHub() {
        return agent instanceof Hub;
    }


    /***
     * We use an internal class to make handling positional information easier. Holds location, heading, and whether it
     * is stopped
     */
    protected static class Position {
        private Coordinate location;
        private Double heading;
        private Boolean stopped;

        public String toString() {
            return "At loc=" + location + ", hd=" + heading + ", stp=" + stopped;
        }


        public Position(Coordinate loc, Double hd, Boolean stpd){
            location=loc;
            heading=hd;
            stopped = stpd;
        }

        public Coordinate getLocation() {
            return location;
        }

        public void setLocation(Coordinate location) {
            this.location = location;
        }

        public Double getHeading() {
            return heading;
        }

        public void setHeading(Double heading) {
            this.heading = heading;
        }

        public Boolean getStopped() {
            return stopped;
        }

        public void setStopped(Boolean stopped) {
            this.stopped = stopped;
        }
    }



}
