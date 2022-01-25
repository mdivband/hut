package server.controller;

import server.Simulator;
import server.model.Agent;
import server.model.Coordinate;
import server.model.target.AdjustableTarget;
import server.model.target.Target;
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
            case Task.TASK_DEEP_SCAN:
                task = new DeepScanTask(id, new Coordinate(lat, lng));
                simulator.getTargetController().adjustForTask(AdjustableTarget.ADJ_DEEP_SCAN, lat, lng);
                break;
            case Task.TASK_SHALLOW_SCAN:
                task = new ShallowScanTask(id, new Coordinate(lat, lng));
                simulator.getTargetController().adjustForTask(AdjustableTarget.ADJ_SHALLOW_SCAN, lat, lng);
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
            LOGGER.info("Moved task " + id + " to " + lat + ", " + lng);
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
        }

        simulator.getState().remove(task);
        LOGGER.info(String.format("%s; DELTSK; Removed task (id, lat, lng); %s; %s; %s", Simulator.instance.getState().getTime(), id, task.getCoordinate().getLatitude(), task.getCoordinate().getLongitude()));

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

    /**
     * Removes tasks matching this coordinate. It may be that we need to introduce an epsilon value later
     * @param coordinate
     */
    public void removeAllTasksAt(Coordinate coordinate) {
        simulator.getState().getTasks().removeIf(tsk -> tsk.getCoordinate().equals(coordinate));
    }

    public List<Task> getAllTasksAt(Coordinate coordinate) {
       List<Task> foundTasks = new ArrayList<>(2);

       for (Task t : simulator.getState().getTasks()) {
           if (t.getCoordinate().equals(coordinate)) {
               foundTasks.add(t);
           }
       }

       return foundTasks;
    }

    public synchronized void resetTaskNumbers() {
        this.uniqueTaskNumber = 1;
    }
}
