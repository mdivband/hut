package server.controller;

//import lsl.LSLLogger;
import server.Simulator;
import server.model.Coordinate;

import java.util.ArrayList;
import java.util.logging.Logger;

public class EpisodeController {
    private final Logger LOGGER = Logger.getLogger(Simulator.class.getName());
    //private final LSLLogger lslLogger = new LSLLogger();
    private final ArrayList<Episode> episodes;
    private Episode currentEpisode = null; // Important to start null; we ensure mainloop has to define time limit first
    private Long currentEpisodeStartTime = null;
    private boolean userHasClicked = false;
    private double triggerTime = -1;

    public EpisodeController() {
        episodes = new ArrayList<>();
    }

    public void addEpisode(int episodeLength, int episodeCooldown, int reviewPeriod, String agentPos, String targetPos, int numAgents, boolean isDegradationMatch, double degradationTime, String episodeCode, ArrayList<String> markers) {
        episodes.add(new Episode(episodeLength, episodeCooldown, reviewPeriod, agentPos, targetPos, numAgents, isDegradationMatch, degradationTime, episodeCode, markers));
    }

    public void incrementEpisode() {
        if (currentEpisode != null) {
            wipeMarkers(currentEpisode.markers);
        }
        currentEpisode = episodes.remove(0);
        currentEpisode.setEpisodeTimeLimit(currentEpisode.getEpisodeLength());//Simulator.instance.getState().getTime() + currentEpisode.getEpisodeLength());
        currentEpisodeStartTime = System.currentTimeMillis(); //Simulator.instance.getState().getTime();
        LOGGER.info(String.format("%s; NEWEP; New episode showing, code is (code); %s", Simulator.instance.getState().getTime(), currentEpisode.getEpisodeCode()));
        //lslLogger.logEventMarker(currentEpisode.getEpisodeCode());
        addMarkers(currentEpisode.markers);
    }

    public int getNumAgents() {
        return currentEpisode.getNumAgents();
    }

    public double getEpisodeTimeLimit() {
        return currentEpisode.getEpisodeTimeLimit();
    }

    public boolean hasStarted() {
        return currentEpisode != null;
    }

    public Coordinate getAgentCoord() {
        return convertEpisodeCoord(currentEpisode.getAgentPos());
    }

    public Coordinate getTargetCoord() {
        return convertEpisodeCoord(currentEpisode.getTargetPos());
    }

    public boolean isDegradationMatch() {
        return currentEpisode != null && currentEpisode.isDegradation;
    }

    public double peekDegradationTime() {
        return (currentEpisode != null && currentEpisode.isDegradation) ? currentEpisode.degradationTime : -1;
    }

    public double getTriggerTime() {
        return triggerTime;
    }

    public void setTriggerTime(double triggerTime) {
        System.out.println("Setting trigger time: " + triggerTime);
        this.triggerTime = triggerTime;
    }

    private Coordinate convertEpisodeCoord(String pos) {
        Coordinate centre = Simulator.instance.getState().getGameCentre();
        double latOffset = 0.008; // Adjust these values as needed
        double lngOffset = 0.024; // Adjust these values as needed
        return switch (pos) {
            case "TL" -> new Coordinate(centre.getLatitude() + latOffset, centre.getLongitude() - lngOffset);
            case "TR" -> new Coordinate(centre.getLatitude() + latOffset, centre.getLongitude() + lngOffset);
            case "BL" -> new Coordinate(centre.getLatitude() - latOffset, centre.getLongitude() - lngOffset);
            case "BR" -> new Coordinate(centre.getLatitude() - latOffset, centre.getLongitude() + lngOffset);
            case "T" -> new Coordinate(centre.getLatitude() + latOffset, centre.getLongitude());
            case "B" -> new Coordinate(centre.getLatitude() - latOffset, centre.getLongitude());
            case "L" -> new Coordinate(centre.getLatitude(), centre.getLongitude() - lngOffset);
            case "R" -> new Coordinate(centre.getLatitude(), centre.getLongitude() + lngOffset);
            default -> new Coordinate(centre.getLatitude(), centre.getLongitude());
        };
    }

    public void click(boolean status) {
        System.out.println("User clicked: " + status);

        boolean isDegradation = currentEpisode.isDegradation();
        double degradationTimeMillis = currentEpisode.degradationTime * 1000;
        double elapsedTimeSinceStart = System.currentTimeMillis() - currentEpisodeStartTime;

        boolean clickedAfterDegradation = elapsedTimeSinceStart >= degradationTimeMillis;
        boolean success = isDegradation && status && clickedAfterDegradation;

        // Reaction time should be 0 if no match OR if clicked too early
        double reactionTime = (isDegradation && clickedAfterDegradation) ? elapsedTimeSinceStart - degradationTimeMillis : 0;

        // Log whether the user clicked, and whether it was correct
        String clickString = (isDegradation ? "T" : "F") + (success ? "T" : "F");

        LOGGER.info(String.format(
                "%s; DGCLK; User clicked. Episode is/is not a degradation episode and so the user was with reaction time and the clickstring for oxysoft is (deg, usrclicked, success, numAgents, reactiontime, clickstring); %s; %s; %s; %s; %s; %s",
                Simulator.instance.getState().getTime(),
                isDegradation, status, success, getNumAgents(), reactionTime, clickString
        ));

        //lslLogger.logEventMarker("CL" + clickString);  // TODO: check if we should send Correct/Incorrect or nBackTrue/nBackFalse

        userHasClicked = true; // Always true when the user clicks, regardless of correctness
        //triggerTime = -1;  // Reset trigger time
        //Simulator.instance.getState().setEditMode(-2);
    }


    public boolean hasEpisodes() {
        return !episodes.isEmpty();
    }

    public double getEpisodeCooldownLimit() {
        return currentEpisode.getEpisodeCooldown();
    }

    public double getReviewPeriodLimit() {
        return currentEpisode.getReviewPeriod();
    }

    public void closeLogger() {
        //lslLogger.close();
    }

    public void logRest() {
        //lslLogger.logEventMarker("REST");
        if (!userHasClicked) {
            String clickString = (currentEpisode.isDegradation ? "T" : "F") + (!currentEpisode.isDegradation ? "T" : "F");
            LOGGER.info(String.format("%s; DGNOC; Deg not clicked. Episode is/is not a deg episidode and so the user was so the clickstring for oxysoft is (match, usrclicked, success, numAgents, clickstring); %s; %s; %s; %s; %s", Simulator.instance.getState().getTime(), currentEpisode.isDegradation, false, (!currentEpisode.isDegradation), getNumAgents(), clickString));
        }

        userHasClicked = false;
    }

    private void wipeMarkers(ArrayList<String> markers) {
        for (String marker : markers) {
            Simulator.instance.getState().removeMarker(marker);
        }
    }

    private void addMarkers(ArrayList<String> markers) {
        for (String marker : markers) {
            Simulator.instance.getState().addMarker(marker);
        }
    }

    public Integer peekNextEpisodeCooldown() {
        return episodes.get(0).episodeCooldown;
    }

    private class Episode {
        private int episodeLength;
        private int episodeCooldown;
        private int reviewPeriod; // New field for review period
        private String agentPos;
        private String targetPos;
        private int numAgents;
        private double episodeTimeLimit;
        private boolean isDegradation;
        private double degradationTime;
        private String episodeCode;
        private ArrayList<String> markers;

        public Episode(int episodeLength, int episodeCooldown, int reviewPeriod, String agentPos, String targetPos, int numAgents, boolean isDegradation, double degradationTime, String episodeCode, ArrayList<String> markers) {
            this.episodeLength = episodeLength;
            this.episodeCooldown = episodeCooldown;
            this.reviewPeriod = reviewPeriod; // Set reviewPeriod
            this.agentPos = agentPos;
            this.targetPos = targetPos;
            this.numAgents = numAgents;
            this.isDegradation = isDegradation;
            this.degradationTime = degradationTime;
            this.episodeCode = episodeCode;
            this.markers = markers;
        }

        public int getEpisodeLength() {
            return episodeLength;
        }

        public String getAgentPos() {
            return agentPos;
        }

        public String getTargetPos() {
            return targetPos;
        }

        public int getNumAgents() {
            return numAgents;
        }

        public double getEpisodeTimeLimit() {
            return episodeTimeLimit;
        }

        public void setEpisodeTimeLimit(double timeLimit) {
            episodeTimeLimit = timeLimit;
        }

        public double getEpisodeCooldown() {
            return episodeCooldown;
        }

        public int getReviewPeriod() {
            return reviewPeriod;
        }

        public boolean isDegradation() {
            return isDegradation;
        }

        public String getEpisodeCode() {
            return episodeCode;
        }
    }
}
