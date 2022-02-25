package server.controller;

import server.Simulator;
import server.model.*;
import server.model.task.PatrolTask;
import server.model.task.Task;

import java.util.List;
import java.util.Map;
import java.util.logging.FileHandler;

public class AgentController extends AbstractController {

    public static int nextAgentAltitude = 5;
    private static int uniqueAgentNumber = 1;

    private Sensor sensor;

    public AgentController(Simulator simulator, Sensor sensor) {
        super(simulator, AgentController.class.getName());
        this.sensor = sensor;
    }

    private String generateUID() {
        return "UAV-" + uniqueAgentNumber++;
    }

    public synchronized Agent addRealAgent(double lat, double lng, double heading) {
        Agent agent = new AgentReal(generateUID(), new Coordinate(lat, lng), simulator.getQueueManager().createMessagePublisher());
        agent.setHeading(heading);
        simulator.getState().add(agent);
        return agent;
    }

    public synchronized Agent addVirtualAgent(double lat, double lng, double heading) {
        Agent agent = new AgentVirtual(generateUID(), new Coordinate(lat, lng), sensor);
        agent.setHeading(heading);
        simulator.getState().add(agent);
        return agent;
    }

    public synchronized Agent addHubAgent(double lat, double lng) {
        // We assume just one HUB, so this is a unique ID
        Agent agent = new AgentHub("HUB", new Coordinate(lat, lng), sensor);
        simulator.getState().add(agent);
        return agent;
    }

    public synchronized boolean deleteAgent(String id) {
        Agent agent = simulator.getState().getAgent(id);
        if(agent == null) {
            LOGGER.warning("Attempted to remove missing agent " + id);
            return false;
        }
        //Only simulated agents can be removed
        if(!agent.isSimulated()) {
            LOGGER.warning("Attempted to remove real agent " + id);
            return false;
        }

        if (agent.getTask() != null) {
            agent.getTask().removeAgent(id);
            agent.setAllocatedTaskId(null);
        }
        agent.getRoute().clear();

        String taskId;
        if((taskId = simulator.getState().getAllocation().get(id)) != null) {
            simulator.getState().getAllocation().remove(id);
            Task task;
            if((task = simulator.getState().getTask(taskId)) != null)
                task.removeAgent(id);
        }

        for (String a : simulator.getState().getAllocation().keySet()) {
            if (id.equals(a)) {
                simulator.getState().getAllocation().remove(a);
                Task task = simulator.getState().getTask(simulator.getState().getAllocation().get(a));
                task.removeAgent(a);
            }
        }

        Map<String, String> oldResult = simulator.getAllocator().getOldResult();
        if (oldResult != null)
            oldResult.remove(id);

        simulator.getState().remove(agent);
        LOGGER.info(String.format("%s; DELAG; Deleted Agent (id); %s ", Simulator.instance.getState().getTime(), id));
        return true;
    }

    public synchronized void stopAllAgents() {
        for(Agent agent : simulator.getState().getAgents())
            agent.stop();
    }

    public synchronized void updateAgentsTempRoutes() {
        for(Agent agent : simulator.getState().getAgents())
            agent.setTempRoute(agent.getRoute());
    }

    public synchronized void updateAgentSpeed(String agentId, double speed) {
       simulator.getState().getAgent(agentId).setSpeed(speed);
    }

    public synchronized void updateAgentAltitude(String agentId, double altitude) {
        simulator.getState().getAgent(agentId).setAltitude(altitude);
    }

    public synchronized void addToAgentTempRoute(String agentId, int index, Coordinate coordinate) {
        Agent agent = simulator.getState().getAgent(agentId);
        List<Coordinate> tempRoute = agent.getTempRoute();
        tempRoute.add(index, coordinate);

        //If region or patrol task, update the end point of the agent's route to be the closest point on the patrol.
        String taskId = simulator.getState().getTempAllocation().get(agentId);
        Task tempAllocatedTask = simulator.getState().getTask(taskId);
        if(tempAllocatedTask.getType() == Task.TASK_PATROL || tempAllocatedTask.getType() == Task.TASK_REGION)
            tempRoute.set(tempRoute.size() - 1, ((PatrolTask) tempAllocatedTask).getNearestPointAbsolute(agent));
    }

    public synchronized void editAgentTempRoute(String agentId, int index, Coordinate coordinate) {
        Agent agent = simulator.getState().getAgent(agentId);
        List<Coordinate> tempRoute = agent.getTempRoute();
        tempRoute.set(index, coordinate);

        //If region or patrol task, update the end point of the agent's route to be the closest point on the patrol.
        String taskId = simulator.getState().getTempAllocation().get(agentId);
        Task tempAllocatedTask = simulator.getState().getTask(taskId);
        if(tempAllocatedTask.getType() == Task.TASK_PATROL || tempAllocatedTask.getType() == Task.TASK_REGION)
            tempRoute.set(tempRoute.size() - 1, ((PatrolTask) tempAllocatedTask).getNearestPointAbsolute(agent));
    }

    public synchronized void deleteFromAgentTempRoute(String agentId, int index) {
        List<Coordinate> tempRoute = simulator.getState().getAgent(agentId).getTempRoute();
        if(index >= 0 && index < tempRoute.size() - 1)
            tempRoute.remove(index);
    }

    public synchronized boolean setAgentTimedOut(String agentId, boolean timedOut) {
        Agent agent = simulator.getState().getAgent(agentId);
        if(!agent.isSimulated())
            return false;
        agent.setTimedOut(timedOut);
        return true;
    }

    public synchronized void resetAgentNumbers() {
        this.uniqueAgentNumber = 1;
    }

}
