package server.controller;

import server.Simulator;
import server.model.*;
import server.model.agents.Agent;
import server.model.agents.AgentVirtual;
import server.model.State;
import server.model.fire.Fire;
import tool.DDSListener;
import tool.DDSUtils;
import tool.PythonExecutor;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Collection;

import com.google.gson.JsonObject;

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
    private String latestDDSMsg = "";
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
    private java.util.HashMap<String, Object> extractedDDSData = new java.util.HashMap<>();
    
    // Mapping of DDS agent IDs to simulator agent IDs
    private java.util.Map<String, String> ddsToSimulatorAgentIdMap = new java.util.HashMap<>();
    private java.util.Map<Integer, String> ddsToSimulatorFireIdMap = new java.util.HashMap<>();

    public DDSController(Simulator simulator) {
        super(simulator, DDSController.class.getName());
        this.ddsListener = new DDSListener();
        this.publisherExecutor = new PythonExecutor();
    }

    // Return the extracted DDS data hashmap
    public java.util.HashMap<String, Object> getExtractedDDSData() {
        synchronized (dataLock) {
            return new java.util.HashMap<>(extractedDDSData);
        }
    }

    /**
     * Gets the current agent data list (for backward compatibility)
     * @return List of agent data where each agent is represented as a list of objects
     */
    public List<List<Object>> getAgentDataList() {
        synchronized (dataLock) {
            List<List<Object>> agentDataList = new ArrayList<>();
            Object agentsObj = extractedDDSData.get("agents");
            if (agentsObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<java.util.HashMap<String, Object>> agents = 
                    (List<java.util.HashMap<String, Object>>) agentsObj;
                
                for (java.util.HashMap<String, Object> agent : agents) {
                    List<Object> agentData = new ArrayList<>();
                    agentData.add(agent.get("agent_id"));
                    agentData.add(agent.get("lat"));
                    agentData.add(agent.get("lng"));
                    agentData.add(agent.get("heading"));
                    agentData.add(agent.get("altitude"));
                    agentData.add(agent.get("battery_level"));
                    agentDataList.add(agentData);
                }
            }
            return agentDataList;
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
        // Update message to stopped state
        updateLatestDDSMessage("", "stopped");

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
        
        // Clear DDS agent mapping and extracted data
        synchronized (dataLock) {
            ddsToSimulatorAgentIdMap.clear();
            ddsToSimulatorFireIdMap.clear();
            extractedDDSData.clear();
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
            String countArg = "200"; // Default count for dev mode
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
                    updateLatestDDSMessage(pythonOutput, "no_publisher");
                } else if (pythonOutput.contains("No data received")) {
                    handleNoDataWarning();
                    updateLatestDDSMessage(pythonOutput, "no_data");
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
                    // Update message after parsing data
                    updateLatestDDSMessage(pythonOutput, "valid_data");
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
     * Parses JSON data and extracts agent information using DDSUtils HashMap approach
     * @param data The raw DDS data containing JSON
     * @return true if parsing was successful, false otherwise
     */
    private boolean parseAndExtractAgentData(String data) {
        try {
            // Use DDSUtils to extract data into HashMap
            java.util.HashMap<String, Object> newExtractedData = DDSUtils.extractDDSData(data);
            
            if (newExtractedData.isEmpty()) {
                LOGGER.warning(String.format(
                    "%s; DDSWRN; Failed to extract DDS data - validation failed or invalid format",
                    simulator.getState().getTime()));
                return false;
            }
            
            // Update the class member with extracted data
            synchronized (dataLock) {
                extractedDDSData.clear();
                extractedDDSData.putAll(newExtractedData);
                
                // Log success
                Object agentsObj = extractedDDSData.get("agents");
                int agentCount = 0;
                if (agentsObj instanceof List) {
                    agentCount = ((List<?>) agentsObj).size();
                }
                
                LOGGER.info(String.format(
                    "%s; DDSEX; Successfully extracted %d agents from DDS data", 
                    simulator.getState().getTime(), agentCount));
            }
            
            return true; // Success
                
        } catch (Exception e) {
            LOGGER.severe(String.format(
                "%s; DDSER; Failed to parse DDS data using DDSUtils; %s",
                simulator.getState().getTime(), e.getMessage()));
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
     * Updates the simulator with agent data from DDS using the extracted HashMap data
     * @return true if simulator was updated successfully, false if no agent data to process
     */
    public boolean updateSimulator() {
        synchronized (dataLock) {
            Object agentsObj = extractedDDSData.get("agents");
            if (!(agentsObj instanceof List) || ((List<?>) agentsObj).isEmpty()) {
                return false; // No agent data to process
            }

            Object firesObj = extractedDDSData.get("fires");
            if (firesObj instanceof List && !((List<?>) firesObj).isEmpty()) {
                @SuppressWarnings("unchecked")
                List<java.util.HashMap<String, Object>> fires =
                        (List<java.util.HashMap<String, Object>>) firesObj;

                for (java.util.HashMap<String, Object> fireData : fires) {
                    try {
                        int ddsFireId = (Integer) fireData.get("id");
                        double lat = (Double) fireData.get("latitude");
                        double lng = (Double) fireData.get("longitude");

                        String simulatorFireId = ddsToSimulatorFireIdMap.get(ddsFireId);
                        Fire existingFire = null;

                        if (simulatorFireId != null) {
                            existingFire = simulator.getState().getFire(simulatorFireId);
                        }

                        if (existingFire == null) {
                            // Fire doesn't exist, create it.
                            String newId = simulator.getFireController().generateUID(ddsFireId);
                            Fire newFire = simulator.getFireController().addFire(newId, lat, lng);

                            // Store the mapping for future updates.
                            ddsToSimulatorFireIdMap.put(ddsFireId, newId);

                            LOGGER.info(String.format(
                                    "%s; DDSFIRE; Created new DDS fire; DDS_ID=%d, SIM_ID=%s, Position=(%.6f,%.6f)",
                                    simulator.getState().getTime(), ddsFireId, newId, lat, lng));
                        } else {
                            // Fire exists, update its position.
                            existingFire.setCoordinate(new Coordinate(lat, lng));

                            LOGGER.fine(String.format(
                                    "%s; DDSFIREUP; Updated existing DDS fire; SIM_ID=%s, Position=(%.6f,%.6f)",
                                    simulator.getState().getTime(), existingFire.getId(), lat, lng));
                        }

                    } catch (Exception e) {
                        LOGGER.severe(String.format(
                                "%s; DDSER; Failed to update simulator with fire data; %s",
                                simulator.getState().getTime(), e.getMessage()));
                    }
                }
            }

            @SuppressWarnings("unchecked")
            List<java.util.HashMap<String, Object>> agents = 
                (List<java.util.HashMap<String, Object>>) agentsObj;

            for (java.util.HashMap<String, Object> agentData : agents) {
                try {
                    // Extract agent data from HashMap
                    String ddsAgentId = (String) agentData.get("agent_id");
                    double lat = (Double) agentData.get("lat");
                    double lng = (Double) agentData.get("lng");
                    double heading = (Double) agentData.get("heading");
                    double altitude = (Double) agentData.get("altitude");
                    double batteryLevel = ((Integer) agentData.get("battery_level")).doubleValue();

                    // Create coordinate for this agent
                    Coordinate agentCoordinate = new Coordinate(lat, lng);

                    // Check if we have a mapping for this DDS agent ID
                    String simulatorAgentId = ddsToSimulatorAgentIdMap.get(ddsAgentId);
                    Agent existingAgent = null;
                    
                    if (simulatorAgentId != null) {
                        existingAgent = simulator.getState().getAgent(simulatorAgentId);
                    }
                    
                    if (existingAgent == null) {
                        // Agent doesn't exist, create new virtual agent with string ID
                        Agent newAgent = simulator.getAgentController().addIdVirtualAgent(
                            ddsAgentId, lat, lng, heading);
                        newAgent.setAltitude(altitude);
                        newAgent.setBattery(batteryLevel);
                        
                        // Store the mapping between DDS ID and simulator ID
                        ddsToSimulatorAgentIdMap.put(ddsAgentId, newAgent.getId());
                        
                        // Check for waypoint data and add to route if available
                        Object waypointObj = agentData.get("waypoint");
                        if (waypointObj instanceof java.util.HashMap) {
                            @SuppressWarnings("unchecked")
                            java.util.HashMap<String, Object> waypoint = 
                                (java.util.HashMap<String, Object>) waypointObj;
                            
                            // At the begining, we set the first waypoint as the current position
                            // double wpLat = (Double) waypoint.get("lat");
                            // double wpLng = (Double) waypoint.get("lng");
                            Coordinate waypointCoordinate = new Coordinate(lat, lng);
                            
                            // Add waypoint to new agent's waypoints list
                            newAgent.addWaypoint(waypointCoordinate);
                            
                            LOGGER.info(String.format(
                                "%s; DDSWP; Set the inital position as first waypoint to new agent; DDS_ID=%s, Waypoint=(%.6f,%.6f)", 
                                simulator.getState().getTime(), ddsAgentId, lat, lng));
                        }
                        
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
                        
                        // Check for waypoint data and update route if needed
                        Object waypointObj = agentData.get("waypoint");
                        if (waypointObj instanceof java.util.HashMap) {
                            @SuppressWarnings("unchecked")
                            java.util.HashMap<String, Object> waypoint = 
                                (java.util.HashMap<String, Object>) waypointObj;
                            
                            double wpLat = (Double) waypoint.get("lat");
                            double wpLng = (Double) waypoint.get("lng");
                            Coordinate waypointCoordinate = new Coordinate(wpLat, wpLng);
                            
                            // Check if waypoint is already the last coordinate in the route
                            List<Coordinate> currentRoute = existingAgent.getRoute();
                            // Add waypoint to existing agent's waypoints list
                            if (existingAgent.addWaypoint(waypointCoordinate)) {
                                LOGGER.info(String.format(
                                    "%s; DDSWP; Added new waypoint to existing agent; DDS_ID=%s, Waypoint=(%.6f,%.6f)", 
                                    simulator.getState().getTime(), ddsAgentId, wpLat, wpLng));
                            }
                        }
                        
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
            
            // Get the waypoints so the code to print them can run
            JsonObject waypoints = getAllAgentWaypoints();
            LOGGER.info(String.format(
                "%s; DDSSIM; Updated simulator with %d agents from DDS data", 
                simulator.getState().getTime(), agents.size()));
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

    /**
     * Updates the latest DDS message based on controller state and data
     * @param rawMessage The raw message from the DDS listener for error reporting if needed
     * @param messageType Type of message: "stopped", "no_publisher", "no_data", "valid_data"
     */
    private void updateLatestDDSMessage(String rawMessage, String messageType) {
        synchronized (dataLock) {
            String timestamp = DDSUtils.parseTime(simulator.getState().getTime());
            
            switch (messageType) {
                case "stopped":
                    latestDDSMsg = "DDS Controller Stopped";
                    break;
                    
                case "no_publisher":
                    latestDDSMsg = "Publisher may not be active";
                    break;
                    
                case "no_data":
                    latestDDSMsg = String.format(
                        "[%s] No DDS data received - waiting for publisher", 
                        timestamp);
                    break;
                    
                case "valid_data":
                    // Format the JSON message with timestamp and agent count
                    try {
                        // Get agent count from extracted data
                        int agentCount = 0;
                        Object agentsObj = extractedDDSData.get("agents");
                        if (agentsObj instanceof List) {
                            agentCount = ((List<?>) agentsObj).size();
                        }
                        
                        // Use DDSUtils to build formatted message
                        String formattedMessage = DDSUtils.buildDDSMsgString(extractedDDSData);
                        latestDDSMsg = String.format("[%s] DDS Data received - %d agents: %s", 
                            timestamp, agentCount, formattedMessage);
                    } catch (Exception e) {
                        latestDDSMsg = String.format(
                            "[%s] DDS Data received (parsing error): %s", 
                            timestamp, rawMessage.substring(
                                0, Math.min(100, rawMessage.length())));
                    }
                    break;
                    
                default:
                    latestDDSMsg = String.format("[%s] Unknown DDS status: %s", 
                                                 timestamp, rawMessage);
                    break;
            }
        }
    }

    /** Compile the hub status json object
        Example expected data:
        {
        "location": "(37.7749, -122.4194)",
        "operators": 2,
        "sta": { "active": 3, "inactive": 1, "ready": 2 },
        "fsa": { "active": 1, "inactive": 2, "ready": 1 }
        }
        @return JsonObject representing the hub status
      */
    public JsonObject getHubStatus() {
        JsonObject hubStatus = new JsonObject();
        hubStatus.addProperty("location", this.simulator.getState().getGameCentre().toString());
        hubStatus.addProperty("operators", 2);

        int staActive = 0, staInactive = 0, staReady = 0;
        int fsaActive = 0, fsaInactive = 0, fsaReady = 0;

        synchronized (dataLock) {
            Object agentsObj = extractedDDSData.get("agents");
            if (agentsObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<java.util.HashMap<String, Object>> agents = (List<java.util.HashMap<String, Object>>) agentsObj;
                for (java.util.HashMap<String, Object> agent : agents) {
                    String agentId = (String) agent.get("agent_id");
                    // Assuming status: 0=active, 1=inactive, 2=ready
                    Integer status = (Integer) agent.get("status");
                    if (agentId != null) {
                        if (agentId.toUpperCase().contains("STA")) {
                            if (status != null) {
                                if (status == 0) staActive++;
                                else if (status == 1) staInactive++;
                                else if (status == 2) staReady++;
                            }
                        } else if (agentId.toUpperCase().contains("FSA")) {
                            if (status != null) {
                                if (status == 0) fsaActive++;
                                else if (status == 1) fsaInactive++;
                                else if (status == 2) fsaReady++;
                            }
                        }
                    }
                }
            }
        }

        JsonObject sta = new JsonObject();
        sta.addProperty("active", staActive);
        sta.addProperty("inactive", staInactive);
        sta.addProperty("ready", staReady);
        hubStatus.add("sta", sta);

        JsonObject fsa = new JsonObject();
        fsa.addProperty("active", fsaActive);
        fsa.addProperty("inactive", fsaInactive);
        fsa.addProperty("ready", fsaReady);
        hubStatus.add("fsa", fsa);

        return hubStatus;
    }

    /**
     * Get all the waypoints from all agents in the simulator
     Example expected data:
        {
            "STA-1": { "0": "lat, lng", "1": "lat, lng", "2": "lat, lng" },
            "FSA-1": { "0": "lat, lng", "1": "lat, lng", "2": "lat, lng" }
        } or {} if no waypoints
    * @return JsonObject containing all agent waypoints
    */
    public JsonObject getAllAgentWaypoints() {
        JsonObject allWaypoints = new JsonObject();
        
        try {
            Collection<Agent> allAgents = simulator.getState().getAgents();
            
            for (Agent agent : allAgents) {
                String agentId = agent.getId();
                JsonObject agentWaypoints = new JsonObject();
                
                List<Coordinate> waypoints = agent.getWaypoints();
                
                if (waypoints != null && !waypoints.isEmpty()) {
                    for (int i = 0; i < waypoints.size(); i++) {
                        Coordinate waypoint = waypoints.get(i);
                        agentWaypoints.addProperty(String.valueOf(i), waypoint.toString());
                    }
                }
                allWaypoints.add(agentId, agentWaypoints);
                // System.out.println("Agent " + agentId + " waypoints: " + agentWaypoints.toString());
            }
            
            LOGGER.fine(String.format(
                "%s; DDSWP; Retrieved waypoints for %d agents", 
                simulator.getState().getTime(), allAgents.size()));
                
        } catch (Exception e) {
            LOGGER.severe(String.format(
                "%s; DDSER; Failed to retrieve agent waypoints; %s", 
                simulator.getState().getTime(), e.getMessage()));
            return new JsonObject();
        }
        
        return allWaypoints;
    }

    // Return the latest DDS message
    public String getLatestDDSMessage() {
        synchronized (dataLock) {
            return latestDDSMsg;
        }
    }
}