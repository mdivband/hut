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

    public void sendEventMarker(String condition) {
        String marker = "";
        switch (condition) {
            case "A":
                marker = "a";
                break;
            case "B":
                marker = "b";
                break;
            case "C":
                marker = "c";
                break;
            case "rest":
                marker = "R";
                break;
            default:
                System.out.println("Unknown condition: " + condition);
                return;
        }

        if (!marker.isEmpty()) {
            outlet.push_sample(new String[]{marker});
            System.out.println("Event marker '" + marker + "' for condition '" + condition + "' sent.");
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

        oxysoftConnection.sendEventMarker("A");
        // Your existing code for condition A

        oxysoftConnection.sendEventMarker("B");
        // Your existing code for condition B

        oxysoftConnection.sendEventMarker("C");
        // Your existing code for condition C

        oxysoftConnection.sendEventMarker("rest");
        // Your existing code for rest period

        // Clean up resources
        oxysoftConnection.close();
    }
}
