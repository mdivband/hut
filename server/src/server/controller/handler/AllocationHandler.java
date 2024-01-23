package server.controller.handler;

import server.Simulator;
import server.model.agents.Agent;
import server.model.task.Task;
import tool.HttpServer.Request;
import tool.HttpServer.Response;

import java.io.IOException;
import java.util.*;
import java.util.logging.FileHandler;

/**
 * Rest handler for the allocation
 */
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
            case "groupAllocate":
                handleGroupAllocate(req, resp);
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
        LOGGER.info(String.format("%s; ALRUN; Running auto allocation;", Simulator.instance.getState().getTime()));
        simulator.getAllocator().runAutoAllocation();
        LOGGER.info(String.format("%s; FNRUN; Finished auto allocation;", Simulator.instance.getState().getTime()));
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
        simulator.getState().getAgent(agentId).setAgentTeam(new ArrayList<>(Collections.singleton(taskId)));
        resp.sendOkay();
    }

    private void handleGroupAllocate(Request req, Response resp) throws IOException {
        Map<String, String> params = req.getParams();
        List<String> expectedKeys = Arrays.asList("agentIds", "taskIds");

        if (!checkParams(params, expectedKeys, resp))
            return;

        List<Agent> agentIdsAsArray = new ArrayList<>();
        Arrays.stream(params.get("agentIds").split(",")).forEach(a -> agentIdsAsArray.add(Simulator.instance.getState().getAgent(a)));

        List<Task> taskIdsAsArray = new ArrayList<>();
        Arrays.stream(params.get("taskIds").split(",")).forEach(t -> taskIdsAsArray.add(Simulator.instance.getState().getTask(t)));

        simulator.getAllocator().dynamicRandomAssignSubgroup(agentIdsAsArray, taskIdsAsArray);
        simulator.getAllocator().confirmAllocation(simulator.getState().getTempAllocation());


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
