package server.model.agents;
import com.mysql.jdbc.log.Log;
import server.Simulator;
import server.model.Coordinate;
import server.model.target.AdjustableTarget;
import server.model.target.Target;
import server.model.task.Task;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Here is where the user should program agent behaviour to be run on each programmed agent
 * @author William Hunt
 */
public abstract class AgentProgrammer {
    private final transient Logger LOGGER = Logger.getLogger(AgentVirtual.class.getName());
    ProgrammerHandler a;
    public AgentProgrammer(ProgrammerHandler programmerHandler) {
        a = programmerHandler;
    }

    /***
     * The setup function. Called once, when the agent is first called (not when it is created).
     * This would be a good place to make the agent randomly decide if it should be a leader, if desired
     */
    public abstract void setup();

    /***
     * Called at every time step (currently 200ms)
     */
    public abstract void step();

    public abstract void onComplete(Coordinate coords);

    /** To override the default homing condition, you can create your own
     * You can also create you own override to the goHome condition depending on the route you want the agent to take
     * This method, though, allows you to define when the agent has reached home (e.g. in comm range, in contact, etc)
     * */
    public boolean getHomingCondition() {
        return (a.getPosition().getDistance(a.getHome()) < (a.getCommunicationRange() * 0.75));
    }

    /**
     * Needed to handle custom messages that we have added
     * @param opCode A code you can use to specify what type of message
     * @param payload The data you want to send with the message
     */
    public abstract void onMessageReceived(String opCode, String payload);
}
