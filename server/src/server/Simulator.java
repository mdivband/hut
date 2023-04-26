package server;

import server.controller.AgentController;
import server.controller.ConnectionController;
import server.controller.TaskController;
import server.controller.TargetController;
import server.controller.HazardController;
import server.model.Agent;
import server.model.Coordinate;
import server.model.Sensor;
import server.model.State;
import server.model.target.Target;
import server.model.task.Task;
import tool.GsonUtils;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * @author Feng Wu
 */
/* Edited by Yuai */
public class Simulator {

    private final static String SERVER_CONFIG_FILE = "web/config/serverConfig.json";
    private final static String SCENARIO_DIR_PATH = "web/scenarios/";
    private Logger LOGGER = Logger.getLogger(Simulator.class.getName());
    private State state;
    private Sensor sensor;

    private final QueueManager queueManager;
    private final AgentController agentController;
    private final TaskController taskController;
    private final TargetController targetController;
    private final ConnectionController connectionController;
    private final HazardController hazardController;
    private final Allocator allocator;

    public static Simulator instance;

    private static final double gameSpeed = 6;

    public Simulator() {
        instance = this;

        state = new State();
        sensor = new Sensor(this);
        connectionController = new ConnectionController(this);
        allocator = new Allocator(this);
        queueManager = new QueueManager(this);
        agentController = new AgentController(this, sensor);
        taskController = new TaskController(this);
        hazardController = new HazardController(this);
        targetController = new TargetController(this);

        queueManager.initDroneDataConsumer();
    }

    public static void main(String[] args) {
        try {
            LogManager.getLogManager().readConfiguration(new FileInputStream("./logging.properties"));
        } catch (final IOException e) {
            Logger.getAnonymousLogger().severe("Could not load default logging.properties file");
            Logger.getAnonymousLogger().severe(e.getMessage());
        }

        //Setup GSON
        GsonUtils.registerTypeAdapter(Task.class, Task.taskSerializer);
        GsonUtils.registerTypeAdapter(State.HazardHitCollection.class, State.hazardHitsSerializer);
        GsonUtils.create();

        new Simulator().start();
    }

    public void start() {
        readConfig();
        new Thread(connectionController::start).start();
        LOGGER.info("Server ready.");
    }

    public void startSandboxMode() {
        this.state.setGameType(State.GAME_TYPE_SANDBOX);
        this.state.setGameId("Sandbox");
        LOGGER.info("Sandbox loaded.");
        this.startSimulation();
    }

    public boolean loadScenarioMode(String scenarioFileName) {
        if(loadScenarioFromFile("/web/scenarios/" + scenarioFileName)) {
            LOGGER.info("Scenario loaded.");
            return true;
        } else {
            LOGGER.severe("Unable to start scenario from file - " + scenarioFileName);
            return false;
        }
    }

    public void startSimulation() {
        //Heart beat all virtual agents to prevent time out when user is reading the description.
        for(Agent agent : this.state.getAgents())
            if(agent.isSimulated())
                agent.heartbeat();
        this.agentController.stopAllAgents();
        new Thread(this::mainLoop).start();
        this.state.setInProgress(true);
        LOGGER.info("Simulation started.");
    }

    public Map<String, String> getScenarioFileListWithGameIds() {
        Map<String, String> scenarios = new HashMap<>();
        File scenarioDir = new File(SCENARIO_DIR_PATH);
        if(scenarioDir.exists() && scenarioDir.isDirectory()) {
            for(File file : scenarioDir.listFiles()) {
                String scenarioName = getScenarioNameFromFile(SCENARIO_DIR_PATH + file.getName());
                if(scenarioName != null)
                    scenarios.put(file.getName(), scenarioName);
            }
        }
        else
            LOGGER.severe("Could not find scenario directory at " + SCENARIO_DIR_PATH);
        return scenarios;
    }

    private void mainLoop() {
        final double waitTime = (int) (1000/(gameSpeed * 5)); //When gameSpeed is 1, should be 200ms.
        int sleepTime;
        do {
            long startTime = System.currentTimeMillis();

            state.incrementTime(0.2);

            //Step agents
            checkAgentsForTimeout();
            for (Agent agent : state.getAgents())
                agent.step(state.isFlockingEnabled());

            //Step tasks - requires completed tasks array to avoid concurrent modification.
            List<Task> completedTasks = new ArrayList<Task>();
            for (Task task : state.getTasks())
                if(task.step())
                    completedTasks.add(task);
            for(Task task : completedTasks)
                task.complete();

            //Step hazard hits
            this.state.decayHazardHits();

            long endTime = System.currentTimeMillis();
            sleepTime = (int) (waitTime - (endTime - startTime));
            if (sleepTime < 0) {
                sleepTime = 0;
            }
        } while (sleep(sleepTime));
    }

    /**
     * Check if any agents have timed out or reconnected this step.
     */
    private void checkAgentsForTimeout() {
        for (Agent agent : state.getAgents()) {
            //Check if agent is timed out
            if (agent.getMillisSinceLastHeartbeat() > 20 * 1000) {
                if(!agent.isTimedOut()) {
                    agent.setTimedOut(true);
                    LOGGER.info("Lost connection with agent " + agent.getId());
                }
            }
        }
    }

    public void changeView(boolean toEdit) {
        if (toEdit) {
            agentController.stopAllAgents();
            agentController.updateAgentsTempRoutes();
            allocator.copyRealAllocToTempAlloc();
            allocator.clearAllocationHistory();
            state.setEditMode(true);
        } else {
            allocator.confirmAllocation(state.getAllocation());
            state.setEditMode(false);
        }
    }

    public void setProvDoc(String docid) {
        state.setProvDoc(docid);
    }

    public synchronized void reset() {
        state.reset();
        LOGGER.info("Server reset.");
    }

    private void readConfig() {
        try {
            LOGGER.info("Reading Server Config File: " + SERVER_CONFIG_FILE);
            String json = GsonUtils.readFile(SERVER_CONFIG_FILE);
            Object obj = GsonUtils.fromJson(json);
            Double port = GsonUtils.getValue(obj, "port");

            connectionController.init((port != null) ? port.intValue() : 8080);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean loadScenarioFromFile(String scenarioFile) {
        try {
            String json = GsonUtils.readFile(System.getProperty("user.dir")+scenarioFile);
            Object obj = GsonUtils.fromJson(json);
            this.state.setGameId(GsonUtils.getValue(obj, "gameId"));
            
            this.state.setGameDescription(GsonUtils.getValue(obj, "gameDescription"));
            
            Object centre = GsonUtils.getValue(obj, "gameCentre");
            this.state.setGameCentre(new Coordinate(GsonUtils.getValue(centre, "lat"), GsonUtils.getValue(centre, "lng")));
            
            if(GsonUtils.hasKey(obj,"allocationMethod")) {
                String allocationMethod = GsonUtils.getValue(obj, "allocationMethod")
                        .toString()
                        .toLowerCase();

                List<String> possibleMethods = new ArrayList<String>(Arrays.asList(
                        "random",
                        "maxsum"
                ));

                if(possibleMethods.contains(allocationMethod)) {
                    this.state.setAllocationMethod(allocationMethod);
                } else {
                    LOGGER.warning("Allocation method: '" + allocationMethod + "' not valid. Set to 'maxsum'.");
                    //state.allocationMethod initialised with default value of 'maxsum'
                }
            }
            
            if(GsonUtils.hasKey(obj,"flockingEnabled")){
                Object flockingEnabled = GsonUtils.getValue(obj, "flockingEnabled");
                if(flockingEnabled.getClass() == Boolean.class) {
                    this.state.setFlockingEnabled((Boolean)flockingEnabled);
                } else {
                    LOGGER.warning("Expected boolean value for flockingEnabled in scenario file. Received: '" +
                            flockingEnabled.toString() + "'. Set to false.");
                    // state.flockingEnabled initialised with default value of false
                }
            }
            
            List<Object> agentsJson = GsonUtils.getValue(obj, "agents");
            if (agentsJson != null) {
                for (Object agentJSon : agentsJson) {
                    Double lat = GsonUtils.getValue(agentJSon, "lat");
                    Double lng = GsonUtils.getValue(agentJSon, "lng");
                    Agent agent = agentController.addVirtualAgent(lat, lng, 0);
                    Double battery = GsonUtils.getValue(agentJSon, "battery");
                    if(battery != null)
                        agent.setBattery(battery);
                }
            }
            
            List<Object> hazards = GsonUtils.getValue(obj, "hazards");
            if (hazards != null) {
                for (Object hazard : hazards) {
                    Double lat = GsonUtils.getValue(hazard, "lat");
                    Double lng = GsonUtils.getValue(hazard, "lng");
                    int type = ((Double) GsonUtils.getValue(hazard, "type")).intValue();
                    if (GsonUtils.hasKey(hazard, "size")) {
                        int size = ((Double) GsonUtils.getValue(hazard, "size")).intValue();
                        hazardController.addHazard(lat, lng, type, size);
                    } else {
                        hazardController.addHazard(lat, lng, type);
                    }
                }
            }
            
            List<Object> targetsJson = GsonUtils.getValue(obj, "targets");
            if (targetsJson != null) {
                for (Object targetJson : targetsJson) {
                    Double lat = GsonUtils.getValue(targetJson, "lat");
                    Double lng = GsonUtils.getValue(targetJson, "lng");
                    int type = ((Double) GsonUtils.getValue(targetJson, "type")).intValue();
                    Target target = targetController.addTarget(lat, lng, type);
                    //Hide all targets initially - they must be found!!
                    targetController.setTargetVisibility(target.getId(), false);
                }
            }

            return true;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    private String getScenarioNameFromFile(String scenarioFile) {
        try {
            String json = GsonUtils.readFile(scenarioFile);
            Object obj = GsonUtils.fromJson(json);
            return GsonUtils.getValue(obj, "gameId");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static boolean sleep(long millis) {
        try {
            if (millis > 0) {
                Thread.sleep(millis);
            }
            return true;
        } catch (InterruptedException e) {
            e.printStackTrace();
            return false;
        }
    }

    public synchronized String getStateAsString() {
        return state.toString();
    }

    public synchronized State getState() {
        return state;
    }

    public Allocator getAllocator() {
        return this.allocator;
    }

    public AgentController getAgentController() {
        return agentController;
    }

    public TaskController getTaskController() {
        return taskController;
    }

    public TargetController getTargetController() {
        return targetController;
    }

    public QueueManager getQueueManager() {
        return queueManager;
    }

}
