package cbba;

import server.Simulator;
import server.model.Coordinate;
import server.model.agents.Agent;
import server.model.task.Task;

import java.util.*;

/**
 * Constraint Based Bundle Allocation
 * https://ieeexplore.ieee.org/abstract/document/5072249?casa_token=wpoSEGtFX8oAAAAA:zwM0ViLXME4UevtMcXoEEwc4uff9QCkP7B31t0O1sZR4jacHXzETHen06s5K8bau4U61cZx3B5a0lMo
 */
public class Cbba {
    private final List<AgentRep> agents;
    private final List<Task> tasks;
    private final boolean coverageRequired;

    /**
     * Constructor
     * @param agentsToAllocate The list of agents available
     * @param tasksToAllocate The list of tasks to assign these to
     */
    public Cbba(List<Agent> agentsToAllocate, List<Task> tasksToAllocate, boolean coverage) {
        agents = new ArrayList<>();
        tasks = tasksToAllocate;
        int bundleSize = Math.floorDiv(tasksToAllocate.size(), agentsToAllocate.size()) + 1; //Same as ceildiv
        int numTasks = tasksToAllocate.size();
        int numAgents = agentsToAllocate.size();
        for (Agent a : agentsToAllocate) {
            agents.add(new AgentRep(numTasks, numAgents, a, bundleSize));
        }
        coverageRequired = coverage;
    }

    /**
     * The main compute method
     * @return Allocation as a HashMap, handled in Allocator
     */
    public HashMap<String, List<String>> compute() {
        HashMap<String, List<String>> newAlloc = new HashMap<>();
        boolean converged = false;
        for (AgentRep a : agents) {
            a.bundle.add(a.findNearestTask());
            a.buildBundle(a.bundle);
            //System.out.println(a.agent.getId() + ", bundle: ");
            //System.out.println(a.bundle);
        }
        int depth = 0;
        while (!converged && depth < 100) {
            converged = true;
            for (AgentRep a : agents) {
                /*
                if (coverageAttained()) {
                    converged = true;
                    break;
                }
                 */
                // TODO comms here to update adjacency matrix
                if (!a.findConsensus()) {
                    converged = false;
                }
            }
            depth++;
            /*
            System.out.println();
            System.out.println();
            System.out.println("===============================Round complete=============================");
            System.out.println();
            agents.forEach(AgentRep::printBundles);
            agents.forEach(AgentRep::printBids);
            agents.forEach(AgentRep::printAllocation);
            System.out.println();
            */
        }

        System.out.println("At depth = " + depth);

        List<String> unassignedTasks = new ArrayList<>();
        tasks.forEach(t -> unassignedTasks.add(t.getId()));
        for (AgentRep a : agents) {
            newAlloc.put(a.agent.getId(), a.getChosenTaskIds());
            unassignedTasks.removeAll(a.getChosenTaskIds());
        }
        if (coverageRequired) {
            unassignedTasks.forEach(t -> {
                double maxTotal = -999999999;
                AgentRep bestAgent = null;
                for (AgentRep a : agents) {
                    double thisTotal = a.bidsList[a.bundle.get(0)] - tasks.get(a.bundle.get(a.bundle.size() - 1)).getCoordinate().getDistance(Simulator.instance.getState().getTask(t).getCoordinate());
                    if (thisTotal > maxTotal) {
                        bestAgent = a;
                        maxTotal = thisTotal;
                    }
                }


                //bestAgent.bundle.add(tasks.indexOf(Simulator.instance.getState().getTask(t)));
                bestAgent.addToBundle(tasks.indexOf(Simulator.instance.getState().getTask(t)));
                newAlloc.get(bestAgent.agent.getId()).add(t);


                // Update bids, incase we have to do another insertion
                for (AgentRep a : agents) {
                    for (int i : bestAgent.bundle) {
                        a.bidsList[i] = maxTotal;
                    }
                }

                System.out.println("Added " + t + " to " + bestAgent.agent.getId());
            });
        }


        System.out.println("===SUMMARY===");
        agents.forEach(AgentRep::printBundles);
        //agents.forEach(AgentRep::printBids);
        //agents.forEach(AgentRep::printAllocation);
        return newAlloc;
    }

    /**
     * Unused, Returns true if every task has been assigned
     * @return
     */
    private boolean coverageAttained() {
        for (AgentRep a : agents) {
            if (Arrays.stream(a.allocation).anyMatch(Objects::isNull)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Inner class used to represent agents.
     */
    private class AgentRep {
        // x_i is agent i's task list, where x_ij = 1 if i has been assigned to task j, or 0 otherwise (maybe use an array?)
        // y_i us the winning bids list. Up to date list of highest bid for each task
        Agent agent;
        int[] taskList;
        double[] bidsList;
        int bundleSize;
        List<Integer> bundle;  // Contains index references.
        AgentRep[] allocation;
        double[] timeStamps; // I'm not 100% certain this is their method, but I store a list of timestamps for the most recent update
        int rebuildCounter = (new Random()).nextInt(20);
        int rebuildLimit = 40;
        private double weight = 0;

        public AgentRep(int numTasks, int numAgents, Agent a, int bundleSize) {
            agent = a;
            taskList = new int[numTasks];
            bidsList = new double[numTasks];
            allocation = new AgentRep[numTasks];
            timeStamps = new double[numTasks];
            this.bundleSize = bundleSize;
            for (int i=0; i<numAgents; i++) {
                timeStamps[i] = System.currentTimeMillis();
            }
            for (int i=0;i<numTasks;i++) {
                allocation[i] = null;
            }
            bundle = new ArrayList<>();
        }

        /**
         * Construct the shortest-distaced bundle of tasks from the given stub
         * @param currentBundle
         */
        public void buildBundle(List<Integer> currentBundle) {
            //System.out.println(" FOR AGENT " + agent.getId());
            // c_ij >=0 is the bid for agent i, task j
            // x_i is agent i's task list, where x_ij = 1 if i has been assigned to task j, or 0 otherwise (maybe use an array?)
            // y_i us the winning bids list. Up to date list of highest bid for each task
            // h_i is the valid task list, h_ij = I(c_ij > y_ij) for all J
            double[] newBidsList = bidsList.clone();

            while (currentBundle.size() < bundleSize) {
                List<Integer> possibleTasks = new ArrayList<>();

                for (int j=0; j<taskList.length; j++) {
                    if (!currentBundle.contains(j)) { // && j!=veto) {
                        possibleTasks.add(j);
                    }
                }
                //Collections.shuffle(possibleTasks);
                //System.out.println("POSS: " + possibleTasks);
                //System.out.println("bundle = " + currentBundle);

                double minDistOverall = 999999999;
                int bestI = -1;
                int bestP = -1;
                // For each task
                for (int i : possibleTasks) {
                    //System.out.println("Considering " + tasks.get(i));
                    double minDistForThisTask = 999999999;
                    int thisBestP = -1;
                    if (currentBundle.isEmpty()) {
                        double thisDist = agent.getCoordinate().getDistance(tasks.get(i).getCoordinate());
                        if (thisDist < minDistOverall) {
                            minDistOverall = thisDist;
                            bestI = i;
                            //System.out.println("---Initial assignment: i=" + i + " (d=" + minDistOverall + ")");
                        }
                    } else {
                        for (int p = 0; p < currentBundle.size(); p++) {
                            //System.out.println("For p="+p);
                            // Each possible insertion position
                            double thisDist;
                            if (currentBundle.size() == 1) {
                                thisDist = agent.getCoordinate().getDistance(tasks.get(currentBundle.get(0)).getCoordinate());
                                thisDist += tasks.get(currentBundle.get(0)).getCoordinate().getDistance(tasks.get(i).getCoordinate());
                            } else {
                                thisDist = 0;//agent.getCoordinate().getDistance(tasks.get(currentBundle.get(0)).getCoordinate());
                                Iterator<Integer> bundleIt = currentBundle.listIterator();
                                int bundleIndex = 0;
                                Coordinate lastCoord = agent.getCoordinate();
                                while (bundleIt.hasNext()) {
                                    if (bundleIndex == p) {
                                        Task insertTask = tasks.get(i);
                                        thisDist += lastCoord.getDistance(insertTask.getCoordinate());

                                        Task thisTask = tasks.get(bundleIt.next());
                                        thisDist += insertTask.getCoordinate().getDistance(thisTask.getCoordinate());
                                        lastCoord = thisTask.getCoordinate();
                                    } else {
                                        Task thisTask = tasks.get(bundleIt.next());
                                        thisDist += lastCoord.getDistance(thisTask.getCoordinate());
                                        lastCoord = thisTask.getCoordinate();
                                    }
                                    bundleIndex++;
                                }
                            }
                            if (thisDist < minDistForThisTask) {
                                minDistForThisTask = thisDist;
                                thisBestP = p;
                                //System.out.println("---updating best insertion: " + thisBestP + " (d=" + minDistForThisTask + ")");
                            }
                        }

                        if (minDistForThisTask < minDistOverall) {
                            //System.out.println("for i="+i+", p="+thisBestP+", a score of "+minDistForThisTask+" is better than "+ minDistOverall);
                            minDistOverall = minDistForThisTask;
                            bestI = i;
                            bestP = thisBestP;
                            //System.out.println("updating best task: " + bestI + ", p=" + bestP + " (d=" + minDistOverall + ")");
                        }
                    }
                }
                //System.out.println("Chose task " + tasks.get(bestI));
                List<Integer> newBundle = new ArrayList<>();
                if (currentBundle.isEmpty()) {
                    newBundle.add(bestI);
                } else {
                    for (int i = 0; i < currentBundle.size(); i++) {
                        if (i == bestP) {
                            newBundle.add(bestI);
                            newBidsList[bestI] = -minDistOverall;
                        }
                        newBundle.add(currentBundle.get(i));
                        newBidsList[currentBundle.get(i)] = -minDistOverall;
                    }
                }
                currentBundle = newBundle;
                weight = -minDistOverall;
            }

            bundle = currentBundle;
            bidsList = newBidsList;

            for (int i : bundle) {
                allocation[i] = this;
            }
        }

        /**
         * Manually add the given task to bundle at best location
         * @param taskToAdd
         */
        public void addToBundle(int taskToAdd) {
            double minDistForThisTask = 999999999;
            int bestP = -1;

            for (int p = 0; p < bundle.size(); p++) {
                //System.out.println("For p="+p);
                // Each possible insertion position
                double thisDist = 0;//agent.getCoordinate().getDistance(tasks.get(currentBundle.get(0)).getCoordinate());
                Iterator<Integer> bundleIt = bundle.listIterator();
                int bundleIndex = 0;
                Coordinate lastCoord = agent.getCoordinate();
                while (bundleIt.hasNext()) {
                    if (bundleIndex == p) {
                        Task insertTask = tasks.get(taskToAdd);
                        thisDist += lastCoord.getDistance(insertTask.getCoordinate());

                        Task thisTask = tasks.get(bundleIt.next());
                        thisDist += insertTask.getCoordinate().getDistance(thisTask.getCoordinate());
                        lastCoord = thisTask.getCoordinate();
                    } else {
                        Task thisTask = tasks.get(bundleIt.next());
                        thisDist += lastCoord.getDistance(thisTask.getCoordinate());
                        lastCoord = thisTask.getCoordinate();
                    }
                    bundleIndex++;
                }
                if (thisDist < minDistForThisTask) {
                    minDistForThisTask = thisDist;
                    bestP = p;
                }
            }

            weight = minDistForThisTask;
            List<Integer> newBundle = new ArrayList<>();
            for (int i = 0; i < bundle.size(); i++) {
                if (i == bestP) {
                    newBundle.add(taskToAdd);
                }
                newBundle.add(bundle.get(i));
                if (bidsList[i] > minDistForThisTask) {
                    bidsList[i] = minDistForThisTask;
                }
            }
            bundle = newBundle;
            System.out.println("Updated: ");
            printBundles();
            printBids();
            System.out.println();
        }

        /**
         * Attempts to find a consensus through communication between agents
         * @return
         */
        public boolean findConsensus() {
            // For now replace the communication matrix with global comms.
            // Not certain this is right
            boolean converged = true;

            // "If a bid is changed by the decision rules in Table I, each agent
            //checks if any of the updated or reset tasks were in their bundle,
            //and if so, these tasks, along with all of the tasks that were added
            //to the bundle after them, are released"

            for (AgentRep a : agents) {
                if (a != this) {  // Don't send to ourself
                    for (int j = 0; j < allocation.length; j++) {
                        if (a.receive(j, this, allocation[j])) {
                            converged = false;
                            break;
                        }
                    }
                }
            }

            if (converged) {
                if (Arrays.stream(allocation).anyMatch(Objects::isNull)) {
                    // converged, but does not cover all tasks
                    for (AgentRep a : agents) {
                        a.rebuildCounter = 0;
                        a.bundleSize = Math.floorDiv(tasks.size(), agents.size()) + 1; //Same as ceildiv
                    }
                    return false;
                } else {
                    return true;
                }
            } else {
                return false;
            }
        }

        /**
         * This is essentially a coded table from table 1 of the paper on this.
         * Note that the order of the table is different form original, to allow m nonMem {i,k} as else case
         *  (This has no influence on outcome, just makes it read differently)
         * @param j
         * @param sender
         * @param senderBelief
         * @return true if a change made
         */
        private boolean receive(int j, AgentRep sender, AgentRep senderBelief) {
            //System.out.println("For allocation " + j);
            AgentRep thisBelief = allocation[j];
            // y is winning bids list
            // z is winning agent list
            double senderBid = sender.getBid(j);
            double thisBid = getBid(j);

            if (senderBelief == sender) {
                //System.out.println("Sender thinks: k (sender)");
                if (thisBelief == this) {
                    //System.out.println("--Receiver thinks: i (receiver)");
                    if (senderBid > thisBid) {
                        // UPDATE
                        //System.out.println("k, i. UPDATE");
                        return update(senderBid, sender, j);
                    }
                } else if (thisBelief == sender) {
                    //System.out.println("--Receiver thinks: k (sender)");
                    // UPDATE
                    //System.out.println("k, k. UPDATE");
                    //update(senderBid, sender, j);
                    return false;  // As these agree, we return false
                } else if (thisBelief == null) {
                    //System.out.println("--Receiver thinks: none");
                    // UPDATE
                    //System.out.println("k, null. UPDATE");
                    return update(senderBid, sender, j);
                } else {
                    //System.out.println("--Receiver thinks: m (other agent)");
                    // We believe it's m, which is thisBelief
                    if (sender.timeStamps[agents.indexOf(thisBelief)] > timeStamps[agents.indexOf(thisBelief)] || senderBid > thisBid) {
                        // UPDATE
                        //System.out.println("k, m. UPDATE");
                        return update(senderBid, sender, j);
                    }
                }
            } else if (senderBelief == this) {
                //System.out.println("Sender thinks: i (receiver)");
                if (thisBelief == this) {
                    //System.out.println("--Receiver thinks: i (receiver)");
                    // LEAVE
                    return false;
                } else if (thisBelief == sender) {
                    //System.out.println("--Receiver thinks: k (sender)");
                    // RESET
                    //System.out.println("i, k. RESET");
                    return reset(j);
                } else if (thisBelief == null) {
                    //System.out.println("--Receiver thinks: none");
                    // LEAVE
                    return false;
                } else {
                    //System.out.println("--Receiver thinks: m (other agent)");
                    if (sender.timeStamps[agents.indexOf(thisBelief)] > timeStamps[agents.indexOf(thisBelief)]) {
                        // RESET
                        //System.out.println("i, m. RESET");
                        return reset(j);
                    }
                }
            } else if (senderBelief == null) {
                //System.out.println("Sender thinks: none");
                if (thisBelief == this) {
                    //System.out.println("Sender thinks: i (receiver)");
                    // LEAVE
                    return false;
                } else if (thisBelief == sender) {
                    //System.out.println("--Receiver thinks: k (sender)");
                    // UPDATE
                    //System.out.println("null, k. UPDATE");
                    return update(senderBid, sender, j);
                } else if (thisBelief == null) {
                    //System.out.println("--Receiver thinks: none");
                    // LEAVE
                    // TEMP -  WE may need to ensure it is covered
                    return false;  // As these agree, we return false
                } else {
                    //System.out.println("--Receiver thinks: m (other agent)");
                    if (sender.timeStamps[agents.indexOf(thisBelief)] > timeStamps[agents.indexOf(thisBelief)]) {
                        // UPDATE
                        //System.out.println("null, m. UPDATE");
                        return update(senderBid, sender, j);
                    }
                }
            } else {
                //System.out.println("Sender thinks: m (other agent)");
                // m is senderBelief by definition
                if (thisBelief == this) {
                    //System.out.println("Sender thinks: i (receiver)");
                    if (sender.timeStamps[agents.indexOf(thisBelief)] > timeStamps[agents.indexOf(thisBelief)] && senderBid > thisBid) {
                        // UPDATE
                        //System.out.println("m, i. UPDATE");
                        return update(senderBid, sender, j);
                    }
                } else if (thisBelief == sender) {
                    //System.out.println("--Receiver thinks: k (sender)");
                    if (sender.timeStamps[agents.indexOf(thisBelief)] > timeStamps[agents.indexOf(thisBelief)]) {
                        // UPDATE
                        //System.out.println("m, k. UPDATE");
                        return  update(senderBid, sender, j);
                    } else {
                        // RESET
                        //System.out.println("m, k. RESET");
                        return reset(j);
                    }
                } else if (thisBelief == senderBelief) {
                    //System.out.println("--Receiver thinks: m (other agent)");
                    // m for both
                    if (sender.timeStamps[agents.indexOf(thisBelief)] > timeStamps[agents.indexOf(thisBelief)]) {
                        // UPDATE
                        //update(senderBid, sender, j);
                        //System.out.println("m, m. UPDATE");
                        return false;  // As these agree, we return false
                    }
                } else if (thisBelief == null) {
                    //System.out.println("--Receiver thinks: none");
                    if (sender.timeStamps[agents.indexOf(senderBelief)] > timeStamps[agents.indexOf(senderBelief)]) {
                        // UPDATE
                       // System.out.println("m, null. UPDATE");
                        return update(senderBid, sender, j);
                    }
                } else {
                    //System.out.println("--Receiver thinks: n (different other agents)");
                    // the n nonMember {i,k,m} case
                    // m = senderBelief, n = thisBelief
                    if (sender.timeStamps[agents.indexOf(senderBelief)] > timeStamps[agents.indexOf(senderBelief)] && sender.timeStamps[agents.indexOf(thisBelief)] > timeStamps[agents.indexOf(thisBelief)]) {
                       // UPDATE
                        //System.out.println("m, n. UPDATE 1");
                        return update(senderBid, sender, j);
                    } else if (sender.timeStamps[agents.indexOf(senderBelief)] > timeStamps[agents.indexOf(senderBelief)] && senderBid > thisBid) {
                        // UPDATE
                        //System.out.println("m, n. UPDATE 2");
                        return update(senderBid, sender, j);
                    } else if (sender.timeStamps[agents.indexOf(thisBelief)] > timeStamps[agents.indexOf(thisBelief)] && timeStamps[agents.indexOf(senderBelief)] > sender.timeStamps[agents.indexOf(sender)]) {
                        // RESET
                        //System.out.println("m, n. RESET");
                        return reset(j);
                    } else {
                        //System.out.println("FINAL COND");
                    }
                }
            }
            return false;
        }

        /**
         * For update signals
         * @param senderBid
         * @param sender
         * @param j
         * @return
         */
        private boolean update(double senderBid, AgentRep sender, int j) {
            bidsList[j] = senderBid;
            allocation[j] = sender;
            timeStamps[j] = sender.timeStamps[j]; //System.currentTimeMillis();

            return recomputeBundle(j);
        }

        /**
         * For reset signals
          * @param j
         * @return
         */
        private boolean reset(int j) {
            for (int k=j; k<allocation.length; k++) {
                bidsList[k] = 0;
                allocation[k] = null;
                timeStamps[k] = System.currentTimeMillis();
            }

            return recomputeBundle(j);
        }

        /**
         * Rebuilds the bundle given the changes
         * @param j
         * @return
         */
        private boolean recomputeBundle(int j) {
            if (bundle.contains(j)) {
                int i = bundle.indexOf(j);
                System.out.println("==Recomputing Bundle from i="+i+", bundle = " + bundle);
                if (i == 0) {
                    bundle = new ArrayList<>();
                    for (int k = 0; k < allocation.length; k++) {
                        AgentRep a = allocation[k];
                        if (a == null && bundle.size() < bundleSize - 1) {
                            bundle.add(k);
                            System.out.println("Manually adding " + k);
                        }
                    }
                } else {
                    bundle = bundle.subList(0, i);
                }

                //System.out.println("==Reduced to " + bundle);
                considerReduction();
                buildBundle(new ArrayList<>(bundle));
                return true;
            }
            return false;
        }

        /**
         * Checks, and if required, reduces the bundle size
         */
        private void considerReduction() {
            rebuildCounter++;
            if (rebuildCounter > rebuildLimit) {
                bundleSize--;
                rebuildCounter = 0;
                if (bundle.size() > bundleSize) {
                    bundle = bundle.subList(0, bundleSize);
                }
                //System.out.println("REDUCED BUNDLE SIZE TO " + bundleSize);
                sendReductionReset();
            }
        }

        /**
         * Alert other agents to reset their counters for reducing bundle size
         */
        private void sendReductionReset() {
            for (AgentRep agentRep : agents) {
                // TODO limited comms
                if (agentRep != this) {
                    agentRep.rebuildCounter = (new Random()).nextInt(20);
                    //System.out.println("RESET REBUILD COUNTER FOR " + agentRep.agent.getId());
                }
            }
        }

        private double getBid(int j) {
            return bidsList[j];
        }

        private double calculateBid(int j) {
            return (1 / agent.getCoordinate().getDistance(tasks.get(j).getCoordinate()));
        }

        public void printTasks() {
            System.out.println(agent.getId() + ": " + Arrays.toString(taskList));
        }

        public void printBids() {
            System.out.println(agent.getId() + ": " + Arrays.toString(bidsList));
        }

        public List<String> getChosenTaskIds() {
            List<String> chosenTasks = new ArrayList<>();
            for (int j : bundle) {
                chosenTasks.add(tasks.get(j).getId());
            }
            return chosenTasks;
        }

        public void printBundles() {
            System.out.println(agent.getId() + ": " + bundle + " -> dist" + weight);
        }

        public void printAllocation() {
            System.out.print(agent.getId() + ": Allocation: [");Arrays.stream(allocation).forEach(a -> {if (a == null) {System.out.print("0, ");}else{System.out.print(a.agent.getId() + ", ");}});System.out.println("]");
        }

        public Integer findNearestTask() {
            int bestIndex = -1;
            double minDist = 999999999;
            for (int i = 0; i < tasks.size(); i++) {
                if (!bundle.contains(i)) {
                    double thisDist = tasks.get(i).getCoordinate().getDistance(agent.getCoordinate());
                    if (thisDist < minDist) {
                        minDist = thisDist;
                        bestIndex = i;
                    }
                }
            }
            return bestIndex;
        }


    }


}
