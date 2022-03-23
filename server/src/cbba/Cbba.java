package cbba;

import server.model.agents.Agent;
import server.model.task.Task;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class Cbba {
    private List<AgentRep> agents;
    private List<Task> tasks;
    private int bundleSize;


    public Cbba(List<Agent> agentsToAllocate, List<Task> tasksToAllocate) {
        agents = new ArrayList<>();
        tasks = tasksToAllocate;
        bundleSize = Math.floorDiv(tasksToAllocate.size(), agentsToAllocate.size()) + 1; // Same as ceildiv
        int numTasks = tasksToAllocate.size();
        for (Agent a : agentsToAllocate) {
            agents.add(new AgentRep(numTasks, a));
        }
    }

    public HashMap<String, List<String>> compute() {
        HashMap<String, List<String>> newAlloc = new HashMap<>();
        boolean converged = false;
        while (!converged) {
            converged = true;
            for (AgentRep a : agents) {
                a.buildBundle();
                // TODO comms here to update adjacency matrix
                //if (!a.findConsensus()) {
                //    converged = false;
                //}
                System.out.println(a.agent.getId() + ", bundle: ");
                System.out.println(a.bundle);

                // WHERE:
                // c_ij >=0 is the bid for agent i, task j
                // x_i is agent i's task list, where x_ij = 1 if i has been assigned to task j, or 0 otherwise (maybe use an array?)
                // y_i us the winning bids list. Up to date list of highest bid for each task
                // h_i is the valid task list, h_ij = I(c_ij > y_ij) for all J



                // phase 1
                // x_i (t) = x_i (t - 1)
                // y_i (t) = y_i (t - 1)

                // [ for I(·) is the indicator function that is unity if the argument is true and zero otherwise.]
                // cnd = sum of [ (x_ij (t) ] == 0
                // if (cnd) {
                // h_ij = I(c_ij > y_ij(t)), for all j in J
                // if h_i != 0 {
                // J_i = argmax_j(h_ij . c_ij)
                // x_i,J_i(t) = 1
                // y_i,J_i(t) = c_i,J_i
                // }
                // }

            }
        }

        for (AgentRep a : agents) {
            newAlloc.put(a.agent.getId(), a.getChosenTaskIds());
        }

        agents.forEach(AgentRep::printTasks);
        System.out.println();
        System.out.println();
        System.out.println();
        agents.forEach(AgentRep::printBids);


        return newAlloc;
    }

    private class AgentRep {
        // x_i is agent i's task list, where x_ij = 1 if i has been assigned to task j, or 0 otherwise (maybe use an array?)
        // y_i us the winning bids list. Up to date list of highest bid for each task
        Agent agent;
        int[] taskList;
        double[] bidsList;
        List<Integer> bundle;  // Contains index references.
        //List<Integer> bundle;  // Contains index references. List required as order matters
        int t = 0;

        public AgentRep(int numTasks, Agent a) {
            agent = a;
            taskList = new int[numTasks];
            bidsList = new double[numTasks];
            bundle = new ArrayList<>();
        }


        public void buildBundle() {
            // c_ij >=0 is the bid for agent i, task j
            // x_i is agent i's task list, where x_ij = 1 if i has been assigned to task j, or 0 otherwise (maybe use an array?)
            // y_i us the winning bids list. Up to date list of highest bid for each task
            // h_i is the valid task list, h_ij = I(c_ij > y_ij) for all J
            int[] newTaskList = taskList.clone();
            double[] newBidsList = bidsList.clone();

            while (bundle.size() < bundleSize) {
                List<Integer> possibleTasks = new ArrayList<>();

                for (int j=0; j<taskList.length; j++) {
                    if (!bundle.contains(j)) {
                        possibleTasks.add(j);
                    }
                }

                double minDist = 999999999;
                int bestI = -1;
                int bestP = -1;
                // For each task
                for (int i : possibleTasks) {
                    double minDistForThisTask = 999999999;
                    int thisBestP = -1;
                    for (int p = 0; p< bundle.size(); p++) {
                        // Each possible insertion position
                        double thisDist = agent.getCoordinate().getDistance(tasks.get(bundle.get(0)).getCoordinate());
                        for (int k = 0; k< bundle.size() - 1; k++) {
                            if (k == p) {
                                // Insert, then add next one
                                thisDist += tasks.get(bundle.get(k)).getCoordinate().getDistance(tasks.get(bundle.get(i)).getCoordinate());
                                thisDist += tasks.get(bundle.get(i)).getCoordinate().getDistance(tasks.get(bundle.get(k+1)).getCoordinate());
                            } else {
                                thisDist += tasks.get(bundle.get(k)).getCoordinate().getDistance(tasks.get(bundle.get(k+1)).getCoordinate());
                            }
                        }
                        if (thisDist < minDistForThisTask) {
                            minDistForThisTask = thisDist;
                            thisBestP = p;
                        }
                    }
                    if (minDistForThisTask < minDist) {
                        minDist = minDistForThisTask;
                        bestI = i;
                        bestP = thisBestP;
                    }
                }

                List<Integer> newBundle = new ArrayList<>();
                if (bundle.isEmpty()) {
                    newBundle.add(bestI);
                } else {
                    System.out.println("bestP = " + bestP);
                    for (int i = 0; i < bundle.size(); i++) {
                        if (i == bestP) {
                            newBundle.add(bestI);
                            newBundle.add(bundle.get(bundle.get(i)));
                        } else {
                            newBundle.add(bundle.get(bundle.get(i)));
                            newBidsList[bundle.get(i)] = minDist;
                        }
                    }
                }
                bundle = newBundle;

                System.out.println("Updating bundle to "  + bundle);

            }


            taskList = newTaskList;
            bidsList = newBidsList;
            System.out.println(agent.getId());
            System.out.println("tl = " + Arrays.toString(taskList));
            System.out.println("bl = " + Arrays.toString(bidsList));
            System.out.println("bundle = " + bundle);
            System.out.println();
        }

        public boolean findConsensus() {
            // For now replace the communication matrix with global comms.
            // Not certain this is right
            boolean converged = true;
            for (int j=0; j<taskList.length; j++) {
                for (AgentRep a : agents) {
                    double thisBid = a.getBid(j);
                    if (thisBid > bidsList[j]) {
                        converged = false;
                        // This neighbour has a higher bid
                        bidsList[j] = thisBid;  // Update bid
                        taskList[j] = 0;  // Deselect this task
                    }
                }
            }
            return converged;
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
            for (int j=0;j<taskList.length;j++) {
                if (taskList[j] == 1) {
                    chosenTasks.add(tasks.get(j).getId());
                }
            }
            return chosenTasks;
        }
    }


}
