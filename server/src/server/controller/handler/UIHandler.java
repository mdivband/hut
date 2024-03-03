package server.controller.handler;

import server.Simulator;
import server.model.task.Task;
import tool.HttpServer.Request;
import tool.HttpServer.Response;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Rest handler for agents
 */
public class UIHandler extends RestHandler {

    public UIHandler(String handlerName, Simulator simulator) {
        super(handlerName, simulator);
    }

    @Override
    public void handlePost(Request req, Response resp) throws IOException {
        String rPath = parseRemainingPath(req.getPath());
        if (rPath.startsWith("/toggle")) {
            handleToggle(req, resp);
        }

    }

    private void handleToggle(Request req, Response resp) throws IOException {
        Map<String, String> params = req.getParams();
        List<String> expectedKeys = Arrays.asList("name", "status");
        if (!checkParams(params, expectedKeys, resp))
            return;
        String name = (params.get("name"));
        boolean status = Boolean.parseBoolean(params.get("status"));

        try {
            simulator.getState().updateUIOption(name, status);
        } catch (IllegalArgumentException e) {
            LOGGER.warning("Unable to update UI - " + e.getMessage());
            resp.send(400, "Unable to update UI - " + e.getMessage());
        }
    }


}
