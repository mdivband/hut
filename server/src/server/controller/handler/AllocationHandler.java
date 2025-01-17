package server.controller.handler;

import server.Simulator;
import server.model.agents.Agent;
import server.model.task.Task;
import tool.HttpServer.Request;
import tool.HttpServer.Response;

import java.io.IOException;
import java.util.*;
import java.util.logging.FileHandler;
import java.util.stream.Collectors;

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
        // TODO Temp workaround for demo is to lock in groups as unchangeable
        //simulator.getState().getAgent(agentId).setAgentTeam(new ArrayList<>(Collections.singleton(agentId)));
        simulator.getAllocator().confirmAllocation(simulator.getState().getTempAllocation());
        resp.sendOkay();
    }

    private void handleGroupAllocate(Request req, Response resp) throws IOException {
        Map<String, String> params = req.getParams();
        List<String> expectedKeys = Arrays.asList("agentIds", "taskGroupIndex");

        System.out.println(params);

        // Validate the parameters
        if (!checkParams(params, expectedKeys, resp)) {
            return;
        }

        // Parse agent IDs and task group index
        List<String> agentIds = Arrays.asList(params.get("agentIds").split(","));
        int taskGroupIndex = Integer.parseInt(params.get("taskGroupIndex"));

        // Retrieve matching agents
        List<Agent> matchingAgents = simulator.getState().getAgents().stream()
                .filter(agent -> agentIds.contains(agent.getId()))
                .toList();

        // Retrieve matching tasks
        List<Task> matchingTasks = simulator.getState().getTasks().stream()
                .filter(task -> task.getTaskGroup() == taskGroupIndex)
                .toList();

        // Debugging: Log the matching agents and tasks
        System.out.println("Matching Agents:");
        matchingAgents.forEach(agent -> System.out.println("Agent ID: " + agent.getId()));

        System.out.println("Matching Tasks:");
        matchingTasks.forEach(task -> System.out.println("Task ID: " + task.getId()));

        // Perform the allocation
        simulator.getAllocator().dynamicRandomAssignSubgroup(matchingAgents, matchingTasks);
        simulator.getAllocator().confirmAllocation(simulator.getState().getTempAllocation());
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
