package server.controller;

import server.Simulator;
import server.model.*;
import server.model.agents.Agent;
import server.model.agents.AgentVirtual;
import server.model.State;
import tool.DDSListener;
import tool.PythonExecutor;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.ArrayList;
import tool.GsonUtils;

public class DDSController extends AbstractController {
    private DDSListener ddsListener;
    private PythonExecutor publisherExecutor; // For dev mode publisher
    private String publisherScriptPath = "";
    private long lastNoDataWarningTime = 0;
    private long lastNoPublisherWarningTime = 0;
    private long warningRateLimit = 10000; // 10 seconds in milliseconds
    
    // Thread management and data synchronization
    private Thread executorThread;
    private volatile boolean isRunning = false;
    private volatile boolean dataReceived = false;
    private volatile boolean isProcessing = false;
    private String dataBuffer = "";
    private final Object dataLock = new Object();
    private double executionRate = 0.1; // Executor thread sleep rate in seconds
    
    // Agent data storage
    /* 
    This will hold the agent data as a list of lists, 
    Where each inner list represents an agent
    The current inner list structure is:
    [agentId, lat, lng, heading, altitude, batteryLevel]
    */
    private List<List<Object>> agentDataList = new ArrayList<>();
    
    // Mapping of DDS agent IDs to simulator agent IDs
    private java.util.Map<String, String> ddsToSimulatorAgentIdMap = new java.util.HashMap<>();

    public DDSController(Simulator simulator) {
        super(simulator, DDSController.class.getName());
        this.ddsListener = new DDSListener();
        this.publisherExecutor = new PythonExecutor();
    }

    /**
     * Gets the current agent data list
     * @return List of agent data where each agent is represented as a list of objects
     */
    public List<List<Object>> getAgentDataList() {
        synchronized (dataLock) {
            return new ArrayList<>(agentDataList); // Return a copy for thread safety
        }
    }

    /**
     * Sets the rate limit for warning messages in milliseconds
     * @param rateLimitMs Rate limit in milliseconds (default: 10000ms = 10 seconds)
     */
    public void setWarningRateLimit(long rateLimitMs) {
        this.warningRateLimit = rateLimitMs;
    }

    /**
     * Sets the execution rate for DDS polling in seconds
     * @param rateSeconds Rate in seconds between DDS polls (default: 1.0)
     */
    public void setExecutionRate(double rateSeconds) {
        this.executionRate = rateSeconds;
    }
    
    /**
     * Sets the wait time for the Python script execution
     * @param waitTimeSeconds Wait time in seconds for the Python script (default: 0.1)
     */
    public void setScriptWaitTime(double waitTimeSeconds) {
        ddsListener.setScriptWaitTime(waitTimeSeconds);
    }

    /**
     * Sets a custom Python executable path that will be tried first before default commands
     * @param pythonPath The full path to the Python executable (e.g., "C:\\Python39\\python.exe")
     */
    public void setPythonPath(String pythonPath) {
        try {
            ddsListener.setCustomPythonPath(pythonPath);
            // Set the python path for the publisher executor as well if we are in dev mode
            if (simulator.getState().getDevMode()) {
                publisherExecutor.setCustomPythonPath(pythonPath);
            }
        } catch (Exception e) {
            LOGGER.severe(String.format("%s; DDSER; Python path configuration failed; %s", 
                simulator.getState().getTime(), e.getMessage()));
            throw new RuntimeException("Failed to set Python path", e);
        }
    }

    /**
     * Sets the path to the Python script to execute for DDS listening
     * @param scriptPath The full path to the Python script (e.g., "C:\\path\\to\\listener.py")
     * Calls the DDSListener's setScriptPath method and throws an exception if it fails
     */
    public void setScriptPath(String scriptPath) {
        try {
            ddsListener.setScriptPath(scriptPath);
        } catch (Exception e) {
            LOGGER.severe(String.format(
                "%s; DDSER; Script path configuration failed; %s", 
                simulator.getState().getTime(), e.getMessage()));
            throw new RuntimeException("Failed to set script path", e);
        }
    }
    
    /**
     * Sets the path to the Python DDS publisher script for dev mode
     * @param publisherScriptPath The full path to the Python publisher script
     */
    public void setPublisherScriptPath(String publisherScriptPath) {
        this.publisherScriptPath = publisherScriptPath;
        LOGGER.info("DDS publisher script path set to: " + publisherScriptPath);
    }

    /**
     * Starts the DDS executor on a separate thread
     */
    public void start() {
        if (isRunning) {
            LOGGER.warning("DDS Controller is already running");
            return;
        }
        
        // Start the persistent Python listener
        try {
            ddsListener.startPersistentListener();
        } catch (Exception e) {
            LOGGER.severe(String.format(
                "%s; DDSER; Failed to start persistent Python listener; %s", 
                simulator.getState().getTime(), e.getMessage()));
            throw new RuntimeException("Failed to start DDS Controller", e);
        }

        isRunning = true;
        executorThread = new Thread(this::executorLoop, "DDS-Executor-Thread");
        executorThread.setDaemon(true);
        executorThread.start();
        
        LOGGER.info(String.format(
            "%s; DDSST; DDS Controller started with persistent listener, rate %.1f seconds", 
            simulator.getState().getTime(), executionRate));
    }

    /**
     * Stops the DDS executor thread
     */
    public void stop() {
        isRunning = false;
        
        // Stop the persistent Python listener first
        try {
            ddsListener.stopPersistentListener();
        } catch (Exception e) {
            LOGGER.warning(String.format(
                "%s; DDSWRN; Error stopping persistent Python listener; %s",
                simulator.getState().getTime(), e.getMessage()));
        }
        
        // Stop the DDS publisher if running in dev mode
        if (simulator.getState().getDevMode()) {
            stopDDSPublisher();
        }
        
        if (executorThread != null) {
            executorThread.interrupt();
            try {
                executorThread.join(5000); // Wait up to 5 seconds for thread to stop
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        // Clear DDS agent mapping
        synchronized (dataLock) {
            ddsToSimulatorAgentIdMap.clear();
            agentDataList.clear();
        }
        
        LOGGER.info(String.format("%s; DDSSTP; DDS Controller stopped", 
            simulator.getState().getTime()));
    }
    
    /**
     * Starts the DDS publisher in dev mode
     */
    public void startDDSPublisher() {
        if (publisherScriptPath == null || 
            publisherScriptPath.trim().isEmpty()) {
            LOGGER.warning(String.format(
                "%s; DDSWRN; DDS publisher script path not set, cannot start publisher in dev mode", 
                simulator.getState().getTime()));
            return;
        }
        
        try {
            // Start the publisher with the specified arguments
            String countArg = "80"; // Default count for dev mode
            String stepIntervalArgs = "0.5"; // Default step interval in seconds
            boolean success = publisherExecutor.startAsyncScript(
                publisherScriptPath, "--use_csv", "--count", countArg,
                "--step_interval", stepIntervalArgs);
            if (success) {
                LOGGER.info(String.format(
                    "%s; DDSPUB; DDS Publisher started using csvs with: %s step and rate %s", 
                    simulator.getState().getTime(), countArg, stepIntervalArgs));
            } else {
                Exception exception = publisherExecutor.getAsyncScriptException();
                String errorMsg = exception != null ? exception.getMessage() : "Unknown error";
                LOGGER.severe(String.format(
                    "%s; DDSER; Failed to start DDS Publisher in dev mode: %s", 
                    simulator.getState().getTime(), errorMsg));
            }
        } catch (Exception e) {
            LOGGER.severe(String.format(
                "%s; DDSER; Error starting DDS Publisher in dev mode: %s", 
                simulator.getState().getTime(), e.getMessage()));
        }
    }
    
    /**
     * Stops the DDS publisher in dev mode
     */
    private void stopDDSPublisher() {
        try {
            if (publisherExecutor == null || !publisherExecutor.isAsyncScriptRunning()) {
                return;
            }
            
            publisherExecutor.stopAsyncScript();
            
            // Get final status
            Integer exitCode = publisherExecutor.getAsyncScriptExitCode();
            // String output = publisherExecutor.getAsyncScriptOutput();
            Exception exception = publisherExecutor.getAsyncScriptException();
            
            if (exception != null) {
                LOGGER.warning(String.format(
                    "%s; DDSWRN; DDS Publisher encountered error: %s", 
                    simulator.getState().getTime(), exception.getMessage()));
            } else if (exitCode != null) {
                if (exitCode == 0) {
                    LOGGER.info(String.format(
                        "%s; DDSPUB; DDS Publisher completed successfully (exit code: %d)", 
                        simulator.getState().getTime(), exitCode));
                } else {
                    LOGGER.warning(String.format(
                        "%s; DDSWRN; DDS Publisher completed with error (exit code: %d)", 
                        simulator.getState().getTime(), exitCode));
                }
                
                // if (!output.trim().isEmpty()) {
                //     LOGGER.info(String.format(
                //         "%s; DDSPUB; Publisher output: %s", 
                //         simulator.getState().getTime(), output.trim()));
                // }
            } else {
                LOGGER.info(String.format(
                    "%s; DDSPUB; DDS Publisher stopped in dev mode", 
                    simulator.getState().getTime()));
            }
        } catch (Exception e) {
            LOGGER.warning(String.format(
                "%s; DDSWRN; Error stopping DDS Publisher in dev mode: %s", 
                simulator.getState().getTime(), e.getMessage()));
        }
    }
    
    /**
     * Checks the status of the DDS publisher and logs if it has finished
     */
    private void checkPublisherStatus() {
        if (!simulator.getState().getDevMode()) {
            return; // Not in dev mode, no publisher to check
        }
        
        // Only check if async script has been marked as completed
        if (publisherExecutor.isAsyncScriptCompleted()) {
            return;
        }
        
        if (publisherExecutor.isAsyncScriptFinished()) {
            stopDDSPublisher();
            // Mark completion as processed so we don't check again
            publisherExecutor.markAsyncScriptCompleted();
        }
    }

    /**
     * Checks if new DDS data has been received
     * @return true if data is available for processing
     */
    public boolean hasDataReceived() {
        synchronized (dataLock) {
            return dataReceived && !isProcessing;
        }
    }

    /**
     * Checks if data is available for processing
     * This method checks the current data state without calling the Python executor
     * @return true if data is available for processing
     */
    public boolean isDataAvailable() {
        synchronized (dataLock) {
            return dataReceived && !isProcessing;
        }
    }

    /**
     * Main executor loop that runs on separate thread
     */
    private void executorLoop() {
        while (isRunning && !Thread.currentThread().isInterrupted()) {
            try {
                // Only execute if not currently processing data
                synchronized (dataLock) {
                    if (!isProcessing) {
                        executeOnce();
                    }
                }
                
                // Check publisher status if in dev mode
                checkPublisherStatus();
                
                // Sleep for the specified rate
                Thread.sleep((long)(executionRate * 1000));
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                LOGGER.severe(String.format(
                    "%s; DDSER; DDS executor thread error; %s", 
                    simulator.getState().getTime(), e.getMessage()));
                // Continue running even if one execution fails
            }
        }
    }

    /**
     * Executes the DDS listener once and updates data buffer if data is received
     */
    private void executeOnce() {
        try {
            // Check if persistent listener is healthy, restart if needed
            if (!ddsListener.isPersistentListenerHealthy()) {
                LOGGER.warning(String.format(
                    "%s; DDSWRN; Persistent listener unhealthy, restarting", 
                    simulator.getState().getTime()));
                ddsListener.stopPersistentListener();
                ddsListener.startPersistentListener();
                return; // Skip this execution cycle to let it start up
            }
            
            // Get output once and check for data availability patterns
            String pythonOutput = ddsListener.getScriptOutput();
            
            if (!pythonOutput.isEmpty()) {
                // Check for warning states that indicate no data but system is functioning
                // Match the exact patterns from fbs_listener.py
                if (pythonOutput.contains(
                    "No data received - publisher may not be active")) {
                    handleNoPublisherWarning();
                } else if (pythonOutput.contains("Empty Data:")) {
                    handleNoDataWarning();
                } else if (pythonOutput.contains("FlatBuffer Data received for") ||
                           pythonOutput.contains("String Data:")) {
                    // Actual data received - update buffer and flag
                    synchronized (dataLock) {
                        dataBuffer = pythonOutput;
                        dataReceived = true;
                        
                        LOGGER.info(String.format(
                            "%s; DDSRD; DDS data received; %d bytes", 
                            simulator.getState().getTime(), 
                            pythonOutput.length()));
                    }
                } else {
                    // Log unrecognized patterns for debugging
                    LOGGER.fine(String.format(
                        "%s; DDSDBG; Unrecognized Python output pattern: %s", 
                        simulator.getState().getTime(), 
                        pythonOutput.replace("\n", "\\n")));
                }
                // Status messages like "DDS Listener started in continuous mode..." are ignored
            }
            // If pythonOutput is empty, that's normal - the persistent listener is running
            // but no new data has arrived since the last check
            
        } catch (Exception e) {
            LOGGER.severe(String.format("%s; DDSER; DDS execution failed; %s", 
                simulator.getState().getTime(), e.getMessage()));
            
            // Try to restart the persistent listener on error
            try {
                ddsListener.stopPersistentListener();
                ddsListener.startPersistentListener();
            } catch (Exception restartException) {
                LOGGER.severe(String.format(
                    "%s; DDSER; Failed to restart persistent listener; %s", 
                    simulator.getState().getTime(), restartException.getMessage()));
            }
        }
    }

    /**
     * Processes the received DDS data and clears the data received flag
     * This method should be called by the simulator when hasDataReceived() returns true
     * @return true if data was successfully processed, false otherwise
     */
    public boolean processData() {
        synchronized (dataLock) {
            if (!dataReceived) {
                return false;
            }
            
            // Mark as processing to prevent buffer updates
            isProcessing = true;
            String data = dataBuffer;

            LOGGER.info(String.format(
                "%s; DDSPRC; Processing DDS data; %d bytes",
                simulator.getState().getTime(), data.length()));

            // Parse and extract agent data
            boolean success = parseAndExtractAgentData(data);
            //Update the simulator with the parsed data
            if (success) {
                boolean updated = updateSimulator();
                return updated; // Return true if simulator was updated successfully
            }
            // If parsing failed, return false
            return false;
        }
    }

    /**
     * Parses JSON data and extracts agent information into the class member agentDataList
     * @param data The raw DDS data containing JSON
     * @return true if parsing was successful, false otherwise
     */
    private boolean parseAndExtractAgentData(String data) {
        try {
            // Extract JSON part from the data (skip the header line)
            String jsonData = data;
            if (data.contains("{")) {
                jsonData = data.substring(data.indexOf("{"));
            }
            
            // Parse the JSON data using GsonUtils
            Object jsonObject = GsonUtils.fromJson(jsonData);
            
            // Extract agents array
            Object agentsArray = GsonUtils.getValue(jsonObject, "agents");
            
            if (agentsArray instanceof java.util.List) {
                java.util.List<?> agentsList = (java.util.List<?>) agentsArray;
                
                // Clear previous data and update the class member
                synchronized (dataLock) {
                    agentDataList.clear();
                    
                    // Process each agent
                    for (Object agentElement : agentsList) {
                        if (agentElement instanceof java.util.Map) {
                            java.util.Map<?, ?> agent = (java.util.Map<?, ?>) agentElement;
                            
                            // Extract agent data using GsonUtils
                            String agentId = (String) GsonUtils.getValue(agent, "agent_id");
                            Object coordinateObj = GsonUtils.getValue(agent, "coordinate");
                            
                            double lat = 0.0, lng = 0.0;
                            if (coordinateObj instanceof java.util.Map) {
                                java.util.Map<?, ?> coordinate = (java.util.Map<?, ?>) coordinateObj;
                                lat = ((Number) GsonUtils.getValue(coordinate, "lat")).doubleValue();
                                lng = ((Number) GsonUtils.getValue(coordinate, "lng")).doubleValue();
                            }
                            
                            double heading = ((Number) GsonUtils.getValue(agent, "heading")).doubleValue();
                            double altitude = ((Number) GsonUtils.getValue(agent, "altitude")).doubleValue();
                            double batteryLevel = ((Number) GsonUtils.getValue(
                                    agent, "battery_level")).doubleValue();
                            
                            // Create list for this agent's data
                            List<Object> agentData = new ArrayList<>();
                            agentData.add(agentId);
                            agentData.add(lat);
                            agentData.add(lng);
                            agentData.add(heading);
                            agentData.add(altitude);
                            agentData.add(batteryLevel);
                            
                            agentDataList.add(agentData);
                            
                            // Print agent information instead of logging
                            // System.out.printf(
                            //     "Agent: %s, Coordinate: (%.6f, %.6f), Heading: %.1f°, Altitude: %.1fm, Battery: %.1f%%%n",
                            //     agentId, lat, lng, heading, altitude, batteryLevel * 100);
                        }
                    }
                    
                    // Print success message instead of logging
                    // System.out.printf(
                    //     "Successfully processed %d agents from DDS data%n", 
                    //     agentDataList.size());
                }
                
                return true; // Success
            }
            
            return false; // No agents array found
                
        } catch (Exception e) {
            LOGGER.severe(String.format(
                "%s; DDSER; Failed to parse DDS JSON data; %s",
                simulator.getState().getTime(), e.getMessage()));
            // System.out.println("Raw data: " + data);
            return false; // Failure
        }
    }

    /**
     * Completes the data processing and allows new data to be received
     * This should be called by the simulator after processing the data
     */
    public void completeDataProcessing(boolean success) {
        synchronized (dataLock) {
            dataReceived = false;
            isProcessing = false;
            dataBuffer = "";

            if (success) {
                LOGGER.info(String.format(
                    "%s; DDSCP; DDS data processing completed successfully", 
                    simulator.getState().getTime()));
            } else {
                LOGGER.warning(String.format(
                    "%s; DDSCP; DDS data processing failed, new data discarded", 
                    simulator.getState().getTime()));
            }
        }
    }

    /**
     * Updates the simulator with agent data from DDS
     * Creates new agents if they don't exist, or adds coordinates to existing agent routes
     * Expected agentDataList structure: [agentId, lat, lng, heading, altitude, batteryLevel]
     * @return true if simulator was updated successfully, false if no agent data to process
     */
    public boolean updateSimulator() {
        synchronized (dataLock) {
            if (agentDataList.isEmpty()) {
                return false; // No agent data to process
            }

            for (List<Object> agentData : agentDataList) {
                if (agentData.size() < 6) {
                    LOGGER.warning(String.format(
                        "%s; DDSWRN; Invalid agent data structure, expected 6 elements, got %d", 
                        simulator.getState().getTime(), agentData.size()));
                    continue;
                }

                try {
                    // Extract agent data
                    String ddsAgentId = (String) agentData.get(0);
                    double lat = ((Number) agentData.get(1)).doubleValue();
                    double lng = ((Number) agentData.get(2)).doubleValue();
                    double heading = ((Number) agentData.get(3)).doubleValue();
                    double altitude = ((Number) agentData.get(4)).doubleValue();
                    double batteryLevel = ((Number) agentData.get(5)).doubleValue();

                    // Create coordinate for this agent
                    Coordinate agentCoordinate = new Coordinate(lat, lng);

                    // Check if we have a mapping for this DDS agent ID
                    String simulatorAgentId = ddsToSimulatorAgentIdMap.get(ddsAgentId);
                    Agent existingAgent = null;
                    
                    if (simulatorAgentId != null) {
                        existingAgent = simulator.getState().getAgent(simulatorAgentId);
                    }
                    
                    if (existingAgent == null) {
                        // Agent doesn't exist, create new virtual agent
                        Agent newAgent = simulator.getAgentController().addVirtualAgent(
                            lat, lng, heading);
                        newAgent.setAltitude(altitude);
                        newAgent.setBattery(batteryLevel);
                        
                        // Store the mapping between DDS ID and simulator ID
                        ddsToSimulatorAgentIdMap.put(ddsAgentId, newAgent.getId());
                        
                        LOGGER.info(String.format(
                            "%s; DDSAG; Created new DDS agent; DDS_ID=%s, SIM_ID=%s, Position=(%.6f,%.6f), Heading=%.1f°", 
                            simulator.getState().getTime(), 
                            ddsAgentId, newAgent.getId(), 
                            lat, lng, heading));
                            
                    } else {
                        // Agent exists, update its current position directly (not route)
                        // DDS provides real-time position, not planned waypoints
                        existingAgent.setCoordinate(agentCoordinate);
                        existingAgent.setHeading(heading);
                        existingAgent.setAltitude(altitude);
                        existingAgent.setBattery(batteryLevel);
                        
                        LOGGER.fine(String.format(
                            "%s; DDSUP; Updated existing DDS agent; DDS_ID=%s, SIM_ID=%s, Position=(%.6f,%.6f), Heading=%.1f°", 
                            simulator.getState().getTime(), ddsAgentId, existingAgent.getId(), lat, lng, heading));
                    }

                } catch (Exception e) {
                    LOGGER.severe(String.format(
                        "%s; DDSER; Failed to update simulator with agent data; %s", 
                        simulator.getState().getTime(), e.getMessage()));
                    return false; // Failure in processing agent data
                }
            }

            LOGGER.info(String.format(
                "%s; DDSSIM; Updated simulator with %d agents from DDS data", 
                simulator.getState().getTime(), agentDataList.size()));
            return true; // Successfully updated simulator
        }
    }

    /**
     * Handles rate-limited warning for no publisher active
     */
    private void handleNoPublisherWarning() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastNoPublisherWarningTime >= warningRateLimit) {
            LOGGER.warning(String.format(
                "%s; DDSNP; No DDS publisher active - no data source available", 
                simulator.getState().getTime()));
            lastNoPublisherWarningTime = currentTime;
        }
    }

    /**
     * Handles rate-limited warning for no data received
     */
    private void handleNoDataWarning() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastNoDataWarningTime >= warningRateLimit) {
            LOGGER.warning(String.format(
                "%s; DDSND; No DDS data received - publisher may be inactive", 
                simulator.getState().getTime()));
            lastNoDataWarningTime = currentTime;
        }
    }
}
