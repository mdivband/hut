package server.controller.handler;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import server.Simulator;
import tool.HttpServer.Request;
import tool.HttpServer.Response;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ChatHandler extends RestHandler {

    public ChatHandler(String handlerName, Simulator simulator) {
        super(handlerName, simulator);
    }

    @Override
    public void handlePost(Request req, Response resp) throws IOException, UnregisteredPathException {
        String rPath = parseRemainingPath(req.getPath());
        switch (rPath) {
            case "/send":
                handleSend(req, resp);
                break;
        }
    }

    private void handleSend(Request req, Response resp) throws IOException {
        Map<String, String> params = req.getParams();
        List<String> expectedKeys = Arrays.asList("userRole", "message");
        if (!checkParams(params, expectedKeys, resp))
            return;
        String userRole = params.get("userRole");
        String message = params.get("message");
        message = userRole.substring(0, 1).toUpperCase() + userRole.substring(1) + ": " + message;
        if (this.simulator.getState().addToChatLog(message))
            resp.sendOkay();
        else
            resp.sendError(400, "Unable to save chat message: " + message);

        LOGGER.info(String.format("%s; NWMSG; %s ", simulator.getState().getTime(), message));

    }
}