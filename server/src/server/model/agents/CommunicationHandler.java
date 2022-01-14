package server.model.agents;

import server.Simulator;
import server.model.Coordinate;
import server.model.Hub;
import server.model.target.Target;

import java.util.*;
import java.util.logging.Logger;

/**
 * This class serves as a lightweight, cut-down version of the ProgrammerHandler.
 * It essentially only serves to hold the same internal model as a normal ProgrammerHandler and passes it around,
 *  allowing a mixture of agent types.
 * A neater solution would inherit more information, as this way does incur some duplication of code (bad practise),
 *  but for now this is easier
 */
public class CommunicationHandler {
    private final transient Logger LOGGER = Logger.getLogger(AgentProgrammed.class.getName());
    private static final double TARGET_SENSE_RANGE = 50;  // Number of metres around that the agent can detect targets
    private static final int AGENT_TIMEOUT = 6; // (6x200ms = every 1.2 second) ~= 1 real-life seconds
    private static final int pingTimeout = 3; // (6x200ms = every 1.2 second) ~= 1 real-life seconds

    protected transient AgentCommunicating agent;
    private int communicationRange = 250; // The max (and default) radius used for sensing neighbours etc

    private HashMap<String, ProgrammerHandler.Position> neighbours;  // Stores the known other agents (including non-neighbours)
    private List<Coordinate> currentTask = new ArrayList<>();
    private final HashMap<List<Coordinate>, List<String>> tasks;
    private final List<List<Coordinate>> completedTasks;
    private final List<Coordinate> foundTargets;

    private Coordinate home;
    private int stepCounter = 0;
    private int pingCounter = 0;

    /***
     * Constructor. Connects the agent to the programmer s.t. this class behaves akin to an MVC controller
     * @param connectedAgent The agent that this handler controls
     */
    public CommunicationHandler(AgentCommunicating connectedAgent){
        this.agent = connectedAgent;
        neighbours = new HashMap<>();
        tasks = new HashMap<>();
        completedTasks = new ArrayList<>();
        foundTargets = new ArrayList<>();
    }

    /***
     * Called at every time step, we set up on the first run, then pass through to the programmer after
     */
    public void step() {
        if (agent.getNetworkId().equals("")) {
            // Must perform setup on the first step, otherwise they can't find each other
            agent.setNetworkID(agent.generateRandomTag());
            declareSelf(communicationRange);
        } else if (pingCounter >= pingTimeout) {
            declareSelf(communicationRange);
            checkForTargets(); // TODO this check may not be needed for virtuals
            declareTargets(communicationRange);
            pingCounter = 0;
        }
        pingCounter++;
        stepCounter++;
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

    /**
     * Checks for targets around this location using the TARGET_SENSE_RANGE constant
     * NOTE: Uses server-side code to check for target's actual positions, so don't access or edit this
     */
    private void checkForTargets() {
        for (Target t : Simulator.instance.getState().getTargets()) {
            if (t.getCoordinate().getDistance(agent.getCoordinate()) < TARGET_SENSE_RANGE && !foundTargets.contains(t.getCoordinate())) {
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
     * The main communication protocol. Handles the given message, in a form of operator and operands, then acts on the
     * given information
     * @param message The entire message given
     */
    protected void receiveMessage(String message) {
        // TODO as of a recent java update, we can use regex-based case statements here in future
        try {
            if (message.contains("HS_GREET;")) {
                // Handshake introduction. Respond with location of self, and our assigned task, and known neighbours
                // TODO edit this to match the later format (no semicolon after id)
                String id = message.split(";")[1];
                String locX = message.split(";")[2].split(",")[0];
                String locY = message.split(";")[2].split(",")[1];
                String heading = message.split(";")[2].split(",")[2];
                String stopped = message.split(";")[2].split(",")[3];

                ProgrammerHandler.Position newPos = new ProgrammerHandler.Position(
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

                if (thisTask.size() != 0) {  // Checks it wasn't a TASK_NONE
                    if (tasks.get(thisTask) != null && !tasks.get(thisTask).contains(id)) {
                        tasks.get(thisTask).add(id);
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
                    ProgrammerHandler.Position neighbourNewPos = new ProgrammerHandler.Position(new Coordinate(Double.parseDouble(locX), Double.parseDouble(locY)), Double.parseDouble(heading), Boolean.parseBoolean(stopped), false, Integer.parseInt(timeStamp));
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
                    }
                }

            } else if(message.contains("DIAG")) {
                LOGGER.severe("Diagnostic message received: \"" + message.split(";")[1] + "\"");

            } else {
                //LOGGER.severe("Uncategorized message received: \"" + message + "\"");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
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
                    sb.append(c.getLatitude());
                    sb.append(",");
                    sb.append(c.getLongitude());
                }
                broadcast(sb.toString(), communicationRange);
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
                    sb.append(c.getLatitude());
                    sb.append(",");
                    sb.append(c.getLongitude());
                }
                broadcast(sb.toString(), communicationRange);
            }

        } catch (Exception e) {
            LOGGER.severe("Concurrent modification exception for the task list. This is a known error and needs fixing");
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
     * Handles a completion message. Adds this task to the completed list
     * @param coords Coordinate of the task
     */
    private void receiveCompleteTask(List<Coordinate> coords){
        if (!completedTasks.contains(coords)) {
            //System.out.println(agent.getId() + " Receiving new complete task at " + coords.get(0));
            completedTasks.add(coords);
        }

        tasks.remove(coords);
        if (currentTask.equals(coords)) {
            currentTask = new ArrayList<>();
            // Not sure if stopping or returning fixes the halting bug
            agent.stop();
            //goHome();
        }
    }

    public void setCommunicationRange(double communicationRange) {
        this.communicationRange = (int) Math.round(communicationRange);
    }

    public Coordinate getHome() {
        return home;
    }

    public double getSenseRange() {
        return communicationRange;
    }

    protected void completeTask(){
        System.out.println("Comm agent completing");
        // A very important check here, otherwise return home tasks mess up the propagation of completed tasks and it
        //  stops tasks being checked properly
        if (currentTask.size() > 0) {    // TODO this needs to get thje info properly
            List<Coordinate> coords = currentTask;
            tasks.remove(currentTask);

            StringBuilder sb = new StringBuilder();
            sb.append("COMPLETED");
            for (Coordinate c : coords) {
                sb.append(";");
                sb.append(c.getLatitude());
                sb.append(",");
                sb.append(c.getLongitude());

            }
            broadcast(sb.toString(), communicationRange);

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

    public void setCurrentTask(List<Coordinate> coords) {
        currentTask = coords;
    }
}
