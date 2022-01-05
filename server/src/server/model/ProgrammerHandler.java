package server.model;

import server.Simulator;
import server.model.target.Target;
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
    private final transient Logger LOGGER = Logger.getLogger(AgentProgrammed.class.getName());
    private static final double TARGET_SENSE_RANGE = 50;  // Number of metres around that the agent can detect targets
    private static final int AGENT_TIMEOUT = 6; // (6x200ms = every 1.2 second) ~= 1 real-life seconds
    private static final int SENSE_RANGE = 250; // The max (and default) radius used for sensing neighbours etc
    private static final int pingTimeout = 3; // (6x200ms = every 1.2 second) ~= 1 real-life seconds

    protected transient AgentProgrammed agent;
    private final transient AgentProgrammer agentProgrammer;  // Link to the user's code

    private HashMap<String, Position> neighbours;  // Stores the known other agents (including non-neighbours)
    private List<Coordinate> currentTask = new ArrayList<>();
    private final HashMap<List<Coordinate>, List<String>> tasks;
    private final List<List<Coordinate>> completedTasks;
    private final List<Coordinate> foundTargets;
    
    private Coordinate home;
    private boolean leader = false;

    private boolean isGoingHome = false;
    private int stepCounter = 0;
    private int pingCounter = 0;

    /***
     * Constructor. Connects the agent to the programmer s.t. this class behaves akin to an MVC controller
     * @param connectedAgent The agent that this handler controls
     */
    public ProgrammerHandler(AgentProgrammed connectedAgent){
        this.agent = connectedAgent;
        neighbours = new HashMap<>();
        tasks = new HashMap<>();
        completedTasks = new ArrayList<>();
        foundTargets = new ArrayList<>();
        agentProgrammer = new AgentProgrammer(this);
    }

    /***
     * Called at every time step, we set up on the first run, then pass through to the programmer after
     */
    public void step() {
        if (agent.getNetworkId().equals("")) {
            // Must perform setup on the first step, otherwise they can't find each other
            agent.setNetworkID(agent.generateRandomTag());
            declareSelf(SENSE_RANGE);
            agentProgrammer.setup();
        } else if (pingCounter >= pingTimeout) {
            declareSelf(SENSE_RANGE);
            checkForTargets();
            declareTargets(SENSE_RANGE);
            pingCounter = 0;
        }
        pingCounter++;
        stepCounter++;

        agentProgrammer.step();  // Where we actually call the user's code
    }

    /**
     * Checks for targets around this location using the TARGET_SENSE_RANGE constant
     * NOTE: Uses server-side code to check for target's actual positions, so don't access or edit this
     */
    private void checkForTargets() {
        for (Target t : Simulator.instance.getState().getTargets()) {
            if (t.getCoordinate().getDistance(getPosition()) < TARGET_SENSE_RANGE && !foundTargets.contains(t.getCoordinate())) {
                foundTargets.add(t.getCoordinate());
            }
        }
    }

    /**
     * Broadcasts all known targets to the network
     * @param radius The communication range to send out
     */
    private void declareTargets(int radius) {
        StringBuilder sb = new StringBuilder();
        sb.append("TARGETS");
        for (Coordinate tgt : foundTargets) {
            sb.append(";");
            sb.append(tgt.latitude);
            sb.append(",");
            sb.append(tgt.longitude);
        }
        broadcast(sb.toString(), radius);
    }

    /***
     * Because the receiver (base station) inherits from this class, we use a function here just for that class
     */
    public void baseStep() {
        if (agent.getNetworkId().equals("")) {
            agent.type = "hub";
            // Must perform setup on the first step, otherwise they can't find each other
            //agent.setNetworkID(agent.generateRandomTag());
            agent.setNetworkID("HUB");
            declareSelf(SENSE_RANGE);
            agentProgrammer.setup();
        } else if (pingCounter >= pingTimeout) {
            declareSelf(SENSE_RANGE); // This must be separate, as order matters for the first case
            broadcastTasks();
            declareTargets(SENSE_RANGE);
            pingCounter = 0;
        }
        pingCounter++;
        stepCounter++;
    }

    /**
     * Getter for the attached agent's coordinate
     * @return Coordinate for this agent's position
     */
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
                StringBuilder sb = new StringBuilder();

                if (tsk.size() == 1) {
                    sb.append("COMPLETED");
                } else {
                    sb.append("COMPLETED");
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

    /**
     * Cancels the current task this agent is doing
     */
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

    /**
     * Sets the temp route for this agent (not normally used)
     * @param coords The coordinates for this agent's temp route
     */
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
        // A very important check here, otherwise return home tasks mess up the propagation of completed tasks and it
        //  stops tasks being checked properly
        if (currentTask.size() > 0) {
            List<Coordinate> coords = currentTask;
            tasks.remove(currentTask);

            StringBuilder sb = new StringBuilder();
            sb.append("COMPLETED");
            for (Coordinate c : coords) {
                sb.append(";");
                sb.append(c.latitude);
                sb.append(",");
                sb.append(c.longitude);

            }
            broadcast(sb.toString(), SENSE_RANGE);

            completedTasks.add(coords);
        }
    }

    /**
     * Used to send completion messages to the network
     * @param coord Coordinate of the task
     */
    protected void completeTask(Coordinate coord){
        List<Coordinate> thisTask = new ArrayList<>();
        thisTask.add(coord);
        tasks.remove(thisTask);

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
     * @param coords Coordinate of the task
     */
    private void receiveCompleteTask(List<Coordinate> coords){
        if (!completedTasks.contains(coords)) {
            //System.out.println(agent.getId() + " Receiving new complete task at " + coords.get(0));
            completedTasks.add(coords);
        }

        if (agent instanceof Hub) {
            agent.tempRemoveTask(coords);
        }

        tasks.remove(coords);
        if (currentTask.equals(coords)) {
            currentTask = new ArrayList<>();
            // Not sure if stopping or returning fixes the halting bug
            stop();
            //goHome();
        }
    }

    /**
     * Receives the coordinate of a target and attempts to reveal it in the UI
     * NOTE: Only accessed by the Hub, interfaces with the server side code
     * @param coords Coordinate of the target
     */
    private void receiveTarget(Coordinate coords){
        Target t = Simulator.instance.getTargetController().findTargetByCoord(coords);
        Simulator.instance.getTargetController().setTargetVisibility(t.getId(), true);
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
        StringBuilder sb = new StringBuilder();

        sb.append("HS_GREET;").append(agent.getNetworkId()).append(";").
                append(agent.getCoordinate().getLatitude()).append(",").
                append(agent.getCoordinate().getLongitude()).append(",").
                append(agent.getHeading()).append(",").
                append(agent.isStopped()).append( ";");

        if (currentTask.size() == 0) {
            sb.append("TASK_NONE");
        } else if (currentTask.size() == 1) {
            sb.append("TASK_WAYPOINT");
        } else {
            sb.append("TASK_REGION");
        }

        // In theory this should work; it will pass by and won't add any coord
        if (currentTask.size() > 0) {
            for (Coordinate c : currentTask) {
                sb.append(";");
                sb.append(c.latitude);
                sb.append(",");
                sb.append(c.longitude);
            }
        }

        // Add agent info
        sb.append(";AGENTS");
        for (var entry : neighbours.entrySet()) {
            sb.append(";").append(entry.getKey()).append(",").
                    append(entry.getValue().getLocation().latitude).append(",").
                    append(entry.getValue().getLocation().longitude).append(",").
                    append(entry.getValue().getHeading()).append(",").
                    append(entry.getValue().isStopped()).append(",").
                    append(entry.getValue().getLastUpdateTime());
        }

        broadcast(sb.toString(), radius);
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
     * @param coords The coordinates of the given task
     * @return Whether the task is possible to add
     */
    private boolean checkTaskPossible(List<Coordinate> coords) {
        boolean match;
        for (List<Coordinate> tsk : completedTasks) {
            match = true;
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
            match = true;
            for (Coordinate c : entry.getKey()) {
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
        // TODO as of a recent java update, we can use regex-based case statements here in future
        try {
            if (message.contains("CUSTOM")) {
                String opCode = message.split(";")[1];
                String payload = message.substring((message.indexOf(";") + opCode.length() + 2));
                // First semicolon, plus length of the opcode, plus both semicolons themselves (2)
                agentProgrammer.onMessageReceived(opCode, payload);

            } else if (message.contains("HS_GREET;")) {
                // Handshake introduction. Respond with location of self, and our assigned task, and known neighbours
                // TODO edit this to match the later format (no semicolon after id)
                String id = message.split(";")[1];
                String locX = message.split(";")[2].split(",")[0];
                String locY = message.split(";")[2].split(",")[1];
                String heading = message.split(";")[2].split(",")[2];
                String stopped = message.split(";")[2].split(",")[3];

                Position newPos = new Position(
                        new Coordinate(Double.parseDouble(locX), Double.parseDouble(locY))
                        , Double.parseDouble(heading)
                        , Boolean.parseBoolean(stopped)
                        , true
                        , stepCounter);

                neighbours.put(id, newPos);

                // Registers hub location manually
                if (id.equals("HUB")) {
                    home = new Coordinate(Double.parseDouble(locX), Double.parseDouble(locY));
                }

                if (neighbours.containsKey(id)) {
                    broadcast("GET_TASKS;" + id, SENSE_RANGE);
                }
                // Note that we should only register neighbours based on messages. This includes coordinates, just in
                // case we want to model error etc

                String[] msgArray = message.split(";");
                Iterator<String> msgIt = Arrays.stream(msgArray).iterator();
                String next;

                // Discards each part until we hit the "TASK_[SOMETHING]" line
                do {
                    next = msgIt.next();
                } while (!next.contains("TASK"));
                next = msgIt.next();  // Pass over the "TASK" line (includes if it is TASK_NONE)
                List<Coordinate> thisTask = new ArrayList<>();
                // Discards each part until we hit the "AGENTS" line
                while (!next.equals("AGENTS")) {
                    String x = next.split(",")[0];
                    String y = next.split(",")[1];
                    Coordinate coord = new Coordinate(Double.parseDouble(x), Double.parseDouble(y));
                    thisTask.add(coord);
                    next = msgIt.next();
                }

                if (thisTask.size() != 0) {  // Checks it wasn't a TASK_NONE
                    if (tasks.get(thisTask) != null && !tasks.get(thisTask).contains(id)) {
                        tasks.get(thisTask).add(id);
                        if (agent instanceof Hub) {
                            System.out.println("HUB:        " + id + " is doing task at " + thisTask.get(0));
                        }
                    } else if (tasks.get(thisTask) == null) {
                        tasks.put(thisTask, new ArrayList<>());
                        tasks.get(thisTask).add(id);
                    }
                }

                // This now looks for the AGENTS line, which will be there regardless of whether there are TASKS details
                while (msgIt.hasNext()) {
                    next = msgIt.next();
                    id = next.split(",")[0];
                    locX = next.split(",")[1];
                    locY = next.split(",")[2];
                    heading = next.split(",")[3];
                    stopped = next.split(",")[4];
                    String timeStamp = next.split(",")[5];
                    Position neighbourNewPos = new Position(new Coordinate(Double.parseDouble(locX), Double.parseDouble(locY)), Double.parseDouble(heading), Boolean.parseBoolean(stopped), false, Integer.parseInt(timeStamp));
                    if (!neighbours.containsKey(id) ||
                            (neighbours.get(id).getDirectlyConnected()
                            && neighbours.get(id).getLastUpdateTime() + AGENT_TIMEOUT < stepCounter)) {
                        // Either we have no existing record of this at all, in which case we add it as INHERITED,
                        //  or we consider it connected, but it was last updated directly too long ago. Disconnect but
                        //  record as inherited
                        neighbours.put(id, neighbourNewPos);
                        // Registers hub location manually
                        if (id.equals("HUB")) {
                            home = new Coordinate(Double.parseDouble(locX), Double.parseDouble(locY));
                        }
                    } else if (!neighbours.get(id).getDirectlyConnected()) {
                        // We are working on inherited connections for this record, so update
                        neighbours.put(id, neighbourNewPos);
                        // Registers hub location manually
                        if (id.equals("HUB")) {
                            home = new Coordinate(Double.parseDouble(locX), Double.parseDouble(locY));
                        }
                    } // Otherwise, we have a good recent direct connection
                }

            } else if (message.contains("GET_TASKS")) {
                // Respond with a list of all known tasks
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
                    // We don't need to worry about adding it twice as checkPossibleTask() ensures it's not there yet
                    completedTasks.add(thisTask);
                    if (agent instanceof Hub) {
                        System.out.println("Receiving completed at: " + thisCoord);
                    }
                }
            } else if (message.contains("TASK_WAYPOINT")) {
                String x = message.split(";")[1].split(",")[0];
                String y = message.split(";")[1].split(",")[1];
                Coordinate thisCoord = new Coordinate(Double.parseDouble(x), Double.parseDouble(y));
                List<Coordinate> thisTask = new ArrayList<>();
                thisTask.add(thisCoord);
                if (checkTaskPossible(thisTask)) {
                    tasks.put(thisTask, new ArrayList<>());
                    // Debug to display agent knowledge of new tasks
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

                // Debug to display hub knowledge of task completion
                if (!completedTasks.contains(thisTask)) {
                    if (agent instanceof Hub) {
                        System.out.println("HUB:        Receiving completed task from message: " + message);
                    }
                }

                receiveCompleteTask(thisTask);

            } else if (message.contains("TARGETS")) {
                String[] msgArray = message.split(";");
                Iterator<String> msgIt = Arrays.stream(msgArray).iterator();
                msgIt.next();  // Discard the operand ("TARGETS")
                while (msgIt.hasNext()) {
                    String thisLine = msgIt.next();
                    String x = thisLine.split(",")[0];
                    String y = thisLine.split(",")[1];
                    Coordinate coord = new Coordinate(Double.parseDouble(x), Double.parseDouble(y));
                    if (!foundTargets.contains(coord)) {
                        foundTargets.add(coord);
                        if (agent instanceof Hub) {
                            System.out.println("HUB:        Receiving target location at: " + coord);
                            receiveTarget(coord);
                        }
                    }
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
                if(!includeStationary || !entry.getValue().isStopped()) {
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
            if (!entry.getValue().isStopped()){
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
            if (!entry.getValue().isStopped()){
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

    /**
     * Returns a random neighbour of this agent
     * @return Any agent from the neighbours array (not necessarily a connected agent)
     */
    public String getRandomNeighbour() {
        try {
            int index = (int) Math.floor(Math.random() * neighbours.size());

            return neighbours.keySet().toArray()[index].toString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Returns complete list of agent positions
     * @return List of Positions
     */
    public List<Position> getNeighbourPositions() {
        List<Position> positions = new ArrayList<>();
        for(var entry : neighbours.entrySet()) {
            positions.add(entry.getValue());
        }
        return positions;
    }

    /**
     * Returns neighbours HashMap
     * @return neighbours HashMap
     */
    public HashMap<String, Position> getNeighbours() {
        return neighbours;
    }

    /**
     * Adjusts the visual status so we can colour code drones
     * @param type Arbitrary name that is handled in the UI side e.g "leader"
     */
    public void setVisual(String type) {
        agent.setType(type);
    }

    /**
     * Returns the agent to the home location
     */
    public void goHome(){
        agent.clearRoute();
        // TODO this doesn't seem to update properly
        setRoute(Collections.singletonList(calculateNearestHomeLocation()));
        isGoingHome = true;
        resume();
    }

    /**
     * Finds the nearest edge of the communication radius of the hub and goes a little way into the circle there
     * @return Coordinate to go to
     */
    private Coordinate calculateNearestHomeLocation() {
        double xDist = agent.getCoordinate().getLongitude() - home.longitude;
        double yDist = agent.getCoordinate().getLatitude() - home.latitude;
        // using tan rule to find angle from hub to agent
        double theta = Math.atan(yDist / xDist);
        //double radius = 0.8 * (((double) SENSE_RANGE /1000)/6379.1);  // 20% into the radius of the hub (includes metre to
        //  latlng calc

        // approx 111,111m = 1deg latlng
        double radius = 0.8 * (double) SENSE_RANGE / 111111;

        // x,y = rcos(theta), rsin(theta); If in the negative x, we must invert (=== adding pi rads)
        double xRes;
        double yRes;
        if (xDist < 0) {
            xRes = home.longitude - (radius * Math.cos(theta));
            yRes = home.latitude - (radius * Math.sin(theta));
        } else {
            xRes = home.longitude + (radius * Math.cos(theta));
            yRes = home.latitude + (radius * Math.sin(theta));
        }

        return new Coordinate(yRes, xRes);
    }

    /**
     * Checks if this agent is currently in the process of going home
     * @return If it is going home
     */
    public boolean isGoingHome() {
        return isGoingHome;
    }

    /**
     * Gets list of completed tasks
     * @return List of completed tasks
     */
    public List<List<Coordinate>> getCompletedTasks() {
        return completedTasks;
    }

    /**
     * Returns the home location
     * @return The home location
     */
    public Coordinate getHome() {
        return home;
    }

    /**
     * Cancels the home task and stops this agent
     */
    public void stopGoingHome() {
        isGoingHome = false;
        agent.clearRoute();
        stop();
    }

    /**
     * Returns a complete model of this PH as json
     * @return Object as Json
     */
    public String getModel(){
        return GsonUtils.toJson(this);
    }

    /**
     * Checks if this is a Hub
     * @return if it is a Hub
     */
    public boolean isHub() {
        return agent instanceof Hub;
    }

    /**
     * Returns value of the constant for SENSE_RANGE
     * @return SENSE_RANGE
     */
    public double getSenseRange() {
        return SENSE_RANGE;
    }

    /**
     * Checks whether the given agent is connected (known) to this agent
     * Be careful here, it uses some Simulator info, but nothing that informs the agents
     * Based on whether we have a recent record of this
     * @param agent The agent to check for
     * @return If it is connected to this agent
     */
    public boolean checkForConnection(Agent agent) {
        AgentProgrammed ap = (AgentProgrammed) agent;
        for (var entry : neighbours.entrySet()) {
            if (entry.getKey().equals(ap.getNetworkId())) {
                // Return true if the record of this is sufficiently recent
                return entry.getValue().getLastUpdateTime() + AGENT_TIMEOUT > stepCounter;
            }
        }
        // This is for if it is completely unknown
        return false;
    }

    public boolean isLeader() {
        return leader;
    }

    public void setLeader(boolean leaderStatus) {
        leader = leaderStatus;
    }

    public Coordinate getHubPosition() {
        return home;
    }

    public double getNextRandomDouble() {
        return agent.getNextRandom();
    }

    /***
     * We use an internal class to make handling positional information easier. Holds location, heading, and whether it
     * is stopped
     */
    protected static class Position {
        private Coordinate location;
        private Double heading;
        private Boolean stopped;
        private Boolean directlyConnected;
        private int lastUpdateTime;

        public String toString() {
            return "At loc=" + location + ", hd=" + heading + ", stp=" + stopped + ", con=" + directlyConnected + ", time=" + lastUpdateTime;
        }

        public Position(Coordinate loc, Double hd, Boolean stpd, Boolean connected, int updateTime){
            location=loc;
            heading=hd;
            stopped = stpd;
            directlyConnected = connected;
            lastUpdateTime = updateTime;
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

        public Boolean isStopped() {
            return stopped;
        }

        public void setStopped(Boolean stopped) {
            this.stopped = stopped;
        }

        public Boolean getDirectlyConnected() {
            return directlyConnected;
        }

        public void setDirectlyConnected(Boolean directlyConnected) {
            this.directlyConnected = directlyConnected;
        }

        public int getLastUpdateTime() {
            return lastUpdateTime;
        }

        public void setLastUpdateTime(int lastUpdateTime) {
            this.lastUpdateTime = lastUpdateTime;
        }
    }



}
