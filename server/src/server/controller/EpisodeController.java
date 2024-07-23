package server.controller;

import server.Simulator;
import server.model.Coordinate;
import server.model.State;

import java.util.ArrayList;

public class EpisodeController {
    private final ArrayList<Episode> episodes;
    private Episode currentEpisode = null; // Important to start null; we ensure mainloop has to define time limit first

    public EpisodeController() {
        episodes = new ArrayList<>();
    }

    public void addEpisode(int episodeLength, String agentPos, String targetPos, int numAgents) {
        episodes.add(new Episode(episodeLength, agentPos, targetPos, numAgents));
    }

    public void incrementEpisode() {
        currentEpisode = episodes.remove(0);
        currentEpisode.setEpisodeTimeLimit(currentEpisode.getEpisodeLength());//Simulator.instance.getState().getTime() + currentEpisode.getEpisodeLength());
    }

    public int getEpisodeLength() {
        return currentEpisode.getEpisodeLength();
    }

    public String getAgentPos() {
        return currentEpisode.getAgentPos();
    }

    public String getTargetPos() {
        return currentEpisode.getTargetPos();
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
    switch (pos) {
        case "TL":
            return new Coordinate(centre.getLatitude() + latOffset, centre.getLongitude() - lngOffset);
        case "TR":
            return new Coordinate(centre.getLatitude() + latOffset, centre.getLongitude() + lngOffset);
        case "BL":
            return new Coordinate(centre.getLatitude() - latOffset, centre.getLongitude() - lngOffset);
        case "BR":
            return new Coordinate(centre.getLatitude() - latOffset, centre.getLongitude() + lngOffset);
        case "T":
            return new Coordinate(centre.getLatitude() + latOffset, centre.getLongitude());
        case "B":
            return new Coordinate(centre.getLatitude() - latOffset, centre.getLongitude());
        case "L":
            return new Coordinate(centre.getLatitude(), centre.getLongitude() - lngOffset);
        case "R":
            return new Coordinate(centre.getLatitude(), centre.getLongitude() + lngOffset);
        default:
            return new Coordinate(centre.getLatitude(), centre.getLongitude());
    }
}


    private class Episode {
        private int episodeLength;
        private String agentPos;
        private String targetPos;
        private int numAgents;
        private double episodeTimeLimit;

        public Episode(int episodeLength, String agentPos, String targetPos, int numAgents) {
            this.episodeLength = episodeLength;
            this.agentPos = agentPos;
            this.targetPos = targetPos;
            this.numAgents = numAgents;
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
    }
}


