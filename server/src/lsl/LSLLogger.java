package lsl;

/**
 * This class airgaps the logging from the details of Lab Streaming Layer (LSL). Basically the methods are simpler and
 * the main simulator needs never handle the details of the connection.
 */
public class LSLLogger {
    private final OxysoftConnection oxysoftConnection;

    public LSLLogger() {
        oxysoftConnection = new OxysoftConnection();
    }

    public void logEventMarker(String condition) {
        oxysoftConnection.sendEventMarker(condition);
    }

    public void close() {
        oxysoftConnection.close();
    }

}
