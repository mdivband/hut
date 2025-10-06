package tool;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

/*
 * Static class for utility functions for working with DDS Controller class
 */
public class DDSUtils {

    // Required fields to check in the raw DDS message
    private static final List<String> REQUIRED_FIELDS = Arrays.asList(
        "timestamp", "message_id", "source", "agents", "agent_id", 
        "coordinate", "lat", "lng", "heading", "speed", "waypoint"
    );

    /**
     * Extracts the latest complete message from the buffer to avoid concatenated JSON
     * @param buffer The full output buffer
     * @return The latest complete JSON message, data availability message, or empty string if none found
     */
    public static String extractLatestCompleteMessage(String buffer) {
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
            if (!(ErrorHandler.isHeaderLine(content) && content.contains("{") && 
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
            if (ErrorHandler.isDataAvailabilityMessage(line)) {
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
    
    /**
     * Parses buffer lines in reverse order to find complete JSON messages
     * @param lines Array of buffer lines
     * @param maxMessages Maximum number of messages to find (0 for unlimited)
     * @return List of complete messages found, in chronological order (oldest first)
     */
    private static java.util.List<CompleteMessage> parseCompleteMessages(
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
                    if (i > 0 && ErrorHandler.isHeaderLine(lines[i-1])) {
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

     // Represents a complete message found in the buffer
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
     * Extracts raw DDS message data into a HashMap with all required fields.
     *
     * @param rawDDSMsg the raw DDS message string
     * @return HashMap containing extracted data with required fields as keys, or empty HashMap if validation fails
     */
    public static java.util.HashMap<String, Object> extractDDSData(String rawDDSMsg) {
        java.util.HashMap<String, Object> extractedData = new java.util.HashMap<>();
        
        try {
            // Extract JSON part from the message
            String jsonPart = extractJsonFromMessage(rawDDSMsg);
            if (jsonPart.isEmpty()) {
                return extractedData;
            }

            // Parse JSON using GsonUtils
            Object messageObj = GsonUtils.fromJson(jsonPart);

            // Validate required fields
            if (!validateRequiredFields(rawDDSMsg, messageObj)) {
                return extractedData;
            }

            // Extract timestamp
            Object timestampObj = GsonUtils.getValue(messageObj, "timestamp");
            if (timestampObj instanceof Number) {
                extractedData.put("timestamp", ((Number) timestampObj).longValue());
            }

            // Extract message metadata
            extractedData.put("message_id", GsonUtils.getValue(messageObj, "message_id"));
            extractedData.put("source", GsonUtils.getValue(messageObj, "source"));

            // Extract agents data
            Object agentsObj = GsonUtils.getValue(messageObj, "agents");
            if (agentsObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> agents = (List<Object>) agentsObj;
                
                java.util.List<java.util.HashMap<String, Object>> agentDataList = 
                    new java.util.ArrayList<>();
                
                for (Object agentElement : agents) {
                    if (agentElement instanceof java.util.Map) {
                        @SuppressWarnings("unchecked")
                        java.util.Map<String, Object> agent = (java.util.Map<String, Object>) agentElement;
                        
                        java.util.HashMap<String, Object> agentData = new java.util.HashMap<>();
                        
                        // Extract agent basic info
                        agentData.put("agent_id", GsonUtils.getValue(agent, "agent_id"));
                        agentData.put("heading", ((Number) GsonUtils.getValue(agent, "heading")).doubleValue());
                        agentData.put("speed", ((Number) GsonUtils.getValue(agent, "speed")).doubleValue());
                        agentData.put("altitude", ((Number) GsonUtils.getValue(agent, "altitude")).doubleValue());
                        agentData.put("battery_level", ((Number) GsonUtils.getValue(agent, "battery_level")).intValue());
                        agentData.put("status", ((Number) GsonUtils.getValue(agent, "status")).intValue());
                        
                        // Extract coordinate data
                        Object coordinateObj = GsonUtils.getValue(agent, "coordinate");
                        if (coordinateObj instanceof java.util.Map) {
                            @SuppressWarnings("unchecked")
                            java.util.Map<String, Object> coordinate = (java.util.Map<String, Object>) coordinateObj;
                            agentData.put("lat", ((Number) GsonUtils.getValue(coordinate, "lat")).doubleValue());
                            agentData.put("lng", ((Number) GsonUtils.getValue(coordinate, "lng")).doubleValue());
                        }
                        
                        // Extract waypoint data if available and add as HashMap to agent
                        Object waypointObj = GsonUtils.getValue(agent, "waypoint");
                        if (waypointObj instanceof java.util.Map) {
                            @SuppressWarnings("unchecked")
                            java.util.Map<String, Object> waypoint = (java.util.Map<String, Object>) waypointObj;
                            
                            java.util.HashMap<String, Object> waypointData = new java.util.HashMap<>();
                            waypointData.put("lat", ((Number) GsonUtils.getValue(waypoint, "lat")).doubleValue());
                            waypointData.put("lng", ((Number) GsonUtils.getValue(waypoint, "lng")).doubleValue());
                            waypointData.put("altitude", ((Number) GsonUtils.getValue(waypoint, "altitude")).doubleValue());
                            waypointData.put("heading", ((Number) GsonUtils.getValue(waypoint, "heading")).doubleValue());
                            
                            // Add waypoint HashMap directly to the agent
                            agentData.put("waypoint", waypointData);
                        }
                        
                        agentDataList.add(agentData);
                    }
                }
                
                extractedData.put("agents", agentDataList);
            }


            Object firesObj = GsonUtils.getValue(messageObj, "fires");
            if (firesObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> fires = (List<Object>) firesObj;

                java.util.List<java.util.HashMap<String, Object>> fireDataList = new java.util.ArrayList<>();

                for (Object fireElement : fires) {
                    if (fireElement instanceof java.util.Map) {
                        @SuppressWarnings("unchecked")
                        java.util.Map<String, Object> fire = (java.util.Map<String, Object>) fireElement;

                        java.util.HashMap<String, Object> fireData = new java.util.HashMap<>();

                        // Extract fire info (id, latitude, longitude)
                        fireData.put("id", ((Number) GsonUtils.getValue(fire, "id")).intValue());
                        fireData.put("latitude", ((Number) GsonUtils.getValue(fire, "latitude")).doubleValue());
                        fireData.put("longitude", ((Number) GsonUtils.getValue(fire, "longitude")).doubleValue());

                        if (GsonUtils.hasKey(fire, "image")) {
                            fireData.put("image", (String) GsonUtils.getValue(fire, "image"));
                        } else {
                            fireData.put("image", ""); // Default to empty string if not present
                        }

                        fireDataList.add(fireData);
                    }
                }
                extractedData.put("fires", fireDataList);
            }

            return extractedData;

        } catch (Exception e) {
            System.err.println("Error extracting DDS data: " + e.getMessage());
            return new java.util.HashMap<>();
        }
    }

    /**
     * Builds a formatted string from extracted DDS data HashMap.
     *
     * @param extractedData HashMap containing extracted DDS data
     * @return formatted string representation of the DDS data
     */
    public static String buildDDSMsgString(
        java.util.HashMap<String, Object> extractedData) {
        if (extractedData.isEmpty()) {
            return "";
        }
        
        StringBuilder result = new StringBuilder();
        
        try {
            // Add timestamp
            Object timestampObj = extractedData.get("timestamp");
            if (timestampObj instanceof Number) {
                double timestampInSeconds = ((Number) timestampObj).longValue() / 1000.0;
                String formattedTimestamp = parseTime(timestampInSeconds, true);
                result.append("Timestamp: ").append(formattedTimestamp).append("\n");
            }

            // Add agents data
            Object agentsObj = extractedData.get("agents");
            if (agentsObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<java.util.HashMap<String, Object>> agents = 
                    (List<java.util.HashMap<String, Object>>) agentsObj;
                
                for (java.util.HashMap<String, Object> agent : agents) {
                    String agentId = (String) agent.get("agent_id");
                    double lat = (Double) agent.get("lat");
                    double lng = (Double) agent.get("lng");
                    double heading = (Double) agent.get("heading");
                    double speed = (Double) agent.get("speed");
                    double altitude = (Double) agent.get("altitude");
                    int batteryLevel = (Integer) agent.get("battery_level");
                    int status = (Integer) agent.get("status");
                    
                    String statusStr = (status == 0) ? "ACTIVE" : "INACTIVE";
                    
                    result.append(String.format(
                        "Drone Id:%s, Lat:%.4f, Lng:%.4f, Heading:%.1f, Speed:%.1f, " +
                        "Alt:%.1f, Bat Level:%d, Status:%s",
                        agentId, lat, lng, heading, speed, altitude, batteryLevel, statusStr
                    ));
                    
                    // Add waypoint information if available in the agent HashMap
                    Object waypointObj = agent.get("waypoint");
                    if (waypointObj instanceof java.util.HashMap) {
                        @SuppressWarnings("unchecked")
                        java.util.HashMap<String, Object> waypoint = 
                            (java.util.HashMap<String, Object>) waypointObj;
                        
                        double wpLat = (Double) waypoint.get("lat");
                        double wpLng = (Double) waypoint.get("lng");
                        double wpAlt = (Double) waypoint.get("altitude");
                        double wpHeading = (Double) waypoint.get("heading");
                        
                        result.append(String.format(
                            ", Waypoint:[Lat:%.4f, Lng:%.4f, Alt:%.1f, Heading:%.1f]",
                            wpLat, wpLng, wpAlt, wpHeading
                        ));
                    }
                    
                    result.append("\n");
                }
            }

            return result.toString().trim();

        } catch (Exception e) {
            System.err.println("Error building DDS message string: " + e.getMessage());
            return "";
        }
    }
    
    // Parses a raw DDS message into a structured format.
    public static String parseRawDDSMsg(String rawDDSMsg) {
        java.util.HashMap<String, Object> extractedData = extractDDSData(rawDDSMsg);
        return buildDDSMsgString(extractedData);
    }

    // Extracts JSON part from the raw DDS message
    private static String extractJsonFromMessage(String rawMessage) {
        int jsonStart = rawMessage.indexOf('{');
        if (jsonStart == -1) {
            return "";
        }
        return rawMessage.substring(jsonStart);
    }

    // Validates that the required fields are present in the message
    private static boolean validateRequiredFields(
        String rawMessage, Object messageObj) {
        int missingCount = 0;
        
        for (String field : REQUIRED_FIELDS) {
            if (!rawMessage.contains(field)) {
                missingCount++;
            }
        }
        
        // Also check if agents array exists and has valid structure
        if (!GsonUtils.hasKey(messageObj, "agents")) {
            missingCount++;
        } else {
            Object agentsObj = GsonUtils.getValue(messageObj, "agents");
            if (!(agentsObj instanceof List)) {
                missingCount++;
            } else {
                @SuppressWarnings("unchecked")
                List<Object> agents = (List<Object>) agentsObj;
                if (agents.size() == 0) {
                    missingCount++;
                } else {
                    // Check first agent for required structure
                    Object firstAgentObj = agents.get(0);
                    if (firstAgentObj instanceof java.util.Map) {
                        @SuppressWarnings("unchecked")
                        java.util.Map<String, Object> firstAgent = (
                            java.util.Map<String, Object>) firstAgentObj;
                        if (!GsonUtils.hasKey(firstAgent, "coordinate")) {
                            missingCount++;
                        } else {
                            Object coordObj = GsonUtils.getValue(
                                firstAgent, "coordinate");
                            if (coordObj instanceof java.util.Map) {
                                @SuppressWarnings("unchecked")
                                java.util.Map<String, Object> coord = (
                                    java.util.Map<String, Object>) coordObj;
                                if (!GsonUtils.hasKey(coord, "lat") || 
                                !GsonUtils.hasKey(coord, "lng")) {
                                    missingCount++;
                                }
                            } else {
                                missingCount++;
                            }
                        }
                    } else {
                        missingCount++;
                    }
                }
            }
        }
        
        return missingCount <= 4;
    }

    /**
     * Parses a time duration in seconds into a human-readable format.
     *
     * @param timeInSeconds the time duration in seconds (can include decimal places)
     * @param withDate if true, returns current date in timestamp format
     * @return a human-readable string representation of the time duration or timestamp
     * Higher time levels will only be added if the time duration is significant.
     */
    public static String parseTime(double timeInSeconds, boolean withDate) {
        if (withDate) {
            // Convert seconds to milliseconds for Instant
            Instant instant = Instant.ofEpochMilli((long) (timeInSeconds * 1000));
            return instant.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
        }
        
        long totalSeconds = (long) timeInSeconds;
        long seconds = totalSeconds % 60;
        long minutes = (totalSeconds / 60) % 60;
        long hours = (totalSeconds / 3600) % 24;
        long days = totalSeconds / 86400; // 24 * 60 * 60

        String finalTime = "";
        if (days > 0) {
            finalTime += String.format("%d days, ", days);
        }
        finalTime += String.format("%02d:%02d:%02d", hours, minutes, seconds);

        return finalTime;
    }

    // Overloaded method for backward compatibility.
    public static String parseTime(double timeInSeconds) {
        return parseTime(timeInSeconds, false);
    }

    // Returns a string representation of the current time using parseTime
    public static String getCurrentTime() {
        long currentTimeMillis = System.currentTimeMillis();
        double currentTimeSeconds = currentTimeMillis / 1000.0;
        return parseTime(currentTimeSeconds);
    }
}