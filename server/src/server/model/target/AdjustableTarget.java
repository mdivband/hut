package server.model.target;

import server.Simulator;
import server.model.Coordinate;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class AdjustableTarget extends Target {

    //private int status = 0;
    private final boolean real;
    private List<String> data = new ArrayList<>();
    private String description = "";

    public AdjustableTarget(String id, Coordinate coordinate, boolean isReal) {
        super(id, coordinate, Target.ADJUSTABLE);
        this.real = isReal;
    }

    public boolean isReal() {
        return real;
    }

    public List<String> getData() {
        return data;
    }

    public void addData(String dataString) {
        data.add(dataString);
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String desc) {
        description = desc;
    }
}
