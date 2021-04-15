package server.model;

import com.google.gson.*;
import server.Allocator;
import server.Simulator;
import server.model.hazard.Hazard;
import server.model.target.Target;
import server.model.task.Task;
import tool.GsonUtils;

import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

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
