package server.model.task;

import com.google.gson.*;
import server.Simulator;
import server.model.*;
import server.model.agents.Agent;
import server.model.agents.AgentCommunicating;
import server.model.agents.AgentProgrammed;
import server.model.agents.AgentVirtual;

import java.io.Serializable;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Abstract class for all tasks
 * @author Feng Wu, Yuai Liu
 */
/* Edited by Will */
public abstract class Task extends MObject implements Serializable {

    private static final long serialVersionUID = 5561040348988016571L;
    private static final transient Logger LOGGER = Logger.getLogger(WaypointTask.class.getName());

    public static final int STATUS_TODO = 0;
    public static final int STATUS_DOING = 1;
    public static final int STATUS_DONE = 2;
    public static final int STATUS_DONE_PENDING = 3;  // For a completed task that needs to be reported when info gets to the HUB

    public static final int TASK_WAYPOINT = 0;
    public static final int TASK_MONITOR = 1;
    public static final int TASK_PATROL = 2;
    public static final int TASK_REGION = 3;
    public static final int TASK_DEEP_SCAN = 4;
    public static final int TASK_SHALLOW_SCAN = 5;
    public static final int TASK_VISIT = 6;
    public static final int TASK_GROUND = 7;


    //Used in client
    private final List<Agent> agents; //Serialised to just agent ids.
    protected int group;
    protected double priority;
    private int type;

    // Not on client but used on server
    protected transient int status;
    protected transient double startTime;

    public Task(String id, int type, Coordinate coordinate) {
        super(id, coordinate);

        this.type = type;

        group = 1;
        priority = 1.0;
        agents = new ArrayList<>();
    }

    /**
     * Perform an in progress task.
     * @return True if task is complete.
     */
    abstract boolean perform();

    public void complete() {
        for (Agent a : agents) {
            if (a instanceof AgentVirtual av) {
                Task t = av.getNextTaskFromQueue();
                if (t != null) {
                    Simulator.instance.getAllocator().putInTempAllocation(a.getId(), t.getId(), false);
                    Simulator.instance.getAllocator().confirmAllocation(Simulator.instance.getState().getTempAllocation());
                } else {
                    a.stop();
                }
            }
        }
        Simulator.instance.getTaskController().deleteTask(this.getId(), true);
        //System.out.println("INT5: " + a.getId() + " -> " + a.getAgentTeam());
        /*
        try {
            if (!a.getAgentTeam().isEmpty() && a.getAgentTeam().stream().allMatch(n -> Simulator.instance.getState().getAgent(n).getTask() == null)) {
                a.getAgentTeam().stream().filter(a1 -> !a.getId().equals(a1)).forEach(a1 -> Simulator.instance.getState().getAgent(a1).getAgentTeam().clear());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

         */
        //System.out.println("INT6: " + a.getId() + " -> " + a.getAgentTeam());
        LOGGER.info(String.format("%s; TSKCMP; Task completed (id); %s", Simulator.instance.getState().getTime(), this.getId()));
    }

    /**
     * Gets all agents within [10m] of this agent
     * @return ArrayList of arrived agents
     */
    protected ArrayList<Agent> getArrivedAgents(){
        ArrayList<Agent> arrivedAgents = new ArrayList<>();
        for (Agent a : Simulator.instance.getState().getAgents()) {
            //if (a.getCoordinate().getDistance(this.getCoordinate()) < 10) {  //  10m for now
            // 10m doesn't work for an agent speed that's too high. Maybe a better option is for the agent to slow down
            if (a.getCoordinate().getDistance(this.getCoordinate()) < a.getSpeed()) {
                arrivedAgents.add(a);
            }
        }
        return arrivedAgents;
    }

    /**
     * Step a task based on changes to its agents and progress.
     * @return True if the task has been completed, false otherwise.
     */
    public boolean step() {
        if (status == STATUS_DONE_PENDING) {
            // Completed, but not yet reported to HUB
            return false;
        } else if (status == STATUS_DONE) {
            // Completed and should be reported
            return true;
        }
        ArrayList<Agent> arrivedAgents = getArrivedAgents();
        if (arrivedAgents.size() > 0) {
            // An agent has found this task
            if (Simulator.instance.getState().isCommunicationConstrained()) {
                // It is NOT programmed or communicating
                setStatus(Task.STATUS_DONE_PENDING);
                if (this instanceof VisitTask vt) {
                    vt.triggerReturnHome();
                }
            }
        }

        if (status == STATUS_TODO) {
            boolean hasAnyAgentArrived = false;
            for (Agent agent : getAgents()) {
                if (agent.isFinalDestinationReached()) {
                    agent.setWorking(true);
                    hasAnyAgentArrived = true;
                }
            }

            if (hasAnyAgentArrived) {
                setStatus(Task.STATUS_DOING);
                setStartTime(Simulator.instance.getState().getTime());
            }
        }

        if (status == Task.STATUS_DOING) {
            for (Agent agent : getAgents())
                if (agent.isFinalDestinationReached())
                    agent.setWorking(true);
            if(perform())
                setStatus(Task.STATUS_DONE);
            if(agents.isEmpty())
                setStatus(Task.STATUS_TODO);
        }

        return status == Task.STATUS_DONE;
    }

    public List<Agent> getAgents() {
        return agents;
    }

    public void addAgent(Agent agent) {
        // Only add agent if none of the existing agents have the same id.
        if (agents.stream().noneMatch(o -> o.getId().equalsIgnoreCase(agent.getId())))
            agents.add(agent);
    }

    public void removeAgent(String agentId) {
        agents.removeAll(agents.stream().filter(o -> o.getId().equals(agentId)).collect(Collectors.toList()));
    }

    public void clearAgents() {
        agents.clear();
    }

    public int getGroup() {
        return group;
    }

    public void setGroup(int group) {
        this.group = group;
    }

    public double getPriority() {
        return priority;
    }

    public void setPriority(double priority) {
        this.priority = priority;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public int getStatus() {
        return this.status;
    }

    public void setStartTime(double startTime) {
        this.startTime = startTime;
    }

    public double getStartTime() {
        return this.startTime;
    }

    public int getType() {
        return this.type;
    }

    public JsonObject serialize(JsonSerializationContext context) {
        JsonObject jsonObj = new JsonObject();

        //Serialise agents to list of ids
        JsonArray agentsJson = new JsonArray();
        for(Agent agent : agents)
            agentsJson.add(new JsonPrimitive(agent.getId()));
        jsonObj.add("agents", agentsJson);

        //Add everything else (using default serialisation)
        jsonObj.add("group", context.serialize(group));
        jsonObj.add("priority", context.serialize(priority));
        jsonObj.add("coordinate", context.serialize(getCoordinate()));
        jsonObj.add("id", context.serialize(getId()));
        jsonObj.add("type", context.serialize(getType()));

        return jsonObj;
    }

    @SuppressWarnings("Convert2Lambda")
    public static JsonSerializer taskSerializer = new JsonSerializer<Task>() {
        @Override
        public JsonElement serialize(Task task, Type type, JsonSerializationContext context) {
            return task.serialize(context);
        }
    };

}
