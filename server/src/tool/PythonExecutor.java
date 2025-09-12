package tool;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.File;
import java.util.logging.Logger;

/**
 * Simple Python script executor that can run scripts once or persistently
 */
public class PythonExecutor {
    private static final Logger LOGGER = Logger.getLogger(PythonExecutor.class.getName());
    
    private final String[] defaultPythonCommands = {"py", "python", "python3"};
    private String customPythonPath = null;
    private String cachedWorkingCommand = null; // Cache the working Python command
    
    // Persistent process management
    private Process persistentProcess = null;
    private BufferedReader persistentReader = null;
    private Thread readerThread = null;
    private volatile boolean isReaderRunning = false;
    private final Object outputLock = new Object();
    private String outputBuffer = "";
    private final int maxBufferSize = 10000; // Maximum buffer size to prevent memory issues
    
    // Async execution management
    private Process asyncProcess = null;
    private BufferedReader asyncReader = null;
    private Thread asyncReaderThread = null;
    private volatile boolean isAsyncRunning = false;
    private final Object asyncLock = new Object();
    private String asyncOutput = "";
    private Integer exitCode = null;
    private Exception executionException = null;
    private volatile boolean asyncCompleted = false; // Flag to track if completion has been processed
    
    
    /**
     * Sets a custom Python executable path that will be tried first
     * @param pythonPath The full path to the Python executable
     */
    public void setCustomPythonPath(String pythonPath) {
        // Normalize the script path for platform compatibility
        this.customPythonPath = normalizePath(pythonPath);
        // Clear the cache when custom path changes, so we re-test
        this.cachedWorkingCommand = null;
    }
    
    /**
     * Gets the list of Python commands to try, with custom path first if available
     * @return Array of Python commands to try in order
     */
    private String[] getPythonCommands() {
        if (customPythonPath != null && !customPythonPath.trim().isEmpty()) {
            String[] commands = new String[defaultPythonCommands.length + 1];
            commands[0] = customPythonPath;
            System.arraycopy(defaultPythonCommands, 0, commands, 1, 
                defaultPythonCommands.length);
            return commands;
        }
        return defaultPythonCommands;
    }
    
    /**
     * Tries to find a working Python command from the available options
     * Uses cached result if available to avoid repeated testing
     * @return The first working Python command, or null if none work
     */
    private String findWorkingPythonCommand() {
        // Return cached command if we already found one and custom path hasn't changed
        if (cachedWorkingCommand != null) {
            return cachedWorkingCommand;
        }
        
        String[] pythonCommands = getPythonCommands();
        for (String command : pythonCommands) {
            try {
                ProcessBuilder testBuilder = new ProcessBuilder(command, "--version");
                Process testProcess = testBuilder.start();
                int exitCode = testProcess.waitFor();
                if (exitCode == 0) {
                    // Cache the working command for future use
                    cachedWorkingCommand = command;
                    LOGGER.info("Found working Python command: " + command);
                    return command;
                }
            } catch (IOException | InterruptedException e) {
                // Continue to next command
            }
        }
        return null;
    }
    
    /**
     * Executes a Python script once with the given arguments
     * @param scriptPath The path to the Python script
     * @param args Additional arguments to pass to the script
     * @return true if execution was successful, false otherwise
     */
    public boolean executeScript(String scriptPath, String... args) {
        String pythonCommand = findWorkingPythonCommand();
        if (pythonCommand == null) {
            ErrorHandler.checkForErrors(null, 
                new RuntimeException(
                    "Python is not available in this environment"));
            return false;
        }

        // Normalize the script path for platform compatibility
        scriptPath = normalizePath(scriptPath);
        
        // Validate that the script exists
        if (!isPathValid(scriptPath)) {
            ErrorHandler.checkForErrors(null, 
                new RuntimeException("Script file not found or not accessible: " + scriptPath));
            return false;
        }        
        
        try {
            // Build command with script path and arguments
            String[] command = new String[args.length + 2];
            command[0] = pythonCommand;
            command[1] = scriptPath;
            System.arraycopy(args, 0, command, 2, args.length);
            
            // Create the process to run the Python script
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true); // Combine stdout and stderr
            
            Process process = processBuilder.start();
            
            // Read the output from the script
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream())
            );
            
            String line;
            StringBuilder output = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            
            // Wait for the process to complete
            int exitCode = process.waitFor();
            reader.close();
            
            // Check for errors in the output
            ErrorHandler.checkForErrors(output.toString(), null);
            
            return exitCode == 0;
            
        } catch (IOException e) {
            ErrorHandler.checkForErrors(null, e);
            return false;
        } catch (InterruptedException e) {
            LOGGER.warning("Python script execution was interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
            return false;
        }
    }
    
    /**
     * Executes a Python script once and returns its output
     * @param scriptPath The path to the Python script
     * @param args Additional arguments to pass to the script
     * @return The output from the Python script, or null if execution failed
     */
    public String executeScriptAndGetOutput(String scriptPath, String... args) {
        String pythonCommand = findWorkingPythonCommand();
        if (pythonCommand == null) {
            ErrorHandler.checkForErrors(null, 
                new RuntimeException("Python is not available in this environment"));
            return null;
        }

        // Normalize the script path for platform compatibility
        scriptPath = normalizePath(scriptPath);
        
        // Validate that the script exists
        if (!isPathValid(scriptPath)) {
            ErrorHandler.checkForErrors(null, 
                new RuntimeException("Script file not found or not accessible: " + scriptPath));
            return null;
        } 
        
        try {
            // Build command with script path and arguments
            String[] command = new String[args.length + 2];
            command[0] = pythonCommand;
            command[1] = scriptPath;
            System.arraycopy(args, 0, command, 2, args.length);
            
            // Create the process to run the Python script
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true); // Combine stdout and stderr
            
            Process process = processBuilder.start();
            
            // Read the output from the script
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream())
            );
            
            String line;
            StringBuilder output = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            
            // Wait for the process to complete
            int exitCode = process.waitFor();
            reader.close();
            
            String outputStr = output.toString();
            
            // Check for errors in the output
            ErrorHandler.checkForErrors(outputStr, null);
            
            return exitCode == 0 ? outputStr : null;
            
        } catch (IOException e) {
            ErrorHandler.checkForErrors(null, e);
            return null;
        } catch (InterruptedException e) {
            LOGGER.warning("Python script execution was interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
            return null;
        }
    }
    
    /**
     * Starts a persistent Python script process
     * @param scriptPath The path to the Python script
     * @param args Additional arguments to pass to the script
     * @return true if the persistent process was started successfully
     */
    public boolean startPersistentScript(String scriptPath, String... args) {
        if (persistentProcess != null && persistentProcess.isAlive()) {
            return true; // Already running
        }
        
        String pythonCommand = findWorkingPythonCommand();
        if (pythonCommand == null) {
            ErrorHandler.checkForErrors(null, 
                new RuntimeException("Python is not available in this environment"));
            return false;
        }

        // Normalize the script path for platform compatibility
        scriptPath = normalizePath(scriptPath);
        
        // Validate that the script exists
        if (!isPathValid(scriptPath)) {
            ErrorHandler.checkForErrors(null, 
                new RuntimeException("Script file not found or not accessible: " + scriptPath));
            return false;
        } 
        
        try {
            // Build command with script path and arguments
            String[] command = new String[args.length + 2];
            command[0] = pythonCommand;
            command[1] = scriptPath;
            System.arraycopy(args, 0, command, 2, args.length);
            
            // Start the Python process
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);
            
            persistentProcess = processBuilder.start();
            persistentReader = new BufferedReader(
                new InputStreamReader(persistentProcess.getInputStream())
            );
            
            // Start the reader thread
            isReaderRunning = true;
            readerThread = new Thread(this::readOutputContinuously, "Python-Reader");
            readerThread.setDaemon(true);
            readerThread.start();
            
            // Wait a bit and check if the process is still alive
            try {
                Thread.sleep(100); // Wait 100ms for process to potentially fail
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            if (!persistentProcess.isAlive()) {
                // Process failed to start or crashed immediately
                int exitCode = persistentProcess.exitValue();
                
                // Give the reader thread a moment to capture any error output
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                
                // Get any error output that was captured by the reader thread
                String errorOutput;
                synchronized (outputLock) {
                    errorOutput = outputBuffer;
                }
   
                // Clean up
                stopPersistentScript();
                
                // Handle the error
                if (!errorOutput.isEmpty()) {
                    ErrorHandler.checkForErrors(errorOutput, null);
                } else {
                    ErrorHandler.checkForErrors(null, 
                        new RuntimeException(
                            "Python process failed to start. Exit code: " + exitCode));
                }
                return false;
            }
            
            LOGGER.info("Persistent Python script started successfully");
            return true;
            
        } catch (IOException e) {
            ErrorHandler.checkForErrors(null, e);
            return false;
        }
    }
    
    /**
     * Stops the persistent Python script process
     */
    public void stopPersistentScript() {
        isReaderRunning = false;
        
        if (readerThread != null) {
            readerThread.interrupt();
            try {
                readerThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            readerThread = null;
        }
        
        if (persistentReader != null) {
            try {
                persistentReader.close();
            } catch (IOException e) {
                // Ignore close errors
            }
            persistentReader = null;
        }
        
        if (persistentProcess != null && persistentProcess.isAlive()) {
            persistentProcess.destroyForcibly();
            try {
                persistentProcess.waitFor(
                    5000, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            persistentProcess = null;
        }
        
        synchronized (outputLock) {
            outputBuffer = "";
        }
        
        LOGGER.info("Persistent Python script stopped");
    }
    
    /**
     * Gets new output from the persistent Python process
     * @return New output from the Python script, or empty string if no new data
     */
    public String getOutput() {
        synchronized (outputLock) {
            String output = outputBuffer;
            outputBuffer = ""; // Clear buffer after reading
            return output;
        }
    }
    
    /**
     * Checks if the persistent script is running and healthy
     * @return true if the persistent process is alive and reading
     */
    public boolean isPersistentScriptHealthy() {
        return persistentProcess != null && persistentProcess.isAlive() && 
               readerThread != null && readerThread.isAlive() && isReaderRunning;
    }
    
    /**
     * Continuously reads output from the persistent Python process
     */
    private void readOutputContinuously() {
        String line;
        try {
            while (isReaderRunning && !Thread.currentThread().isInterrupted() && 
                   persistentReader != null && persistentProcess != null 
                   && persistentProcess.isAlive()) {
                
                line = persistentReader.readLine();
                if (line != null) {
                    synchronized (outputLock) {
                        outputBuffer += line + "\n";
                        
                        // Trim buffer if it gets too large
                        if (outputBuffer.length() > maxBufferSize) {
                            outputBuffer = outputBuffer.substring(
                                Math.max(0, outputBuffer.length() - maxBufferSize/2));
                        }
                    }
                }
            }
        } catch (IOException e) {
            if (isReaderRunning) {
                LOGGER.severe(
                    "Error reading from persistent Python process: " + e.getMessage());
            }
        }
    }
    
    /**
     * Starts a Python script execution on a separate thread (async)
     * @param scriptPath The path to the Python script
     * @param args Additional arguments to pass to the script
     * @return true if the script was started successfully
     */
    public boolean startAsyncScript(String scriptPath, String... args) {
        // Stop any existing async process
        stopAsyncScript();
        
        String pythonCommand = findWorkingPythonCommand();
        if (pythonCommand == null) {
            synchronized (asyncLock) {
                executionException = new RuntimeException(
                    "Python is not available in this environment");
            }
            return false;
        }

        // Normalize the script path for platform compatibility
        scriptPath = normalizePath(scriptPath);
        
        // Validate that the script exists
        if (!isPathValid(scriptPath)) {
            ErrorHandler.checkForErrors(null, 
                new RuntimeException("Script file not found or not accessible: " + scriptPath));
            return false;
        } 
        
        try {
            // Build command with script path and arguments
            String[] command = new String[args.length + 2];
            command[0] = pythonCommand;
            command[1] = scriptPath;
            System.arraycopy(args, 0, command, 2, args.length);
            
            // Start the Python process
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);
            
            asyncProcess = processBuilder.start();
            asyncReader = new BufferedReader(
                new InputStreamReader(asyncProcess.getInputStream())
            );
            
            // Reset state
            synchronized (asyncLock) {
                asyncOutput = "";
                exitCode = null;
                executionException = null;
                isAsyncRunning = true;
                asyncCompleted = false; // Reset completion flag for new execution
            }
            
            // Start the reader thread
            asyncReaderThread = new Thread(
                this::readAsyncOutput, "Async-Python-Reader");
            asyncReaderThread.setDaemon(true);
            asyncReaderThread.start();
            
            // Wait a bit and check if the process is still alive
            try {
                Thread.sleep(100); // Wait 100ms for process to potentially fail
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            if (!asyncProcess.isAlive()) {
                // Process failed to start or crashed immediately
                int processExitCode = asyncProcess.exitValue();
                
                // Give the reader thread a moment to capture any error output
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                
                // Get any error output that was captured by the reader thread
                String errorOutput;
                synchronized (asyncLock) {
                    errorOutput = asyncOutput;
                }
                
                // Clean up and set error state
                stopAsyncScript();
                
                // Handle the error
                if (!errorOutput.isEmpty()) {
                    ErrorHandler.checkForErrors(errorOutput, null);
                } else {
                    ErrorHandler.checkForErrors(null, 
                        new RuntimeException(
                            "Python process failed to start. Exit code: " + processExitCode));
                }
                return false;
            }
            
            LOGGER.info("Async Python script started successfully");
            return true;
            
        } catch (IOException e) {
            synchronized (asyncLock) {
                executionException = e;
                isAsyncRunning = false;
            }
            return false;
        }
    }
    
    /**
     * Stops the async Python script execution
     */
    public void stopAsyncScript() {
        // Early return if nothing is running
        synchronized (asyncLock) {
            if (!isAsyncRunning && 
                (asyncReaderThread == null || !asyncReaderThread.isAlive()) && 
                (asyncProcess == null || !asyncProcess.isAlive())) {
                return; // Nothing to stop
            }
            isAsyncRunning = false;
        }
        
        if (asyncReaderThread != null && asyncReaderThread.isAlive()) {
            asyncReaderThread.interrupt();
            try {
                asyncReaderThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        asyncReaderThread = null;
        
        if (asyncReader != null) {
            try {
                asyncReader.close();
            } catch (IOException e) {
                // Ignore close errors
            }
            asyncReader = null;
        }
        
        if (asyncProcess != null && asyncProcess.isAlive()) {
            asyncProcess.destroyForcibly();
            try {
                asyncProcess.waitFor(
                    5000, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        asyncProcess = null;
        
        LOGGER.info("Async Python script stopped");
    }
    
    /**
     * Checks if the async script is still running
     * @return true if the script is still running
     */
    public boolean isAsyncScriptRunning() {
        synchronized (asyncLock) {
            return isAsyncRunning && 
                   asyncProcess != null && 
                   asyncProcess.isAlive();
        }
    }
    
    /**
     * Checks if the async script has finished (either successfully or with error)
     * @return true if the script has finished
     */
    public boolean isAsyncScriptFinished() {
        synchronized (asyncLock) {
            return !isAsyncRunning || 
                   (asyncProcess != null && !asyncProcess.isAlive());
        }
    }
    
    /**
     * Gets the exit code of the async script (if finished)
     * @return The exit code, or null if still running or not started
     */
    public Integer getAsyncScriptExitCode() {
        synchronized (asyncLock) {
            if (asyncProcess != null && !asyncProcess.isAlive()) {
                if (exitCode == null) {
                    exitCode = asyncProcess.exitValue();
                }
                return exitCode;
            }
            return null;
        }
    }
    
    /**
     * Gets the current output from the async script
     * @return The accumulated output from the script
     */
    public String getAsyncScriptOutput() {
        synchronized (asyncLock) {
            return asyncOutput;
        }
    }
    
    /**
     * Gets any execution exception that occurred during async script execution
     * @return The exception, or null if no exception occurred
     */
    public Exception getAsyncScriptException() {
        synchronized (asyncLock) {
            return executionException;
        }
    }
    
    /**
     * Marks the async script completion as processed by the controller
     * This should be called after the controller has handled the completion status
     */
    public void markAsyncScriptCompleted() {
        synchronized (asyncLock) {
            asyncCompleted = true;
        }
    }
    
    /**
     * Checks if the async script completion has been processed by the controller
     * @return true if completion has been processed, false otherwise
     */
    public boolean isAsyncScriptCompleted() {
        synchronized (asyncLock) {
            return asyncCompleted;
        }
    }
    
    /**
     * Continuously reads output from the async Python process
     */
    private void readAsyncOutput() {
        String line;
        try {
            while (isAsyncRunning && !Thread.currentThread().isInterrupted() && 
                   asyncReader != null && asyncProcess != null) {
                
                line = asyncReader.readLine();
                if (line != null) {
                    synchronized (asyncLock) {
                        asyncOutput += line + "\n";
                        
                        // Trim buffer if it gets too large
                        if (asyncOutput.length() > maxBufferSize) {
                            asyncOutput = asyncOutput.substring(
                                Math.max(0, asyncOutput.length() - maxBufferSize/2));
                        }
                    }
                } else {
                    // End of stream reached, process has finished
                    break;
                }
            }
        } catch (IOException e) {
            if (isAsyncRunning) {
                synchronized (asyncLock) {
                    executionException = e;
                }
                LOGGER.severe("Error reading from async Python process: " + e.getMessage());
            }
        } finally {
            synchronized (asyncLock) {
                isAsyncRunning = false;
                if (asyncProcess != null && !asyncProcess.isAlive()) {
                    exitCode = asyncProcess.exitValue();
                }
            }
        }
    }

        /**
     * Normalizes a file path to be compatible with the current platform
     * Converts path separators and handles platform-specific path formats
     * @param path The path to normalize
     * @return The normalized path compatible with the current platform
     */
    private String normalizePath(String path) {
        if (path == null || path.trim().isEmpty()) {
            return path;
        }
        
        // Use File.separator to get the platform-specific separator
        String normalizedPath = path.replace('\\', File.separatorChar)
                                   .replace('/', File.separatorChar);
        
        // Create a File object to further normalize the path
        File file = new File(normalizedPath);
        return file.getPath();
    }
    
    /**
     * Checks if a path exists and is accessible on the current platform
     * @param path The path to check
     * @return true if the path exists and is accessible, false otherwise
     */
    private boolean isPathValid(String path) {
        if (path == null || path.trim().isEmpty()) {
            return false;
        }
        
        try {
            File file = new File(normalizePath(path));
            return file.exists() && file.canRead();
        } catch (SecurityException e) {
            LOGGER.warning("Security exception when checking path: " + path + " - " + e.getMessage());
            return false;
        }
    }
}
