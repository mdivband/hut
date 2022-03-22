package server.controller.handler;

import server.Simulator;
import server.model.Coordinate;
import server.model.task.Task;
import tool.HttpServer.Request;
import tool.HttpServer.Response;

import java.io.IOException;
import java.util.*;

public class TaskHandler extends RestHandler {

    public TaskHandler(String handlerName, Simulator simulator) {
        super(handlerName, simulator);
    }

    @Override
    public void handlePost(Request req, Response resp) throws IOException {
        String rPath = parseRemainingPath(req.getPath());
        // /tasks
        if(rPath == null)
            handleAdd(req, resp);
        // /tasks/patrol/update/<id>
        else if(rPath.startsWith("/patrol/update"))
            handleUpdatePatrol(req, resp, rPath.replace("/patrol/update/", ""));
        // /tasks/patrol
        else if(rPath.startsWith("/patrol"))
            handleAddPatrol(req, resp);
        // /tasks/region/update/<id>
        else if(rPath.startsWith("/region/update"))
            handleUpdateRegion(req, resp, rPath.replace("/region/update/", ""));
        // /tasks/region
        else if(rPath.startsWith("/region"))
            handleAddRegion(req, resp);
        // /tasks/<id>
        else
            handleUpdate(req, resp, rPath.substring(1));
    }

    @Override
    public void handleDelete(Request req, Response resp) throws IOException {
        String id = parseId(req.getPath());
        if (!taskExists(id, resp))
            return;
        if (simulator.getTaskController().deleteTask(id, false))
            resp.sendOkay();
        else
            resp.sendError(400, "Unable to delete task " + id);
    }

    private void handleAdd(Request req, Response resp) throws IOException {
        Map<String, String> params = req.getParams();
        List<String> expectedKeys = Arrays.asList("type", "lat", "lng");
        if (!checkParams(params, expectedKeys, resp))
            return;
        int type = Integer.parseInt(params.get("type"));
        double lat = Double.parseDouble(params.get("lat"));
        double lng = Double.parseDouble(params.get("lng"));
        try {
            Task task = simulator.getTaskController().createTask(type, lat, lng);
            resp.send(201, "Created new task " + task.getId());
        }
        catch (IllegalArgumentException e) {
            LOGGER.warning("Unable to add task - " + e.getMessage());
            resp.send(400, "Unable to add task - " + e.getMessage());
        }
    }

    private void handleAddPatrol(Request req, Response resp) throws IOException {
        Map<String, String> params = req.getParams();
        List<String> expectedKeys = Collections.singletonList("path");
        if (!checkParams(params, expectedKeys, resp))
            return;

        List<Coordinate> path = new ArrayList<>();
        String[] pathSplit = params.get("path").split(",");
        for(int i = 0; i < pathSplit.length; i += 2) {
            Double lat = Double.parseDouble(pathSplit[i]);
            Double lng = Double.parseDouble(pathSplit[i + 1]);
            path.add(new Coordinate(lat, lng));
        }
        int rnd = new Random().nextInt(100);
        Boolean ignored = (rnd <= 30);
        Task task = simulator.getTaskController().createPatrolTask(path, ignored);
        resp.getHeaders().add("Content-type", "text");
        resp.send(201, task.getId());
    }

    private void handleUpdatePatrol(Request req, Response resp, String id) throws IOException {
        if (!taskExists(id, resp))
            return;
        Map<String, String> params = req.getParams();
        List<String> expectedKeys = Collections.singletonList("path");
        if (!checkParams(params, expectedKeys, resp))
            return;
        List<Coordinate> path = new ArrayList<>();
        String[] pathSplit = params.get("path").split(",");
        for(int i = 0; i < pathSplit.length; i += 2) {
            Double lat = Double.parseDouble(pathSplit[i]);
            Double lng = Double.parseDouble(pathSplit[i + 1]);
            path.add(new Coordinate(lat, lng));
        }
        if(simulator.getTaskController().updatePatrolPath(id, path))
            resp.sendOkay();
        else {
            LOGGER.warning("Unable to update task path.");
            resp.send(400, "Unable to update task path.");
        }
    }

    private void handleAddRegion(Request req, Response resp) throws IOException {
        Map<String, String> params = req.getParams();
        List<String> expectedKeys = Collections.singletonList("corners");
        if (!checkParams(params, expectedKeys, resp))
            return;

        List<Coordinate> corners = new ArrayList<>();
        String[] pathSplit = params.get("corners").split(",");
        for(int i = 0; i < pathSplit.length; i += 2) {
            Double lat = Double.parseDouble(pathSplit[i]);
            Double lng = Double.parseDouble(pathSplit[i + 1]);
            corners.add(new Coordinate(lat, lng));
        }
        int rnd = new Random().nextInt(100);
        Boolean ignored = (rnd <= 30);
        Task task = simulator.getTaskController().createRegionTask(corners.get(0), corners.get(1), corners.get(2), corners.get(3), ignored);
        resp.getHeaders().add("Content-type", "text");
        resp.send(201, task.getId());
    }

    private void handleUpdateRegion(Request req, Response resp, String id) throws IOException {
        if (!taskExists(id, resp))
            return;
        Map<String, String> params = req.getParams();
        List<String> expectedKeys = Collections.singletonList("corners");
        if (!checkParams(params, expectedKeys, resp))
            return;
        List<Coordinate> corners = new ArrayList<>();
        String[] pathSplit = params.get("corners").split(",");
        for(int i = 0; i < pathSplit.length; i += 2) {
            Double lat = Double.parseDouble(pathSplit[i]);
            Double lng = Double.parseDouble(pathSplit[i + 1]);
            corners.add(new Coordinate(lat, lng));
        }
        if(simulator.getTaskController().updateRegionCorners(id, corners))
            resp.sendOkay();
        else {
            LOGGER.warning("Unable to update task path.");
            resp.send(400, "Unable to update task path.");
        }
    }

    private void handleUpdate(Request req, Response resp, String id) throws IOException {
        if (!taskExists(id, resp))
            return;
        Map<String, String> params = req.getParams();
        if (params.containsKey("lat") && params.containsKey("lng")) {
            double lat = Double.parseDouble(params.get("lat"));
            double lng = Double.parseDouble(params.get("lng"));
            simulator.getTaskController().updateTaskPosition(id, lat, lng);
        }
        if (params.containsKey("group"))
            simulator.getTaskController().updateTaskGroup(id, Integer.parseInt(params.get("group")));
        if (params.containsKey("priority"))
            simulator.getTaskController().updateTaskPriority(id, Double.parseDouble(params.get("priority")));
        resp.sendOkay();
    }

}
