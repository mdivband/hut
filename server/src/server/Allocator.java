package server;

//import com.sun.javafx.geom.Edge;

import maxsum.Constraint;
import maxsum.Domain;
import maxsum.MaxSum;
import maxsum.Variable;
import server.model.Agent;
import server.model.AgentHub;
import server.model.Coordinate;
import server.model.MObject;
import server.model.target.HumanTarget;
import server.model.task.*;
import maxsum.EvaluationFunction;

import java.util.*;
import java.util.logging.FileHandler;
import java.util.logging.Logger;

/**
 * @author Feng Wu, Yuai Liu
 */
/* Edited by Yuai */

public class Allocator {

    private final static Task TASK_NONE = new WaypointTask("none", null);
    private final static Logger LOGGER = Logger.getLogger(Allocator.class.getName());
    private Map<String, String> oldresult = null; // used in runNoMaxsum() for getting previous result created by maxsum
    private Simulator simulator;
    private List<Map<String, String>> tempAllocationHistory; //History of tempAllocation - used for undo/redo.
    private int tempAllocationHistoryIndex; //Current position in history.

    public Allocator(Simulator simulator) {
        this.simulator = simulator;
        tempAllocationHistory = new ArrayList<>();
        tempAllocationHistory.add(simulator.getState().getAllocation());
    }

    public Map<String, String> getOldResult() {
        return oldresult;
    }

    /**
     * Automatically allocate agents to tasks using the maxsum planning algorithm
     * or random allocation.
     * Result is stored in state's temp allocation.
     * This doesn't actually allocate the agents to the tasks,
     *  a call to Allocator#confirmTempAssignemt will do this.
     */
    public void runAutoAllocation() {
        Map<String, String> allocation = new HashMap<>();
        List<Agent> agentsToAllocate = new ArrayList<>(simulator.getState().getAgents());

        //agentsToAllocate.removeIf(agent -> agent.isManuallyControlled() || agent.isTimedOut() || (agent instanceof AgentHub)
        //        || (agent.getTask() !=null && agent.getTask().getType() == Task.TASK_DEEP_SCAN && ((DeepScanTask) agent.getTask()).hasAgentScanned(agent)));

        List<Agent> newAgentsToAllocate = new ArrayList<>();
        for (Agent agent : agentsToAllocate) {
            if (agent.isManuallyControlled() || agent.isTimedOut() || (agent instanceof AgentHub)) {
                // Remove
            } else if (agent.getTask() !=null && agent.getTask().getType() == Task.TASK_DEEP_SCAN ) {
                // Remove, but reassign own task
                agent.getTask().addAgent(agent);
                agent.setWorking(true);
            } else {
                // Keep
                newAgentsToAllocate.add(agent);
            }
        }

        agentsToAllocate = newAgentsToAllocate;
        // Last 2 conditions above filter out the case where it is a Deep scan and this agent has done it.
        //  Note that the sequential nature of logical conjunctions protects against exceptions in this instance

        List<Task> tasksToAllocate = new ArrayList<>(simulator.getState().getTasks());

        String allocationMethod = simulator.getState().getAllocationMethod();

        if(allocationMethod.equals("maxsum")){
            allocation = compute(agentsToAllocate, tasksToAllocate, simulator.getState().getEditMode() == 2);
        } else if(allocationMethod.equals("random")) {
            allocation = randomCompute(agentsToAllocate, tasksToAllocate, simulator.getState().getEditMode() == 2);
        }

        if (allocation != null) {
            simulator.getState().setTempAllocation(allocation);

            //Set temp route of each agent to task coordinate if allocated, else ensure route is empty
            for (Agent agent : simulator.getState().getAgents()) {
                if (allocation.containsKey(agent.getId())) {
                    Task task = simulator.getState().getTask(allocation.get(agent.getId()));
                    if (task.getType() == Task.TASK_PATROL || task.getType() == Task.TASK_REGION) {
                        boolean match = false;
                        for (Coordinate c : ((PatrolTask) task).getPoints()) {
                            if (agent.getTempRoute().contains(c)) {
                                match = true;  // If we already have this route, leave it
                                break;
                            }
                        }
                        if (!match) {
                            agent.setTempRoute(Collections.singletonList(((PatrolTask) task).getNearestPointAbsolute(agent)));
                        }
                        /*
                    } else if (task.getType() == Task.TASK_DEEP_SCAN) {
                        DeepScanTask dst = (DeepScanTask) task;
                        if (dst.hasAgentScanned(agent)) {
                            agent.setTempRoute(Collections.singletonList(simulator.getState().getHubLocation()));
                        } else {
                            agent.setTempRoute(Collections.singletonList(task.getCoordinate()));
                        }

                         */
                    } else {
                        agent.setTempRoute(Collections.singletonList(task.getCoordinate()));
                    }
                } else
                    agent.setTempRoute(new ArrayList<>());
            }
            updateAllocationHistory();
        }
    }

    /**
     * Put allocation into state's temp allocation.
     * Will remove existing allocation (in temp allocation) of agent if one exists.
     */
    public void putInTempAllocation(String agentId, String taskId) {
        //Remove allocation to task if monitor or waypoint task (1 to 1 allocation only!)
        Task task = simulator.getState().getTask(taskId);
        if(task.getType() == Task.TASK_WAYPOINT || task.getType() == Task.TASK_MONITOR)
            simulator.getState().getTempAllocation().entrySet().removeIf(entry -> entry.getValue().equals(taskId));
        //Add new allocation
        simulator.getState().getTempAllocation().put(agentId, taskId);
        //Set agent route to task coordinate.
        Agent agent = simulator.getState().getAgent(agentId);
        if(task.getType() == Task.TASK_PATROL || task.getType() == Task.TASK_REGION)
            agent.setTempRoute(Collections.singletonList(((PatrolTask) task).getNearestPointAbsolute(agent)));
        else
            agent.setTempRoute(Collections.singletonList(task.getCoordinate()));
        updateAllocationHistory();
    }

    /**
     * Move the allocation for the given agent from the main to dropped allocation map.
     * @param agentId - Id of agent.
     */
    public void moveToDroppedAllocation(String agentId) {
        String taskId;
        if((taskId = simulator.getState().getAllocation().get(agentId)) != null) {
            Agent agent = simulator.getState().getAgent(agentId);
            Task task = simulator.getState().getTask(taskId);
            agent.setAllocatedTaskId(null);
            task.removeAgent(agentId);
            simulator.getState().getDroppedAllocation().put(agentId, taskId);
            simulator.getState().getAllocation().remove(agentId);
        }
    }

    /**
     * Remove an allocation from the temporary allocation.
     * @param agentId - Agent to remove allocation for.
     */
    public void removeFromTempAllocation(String agentId) {
        simulator.getState().getTempAllocation().remove(agentId);
        updateAllocationHistory();
    }

    /**
     * Confirms that the given allocation should become the main allocation.
     * Actually allocates agents to tasks based on new (now current) allocation.
     */
    public void confirmAllocation(Map<String, String> allocation) {
        //Copy allocation to main allocation
        Map<String, String> newMainAllocation = new HashMap<>(allocation);
        simulator.getState().setAllocation(newMainAllocation);

        //Clear agents and tasks
        for(Agent agent : simulator.getState().getAgents())
            if(!agent.isWorking()) {
                agent.setAllocatedTaskId(null);
                if(simulator.getState().isFlockingEnabled()) {
                    agent.resume();
                }
            }

        //Allocate agents to tasks
        for(Map.Entry<String, String> entry : newMainAllocation.entrySet()) {
            Agent agent = simulator.getState().getAgent(entry.getKey());
            Task task = simulator.getState().getTask(entry.getValue());
            if (agent != null && task != null) {
                //Assign
                if(agent.getAllocatedTaskId() == null || !agent.getAllocatedTaskId().equals(task.getId())) {
                    agent.setAllocatedTaskId(task.getId());
                    agent.setWorking(false);
                }
                task.addAgent(agent);

                //Update agent route
                agent.setRoute(agent.getTempRoute());
                agent.resume();
            }
        }

        clearAllocationHistory();
        simulator.getState().getDroppedAllocation().clear();
    }

    /**
     * Take of copy of the real allocation and set the temporary allocation to it.
     */
    public void copyRealAllocToTempAlloc() {
        Map<String, String> newTempAllocation = new HashMap<>(simulator.getState().getAllocation());
        simulator.getState().setTempAllocation(newTempAllocation);
    }

    /**
     * Undo a change to the temporary allocation.
     */
    public void undoAllocationChange() {
        moveAllocationHistory(tempAllocationHistoryIndex-1);
    }

    /**
     * Redo a change to the temporary allocation.
     */
    public void redoAllocationChange() {
        moveAllocationHistory(tempAllocationHistoryIndex+1);
    }

    /**
     * Reset the temporary allocation so it matches the real allocation.
     * Will create a new element in history so the previous temporary allocation can always be accessed.
     */
    public void resetAllocation() {
        copyRealAllocToTempAlloc();
        updateAllocationHistory();
    }

    /**
     * Move the allocation history to a new index.
     * @param newIndex - Index to position the history at. 0 < newIndex < size of history.
     */
    private void moveAllocationHistory(int newIndex) {
        if(newIndex < 0)
            return;
        if(newIndex >= tempAllocationHistory.size())
            return;

        simulator.getState().setTempAllocation(new HashMap<>(tempAllocationHistory.get(newIndex)));
        tempAllocationHistoryIndex = newIndex;
        updateUndoRedoAvailable();
    }

    /**
     * Add a new element to the temporary allocation history at the allocation history index.
     * Will only a new element if the temporary allocation is different from the one at the index.
     */
    private void updateAllocationHistory() {
        if(!simulator.getState().getTempAllocation().equals(tempAllocationHistory.get(tempAllocationHistoryIndex))) {
            //Insert into history
            tempAllocationHistory.add(++tempAllocationHistoryIndex, new HashMap<>(simulator.getState().getTempAllocation()));
            //Remove everything after new insert to maintain continuity.
            tempAllocationHistory.subList(tempAllocationHistoryIndex + 1, tempAllocationHistory.size()).clear();
        }
        updateUndoRedoAvailable();
    }

    /**
     * Clear the allocation history.
     * Will reset the history index to zero.
     */
    public void clearAllocationHistory() {
        tempAllocationHistory.clear();
        tempAllocationHistoryIndex = 0;
        tempAllocationHistory.add(simulator.getState().getAllocation());
        updateUndoRedoAvailable();
    }

    /**
     * Update the undo/redo available states (used to enable/disable the buttons on the client).
     */
    private void updateUndoRedoAvailable() {
        if(tempAllocationHistory.size() > 1) {
            simulator.getState().setAllocationUndoAvailable(tempAllocationHistoryIndex != 0);
            simulator.getState().setAllocationRedoAvailable(tempAllocationHistoryIndex != tempAllocationHistory.size()-1);
        } else {
            simulator.getState().setAllocationUndoAvailable(false);
            simulator.getState().setAllocationRedoAvailable(false);
        }

    }

    //TODO this should be passed a copy of the state at the time of calling
    // Rather than access the state directly in case the state changes during processing
    public void run(Map<Agent, Task> assignment, boolean editMode) {

        List<Agent> agentList = new ArrayList<>(simulator.getState().getAgents());

        //Compute routes
        Map<String, String> result = compute(agentList, new ArrayList<>(simulator.getState().getTasks()), editMode);

        if (result != null) {
            //Assign agents to tasks and vice versa. Also set routes of agents.
            for (String agentId : result.keySet()) {
                String taskId = result.get(agentId);
                Agent agent = simulator.getState().getAgent(agentId);
                Task task = simulator.getState().getTask(taskId);

                if (task != null && agent != null) {
                    task.addAgent(agent);
                    agent.setAllocatedTaskId(task.getId());
                    List<Coordinate> route = new ArrayList<>();
                    route.add(task.getCoordinate());
                    agent.setRoute(route);
                }
            }
        }
    }


    protected Map<String, String> compute(List<Agent> agents, List<Task> tasks, boolean editMode) {
        try {
            if (!agents.isEmpty() && !tasks.isEmpty()) {
                Map<String, String> result = runMaxSum(agents, tasks);
                if (!editMode) oldresult = result;
                return result;
            }
        } catch (Exception e) {

        }
        return null;
    }

    protected Map<String, String> randomCompute(List<Agent> agents, List<Task> tasks, boolean editMode) {
        if (!agents.isEmpty() && !tasks.isEmpty()) {
            HashMap<String, String> result = new HashMap<>();

            for (Task task : tasks) {
                task.clearAgents();
            }

            //Make sure the assignments won't be modified if the agent is working
            ArrayList<Agent> workingAgents = new ArrayList<>();
            for (Agent agent : agents) {
                if (agent.getTask() != null && agent.isWorking()) {
                    if (!tasks.contains(agent.getTask()) && agent.getTask().getAgents().size() >= agent.getTask().getGroup()) {
                        agent.setAllocatedTaskId(null);
                        agent.setWorking(false);
                        agent.setSearching(false);
                    } else {
                        workingAgents.add(agent);
                        agent.getTask().addAgent(agent);
                        result.put(agent.getId(), agent.getTask().getId());
                        if (agent.getTask().getAgents().size() >= agent.getTask().getGroup()) {
                            tasks.remove(agent.getTask());
                        }

                    }
                } else {
                    agent.setAllocatedTaskId(null);
                    agent.setSearching(false);
                    agent.setWorking(false);
                }
            }

            for (Agent agent : workingAgents) {
                agents.remove(agent);
            }

            for (Task task : tasks) {
                if (task.getAgents().size() < task.getGroup()) {
                    int rnd = new Random().nextInt(agents.size());
                    Agent agent = agents.get(rnd);
                    result.put(agent.getId(), task.getId());
                    agents.remove(agent);
                }
            }

            if (!editMode) oldresult = result;
            return result;
        }
        return null;
    }

    private Map<String, String> runMaxSum(List<Agent> agents, List<Task> tasks) {

        MaxSum maxsum = new MaxSum();
        HashMap<Agent, Task> resultObjs = new HashMap<>(); // TEMP solution
        HashMap<String, String> result = new HashMap<>();

        for (Task task : tasks) {
            task.clearAgents();
        }

        //Make sure the assignments won't be modified if the agent is working
        ArrayList<Agent> workingAgents = new ArrayList<>();
        for (Agent agent : agents) {
            if (agent.getTask() != null && agent.isWorking()) {
                if (!tasks.contains(agent.getTask()) && agent.getTask().getAgents().size() >= agent.getTask().getGroup()) {
                    agent.setAllocatedTaskId(null);
                    agent.setWorking(false);
                    agent.setSearching(false);
                } else {
                    workingAgents.add(agent);
                    agent.getTask().addAgent(agent);
                    result.put(agent.getId(), agent.getTask().getId());
                    resultObjs.put(agent, agent.getTask());
                    if (agent.getTask().getAgents().size() >= agent.getTask().getGroup()) {
                        tasks.remove(agent.getTask());
                    }

                }
            } else {
                agent.setAllocatedTaskId(null);
                agent.setSearching(false);
                agent.setWorking(false);
            }
        }

        for (Agent agent : workingAgents) {
            agents.remove(agent);
        }

        //Build the factor graph
        HashMap<Double, Edge> graph = createGraph(agents, tasks);
        TreeMap<Double, Edge> edgeGraph = new TreeMap<>(graph);

        //Remove cycles to form a cycle-free graph using minimum spanning tree
        MultiMap tree = minimumSpanningTree(edgeGraph);

        Variable[] variables = new Variable[agents.size()];
        for (int i = 0; i < agents.size(); ++i) {
            Agent agent = agents.get(i);

            // one variable per agent
            variables[i] = new Variable(agent);

            /* Added by Jack */
            //Create Domain for this agent only including those tasks in the MST
            List<Task> validTasks = tree.get(agent);
            //List<Task> validTasks = tasks;
            //If agent does not exist in tree (manual allocation), set size to zero
            int domainSize = 0;
            if (validTasks != null) domainSize = validTasks.size();


            Domain.State[] domain = new Domain.State[domainSize + 1];
            for (int j = 0; j < domainSize; ++j) {
                domain[j] = new Domain.State(validTasks.get(j));
            }
            domain[domainSize] = new Domain.State(TASK_NONE);

            variables[i].addDomains(domain);
        }


        maxsum.addVariables(variables);


        Constraint[] constraints = new Constraint[tasks.size() + 1];
        EvaluationFunction func = new EvaluationFunction();

        for (int i = 0; i < tasks.size(); ++i) {

            constraints[i] = new Constraint(tasks.get(i), func);
        }

        constraints[tasks.size()] = new Constraint(TASK_NONE, func);

        maxsum.addConstraints(constraints);

        for (int i = 0; i < variables.length; ++i) {
            Agent agent = agents.get(i);

            if (tree.get(agent) != null) {
                for (Task t : tree.get(agent)) {
                    for (Constraint constraint : constraints) {
                        if ((Task) constraint.getTask() == t) {
                            variables[i].addConstraint(constraint);
                        }
                    }
                }
            }

        }
        for (Constraint constraint : constraints) {
            for (Variable var : variables) {
                if (var.getConstraints().contains(constraint)) {
                    constraint.addVariable(var);
                }
            }
        }

        for (Variable var : variables) {
            for (Domain.State dom : var.getDomains()) {
                //TODO Solve nullpointer exception
                var.getConstraints().get(0).getFunction().computeUtility(var, dom);
            }
        }

        //Compute the maxsum solution
        maxsum.computeSolution(20);
        Map<Variable, Domain.State> solution = maxsum.getSolution();

        for (int i = 0; i < maxsum.getVariables().size(); ++i) {
            Variable var = maxsum.getVariables().get(i);
            Domain.State val = solution.get(var);

            Agent agent = var.getAgent();
            Task task = val.getTask();

            if (task != TASK_NONE) { // task is not none
                result.put(agent.getId(), task.getId());
                resultObjs.put(agent, task);  // TEMP
            }
        }

        for (Agent _agent : resultObjs.keySet()) { // TEMP
            if (_agent.getTask() != null) {
                _agent.getTask().clearAgents();
            }
        }
        return result;
    }

    //TODO Alternative allocation algorithm - unused - to remove?
    private Map<String, String> runNoMaxSum(double time, List<Agent> agents, List<Task> tasks,
                                            Map<Agent, Task> assignment) {
        /* Added by Yuai */
        Map<String, String> result = new HashMap<>();
        HashMap<Agent, Task> resultObjs = new HashMap<>(); // TEMP solution
        EvaluationFunction function = new EvaluationFunction();

        List<String> taskIDs = new ArrayList<>();
        Map<Task, Integer> taskIDsB = new HashMap<>();
        List<Agent> agentRef = new ArrayList<>();

        for (Task task : tasks) {
            task.clearAgents();
        }


        for (Task t : tasks) {
            taskIDs.add(t.getId());
            taskIDsB.put(t, 0);
        }
        for (Agent a : agents) {
            agentRef.add(a);
        }

        //make sure the fixed assignments won't be modified
        for (Agent agent : assignment.keySet()) {
            Task task = assignment.get(agent);
            result.put(agent.getId(), task.getId());
            resultObjs.put(agent, task);
            agentRef.remove(agent);
            task.addAgent(agent);
            taskIDsB.put(task, taskIDsB.get(task) + 1);
        }

        ArrayList<Agent> workingAgents = new ArrayList<>();
        for (Agent agent : agents) {
            if (agent.getTask() != null && agent.isWorking()) {
                if (!tasks.contains(agent.getTask())) {
                    agent.setAllocatedTaskId(null);
                    agent.setSearching(false);
                    agent.setWorking(false);
                } else {
                    workingAgents.add(agent);
                    taskIDsB.put(agent.getTask(), taskIDsB.get(agent.getTask()) + 1);
                    agent.getTask().addAgent(agent);
                }

            } else {
                agent.setAllocatedTaskId(null);
                agent.setSearching(false);
                agent.setWorking(false);
            }
        }

        for (Agent agent : workingAgents) {
            agentRef.remove(agent);
        }
        /* end Yuai */

        //TODO REF 1.3.2 put no-assignment allocation to result
		/*
		if (oldresult != null) {
			for(Agent agent :agents){
				String agentId = agent.getId();
				if (! result.containsKey(agentId)) {
					Task task = null;
					for (Task t : tasks) {
							if (t.getId().equals(oldresult.get(agentId))) {
								task = tasks.get(tasks.indexOf(t)); // get the tasks
							}
						}
						if (task != null && agent.getMode()!=Agent.TELEOP) {
							//setAgentTask4Region(task, agent);
							//agent.setTask(task);
							result.put(agentId, task.getId());
							resultObjs.put(agent, task);
							taskIDsB.put(task,taskIDsB.get(task) + 1);
						}
				}
			}
		}
		*/

        //TODO REF 1.3.2 put no-assignment allocation to result
        /* Added by Yuai */

        //First-price Auction
        for (Task task : taskIDsB.keySet()) {
            while (taskIDsB.get(task) < task.getGroup()) {
                if (agentRef.size() <= 0) {
                    break;
                }
                Agent maxAgent = agentRef.get(0);
                double maxVal = function.computeUtility(maxAgent, task);
                for (Agent agent : agentRef) {
                    double val = function.computeUtility(agent, task);
                    if (val > maxVal) {
                        maxAgent = agent;
                        maxVal = val;
                    }
                }
                agentRef.remove(maxAgent);
                result.put(maxAgent.getId(), task.getId());
                resultObjs.put(maxAgent, task);
                taskIDsB.put(task, taskIDsB.get(task) + 1);
                task.addAgent(maxAgent);
            }
        }

//		System.out.println("YOLO" + result);
        //TODO REF setup
        for (Map.Entry<Agent, Task> ent : resultObjs.entrySet()) {
            if (ent.getKey() != null && ent.getValue() != null) {
                ent.getKey().setAllocatedTaskId(ent.getValue().getId());
            }
        }
        /* End Yuai */

        //System.out.println(result);
        return result;
    }


    /* Added by Jack */
    private HashMap<Double, Edge> createGraph(List<Agent> agents, List<Task> tasks) {

        HashMap<Double, Edge> result = new HashMap<>();

        for (Agent agent : agents) {
            for (Task task : tasks) {
                double distance = agent.predictPathLength(agent.getCoordinate(), task.getCoordinate(), agent.getSpeed());
                result.put(distance, new Edge(agent, task));
            }
        }
        return result;
    }

    //Run MST algorithm on graph of weighted edges between agents and tasks
    private MultiMap minimumSpanningTree(TreeMap<Double, Edge> graph) {
        MultiMap result = new MultiMap();
        List<HashSet<MObject>> nodes = new ArrayList<>();

        //Loop through graph, adding edges if no cycles are created
        for (Edge edge : graph.values()) {
            Agent agent = edge.getAgent();
            Task task = edge.getTask();

            //Initial case
            if (nodes.size() == 0) {
                HashSet<MObject> firstSet = new HashSet<>();
                firstSet.add(agent);
                firstSet.add(task);
                nodes.add(firstSet);
                result.put(agent, task);
            } else {
                //Loop through all disjoint sets of nodes
                boolean needNew = true;
                for (HashSet<MObject> set : nodes) {
                    //Neither agent/task is in any set of nodes
                    if (set.contains(agent) && set.contains(task)) {
                        needNew = false;
                        //One agent/task, but not both, is in a set of nodes (XOR)
                    } else if (set.contains(agent) ^ set.contains(task)) {
                        if (set.contains(agent)) {
                            set.add(task);
                        } else {
                            set.add(agent);
                        }

                        if (!result.containsEntry(agent, task)) result.put(agent, task);
                        needNew = false;
                    }
                }

                //If no set contained either (or both) node from edge, create new set
                if (needNew) {
                    HashSet<MObject> newSet = new HashSet<>();
                    newSet.add(agent);
                    newSet.add(task);
                    nodes.add(newSet);

                    result.put(agent, task);
                }

                //Merge sets if they share a node
                int merged = 0;
                for (HashSet<MObject> set : nodes) {
                    int index = nodes.indexOf(set);

                    if (index != nodes.size() - 1) {
                        if (shareNode(set, nodes.get(index + 1))) {
                            for (MObject m : nodes.get(index + 1)) {
                                if (!set.contains(m)) {
                                    set.add(m);
                                }
                            }
                            merged = index + 1;
                        }
                    }
                }
                if (merged > 0) nodes.remove(merged);
            }
        }

        return result;
    }

    private boolean shareNode(HashSet<MObject> x, HashSet<MObject> y) {
        for (MObject m : y) {
            if (x.contains(m)) return true;
        }
        return false;
    }

    public boolean isSaturated() {
        // Add 1 to account for hub
        return (simulator.getState().getTempAllocation().size() + 1 == simulator.getState().getAgents().size());
    }

    private Agent getClosestAgentTo(Task t) {
        double closest = 999999;
        Agent closestAgent = null;
        for (Agent a : simulator.getState().getAgents()) {
            double thisDist = a.getCoordinate().getDistance(t.getCoordinate());
            if (thisDist < closest && !(a instanceof AgentHub)) {
                closest = thisDist;
                closestAgent = a;
            }
        }
        return closestAgent;
    }

    public void dynamicReassign(Task t) {
        LOGGER.info(String.format("%s; DYNRS; Dynamically reassigning agents to work scans;", Simulator.instance.getState().getTime()));
        if (isSaturated()) {
            Agent a = getClosestAgentTo(t);
            a.getTask().clearAgents();
            a.setAllocatedTaskId(null);
            //a.stop();
            runAutoAllocation();
            putInTempAllocation(a.getId(), t.getId());
        } else {
            runAutoAllocation();
        }
        confirmAllocation(simulator.getState().getTempAllocation());
    }

    public void dynamicReassign() {
        LOGGER.info(String.format("%s; DYNRS; Dynamically reassigning agents to work scans;", Simulator.instance.getState().getTime()));
        runAutoAllocation();
        confirmAllocation(simulator.getState().getTempAllocation());
    }
    public void resetLogger(FileHandler fileHandler) {
        LOGGER.addHandler(fileHandler);
    }

    //Inner class to provide generic pair of Agent-Task
    private class Edge {

        private final Agent agent;
        private final Task task;

        public Edge(Agent agent, Task task) {
            this.agent = agent;
            this.task = task;
        }

        public Agent getAgent() {
            return this.agent;
        }

        public Task getTask() {
            return this.task;
        }
    }

    public class MultiMap extends HashMap<Agent, List<Task>> {

        private static final long serialVersionUID = 1L;

        public void put(Agent key, Task value) {
            List<Task> current = get(key);
            if (current == null) {
                current = new ArrayList<>();
                super.put(key, current);
            }
            current.add(value);
        }

        boolean containsEntry(Agent agent, Task task) {
            for (Map.Entry<Agent, List<Task>> entry : super.entrySet()) {
                if (entry.getKey() == agent && entry.getValue().contains(task)) return true;
            }
            return false;
        }

        public int getSize() {
            int size = 0;

            for (Agent a : super.keySet()) {
                size += super.get(a).size();
            }
            return size;
        }
    }

}
