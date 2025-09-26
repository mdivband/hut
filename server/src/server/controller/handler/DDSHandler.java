package server.controller.handler;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import server.Simulator;
import tool.HttpServer.Request;
import tool.HttpServer.Response;

import java.io.IOException;

/**
 * DDSHandler is responsible for handling web requests related to the DDS.
 */
public class DDSHandler extends RestHandler {

    public DDSHandler(String handlerName, Simulator simulator) {
        super(handlerName, simulator);
    }

    @Override
    public void handlePost(Request req, Response resp) throws IOException, UnregisteredPathException {
        String rPath = parseRemainingPath(req.getPath());
        switch (rPath) {
            case "/deploy":
                handleDeploy(resp);
                break;
            default:
                throw new UnregisteredPathException("No method for handling POST request on " + req.getPath());
        }
    }

    @Override
    public void handleGet(Request req, Response resp) throws IOException, UnregisteredPathException {
        String rPath = parseRemainingPath(req.getPath());
        switch (rPath) {
            case "/latest":
                handleLatest(resp);
                break;
            case "/hubstatus":
                handleHubStatus(resp);
                break;
            case "/waypoints":
                handleWaypoints(resp);
                break;
            default:
                throw new UnregisteredPathException("No method for handling GET request on " + req.getPath());
        }
    }

    /**
     * Handle deploy DDS message request.
     * Responds with a success message.
     * @param resp The HTTP response object to send the response.
     */
    private void handleDeploy(Response resp) throws IOException {
        // this.simulator.ddsController.deployDDS();
        resp.getHeaders().add("Content-type", "application/json; charset=utf-8");
        resp.send(200, "{\"status\":\"success\"}");
    }

    /**
     * Handle latest DDS message request.
     * Responds with the latest DDS message.
     * @param resp The HTTP response object to send the response.
     */
    private void handleLatest(Response resp) throws IOException {
        String latestMessage = this.simulator.getLatestDDSMessage();
        resp.getHeaders().add("Content-type", "application/json; charset=utf-8");
        resp.send(200, latestMessage);
    }

    // Handle hub status request.
    private void handleHubStatus(Response resp) throws IOException {
        JsonObject status = this.simulator.getHubStatus();
        resp.getHeaders().add("Content-type", "application/json; charset=utf-8");
        resp.send(200, status.toString());
    }

    // Handle waypoints request.
    private void handleWaypoints(Response resp) throws IOException {
        JsonObject waypoints = this.simulator.getAllAgentWaypoints(); 
        resp.getHeaders().add("Content-type", "application/json; charset=utf-8");
        resp.send(200, waypoints.toString());
    }
}