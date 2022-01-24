package server.model.target;

import server.model.Coordinate;

public class AdjustableTarget extends Target {

    //private int status = 0;
    private final boolean real;
    private String lowResFileName = "";
    private String highResFileName = "";

    public AdjustableTarget(String id, Coordinate coordinate, boolean isReal) {
        super(id, coordinate, Target.ADJUSTABLE);
        this.real = isReal;
    }

    public boolean isReal() {
        return real;
    }

    public void setFilenames(String lowRes, String highRes) {
        lowResFileName = lowRes;
        highResFileName = highRes;
    }

    public String getLowResFileName() {
        return lowResFileName;
    }

    public String getHighResFileName() {
        return highResFileName;
    }
}
