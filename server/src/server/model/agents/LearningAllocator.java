package server.model.agents;

import deepnetts.util.Tensor;
import server.model.Coordinate;

import java.util.ArrayList;
import java.util.List;

public abstract class LearningAllocator {
    protected List<AgentProgrammed> subordinates;


    protected int maxReward;
    private Coordinate botLeft;
    private Coordinate topRight;
    protected int xSteps;
    protected int ySteps;
    private double xSpan;
    private double ySpan;
    private double xSquareSpan;
    private double ySquareSpan;
    private float cellWidth;

    protected int counter;

    public void setup() {
        subordinates = new ArrayList<>();
    }

    public void reset() {

    }

    public void step() {

    }

    public void setBounds(Coordinate botLeft, Coordinate topRight) {
        this.botLeft = botLeft;
        this.topRight = topRight;
        xSpan = topRight.getLongitude() - botLeft.getLongitude();
        ySpan = topRight.getLatitude() - botLeft.getLatitude();
        xSquareSpan = xSpan / xSteps;
        ySquareSpan = ySpan / ySteps;
        maxReward = xSteps * ySteps;
        cellWidth = (float) ((xSquareSpan * 111111));  //((0.00016245471 * 111111));
    }

    public float calculateReward() {
        // TODO Note that a better system is to use actual circle geometry to find area of coverage. This can be
        //  problematic though, as overlapping circles shouldn't be counted twice and can be tricky to deal with.
        //  Certainly possible, but I'm leaving this for now for the sake of simplicity -WH
        int numPointsCovered = 0;
        for (int i = 0; i < xSteps; i++) {
            for (int j = 0; j < ySteps; j++) {
                //System.out.println("for (" + i + ", " + j + ")");
                Coordinate equiv = calculateEquivalentCoordinate(i, j);
                for (Agent a : subordinates) {
                    //if (!(a instanceof Hub)) {
                    Coordinate coord = a.getCoordinate();
                    if (equiv.getDistance(coord) < 250) {
                        // This square's centre is in range of an agent
                        numPointsCovered++;
                        break;
                    }
                    //}
                }
            }
        }
        return numPointsCovered;

    }

    private Coordinate calculateEquivalentCoordinate(int x, int y) {
        return new Coordinate( botLeft.getLatitude() + (y * ySquareSpan), botLeft.getLongitude() + (x * xSquareSpan));
    }

    public int[] calculateEquivalentGridCell(Coordinate c) {
        return new int[]{
                (int) Math.floor(((c.getLongitude() - botLeft.getLongitude()) / (xSpan)) * xSteps),
                (int) Math.floor(((c.getLatitude() - botLeft.getLatitude()) / (ySpan)) * ySteps)
        };
    }

    public float getCellWidth() {
        return cellWidth;
    }

    private Tensor getStateForThisAgent(AgentProgrammed agent) {
        float[][][] stateArray = new float[4][xSteps][ySteps];
        List<AgentProgrammed> orderedAgents = new ArrayList<>();
        orderedAgents.add(agent);
        subordinates.forEach(a -> {
            if (!a.equals(agent)) {
                orderedAgents.add(a);
            }
        });

        for (int d=0; d<orderedAgents.size(); d++) {
            Coordinate refCoord = orderedAgents.get(d).getCoordinate();
            int[] cell = calculateEquivalentGridCell(refCoord);
            for (int i=0; i<xSteps; i++) {
                for (int j=0; j<ySteps; j++) {
                    // TODO unsure if best to use binary classifier for coverage or some kind of radiated heatmap variant
                    if (cell[0] == i && cell[1] == j) {
                        stateArray[d][i][j] = 1;
                    } else {
                        stateArray[d][i][j] = 0;
                    }
                }
            }
        }
        return new Tensor(stateArray);
    }

    protected Tensor getState() {
        float[][][] stateArray = new float[4][xSteps][ySteps];
        for (int d=0; d<subordinates.size(); d++) {
            Coordinate refCoord = subordinates.get(d).getCoordinate();
            int[] cell = calculateEquivalentGridCell(refCoord);
            for (int i=0; i<xSteps; i++) {
                for (int j=0; j<ySteps; j++) {
                    // TODO unsure if best to use binary classifier for coverage or some kind of radiated heatmap variant
                    if (cell[0] == i && cell[1] == j) {
                        stateArray[d][i][j] = 1;
                    } else {
                        stateArray[d][i][j] = 0 ;
                    }
                }
            }
        }
        return new Tensor(stateArray);
    }

    public boolean checkInGrid(int[] cell) {
        return cell[0] >= 0 && cell[0] <= xSteps && cell[1] >= 0 && cell[1] <= ySteps;
    }

    public boolean checkCellValid(Coordinate coordinate) {
        return checkInGrid(calculateEquivalentGridCell(coordinate));
    }

    public void addSubordinate(AgentProgrammed ap) {
        subordinates.add(ap);
    }
    public List<AgentProgrammed> getSubordinates() {
        return subordinates;
    }

    public void setSubordinates(List<AgentProgrammed> subordinates) {
        this.subordinates = subordinates;
    }
}
