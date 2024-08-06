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

    public EpisodeController() {
        episodes = new ArrayList<>();
    }

    public void addEpisode(int episodeLength, int episodeCooldown, String agentPos, String targetPos, int numAgents, boolean isNBackMatch, String episodeCode) {
        episodes.add(new Episode(episodeLength, episodeCooldown, agentPos, targetPos, numAgents, isNBackMatch, episodeCode));
    }

    public void incrementEpisode() {
        currentEpisode = episodes.remove(0);
        currentEpisode.setEpisodeTimeLimit(currentEpisode.getEpisodeLength());//Simulator.instance.getState().getTime() + currentEpisode.getEpisodeLength());
        currentEpisodeStartTime = Simulator.instance.getState().getTime();
        LOGGER.info(String.format("%s; NEWEP; New episode showing, code is (code); %s", Simulator.instance.getState().getTime(), currentEpisode.getEpisodeCode()));
        lslLogger.logEventMarker(currentEpisode.getEpisodeCode());

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

    // Method to return a tuple of lat, lng based on the agentPos (where "TL" is top left, etc)
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
        LOGGER.info(String.format("%s; NBCLK; NBack clicked. Episode is/is not a match and so the user was with reaction time so the clickstring for oxysoft is (match, success, reactiontime, clickstring); %s; %s; %s; %s", Simulator.instance.getState().getTime(), currentEpisode.isNBackMatch, success, reactionTime, clickString));
        lslLogger.logEventMarker("CL"+clickString);
    }

    public boolean hasEpisodes() {
        return !episodes.isEmpty();
    }

    public double getEpisodeCooldownLimit() {
        return currentEpisode.getEpisodeCooldown();
    }

    public void closeLogger() {
        lslLogger.close();
    }

    public void logRest() {
        lslLogger.logEventMarker("REST");
    }

    private class Episode {
        private int episodeLength;
        private int episodeCooldown;
        private String agentPos;
        private String targetPos;
        private int numAgents;
        private double episodeTimeLimit;
        private boolean isNBackMatch;
        private String episodeCode;

        public Episode(int episodeLength, int episodeCooldown, String agentPos, String targetPos, int numAgents, boolean isNBackMatch, String episodeCode) {
            this.episodeLength = episodeLength;
            this.episodeCooldown = episodeCooldown;
            this.agentPos = agentPos;
            this.targetPos = targetPos;
            this.numAgents = numAgents;
            this.isNBackMatch = isNBackMatch;
            this.episodeCode = episodeCode;
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

        public boolean isNBackMatch() {
            return isNBackMatch;
        }

        public String getEpisodeCode() {
            return episodeCode;
        }
    }
}


