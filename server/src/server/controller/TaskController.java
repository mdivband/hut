package server.controller;

import server.Simulator;
import server.model.Agent;
import server.model.AgentHub;
import server.model.Coordinate;
import server.model.target.AdjustableTarget;
import server.model.target.Target;
import server.model.task.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.FileHandler;

public class TaskController extends AbstractController {

    private static int uniqueTaskNumber = 1;

    public TaskController(Simulator simulator) {
        super(simulator, TaskController.class.getName());
    }

    private String generateUID() {
        return "Task-" + uniqueTaskNumber++;
    }

    public synchronized Task createTask(int taskType, double lat, double lng) {
        synchronized (simulator.getState().getTasks()) {
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
                    synchronized (simulator.getState().getTasks()) {
                        task = new DeepScanTask(id, new Coordinate(lat, lng));
                    }
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

            if (task instanceof DeepScanTask) {
                task.setPriority(100);
                simulator.getAllocator().dynamicReassign(task);
                String trgId = simulator.getTargetController().getTargetAt(new Coordinate(lat, lng)).getId();
                LOGGER.info(String.format("%s; DPSCN; Creating a deep scan task of id and for target (id, targetId); %s; %s", Simulator.instance.getState().getTime(), id, trgId));

            }
            for (Agent a : simulator.getState().getAgents()) {
                if (!(a instanceof AgentHub) && a.getTask() != null) {
                    a.resume();
                }
            }
            return task;
        }
    }

    public synchronized Task createPatrolTask(List<Coordinate> path, Boolean ignored) {
        String id = generateUID();
        Task task = PatrolTask.createTask(id, path, ignored);
        simulator.getState().add(task);
        StringBuilder sb = new StringBuilder();
        for (Coordinate c : path) {
            sb.append(c.getLatitude()).append(";").append(c.getLongitude()).append(";");
        }
        LOGGER.info(String.format("%s; CRPT; Created new patrol task with points (id, lat1, lng1, lat2, lng2, ...); %s; %s", Simulator.instance.getState().getTime(), id, sb));
        //LOGGER.info(String.format("%s; CRPT; Created new patrol task with centre (id, lat, lng); %s; %s; %s", Simulator.instance.getState().getTime(), id, task.getCoordinate().getLatitude(), task.getCoordinate().getLongitude()));
        return task;
    }

    public synchronized boolean updatePatrolPath(String id, List<Coordinate> path) {
        Task task = simulator.getState().getTask(id);
        if(task.getType() == Task.TASK_PATROL) {
            ((PatrolTask) task).updatePoints(path);
            StringBuilder sb = new StringBuilder();
            for (Coordinate c : path) {
                sb.append(c.getLatitude()).append(";").append(c.getLongitude()).append(";");
            }
            LOGGER.info(String.format("%s; MVPT; Moved patrol task to points (id, lat1, lng1, lat2, lng2, ...); %s; %s", Simulator.instance.getState().getTime(), id, sb));
            return true;
        }
        return false;
    }

    public synchronized Task createRegionTask(Coordinate nw, Coordinate ne, Coordinate se, Coordinate sw, Boolean ignored) {
        String id = generateUID();
        Task task = RegionTask.createTask(id, nw, ne, se, sw, ignored);
        simulator.getState().add(task);
        StringBuilder sb = new StringBuilder();
        sb.append(nw.getLatitude()).append(";").append(nw.getLongitude()).append(";");
        sb.append(ne.getLatitude()).append(";").append(ne.getLongitude()).append(";");
        sb.append(se.getLatitude()).append(";").append(se.getLongitude()).append(";");
        sb.append(sw.getLatitude()).append(";").append(sw.getLongitude()).append(";");
        LOGGER.info(String.format("%s; CRRG; Created new region task with corners (id, nwlat, nwlng, nelat, nelng, selat, selng, swlat, swlng,); %s; %s", Simulator.instance.getState().getTime(), id, sb));


        //LOGGER.info(String.format("%s; CRRG; Created new region task with centre (id, lat, lng); %s; %s; %s", Simulator.instance.getState().getTime(), id, task.getCoordinate().getLatitude(), task.getCoordinate().getLongitude()));
        return task;
    }

    public synchronized boolean updateRegionCorners(String id, List<Coordinate> corners) {
        Task task = simulator.getState().getTask(id);
        if(task.getType() == Task.TASK_REGION) {
            ((RegionTask) task).updateCorners(corners.get(0), corners.get(1), corners.get(2), corners.get(3));
            StringBuilder sb = new StringBuilder();
            sb.append(corners.get(0).getLatitude()).append(";").append(corners.get(0).getLongitude()).append(";");
            sb.append(corners.get(1).getLatitude()).append(";").append(corners.get(1).getLongitude()).append(";");
            sb.append(corners.get(2).getLatitude()).append(";").append(corners.get(2).getLongitude()).append(";");
            sb.append(corners.get(3).getLatitude()).append(";").append(corners.get(3).getLongitude()).append(";");
            LOGGER.info(String.format("%s; MVRG; Moved region task to corners (id, nwlat, nwlng, nelat, nelng, selat, selng, swlat, swlng,); %s; %s", Simulator.instance.getState().getTime(), id, sb));
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

    public synchronized void updateTaskGroup(String id, int group) {
        Task task = simulator.getState().getTask(id);
        task.setGroup(group);
        LOGGER.info(String.format("%s; UPDTSK; Updated task group size (id, lat, lng, agents); %s; %s; %s, %s", Simulator.instance.getState().getTime(), id, task.getCoordinate().getLatitude(), task.getCoordinate().getLongitude(), group));
    }

    public synchronized void updateTaskPriority(String taskId, double priority) {
        simulator.getState().getTask(taskId).setPriority(priority);
    }

    public synchronized void updateRegionRotation(String taskId) {
        Task task = simulator.getState().getTask(taskId);
        if (task.getType() == Task.TASK_REGION) {
            ((RegionTask) task).rotateRoute();
        }
        LOGGER.info(String.format("%s; RTRG; Region task path rotated (id); %s; ", Simulator.instance.getState().getTime(), taskId));
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
