package server.controller;

import server.Simulator;
import server.model.*;
import server.model.agents.*;
import server.model.target.Target;
import server.model.task.PatrolTask;
import server.model.task.Task;

import java.util.*;

/**
 * Controller responsible for the agents, adding and removing them
 */
/* Edited by Will */
public class AgentController extends AbstractController {

    public static int nextAgentAltitude = 5;
    private static int uniqueAgentNumber = 1;
    private int scheduledRemovals = 0;

    private Sensor sensor;

    public AgentController(Simulator simulator, Sensor sensor) {
        super(simulator, AgentController.class.getName());
        this.sensor = sensor;
    }

    private String generateUID() {
        String nextId = "UAV-" + uniqueAgentNumber++;
        while (isClash(nextId)) {
            System.out.println("CLASH DETECTED. INCREMENTING ID");
            nextId = "UAV-" + uniqueAgentNumber++;
        }
        return nextId;
    }

    private boolean isClash(String idToCheck) {
        return simulator.getState().getAgents().stream().anyMatch(a -> a.getId().equals(idToCheck));
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

    public synchronized Agent addProgrammedAgent(double lat, double lng, double heading, Random random, TaskController taskController, String policy) {
        AgentProgrammed agent = new AgentProgrammed("UGV-"+uniqueAgentNumber++, new Coordinate(lat, lng), sensor, random, taskController, policy);
        agent.setCommunicationRange(simulator.getState().getCommunicationRange());
        agent.setHeading(heading);
        simulator.getState().add(agent);
        return agent;
    }

    public synchronized Agent addProgrammedAgent(double lat, double lng, double heading, String policy) {
        AgentProgrammed agent = new AgentProgrammed("UGV-"+uniqueAgentNumber++, new Coordinate(lat, lng), sensor, simulator.getRandom(), simulator.getTaskController(), policy);
        agent.setCommunicationRange(simulator.getState().getCommunicationRange());
        agent.setHeading(heading);
        simulator.getState().add(agent);
        return agent;
    }

    public synchronized Agent addHubProgrammedAgent(double lat, double lng, Random random, TaskController taskController) {
        // We assume just one HUB, so this is a unique ID
        AgentProgrammed agent = new AgentHubProgrammed("HUB", new Coordinate(lat, lng), sensor, random, taskController);
        agent.setCommunicationRange(simulator.getState().getCommunicationRange());
        simulator.getState().add(agent);
        simulator.getState().attachHub(agent);
        return agent;
    }

    public synchronized Agent addVirtualCommunicatingAgent(double lat, double lng, Random random) {
        AgentCommunicating agent = new AgentCommunicating(generateUID(), new Coordinate(lat, lng), sensor, random);
        agent.setCommunicationRange(simulator.getState().getCommunicationRange());
        simulator.getState().add(agent);
        return agent;
    }

    public synchronized Agent addHubAgent(double lat, double lng) {
        // We assume just one HUB, so this is a unique ID
        Agent agent = new AgentHub("HUB", new Coordinate(lat, lng), sensor);
        simulator.getState().add(agent);
        simulator.getState().attachHub(agent);
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

    public synchronized void stopAllNonProgrammedAgents() {
        for(Agent agent : simulator.getState().getAgents()) {
            if (!(agent instanceof AgentProgrammed || agent instanceof AgentGhost)) {
                agent.stop();
            }
        }
    }

    public synchronized void updateAgentsTempRoutes() {
        for(Agent agent : simulator.getState().getAgents())
            agent.setTempRoute(agent.getRoute());
    }

    public synchronized void updateNonProgrammedAgentsTempRoutes() {
        for(Agent agent : simulator.getState().getAgents()) {
            if (!(agent instanceof AgentProgrammed || agent instanceof AgentGhost)) {
                agent.setTempRoute(agent.getRoute());
            }
        }
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

        // If region or patrol task, update the end point of the agent's route to be the closest point on the patrol.
        String taskId = simulator.getState().getTempAllocation().get(agentId);
        Task tempAllocatedTask = simulator.getState().getTask(taskId);
        if(tempAllocatedTask.getType() == Task.TASK_PATROL || tempAllocatedTask.getType() == Task.TASK_REGION)
            tempRoute.set(tempRoute.size() - 1, ((PatrolTask) tempAllocatedTask).getNearestPointAbsolute(agent));
    }

    public synchronized void editAgentTempRoute(String agentId, int index, Coordinate coordinate) {
        Agent agent = simulator.getState().getAgent(agentId);
        List<Coordinate> tempRoute = agent.getTempRoute();
        tempRoute.set(index, coordinate);

        // If region or patrol task, update the end point of the agent's route to be the closest point on the patrol.
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

    /**
     * Gets the beliefs of every programmed agent (as JSON strings)
     * @return ArrayList of Json model strings
     */
    public ArrayList<String> getBelievedModels() {
        ArrayList<String> models = new ArrayList<>(8);
        for (Agent a : simulator.getState().getAgents()) {
            if (a instanceof AgentProgrammed){
                models.add(((AgentProgrammed) a).getBelievedModel());
            }
        }
        return models;
    }

    /**
     * Gets the belief of the programmed Hub
     * @return String Json of the model
     */
    public ArrayList<String> getHubBelief() {
        ArrayList<String> models = new ArrayList<>(1);
        for (Agent a : simulator.getState().getAgents()) {
            if (a instanceof AgentHubProgrammed ahp){
                models.add(ahp.getBelievedModel());
            }
        }
        return models;

    }

    /**
     * Checks whether the given agent is connected to the Hub. Uses records in the ProgrammerHandler
     * @param hub The hub agent
     * @param agent The agent to check
     * @return Boolean  value for whether it is connected
     */
    public boolean checkHubConnection(Hub hub, Agent agent) {
        return ((AgentHubProgrammed) hub).checkForConnection(agent);
    }

    /**
     * Spawns a new agent next to the hub, if allowed by the limits
     * @return
     */
    public Agent spawnAgent() {
        if (scheduledRemovals > 0) {
            scheduledRemovals--;
        } else {
            if (Simulator.instance.getState().getAgents().size() < 11) {
                boolean hasProgrammed = false;
                for (Agent a : Simulator.instance.getState().getAgents()) {
                    if (a instanceof AgentProgrammed) {
                        hasProgrammed = true;
                        break;
                    }
                }
                Agent agent;
                if (hasProgrammed) {
                    synchronized (simulator.getState().getAgents()) {
                        agent = simulator.getAgentController().addProgrammedAgent(simulator.getState().getHubLocation().getLatitude(), simulator.getState().getHubLocation().getLongitude(), 0, "GenericAgentPolicy");
                    }
                } else {
                    int counter = 10;
                    double xOffset;
                    double yOffset;
                    boolean clash = true;
                    Coordinate c = null;
                    while (clash && counter > 0) {
                        xOffset = (simulator.getRandom().nextDouble() * 0.0015) - 0.00075;
                        yOffset = (simulator.getRandom().nextDouble() * 0.0015) - 0.00075;
                        c = new Coordinate(simulator.getState().getHubLocation().getLatitude() + xOffset, simulator.getState().getHubLocation().getLongitude() + yOffset);
                        // Check if any agent is too close
                        Coordinate finalC = c;
                        clash = simulator.getState().getAgents().stream().anyMatch(a -> a.getCoordinate().getDistance(finalC) < 0.0002);
                        counter--;
                    }

                    if (clash) {
                        // We still have a clash and can't fit it after 10 attempts
                        agent = null;
                    } else {
                        synchronized (simulator.getState().getAgents()) {
                            agent = simulator.getAgentController().addVirtualAgent(c.getLatitude(), c.getLongitude(), 0);
                        }
                        //agent.stop();
                    }
                }
                return agent;
            }
        }
        return null;
    }

    /**
     * If within limits, schedule the hub to remove the next non-recharging agent to enter its area
     * @return
     */
    public int despawnAgent() {
        Hub hub = Simulator.instance.getState().getHub();
        if (hub instanceof AgentHubProgrammed ahp) {
            return ahp.scheduleRemoval(1);
        } else if (hub instanceof AgentHub ah) {
            if (Simulator.instance.getState().getAgents().size() - ah.getScheduledRemovals() > 4) {
                simulator.getAgentController().decrementAgentNumbers();
                return simulator.getAgentController().incrementRemoval();
                //return ah.scheduleRemoval(1);
            }
        }
        return -1;
    }

    /**
     * Increments the number of scheduled removals
     * @return
     */
    private int incrementRemoval() {
        scheduledRemovals++;
        return scheduledRemovals;
    }

    /**
     * Decrements the number of scheduled removals
     */
    public void decrementRemoval() {
        scheduledRemovals--;
    }

    public int getScheduledRemovals() {
        return scheduledRemovals;
    }

    /**
     * Gets the closest agents from the entire state
     * @return
     */
    public Agent removeClosestAgentToHub() {
        synchronized (Simulator.instance.getState().getAgents()) {
            return removeClosestAgentToHub(Simulator.instance.getState().getAgents());
        }
    }

    /**
     * Removes the closest agent from a given list
     * @param agents
     * @return
     */
    public Agent removeClosestAgentToHub(Collection<Agent> agents) {
        Agent closestAgent = getClosestAgentToHub(agents);
        Simulator.instance.getState().getAgents().remove(closestAgent);
        return closestAgent;
    }

    /**
     * Gets the closest agent from a given list
     * @param agents
     * @return
     */
    public Agent getClosestAgentToHub(Collection<Agent> agents) {
        double minDistance = 99999.0;
        Agent closestAgent = null;
        for (Agent a : agents) {
            double thisDistance = simulator.getState().getHubLocation().getDistance(a.getCoordinate());
            if (thisDistance < minDistance && !(a instanceof Hub)) {
                closestAgent = a;
                minDistance = thisDistance;
            }
        }
        return closestAgent;
    }

    public Agent removeLeastRequiredAgent() {
        synchronized (Simulator.instance.getState().getAgents()) {
            return removeLeastRequiredAgent(Simulator.instance.getState().getAgents());
        }
    }

    /**
     * Remove the agent nearest to the hub that has no task
     * @param agents
     * @return
     */
    public Agent removeLeastRequiredAgent(Collection<Agent> agents) {
        double maxDistance = -1;
        Agent leastRequiredAgent = null;
        // We handle two cases: whether there are some unassigned agents, or whether there are zero unassigned agents
        boolean hasNull = agents.stream().anyMatch(a -> !(a instanceof Hub) && a.getTask() == null);
        ArrayList<Agent> agentsToConsider = new ArrayList<>();
        for (Agent a : agents) {
            if (hasNull && a.getTask() == null && !(a instanceof Hub)) {
                agentsToConsider.add(a);
            } else if (!(a instanceof Hub)) {
                double thisDistance = a.getCoordinate().getDistance(a.getTask().getCoordinate());
                if (thisDistance > maxDistance) {
                    leastRequiredAgent = a;
                    maxDistance = thisDistance;
                }
            }
        }
        Agent agentToRemove;
        if (hasNull) {
            // We return the closest of these to the hub as normal
            if (agentsToConsider.size() > 1) {
                agentToRemove = getClosestAgentToHub(agentsToConsider);
            } else {
                agentToRemove = agentsToConsider.get(0);
            }
        } else {
            agentToRemove = leastRequiredAgent;
        }
        Simulator.instance.getState().getAgents().remove(agentToRemove);
        LOGGER.severe("Removing " + agentToRemove);
        return agentToRemove;
    }

    public synchronized void resetAgentNumbers() {
        this.uniqueAgentNumber = 1;
    }

    public synchronized void decrementAgentNumbers() {
        this.uniqueAgentNumber-= 1;
    }

    public boolean areAllAgentsStopped() {
        for (Agent a : simulator.getState().getAgents()) {
            if (!a.isStopped()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Models the random failure, and kills if successful, the given agent
     * @param a
     * @return
     */
    public boolean modelFailure(Agent a) {
        // Each agent is called every 5 seconds, and the scenario is 4 mins (at time of writing) at 6 times speed
        //  this means that to have just a few failures in a scenario typically, let's say 4*6*60*5 = 7200,
        //  and 7200*10agents max = 72000, so 3/72000 = 1/24000 per step to average three failures at 10 agents total
        //  Gives ~0.000041667, and I tweak by eye after that

        if (a instanceof AgentVirtual av) {
            if (simulator.getRandom().nextDouble() < 0.00004) {
                av.setTimedOut(true);
                if (av.getAllocatedTaskId() != null && !av.getAllocatedTaskId().equals("")) {
                    av.getTask().getAgents().remove(av);
                }
                Simulator.instance.getAllocator().removeFromTempAllocation(av.getId());
                av.stop();
                av.setType("ghost");
                av.clearRoute();
                av.clearTempRoute();
                av.setAllocatedTaskId(null);
                av.setSearching(false);
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the agent at this coordinate (within 5m)
     * @param c
     * @return
     */
    public Agent getAgentAt(Coordinate c) {
        getAgentAt(c, 5);
        return null;
    }

    /**
     * Returns the agent at this coordinate (within epsilon m)
     * @param c
     * @return
     */
    public Agent getAgentAt(Coordinate c, double epsilon) {
        for (Agent a : Simulator.instance.getState().getAgents()) {
            if(a.getCoordinate().getDistance(c) < epsilon) {
                return a;
            }
        }
        return null;
    }

}
