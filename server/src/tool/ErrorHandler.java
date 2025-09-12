package tool;

/**
 * Handles various types of errors that can occur during Python script execution
 */
public class ErrorHandler {
    
    /**
     * Checks for various types of errors and throws appropriate exceptions
     * @param output The output from the Python script (can be null)
     * @param exception The exception that occurred (can be null)
     * @throws RuntimeException with appropriate error message based on the type of error detected
     */
    public static void checkForErrors(String output, Exception exception) {
        // Check for Python not found errors
        if (isPythonNotFoundError(output, exception)) {
            throw new RuntimeException(
                "Cannot run pyDDS as Python is not available in this environment");
        }
        
        // Check for script not found errors
        if (isScriptNotFoundError(output, exception)) {
            throw new RuntimeException(
                "Cannot run pyDDS as the Python script was not found at the specified path");
        }
        
        // Check for permission errors
        if (isPermissionError(output, exception)) {
            throw new RuntimeException(
                "Cannot run pyDDS due to permission restrictions on the Python script");
        }
        
        // Check for module import errors
        if (isModuleImportError(output, exception)) {
            throw new RuntimeException(
                "Cannot run pyDDS due to missing Python dependencies (e.g. zenoh, flatbuffers). Are you in the correct Python environment?");
        }
        
        // Check for general Python syntax or runtime errors (but not data availability issues)
        if (output != null && !isDataAvailabilityMessage(output)) {
            if (output.contains("Traceback (most recent call last)") ||
                output.contains("SyntaxError") ||
                output.contains("NameError") ||
                output.contains("AttributeError") ||
                output.contains("TypeError") ||
                output.contains("ValueError")) {
                throw new RuntimeException("Python script execution error: " + output);
            }
        }
        
        // Note: We no longer check for "no data" or "no publisher" errors here
        // These are handled as warnings in the update() method with rate limiting
    }
    
    /**
     * Checks if the output message is related to data availability (not an error)
     */
    public static boolean isDataAvailabilityMessage(String output) {
        return output.contains("No data received") ||
               output.contains("Empty Data:") ||
               output.contains("Empty data received") ||
               output.contains("DDS Listener started, waiting for");
    }

    // Checks if a line is a header line that precedes JSON data
    public static boolean isHeaderLine(String line) {
        return line.contains("FlatBuffer Data") || line.contains("String Data");
    }
    
    /**
     * Checks if the error is related to Python not being found
     */
    private static boolean isPythonNotFoundError(String output, Exception exception) {
        return containsErrorPattern(output, exception, "python", 
                new String[]{"not found", "is not recognized", "command not found"});
    }
    
    /**
     * Checks if the error is related to the script file not being found
     */
    private static boolean isScriptNotFoundError(String output, Exception exception) {
        return containsErrorPattern(output, exception, ".py",
                new String[]{"no such file", "file not found", "cannot find"}) ||
               containsErrorPattern(output, exception, "FileNotFoundError", new String[]{""});
    }
    
    /**
     * Checks if the error is related to permission issues
     */
    private static boolean isPermissionError(String output, Exception exception) {
        return containsErrorPattern(output, exception, "permission",
                new String[]{"denied", "access denied"}) ||
               containsErrorPattern(output, exception, "PermissionError", new String[]{""});
    }
    
    /**
     * Checks if the error is related to missing Python modules
     */
    private static boolean isModuleImportError(String output, Exception exception) {
        return containsErrorPattern(output, exception, "ModuleNotFoundError", new String[]{""}) ||
               containsErrorPattern(output, exception, "ImportError", new String[]{""}) ||
               containsErrorPattern(output, exception, "No module named", new String[]{""});
    }
    
    /**
     * Helper method to check if output or exception contains specific error patterns
     */
    private static boolean containsErrorPattern(
        String output, Exception exception, String keyword, String[] patterns) {
        String[] sources = new String[2];
        sources[0] = output != null ? output.toLowerCase() : "";
        sources[1] = exception != null ? exception.getMessage().toLowerCase() : "";
        
        for (String source : sources) {
            if (source.contains(keyword.toLowerCase())) {
                for (String pattern : patterns) {
                    if (pattern.isEmpty() || source.contains(pattern.toLowerCase())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
