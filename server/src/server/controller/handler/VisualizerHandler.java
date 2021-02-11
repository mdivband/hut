package server.controller.handler;

import server.Simulator;
import tool.GsonUtils;
import tool.HttpServer;

import java.io.IOException;

public class VisualizerHandler extends RestHandler {

    public VisualizerHandler(String handlerName, Simulator simulator) {
        super(handlerName, simulator);
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
