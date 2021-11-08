package server.controller;

import server.Simulator;
import server.model.Agent;

import java.util.Arrays;
import java.util.stream.IntStream;

import static java.lang.Thread.sleep;

public class MessageController extends AbstractController {
    private final int STEPS_PER_CHECK = 20;
    private final double MIN_STEP = 0.0001;
    private final double LENGTH_WIDTH = 0.002;

    public MessageController(Simulator simulator) {
        super(simulator, AgentController.class.getName());
    }

    public boolean send(int[][] snapShot) {
        boolean hasOne = false;
        for(int[] line : snapShot) {
            if (IntStream.of(line).anyMatch(x -> x == 1)) {
                hasOne = true;
                break;
            }
        }

        if (hasOne) {
            System.out.println("=========================================================");
            for(int[] line : snapShot) {
                System.out.println(Arrays.toString(line));
            }
            System.out.println();
            System.out.println();
            System.out.println();
            return true;
        }
        return false;
    }

    public boolean step(Agent agent) {
        if (agent.incrementAndCheck(STEPS_PER_CHECK)) {
            // If the agent is ready to send (has waited long enough):
            return send(agent.snapShot(MIN_STEP, LENGTH_WIDTH));
            // Take a snapshot and send it thorough the message controller (where to go tbd)
            //return true;
        }
        return false;
    }
}
