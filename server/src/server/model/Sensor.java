package server.model;

import server.Simulator;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class Sensor {
    private final Logger LOGGER;
    private Simulator simulator;

    public Sensor(Simulator simulator, Logger LOGGER){
        this.simulator = simulator;
        this.LOGGER = LOGGER;
    }

    /**
     * Check the distance between a specific agent and all other agents
     * @return neighbours - List of all agents within sensingRadius of the
     * specified agent
     */
    public List<Agent> senseNeighbours(Agent agent, Double sensingRadius){
        List<Agent> neighbours = new ArrayList<>();
        for (Agent neighbour : this.simulator.getState().getAgents()){
            if (neighbour != agent &&
                    agent.getCoordinate().getDistance(neighbour.getCoordinate()) <= sensingRadius){
                neighbours.add(neighbour);
            }
        }
        return neighbours;
    }

    public Double[] senseWind(){
        Double[] wind = new Double[2];
        wind[0] = this.simulator.getState().getWindSpeed();
        wind[1] = this.simulator.getState().getWindHeading();
        return wind;
    }
}
