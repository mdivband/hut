package com.dji.hut_controller.model;

import com.google.gson.JsonObject;

/**
 * Created by uav on 04/07/2016.
 */
public class Coordinates {
    private double latitude;
    private double longitude;
    private float altitude;

    public Coordinates (double latitude, double longitude, float altitude){
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitude = altitude;
    }

    public Coordinates(JsonObject jsonObject) {
        this.latitude = Double.parseDouble(jsonObject.get("Latitude").getAsString());
        this.longitude = Double.parseDouble(jsonObject.get("Longitude").getAsString());
        if(jsonObject.has("Altitude"))
            this.altitude = Float.parseFloat(jsonObject.get("Altitude").getAsString());
    }

    public static boolean isValid(double latitude, double longitude) {
        return (latitude > -90 && latitude < 90 && longitude > -180 && longitude < 180) && (latitude != 0f && longitude != 0f);
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public float getAltitude() {
        return altitude;
    }

    public void setAltitude(float altitude) {
        this.altitude = altitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public JsonObject toJSONObject(){
        JsonObject tbr = new JsonObject();
        tbr.addProperty("Latitude", latitude);
        tbr.addProperty("Longitude", longitude);
        tbr.addProperty("Altitude", altitude);
        return tbr;
    }

    public boolean setFromJson(JsonObject coordinates){
        try{
            this.latitude = Double.parseDouble(coordinates.get("Latitude").getAsString());
            this.longitude = Double.parseDouble(coordinates.get("Longitude").getAsString());
        }catch (Exception e){
            return false;
        }

        return true;
    }

    public String toString(){
        return latitude + " " + longitude + " " + altitude;
    }

    public boolean equals(Object other){
        if(!(other instanceof Coordinates)){
            return false;
        }
        if(other == this){
            return true;
        }

        Coordinates otherCoordinates = (Coordinates) other;
        return this.altitude == otherCoordinates.getAltitude() && this.longitude == otherCoordinates.getLongitude() && this.latitude == otherCoordinates.getLatitude();
    }
}
