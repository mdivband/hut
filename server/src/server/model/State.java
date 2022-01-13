package server.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import server.Allocator;
import server.Simulator;
import server.model.agents.*;
import server.model.hazard.Hazard;
import server.model.target.Target;
import server.model.task.Task;
import tool.GsonUtils;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class State {

    public static final int GAME_TYPE_SANDBOX = 0;
    public static final int GAME_TYPE_SCENARIO = 1;
    private final static transient Logger LOGGER = Logger.getLogger(Allocator.class.getName());
    private boolean inProgress;

    private String gameId;
    private String gameDescription;
    private int gameType;
    private String allocationMethod = "maxsum";
    private Boolean flockingEnabled = false;
    private double time;
    private boolean editMode;

    private String prov_doc;

    private final Collection<Agent> agents;
    private final Collection<AgentGhost> ghosts;
    private final Collection<Task> tasks;
    private final Collection<Task> completedTasks;
    private final Collection<Hazard> hazards;

    //State information for scenarios
    private Coordinate gameCentre;
    private final Collection<Target> targets;

    //Updated on server but only used on client.
    @SuppressWarnings("unused")
    private boolean allocationUndoAvailable;
    @SuppressWarnings("unused")
    private boolean allocationRedoAvailable;

    private Map<String, String> allocation;
    //Allocation that is WIP (i.e. not confirmed by user).
    private Map<String, String> tempAllocation;
    //Allocation created from dropped out agents.
    private Map<String, String> droppedAllocation;

    private HazardHitCollection hazardHits;

    private ArrayList<String> uiOptions = new ArrayList<>();
    private double uncertaintyRadius = 0;
    private double communicationRange = 0;
    private boolean communicationConstrained = false;

    // We could combine these, but it might be little more efficient to let them stay separate
    private Hub hub;
    private Coordinate hubLocation;


    public State() {
        agents = new ArrayList<>();
        ghosts = new ArrayList<>();
        tasks = new ArrayList<>();
        completedTasks = new ArrayList<>();
        targets = new ArrayList<>();
        hazards = new ArrayList<>();
        allocation = new ConcurrentHashMap<>();
        tempAllocation = new ConcurrentHashMap<>();
        droppedAllocation = new ConcurrentHashMap<>();
        hazardHits = new HazardHitCollection();

        allocationUndoAvailable = false;
        allocationRedoAvailable = false;

        reset();
    }

    public synchronized void reset() {
        time = 0;
        editMode = false;
        inProgress = false;

        agents.clear();
        ghosts.clear();
        tasks.clear();
        completedTasks.clear();
        targets.clear();
        hazards.clear();
        allocation.clear();
        tempAllocation.clear();
        hazardHits.clear();

        hazardHits.init();
    }

    @Override
    public synchronized String toString() {
        return GsonUtils.toJson(this);
    }

    public Target getTarget(String targetId) {
        return getById(targets, targetId);
    }

    public Task getTask(String taskId) {
        return getById(tasks, taskId);
    }

    public Agent getAgent(String agentId) {
        return getById(agents, agentId);
    }

    public Hazard getHazard(String hazardId) {
        return getById(hazards, hazardId);
    }

    public void add(IdObject item) {
        if(item instanceof Target)
            add(targets, (Target) item);
        else if(item instanceof  Task) {
            add(tasks, (Task) item);
            // For programmed agents:
            for (Agent a : agents) {
                if (a instanceof AgentHubProgrammed abs) {
                    abs.addTaskFromUser((Task) item);
                }
            }
        } else if(item instanceof Agent)
            add(agents, (Agent) item);
        else if(item instanceof Hazard)
            add(hazards, (Hazard) item);
        else
            throw new RuntimeException("Cannot add item to state, unrecognised class - " + item.getClass().getSimpleName());

    }

    public void remove(IdObject item) {
        if(item instanceof Target)
            remove(targets, (Target) item);
        else if(item instanceof  Task) {
            remove(tasks, (Task) item);
            for (Agent a : agents) {
                if (a instanceof AgentHubProgrammed abs) {
                    abs.removeTaskFromUser((Task) item);
                }
            }
        }
        else if(item instanceof  Agent)
            remove(agents, (Agent) item);
        else
            throw new RuntimeException("Cannot remove item from state, unrecognised class - " + item.getClass().getSimpleName());
    }

    private <T extends IdObject> void add(Collection<T> items, T item) {
        if(getById(items, item.getId()) != null)
            throw new RuntimeException("Cannot add item to list - list already contains item with given id.");
        items.add(item);
    }

    private <T extends IdObject> boolean remove(Collection<T> items, T item) {
        if((item = getById(items, item.getId())) == null)
            return false;
        items.remove(item);
        return true;
    }

    private <T extends IdObject> T getById(Collection<T> items, String id) {
        List<T> matching = items.stream().filter(o -> o.getId().equals(id)).collect(Collectors.toList());
        if(matching.size() == 0)
            return null;
        if(matching.size() > 1)
            throw new RuntimeException("Two objects found with same id!");
        return matching.get(0);
    }

    //Getters and setters below
    public synchronized double getTime() {
        return time;
    }

    public synchronized void setTime(double time) {
        this.time = time;
    }

    public synchronized void incrementTime(double increment) {
        setTime(this.time + increment);
    }

    public synchronized boolean isEditMode() {
        return editMode;
    }

    public synchronized void setEditMode(boolean editMode) {
        this.editMode = editMode;
    }

    public synchronized String getGameId() {
        return gameId;
    }

    public synchronized void setGameId(String gameId) {
        this.gameId = gameId;
    }

    public synchronized int getGameType() {
        return gameType;
    }

    public String getGameDescription() {
        return gameDescription;
    }

    public void setGameDescription(String gameDescription) {
        this.gameDescription = gameDescription;
    }

    public synchronized void setGameType(int gameType) {
        this.gameType = gameType;
    }

    public synchronized void setGameCentre(Coordinate gameCentre) {
        this.gameCentre = gameCentre;
    }

    public synchronized void setAllocationMethod(String allocationMethod) {
        this.allocationMethod = allocationMethod;
    }

    public synchronized String getAllocationMethod() {
        return this.allocationMethod;
    }

    public synchronized void setFlockingEnabled(Boolean flockingEnabled) {
        this.flockingEnabled = flockingEnabled;
    }

    public synchronized Boolean isFlockingEnabled() {
        return this.flockingEnabled;
    }

    public Collection<Target> getTargets() {
        return targets;
    }

    public Collection<Task> getTasks() {
        return tasks;
    }

    public Collection<Agent> getAgents() {
        return agents;
    }

    public Map<String, String> getAllocation() {
        return allocation;
    }

    public Collection<Hazard> getHazards() {
        return hazards;
    }

    public void setAllocation(Map<String, String> allocation) {
        this.allocation = allocation;
    }

    public Map<String, String> getTempAllocation() {
        return tempAllocation;
    }

    public void setTempAllocation(Map<String, String> tempAllocation) {
        if(tempAllocation == null)
            this.tempAllocation.clear();
        else
            this.tempAllocation = tempAllocation;
    }

    public Map<String, String> getDroppedAllocation() {
        return droppedAllocation;
    }

    public synchronized void setProvDoc(String prov_doc) {
        this.prov_doc = prov_doc;
    }

    public void setAllocationUndoAvailable(boolean allocationUndoAvailable) {
        this.allocationUndoAvailable = allocationUndoAvailable;
    }

    public void setAllocationRedoAvailable(boolean allocationRedoAvailable) {
        this.allocationRedoAvailable = allocationRedoAvailable;
    }

    public void addCompletedTask(Task task) {
        this.completedTasks.add(task);
    }

    public boolean isInProgress() {
        return inProgress;
    }

    public void setInProgress(boolean inProgress) {
        this.inProgress = inProgress;
    }

    /**
     * Adds the given option to the ui settings
     * @param option String enumerated option
     */
    public void addUIOption(String option) {
        uiOptions.add(option);
    }

    /**
     * Setter for the uncertainty radius of agents
     * @param uncertaintyRadius Radius for uncertainty of agent position
     */
    public void setUncertaintyRadius(double uncertaintyRadius) {
        this.uncertaintyRadius = uncertaintyRadius;
    }

    /**
     * Setter for the communication range of agents
     * @param communicationRange Radius for communication between agents
     */
    public void setCommunicationRange(double communicationRange) {
        this.communicationRange = communicationRange;
    }

    /**
     * Getter for the communication range of agents
     * @return Radius for communication between agents
     */
    public double getCommunicationRange() {
        return communicationRange;
    }

    public synchronized void addHazardHit(int type, Coordinate location) {
        hazardHits.add(type, location);
    }

    public void decayHazardHits() {
        hazardHits.decayAll();
    }

    /**
     * Searches and gets the task with the given coordinate
     * @param coordinate The coordinate to check
     * @return the Task if found (null otherwise)
     */
    public Task getTaskByCoordinate(Coordinate coordinate) {
        for (Task t : tasks) {
            if(t.getCoordinate().equals(coordinate)) {
                return t;
            }
        }
        return null;
    }

    /**
     * Searches and gets the target with the given coordinate
     * @param coordinate The coordinate to check
     * @return the Target if found (null otherwise)
     */
    public Target getTargetByCoordinate(Coordinate coordinate) {
        for (Target t : targets) {
            if(t.getCoordinate().equals(coordinate)) {
                return t;
            }
        }
        return null;
    }

    /**
     * Getter for Hub location
     * @return Coordinate of hub (can be null if not set)
     */
    public Coordinate getHubLocation() {
        return hubLocation;
    }

    /**
     * Setter for Hub location
     * @param hubLocation Coordinate of hub (can be null)
     */
    public void setHubLocation(Coordinate hubLocation) {
        this.hubLocation = hubLocation;
    }

    /**
     * Updates the visibility of all Agents
     */
    public void updateAgentVisibility() {
        for (Agent agent : agents) {
            if (!(agent instanceof Hub)) {
                // Is programmed/communicating, see if it's connected
                boolean connected = Simulator.instance.getAgentController().checkHubConnection(hub, agent);
                if (connected && !agent.isVisible()) {
                    agent.setVisible(true);
                } else if (!connected && agent.isVisible()) {
                    // Has left the range
                    agent.setVisible(false);
                    addGhost(agent);
                }
            } else if (!agent.isVisible()) {
                // To make sure the hub is always visible
                agent.setVisible(true);
            }
        }
    }

    /**
     * Adds a ghost marker for the given agent
     * @param agent The Agent to replace (copies this position, heading, route)
     */
    private void addGhost(Agent agent) {
        ghosts.add(new AgentGhost(agent));
    }

    /**
     * Updates, and removes if required, all ghost agents
     */
    public void updateGhosts() {
        ArrayList<AgentGhost> ghostsToRemove = new ArrayList<>();
        for (AgentGhost ghost : ghosts) {
            String baseId = ghost.getId().split("_")[0];
            Agent equivalentAgent = agents.stream().filter(agent -> agent.getId().equals(baseId)).findFirst().get();
            boolean connected = Simulator.instance.getAgentController().checkHubConnection(hub, equivalentAgent);

            if (connected) {
                // Has re-entered the range. We must kill this ghost and reinstate the real agent
                ghostsToRemove.add(ghost);
            }
        }
        try {
            for (AgentGhost ghostToRem : ghostsToRemove) {
                ghosts.remove(ghostToRem);
                String baseId = ghostToRem.getId().split("_")[0];  // Removes the "_ghost" part
                // Line below just finds first (and only) agent with this ID. Shouldn't ever fail if everything else is correct
                Agent agentToReinstate = agents.stream().filter(agent -> agent.getId().equals(baseId)).findFirst().get();
                agentToReinstate.setVisible(true);
            }
        } catch (Exception e) {
            System.out.println("Error reinstating agent: " + e);
        }
    }

    /**
     * Moves all ghost markers based on believed route
     */
    public void moveGhosts() {
        for (AgentGhost ghost : ghosts) {
            ghost.step(true);  // The argument here allows us to get ghosts to try to simulate flocking too
            if (ghost.isAtHome() && ghost.isVisible()) {
                // It has returned to the Hub and we now know nothing about it, so let's hide it, otherwise it clutters
                //  and confuses the interface
                ghost.setVisible(false);
            } else if (!ghost.isAtHome() && !ghost.isVisible()) {
                // For if we rediscover this agent away from home
                ghost.setVisible(true);
                ghost.setAtHome(false);
            }  // Otherwise leave it be, it's either: Visible and not home yet, or At home and invisible
        }
    }

    /**
     * Senses all ghosts near to the given ghost. Used to model ghost flocking
     * @param ghostToSense AgentGhost to check around
     * @param radius Radius distance to search within
     * @return List of all neighbouring ghosts
     */
    public List<Agent> senseNeighbouringGhosts(AgentGhost ghostToSense, int radius) {
        List<Agent> neighbours = new ArrayList<>();
        for (AgentGhost ghost : ghosts) {
            if (ghostToSense.getCoordinate().getDistance(ghost.getCoordinate()) < radius || !ghostToSense.getId().equals(ghost.getId())) {
                // This ghost is close enough
                neighbours.add(ghost);
            }
        }
        return neighbours;
    }

    /**
     * Sets the hub to this given AgentHub
     * @param hub The hub to set
     */
    public void attachHub(Agent hub) {
        this.hub = (Hub) hub;
    }

    /**
     * Getter for whether the simulation is communication constrained
     * @return boolean value for whether simulation is communication constrained
     */
    public boolean isCommunicationConstrained() {
        return communicationConstrained;
    }

    /**
     * Setter for whether the simulation is communication constrained
     * @param communicationConstrained boolean value for whether simulation is communication constrained
     */
    public void setCommunicationConstrained(Boolean communicationConstrained) {
        this.communicationConstrained = communicationConstrained;
    }

    private class HazardHit {
        private Coordinate location;
        private double weight;
        private transient double decayRate;

        private HazardHit(Coordinate location, double decayRate) {
            this.location = location;
            this.weight = 1;
            this.decayRate = decayRate;
        }

        private boolean decay() {
            this.weight -= decayRate;
            return this.weight < 0;
        }
    }

    public class HazardHitCollection {
        private transient Map<Integer, Map<Coordinate, HazardHit>> hazardHits;

        private HazardHitCollection() {
            this.hazardHits = new ConcurrentHashMap<>();
        }

        private void init() {
            hazardHits.put(Hazard.NONE, new ConcurrentHashMap<>());
            hazardHits.put(Hazard.FIRE, new ConcurrentHashMap<>());
            hazardHits.put(Hazard.DEBRIS, new ConcurrentHashMap<>());
        }

        private void add(int type, Coordinate location) {
            if(this.hazardHits.containsKey(type)) {
                /* Hits should only be registered if they are far enough from all
                 * other hits. This is done by storing a rounded coordinate to provide
                 * a quick way to see if a hit is far enough away from all the other hits.
                 *
                 * The actual coordinate is kept, and this is the one that should be rendered
                 * so the heatmap does not appear 'blocky'.
                 */
                double lat = location.latitude;
                double lng = location.longitude;
                double roundedLat = Math.round(lat * 10000D) / 10000D;
                double roundedLng = Math.round(lng * 10000D) / 10000D;
                Coordinate roundedCoord = new Coordinate(roundedLat, roundedLng);
                Map<Coordinate, HazardHit> coordMap = this.hazardHits.get(type);
                HazardHit hit;
                if(type == -1)
                    hit = new HazardHit(location, 0.001);
                else
                    hit = new HazardHit(location, 0);
                coordMap.put(roundedCoord, hit);
            }
            else {
                LOGGER.severe("Could not register hazard hit - not list for hazard type " + type);
            }
        }

        private void decayAll() {
            for(Map<Coordinate, HazardHit> m : hazardHits.values()) {
                for(Map.Entry<Coordinate, HazardHit> e : m.entrySet())
                    if(e.getValue().decay())
                        m.remove(e.getKey());
            }
        }

        private void clear() {
            hazardHits.clear();
        }
    }

    public static JsonSerializer hazardHitsSerializer = new JsonSerializer<HazardHitCollection>() {
        @Override
        public JsonElement serialize(HazardHitCollection hazardHitCollection, Type type, JsonSerializationContext context) {
            JsonObject jsonObject = new JsonObject();
            jsonObject.add("-1", context.serialize(hazardHitCollection.hazardHits.get(-1).values()));
            jsonObject.add("0", context.serialize(hazardHitCollection.hazardHits.get(0).values()));
            jsonObject.add("1", context.serialize(hazardHitCollection.hazardHits.get(1).values()));
            return jsonObject;
        }
    };
}
