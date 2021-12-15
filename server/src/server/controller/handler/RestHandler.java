package server.controller.handler;

import server.Simulator;
import tool.HttpServer.Request;
import tool.HttpServer.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public abstract class RestHandler {

    private final String handlerName;
    final Simulator simulator;
    final Logger LOGGER;

    RestHandler(String handlerName, Simulator simulator) {
        this.handlerName = handlerName;
        this.simulator = simulator;
        this.LOGGER = Logger.getLogger(handlerName + "Handler");
    }

    public void handle(Request req, Response resp) throws IOException, UnregisteredPathException {
        switch (req.getMethod()) {
            case "GET":
                handleGet(req, resp);
                break;
            case "POST":
                handlePost(req, resp);
                break;
            case "PUT":
                handlePut(req, resp);
                break;
            case "DELETE":
                handleDelete(req, resp);
                break;
            default:
                throw new RuntimeException("Unknown HTTP request method " + req.getMethod());
        }
    }

    public void handleGet(Request req, Response resp) throws IOException, UnregisteredPathException {}

    public void handlePost(Request req, Response resp) throws IOException, UnregisteredPathException {}

    @SuppressWarnings({"WeakerAccess", "unused", "RedundantThrows"})
    public void handlePut(Request req, Response resp) throws IOException {}

    public void handleDelete(Request req, Response resp) throws IOException {}

    /**
     * Get the id contained in the path.
     * Assumes the path is of the form /objectList/id (eg /tasks/task-1)
     * Uses the handlerName to get just the id.
     *  In the example above, the handlerName would be /tasks.
     * @param path - Path containing the id.
     * @return Id if one exists, null otherwise.
     */
    String parseId(String path) {
        String remainingPath = path.replace(handlerName, "");
        if(remainingPath.equals(""))
            return null;
        //Remove leading /
        return remainingPath.substring(1);
    }

    String parseRemainingPath(String path) {
        String remainingPath = path.replace(handlerName, "");
        if(remainingPath.equals(""))
            return null;
        return remainingPath;
    }

    String parseRemainingPath(String path, String handlerName) {
        String remainingPath = path.replace(handlerName, "");
        if(remainingPath.equals(""))
            return null;
        return remainingPath;
    }

    /**
     * Check that the request parameters contain the expected keys.
     * @param params - Parameter map to check.
     * @param expectedKeys - List of expected keys that should all be present in parameter map.
     * @param resp - Response object to send error message to server if required.
     * @return True if all keys found in map, false otherwise.
     * @throws IOException - Error sending response.
     */
    boolean checkParams(Map<String, String> params, List<String> expectedKeys, Response resp) throws IOException {
        List<String> missingParams = new ArrayList<>(expectedKeys);
        missingParams.removeAll(params.keySet());
        if(!missingParams.isEmpty()) {
            resp.sendError(400, "Unable to process request, missing parameters: " + missingParams.toString());
            return false;
        }
        return true;
    }

    boolean agentExists(String agentId, Response resp) throws IOException {
        if (agentId == null || simulator.getState().getAgent(agentId) == null) {
            resp.sendError(404, "No agent found with id " + agentId);
            return false;
        }
        return true;
    }

    boolean targetExists(String targetId, Response resp) throws IOException {
        if (targetId == null || simulator.getState().getTarget(targetId) == null) {
            resp.sendError(404, "No target found with id " + targetId);
            return false;
        }
        return true;
    }

    boolean taskExists(String taskId, Response resp) throws IOException {
        if (taskId == null || simulator.getState().getTask(taskId) == null) {
            resp.sendError(404, "No task found with id " + taskId);
            return false;
        }
        return true;
    }

    public String getHandlerName() {
        return handlerName;
    }

}
