package server.model;

import server.Simulator;
import server.model.agents.Agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;

public class Sensor {
    private static final Logger LOGGER = Logger.getLogger(Sensor.class.getName());
    private Simulator simulator;

    public Sensor(Simulator simulator){
        this.simulator = simulator;
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
}
