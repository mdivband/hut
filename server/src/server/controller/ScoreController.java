package server.controller;

import server.Simulator;
import server.model.agents.AgentVirtual;
import server.model.agents.Hub;

public class ScoreController extends AbstractController {
    private int completedTasks;
    private int totalTasks;
    private double score;
    private double mission_cost = 0.00;
    private double previous_mission_cost = 0.00;
    private int POINTS_PER_TASK = 100;
    private double UPKEEP_PER_SIM_SECOND = 0.25;

    public ScoreController(Simulator simulator) {
        super(simulator, ScoreController.class.getName());
        completedTasks = 0;
        score = 500;
        totalTasks = 0;
        simulator.getState().addScoreInfo("upkeep", 0.0);
        simulator.getState().addScoreInfo("earned", 0.0);
        simulator.getState().addScoreInfo("progress", 0.0);
        simulator.getState().addScoreInfo("score", 0.0);
        simulator.getState().addScoreInfo("mission_cost", 0.0);
        simulator.getState().addScoreInfo("upkeep_add_agent", 0.0);
        simulator.getState().addScoreInfo("upkeep_rm_agent", 0.0);
    }

    public void reset() {
        completedTasks = 0;
        score = 500;
        totalTasks = 0;
        previous_mission_cost = mission_cost;
        mission_cost = 0.00;
        simulator.getState().addScoreInfo("upkeep", 0.0);
        simulator.getState().addScoreInfo("earned", 0.0);
        simulator.getState().addScoreInfo("progress", 0.0);
        simulator.getState().addScoreInfo("score", 0.0);
        simulator.getState().addScoreInfo("mission_cost", 0.0);
        simulator.getState().addScoreInfo("upkeep_add_agent", 0.0);
        simulator.getState().addScoreInfo("upkeep_rm_agent", 0.0);
    }

    public void handleUpkeep() {
        //System.out.println("UPKEEP - " + simulator.getState().getTime());
        int n = (int) simulator.getState().getAgents().stream().filter(a -> !a.getType().equals("ghost")).count();
        double upkeep = (0.1 * n * n) + 1.3;
        //double upkeep = simulator.getState().getAgents().size() * UPKEEP_PER_SIM_SECOND;
        simulator.getState().addScoreInfo("upkeep", upkeep * -1);

        // 20230904_0948h - Ayo Abioye (a.o.abioye@soton.ac.uk) added upkeep prediction to add/remove agent button
        int temp_n = (n - simulator.getState().getScheduledRemovals()) + 1;
        double upkeep_add_agent = (0.1 * temp_n * temp_n) + 1.3;
        simulator.getState().addScoreInfo("upkeep_add_agent", upkeep_add_agent);
        temp_n = (n - simulator.getState().getScheduledRemovals()) - 1;
        double upkeep_rm_agent = (0.1 * temp_n * temp_n) + 1.3;
        simulator.getState().addScoreInfo("upkeep_rm_agent", upkeep_rm_agent);

        // TODO also add the time limit?
        //double timeRemaining =

        score -= upkeep;
        simulator.getState().addScoreInfo("score", score);

        // 20230815_2107h - Ayo Abioye (a.o.abioye@soton.ac.uk) added mission cost
        mission_cost += upkeep;
        // added to if-else loop to fix resetting of mission cost at the end
        if (previous_mission_cost > mission_cost) {
            simulator.getState().addScoreInfo("mission_cost", previous_mission_cost);
            previous_mission_cost = 0.00;
        } else {
            simulator.getState().addScoreInfo("mission_cost", mission_cost);
        }
    }

    public void incrementCompletedTask() {
        completedTasks++;
        double earnedPoints = completedTasks * POINTS_PER_TASK;
        simulator.getState().addScoreInfo("earned", earnedPoints);

        double progress = 100 * ((double) completedTasks / totalTasks);
        simulator.getState().addScoreInfo("progress", progress);

        score += POINTS_PER_TASK;
        simulator.getState().addScoreInfo("score", score);

    }

    public double tempHeuristicPredict() {
        // Assume 20 seconds per task per agent
        // Use reciprocal as it is for remaining time
        double pred = completedTasks + (simulator.getState().getTimeLimit() / simulator.getState().getTime()) * completedTasks;
        double diff = pred - totalTasks;
        System.out.println("prog = " + simulator.getState().getTime() / simulator.getState().getTimeLimit() );
        System.out.println("comp = " + completedTasks);
        System.out.println("p = " + pred);
        System.out.println("d = " + diff);
        // e.g 5 more than reqd, or 3 less than reqd

        // If we can solve 50% more it's certain, and visa versa
        double h = ((diff / (totalTasks / 2d)) * 100);

        System.out.println(h);
        if (h > 100) {
            return 100;
        } else if (h < 0) {
            return 0;
        } else {
            return h;
        }

    }

    public void setTotalTasks(int totalTasks) {
        this.totalTasks = totalTasks;
    }

    public int getCompletedTasks() {
        return completedTasks;
    }

    public int getTotalTasks() {
        return totalTasks;
    }

    public double getScore() {
        return score;
    }

    public double getMission_cost() {
        return mission_cost;
    }

    public double getPrevious_mission_cost() {
        return previous_mission_cost;
    }
}
