package server.model;

import com.google.gson.JsonObject;

import java.io.Serializable;
import java.util.List;

/**
 * The representation of a Coordinate (lat, lng)
 * @author Feng Wu
 */
/* Edited by Will */
public class Coordinate implements Serializable {

    private static final long serialVersionUID = 5561040348988016571L;
    protected double latitude;
    protected double longitude;

    public Coordinate(double lat, double lng) {
        set(lat, lng);
    }

    @Override
    public Coordinate clone() {
        return new Coordinate(latitude, longitude);
    }

    public void set(double lat, double lng) {
        latitude = lat;
        longitude = lng;
    }

    public double getLatitude() {
        return latitude;
    }

    public Coordinate setLatitude(double lat) {
        latitude = lat;
        return this;
    }

    public double getLongitude() {
        return longitude;
    }

    public Coordinate setLongitude(double lng) {
        longitude = lng;
        return this;
    }

    /**
     * Get angle from this coordinate to the given coordinate in radians
     * @return radian
     */
    public double getAngle(Coordinate coordinate) {
        double north_south_distance = (coordinate.getLatitude() - latitude) * 60.0 * 1852.0;
        double east_west_distance = Math.cos(latitude * Math.PI / 180.0) * (coordinate.getLongitude() - longitude) * 60.0 * 1852.0;
        return Math.atan2(north_south_distance, east_west_distance);
    }

    /**
     * Get distance to coordinate in meters.
     */
    public double getDistance(Coordinate coordinate) {
        final int R = 6371;

        double latDistance = Math.toRadians(this.latitude - coordinate.latitude);
        double lonDistance = Math.toRadians(this.longitude - coordinate.longitude);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(coordinate.latitude)) * Math.cos(Math.toRadians(this.latitude))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c * 1000;
    }

    /**
     * @param distance meter
     * @param angle    radian
     * @return c
     */
    public Coordinate getCoordinate(double distance, double angle) {
        double north_south_distance = distance * Math.sin(angle);
        double east_west_distance = distance * Math.cos(angle);
        double lat = latitude + north_south_distance / (60.0 * 1852.0);
        double lng = longitude + east_west_distance / (60.0 * 1852.0 * Math.cos(latitude * Math.PI / 180.0));
        return new Coordinate(lat, lng);
    }

    /**
     * Convert a LatLng position to cartesian coords.
     * @param lat0 - Latitude for aspect ratio (degrees) - should be in middle of area calculating coords for.
     * @return double array containing x, y
     */
    public double[] toCartesian(double lat0) {
        double x = 6371 * Math.toRadians(this.longitude) * Math.cos(Math.toRadians(lat0));
        double y = 6371 * Math.toRadians(this.latitude);
        return new double[]{x, y};
    }

    public static Coordinate fromCartesian(double x, double y, double lat0) {
        double lat = Math.toDegrees(y/6371);
        double lng = Math.toDegrees(x/(6371 * Math.cos(Math.toRadians(lat0))));
        return new Coordinate(lat, lng);
    }

    /**
     * Finds geometric centre of a given list of coordinates
     * @param coordinates
     * @return
     */
    public static Coordinate findCentre(List<Coordinate> coordinates) {
        double x = 0, y = 0, z = 0;
        for(Coordinate coordinate : coordinates) {
            double lat = Math.toRadians(coordinate.latitude);
            double lng = Math.toRadians(coordinate.longitude);
            x += Math.cos(lat) * Math.cos(lng);
            y += Math.cos(lat) * Math.sin(lng);
            z += Math.sin(lat);
        }
        x /= coordinates.size();
        y /= coordinates.size();
        z /= coordinates.size();
        double lng = Math.atan2(y, x);
        double hyp = Math.sqrt(x*x + y*y);
        double lat = Math.atan2(z, hyp);
        return new Coordinate(Math.toDegrees(lat), Math.toDegrees(lng));
    }

    @Override
    public String toString() {
        return latitude + "," + longitude;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof Coordinate) {
            Coordinate c = (Coordinate) o;
            return ((Double.compare(latitude, c.latitude) == 0) &&
                    (Double.compare(longitude, c.longitude) == 0));
        }
        return false;
    }

    @Override
    public int hashCode() {
        return (new Double(latitude).hashCode() ^ new Double(longitude).hashCode());
    }

    public JsonObject getJSON() {
        JsonObject tbr = new JsonObject();
        tbr.addProperty("Latitude", latitude);
        tbr.addProperty("Longitude", longitude);
        return tbr;
    }
}
