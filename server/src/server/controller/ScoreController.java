package server.controller;

import server.Simulator;

public class ScoreController extends AbstractController {
    private int completedTasks;
    private double score;
    private int POINTS_PER_TASK = 100;
    private double UPKEEP_PER_SIM_SECOND = 0.25;

    public ScoreController(Simulator simulator) {
        super(simulator, ScoreController.class.getName());
        completedTasks = 0;
        score = 500;
        simulator.getState().addScoreInfo("upkeep", 0.0);
        simulator.getState().addScoreInfo("earned", 0.0);
        simulator.getState().addScoreInfo("progress", 0.0);
        simulator.getState().addScoreInfo("score", 0.0);
    }

    public void reset() {
        completedTasks = 0;
        score = 500;
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

        double progress = 100 * (double) completedTasks / simulator.getState().getTasks().size();
        simulator.getState().addScoreInfo("progress", progress);

        score += POINTS_PER_TASK;
        simulator.getState().addScoreInfo("score", score);

    }
}
