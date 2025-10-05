package server.model.fire;

import server.model.Coordinate;
import server.model.MObject;

/**
 * Represents a single fire event in the simulation.
 */
public class Fire extends MObject {

    private double intensity;
    private boolean visible;

    public Fire(String id, Coordinate coordinate) {
        super(id, coordinate);
        this.intensity = 1.0; // Default value
        this.visible = true;  // Fires are visible by default
    }

    public double getIntensity() {
        return intensity;
    }

    public void setIntensity(double intensity) {
        this.intensity = intensity;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }
}