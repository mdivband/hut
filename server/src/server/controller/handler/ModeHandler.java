package server.controller.handler;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import server.Simulator;
import tool.HttpServer.Request;
import tool.HttpServer.Response;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ModeHandler extends RestHandler {

    public ModeHandler(String handlerName, Simulator simulator) {
        super(handlerName, simulator);
    }

    @Override
    public void handlePost(Request req, Response resp) throws IOException, UnregisteredPathException {
        String rPath = parseRemainingPath(req.getPath());
        switch (rPath) {
            case "/sandbox":
                handleSandbox(resp);
                break;
            case "/scenario":
                handleScenario(req, resp);
                break;
            case "/scenario/start":
                handleScenarioStart(resp);
                break;
            default:
                throw new UnregisteredPathException("No method for handling POST request on " + req.getPath());
        }
    }

    @Override
    public void handleGet(Request req, Response resp) throws IOException, UnregisteredPathException {
        String rPath = parseRemainingPath(req.getPath());
        switch (rPath) {
            case "/scenario-list":
                handleScenarioList(resp);
                break;
            case "/in-progress":
                handleInProgress(resp);
                break;
            default:
                throw new UnregisteredPathException("No method for handling GET request on " + req.getPath());
        }
    }

    private void handleSandbox(Response resp) throws IOException {
        this.simulator.startSandboxMode();
        resp.sendOkay();
    }

    private void handleScenario(Request req, Response resp) throws IOException {
        Map<String, String> params = req.getParams();
        List<String> expectedKeys = Collections.singletonList("file-name");
        if (!checkParams(params, expectedKeys, resp))
            return;
        String scenarioFileName = params.get("file-name");
        if(this.simulator.loadScenarioMode(scenarioFileName))
            resp.sendOkay();
        else
            resp.sendError(400, "Unable to start scenario from file " + scenarioFileName);
    }

    private void handleScenarioStart(Response resp) throws IOException {
        this.simulator.startSimulation();
        resp.sendOkay();
    }

    private void handleScenarioList(Response resp) throws IOException {
        Map<String, String> scenarios = this.simulator.getScenarioFileListWithGameIds();

        JsonArray scenarioList = new JsonArray();
        for(Map.Entry<String, String> e : scenarios.entrySet()) {
            JsonObject innerObject = new JsonObject();
            innerObject.addProperty("fileName", e.getKey());
            innerObject.addProperty("gameId", e.getValue());
            scenarioList.add(innerObject);
        }

        String scenarioListJson = scenarioList.toString();
        resp.getHeaders().add("Content-type", "application/json; charset=utf-8");
        resp.send(200, scenarioListJson);
    }

    private void handleInProgress(Response resp) throws IOException {
        resp.getHeaders().add("Content-type", "application/json; charset=utf-8");
        resp.send(200, Boolean.toString(this.simulator.getState().isInProgress()));
    }
}
