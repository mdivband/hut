package server.model.target;

import server.model.Coordinate;

public class AdjustableTarget extends Target {

    //private int status = 0;
    private final String correctClassification;
    private String lowResFileName = "";
    private String highResFileName = "";

    public AdjustableTarget(String id, Coordinate coordinate, String correctClassification) {
        super(id, coordinate, Target.ADJUSTABLE);
        this.correctClassification = correctClassification;
    }

    public String getCorrectClassification() {
        return correctClassification;
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
