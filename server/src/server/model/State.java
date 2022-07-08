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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.FileHandler;
import java.util.logging.LogManager;
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
    private String allocationMethod;
    private String allocationStyle;
    private String modelStyle;
    private Boolean flockingEnabled;
    private double time;
    private double timeLimit;
    private long scenarioEndTime;
    private int editMode;
    // editMode 1 = monitor
    //          2 = edit
    //          3 = images
    private boolean passthrough = false;
    private String nextFileName = "";
    private boolean deepAllowed = false;
    private boolean showReviewPanel = false;

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
    private Map<String, Double> varianceOptions = new HashMap<>();
    private Map<String, Double> noiseOptions = new HashMap<>();
    private double uncertaintyRadius = 0;
    private double communicationRange = 0;
    private boolean communicationConstrained = false;
    private double successChance;
    private double missionSuccessChance;
    private double missionSuccessOverChance;
    private double missionSuccessUnderChance;
    private double missionBoundedSuccessChance;
    private double missionBoundedSuccessUnderChance;
    private double missionBoundedSuccessOverChance;

    private Map<String, Double> scoreInfo;

    // We could combine these, but it might be little more efficient to let them stay separate
    private Hub hub;
    private Coordinate hubLocation;


    private String userName = "";
    private List<String> markers= new ArrayList<>();

    //                   ID->ImageName
    private final Map<String, String> storedImages = new ConcurrentHashMap<>(16);
    private final List<String> deepScannedIds = new ArrayList<>(16);
    private final List<String> pendingIds = new ArrayList<>(16);
    private ArrayList<String> handledTargets = new ArrayList<>();
    private HashMap<Coordinate, String> pendingMap = new HashMap<>();

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
        scoreInfo = new HashMap<>();
        successChance = 100.00;
        allocationUndoAvailable = false;
        allocationRedoAvailable = false;

        reset();
    }

    public synchronized void reset() {
        // Define defaults
        time = 0;
        timeLimit = 0;    // 0 means no time limit
        scenarioEndTime = 0; // 0 means no time limit
        editMode = 1;
        inProgress = false;
        allocationMethod = "maxsum";
        allocationStyle = "manualwithstop";
        flockingEnabled = false;
        successChance = 100.00;
        scoreInfo.clear();
        uncertaintyRadius = 0;
        communicationConstrained = false;
        communicationRange = 0;
        allocationMethod = "maxsum";
        flockingEnabled = false;
        uncertaintyRadius = 0;
        markers.clear();

        gameCentre = null;
        userName = "";
        modelStyle = "off";
        showReviewPanel = false;

        agents.clear();
        ghosts.clear();
        tasks.clear();
        completedTasks.clear();
        targets.clear();
        hazards.clear();
        allocation.clear();
        tempAllocation.clear();
        hazardHits.clear();
        uiOptions.clear();
        varianceOptions.clear();
        noiseOptions.clear();

        storedImages.clear();
        uiOptions.clear();

        hazardHits.init();
    }

    public void resetNext() {
        passthrough = false;
        nextFileName = "";
        scenarioEndTime = 0; // 0 means no time limit
        timeLimit = 0;    // 0 means no time limit
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
            Simulator.instance.getScoreController().setTotalTasks(getTasks().size());
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
            synchronized (agents) {
                remove(agents, (Agent) item);
            }
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

    public synchronized double getTimeLimit() {
        return timeLimit;
    }

    public synchronized void setTimeLimit(double timeLimit) {
        this.timeLimit = timeLimit;
        this.setScenarioEndTime(timeLimit);
    }

    public synchronized void incrementTimeLimit(double increment) {
        setTimeLimit(this.timeLimit + increment);
        this.setScenarioEndTime(timeLimit);
    }

    public synchronized long getScenarioEndTime() {
        return scenarioEndTime;
    }

    public synchronized void setScenarioEndTime() {
        if (this.timeLimit == 0) {
            this.scenarioEndTime = 0;
        } else {
            this.scenarioEndTime = System.currentTimeMillis() + (long)(this.timeLimit * 1000);
        }
    }

    private synchronized void setScenarioEndTime(double timeLimit) {
        if (timeLimit == 0) {
            this.scenarioEndTime = 0;
        } else {
            this.scenarioEndTime = System.currentTimeMillis() + (long) (timeLimit * 1000);
        }
    }

    public synchronized int getEditMode() {
        return editMode;
    }

    public synchronized void setEditMode(int editMode) {
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

    public String getAllocationStyle() {
        return allocationStyle;
    }

    public void setAllocationStyle(String allocationStyle) {
        this.allocationStyle = allocationStyle;
    }

    public String getModelStyle() {
        return modelStyle;
    }

    public void setModelStyle(String modelStyle) {
        System.out.println("setting style to " + modelStyle);
        this.modelStyle = modelStyle;
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
        synchronized (tasks) {
            return tasks;
        }
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

    public void setPassthrough(boolean passthrough) {
        this.passthrough = passthrough;
    }

    public boolean isPassthrough() {
        return passthrough;
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

    public void setSuccessChance(double successChance) {
        this.successChance = successChance;
    }

    public Hub getHub() {
        return hub;
    }

    public void addScoreInfo(String key, double value) {
        scoreInfo.put(key, value);
    }

    public Map<String, Double> getScoreInfo() {
        return scoreInfo;
    }

    public Collection<Task> getCompletedTasks() {
        return completedTasks;
    }

    public void setMissionSuccessChance(double successChance) {
        this.missionSuccessChance = successChance;
    }

    public void setMissionBoundedSuccessChance(double successChance) {
        this.missionBoundedSuccessChance = successChance;
    }

    public void setMissionBoundedSuccessUnderChance(double missionBoundedSuccessUnderChance) {
        this.missionBoundedSuccessUnderChance = missionBoundedSuccessUnderChance;
    }

    public void setMissionBoundedSuccessOverChance(double missionBoundedSuccessOverChance) {
        this.missionBoundedSuccessOverChance = missionBoundedSuccessOverChance;
    }

    public void setMissionSuccessOverChance(double overChance) {
        this.missionSuccessOverChance = overChance;
    }

    public void setMissionSuccessUnderChance(double underChance) {
        this.missionSuccessUnderChance = underChance;
    }

    /**
     * Place a k,v pair for a variance (per agent persistent random offset)
     * @param key
     * @param val
     */
    public void putVarianceOption(String key, Double val) {
        varianceOptions.put(key, val);
    }

    /**
     * Place a k,v pair for a variance (per agent persistent random offset)
     * @param key
     * @param val
     */
    public void putNoiseOption(String key, Double val) {
        noiseOptions.put(key, val);
    }

    public double getVarianceValue(String key) {
        return varianceOptions.get(key);
    }

    public double getNoiseValue(String key) {
        return noiseOptions.get(key);
    }

    /**
     * Calculates a zero-centred random value for the given key (noise or variance)
     * @param key
     * @return
     */
    public double calculateRandomValueFor(String key) {
        double bound;
        if (varianceOptions.containsKey(key)) {
            bound = varianceOptions.get(key);
        } else if (noiseOptions.containsKey(key)) {
            bound = noiseOptions.get(key);
        } else {
            return 0;
        }
        // bound-[0,1]*bound*2 gives a value from [-bound/2, bound/2]
        return bound - (Simulator.instance.getRandom().nextDouble() * bound * 2);
    }

    public String getNextFileName() {
        return nextFileName;
    }

    public void setNextFileName(String nextFileName) {
        this.nextFileName = nextFileName;
    }

    public Map<String, String> getStoredImages() {
        return storedImages;
    }

    public void addToStoredImages(String id, String filename, boolean isDeep) {
        storedImages.put(id, filename);
        if (isDeep) {
            deepScannedIds.add(id);
        }
    }

    public void setDeepAllowed(Boolean deepAllowed) {
        this.deepAllowed = deepAllowed;
    }

    public boolean setUserName(String userName) {
        this.userName = userName;
        return true;
    }

    public String getUserName() {
        return userName;
    }

    public List<String> getMarkers() {
        return markers;
    }

    public List<String> getPendingIds() {
        return pendingIds;
    }

    public void resetLogger(FileHandler fileHandler) {
        LOGGER.addHandler(fileHandler);
    }

    public void setShowReviewPanel(Boolean reviewPanel) {
        showReviewPanel = reviewPanel;
    }

    public boolean isShowReviewPanel() {
        return showReviewPanel;
    }

    public void addToHandledTargets(String trgId) {
        handledTargets.add(trgId);
    }

    public void addToPendingMap(String id, Coordinate coordinate) {
        pendingMap.put(coordinate, id);
    }

    public HashMap<Coordinate, String> getPendingMap() {
        return pendingMap;
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
