package server.controller;

import server.Simulator;
import server.model.Coordinate;
import java.util.ArrayList;
import java.util.logging.Logger;

public class EpisodeController {
    private final Logger LOGGER = Logger.getLogger(Simulator.class.getName());
    private final ArrayList<Episode> episodes;
    private Episode currentEpisode = null;
    private Long currentEpisodeStartTime = null;
    private boolean userHasClicked = false;
    private double triggerTime = -1;

    public EpisodeController() {
        episodes = new ArrayList<>();
    }

    // NEW SIGNATURE: prevAgents included; colours/degColour optional (kept for logging/future use)
    public void addEpisode(
            int episodeLength, int episodeCooldown, int reviewPeriod,
            String agentPos, String targetPos, int numAgents,
            boolean isDegradationMatch, double degradationTime,
            String episodeCode, ArrayList<String> markers,
            Integer prevAgents, ArrayList<String> colours, String degColour
    ) {
        episodes.add(new Episode(
                episodeLength, episodeCooldown, reviewPeriod,
                agentPos, targetPos, numAgents,
                /* always treat as true, but keep original value: */ isDegradationMatch,
                degradationTime, episodeCode, markers,
                (prevAgents != null ? prevAgents : 0),
                colours, degColour
        ));
    }

    // Backward-compat overload (old callers)
    public void addEpisode(
            int episodeLength, int episodeCooldown, int reviewPeriod,
            String agentPos, String targetPos, int numAgents,
            boolean isDegradationMatch, double degradationTime,
            String episodeCode, ArrayList<String> markers
    ) {
        addEpisode(episodeLength, episodeCooldown, reviewPeriod, agentPos, targetPos, numAgents,
                isDegradationMatch, degradationTime, episodeCode, markers,
                /*prev*/ null, /*colours*/ null, /*degColour*/ null);
    }

    public void incrementEpisode() {
        if (currentEpisode != null) wipeMarkers(currentEpisode.markers);
        currentEpisode = episodes.remove(0);
        currentEpisode.setEpisodeTimeLimit(currentEpisode.getEpisodeLength());
        currentEpisodeStartTime = System.currentTimeMillis();
        LOGGER.info(String.format("%s; NEWEP; New episode (code,prev,N,degT); %s;%s;%s;%s",
                Simulator.instance.getState().getTime(),
                currentEpisode.getEpisodeCode(), currentEpisode.getPrevAgents(),
                currentEpisode.getNumAgents(), currentEpisode.getDegradationTime()));
        addMarkers(currentEpisode.markers);
    }

    public boolean hasEpisodes() { return !episodes.isEmpty(); }
    public boolean hasStarted() { return currentEpisode != null; }

    public int getNumAgents() { return currentEpisode.getNumAgents(); }
    public int getPrevAgents() { return currentEpisode.getPrevAgents(); }
    public String getEpisodeCode() { return currentEpisode.getEpisodeCode(); }

    public double getEpisodeTimeLimit() { return currentEpisode.getEpisodeTimeLimit(); }
    public void setTriggerTime(double t) { this.triggerTime = t; }
    public double getTriggerTime() { return triggerTime; }

    // Always-on degradation semantics: match == true; time from JSON
    public boolean isDegradationMatch() { return true; }
    public double peekDegradationTime() { return currentEpisode.getDegradationTime(); }
    public double getDegradationTime() { return currentEpisode.getDegradationTime(); }

    public Coordinate getAgentCoord() { return convertEpisodeCoord(currentEpisode.getAgentPos()); }
    public Coordinate getTargetCoord() { return convertEpisodeCoord(currentEpisode.getTargetPos()); }

    public Integer peekNextEpisodeCooldown() { return currentEpisode != null ? currentEpisode.getEpisodeCooldown() : 0; }
    public double getEpisodeCooldownLimit() { return 0; }     // not used in new flow
    public double getReviewPeriodLimit() { return 0; }        // not used in new flow

    public void closeLogger() { /* no-op for now */ }
    public void logRest() {
        if (!userHasClicked) {
            String clickString = "TT"; // always deg + not clicked => incorrect in old semantics
            LOGGER.info(String.format("%s; DGNOC; Deg not clicked (N,code); %s;%s",
                    Simulator.instance.getState().getTime(),
                    getNumAgents(), getEpisodeCode()));
        }
        userHasClicked = false;
    }

    // (kept from your version)
    private Coordinate convertEpisodeCoord(String pos) {
        Coordinate centre = Simulator.instance.getState().getGameCentre();
        double latOffset = 0.008, lngOffset = 0.024;
        return switch (pos == null ? "" : pos) {
            case "TL" -> new Coordinate(centre.getLatitude() + latOffset, centre.getLongitude() - lngOffset);
            case "TR" -> new Coordinate(centre.getLatitude() + latOffset, centre.getLongitude() + lngOffset);
            case "BL" -> new Coordinate(centre.getLatitude() - latOffset, centre.getLongitude() - lngOffset);
            case "BR" -> new Coordinate(centre.getLatitude() - latOffset, centre.getLongitude() + lngOffset);
            case "T"  -> new Coordinate(centre.getLatitude() + latOffset, centre.getLongitude());
            case "B"  -> new Coordinate(centre.getLatitude() - latOffset, centre.getLongitude());
            case "L"  -> new Coordinate(centre.getLatitude(), centre.getLongitude() - lngOffset);
            case "R"  -> new Coordinate(centre.getLatitude(), centre.getLongitude() + lngOffset);
            default   -> new Coordinate(centre.getLatitude(), centre.getLongitude());
        };
    }

    public void click(boolean status) {
        // (Kept, but simplified—every episode is a match.)
        boolean isDegradation = true;
        double degradationTimeMillis = currentEpisode.getDegradationTime() * 1000.0;
        double elapsed = System.currentTimeMillis() - currentEpisodeStartTime;
        boolean clickedAfter = elapsed >= degradationTimeMillis;
        boolean success = isDegradation && status && clickedAfter;
        double reactionTime = (isDegradation && clickedAfter) ? elapsed - degradationTimeMillis : 0;
        String clickString = (isDegradation ? "T" : "F") + (success ? "T" : "F");

        LOGGER.info(String.format("%s; DGCLK; Click (code,N,prev,success,rt,clickstr); %s;%s;%s;%s;%s;%s",
                Simulator.instance.getState().getTime(),
                getEpisodeCode(), getNumAgents(), getPrevAgents(), success, reactionTime, clickString));
        userHasClicked = true;
    }

    private void wipeMarkers(ArrayList<String> markers) {
        if (markers == null) return;
        for (String marker : markers) Simulator.instance.getState().removeMarker(marker);
    }
    private void addMarkers(ArrayList<String> markers) {
        if (markers == null) return;
        for (String marker : markers) Simulator.instance.getState().addMarker(marker);
    }

    public java.util.List<String> getColours() {
        return (currentEpisode != null && currentEpisode.colours != null)
                ? java.util.Collections.unmodifiableList(currentEpisode.colours)
                : java.util.Collections.emptyList();
    }
    public String getDegColour() {
        return (currentEpisode != null) ? currentEpisode.degColour : null;
    }


    // -------- Episode POJO --------
    private static class Episode {
        private final int episodeLength;
        private final int episodeCooldown;
        private final int reviewPeriod;
        private final String agentPos;
        private final String targetPos;
        private final int numAgents;
        private final boolean isDegradation;
        private final double degradationTime;
        private final String episodeCode;
        private final ArrayList<String> markers;
        private final int prevAgents;              // NEW
        private final ArrayList<String> colours;   // optional
        private final String degColour;            // optional

        private double episodeTimeLimit;

        Episode(int episodeLength, int episodeCooldown, int reviewPeriod,
                String agentPos, String targetPos, int numAgents,
                boolean isDegradation, double degradationTime,
                String episodeCode, ArrayList<String> markers,
                int prevAgents, ArrayList<String> colours, String degColour) {
            this.episodeLength = episodeLength;
            this.episodeCooldown = episodeCooldown;
            this.reviewPeriod = reviewPeriod;
            this.agentPos = agentPos;
            this.targetPos = targetPos;
            this.numAgents = numAgents;
            this.isDegradation = true; // force-on; keep JSON value for logging if needed
            this.degradationTime = degradationTime;
            this.episodeCode = episodeCode;
            this.markers = markers;
            this.prevAgents = prevAgents;
            this.colours = colours;
            this.degColour = degColour;
        }

        int getEpisodeLength() { return episodeLength; }
        String getAgentPos() { return agentPos; }
        String getTargetPos() { return targetPos; }
        int getNumAgents() { return numAgents; }
        boolean isDegradation() { return isDegradation; }
        double getDegradationTime() { return degradationTime; }
        String getEpisodeCode() { return episodeCode; }
        int getPrevAgents() { return prevAgents; }

        double getEpisodeTimeLimit() { return episodeTimeLimit; }
        void setEpisodeTimeLimit(double t) { episodeTimeLimit = t; }

        int getEpisodeCooldown() { return episodeCooldown; }
        int getReviewPeriod() { return reviewPeriod; }
    }
}
