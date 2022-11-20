package server.model.agents;

import java.util.Arrays;

public class CommFrame {
    protected String agentID;
    protected int ttl;
    protected int xCell;
    protected int yCell;
    protected float[][] report;
    protected boolean sent = false;


    public CommFrame(String agentID, int ttl, int xCell, int yCell, float[][] report) {
        this.agentID = agentID;
        this.ttl = ttl;
        this.xCell = xCell;
        this.yCell = yCell;
        this.report = report;
    }

    /**
     * Duplicates this frame with TTL reduced by 1. Returns null if TTL would reach 0 for this new frame
     * @return
     */
    public CommFrame duplicateAndDecrement() {
        sent = true; // As we've duplicated this and sent it on, it means it's been handled, so let's mark this
        if (ttl > 1) {
            return new CommFrame(agentID, ttl-1, xCell, yCell, report);
        } else {
            return null;
        }
    }

    @Override
    public String toString() {
        return "CommFrame{" +
                "ID=" + agentID +
                ", ttl=" + ttl +
                ", xCell=" + xCell +
                ", yCell=" + yCell +
                ", report=" + Arrays.toString(report) +
                '}';
    }

}
