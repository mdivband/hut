package tool;

import java.util.logging.Logger;

/**
 * Handles DDS listener functionality with persistent process management and message parsing
 */
public class DDSListener {
    private static final Logger LOGGER = Logger.getLogger(DDSListener.class.getName());
    
    private final PythonExecutor pythonExecutor;    
    private String scriptPath = "C:\\Users\\haris\\hut\\server\\scripts\\pyDDS\\simple_listener.py";
    private double scriptWaitTime = 0.1; // Default wait time in seconds for Python script
    private String lastProcessedOutput = "";
    private final Object outputLock = new Object();
    
    /**
     * Creates a new DDSListener with default settings
     */
    public DDSListener() {
        this.pythonExecutor = new PythonExecutor();
    }
    
    /**
     * Creates a new DDSListener with a custom PythonExecutor
     * @param pythonExecutor The PythonExecutor instance to use
     */
    public DDSListener(PythonExecutor pythonExecutor) {
        this.pythonExecutor = pythonExecutor;
    }
    
    /**
     * Sets the path to the Python DDS listener script
     * @param scriptPath The full path to the Python script
     */
    public void setScriptPath(String scriptPath) {
        this.scriptPath = scriptPath;
        LOGGER.info("DDS script path set to: " + scriptPath);
    }
    
    /**
     * Gets the current script path
     * @return The current script path
     */
    public String getScriptPath() {
        return this.scriptPath;
    }
    
    /**
     * Sets the wait time for the Python script
     * @param waitTimeSeconds Wait time in seconds (default: 0.1)
     */
    public void setScriptWaitTime(double waitTimeSeconds) {
        this.scriptWaitTime = waitTimeSeconds;
    }
    
    /**
     * Sets a custom Python executable path that will be tried first
     * @param pythonPath The full path to the Python executable
     */
    public void setCustomPythonPath(String pythonPath) {
        pythonExecutor.setCustomPythonPath(pythonPath);
    }
    
    /**
     * Starts the persistent DDS listener process
     */
    public void startPersistentListener() {
        boolean success = pythonExecutor.startPersistentScript(scriptPath, "--continuous", "--log_messages");
        if (!success) {
            throw new RuntimeException("Failed to start persistent DDS listener");
        }
        // LOGGER.info("Persistent DDS listener started successfully");
    }
    
    /**
     * Stops the persistent DDS listener process
     */
    public void stopPersistentListener() {
        pythonExecutor.stopPersistentScript();
        synchronized (outputLock) {
            lastProcessedOutput = "";
        }
        LOGGER.info("Persistent DDS listener stopped");
    }
    
    /**
     * Checks if the persistent listener is running and healthy
     * @return true if the persistent process is alive and reading
     */
    public boolean isPersistentListenerHealthy() {
        return pythonExecutor.isPersistentScriptHealthy();
    }
    
    /**
     * Executes the DDS listener script once and prints the output
     * @throws RuntimeException if Python is not available in the environment
     */
    public void executeListenerScript() {
        boolean success = pythonExecutor.executeScript(scriptPath);
        if (!success) {
            throw new RuntimeException("Failed to execute DDS listener script");
        }
        LOGGER.info("DDS listener script executed successfully");
    }
    
    /**
     * Gets new output from the persistent DDS listener process
     * This method starts the persistent listener if not already running.
     * Returns only the new data that hasn't been processed yet
     * @return New output from the DDS script, or empty string if no new data
     */
    public String getScriptOutput() {
        // Ensure persistent listener is running
        if (!pythonExecutor.isPersistentScriptHealthy()) {
            startPersistentListener();
            // Give it a moment to start
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "";
            }
        }
        
        synchronized (outputLock) {
            String rawOutput = pythonExecutor.getOutput();
            
            if (rawOutput.isEmpty()) {
                return ""; // No new data
            }
            
            // First, check for errors in the raw output buffer before extracting messages
            // This ensures error handlers can catch issues even in incomplete or partial output
            try {
                ErrorHandler.checkForErrors(rawOutput, null);
            } catch (Exception e) {
                // If error handler throws an exception, we still want to continue
                // but log the error for debugging
                LOGGER.warning("Error handler detected issue in output: " + e.getMessage());
            }
            
            // Always extract only the latest complete message to avoid concatenation
            String latestMessage = DDSUtils.extractLatestCompleteMessage(rawOutput);
            
            // Check if this message is different from what we last processed
            if (latestMessage.isEmpty() || latestMessage.equals(lastProcessedOutput)) {
                return ""; // No new complete message
            }
            
            // Update the last processed output to this latest message
            lastProcessedOutput = latestMessage;
            
            // Return the latest complete message
            return latestMessage;
        }
    }
}
