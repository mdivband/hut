package server.controller.handler;

import server.Simulator;
import server.model.target.Target;
import tool.HttpServer.Request;
import tool.HttpServer.Response;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class TargetHandler extends RestHandler {

    public TargetHandler(String handlerName, Simulator simulator) {
        super(handlerName, simulator);
    }

    @Override
    public void handlePost(Request req, Response resp) throws IOException {
        String rPath = parseRemainingPath(req.getPath());
        // /targets
        if(rPath == null)
            handleAdd(req, resp);
        // /targets/reveal/<id>
        else if(rPath.startsWith("/reveal"))
            handleReveal(resp, rPath.replace("/reveal/", ""));
        else if(rPath.startsWith("/requestImage"))
            handleRequestImage(resp, rPath.replace("/requestImage/", ""));
    }

    @Override
    public void handleDelete(Request req, Response resp) throws IOException {
        String id = parseId(req.getPath());
        if (!targetExists(id, resp))
            return;
        if (simulator.getTargetController().deleteTarget(id))
            resp.sendOkay();
        else
            resp.sendError(400, "Unable to delete target " + id);
    }

    private void handleAdd(Request req, Response resp) throws IOException {
        Map<String, String> params = req.getParams();
        List<String> expectedKeys = Arrays.asList("lat", "lng", "type");
        if (!checkParams(params, expectedKeys, resp))
            return;
        double lat = Double.parseDouble(params.get("lat"));
        double lng = Double.parseDouble(params.get("lng"));
        int type = Integer.parseInt(params.get("type"));
        Target target = simulator.getTargetController().addTarget(lat, lng, type);
        resp.send(201, "Created new target " + target.getId());
    }

    private void handleReveal(Response resp, String id) throws IOException {
        if (!targetExists(id, resp))
            return;
        simulator.getTargetController().setTargetVisibility(id, true);
        simulator.getImageController().takeImageById(id);
        resp.sendOkay();
    }

    private void handleRequestImage(Response resp, String id) throws IOException {
        if (!targetExists(id, resp))
            return;
        simulator.getTargetController().requestImage(id);
        resp.sendOkay();
    }

}
