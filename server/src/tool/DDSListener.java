package tool;

import java.util.logging.Logger;

/**
 * Handles DDS listener functionality with persistent process management and message parsing
 */
public class DDSListener {
    private static final Logger LOGGER = Logger.getLogger(DDSListener.class.getName());
    
    private final PythonExecutor pythonExecutor;
    private final ErrorHandler errorHandler = new ErrorHandler();
    
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
        boolean success = pythonExecutor.startPersistentScript(scriptPath, "--continuous");
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
                errorHandler.checkForErrors(rawOutput, null);
            } catch (Exception e) {
                // If error handler throws an exception, we still want to continue
                // but log the error for debugging
                LOGGER.warning("Error handler detected issue in output: " + e.getMessage());
            }
            
            // Always extract only the latest complete message to avoid concatenation
            String latestMessage = extractLatestCompleteMessage(rawOutput);
            
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
    
    /**
     * Represents a complete message found in the buffer
     */
    private static class CompleteMessage {
        final String content;
        final int startLineIndex;
        final int endLineIndex;
        
        CompleteMessage(String content, int startLineIndex, int endLineIndex) {
            this.content = content;
            this.startLineIndex = startLineIndex;
            this.endLineIndex = endLineIndex;
        }
    }
    
    /**
     * Parses buffer lines in reverse order to find complete JSON messages
     * @param lines Array of buffer lines
     * @param maxMessages Maximum number of messages to find (0 for unlimited)
     * @return List of complete messages found, in chronological order (oldest first)
     */
    private java.util.List<CompleteMessage> parseCompleteMessages(
        String[] lines, int maxMessages) {
        java.util.List<CompleteMessage> messages = new java.util.ArrayList<>();
        boolean inMessage = false;
        int braceCount = 0;
        StringBuilder currentMessage = new StringBuilder();
        int messageStartLine = -1;
        int messageEndLine = -1;
        
        // Process lines from end to beginning to find the most recent messages
        for (int i = lines.length - 1; i >= 0 && 
            (maxMessages == 0 || messages.size() < maxMessages); i--) {
            String line = lines[i];
            
            // If we find a closing brace and we're not in a message, start collecting
            if (!inMessage && line.trim().equals("}")) {
                currentMessage.insert(0, line + "\n");
                inMessage = true;
                braceCount = -1; // We started with a closing brace
                messageEndLine = i;
                continue;
            }
            
            if (inMessage) {
                currentMessage.insert(0, line + "\n");
                
                // Count braces to find the start of the JSON object
                for (char c : line.toCharArray()) {
                    if (c == '{') braceCount++;
                    else if (c == '}') braceCount--;
                }
                
                // If braces are balanced, we have a complete JSON object
                if (braceCount == 0) {
                    messageStartLine = i;
                    
                    // Look for the header line (should be right before the JSON)
                    if (i > 0 && isHeaderLine(lines[i-1])) {
                        currentMessage.insert(0, lines[i-1] + "\n");
                        messageStartLine = i - 1;
                    }
                    
                    // Add this complete message to our list
                    messages.add(new CompleteMessage(
                        currentMessage.toString().trim(), 
                        messageStartLine, 
                        messageEndLine
                    ));
                    
                    // Reset for next message
                    currentMessage = new StringBuilder();
                    inMessage = false;
                    braceCount = 0;
                    messageStartLine = -1;
                    messageEndLine = -1;
                }
            }
        }
        
        // Reverse the list to get chronological order (oldest first)
        java.util.Collections.reverse(messages);
        return messages;
    }
    
    /**
     * Checks if a line is a header line that precedes JSON data
     * @param line The line to check
     * @return true if the line is a header line
     */
    private boolean isHeaderLine(String line) {
        return line.contains("FlatBuffer Data") || line.contains("String Data");
    }
    
    /**
     * Extracts the latest complete message from the buffer to avoid concatenated JSON
     * @param buffer The full output buffer
     * @return The latest complete JSON message, data availability message, or empty string if none found
     */
    private String extractLatestCompleteMessage(String buffer) {
        if (buffer == null || buffer.trim().isEmpty()) {
            return "";
        }
        
        String[] lines = buffer.split("\n");
        
        // Find the latest complete JSON message and the line index
        java.util.List<CompleteMessage> messages = parseCompleteMessages(lines, 1);
        CompleteMessage latestJsonMessage = null;
        int latestJsonLineIndex = -1;
        
        if (!messages.isEmpty()) {
            latestJsonMessage = messages.get(messages.size() - 1);
            latestJsonLineIndex = latestJsonMessage.endLineIndex;
            
            // Validate that the message contains both header and complete JSON
            String content = latestJsonMessage.content;
            if (!(isHeaderLine(content) && content.contains("{") && 
                  content.contains("}"))) {
                // JSON message is not valid, ignore it
                latestJsonMessage = null;
                latestJsonLineIndex = -1;
            } else {
                // Ensure the JSON part is complete and not truncated
                int jsonStart = content.indexOf("{");
                int jsonEnd = content.lastIndexOf("}");
                if (jsonStart >= jsonEnd) {
                    // JSON is truncated, ignore it
                    latestJsonMessage = null;
                    latestJsonLineIndex = -1;
                }
            }
        }
        
        // Find the latest data availability message and the line index
        String latestDataAvailabilityMessage = null;
        int latestDataAvailabilityLineIndex = -1;
        
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            if (errorHandler.isDataAvailabilityMessage(line)) {
                latestDataAvailabilityMessage = line;
                latestDataAvailabilityLineIndex = i;
                break; // Found the most recent one
            }
        }

        // Compare which message is more recent and return the latest one
        if (latestJsonMessage != null && latestDataAvailabilityMessage != null) {
            // Both types found, return the one that appears later in the buffer
            if (latestDataAvailabilityLineIndex > latestJsonLineIndex) {
                return latestDataAvailabilityMessage;
            } else {
                return latestJsonMessage.content;
            }
        } else if (latestJsonMessage != null) {
            // Only JSON message found
            return latestJsonMessage.content;
        } else if (latestDataAvailabilityMessage != null) {
            // Only data availability message found
            return latestDataAvailabilityMessage;
        }
        
        return "";
    }
}
