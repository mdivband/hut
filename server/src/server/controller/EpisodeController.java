package server.controller;

import lsl.LSLLogger;
import server.Simulator;
import server.model.Coordinate;
import server.model.State;

import java.util.ArrayList;
import java.util.logging.Logger;

public class EpisodeController {
    private final Logger LOGGER = Logger.getLogger(Simulator.class.getName());
    private final LSLLogger lslLogger = new LSLLogger();
    private final ArrayList<Episode> episodes;
    private Episode currentEpisode = null; // Important to start null; we ensure mainloop has to define time limit first
    private Double currentEpisodeStartTime = null;
    private boolean userHasClicked = false;

    public EpisodeController() {
        episodes = new ArrayList<>();
    }

    public void addEpisode(int episodeLength, int episodeCooldown, int reviewPeriod, String agentPos, String targetPos, int numAgents, boolean isNBackMatch, String episodeCode, ArrayList<String> markers) {
        episodes.add(new Episode(episodeLength, episodeCooldown, reviewPeriod, agentPos, targetPos, numAgents, isNBackMatch, episodeCode, markers));
    }

    public void incrementEpisode() {
        if (currentEpisode != null) {
            wipeMarkers(currentEpisode.markers);
        }
        currentEpisode = episodes.remove(0);
        currentEpisode.setEpisodeTimeLimit(currentEpisode.getEpisodeLength());//Simulator.instance.getState().getTime() + currentEpisode.getEpisodeLength());
        currentEpisodeStartTime = Simulator.instance.getState().getTime();
        LOGGER.info(String.format("%s; NEWEP; New episode showing, code is (code); %s", Simulator.instance.getState().getTime(), currentEpisode.getEpisodeCode()));
        lslLogger.logEventMarker(currentEpisode.getEpisodeCode());
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

    private Coordinate convertEpisodeCoord(String pos) {
        Coordinate centre = Simulator.instance.getState().getGameCentre();
        double latOffset = 0.015; // Adjust these values as needed
        double lngOffset = 0.05; // Adjust these values as needed
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
        boolean success = (status == currentEpisode.isNBackMatch);
        double reactionTime = Simulator.instance.getState().getTime() - currentEpisodeStartTime;
        String clickString = (currentEpisode.isNBackMatch ? "T" : "F") + (success ? "T" : "F");
        LOGGER.info(String.format("%s; NBCLK; NBack clicked. Episode is/is not a match and so the user was with reaction time so the clickstring for oxysoft is (match, usrclicked, success, numAgents, reactiontime, clickstring); %s; %s; %s; %s; %s; %s", Simulator.instance.getState().getTime(), currentEpisode.isNBackMatch, status, (status == currentEpisode.isNBackMatch), getNumAgents(), reactionTime, clickString));
        lslLogger.logEventMarker("CL"+clickString);  // TODO check if we should send Correct/Incorrect or nBackTrue/nBackFalse
        userHasClicked = true;
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
        lslLogger.close();
    }

    public void logRest() {
        lslLogger.logEventMarker("REST");
        if (!userHasClicked) {
            String clickString = (currentEpisode.isNBackMatch ? "T" : "F") + (!currentEpisode.isNBackMatch ? "T" : "F");
            LOGGER.info(String.format("%s; NBNOC; NBack not clicked. Episode is/is not a match and so the user was with reaction time so the clickstring for oxysoft is (match, usrclicked, success, numAgents, reactiontime, clickstring); %s; %s; %s; %s; %s; %s", Simulator.instance.getState().getTime(), currentEpisode.isNBackMatch, false, (!currentEpisode.isNBackMatch), getNumAgents(), currentEpisode.episodeLength, clickString));
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
        private boolean isNBackMatch;
        private String episodeCode;
        private ArrayList<String> markers;

        public Episode(int episodeLength, int episodeCooldown, int reviewPeriod, String agentPos, String targetPos, int numAgents, boolean isNBackMatch, String episodeCode, ArrayList<String> markers) {
            this.episodeLength = episodeLength;
            this.episodeCooldown = episodeCooldown;
            this.reviewPeriod = reviewPeriod; // Set reviewPeriod
            this.agentPos = agentPos;
            this.targetPos = targetPos;
            this.numAgents = numAgents;
            this.isNBackMatch = isNBackMatch;
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

        public boolean isNBackMatch() {
            return isNBackMatch;
        }

        public String getEpisodeCode() {
            return episodeCode;
        }
    }
}
