package com.dji.hut_controller.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import org.junit.Test;

import static org.junit.Assert.*;

public class MissionTest {

    private static final String missionID = "Example";
    private static final Coordinates coordinates = new Coordinates(0.1f, -0.1f, 10f);

    private JsonObject createTestJsonObject() {
        JsonArray coordArray = new JsonArray();
        coordArray.add(coordinates.toJSONObject());
        JsonObject object = new JsonObject();
        object.addProperty("ID", missionID);
        object.add("Coordinates", coordArray);
        return object;
    }

    @Test
    public void fromJSONObject_success() {
        JsonObject object = createTestJsonObject();
        Mission mission = Mission.fromJSONObject(object);
        assertEquals(mission.getId(), missionID);
        assertEquals(mission.getCoordinates(), coordinates);
    }

    @Test(expected = JsonParseException.class)
    public void fromJSONObject_missingID() {
        JsonObject object = createTestJsonObject();
        object.remove("ID");
        Mission.fromJSONObject(object);
    }

    @Test(expected = JsonParseException.class)
    public void fromJSONObject_missingCoordinates() {
        JsonObject object = createTestJsonObject();
        object.remove("Coordinates");
        Mission.fromJSONObject(object);
    }

    @Test(expected = JsonParseException.class)
    public void fromJSONObject_emptyCoordinateArray() {
        JsonArray coordArray = new JsonArray();
        JsonObject object = new JsonObject();
        object.addProperty("ID", missionID);
        object.add("Coordinates", coordArray);
        Mission.fromJSONObject(object);
    }

}