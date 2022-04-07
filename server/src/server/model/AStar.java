package server.model;

import server.Simulator;

import java.util.*;

public class AStar {
    private List<Coordinate> hazards;


    public AStar(List<Coordinate> hazards) {
        this.hazards = hazards;
    }

    /**
     * Based heavily on pseudocode from wikipedia https://en.wikipedia.org/wiki/A*_search_algorithm
     * @param start
     * @param destination
     * @return
     */
    public ArrayList<Coordinate> compute(Coordinate start, Coordinate destination) {
        //String destMarker = "circle"+","+destination.getLatitude()+","+destination.getLongitude()+","+10;
        //Simulator.instance.getState().getMarkers().add(destMarker);

        List<Coordinate> openSet = new ArrayList<>();
        //openSet.add(start);
        HashMap<Coordinate, Coordinate> cameFrom = new HashMap<>();
        HashMap<Coordinate, Double> gScore = new HashMap<>();
        gScore.put(start, 0.0);
        // We don't use fscore map, we just calculate it as needed
        List<Coordinate> visitedNodes = new ArrayList<>();

        // Initially, add all possible starts
        for (int i=0; i<8; i++) {
            Coordinate thisCoord = start.getCoordinate(100, i * (Math.PI / 4));
            if (hazards.stream().noneMatch(h -> h.getDistance(thisCoord) < 50)) {
                cameFrom.put(thisCoord, start);
                gScore.put(thisCoord, 100.0);
                openSet.add(thisCoord);
                visitedNodes.add(start);
            }
        }

        while (!openSet.isEmpty()) {
            double minVal = 999999999;
            Coordinate current = null;
            for (Coordinate c : openSet) {
                // score = g(n) + h(n)
                double score = gScore.get(c) + c.getDistance(destination);
                if (score < minVal) {
                    minVal = score;
                    current = c;
                }
            }

            /*
            System.out.println("Selected " + current + " h = " + current.getDistance(destination));
            List<String> markers = new ArrayList<>();
            String destMarker = "circle"+","+destination.getLatitude()+","+destination.getLongitude()+","+10;
            markers.add(destMarker);
            for (Coordinate c : openSet) {
                String marker = "circle"+","+c.getLatitude()+","+c.getLongitude()+","+2;
                markers.add(marker);
            }
            Simulator.instance.getState().getMarkers().clear();
            Simulator.instance.getState().getMarkers().addAll(markers);
            String currentMarker = "circle"+","+current.getLatitude()+","+current.getLongitude()+","+5;
            Simulator.instance.getState().getMarkers().add(currentMarker);
             */

            if (reached(current, destination)) {
                return reconstructPath(cameFrom, current, destination);
            }

            double theta;
            if (cameFrom.containsKey(current)) {
                theta = cameFrom.get(current).getAngle(current);
            } else {
                theta = 0;
            }

            //double theta = cameFrom.get(current).getAngle(current);
            Coordinate leftTurn = current.getCoordinate(50, theta - (Math.PI / 4));
            Coordinate straight = current.getCoordinate(50, theta);
            Coordinate rightTurn = current.getCoordinate(50, theta + (Math.PI / 4));
            List<Coordinate> coords = new ArrayList<>();
            coords.add(leftTurn);
            coords.add(straight);
            coords.add(rightTurn);
            double tentativeGScore = gScore.get(current) + 50;
            // For each neighbour
            for (Coordinate c : coords) {
                //String nMarker = "circle"+","+c.getLatitude()+","+c.getLongitude()+","+1;
                //Simulator.instance.getState().getMarkers().add(nMarker);
                if (!visitedNodes.contains(c) && hazards.stream().noneMatch(h -> h.getDistance(c) < 50) && openSet.stream().noneMatch(e -> e.getDistance(c) < 5)) {
                    if (!gScore.containsKey(c) || gScore.get(c) > tentativeGScore) {
                        cameFrom.put(c, current);
                        gScore.put(c, tentativeGScore);
                        openSet.add(c);
                    }
                }
            }
            visitedNodes.add(current);
            openSet.remove(current);
        }
        return null;
    }

    private boolean reached(Coordinate current, Coordinate destination) {
        return current.getDistance(destination) < 60;
    }

    private ArrayList<Coordinate> reconstructPath(HashMap<Coordinate, Coordinate> cameFrom, Coordinate current, Coordinate destination) {
        ArrayList<Coordinate> path = new ArrayList<>();
        path.add(destination);
        path.add(current);
        //Simulator.instance.getState().getMarkers().clear();
        while (cameFrom.containsKey(current)) {
            //String currentMarker = "circle"+","+current.getLatitude()+","+current.getLongitude()+","+5;
            //Simulator.instance.getState().getMarkers().add(currentMarker);
            current = cameFrom.get(current);
            path.add(current);  // Instead of prepend, just add in order and reverse after
        }
        path.remove(path.size() - 1);
        Collections.reverse(path);
        return path;
    }

}
