package server.model.agents;

import server.Simulator;
import server.model.Coordinate;
import server.model.target.Target;
import server.model.task.PatrolTask;
import server.model.task.Task;
import server.model.task.VisitTask;
import server.model.task.WaypointTask;
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
    private static final int pingTimeout = 3; // (6x200ms = every 1.2 second) ~= 1 real-life seconds

    protected transient AgentProgrammed agent;
    private final transient AgentProgrammer agentProgrammer;  // Link to the user's code
    private int communicationRange = 250; // The max (and default) radius used for sensing neighbours etc

    private HashMap<String, Position> neighbours;  // Stores the known other agents (including non-neighbours)
    private List<Coordinate> currentTask = new ArrayList<>();
    private final HashMap<List<Coordinate>, List<String>> tasks;
    private final HashMap<List<Coordinate>, String> orders;
    private final List<Coordinate> completedTasks;
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
        orders = new HashMap<>();
        completedTasks = new ArrayList<>();
        foundTargets = new ArrayList<>();
        agentProgrammer = new AgentProgrammer(this);
    }

    /***
     * Called at every time step, we set up on the first run, then pass through to the programmer after
     */
    public void step() {
        if (agent.getNetworkId().equals("")) {
            agent.stop();
            // Must perform setup on the first step, otherwise they can't find each other
            //agent.setNetworkID(agent.generateRandomTag());
            agent.setNetworkID(agent.getId());
            declareSelf(communicationRange);
            agentProgrammer.setup();
        } else if (pingCounter >= pingTimeout) {
            declareSelf(communicationRange);
            checkForTargets();
            declareTargets(communicationRange);
            pingCounter = 0;
        }
        pingCounter++;
        stepCounter++;

        agentProgrammer.step();  // Where we actually call the user's code
    }

    public boolean gridMove(int i) {
        return agentProgrammer.gridMove(i);
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
            sb.append(tgt.getLatitude());
            sb.append(",");
            sb.append(tgt.getLongitude());
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
            declareSelf(communicationRange);
            agentProgrammer.setup();
        } else if (pingCounter >= pingTimeout) {
            declareSelf(communicationRange); // This must be separate, as order matters for the first case
            broadcastTasks();
            broadcastOrders(communicationRange);
            declareTargets(communicationRange);
            pingCounter = 0;
        }
        pingCounter++;
        stepCounter++;

    }

    private void broadcastOrders(int radius) {
        StringBuilder sb = new StringBuilder();
        sb.append("ORDERS");
        for (var entry : orders.entrySet()) {
            sb.append(";");
            sb.append(entry.getKey().get(0).getLatitude());
            sb.append(",");
            sb.append(entry.getKey().get(0).getLongitude());
            sb.append(",");
            sb.append(entry.getValue());
        }
        broadcast(sb.toString(), radius);
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
                    sb.append("TASK_PATROL");
                }
                // TODO To handle region tasks, we will need to provide a different format, maybe (TL, BR, 0) so it can be identified

                List<Coordinate> coords = entry.getKey();
                for (Coordinate c : coords) {
                    sb.append(";");
                    sb.append(c.getLatitude());
                    sb.append(",");
                    sb.append(c.getLongitude());
                }
                broadcast(sb.toString(), communicationRange);
            }

            for (Coordinate tsk : completedTasks) {

                String sb = "COMPLETED" +
                        ";" +
                        tsk.getLatitude() +
                        "," +
                        tsk.getLongitude();

                broadcast(sb, communicationRange);
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
        // From ms/s, but instead of dividing by 1 second, it's by one game step (fraction of a second)
        // We also check if we are closer than 1 move step; in which case
        // double distToMove = Math.min(agent.getSpeed() / Simulator.instance.getGameSpeed(), agent.getCoordinate().getDistance(getCurrentDestination()));
        double distToMove = agent.getSpeed() / Simulator.instance.getGameSpeed();
        agent.moveAlongHeading(distToMove);
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
        if (!currentTask.isEmpty()) {
            tasks.get(currentTask).remove(getId());
        }
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
        if (currentTask.size() > 1) {
            // Patrol (or region, NYI)
            agent.moveAlongPatrol();
        } else {
            agent.moveTowardsDestination();
        }

    }

    private Coordinate calculateRepresentativeCoordinate(List<Coordinate> coords) {
        if (coords.size() == 1) {
            return coords.get(0);
        } else if (coords.size() > 1) {
            return Coordinate.findCentre(coords.subList(0, coords.size() - 1));
        } else {
            return null;
        }
    }

    /***
     * Completes the task currently set. Also sends this completion message to the network automatically, and adds it
     * to the set of tasks that it will report as complete from now on
     */
    protected void completeTask(){
        // A very important check here, otherwise return home tasks mess up the propagation of completed tasks and it
        //  stops tasks being checked properly
        Coordinate coords = calculateRepresentativeCoordinate(currentTask);

        if (coords != null) {
            tasks.remove(currentTask);

            String sb = "COMPLETED" +
                    ";" +
                    coords.getLatitude() +
                    "," +
                    coords.getLongitude();


            broadcast(sb, communicationRange);

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
            sb.append(c.getLatitude());
            sb.append(",");
            sb.append(c.getLongitude());
        }
        broadcast(sb.toString(), communicationRange);
    }

    /***
     * Handles a completion message. Adds this task to the completed list
     * @param coord Coordinate of the task
     */
    private void receiveCompleteTask(Coordinate coord){
        if (!completedTasks.contains(coord)) {
            completedTasks.add(coord);
        }

        if (agent instanceof Hub) {
            agent.tempRemoveTask(coord);
        }

        // This will work if it's a waypoint task
        tasks.remove(Collections.singletonList(coord));


        // Complicated statement to cover singleton currentTask exact match or non-singleton centre reference match with
        //  or without patrol allowance. Order of statements prevents errors
        if (coord.equals(calculateRepresentativeCoordinate(currentTask))) {
            tasks.remove(currentTask); // To make sure, it doesn't always remove right otherwise
            currentTask = new ArrayList<>();
            agent.clearRoute();
            stop();
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
            sb.append("TASK_PATROL");
        }
        // TODO To handle region tasks, we will need to provide a different format, maybe (TL, BR, 0) so it can be identified

        // In theory this should work; it will pass by and won't add any coord
        if (currentTask.size() > 0) {
            for (Coordinate c : currentTask) {
                sb.append(";");
                sb.append(c.getLatitude());
                sb.append(",");
                sb.append(c.getLongitude());
            }
        }

        // Add agent info
        sb.append(";AGENTS");
        for (var entry : neighbours.entrySet()) {
            sb.append(";").append(entry.getKey()).append(",").
                    append(entry.getValue().getLocation().getLatitude()).append(",").
                    append(entry.getValue().getLocation().getLongitude()).append(",").
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
            if (n instanceof AgentProgrammed ap) {
                ap.receiveMessage(message);
            } else if (n instanceof AgentCommunicating ac) {
                ac.receiveMessage(message);
            } else {
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
        Coordinate thisRep = calculateRepresentativeCoordinate(coords);
        if (completedTasks.contains(thisRep)) {
            return false;
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
                    broadcast("GET_TASKS;" + id, communicationRange);
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

                // Remove the current record of this id for any task other than the reported one
                pruneTaskForReset(id, thisTask);
                if (thisTask.size() != 0) {  // Checks it wasn't a TASK_NONE
                    if (tasks.get(thisTask) != null && !tasks.get(thisTask).contains(id)) {
                        tasks.get(thisTask).add(id);
                        if (agent instanceof Hub) {
                            //System.out.println("HUB:        " + id + " is doing task at " + thisTask.get(0));
                        }
                    } else if (tasks.get(thisTask) == null) {
                        tasks.put(thisTask, new ArrayList<>());
                        // TODO make this cope with larger tasks
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
                if (checkTaskPossible(thisTask)) {  // TODO check this, it shouldn't be needed?
                    // We don't need to worry about adding it twice as checkPossibleTask() ensures it's not there yet
                    completedTasks.add(calculateRepresentativeCoordinate(thisTask));
                    if (agent instanceof Hub) {
                        //System.out.println("HUB:        Receiving completed task at " + thisCoord);
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
            } else if (message.contains("TASK_PATROL")) {
                String[] msgArray = message.split(";");
                Iterator<String> msgIt = Arrays.stream(msgArray).iterator();
                String next;
                msgIt.next();  // Pass over the "TASK" line
                List<Coordinate> thisTask = new ArrayList<>();
                // Discards each part until we hit the "AGENTS" line
                while (msgIt.hasNext()) {
                    next = msgIt.next();
                    String x = next.split(",")[0];
                    String y = next.split(",")[1];
                    Coordinate coord = new Coordinate(Double.parseDouble(x), Double.parseDouble(y));
                    thisTask.add(coord);
                }
                if (checkTaskPossible(thisTask)) {
                    // We don't need to worry about adding it twice as checkPossibleTask() ensures it's not there yet
                    tasks.put(thisTask, new ArrayList<>());
                }
            }


            else if (message.contains("TASK_REGION")) {
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

                // TODO the above loop isn't needed any more as we only use the rep

                // Debug to display hub knowledge of task completion
                if (!completedTasks.contains(calculateRepresentativeCoordinate(thisTask))) {
                    if (agent instanceof Hub) {
                        //System.out.println("HUB:        Receiving completed task at " + thisTask);
                    }
                }

                receiveCompleteTask(calculateRepresentativeCoordinate(thisTask));

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
                            //System.out.println("HUB:        Receiving target location at: " + coord);
                            receiveTarget(coord);
                        }
                    }
                }

            } else if (message.contains("ORDERS")) {
                String[] msgArray = message.split(";");
                Iterator<String> msgIt = Arrays.stream(msgArray).iterator();
                msgIt.next();  // Discard the operand ("ORDERS")
                while (msgIt.hasNext()) {
                    String thisLine = msgIt.next();
                    String x = thisLine.split(",")[0];
                    String y = thisLine.split(",")[1];
                    Coordinate coord = new Coordinate(Double.parseDouble(x), Double.parseDouble(y));
                    String agent = thisLine.split(",")[2];
                    orders.put(Collections.singletonList(coord), agent);
                }
            }

            else if(message.contains("DIAG")) {
                LOGGER.severe("Diagnostic message received: \"" + message.split(";")[1] + "\"");

            } else {
                LOGGER.severe("Uncategorized message received: \"" + message + "\"");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Searches through the task list and removes the agent from that task assignment
     * @param id
     * @param thisTask
     */
    private void pruneTaskForReset(String id, List<Coordinate> thisTask) {
        // TODO Note that we are now ignoring null allocations to allow assignment propagation. In future this may need a flag
        if (!thisTask.isEmpty()) {
            for (var entry : tasks.entrySet()) {
                if (!entry.getKey().equals(thisTask) && entry.getValue().contains(id)) {
                    entry.getValue().remove(id);
                }
            }
        }
    }

    /***
     * Sends a message to a given NetworkID that will be printed in the log. Mostly for diagnostics etc
     * @param targetID The NetworkID to send to
     * @param message The message to send
     */
    protected void sendDiagnosticMessage(String targetID, String message){
        List<Agent> neighbours = agent.senseNeighbours(communicationRange);
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
    public List<Coordinate> getNearestEmptyWaypointTask(){
        List<Coordinate> bestTask = null;
        double shortestDist = 100000;
        for (var entry : tasks.entrySet()) {
            if (entry.getKey().size() == 1 && entry.getValue().isEmpty()) {
                // It's a waypoint task with no agents assigned
                double dist = agent.getCoordinate().getDistance(entry.getKey().get(0));
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
    public List<Coordinate> getNearestEmptyTask() {
        List<Coordinate> bestTask = null;
        double shortestDist = 100000;
        for (var entry : tasks.entrySet()) {
            if (entry.getKey().size() == 1 && entry.getValue().isEmpty()) {
                // It's a waypoint task with no agents assigned
                double dist = agent.getCoordinate().getDistance(entry.getKey().get(0));
                if (dist < shortestDist) {
                    shortestDist = dist;
                    bestTask = entry.getKey();
                }
            } else if (entry.getKey().size() > 1 && entry.getValue().isEmpty()) {
                // It's a patrol (or region) with noting assigned
                Coordinate nearestPoint = safeGetNearestPoint(entry.getKey());
                double dist = agent.getCoordinate().getDistance(nearestPoint);
                if (dist < shortestDist) {
                    shortestDist = dist;
                    bestTask = entry.getKey();
                }
            }
        }

        return bestTask;
    }

    private Coordinate safeGetNearestPoint(List<Coordinate> coords) {
        PatrolTask pt = new PatrolTask("temp", Task.TASK_PATROL, coords, Coordinate.findCentre(coords));
        return pt.getNearestPointAbsolute(agent);
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
            tasks.get(taskCoords).add(getId());  // must add ourselves
            agent.setRoute(taskCoords);
            resume();
        } catch (Exception e) {
            // Failed to do this, probably due to incorrect information. We have to allow this mistake to happen,
            // as otherwise we are letting globally known information leak into the process
            tempPlaceNewTask(taskCoords);
            currentTask = taskCoords;
            tasks.get(taskCoords).add(getId());  // must add ourselves
            agent.setRoute(taskCoords);
            resume();
        }
    }

    public void manualSetTask(Coordinate c) {
        agentProgrammer.manualSetTask(c);

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
     * @param coords Task's coordinate
     */
    protected void tempPlaceNewTask(List<Coordinate> coords) {
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

    /**
     * This shouldn't be used by controller, as it handles Task objects. You can create tasks as coordinate lists and
     * use the TempPlaceTask, or you can set a route for an agent, but probably best not to use this.
     * Adds a task to this agent. Passed through to receivers, when adding tasks from the user
     * @param item Task to add
     */
    protected void addTask(Task item) {
        List<Coordinate> thisTask;
        if (item instanceof WaypointTask wt) {
            thisTask = Collections.singletonList(wt.getCoordinate());
        } else if (item instanceof VisitTask vt) {
            // TODO actually handle these right
            thisTask = Collections.singletonList(vt.getCoordinate());
        } else if (item instanceof PatrolTask pt) {
            thisTask = pt.getPoints();
        } else {
            // We don't know how to handle this task type
            return;
        }
        tasks.put(thisTask, new ArrayList<>());
    }

    /**
     * This shouldn't be used by controller, as it handles Task objects.
     * Removes a task for this agent. Passed through to receivers, when removing tasks by the user
     * @param item Task to add
     */
    protected void removeTask(Task item) {
        // For now we just treat this as a normal completion, which may be ambiguous
        //  However, it is hard to distinguish a completed or deleted patrol task without additional context
        //  i.e: did the user cancel it because it wasn't useful or because it did its job?
        Coordinate thisCoord = item.getCoordinate();
        completedTasks.add(thisCoord);

        if (agent instanceof Hub) {
            agent.tempRemoveTask(thisCoord);
        }

        if (item instanceof PatrolTask pt) {
            tasks.remove(pt.getPoints());
        } else {
            tasks.remove(Collections.singletonList(thisCoord));
        }

    }

    /**
     * Uses the pre-existing flocking algorithm based on attraction and repulsion
     */
    protected void flockWithAttractionRepulsion(double senseRadius, double tooCloseRadius){
        agent.adjustFlockingHeading(senseRadius, tooCloseRadius);
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
        broadcast("CUSTOM;"+opCode+";"+payload, communicationRange);
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
        double xDist = agent.getCoordinate().getLongitude() - home.getLongitude();
        double yDist = agent.getCoordinate().getLatitude() - home.getLatitude();
        // using tan rule to find angle from hub to agent
        double theta = Math.atan(yDist / xDist);
        //double radius = 0.8 * (((double) SENSE_RANGE /1000)/6379.1);  // 20% into the radius of the hub (includes metre to
        //  latlng calc

        // approx 111,111m = 1deg latlng
        double radius = 0.8 * (double) communicationRange / 111111;

        // x,y = rcos(theta), rsin(theta); If in the negative x, we must invert (=== adding pi rads)
        double xRes;
        double yRes;
        if (xDist < 0) {
            xRes = home.getLongitude() - (radius * Math.cos(theta));
            yRes = home.getLatitude() - (radius * Math.sin(theta));
        } else {
            xRes = home.getLongitude() + (radius * Math.cos(theta));
            yRes = home.getLatitude() + (radius * Math.sin(theta));
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
    public List<Coordinate> getCompletedTasks() {
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
        return communicationRange;
    }

    /**
     * Checks whether the given agent is connected (known) to this agent
     * Be careful here, it uses some Simulator info, but nothing that informs the agents
     * Based on whether we have a recent record of this
     * @param agent The agent to check for
     * @return If it is connected to this agent
     */
    public boolean checkForConnection(Agent agent) {
        String id = "";
        if (agent instanceof AgentProgrammed ap) {
            id = ap.getNetworkId();
        } else if (agent instanceof AgentCommunicating ac) {
            id = ac.getNetworkId();
        }

        for (var entry : neighbours.entrySet()) {
            if (entry.getKey().equals(id)) {
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

    public void setCommunicationRange(double communicationRange) {
        this.communicationRange = (int) Math.round(communicationRange);
    }

    public boolean checkForDuplicateAssignment() {
        try {
            if (currentTask != null && !currentTask.isEmpty()) {
                // This means more than 1 user assigned
                return tasks.get(currentTask).size() > 1;
            }
            return false;
        } catch (Exception e) {
            // Task not found
            return false;
        }
    }

    /**
     * Returns true if any task has this agent ID in its assignment list
     * @param a
     * @return
     */
    public boolean agentHasTask(String a) {
        return tasks.entrySet().stream().anyMatch(pair -> pair.getValue().contains(a));
    }

    public void issueOrder(String a, Coordinate c) {
        orders.put(Collections.singletonList(c), a);
    }

    public List<Coordinate> findOwnOrder() {
        //System.out.println(getId() + " -> " + orders.entrySet());
        for (var entry : orders.entrySet()) {
            if (entry.getValue().contains(getId())) {
                return entry.getKey();
            }
        }
        return null;
    }

    public AgentProgrammer getAgentProgrammer() {
        return agentProgrammer;
    }

    public void teleport(Coordinate myTask) {
        agent.setCoordinate(myTask);
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
