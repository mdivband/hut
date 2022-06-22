package server.model.agents;

import deepnetts.util.Tensor;
import server.Simulator;
import server.model.Coordinate;

import java.util.ArrayList;
import java.util.List;

public abstract class LearningAllocator {
    protected List<AgentProgrammed> subordinates;
    protected AgentProgrammed supervisor;


    protected int maxReward;
    private Coordinate botLeft;
    private Coordinate topRight;
    protected int xSteps;
    protected int ySteps;
    private double X_SPAN = 0.01;
    private double Y_SPAN = 0.006;
    private double xSquareSpan;
    private double ySquareSpan;
    private float cellWidth;

    protected int counter;

    public void setup() {
        subordinates = new ArrayList<>();
    }

    public void reset() {

    }

    public abstract void complete();

    public abstract void step();

    public void updateBounds(Coordinate position) {
        double topBound = position.getLatitude() + (Y_SPAN / 2);
        double botBound = position.getLatitude() - (Y_SPAN / 2);
        double rightBound = position.getLongitude() + (X_SPAN / 2);
        double leftBound = position.getLongitude() - (X_SPAN / 2);

        botLeft = new Coordinate(botBound, leftBound);
        topRight = new Coordinate(topBound, rightBound);

        xSquareSpan = X_SPAN / xSteps;
        ySquareSpan = Y_SPAN / ySteps;
        maxReward = xSteps * ySteps;
        cellWidth = (float) ((xSquareSpan * 111111));

        Simulator.instance.getState().getMarkers().removeIf(m -> m.contains(supervisor.getId()));

        double bot = botLeft.getLatitude();
        double top = topRight.getLatitude();
        double left = botLeft.getLongitude();
        double right = topRight.getLongitude();
        String botLine = supervisor.getId()+"bot,"+",polyline,"
                +bot+","+left+","+bot+","+right;
        String rightLine = supervisor.getId()+"right,"+",polyline,"
                +bot+","+right+","+top+","+right;
        String topLine = supervisor.getId()+"top,"+",polyline,"
                +top+","+right+","+top+","+left;
        String leftLine = supervisor.getId()+"left,"+",polyline,"
                +top+","+left+","+bot+","+left;

        Simulator.instance.getState().getMarkers().add(botLine);
        Simulator.instance.getState().getMarkers().add(rightLine);
        Simulator.instance.getState().getMarkers().add(topLine);
        Simulator.instance.getState().getMarkers().add(leftLine);

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

    public Coordinate calculateEquivalentCoordinate(int x, int y) {
        return new Coordinate( botLeft.getLatitude() + (y * ySquareSpan), botLeft.getLongitude() + (x * xSquareSpan));
    }

    public int[] calculateEquivalentGridCell(Coordinate c) {
        return new int[]{
                (int) Math.floor(((c.getLongitude() - botLeft.getLongitude()) / (X_SPAN)) * xSteps),
                (int) Math.floor(((c.getLatitude() - botLeft.getLatitude()) / (Y_SPAN)) * ySteps)
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

    public AgentProgrammed getSupervisor() {
        return supervisor;
    }

    public void setSupervisor(AgentProgrammed supervisor) {
        this.supervisor = supervisor;
    }
}
