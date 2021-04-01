package server.controller.handler;

import server.Simulator;
import tool.HttpServer.*;

import java.io.IOException;
import java.util.*;

public class AllocationHandler extends RestHandler {

    public AllocationHandler(String handlerName, Simulator simulator) {
        super(handlerName, simulator);
    }

    @Override
    public void handlePost(Request req, Response resp) throws IOException, UnregisteredPathException {
        switch(parseId(req.getPath())) {
            case "auto-allocate":
                handleAutoAllocate(resp);
                break;
            case "confirm":
                handleConfirm(resp);
                break;
            case "allocate":
                handleAllocate(req, resp);
                break;
            case "undo":
                handleUndo(resp);
                break;
            case "redo":
                handleRedo(resp);
                break;
            case "reset":
                handleReset(resp);
                break;
            default:
                throw new UnregisteredPathException("No method for handling POST request on " + req.getPath());
        }
    }

    @Override
    public void handleDelete(Request req, Response resp) throws IOException {
        String id = parseId(req.getPath());
        if (!agentExists(id, resp))
            return;
        simulator.getAllocator().removeFromTempAllocation(id);
        resp.sendOkay();
    }

    private void handleAutoAllocate(Response resp) throws IOException {
        LOGGER.info("Running auto allocation.");
        simulator.getAllocator().runAutoAllocation();
        LOGGER.info("Finished auto allocation.");
        resp.sendOkay();
    }

    private void handleConfirm(Response resp) throws IOException {
        simulator.getAllocator().confirmAllocation(simulator.getState().getTempAllocation());
        resp.sendOkay();
    }

    private void handleAllocate(Request req, Response resp) throws IOException {
        Map<String, String> params = req.getParams();
        List<String> expectedKeys = Arrays.asList("agentId", "taskId");
        if (!checkParams(params, expectedKeys, resp))
            return;
        String agentId = params.get("agentId");
        if(!agentExists(agentId, resp))
            return;
        String taskId = params.get("taskId");
        if(!taskExists(taskId, resp))
            return;
        simulator.getAllocator().putInTempAllocation(agentId, taskId);
        resp.sendOkay();
    }

    private void handleUndo(Response resp) throws IOException {
        simulator.getAllocator().undoAllocationChange();
        resp.sendOkay();
    }

    private void handleRedo(Response resp) throws IOException {
        simulator.getAllocator().redoAllocationChange();
        resp.sendOkay();
    }

    private void handleReset(Response resp) throws IOException {
        simulator.getAllocator().resetAllocation();
        resp.sendOkay();
    }
}
