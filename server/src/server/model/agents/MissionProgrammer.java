package server.model.agents;

import server.Simulator;
import server.model.Coordinate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;

/**
 * This is essentially a hub-specific programmer. Use this to setup the mission and rewards etc.
 */
public class MissionProgrammer {
    private final transient Logger LOGGER = Logger.getLogger(AgentVirtual.class.getName());
    private AgentHubProgrammed hub;
    private ProgrammerHandler programmerHandler;
    private List<String> agents;

    //TODO these values can be passed through the AgentHubProgrammed and therefore can be scenario file defined
    private Coordinate botLeft = new Coordinate(50.918934561834035, -1.415377448133106);
    private Coordinate topRight = new Coordinate(50.937665618776656, -1.3991319762570154);
    private int xSteps = 100;
    private int ySteps = 100;

    public MissionProgrammer(AgentHubProgrammed ahp, ProgrammerHandler progHandler) {
        hub = ahp;
        programmerHandler = progHandler;
        agents = new ArrayList<>();
        Simulator.instance.getState().getAgents().forEach(a -> agents.add(a.getId()));
    }

    public void step() {
        HashMap<String, ProgrammerHandler.Position> positions = programmerHandler.getNeighbours();
        HashMap<List<Coordinate>, List<String>> assignments = programmerHandler.getTasks();

        for (String a : agents) {
            if (!programmerHandler.agentHasTask(a)) {
                int x = (int) Math.floor(programmerHandler.getNextRandomDouble() * xSteps);
                int y = (int) Math.floor(programmerHandler.getNextRandomDouble() * ySteps);

                Coordinate c = calculateEquivalentCoordinate(x, y);
                programmerHandler.issueOrder(a, c);
            }
        }

        // TODO defer this until a certain time
        int reward = calculateReward();
        System.out.println("Reward = " + reward);
    }

    /**
     * Reward function. Uses actual sim-side data to make this more efficient and generally easier to program
     */
    private int calculateReward() {
        // TODO Note that a better system is to use actual circle geometry to find area of coverage. This can be
        //  problematic though, as overlapping circles shouldn't be counted twice and can be tricky to deal with.
        //  Certainly possible, but I'm leaving this for now for the sake of simplicity -WH
        double detectionRange = programmerHandler.getSenseRange();
        int numPointsCovered = 0;

        synchronized (Simulator.instance.getState().getAgents()) {
            for (int i = 0; i < xSteps; i++) {
                for (int j = 0; j < ySteps; j++) {
                    Coordinate equiv = calculateEquivalentCoordinate(i, j);
                    for (Agent a : Simulator.instance.getState().getAgents()) {
                        Coordinate coord = a.getCoordinate();
                        if (equiv.getDistance(coord) < detectionRange) {
                            // This square's centre is in range of an agent
                            numPointsCovered++;
                            break;
                        }
                    }
                }
            }
        }

        return numPointsCovered;

    }

    private Coordinate calculateEquivalentCoordinate(int x, int y) {
        double xSquareSpan = (topRight.getLongitude() - botLeft.getLongitude()) / xSteps;
        double ySquareSpan = (topRight.getLatitude() - botLeft.getLatitude()) / ySteps;

        return new Coordinate( botLeft.getLatitude() + (y * ySquareSpan), botLeft.getLongitude() + (x * xSquareSpan));
    }

}
