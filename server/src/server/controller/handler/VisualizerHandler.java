package server.controller.handler;

import server.Simulator;
import tool.GsonUtils;
import tool.HttpServer;

import java.io.IOException;
import java.util.logging.Logger;

public class VisualizerHandler extends RestHandler {

    public VisualizerHandler(String handlerName, Simulator simulator, Logger LOGGER) {
        super(handlerName, simulator, LOGGER);
    }

    @Override
    public void handleGet(HttpServer.Request req, HttpServer.Response resp) throws IOException {
        String jsonString = "";
        //Get agent list as JSON string.
        jsonString += GsonUtils.toJson(simulator.getState().getAgents()) + "\n";
        jsonString += GsonUtils.toJson(simulator.getState().getTasks()) + "\n";

        //Send agent JSON string as response.
        resp.getHeaders().add("Content-type", "application/json; charset=utf-8");
        resp.send(200, jsonString);
    }

}
