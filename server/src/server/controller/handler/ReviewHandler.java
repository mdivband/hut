package server.controller.handler;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import server.Simulator;
import server.model.agents.Agent;
import server.model.Coordinate;
import tool.HttpServer.Request;
import tool.HttpServer.Response;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Handles classification of images in the review scene
 * @author William Hunt
 */
public class ReviewHandler extends RestHandler {

    public ReviewHandler(String handlerName, Simulator simulator) {
        super(handlerName, simulator);
    }

    @Override
    public void handlePost(Request req, Response resp) throws IOException, UnregisteredPathException {
        String rPath = parseRemainingPath(req.getPath());
        switch (rPath) {
            case "/classify":
                handleClassify(req, resp);
                break;

            default:
                throw new UnregisteredPathException("No method for handling POST request on " + req.getPath());
        }
    }

    /**
     * This will handle an incoming ruling and:
     *  Classify that image server side
     *  Remove it from future consideration
     *  Remove the associated target from it
     *  [if req'd] also remove associated tasks at this coordinate
     * @param req
     * @param resp
     * @throws IOException
     */
    private void handleClassify(Request req, Response resp) throws IOException {
        Map<String, String> params = req.getParams();
        List<String> expectedKeys = Arrays.asList("ref", "status");
        if (!checkParams(params, expectedKeys, resp))
            return;

        String ref = params.get("ref");
        boolean status = Boolean.parseBoolean(params.get("status"));

        this.simulator.getImageController().classify(ref, status);

    }


}
