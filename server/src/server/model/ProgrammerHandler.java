package server.model;

import server.model.task.Task;

import java.io.Serializable;
import java.util.*;
import java.util.logging.Logger;

public class ProgrammerHandler implements Serializable {
    AgentProgrammed agent;

    private final transient Logger LOGGER = Logger.getLogger(AgentProgrammed.class.getName());

    private final transient AgentProgrammer agentProgrammer;

    private HashMap<String, Position> neighbours;

    private List<Coordinate> currentTask = new ArrayList<>();
    private HashMap<List<Coordinate>, List<String>> tasks;
    private ArrayList<Coordinate> completedTasks;

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
            broadcastAll();
        }
    }

    private void broadcastAll() {
        declareSelf(100);
        for (var entry : tasks.entrySet()){
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
            broadcast(sb.toString(), 100);

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
        agent.moveTowardsDestination();

    }

    protected void completeTask(){
        List<Coordinate> coords = currentTask;
        if (coords.size() > 1) {
            completedTasks.add(Coordinate.findCentre(coords));
        } else {
            completedTasks.add(coords.get(0));
        }
        tasks.remove(currentTask);

        StringBuilder sb = new StringBuilder();
        sb.append("COMPLETED");
        for (Coordinate c : coords) {
            sb.append(";");
            sb.append(c.latitude);
            sb.append(",");
            sb.append(c.longitude);

        }
        broadcast(sb.toString(), 100);
    }

    private void receiveCompleteTask(List<Coordinate> coords){
        Coordinate coord;
        if (coords.size() > 1) {
            coord = Coordinate.findCentre(coords);
        } else {
            coord = coords.get(0);
        }
        completedTasks.add(coord);
        tasks.remove(coords);
        //if (getTaskById(getCurrentTaskId()).equals(coords)) {
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
        if (currentTask.size() == 0) {
            String msg = "HS_GREET;" + agent.getNetworkId() + ";"
                    + agent.getCoordinate().getLatitude() + ","
                    + agent.getCoordinate().getLongitude() + ","
                    + agent.getHeading();
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
                    + agent.getHeading()
                    + ";" + sb;
            broadcast(msg, radius);
        }
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
                    if (neighbours.get(id) != null) {
                        Position position = new Position(ap.getCoordinate(), 0d);
                        neighbours.put(id, position);
                    } else {
                        neighbours.get(id).setLocation(ap.getCoordinate());
                    }

                }
            } catch (Exception e) {
                LOGGER.severe("Unreceived message. Probably due to this not being a programmed agent.");
            }
        }

    }

    private boolean checkTaskPossible(List<Coordinate> coords) {
        Coordinate centre = Coordinate.findCentre(coords);

        for (Coordinate c1 : completedTasks) {
            if (c1.equals(centre)) {
                // already done
                return false;
            }
        }

        for (var entry : tasks.entrySet()) {
            Coordinate thisTaskCentre = Coordinate.findCentre(entry.getKey());
            if (centre.equals(thisTaskCentre)) {
                // If any coordinate is different, this task can't be a match
                return false;
            }

        }
        return true;
    }





    protected void receiveMessage(String message) {
        // TODO as of a recent java update, we can use regex-based case statements here in future
        try {
            if (message.contains("HS_GREET;")){
                // Handshake introduction
                String id = message.split(";")[1];
                String locX = message.split(";")[2].split(",")[0];
                String locY = message.split(";")[2].split(",")[1];
                String heading = message.split(";")[2].split(",")[2];
                //Coordinate coord = getAgentByNetworkId(id).getCoordinate();
                Position newPos = new Position(new Coordinate(Double.parseDouble(locX), Double.parseDouble(locY)), Double.parseDouble(heading));
                neighbours.put(id, newPos);
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
                    }
                }

            } else if (message.contains("HS_REPLY")){
                // Handshake response, currently ignored

            } else if (message.contains("TASK_WAYPOINT")) {
                String x = message.split(";")[1].split(",")[0];
                String y = message.split(";")[1].split(",")[1];
                Coordinate thisCoord = new Coordinate(Double.parseDouble(x), Double.parseDouble(y));
                List<Coordinate> thisTask = new ArrayList<>();
                thisTask.add(thisCoord);
                if (checkTaskPossible(thisTask)) {
                    tasks.put(thisTask, new ArrayList<>());
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
                while (msgIt.hasNext()){
                    String thisLine = msgIt.next();
                    String x = thisLine.split(",")[0];
                    String y = thisLine.split(",")[1];
                    Coordinate coord = new Coordinate(Double.parseDouble(x), Double.parseDouble(y));
                    thisTask.add(coord);
                }
                receiveCompleteTask(thisTask);

            }

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public HashMap<List<Coordinate>, List<String>> getTasks() {
        return tasks;
    }

    public List<Coordinate> getNearestTask() {
        List<Coordinate> bestTask = new ArrayList<>();
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

    public List<Coordinate> getNearestEmptyTask(){
        List<Coordinate> bestTask = new ArrayList<>();
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

    public double calculateAverageNeighbourHeading() {
        double totalHeading = 0;
        for (var entry : neighbours.entrySet()) {
            totalHeading += entry.getValue().heading;
        }
        return totalHeading / neighbours.size();
    }

    public void setTask(List<Coordinate> taskCoords) {
        agent.setAllocatedTaskByCoords(taskCoords);
        currentTask = taskCoords;
        agent.setRoute(taskCoords);

    }

    protected List<String> getAgentsAssigned(List<Coordinate> task) {
        return tasks.get(task);
    }

    public void tempPlaceNewTask(String type, List<Coordinate> coords) {
        agent.tempPlaceNewTask(type, coords);
        tasks.put(coords, new ArrayList<>());
    }

    public boolean hasNeighbours() {
        return (neighbours.size() != 0);
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
    private class Position {
        private Coordinate location;
        private Double heading;

        public Position(Coordinate loc, Double hd){
            location=loc;
            heading=hd;
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
    }



}
