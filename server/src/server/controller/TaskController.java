package server.controller;

import server.Simulator;
import server.model.agents.Agent;
import server.model.Coordinate;
import server.model.task.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

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
            case Task.TASK_VISIT:
                task = new VisitTask(id, new Coordinate(lat, lng));
                break;
            default:
                throw new IllegalArgumentException("Unable to create task of type " + taskType);
        }
        simulator.getState().add(task);
        LOGGER.info(String.format("%s; CRWP; Created new task (id, lat, lng); %s; %s; %s", Simulator.instance.getState().getTime(), id, lat, lng));
        return task;
    }

    public synchronized Task createPatrolTask(List<Coordinate> path) {
        String id = generateUID();
        Task task = PatrolTask.createTask(id, path);
        simulator.getState().add(task);
        LOGGER.info(String.format("%s; CRPT; Created new patrol task with centre (id, lat, lng); %s; %s; %s", Simulator.instance.getState().getTime(), id, task.getCoordinate().getLatitude(), task.getCoordinate().getLongitude()));
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
        LOGGER.info(String.format("%s; CRRG; Created new region task with centre (id, lat, lng); %s; %s; %s", Simulator.instance.getState().getTime(), id, task.getCoordinate().getLatitude(), task.getCoordinate().getLongitude()));
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
            LOGGER.info(String.format("%s; MVTSK; Moved task to (id, lat, lng); %s; %s; %s", Simulator.instance.getState().getTime(), id, task.getCoordinate().getLatitude(), task.getCoordinate().getLongitude()));
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
            agent.stop();
        }

        simulator.getState().remove(task);
        LOGGER.info(String.format("%s; DELTSK; Removed task (id, lat, lng); %s; %s; %s", Simulator.instance.getState().getTime(), id, task.getCoordinate().getLatitude(), task.getCoordinate().getLongitude()));

        if(completed) {
            simulator.getState().addCompletedTask(task);
        }
        return true;
    }

    /**
     * Deletes the task with the given coordinates
     * @param coord Coordinate to check
     */
    public void deleteTaskByCoords(Coordinate coord) {
        try {
            findTaskByCoord(coord).setStatus(Task.STATUS_DONE);
        } catch (Exception e){
            // For now we leave this. It's rare, but when deleting an already completed task this throws an error
            // It doesn't matter that we let this through, because it was already gone
        }
    }

    /**
     * Searcges the nearby area for any task and returns the first match
     * @param position Coordinate around which to check
     * @param eps Epsilon value to search around
     * @return The first nearby task, or null if none found
     */
    public Coordinate checkIfNearbyTaskComplete(Coordinate position, double eps){
        for (Task t :simulator.getState().getTasks()) {
            Coordinate thisPos = t.getCoordinate();
            if(Math.abs(position.getLatitude() - thisPos.getLatitude()) < eps &&
               Math.abs(position.getLongitude() - thisPos.getLongitude()) < eps) {
                // The agent is close enough to this task, so we report it
                return thisPos;
            }
        }
        return null;
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

    public synchronized void resetTaskNumbers() {
        this.uniqueTaskNumber = 1;
    }

    public Task findTaskByCoord(Coordinate coordinate) {
        return simulator.getState().getTaskByCoordinate(coordinate);
    }

    public boolean checkForFreeTasks() {
        return simulator.getState().getTasks().stream().anyMatch(a -> a.getAgents().isEmpty());
    }

}
