package server.controller;

import server.model.*;
import server.Simulator;
import server.model.task.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TaskController extends AbstractController {

    private static int uniqueTaskNumber = 1;

    public TaskController(Simulator simulator) {
        super(simulator, TaskController.class.getName());
    }

    private String generateUID() {
        return "Task-" + uniqueTaskNumber++;
    }

    public synchronized Task createTask(int taskType, double lat, double lng) {
        String id = generateUID();
        Task task;
        switch (taskType) {
            case Task.TASK_WAYPOINT:
                task = new WaypointTask(id, new Coordinate(lat, lng));
                break;
            case Task.TASK_MONITOR:
                task = new MonitorTask(id, new Coordinate(lat, lng));
                break;
            default:
                throw new IllegalArgumentException("Unable to create task of type " + taskType);
        }
        simulator.getState().add(task);
        LOGGER.info("Created new task " + id + " at " + lat + ", " + lng);
        return task;
    }

    public synchronized Task createPatrolTask(List<Coordinate> path) {
        String id = generateUID();
        Task task = PatrolTask.createTask(id, path);
        simulator.getState().add(task);
        LOGGER.info("Created new patrol task " + id);
        return task;
    }

    public synchronized boolean updatePatrolPath(String id, List<Coordinate> path) {
        Task task = simulator.getState().getTask(id);
        if(task.getType() == Task.TASK_PATROL) {
            ((PatrolTask) task).updatePoints(path);
            return true;
        }
        return false;
    }

    public synchronized Task createRegionTask(Coordinate nw, Coordinate ne, Coordinate se, Coordinate sw) {
        String id = generateUID();
        Task task = RegionTask.createTask(id, nw, ne, se, sw);
        simulator.getState().add(task);
        LOGGER.info("Created new region task " + id);
        return task;
    }

    public synchronized boolean updateRegionCorners(String id, List<Coordinate> corners) {
        Task task = simulator.getState().getTask(id);
        if(task.getType() == Task.TASK_REGION) {
            ((RegionTask) task).updateCorners(corners.get(0), corners.get(1), corners.get(2), corners.get(3));
            return true;
        }
        return false;
    }

    public synchronized Task updateTaskPosition(String id, double lat, double lng) {
        Task task = simulator.getState().getTask(id);
        if (!task.getCoordinate().equals(new Coordinate(lat, lng))) {
            task.getCoordinate().set(lat, lng);
            LOGGER.info("Moved task " + id + " to " + lat + ", " + lng);
        }
        return task;
    }

    public synchronized boolean deleteTask(String id, boolean completed) {
        Task task = simulator.getState().getTask(id);
        if (task == null) {
            LOGGER.warning("Attempted to remove missing task " + id);
            return false;
        }

        removeTaskAllocations(id, simulator.getState().getAllocation());
        removeTaskAllocations(id, simulator.getState().getTempAllocation());

        for(Agent agent : task.getAgents()) {
            agent.setTempRoute(new ArrayList<>());
            agent.setRoute(new ArrayList<>());
            agent.setWorking(false);
            agent.setAllocatedTaskId("");
        }

        simulator.getState().remove(task);
        LOGGER.info("Removed task " + id);

        if(completed)
            simulator.getState().addCompletedTask(task);
        return true;
    }

    public synchronized void updateTaskGroup(String taskId, int group) {
        simulator.getState().getTask(taskId).setGroup(group);
    }

    public synchronized void updateTaskPriority(String taskId, double priority) {
        simulator.getState().getTask(taskId).setPriority(priority);
    }

    private void removeTaskAllocations(String taskId, Map<String, String> allocation) {
        List<String> keysToRemove = new ArrayList<>();
        for(Map.Entry<String, String> e : allocation.entrySet()) {
            if(e.getValue().equals(taskId))
                keysToRemove.add(e.getKey());
        }
        for(String key : keysToRemove)
            allocation.remove(key);
    }
}
