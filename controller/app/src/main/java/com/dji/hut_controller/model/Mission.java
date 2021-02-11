package com.dji.hut_controller.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

/**
 * Model object for missions.
 */
public class Mission {

    private String id;
    private String type;
    private Coordinates coordinates;

    public Mission(String id, Coordinates coordinates) {
        this.id = id;
        this.coordinates = coordinates;
    }

    public static Mission fromJSONObject(JsonObject jsonObject) {
        //Check fields
        if(!jsonObject.has("ID"))
            throw new JsonParseException("Mission JsonObject missing field ID");
        if(!jsonObject.has("Coordinates"))
            throw new JsonParseException("Mission JsonObject missing field Coordinates");

        JsonArray coordinateArray;
        //Check coordinate array non-empty
        if((coordinateArray = jsonObject.get("Coordinates").getAsJsonArray()).size() == 0)
            throw new JsonParseException("Mission JsonObject has empty Coordinates array");

        String id = jsonObject.get("ID").getAsString();
        Coordinates coordinates = new Coordinates(coordinateArray.get(0).getAsJsonObject());
        return new Mission(id, coordinates);
    }

    public String getId() {
        return id;
    }

    public Coordinates getCoordinates() {
        return coordinates;
    }

}
