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
import java.util.logging.Logger;

public class PresetHandler extends RestHandler {

    public PresetHandler(String handlerName, Simulator simulator, Logger LOGGER) {
        super(handlerName, simulator, LOGGER);
    }

    @Override
    public void handlePost(Request req, Response resp) throws IOException, UnregisteredPathException {
        String rPath = parseRemainingPath(req.getPath());
        switch (rPath) {
            case "/scenario":
                handlePresetScenario(req, resp);
                break;
            default:
                throw new UnregisteredPathException("No method for handling POST request on " + req.getPath());
        }
    }

    private void handlePresetScenario(Request req, Response resp) throws IOException {
        Map<String, String> params = req.getParams();
        List<String> expectedKeys = Collections.singletonList("file-name");
        if (!checkParams(params, expectedKeys, resp))
            return;
        String scenarioFileName = params.get("file-name");
        if(this.simulator.loadScenarioMode(scenarioFileName)) {
            //this.simulator.startSimulation();
            resp.redirect("/sandbox.html", true);
        } else {
            resp.sendError(400, "Unable to start scenario from file " + scenarioFileName);
        }
    }

    @Override
    public void handleGet(Request req, Response resp) throws IOException, UnregisteredPathException {
        // TODO this is a temporary workaround. Not good practise
        handlePost(req, resp);
    }

}
