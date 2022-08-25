package server.controller;

import server.Simulator;

/**
 * This isn't really used at the moment. I have left it in so it can act as a template for a score system later -WH
 * @author William Hunt
 */
public class ScoreController extends AbstractController {
    private int completedTasks;
    private int totalTasks;
    private double score;
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
    }

    public void reset() {
        completedTasks = 0;
        score = 500;
        totalTasks = 0;
        simulator.getState().addScoreInfo("upkeep", 0.0);
        simulator.getState().addScoreInfo("earned", 0.0);
        simulator.getState().addScoreInfo("progress", 0.0);
        simulator.getState().addScoreInfo("score", 0.0);
    }

    public void handleUpkeep() {
        double upkeep = simulator.getState().getAgents().size() * UPKEEP_PER_SIM_SECOND;
        simulator.getState().addScoreInfo("upkeep", upkeep * -1);

        // TODO also add the time limit?
        //double timeRemaining =

        score -= upkeep;
        simulator.getState().addScoreInfo("score", score);
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

}
