package server;

import server.controller.*;
import server.model.*;
import server.model.agents.*;
import server.model.target.Target;
import server.model.task.Task;
import tool.GsonUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.logging.FileHandler;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * @author Feng Wu
 */
/* Edited by Yuai */
public class Simulator {

    private String webRef ="web";

    private final static String SERVER_CONFIG_FILE = "/config/serverConfig.json";
    private final static String SCENARIO_DIR_PATH = "/scenarios/";
    private Logger LOGGER = Logger.getLogger(Simulator.class.getName());


    private State state;
    private Sensor sensor;

    private final QueueManager queueManager;
    private final AgentController agentController;
    private final TaskController taskController;
    private final TargetController targetController;
    private final ConnectionController connectionController;
    private final ScoreController scoreController;
    private final HazardController hazardController;
    private final Allocator allocator;
    private final Modeller modeller;
    private final ModelCaller modelCaller;

    public static Simulator instance;

    private static final double gameSpeed = 5;
    private Random random;
    private String nextFileName = "";

    private Thread mainLoopThread;

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
        scoreController = new ScoreController(this);
        modeller = new Modeller(this);
        modelCaller = new ModelCaller();
        random = new Random();

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

        int port;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        } else {
            port = 44101;

        }
        new Simulator().start(port);
    }

    public void start(Integer port) {
        /*
        try {
            LogManager.getLogManager().readConfiguration(new FileInputStream("./loggingForStudy.properties"));
        } catch (final IOException e) {
            Logger.getAnonymousLogger().severe("Could not load default loggingForStudy.properties file");
            Logger.getAnonymousLogger().severe(e.getMessage());
        }

         */

        //Setup GSON
        GsonUtils.registerTypeAdapter(Task.class, Task.taskSerializer);
        GsonUtils.registerTypeAdapter(State.HazardHitCollection.class, State.hazardHitsSerializer);
        GsonUtils.create();

        pushConfig(port);
        new Thread(connectionController::start).start();
        LOGGER.info(String.format("%s; SVRDY; Server ready ", getState().getTime()));
    }

    public void startSandboxMode() {
        this.state.setGameType(State.GAME_TYPE_SANDBOX);
        this.state.setGameId("Sandbox");
        LOGGER.info(String.format("%s; SBXLD; Sandbox loaded ", getState().getTime()));
        this.startSimulation();
    }

    public boolean loadScenarioMode(String scenarioFileName) {
        this.state.setGameType(State.GAME_TYPE_SCENARIO);
        if(loadScenarioFromFile(webRef+"/scenarios/" + scenarioFileName)) {
            //LOGGER.info(String.format("%s; SCLD; Scenario loaded (filename); %s ", getState().getTime(), scenarioFileName));
            return true;
        } else {
            //LOGGER.info(String.format("%s; SCUN; Unable to start scenario (filename); %s ", getState().getTime(), scenarioFileName));
            return false;
        }
    }

    public void startSimulation() {
        state.setScenarioEndTime();
        //Heart beat all virtual agents to prevent time out when user is reading the description.
        for(Agent agent : this.state.getAgents())
            if(agent.isSimulated())
                agent.heartbeat();
        this.agentController.stopAllAgents();
        this.mainLoopThread = new Thread(this::mainLoop);
        mainLoopThread.start();
        this.state.setInProgress(true);
        //allocator.runAutoAllocation();
        //allocator.confirmAllocation(state.getTempAllocation());
        LOGGER.info(String.format("%s; SIMST; Simulation started", getState().getTime()));
    }

    public Map<String, String> getScenarioFileListWithGameIds() {
        Map<String, String> scenarios = new HashMap<>();
        File scenarioDir = new File(webRef+SCENARIO_DIR_PATH);
        if(scenarioDir.exists() && scenarioDir.isDirectory()) {
            for(File file : scenarioDir.listFiles()) {
                if (!file.isDirectory()) {
                    String scenarioName = getScenarioNameFromFile(webRef + SCENARIO_DIR_PATH + file.getName());
                    if (scenarioName != null)
                        scenarios.put(file.getName(), scenarioName);
                }
            }
        }
        else
            LOGGER.info(String.format("%s; SCNF; Could not find scenario (directory); %s ", getState().getTime(), SCENARIO_DIR_PATH));
        return scenarios;
    }

    private void mainLoop() {
        final double waitTime = (int) (1000/(gameSpeed * 5)); //When gameSpeed is 1, should be 200ms.
        int sleepTime;
        do {
            long startTime = System.currentTimeMillis();

            if (state.getTasks().size() == 0) {// && getState().getHub() instanceof AgentHub && ((AgentHub) getState().getHub()).allAgentsNear()) {
                System.out.println("DONE BY COMPLETION: " + state.getTime());
                System.out.println("agents = " + state.getAgents());
                int numFailed = 0;
                for (Agent a : state.getAgents()) {
                    if (a instanceof AgentVirtual av) {
                        if (!av.isAlive()) {
                            numFailed++;
                        }
                    }
                }
                System.out.println("Num failed: " + numFailed);
                this.reset();
            }

            state.incrementTime(0.2);
            if (state.getScenarioEndTime() !=0 && System.currentTimeMillis() >= state.getScenarioEndTime()) {
                System.out.println("DONE BY TIME: " + state.getTime());
                System.out.println("agents = " + state.getAgents());
                int numFailed = 0;
                for (Agent a : state.getAgents()) {
                    if (a instanceof AgentVirtual av) {
                        if (!av.isAlive()) {
                            numFailed++;
                        }
                    }
                }
                System.out.println("Num failed: " + numFailed);
                modeller.outputResults();
                if (state.isPassthrough()) {
                    updateNextValues();
                }
                this.reset();
            }

            if (Simulator.instance.getState().getTime() > gameSpeed * 5) {

                if (state.getAllocationStyle().equals("dynamic")) {
                    List<Agent> agentsToRemove = new ArrayList<>();
                    synchronized (state.getAgents()) {
                        for (Agent agent : state.getAgents()) {
                            if (agent instanceof AgentVirtual av) {
                                if (agentController.modelFailure(av)) {
                                    modeller.failRecord(agent.getId(), agent.getAllocatedTaskId());
                                }

                                if (agent.isTimedOut()) {
                                    //System.out.println("timed out, passing");
                                } else if (!av.isAlive() && (!av.isGoingHome() || av.isHome())) {
                                    av.charge();
                                } else if (agent.getBattery() < 0.15 && av.isAlive()) {
                                    modeller.failRecord(agent.getId(), agent.getAllocatedTaskId());
                                    av.kill();
                                } else if (av.getTask() != null || (av.isGoingHome() && !av.isHome())) {
                                    //System.out.println(agent);
                                    av.step(state.isFlockingEnabled());
                                } else {
                                    if (getAgentController().getScheduledRemovals() > 0) {
                                        agentsToRemove.add(agent);
                                        getAgentController().decrementRemoval();
                                    } else if (getTaskController().checkForFreeTasks()) {
                                        av.stopGoingHome();
                                        getAllocator().dynamicAssign(av);
                                        Simulator.instance.getScoreController().incrementCompletedTask();
                                        // In-runtime allocation model
                                        double successChance = modeller.calculateAll(agent);
                                        state.setSuccessChance(successChance);
                                    } else if (agent.getBattery() < 0.9 && av.isAlive()) {
                                        // If no tasks available, charge up in case we need to replace it
                                        av.charge();
                                    } else {
                                        av.heartbeat();
                                    }
                                }
                            }
                        }
                    }

                    agentsToRemove.forEach(a -> {
                        getState().getAgents().remove(a);

                        // If an agent is removed or dies, update model and start thread
                        //updateMissionModel();
                    });

                } else {
                    // Step agents
                    checkAgentsForTimeout();

                    Hub hub = state.getHub();
                    if (hub instanceof AgentHub ah) {
                        ah.step(state.isFlockingEnabled());
                    } else if (hub instanceof AgentHubProgrammed ahp) {
                        ahp.step(state.isFlockingEnabled());
                    }
                    // ELSE no hub

                    synchronized (state.getAgents()) {
                        for (Agent agent : state.getAgents()) {
                            agent.step(getState().isFlockingEnabled());
                        }
                    }
                }

                if (state.isCommunicationConstrained()) {
                    state.updateAgentVisibility();
                    state.updateGhosts();
                    state.moveGhosts();
                }
                // Step tasks - requires completed tasks array to avoid concurrent modification.
                List<Task> completedTasks = new ArrayList<>();
                for (Task task : state.getTasks()) {
                    if (task.step()) {
                        // If it's already tagged by a programmed agent, or if it gets completed by the step command
                        completedTasks.add(task);
                        //System.out.println("Adding " + task.getId());
                    }
                }

                for (Task task : completedTasks) {
                    task.getAgents().forEach(a -> a.setType("standard"));
                    task.complete();
                }

                if (!completedTasks.isEmpty()) {
                    completedTasks.forEach(t -> modeller.passRecords(t.getId()));

                }

                if (!modeller.isStarted()) {
                    modeller.start();
                    updateMissionModel();
                }

            }

            scoreController.handleUpkeep();

            // Step hazard hits
            this.state.decayHazardHits();

            long endTime = System.currentTimeMillis();
            sleepTime = (int) (waitTime - (endTime - startTime));
            if (sleepTime < 0) {
                sleepTime = 0;
            }
        } while (sleep(sleepTime));
    }

    public void updateMissionModel() {
        // Update model and start thread
        boolean generatedCurrent = ModelGenerator.run(state, webRef);
        boolean generatedOver = ModelGenerator.runOver(state, webRef);
        boolean generatedUnder = ModelGenerator.runUnder(state, webRef);
        if (generatedCurrent && generatedOver && generatedUnder) {
        //if (generatedCurrent) {
            System.out.println("Model generated successfully");
            modelCaller.startThread(webRef);
        } else {
            System.out.println("Generation failure");
        }
    }

    private void updateNextValues() {
        try {
            String json = GsonUtils.readFile("web/scenarios/" + nextFileName);
            Object obj = GsonUtils.fromJson(json);
            state.setGameId(GsonUtils.getValue(obj, "gameId"));
            state.setGameDescription(GsonUtils.getValue(obj, "gameDescription"));
            LOGGER.info(String.format("%s; SCPS; Passing through to next scenario ", getState().getTime()));
        } catch (IOException e) {
            e.printStackTrace();
        }
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
                    LOGGER.info(String.format("%s; LSTCN; Lost connection with agent (id); %s ", getState().getTime(), agent.getId()));
                }
            }
        }
    }

    public void changeView(boolean toEdit) {
        if (toEdit) {
            if (state.getAllocationStyle().equals("manualwithstop")) {
                agentController.stopAllNonProgrammedAgents();
                agentController.updateNonProgrammedAgentsTempRoutes();
                allocator.copyRealAllocToTempAlloc();
            }
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
        if (this.mainLoopThread != null) {
            this.mainLoopThread.interrupt();
        }
        state.reset();
        agentController.resetAgentNumbers();
        hazardController.resetHazardNumbers();
        targetController.resetTargetNumbers();
        taskController.resetTaskNumbers();
        scoreController.reset();
        modelCaller.reset();
        modeller.stop();  // NOTE, if we disable the normal modeller, we will need to slightly refactor to give the modelCaller this start/stop functionality

        LOGGER.info(String.format("%s; SVRST; Server reset ", getState().getTime()));
        /*
        LogManager.getLogManager().reset();
        try {
            LogManager.getLogManager().readConfiguration(new FileInputStream("./loggingForStudy.properties"));
        } catch (final IOException e) {
            Logger.getAnonymousLogger().severe("Could not load default loggingForStudy.properties file");
            Logger.getAnonymousLogger().severe(e.getMessage());
        }
        LOGGER = null;
        LOGGER = Logger.getLogger(Simulator.class.getName());

         */
    }

    public void resetLogging(String userName) {
        try {
            String fileName = userName + "-" + state.getGameId() + ".log";
            FileHandler fileHandler = new FileHandler(fileName);
            LogManager.getLogManager().reset();
            LogManager.getLogManager().readConfiguration(new FileInputStream("./loggingForStudy.properties"));
            LOGGER.addHandler(fileHandler);
            state.resetLogger(fileHandler);
            taskController.resetLogger(fileHandler);
            queueManager.resetLogger(fileHandler);
            agentController.resetLogger(fileHandler);
            targetController.resetLogger(fileHandler);
            connectionController.resetLogger(fileHandler);
            hazardController.resetLogger(fileHandler);
            allocator.resetLogger(fileHandler);
            LOGGER.info(String.format("%s; LGSTRT; Reset log (scenario, username); %s; %s ", getState().getTime(), state.getGameId(), userName));

        } catch (final IOException e) {
            Logger.getAnonymousLogger().severe("Could not load default loggingForStudy.properties file");
            Logger.getAnonymousLogger().severe(e.getMessage());
        }

        //System.out.println("Save now as " + userName + ", " + state.getGameId());
    }

    private void readConfig() {
        try {
            LOGGER.info(String.format("%s; RDCFG; Reading Server Config File (directory); %s ", getState().getTime(), SERVER_CONFIG_FILE));
            String json = GsonUtils.readFile(SERVER_CONFIG_FILE);
            Object obj = GsonUtils.fromJson(json);
            Double port = GsonUtils.getValue(obj, "port");

            connectionController.init((port != null) ? port.intValue() : 8080, webRef);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void pushConfig(int port) {
        webRef = webRef+port;
        connectionController.init(port, webRef);
    }

    private boolean loadScenarioFromFile(String scenarioFile) {
        try {
            String json = GsonUtils.readFile(scenarioFile);
            Object obj = GsonUtils.fromJson(json);

            this.state.setGameId(GsonUtils.getValue(obj, "gameId"));
            this.state.setGameDescription(GsonUtils.getValue(obj, "gameDescription"));
            Object centre = GsonUtils.getValue(obj, "gameCentre");
            this.state.setGameCentre(new Coordinate(GsonUtils.getValue(centre, "lat"), GsonUtils.getValue(centre, "lng")));

            if(GsonUtils.hasKey(obj,"allocationMethod")) {
                String allocationMethod = GsonUtils.getValue(obj, "allocationMethod")
                        .toString()
                        .toLowerCase();

                List<String> possibleMethods = new ArrayList<>(Arrays.asList(
                        "random",
                        "maxsum",
                        "maxsumwithoverspill",
                        "bestfirst",
                        "basicbundle",
                        "heuristicbundle",
                        "cbaa",
                        "cbba",
                        "cbbacoverage",
                        "pimaxass"
                ));

                if(possibleMethods.contains(allocationMethod)) {
                    this.state.setAllocationMethod(allocationMethod);
                } else {
                    LOGGER.warning("Allocation method: '" + allocationMethod + "' not valid. Set to 'maxsum'.");
                    // state.allocationMethod initialised with default value of 'maxsum'
                }
            }

            if(GsonUtils.hasKey(obj,"allocationStyle")) {
                String allocationStyle = GsonUtils.getValue(obj, "allocationStyle")
                        .toString()
                        .toLowerCase();

                List<String> possibleMethods = new ArrayList<>(Arrays.asList(
                        "manual",
                        "manualwithstop",
                        "dynamic",
                        "bundle"
                ));

                if(possibleMethods.contains(allocationStyle)) {
                    this.state.setAllocationStyle(allocationStyle);
                } else {
                    LOGGER.warning("Allocation style: '" + allocationStyle + "' not valid. Set to 'manualwithstop'.");
                    // state.allocationMethod initialised with default value of 'maxsum'
                }
            }

            if(GsonUtils.hasKey(obj,"flockingEnabled")){
                Object flockingEnabled = GsonUtils.getValue(obj, "flockingEnabled");
                if(flockingEnabled.getClass() == Boolean.class) {
                    this.state.setFlockingEnabled((Boolean)flockingEnabled);
                } else {
                    LOGGER.warning("Expected boolean value for flockingEnabled in scenario file. Received: '" +
                            flockingEnabled + "'. Set to false.");
                    // state.flockingEnabled initialised with default value of false
                }
            }

            if(GsonUtils.hasKey(obj,"modelStyle")){
                Object modelStyle = GsonUtils.getValue(obj, "modelStyle");
                if(modelStyle.getClass() == String.class) {
                    this.modelCaller.setStyle((String) modelStyle);
                }
            }

            Object varJson = GsonUtils.getValue(obj, "varianceParameters");
            if (varJson != null) {
                if (GsonUtils.getValue(varJson, "speedPerAgent") != null) {
                    state.putVarianceOption("speedPerAgent", GsonUtils.getValue(varJson, "speedPerAgent"));
                }
                if (GsonUtils.getValue(varJson, "batteryPerAgent") != null) {
                    state.putVarianceOption("batteryPerAgent", GsonUtils.getValue(varJson, "batteryPerAgent"));
                }
            }

            Object noiseJson = GsonUtils.getValue(obj, "noiseParameters");
            if (noiseJson != null) {
                if (GsonUtils.getValue(noiseJson, "speedPerSecond") != null) {
                    state.putNoiseOption("speedPerSecond", GsonUtils.getValue(noiseJson, "speedPerSecond"));
                }
                if (GsonUtils.getValue(noiseJson, "batteryPerStep") != null) {
                    state.putNoiseOption("batteryPerStep", GsonUtils.getValue(noiseJson, "batteryPerStep"));
                }
                if (GsonUtils.getValue(noiseJson, "locationNoise") != null) {
                    state.putNoiseOption("locationNoise", GsonUtils.getValue(noiseJson, "locationNoise"));
                }
            }
            
            if(GsonUtils.hasKey(obj,"timeLimitSeconds")){
                Object timeLimitSeconds = GsonUtils.getValue(obj, "timeLimitSeconds");
                if(timeLimitSeconds.getClass() == Double.class) {
                    state.incrementTimeLimit((Double)timeLimitSeconds);
                } else {
                    LOGGER.warning("Expected double value for timeLimitSeconds in scenario file. Received: '" +
                            timeLimitSeconds.toString() + "'. Time limit not changed.");
                }
            }
            if(GsonUtils.hasKey(obj,"timeLimitMinutes")){
                Object timeLimitMinutes = GsonUtils.getValue(obj, "timeLimitMinutes");
                if(timeLimitMinutes.getClass() == Double.class) {
                    state.incrementTimeLimit((Double)timeLimitMinutes * 60);
                } else {
                    LOGGER.warning("Expected double value for timeLimitMinutes in scenario file. Received: '" +
                            timeLimitMinutes.toString() + "'. Time limit not changed.");
                }
            }

            if(GsonUtils.hasKey(obj,"nextScenarioFile")){
                Object nextScenarioFile = GsonUtils.getValue(obj, "nextScenarioFile");
                if(nextScenarioFile.getClass() == String.class) {
                    this.state.setPassthrough(true);
                    nextFileName = nextScenarioFile.toString();
                }
            }

            Object uiJson = GsonUtils.getValue(obj, "extendedUIOptions");
            if (uiJson != null) {
                if (GsonUtils.getValue(uiJson, "predictions") != null && (boolean) GsonUtils.getValue(uiJson, "predictions")) {
                    state.addUIOption("predictions");
                }
                if (GsonUtils.getValue(uiJson, "ranges") != null && (boolean) GsonUtils.getValue(uiJson, "ranges")) {
                    state.addUIOption("ranges");
                }
                if (GsonUtils.getValue(uiJson, "uncertainties") != null && (boolean) GsonUtils.getValue(uiJson, "uncertainties")) {
                    state.addUIOption("uncertainties");
                }
            }

            if(GsonUtils.hasKey(obj,"uncertaintyRadius")) {
                this.state.setUncertaintyRadius(GsonUtils.getValue(obj, "uncertaintyRadius"));
            } else {
                this.state.setUncertaintyRadius(10.0);
            }
            if(GsonUtils.hasKey(obj,"communicationRange")) {
                this.state.setCommunicationRange(GsonUtils.getValue(obj, "communicationRange"));
            } else {
                this.state.setCommunicationRange(250);
            }

            if(GsonUtils.hasKey(obj,"communicationConstrained")){
                Object communicationConstrained = GsonUtils.getValue(obj, "communicationConstrained");
                if(communicationConstrained.getClass() == Boolean.class) {
                    this.state.setCommunicationConstrained((Boolean)communicationConstrained);
                } else {
                    LOGGER.warning("Expected boolean value for communicationConstrained in scenario file. Received: '" +
                            communicationConstrained + "'. Set to false.");
                    // state.communicationConstrained initialised with default value of false
                }
            }

            boolean containsProgrammed = false;
            List<Object> agentsJson = GsonUtils.getValue(obj, "agents");
            if (agentsJson != null) {
                for (Object agentJSon : agentsJson) {
                    Double lat = GsonUtils.getValue(agentJSon, "lat");
                    Double lng = GsonUtils.getValue(agentJSon, "lng");
                    Boolean programmed = false;
                    if(GsonUtils.hasKey(agentJSon,"programmed")){
                        programmed = GsonUtils.getValue(agentJSon, "programmed");
                    }
                    Agent agent;
                    if (programmed) {
                        // This means the agent is a programmed one, and the Hub is set up for this
                        agent = agentController.addProgrammedAgent(lat, lng, 0, random, taskController);
                        containsProgrammed = true;
                    } else if (this.state.isCommunicationConstrained()) {
                        // This means the agent is a non-programmed one, but there is communication required for the network
                        agent = agentController.addVirtualCommunicatingAgent(lat, lng, random);
                    } else {
                        // Neither programmed or communication-required
                        agent = agentController.addVirtualAgent(lat, lng, 0);
                    }
                    Double battery = GsonUtils.getValue(agentJSon, "battery");
                    if(battery != null) {
                        agent.setBattery(battery);
                    }
                }
            }

            Object hub = GsonUtils.getValue(obj, "hub");
            if(hub != null) {
                Double lat = GsonUtils.getValue(hub, "lat");
                Double lng = GsonUtils.getValue(hub, "lng");

                if (this.state.isCommunicationConstrained() || containsProgrammed) {
                    // Even if it's not constrained, if there are programmed agents then we need to provide hub communication
                    agentController.addHubProgrammedAgent(lat, lng, random, taskController);
                } else {
                    agentController.addHubAgent(lat, lng);
                }

                state.setHubLocation(new Coordinate(lat, lng));
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
                    // Hide all targets initially - they must be found!!
                    targetController.setTargetVisibility(target.getId(), false);
                }
            }

            List<Object> tasksJson = GsonUtils.getValue(obj, "tasks");
            if (tasksJson != null) {
                for (Object taskJson : tasksJson) {
                    Double lat = GsonUtils.getValue(taskJson, "lat");
                    Double lng = GsonUtils.getValue(taskJson, "lng");
                    int type = ((Double) GsonUtils.getValue(taskJson, "type")).intValue();
                    Task task = taskController.createTask(type, lat, lng);
                }
            }

            if(GsonUtils.hasKey(obj,"uncertaintyRadius")) {
                this.state.setUncertaintyRadius(GsonUtils.getValue(obj, "uncertaintyRadius"));
            }

            List<Object> markers = GsonUtils.getValue(obj, "markers");
            if (markers != null) {
                for (Object markerJson : markers) {
                    String shape = GsonUtils.getValue(markerJson, "shape");
                    Double cLat = GsonUtils.getValue(markerJson, "centreLat");
                    Double cLng = GsonUtils.getValue(markerJson, "centreLng");
                    Double radius = GsonUtils.getValue(markerJson, "radius");

                    String shapeRep = shape+","+cLat+","+cLng+","+radius;

                    this.state.getMarkers().add(shapeRep);
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
            // TODO make this more graceful when state is reset
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Prints the complete belief for each programmed agent
     */
    private void printBeliefs() {
        ArrayList<String> beliefs = agentController.getBelievedModels();
        System.out.println("===========Beliefs===========");
        for (String b : beliefs) {
            System.out.println(b);
            System.out.println();
        }
    }

    /**
     * Prints the complete belief for the programmed hub
     */
    private void printHubBelief() {
        ArrayList<String> beliefs = agentController.getHubBelief();
        System.out.println("===========HUB Belief===========");
        for (String b : beliefs) {
            System.out.println(b);
            System.out.println();
        }
    }

    /**
     * Prints the state as a JSON
     */
    private void printStateJson() {
        String stateJson = GsonUtils.toJson(state);
        System.out.println(stateJson);
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

    public Random getRandom() {
        return random;
    }

    public double getGameSpeed() {
        return gameSpeed;
    }

    public ScoreController getScoreController() {
        return scoreController;
    }
}
