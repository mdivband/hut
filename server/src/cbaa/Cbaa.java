package cbaa;

import server.model.agents.Agent;
import server.model.task.Task;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class Cbaa {
    private List<AgentRep> agents;
    private List<Task> tasks;


    public Cbaa(List<Agent> agentsToAllocate, List<Task> tasksToAllocate) {
        agents = new ArrayList<>();
        tasks = tasksToAllocate;
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
                a.selectTask();
                // TODO comms here to update adjacency matrix
                if (!a.findConsensus()) {
                    converged = false;
                }

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
        int t = 0;

        public AgentRep(int numTasks, Agent a) {
            agent = a;
            taskList = new int[numTasks];
            bidsList = new double[numTasks];
        }


        public void selectTask() {
            // c_ij >=0 is the bid for agent i, task j
            // x_i is agent i's task list, where x_ij = 1 if i has been assigned to task j, or 0 otherwise (maybe use an array?)
            // y_i us the winning bids list. Up to date list of highest bid for each task
            // h_i is the valid task list, h_ij = I(c_ij > y_ij) for all J
            int[] newTaskList = taskList.clone();
            double[] newBidsList = bidsList.clone();

            //if (Arrays.stream(taskList).noneMatch(t -> (t == 1))) {
            if (true) {
                for (int j=0; j<taskList.length; j++) {
                    double bid = calculateBid(j);
                    if (bid >= newBidsList[j]) {
                        // [implied] if h != 0 THEN:
                        newBidsList[j] = bid;
                        newTaskList[j] = 1;
                    } else {
                        newTaskList[j] = 0;
                    }

                }
            }
            taskList = newTaskList;
            bidsList = newBidsList;
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
