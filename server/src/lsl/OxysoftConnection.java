package lsl;

/**
 * This class handles the connection to an LSL stream for sending event markers.
 */
public class OxysoftConnection {
    private LSL.StreamInfo streamInfo;
    private LSL.StreamOutlet outlet;

    public OxysoftConnection() {
        // Initialize the LSL stream
        try {
            streamInfo = new LSL.StreamInfo("OxysoftMarkers", "Markers", 1, 0, LSL.ChannelFormat.string, "uniqueID12345");
            outlet = new LSL.StreamOutlet(streamInfo);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Failed to create LSL stream.");
        }
    }

    public void sendEventMarker(String conditionCode) {
        if (!conditionCode.isEmpty()) {
            outlet.push_sample(new String[]{conditionCode});
            System.out.println("LSL-Log: Event marker '" + conditionCode + "' sent.");
        }
    }

    // Optionally, you can define a cleanup method, although LSL will clean up on its own
    public void close() {
        if (outlet != null) {
            outlet.close();
        }
        if (streamInfo != null) {
            streamInfo.destroy();
        }
    }

    // Example usage
    public static void main(String[] args) {
        OxysoftConnection oxysoftConnection = new OxysoftConnection();

        // Example episode. This is EPsiode with (f=5) agents, from top to to-left
        oxysoftConnection.sendEventMarker("EPfttl");

        // Example episode. This is EPsiode with (e=4) agents, from bottom-left to right
        oxysoftConnection.sendEventMarker("EPeblr");



        // Example click. This means a CLick event that is not an NBack match (False), and so the user was not successful (False)
        oxysoftConnection.sendEventMarker("CLFF");

        // Example click. This means a CLick event that was an NBack match (True), and so the user was successful (True)
        oxysoftConnection.sendEventMarker("CLTT");

        oxysoftConnection.sendEventMarker("REST");
        // Your existing code for rest period

        // Clean up resources
        oxysoftConnection.close();
    }
}
