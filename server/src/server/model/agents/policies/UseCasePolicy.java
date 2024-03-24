package server.model.agents.policies;
import server.Simulator;
import server.model.Coordinate;
import server.model.agents.AgentProgrammer;
import server.model.agents.AgentVirtual;
import server.model.agents.ProgrammerHandler;
import server.model.task.Task;

import java.util.List;
import java.util.logging.Logger;

/**
 * Here is where the user should program agent behaviour to be run on each programmed agent
 * @author William Hunt
 */
public class UseCasePolicy extends AgentProgrammer {
    private final transient Logger LOGGER = Logger.getLogger(AgentVirtual.class.getName());
    ProgrammerHandler a;

    public UseCasePolicy(ProgrammerHandler programmerHandler) {
        super(programmerHandler);
        a = programmerHandler;
    }

    // These are example member variables
    private int dupeCounter = 0;
    private int dupeLimit;

    /***
     * The setup function. Called once, when the agent is first called (not when it is created).
     * This would be a good place to make the agent randomly decide if it should be a leader, if desired
     */
    public void setup() {
        if (a.isHub()) {
            LOGGER.severe("Sv: HUB " + a.agent.getId() + " assigned leadership and hub status");
            a.setLeader(true);
        } else {
            a.setLeader(true);
            // For usecase, make them trucks
            a.agent.setMarker("TruckMarker");
        }
        // Sets a random wait interval from 5-15
        dupeLimit = (int) Math.floor((a.getNextRandomDouble() * 10) + 5);
        LOGGER.severe("---- and set timeout to " + dupeLimit);

    }

    /***
     * Called at every time step (currently 200ms)
     */
    public void step(){
        if (a.isGoingHome()) {
            a.followRoute();
        } else if (a.getTasks().size() == 0 && a.getCompletedTasks().size() == 0) {
            // Here we have the problem that tasks is always emtpy.
            // WAIT; Only begin executing if we have tasks added now (so they don't fly off at the start)
            // if outide commuincation range of the hub, go home

        } else {
            if (a.isStopped()) {
                if (a.getTasks().size() > 0) {
                    // For usecase
                    List<Coordinate> task = a.getHighestPriorityNearestTask();
                    // For res study
                    //List<Coordinate> task = a.getNearestEmptyTask();

                    if (task != null) {
                        a.setTask(task);
                        a.resume();
                    }
                    // ELSE wait
                }
                // ELSE wait

            } else {
                if (dupeCounter < dupeLimit) {
                    a.followRoute();
                    dupeCounter += 1;
                } else {
                    dupeCounter = 0;
                    if (a.checkForDuplicateAssignment()) {
                        a.cancel();
                        a.stop();
                    }
                }
            }
        }
    }

    public void onComplete(Coordinate c) {
        List<Task> tasksHere = Simulator.instance.getTaskController().getAllTasksAt(c);
        tasksHere.forEach(t -> {
            double prio = t.getPriority();
            LOGGER.info(String.format("%s; APCMP; Programmed agent completed task (x, y, priority); %s; %s; %s",
                    Simulator.instance.getState().getTime(), c.getLatitude(), c.getLongitude(), prio));

            // For usecase study
            String id = Simulator.instance.getState().getPendingMap().get(c);
            Simulator.instance.getState().addToHandledTargets(id);

            a.goHome();
        });


    }

    /** To override the default homing condition, you can create your own
     * You can also create you own override to the goHome condition depending on the route you want the agent to take
     * This method, though, allows you to define when the agent has reached home (e.g. in comm range, in contact, etc)
     * */
    public boolean getHomingCondition() {
        return (a.getPosition().getDistance(a.getHome()) < 5);
    }



    /**
     * Needed to handle custom messages that we have added
     * @param opCode A code you can use to specify what type of message
     * @param payload The data you want to send with the message
     */
    public void onMessageReceived(String opCode, String payload) {

    }
}
