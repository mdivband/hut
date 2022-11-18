package server.model.agents;

import server.Simulator;
import server.model.Coordinate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class LearningAllocator {
    protected List<AgentProgrammed> subordinates;
    protected AgentProgrammed supervisor;
    protected AgentProgrammed agent;


    protected int maxReward;
    private Coordinate botLeft;
    private Coordinate topRight;
    protected int xSteps;
    protected int ySteps;
    //private double X_SPAN = 0.01;
    //private double Y_SPAN = 0.006;
    private double X_SPAN = 0.015;
    private double Y_SPAN = 0.009;
    private double xSquareSpan;
    private double ySquareSpan;
    private float cellWidth;

    protected int counter;
    private int level; // level 0 is a bottom level; raising as we move up
    private int bestRwd = 0;

    public LearningAllocator(AgentProgrammed agent) {
        this.agent = agent;
    }

    public void setup() {
        subordinates = new ArrayList<>();
    }

    public void reset() {
        updateBounds(agent.getCoordinate());

    }

    public abstract void complete();

    public abstract void step();

    public void step(float jointReward) {
        System.out.println("NOT IMPLEMENTED");
    }

    public void randStep() {

        System.out.println(subordinates);
        for (AgentProgrammed hero : subordinates) {
            int[] heroCell = new int[]{((TensorRLearner) hero.getProgrammerHandler().getAgentProgrammer().getLearningAllocator()).getxCell(),
                    ((TensorRLearner) hero.getProgrammerHandler().getAgentProgrammer().getLearningAllocator()).getyCell()};

            int best = Simulator.instance.getRandom().nextInt(5);
            System.out.println(hero.getId() + " -> " + best);
            if (best == 0) {
                // No move
                if (heroCell[0] > 5 || heroCell[0] < 0 || heroCell[1] > 5 || heroCell[1] < 0) {
                    return;
                }
                hero.programmerHandler.manualSetTask(calculateEquivalentCoordinate(heroCell[0], heroCell[1]));
                ((TensorRLearner) hero.programmerHandler.getAgentProgrammer().getLearningAllocator()).setxCell(heroCell[0]);
                ((TensorRLearner) hero.programmerHandler.getAgentProgrammer().getLearningAllocator()).setyCell(heroCell[1]);
            } else if (best == 1) {
                if (heroCell[0] > 5 || heroCell[0] < 0 || heroCell[1] + 1 > 5 || heroCell[1] + 1 < 0) {
                    return;
                }
                hero.programmerHandler.manualSetTask(calculateEquivalentCoordinate(heroCell[0], heroCell[1] + 1));
                ((TensorRLearner) hero.programmerHandler.getAgentProgrammer().getLearningAllocator()).setxCell(heroCell[0]);
                ((TensorRLearner) hero.programmerHandler.getAgentProgrammer().getLearningAllocator()).setyCell(heroCell[1] + 1);
            } else if (best == 2) {
                if (heroCell[0] > 5 || heroCell[0] < 0 || heroCell[1] - 1 > 5 || heroCell[1] - 1 < 0) {
                    return;
                }
                hero.programmerHandler.manualSetTask(calculateEquivalentCoordinate(heroCell[0], heroCell[1] - 1));
                ((TensorRLearner) hero.programmerHandler.getAgentProgrammer().getLearningAllocator()).setxCell(heroCell[0]);
                ((TensorRLearner) hero.programmerHandler.getAgentProgrammer().getLearningAllocator()).setyCell(heroCell[1] - 1);
            } else if (best == 3) {
                if (heroCell[0] + 1 > 5 || heroCell[0] + 1 < 0 || heroCell[1] > 5 || heroCell[1] < 0) {
                    return;
                }
                hero.programmerHandler.manualSetTask(calculateEquivalentCoordinate(heroCell[0] + 1, heroCell[1]));
                ((TensorRLearner) hero.programmerHandler.getAgentProgrammer().getLearningAllocator()).setxCell(heroCell[0] + 1);
                ((TensorRLearner) hero.programmerHandler.getAgentProgrammer().getLearningAllocator()).setyCell(heroCell[1]);
            } else if (best == 4) {
                if (heroCell[0] - 1 > 5 || heroCell[0] - 1 < 0 || heroCell[1] > 5 || heroCell[1] < 0) {
                    return;
                }
                hero.programmerHandler.manualSetTask(calculateEquivalentCoordinate(heroCell[0] - 1, heroCell[1]));
                ((TensorRLearner) hero.programmerHandler.getAgentProgrammer().getLearningAllocator()).setxCell(heroCell[0] - 1);
                ((TensorRLearner) hero.programmerHandler.getAgentProgrammer().getLearningAllocator()).setyCell(heroCell[1]);
            }
            hero.programmerHandler.step();
        }
    }

    public void updateBounds(Coordinate position) {
        double topBound = position.getLatitude() + ((Math.pow(2, level-1) * Y_SPAN) / 2);
        double botBound = position.getLatitude() - ((Math.pow(2, level-1) * Y_SPAN) / 2);
        double rightBound = position.getLongitude() + ((Math.pow(2, level-1) * X_SPAN) / 2);
        double leftBound = position.getLongitude() - ((Math.pow(2, level-1) * X_SPAN) / 2);

        botLeft = new Coordinate(botBound, leftBound);
        topRight = new Coordinate(topBound, rightBound);

        xSquareSpan = (Math.pow(2, level-1) * X_SPAN) / xSteps;
        ySquareSpan = (Math.pow(2, level-1) * Y_SPAN) / ySteps;
        maxReward = xSteps * ySteps;
        cellWidth = (float) ((xSquareSpan * 111111));

        //Simulator.instance.getState().getMarkers().removeIf(m -> m.contains(supervisor.getId()));
        Simulator.instance.getState().getMarkers().removeIf(m -> m.contains(agent.getId()));

        double bot = botLeft.getLatitude();
        double top = topRight.getLatitude();
        double left = botLeft.getLongitude();
        double right = topRight.getLongitude();
        String botLine = agent.getId()+"bot,"+",polyline,"
                +bot+","+left+","+bot+","+right;
        String rightLine = agent.getId()+"right,"+",polyline,"
                +bot+","+right+","+top+","+right;
        String topLine = agent.getId()+"top,"+",polyline,"
                +top+","+right+","+top+","+left;
        String leftLine = agent.getId()+"left,"+",polyline,"
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

    public float calculateGridReward() {
        int numPointsCovered = 0;
        for (int i = 0; i < xSteps; i++) {
            for (int j = 0; j < ySteps; j++) {
                for (AgentProgrammed a : subordinates) {
                    int[] cell = calculateEquivalentGridCell(a.getCoordinate());
                    if (cell[0] - 1 <= i && cell[0] + 1 >= i && cell[1] - 1 <= j && cell[1] + 1 >= j) {
                        numPointsCovered++;
                        break;
                    }
                }
            }
        }

        /*
        if (numPointsCovered > bestRwd) {
            bestRwd = numPointsCovered;
            System.out.println("==============");
            outputAgentPositions();
            System.out.println("rw = " + numPointsCovered);
            System.out.println();
        }


         */


        return numPointsCovered;
    }

    public float calculatetwobytwoReward() {
        int points = 0;
        for (AgentProgrammed a : subordinates) {
            int[] cell = calculateEquivalentGridCell(a.getCoordinate());
            if (cell[0] < 8 && cell[1] < 8) {
                points++;
                break;
            }
        }

        for (AgentProgrammed a : subordinates) {
            int[] cell = calculateEquivalentGridCell(a.getCoordinate());
            if (cell[0] < 8 && cell[1] >= 8) {
                points++;
                break;
            }
        }

        for (AgentProgrammed a : subordinates) {
            int[] cell = calculateEquivalentGridCell(a.getCoordinate());
            if (cell[0] >= 8 && cell[1] < 8) {
                points++;
                break;
            }
        }

        for (AgentProgrammed a : subordinates) {
            int[] cell = calculateEquivalentGridCell(a.getCoordinate());
            if (cell[0] >= 8 && cell[1] >= 8) {
                points++;
                break;
            }
        }

        return points * 64;

    }

    public float calculateGridRewardWithPunishment() {
        int numPointsCovered = 0;
        for (int i = 0; i < xSteps; i++) {
            for (int j = 0; j < ySteps; j++) {
                boolean here = false;
                for (AgentProgrammed a : subordinates) {
                    int[] cell = calculateEquivalentGridCell(a.getCoordinate());
                    if (cell[0] - 4 <= i && cell[0] + 4 >= i && cell[1] - 4 <= j && cell[1] + 4 >= j) {
                        if (!here) {
                            numPointsCovered++;
                            here = true;
                        } else {
                            numPointsCovered--;
                        }
                    }
                }
            }
        }
        return numPointsCovered;
    }

    public void outputAgentPositions() {
        System.out.println("Positions:");

        for (int i = 0; i < xSteps; i++) {
            char[] line = new char[16];
            for (int j = 0; j < ySteps; j++) {
                boolean here = false;
                for (AgentProgrammed a : subordinates) {
                    if (calculateEquivalentGridCell(a.getCoordinate())[0] == j && calculateEquivalentGridCell(a.getCoordinate())[1] == i) {
                        here = true;
                        break;
                    }
                }
                if (here) {
                    line[j] = 'X';
                } else {
                    line[j] = '-';
                }
            }
            System.out.println(String.valueOf(line));
        }
    }

    public Coordinate calculateEquivalentCoordinate(int x, int y) {
        return new Coordinate( botLeft.getLatitude() + (y * ySquareSpan) + (ySquareSpan / 2), botLeft.getLongitude() + (x * xSquareSpan) + (xSquareSpan / 2));
    }

    public Coordinate calculateEquivalentCoordinate(Coordinate centre, int x, int y) {
        return new Coordinate( (centre.getLatitude() - ((ySteps * ySquareSpan) / 2)) + (y * ySquareSpan), (centre.getLongitude() - ((xSteps * xSquareSpan) / 2)) + (x * xSquareSpan));
    }

    public int[] calculateEquivalentGridCell(Coordinate c) {
        c = new Coordinate(c.getLatitude() - (ySquareSpan / 2), c.getLongitude() - (xSquareSpan / 2));
        //System.out.println(agent.getId());
        //System.out.println(c.getLongitude() + " - " +  botLeft.getLongitude() + " = " + (c.getLongitude() - botLeft.getLongitude())
        //        + ", / " + (level * X_SPAN) + " = " + ((c.getLongitude() - botLeft.getLongitude()) / (level * X_SPAN))
        //        + " * " + xSteps + " = " + Math.round(((c.getLongitude() - botLeft.getLongitude()) / (level * X_SPAN)) * xSteps));
        //System.out.println(c.getLatitude() + " - " +  botLeft.getLatitude() + " = " + (c.getLatitude() - botLeft.getLatitude())
        //        + ", / " + (level * Y_SPAN) + " = " + ((c.getLatitude() - botLeft.getLatitude()) / (level * Y_SPAN))
        //        + " * " + ySteps + " = " + Math.round(((c.getLatitude() - botLeft.getLatitude()) / (level * Y_SPAN)) * ySteps));

        return new int[]{
                (int) Math.round(((c.getLongitude() - botLeft.getLongitude()) / (Math.pow(2, level-1) * X_SPAN)) * xSteps),
                (int) Math.round(((c.getLatitude() - botLeft.getLatitude()) / (Math.pow(2, level-1) * Y_SPAN)) * ySteps)
        };
    }

    public float getCellWidth() {
        return cellWidth;
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

    public void clearAssociations() {
        subordinates.clear();
        supervisor = null;
    }

    public void setLevel(int l) {
        this.level = l;
        if (l == 0) {
            agent.getProgrammerHandler().setVisual("ghost");
        } else if (l == 1) {
            agent.getProgrammerHandler().setVisual("standard");
        } else if (l == 2) {
            agent.getProgrammerHandler().setVisual("leader");
        }
        updateBounds(agent.getCoordinate());
    }

    public int getLevel() {
        return level;
    }

    /**
     * Recursively calls itself to check that each agent in the tree below is stopped
     * @return
     */
    public boolean readyBelow() {
        return agent.isStopped() && subordinates.stream().allMatch(a -> a.programmerHandler.getAgentProgrammer().getLearningAllocator().readyBelow());
    }

    protected void tempPrintBounds() {
        System.out.println("bl = " + botLeft + ", tr = " + topRight + ", xss=" + xSquareSpan);
    }

    public Coordinate getBotLeft() {
        return botLeft;
    }

}
