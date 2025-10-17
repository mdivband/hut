package server;

import server.controller.*;
import server.model.*;
import server.model.agents.*;
import server.model.target.AdjustableTarget;
import server.model.target.Target;
import server.model.task.Task;
import tool.GsonUtils;
import tool.LogProcessor;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.LogManager;
import java.util.logging.Logger;


import java.nio.file.Paths;


/**
 * This is the core code for the mainloop and loading of the simulator
 * @author Feng Wu
 */
/* Edited by Yuai */
/* Edited by Will */

public class Simulator {

    private String webRef ="web";

    private final static String SERVER_CONFIG_FILE = "/config/serverConfig.json";
    private final static String SCENARIO_DIR_PATH = "/scenarios/";
    private Logger LOGGER = Logger.getLogger(Simulator.class.getName());


    private final State state;
    private final Sensor sensor;

    //private final QueueManager queueManager;
    private final AgentController agentController;
    private final TaskController taskController;
    private final TargetController targetController;
    private final ConnectionController connectionController;
    private final ScoreController scoreController;

    private MissionController missionController = null;
    private final HazardController hazardController;
    private final Allocator allocator;
    //private final Modeller modeller;
    private final ModelCaller modelCaller;

    private final ImageController imageController;
    private final EpisodeController episodeController;

    public static Simulator instance;

    private static final double highTickRate = 15;
    private static final double lowTickRate = 1;  // Certain functions can be checked less often (once per second)
    private static final double gameSpeed = 25;
    private final Random random;

    private Thread mainLoopThread;
    private int completedTargets = 0;

    // ---- Video capture ----
    private FFmpegScreenRecorder screenRec;
    private boolean videoSessionStarted = false;
    private Integer vidEpIndex = null;
    private String  vidEpCode  = null;
    private long    vidEpWallStartMs = 0L;

    // Tunables for cut margins (seconds) — tightened defaults
    private static final double VIDEO_PREROLL_S = 0.0;  // was 0.8 — start earlier
    private static final double VIDEO_POSTROLL_S = 0.2; // was 0.8 — end sooner




    public Simulator() {
        instance = this;
        state = new State();
        sensor = new Sensor(this);
        connectionController = new ConnectionController(this);
        allocator = new Allocator(this);
        //queueManager = new QueueManager(this);
        agentController = new AgentController(this, sensor);
        taskController = new TaskController(this);
        hazardController = new HazardController(this);
        targetController = new TargetController(this);
        scoreController = new ScoreController(this);
        //modeller = new Modeller(this);
        modelCaller = new ModelCaller();
        random = new Random();

        // Video recorder: adjust path & folder as needed
        try {
            screenRec = new FFmpegScreenRecorder(
                    "C:\\Program Files (x86)\\ffmpeg\\bin\\ffmpeg.exe",   // <-- set your ffmpeg path
                    Paths.get("recordings")          // <-- output folder
            );
        } catch (Exception e) {
            e.printStackTrace();
            screenRec = null; // leave null if init failed; code below will skip recording safely
        }


        imageController = new ImageController(this);
        episodeController = new EpisodeController();

        //queueManager.initDroneDataConsumer();
    }

    public static void main(String[] args) {
        try {
            LogManager.getLogManager().readConfiguration(new FileInputStream("./loggingForStudy.properties"));
        } catch (final IOException e) {
            Logger.getAnonymousLogger().severe("Could not load default loggingForStudy.properties file");
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
            LOGGER.info(String.format("%s; SCLD; Scenario loaded (filename); %s ", getState().getTime(), scenarioFileName));
            return true;
        } else {
            LOGGER.info(String.format("%s; SCUN; Unable to start scenario (filename); %s ", getState().getTime(), scenarioFileName));
            return false;
        }
    }

    public void startSimulation() {

        // startSimulation()
        if (screenRec != null && !videoSessionStarted) {
            try {
                screenRec.startSession();
                videoSessionStarted = true;
                LOGGER.info(String.format("%s; VIDST; Video session started", getState().getTime()));
            } catch (IOException ioe) {
                ioe.printStackTrace();
                LOGGER.warning(String.format("%s; VIDER; Failed to start video session", getState().getTime()));
            }
        }


        //state.setScenarioEndTime();
        //Heart beat all virtual agents to prevent time out when user is reading the description.
        for(Agent agent : this.state.getAgents())
            if(agent.isSimulated())
                agent.heartbeat();
        this.agentController.stopAllAgents();
        this.mainLoopThread = new Thread(this::mainLoop);
        mainLoopThread.start();
        this.state.setInProgress(true);
        LOGGER.info(String.format("%s; SIMST; Simulation started", getState().getTime()));
    }

    private void videoOnEpisodeStart(String epCode, int epIndex) {
        if (screenRec == null || !videoSessionStarted) return;
        vidEpCode = (epCode != null && !epCode.isBlank()) ? epCode : defaultEpCode();
        vidEpIndex = epIndex;
        long prerollMs = (long) (VIDEO_PREROLL_S * 1000);
        vidEpWallStartMs = System.currentTimeMillis() - prerollMs;
        LOGGER.info(String.format("%s; VRUN; Episode running (code, idx, startWall); %s;%d;%d",
                getState().getTime(), vidEpCode, vidEpIndex, vidEpWallStartMs));
    }

    private void videoOnEpisodeEndIfActive() {
        if (screenRec == null || !videoSessionStarted) return;
        if (vidEpIndex == null) return;
        long wallEnd = System.currentTimeMillis() + (long)(VIDEO_POSTROLL_S * 1000);
        try {
            screenRec.cutEpisodeAsync(vidEpCode, vidEpIndex, vidEpWallStartMs, wallEnd, /*reencode=*/true);
            LOGGER.info(String.format("%s; VEND; Episode ended (code, idx, endWall); %s;%d;%d",
                    getState().getTime(), vidEpCode, vidEpIndex, wallEnd));
        } catch (Exception e) {
            e.printStackTrace();
            LOGGER.warning(String.format("%s; VERR; Cut enqueue failed (code, idx); %s;%d",
                    getState().getTime(), vidEpCode, vidEpIndex));
        } finally {
            vidEpIndex = null;
            vidEpCode = null;
            vidEpWallStartMs = 0L;
        }
    }



    private void videoStopSessionIfRunning() {
        if (screenRec == null || !videoSessionStarted) return;
        try {
            screenRec.stopSessionGracefully();
            LOGGER.info(String.format("%s; VSTOP; Video session stopped", getState().getTime()));
        } catch (Exception ignored) {
        } finally {
            videoSessionStarted = false;
        }
    }

    private String defaultEpCode() {
        // Cheap, robust code prefix: scenario/gameId if present
        String gid = (getState().getGameId() != null && !getState().getGameId().isBlank())
                ? getState().getGameId()
                : "EP";
        return gid.replaceAll("[^A-Za-z0-9_-]", "_");
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


    // fields
    private long tickId = 0;
    private boolean videoStartArmed = false;
    private String armedEpCode;
    private int    armedEpIndex;
    private long   armAtTick;

    // arm to a near-future tick (just one loop later)
    private void armVideoStart(String epCode, int epIndex) {
        armedEpCode = epCode;
        armedEpIndex = epIndex;
        armAtTick = tickId + 1;   // was +2; start a tick earlier (~66ms at 15 Hz)
        videoStartArmed = true;
    }


    // fire only at/after the target tick
    private void maybeFireArmedVideoStart() {
        if (videoStartArmed && tickId >= armAtTick) {
            videoOnEpisodeStart(armedEpCode, armedEpIndex);
            videoStartArmed = false;
        }
    }


    private void mainLoop() {
        final int waitTimeMs = (int)Math.round(1000.0 / highTickRate); // (optional: round)
        int lowTickCounter = 0;
        int sleepTime;

        // video: simple counter to label clips 001, 002, ...
        int epCounter = 0;

        episodeController.setTriggerTime(-1d);
        double degradationTriggerTime = -1d; // next scheduled disappearance
        changeView(1); // Start directly in EPISODE mode (no review/cooldown)

        do {
            // 0) gate any armed starts for this tick *before* episode boundary logic
            maybeFireArmedVideoStart();

            long startTime = System.currentTimeMillis();
            state.incrementTime(1 / highTickRate);

            boolean isOutOfTime = (state.getTimeLimit() != 0 && state.getTime() >= state.getTimeLimit());
            boolean allEpisodesUsed = (!episodeController.hasEpisodes());
            boolean inFinalReviewMode = (state.getEditMode() == -9);  // Review panel is showing

            if (inFinalReviewMode && (isOutOfTime ||
                    (state.getTime() >= episodeController.getTriggerTime() && episodeController.hasStarted() && allEpisodesUsed))) {
                // ---- Terminate simulation ----
                LOGGER.info(String.format("%s; WKLD; User set workload level to (level); %s", state.getTime(), state.getWorkloadLevel()));
                LOGGER.info(String.format("%s; PRCP; User set Subjective performance level to (level); %s", state.getTime(), state.getSubjPerfLevel()));
                LOGGER.info(String.format("%s; EPEND; Episode end", state.getTime()));

                // VIDEO: close active clip + stop session
                videoOnEpisodeEndIfActive();
                videoStopSessionIfRunning();

                System.out.println("DONE BY TIME: " + state.getTime());
                System.out.println("NOTE: Ending after final review completed.");
                episodeController.closeLogger();
                LogProcessor.processLogFile("logs/" + state.getUserName() + "-" + state.getGameId() + ".log");
                this.reset(false);
                break;
            }

            // --- Fast deck timing (no review/cooldown) ---
            if (episodeController.getTriggerTime() == -1d) {
                // First episode
                if (episodeController.hasEpisodes()) {
                    changeView(1);
                    episodeController.incrementEpisode();
                    this.softReset();

                    spawnEpisodeAgentsAndTask(); // <-- prepare scene first

                    // VIDEO: START first episode (no preroll needed)
                    epCounter++;
                    armVideoStart(defaultEpCode(), epCounter);

                    // schedule end + disappearance
                    episodeController.setTriggerTime(state.getTime() + episodeController.getEpisodeTimeLimit());
                    degradationTriggerTime = state.getTime() + episodeController.getDegradationTime();
                }
                else {
                    // No episodes: end
                    // VIDEO: ensure everything is stopped
                    videoOnEpisodeEndIfActive();
                    videoStopSessionIfRunning();

                    episodeController.closeLogger();
                    LogProcessor.processLogFile("logs/" + state.getUserName() + "-" + state.getGameId() + ".log");
                    this.reset(false);
                    return;
                }

            } else if (state.getTime() >= episodeController.getTriggerTime()) {
                // Episode finished → immediately start next (no review/cooldown)
                if (episodeController.hasEpisodes()) {
                    // VIDEO: END previous
                    videoOnEpisodeEndIfActive();

                    changeView(1);
                    episodeController.incrementEpisode();
                    this.softReset();

                    spawnEpisodeAgentsAndTask(); // <-- now set the new scene

// VIDEO: START next (no preroll)=
                    epCounter++;
                    armVideoStart(defaultEpCode(), epCounter);

                    episodeController.setTriggerTime(state.getTime() + episodeController.getEpisodeTimeLimit());
                    degradationTriggerTime = state.getTime() + episodeController.getDegradationTime();

                } else {
                    // Finished last episode
                    // VIDEO: END last + stop session
                    videoOnEpisodeEndIfActive();
                    videoStopSessionIfRunning();

                    episodeController.closeLogger();
                    LogProcessor.processLogFile("logs/" + state.getUserName() + "-" + state.getGameId() + ".log");
                    this.reset(false);
                    return;
                }
            }

            // Independent disappearance trigger at the configured time
            if (degradationTriggerTime > 0 && state.getTime() >= degradationTriggerTime) {
                String degColour = episodeController.getDegColour(); // e.g., "blue"
                String wantedMarker = (degColour != null) ? ("UAV-" + degColour) : null;

                Optional<Agent> victim = Optional.empty();

                if (wantedMarker != null) {
                    victim = state.getAgents().stream()
                            .filter(a -> a instanceof AgentVirtual av && av.isAlive() && av.getTask() == null)
                            .filter(a -> wantedMarker.equals(a.getMarker()))
                            .findAny();
                }

                if (victim.isEmpty()) {
                    // Fallback: any alive, un-tasked agent
                    victim = state.getAgents().stream()
                            .filter(a -> a instanceof AgentVirtual av && av.isAlive() && av.getTask() == null)
                            .findAny();
                }

                victim.ifPresent(agent -> {
                    agentController.deleteAgent(agent.getId());
                    LOGGER.info(String.format(
                            "%s; DEGTR; Degradation triggered (agentId, marker, targetDegColour); %s;%s;%s",
                            state.getTime(), agent.getId(), agent.getMarker(), degColour
                    ));
                });

                degradationTriggerTime = -1d; // fire once per episode
            }

            // 6. If in review mode (-2) and no other condition applies, do nothing (hold)
            else if (state.getEditMode() == -2) {
                // In review mode: waiting for user action to trigger mode change to -9.
            }

            // Decide if we should spawn a new task
            lowTickCounter++;
            if (lowTickCounter == lowTickRate * highTickRate) {
                if (missionController != null) {
                    missionController.spawnIfRequired(state.getTime());
                }
                lowTickCounter = 0;
            }

            // Main agent/task stepping
            if (true) {
                if (state.getAllocationStyle().equals("dynamic")) {
                    if (state.getTasks().size() == 0) {
                        System.out.println("DONE BY COMPLETION: " + state.getTime());
                        System.out.println("agents = " + state.getAgents());
                        int numFailed = 0;
                        for (Agent a : state.getAgents()) {
                            if (a instanceof AgentVirtual av) {
                                if (!av.isAlive()) numFailed++;
                            }
                        }
                        System.out.println("Num failed: " + numFailed);
                        this.reset();
                    }

                    List<Agent> agentsToRemove = new ArrayList<>();
                    synchronized (state.getAgents()) {
                        for (Agent agent : state.getAgents()) {
                            if (agent instanceof AgentVirtual av) {
                                if (agentController.modelFailure(av)) {
                                    // modeller.failRecord(agent.getId(), agent.getAllocatedTaskId());
                                }

                                if (agent.isTimedOut()) {
                                    // pass
                                } else if (!av.isAlive() && (!av.isGoingHome() || av.isHome())) {
                                    av.charge();
                                } else if (agent.getBattery() < 0.15 && av.isAlive()) {
                                    // modeller.failRecord(agent.getId(), agent.getAllocatedTaskId());
                                    av.killBattery();
                                } else if (av.getTask() != null || (av.isGoingHome() && !av.isHome())) {
                                    av.step(state.isFlockingEnabled());
                                } else {
                                    if (getAgentController().getScheduledRemovals() > 0) {
                                        agentsToRemove.add(agent);
                                        getAgentController().decrementRemoval();
                                    } else if (getTaskController().checkForFreeTasks()) {
                                        av.stopGoingHome();
                                        getAllocator().dynamicAssign(av);
                                        if (av.getAllocatedTaskId() != null && av.getTask().getType() == 6) {
                                            av.setType("withpack");
                                            av.setMarker("UAVWithPack");
                                        }
                                        Simulator.instance.getScoreController().incrementCompletedTask();
                                    } else if (agent.getBattery() < 0.9 && av.isAlive()) {
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
                        // updateMissionModel();
                    });

                } else {
                    checkAgentsForTimeout();

                    Hub hub = state.getHub();
                    if (hub instanceof AgentHub ah) {
                        ah.step(state.isFlockingEnabled());
                    } else if (hub instanceof AgentHubProgrammed ahp) {
                        ahp.step(state.isFlockingEnabled());
                    }
                    state.getAgents().forEach(a -> a.step(state.isFlockingEnabled()));
                }

                if (state.isCommunicationConstrained()) {
                    state.updateAgentVisibility();
                    state.updateGhosts();
                    state.moveGhosts();
                }

                // Step tasks - requires completed tasks array to avoid concurrent modification.
                List<Task> completedTasks = new ArrayList<>();
                synchronized (state.getTasks()) {
                    for (Task task : state.getTasks()) {
                        if (task.step()) {
                            completedTasks.add(task);
                        }
                    }
                }

                synchronized (Simulator.instance.getState().getCompletedTasks()) {
                    completedTasks.stream().filter(task -> task.getType() == 6).forEach(task -> task.getAgents().forEach(a -> a.setType("standard")));
                    completedTasks.forEach(Task::complete);
                }
            }

            // if (state.UIOptionIsAvailable("reviewPanel")) { imageController.checkForImages(); }
            if (state.UIOptionIsAvailable("reviewPanel")) {
                imageController.checkForImages();
            }

            long endTime = System.currentTimeMillis();
            sleepTime = (int)(waitTimeMs - (endTime - startTime));
            if (sleepTime < 0) sleepTime = 0;

            tickId++;              // bump at end of a fully completed loop
        } while (sleep(sleepTime));
    }


    private void spawnEpisodeAgentsAndTask() {
        // --- Get colours for this episode (one per agent) ---
        List<String> epColours = new ArrayList<>(episodeController.getColours());
        int numAgents = episodeController.getNumAgents();

        if (epColours.size() != numAgents) {
            // Fallback: synthesize a balanced list if JSON is missing/mismatched
            epColours.clear();
            String[] palette = {"red","green","blue","yellow"};
            for (int i = 0; i < numAgents; i++) epColours.add(palette[i % palette.length]);
        }

        // --- Place hero agent ---
        Coordinate c = episodeController.getAgentCoord();
        Agent heroAgent = agentController.addVirtualAgent(c.getLatitude(), c.getLongitude(), 0);
        // Set hero's colour (index 0)
        heroAgent.setMarker("UAV-" + epColours.get(0));

        List<Coordinate> placedAgents = new ArrayList<>();
        placedAgents.add(c);

        // --- Heading/task as before ---
        Coordinate targetLocation = episodeController.getTargetCoord();
        double angle = heroAgent.getCoordinate().getAngle(targetLocation);
        Coordinate newTaskLocation = targetLocation.getCoordinate(100000, angle);
        heroAgent.setHeading(Math.toDegrees(angle));

        int separationDist = state.isComplexFlocking() ? 150 : 150;

        // --- Spawn remaining agents (1..N-1) with colours[i] ---
        for (int i = 1; i < numAgents; i++) {
            Coordinate newCoord = null;
            boolean validPlacement = false;
            int attempts = 0;

            while (!validPlacement && attempts < 50) {
                List<Agent> agentList = new ArrayList<>(state.getAgents());
                Coordinate existingAgentCoord = agentList.get(random.nextInt(agentList.size())).getCoordinate();

                double spawnAngle = 2 * Math.PI * random.nextDouble();
                double offset = (separationDist * 1.5) / 111139d; // ~250m in degrees
                double newLat = existingAgentCoord.getLatitude() + offset * Math.cos(spawnAngle);
                double newLng = existingAgentCoord.getLongitude() + offset * Math.sin(spawnAngle)
                        / Math.cos(existingAgentCoord.getLatitude());

                newCoord = new Coordinate(newLat, newLng);
                validPlacement = true;

                for (Coordinate placedCoord : placedAgents) {
                    if (placedCoord.getDistance(newCoord) < separationDist) {
                        validPlacement = false;
                        break;
                    }
                }
                attempts++;
            }

            if (validPlacement) {
                Agent agent = agentController.addVirtualAgent(
                        newCoord.getLatitude(), newCoord.getLongitude(),
                        180 + Math.toDegrees(angle)
                );
                placedAgents.add(newCoord);

                // Set this agent's colour from the episode list
                agent.setMarker("UAV-" + epColours.get(i));
            } else {
                System.out.println("Failed to place an agent after 50 attempts.");
            }
        }

        // --- Create/confirm task as before ---
        Task task = taskController.createTask(0, newTaskLocation.getLatitude(), newTaskLocation.getLongitude());
        allocator.putInTempAllocation(heroAgent.getId(), task.getId());
        allocator.confirmAllocation(state.getTempAllocation());
    }



    /**
     * Runs the model for the current setup
     */
    public void updateMissionModel() {
        if (!state.getModelStyle().equals("off")) {
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
    }

    private void passthrough() {
        state.reset();
        agentController.resetAgentNumbers();
        hazardController.resetHazardNumbers();
        targetController.resetTargetNumbers();
        taskController.resetTaskNumbers();
        scoreController.reset();
        modelCaller.reset();
        //modeller.stop();  // NOTE, if we disable the normal modeller, we will need to slightly refactor to give the modelCaller this start/stop functionality

        LOGGER.info(String.format("%s; SVRST; Server temporarily reset ", getState().getTime()));
        imageController.reset();

        loadScenarioFromFile("web/scenarios/" + state.getNextFileName());

        //startSimulation();
    }

    private void updateNextValues() {
        try {
            String json = GsonUtils.readFile("web/scenarios/" + getState().getNextFileName());
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

    /**
     * Important to note that we create a condition for each flag. This used to allow extra things to happen, but it
     * remains this way to ensure care it taken over each mode change.
     * @param modeFlag
     */
    public void changeView(int modeFlag) {
        //System.out.println("TEMP FORCECHANGE: mode forced to task edit");
        //modeFlag = 2;
        LOGGER.info(String.format("%s; CHVW; Changing view to mode; %s ", Simulator.instance.getState().getTime(), modeFlag));
        if (modeFlag == 2) {
            //agentController.stopAllAgents();
            //agentController.updateAgentsTempRoutes();
            //allocator.copyRealAllocToTempAlloc();
            //allocator.clearAllocationHistory();
            state.setEditMode(2);
        } else if (modeFlag == 1){
            //allocator.confirmAllocation(state.getAllocation());
            state.setEditMode(1);
        } else if (modeFlag == 3) {
            state.setEditMode(3);
            //Clear agents and tasks
            /*
            for(Agent agent : state.getAgents()) {
                agent.resume();
            }

             */
        } else if (modeFlag == -1){
            state.setEditMode(-1);
        } else if (modeFlag == -2){
            state.setEditMode(-2);
        } else if (modeFlag == -9){
            state.setEditMode(-9);
        }
    }

    public void setProvDoc(String docid) {
        state.setProvDoc(docid);
    }

    public synchronized void reset() {
        reset(true);
    }

    public synchronized void reset(boolean interruptMain) {
        if (interruptMain && this.mainLoopThread != null) {
            this.mainLoopThread.interrupt();
         }

        // stop any active capture cleanly
        videoOnEpisodeEndIfActive();
        videoStopSessionIfRunning();


        state.reset();
        agentController.resetAgentNumbers();
        hazardController.resetHazardNumbers();
        targetController.resetTargetNumbers();
        taskController.resetTaskNumbers();
        scoreController.reset();
        modelCaller.reset();
        if (missionController != null) {
            missionController.reset();
        }
        //modeller.stop();  // NOTE, if we disable the normal modeller, we will need to slightly refactor to give the modelCaller this start/stop functionality

        LOGGER.info(String.format("%s; SVRST; Server reset ", getState().getTime()));
        imageController.reset();
    }

    public void softReset() {
        state.softReset();
    }

    public void resetLogging(String userName) {
        try {
//            for (Handler handler : LOGGER.getHandlers()) {
//                LOGGER.removeHandler(handler);
//                handler.close();
//            }

            String fileName = "logs/" + userName + "-" + state.getGameId() + ".log";
            FileHandler fileHandler = new FileHandler(fileName);
            LogManager.getLogManager().reset();
            LogManager.getLogManager().readConfiguration(new FileInputStream("./loggingForStudy.properties"));
            LOGGER.addHandler(fileHandler);
            state.resetLogger(fileHandler);
            taskController.resetLogger(fileHandler);
            //queueManager.resetLogger(fileHandler);
            agentController.resetLogger(fileHandler);
            targetController.resetLogger(fileHandler);
            connectionController.resetLogger(fileHandler);
            hazardController.resetLogger(fileHandler);
            allocator.resetLogger(fileHandler);
            imageController.resetLogger(fileHandler);
            LOGGER.info(String.format("%s; LGSTRT; Reset log (scenario, username); %s; %s ", getState().getTime(), state.getGameId(), userName));

        } catch (final IOException e) {
            Logger.getAnonymousLogger().severe("Could not load default loggingForStudy.properties file");
            Logger.getAnonymousLogger().severe(e.getMessage());
        }

        //System.out.println("Save now as " + userName + ", " + state.getGameId());
    }

    private void readConfig() {
        try {
            LOGGER.info(String.format("%s; RDCFG; Reading Server Config File (directory); %s ", getState().getTime(), webRef+SERVER_CONFIG_FILE));
            String json = GsonUtils.readFile(webRef+SERVER_CONFIG_FILE);
            Object obj = GsonUtils.fromJson(json);
            Double port = GsonUtils.getValue(obj, "port");

            connectionController.init((port != null) ? port.intValue() : 8080, webRef);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void pushConfig(int port) {
        webRef = webRef;//+port;
        connectionController.init(port);//), webRef);
    }

    private boolean loadScenarioFromFile(String scenarioFile) {
        try {
            String json = GsonUtils.readFile(scenarioFile);
            Object obj = GsonUtils.fromJson(json);

            this.state.setGameId(GsonUtils.getValue(obj, "gameId"));
            this.state.setGameDescription(GsonUtils.getValue(obj, "gameDescription"));
            Object centre = GsonUtils.getValue(obj, "gameCentre");
            this.state.setGameCentre(new Coordinate(GsonUtils.getValue(centre, "lat"), GsonUtils.getValue(centre, "lng")));

            Object dynamicUIFeaturesJson = GsonUtils.getValue(obj, "dynamicUIFeatures");
            if (dynamicUIFeaturesJson != null) {
                List<List<String>> featureList = new ArrayList<>(5);
                featureList.add(GsonUtils.getValue(dynamicUIFeaturesJson, "1"));
                featureList.add(GsonUtils.getValue(dynamicUIFeaturesJson, "2"));
                featureList.add(GsonUtils.getValue(dynamicUIFeaturesJson, "3"));
                featureList.add(GsonUtils.getValue(dynamicUIFeaturesJson, "4"));
                featureList.add(GsonUtils.getValue(dynamicUIFeaturesJson, "5"));
                this.state.setDynamicUIFeatures(featureList);
            }

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
                        "cbbacoverage"
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

            if(GsonUtils.hasKey(obj,"loggingById")){
                Object loggingById = GsonUtils.getValue(obj, "loggingById");
                if(loggingById.getClass() == Boolean.class) {
                    this.state.setLoggingById((Boolean) loggingById);
                } else {
                    LOGGER.warning("Expected boolean value for loggingById in scenario file. Received: '" +
                            loggingById + "'. Set to false.");
                    // state.flockingEnabled initialised with default value of false
                }
            }

            this.state.resetNext();
            if(GsonUtils.hasKey(obj,"timeLimitSeconds")){
                Object timeLimitSeconds = GsonUtils.getValue(obj, "timeLimitSeconds");
                if(timeLimitSeconds.getClass() == Double.class) {
                    //state.incrementTimeLimit((Double)timeLimitSeconds);
                    state.setSimTimeLimit(((Double) timeLimitSeconds).intValue());
                } else {
                    LOGGER.warning("Expected double value for timeLimitSeconds in scenario file. Received: '" +
                            timeLimitSeconds + "'. Time limit not changed.");
                }
            }
            if(GsonUtils.hasKey(obj,"timeLimitMinutes")){
                Object timeLimitMinutes = GsonUtils.getValue(obj, "timeLimitMinutes");
                if(timeLimitMinutes.getClass() == Double.class) {
                    //state.incrementTimeLimit((Double)timeLimitMinutes * 60);
                    state.setSimTimeLimit(((Double) timeLimitMinutes).intValue() * 60);
                } else {
                    LOGGER.warning("Expected double value for timeLimitMinutes in scenario file. Received: '" +
                            timeLimitMinutes + "'. Time limit not changed.");
                }
            }
            if(GsonUtils.hasKey(obj,"nextScenarioFile")){
                Object nextScenarioFile = GsonUtils.getValue(obj, "nextScenarioFile");
                if(nextScenarioFile.getClass() == String.class) {
                    this.state.setPassthrough(true);
                    state.setNextFileName(nextScenarioFile.toString());
                }
            }

            if(GsonUtils.hasKey(obj,"deepAllowed")) {
                Object deepAllowed = GsonUtils.getValue(obj, "deepAllowed");
                if (deepAllowed.getClass() == Boolean.class) {
                    state.setDeepAllowed((Boolean) deepAllowed);
                }
            }

            if(GsonUtils.hasKey(obj,"modelStyle")){
                Object modelStyle = GsonUtils.getValue(obj, "modelStyle");
                if(modelStyle.getClass() == String.class) {
                    state.setModelStyle((String) modelStyle);
                }
            }

            if(GsonUtils.hasKey(obj,"reviewPanel")){
                Object reviewPanel = GsonUtils.getValue(obj, "reviewPanel");
                if(reviewPanel.getClass() == Boolean.class) {
                    state.setShowReviewPanel((Boolean) reviewPanel);
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

            List<Object> uiJson = GsonUtils.getValue(obj, "uIOptions");
            if (uiJson != null) {
                for (Object uIOption : uiJson) {
                    String name = GsonUtils.getValue(uIOption, "name");
                    boolean[] uIPair = new boolean[]{GsonUtils.getValue(uIOption, "default"),
                            GsonUtils.getValue(uIOption, "toggleable")};
                    state.addUIOption(name, uIPair);
                }

            }

            List<Object> episodesJson = GsonUtils.getValue(obj, "episodes");
            if (episodesJson != null) {
                for (Object episode : episodesJson) {
                    int episodeLength = ((Double) GsonUtils.getValue(episode, "episodeLength")).intValue();

                    // Optional legacy fields (default 0)
                    int episodeCooldown = GsonUtils.hasKey(episode, "episodeCooldown")
                            ? ((Double) GsonUtils.getValue(episode, "episodeCooldown")).intValue() : 0;
                    int reviewPeriod = GsonUtils.hasKey(episode, "reviewPeriod")
                            ? ((Double) GsonUtils.getValue(episode, "reviewPeriod")).intValue() : 0;

                    String agentPos = GsonUtils.hasKey(episode, "agentPos") ? GsonUtils.getValue(episode, "agentPos") : "C";
                    String targetPos = GsonUtils.hasKey(episode, "targetPos") ? GsonUtils.getValue(episode, "targetPos") : "R";

                    int numAgents = ((Double) GsonUtils.getValue(episode, "numAgents")).intValue();

                    // Always-on degradation: read time only
                    double degradationTime = GsonUtils.hasKey(episode, "degradationTime")
                            ? ((Double) GsonUtils.getValue(episode, "degradationTime"))
                            : 4.0; // sensible default

                    String episodeCode = GsonUtils.hasKey(episode, "episodeCode") ? GsonUtils.getValue(episode, "episodeCode") : "EP-?-?";

                    // Optional extras
                    Integer prevAgents = GsonUtils.hasKey(episode, "prevAgents")
                            ? ((Double) GsonUtils.getValue(episode, "prevAgents")).intValue()
                            : 0;

                    ArrayList<String> colours = null;
                    if (GsonUtils.hasKey(episode, "colours")) {
                        List<Object> cols = GsonUtils.getValue(episode, "colours");
                        colours = new ArrayList<>();
                        for (Object c : cols) colours.add(c.toString());
                    }
                    String degColour = GsonUtils.hasKey(episode, "degColour") ? GsonUtils.getValue(episode, "degColour") : null;

                    // Markers (unchanged)
                    List<Object> markersJson = GsonUtils.getValue(episode, "markers");
                    ArrayList<String> markerList = new ArrayList<>();
                    if (markersJson != null) {
                        for (Object markerJson : markersJson) {
                            String shape = GsonUtils.getValue(markerJson, "shape");
                            if ("circle".equals(shape)) {
                                Double cLat = GsonUtils.getValue(markerJson, "centreLat");
                                Double cLng = GsonUtils.getValue(markerJson, "centreLng");
                                Double radius = GsonUtils.getValue(markerJson, "radius");
                                markerList.add(shape + "," + cLat + "," + cLng + "," + radius);
                            } else if ("textMarker".equals(shape)) {
                                Double lat = GsonUtils.getValue(markerJson, "lat");
                                Double lng = GsonUtils.getValue(markerJson, "lng");
                                String text = GsonUtils.getValue(markerJson, "text");
                                markerList.add(shape + "," + lat + "," + lng + "," + text);
                            } else if ("banner".equals(shape)) {
                                String colourBg = GsonUtils.getValue(markerJson, "colorBg");
                                String colourTxt = GsonUtils.getValue(markerJson, "colourTxt");
                                String text = GsonUtils.getValue(markerJson, "text");
                                markerList.add(shape + "," + colourBg + "," + colourTxt + "," + text);
                            }
                        }
                    }

                    // NOTE: isDegradationMatch parameter kept for signature, but unused (always true)
                    this.episodeController.addEpisode(
                            episodeLength, episodeCooldown, reviewPeriod,
                            agentPos, targetPos, numAgents,
                            true, degradationTime, episodeCode, markerList,
                            prevAgents, colours, degColour
                    );
                }
            }


            if(GsonUtils.hasKey(obj,"complexFlocking")) {
                this.state.setComplexFlocking(GsonUtils.getValue(obj, "complexFlocking"));
            } else {
                this.state.setComplexFlocking(false);
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
            //this.state.setCommunicationRange(10000000);

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
                        String policy = "GenericAgentPolicy";
                        if (GsonUtils.hasKey(agentJSon, "policy")) {
                            policy = GsonUtils.getValue(agentJSon, "policy");
                        }
                        agent = agentController.addProgrammedAgent(lat, lng, 0, random, taskController, policy);
                        containsProgrammed = true;
                    } else if (this.state.isCommunicationConstrained()) {
                        // This means the agent is a non-programmed one, but there is communication required for the network
                        //agent = agentController.addVirtualCommunicatingAgent(lat, lng, random);
                        agent = agentController.addVirtualAgent(lat, lng, 0);
                    } else {
                        // Neither programmed or communication-required
                        agent = agentController.addVirtualAgent(lat, lng, 0);
                    }
                    Double battery = GsonUtils.getValue(agentJSon, "battery");
                    if(battery != null) {
                        agent.setBattery(battery);
                    }
                    if (GsonUtils.hasKey(agentJSon, "mkr")) {
                        agent.setMarker(GsonUtils.getValue(agentJSon, "mkr"));
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
                    String highRes = GsonUtils.getValue(targetJson, "highRes");
                    String lowRes = GsonUtils.getValue(targetJson, "lowRes");
                    Target target;
                    if (GsonUtils.hasKey(targetJson, "real")) {
                        boolean isReal = GsonUtils.getValue(targetJson, "real");
                        target = targetController.addTarget(lat, lng, type, isReal);
                        ((AdjustableTarget) target).setFilenames(lowRes, highRes);
                    } else {
                        target = targetController.addTarget(lat, lng, type);
                    }

                    //Hide all targets initially - they must be found!!
                    targetController.setTargetVisibility(target.getId(), false);
                }
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

            if(GsonUtils.hasKey(obj,"missionController")) {
                Object missionController = GsonUtils.getValue(obj, "missionController");
                this.missionController = new MissionController(this);

                if(GsonUtils.hasKey(missionController,"spawnRadius")) {
                    this.missionController.setSpawnRadius((GsonUtils.getValue(missionController, "spawnRadius")));
                }

                List<Object> spawnPairs = GsonUtils.getValue(missionController, "taskSpawnTrack");
                if (spawnPairs != null) {
                    for (Object spawnPair : spawnPairs) {
                        Integer spawnTime = ((Double) GsonUtils.getValue(spawnPair, "spawnTime")).intValue();
                        Integer spawnRate = ((Double) GsonUtils.getValue(spawnPair, "spawnRate")).intValue();

                        this.missionController.addPair(spawnTime, spawnRate);
                    }
                    this.missionController.orderPairs();
                }

            }

            this.state.setGameSpeed((int) gameSpeed);

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
    //    return queueManager;
        return null;
    }

    public ImageController getImageController() {
        return imageController;
    }

    public Random getRandom() {
        return random;
    }

    public double getTickRate() {
        return highTickRate;
    }

    public ScoreController getScoreController() {
        return scoreController;
    }

    public MissionController getMissionController() {
        return missionController;
    }

    public EpisodeController getEpisodeController() {
        return episodeController;
    }

    public double getStepScale() {
        return gameSpeed / highTickRate;
    }

    public void incrementCompletedTargets() {
        completedTargets++;
    }

}
