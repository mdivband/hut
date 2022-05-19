package server.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;

public class AgentVirtual extends Agent {

    private transient Logger LOGGER = Logger.getLogger(AgentVirtual.class.getName());

    private transient Sensor sensor;

    public AgentVirtual(String id, Coordinate position, Sensor sensor) {
        super(id, position, true);
        this.sensor = sensor;
    }

    @Override
    public void step(Boolean flockingEnabled, Double avgAgentDropout) {
        super.step(flockingEnabled, avgAgentDropout);
        //Simulate things that would be done by a real drone
        if(!isTimedOut())
            heartbeat();
        int rnd = new Random().nextInt(360000);
        Boolean droppedOut = (rnd < avgAgentDropout);
        if (this.getTask() != null) {
            this.battery = this.battery > 0 ? this.battery - (windAdjustedBatteryConsumption*0.75) : 0;
        }
        if (this.battery == 0 || droppedOut) {
            this.setTimedOut(true);
        }
    }

    @Override
    void moveTowardsDestination() {
        if(!isStopped()) {
            this.adjustHeadingTowardsGoal();
            this.moveAlongHeading(this.windAdjustedSpeed);
        }
    }

    @Override
    void performFlocking() {
        //Align agent, if aligned then moved towards target
        if(!isStopped() && this.adjustFlockingHeading())
            this.moveAlongHeading(this.windAdjustedSpeed);
    }

    /**
     * Adjust heading of agent towards the heading that will take it towards its goal.
     * @return isAligned - Whether the agent is aligned or needs to continue rotating.
     */
    private boolean adjustHeadingTowardsGoal() {
        double lat1 = Math.toRadians(this.getCoordinate().getLatitude());
        double lng1 = Math.toRadians(this.getCoordinate().getLongitude());
        double lat2 = Math.toRadians(this.getCurrentDestination().getLatitude());
        double lng2 = Math.toRadians(this.getCurrentDestination().getLongitude());
        double dLng = (lng2 - lng1);
        double y = Math.sin(dLng) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1)
                * Math.cos(lat2) * Math.cos(dLng);
        double angleToGoal = Math.atan2(y, x);
        return adjustHeading(angleToGoal);
    }

    /**
     * Adjust heading of agent towards the average heading of its neighbours.
     * @return isAligned - Whether the agent is aligned or needs to continue rotating.
     */
    private boolean adjustFlockingHeading() {
        double xSum = 0.0;
        double ySum = 0.0;
        double magnitude = 0.0;
        double xAlign = 0.0;
        double yAlign = 0.0;
        double xRepulse = 0.0;
        double yRepulse = 0.0;
        double xAttract = 0.0;
        double yAttract = 0.0;
        double targetHeading = Math.toRadians(this.heading);

        List<Agent> neighbours = this.sensor.senseNeighbours(this, 50.0);

        if (neighbours.size() > 0) {

            for (Agent neighbour : neighbours) {
                double multiplier = 1;
                if (neighbour.getTask() != null) {
                    multiplier = 100;
                }
                else {
                    multiplier = 1;
                }
                double neighbourHeading = Math.toRadians(neighbour.getHeading());
                xSum += Math.cos(neighbourHeading) * multiplier;
                ySum += Math.sin(neighbourHeading) * multiplier;
            }
            magnitude = Math.sqrt(xSum * xSum + ySum * ySum);
            xAlign = xSum/magnitude;
            yAlign = ySum/magnitude;

            List<Agent> tooCloseNeighbours = this.sensor.senseNeighbours(this, 5.0);
            List<Agent> notTooClose = new ArrayList<>(neighbours);

            if (tooCloseNeighbours.size() > 0) {
                notTooClose.removeAll(tooCloseNeighbours);

                xSum = 0.0;
                ySum = 0.0;

                for(Agent neighbour : tooCloseNeighbours) {
                    double lat1 = Math.toRadians(this.getCoordinate().getLatitude());
                    double lng1 = Math.toRadians(this.getCoordinate().getLongitude());
                    double lat2 = Math.toRadians(neighbour.getCoordinate().getLatitude());
                    double lng2 = Math.toRadians(neighbour.getCoordinate().getLongitude());
                    double dLng = (lng2 - lng1);
                    ySum -= Math.sin(dLng) * Math.cos(lat2);
                    xSum -= Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1)
                            * Math.cos(lat2) * Math.cos(dLng);
                    magnitude = Math.sqrt(xSum * xSum + ySum * ySum);
                    xRepulse = xSum/magnitude;
                    yRepulse = ySum/magnitude;
                }
            }

            xSum = 0.0;
            ySum = 0.0;

            for(Agent neighbour : notTooClose) {
                double lat1 = Math.toRadians(this.getCoordinate().getLatitude());
                double lng1 = Math.toRadians(this.getCoordinate().getLongitude());
                double lat2 = Math.toRadians(neighbour.getCoordinate().getLatitude());
                double lng2 = Math.toRadians(neighbour.getCoordinate().getLongitude());
                double dLng = (lng2 - lng1);
                ySum += Math.sin(dLng) * Math.cos(lat2);
                xSum += Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1)
                        * Math.cos(lat2) * Math.cos(dLng);
                magnitude = Math.sqrt(xSum * xSum + ySum * ySum);
                xAttract = xSum/magnitude;
                yAttract = ySum/magnitude;
            }

            targetHeading = Math.atan2(
                    yAlign + 0.5 * yAttract + yRepulse,
                    xAlign + 0.5 * xAttract + xRepulse
            );
        }
        adjustHeading(targetHeading);
        return true;
    }

    /**
     * Adjust heading of agent.
     * @return isAligned - Whether the agent is aligned or needs to continue rotating.
     */
    private boolean adjustHeading(double angleToGoal) {
        Boolean isAligned;
        double hdgRad = Math.toRadians(this.heading);

        //Calculate difference in clockwise (CW) and counter clockwise (CCW) directions.
        double diffCW, diffCCW;
        if(hdgRad < angleToGoal) {
            diffCW = Math.abs(angleToGoal - hdgRad);
            diffCCW = 2*Math.PI - diffCW;
        }
        else if(hdgRad > angleToGoal) {
            diffCCW = Math.abs(angleToGoal - hdgRad);
            diffCW = 2*Math.PI - diffCCW;
        }
        else
            diffCW = diffCCW = 0;

        if(Math.min(diffCW, diffCCW) <= unitTurningAngle) {
            hdgRad = angleToGoal;
            isAligned = true;
        }
        else {
            //Move in direction with smallest difference
            if(diffCW < diffCCW)
                hdgRad += unitTurningAngle;
            else
                hdgRad -= unitTurningAngle;
            isAligned = false;
        }

        //Account for crossing -pi/pi threshold.
        if(hdgRad > Math.PI)
            hdgRad -= 2*Math.PI;
        else if(hdgRad < -Math.PI)
            hdgRad += 2*Math.PI;

        this.heading = Math.toDegrees(hdgRad);
        adjustForWind();
        return isAligned;
    }

    private void adjustForWind() {
        Double[] wind = sensor.senseWind();
        double windSpeed = wind[0] * this.speed;
        double windHeading = wind[1];

        double hdgRad = Math.toRadians(this.heading);
        double windHdgRad = Math.toRadians(windHeading);

        double speed = this.speed - 0.1; // Subtract 0.1 to prepare for first iteration of while loop
        double adjustedSpeed = 0.0;
        double adjustedHeading = 0.0;
        while (adjustedSpeed < 0.1) { // Have drone speed up to overcome wind
            speed += 0.1;
            double speedX = speed * Math.sin(hdgRad);
            double speedY = speed * Math.cos(hdgRad);

            double windSpeedX = windSpeed * Math.sin(windHdgRad);
            double windSpeedY = windSpeed * Math.cos(windHdgRad);

            adjustedSpeed = Math.sqrt(Math.pow(speedX + windSpeedX, 2) + Math.pow(speedY + windSpeedY, 2));

            adjustedHeading = Math.atan2(speedX + windSpeedX, speedY + windSpeedY);
        }

        this.windAdjustedSpeed = adjustedSpeed;
        this.windAdjustedHeading = Math.toDegrees(adjustedHeading);
        this.windAdjustedBatteryConsumption = this.unitTimeBatteryConsumption * (float) (speed / this.windAdjustedSpeed);
    }

    /**
     * Move agent in direction it is currently facing.
      * @param distance - Distance to move in m.
     */
    private void moveAlongHeading(double distance) {
        double r = 6379.1; //Radius of earth in km
        double d = (distance/1000)/r;
        double hdg = Math.toRadians(this.windAdjustedHeading);
        double lat1 = Math.toRadians(this.getCoordinate().getLatitude());
        double lng1 = Math.toRadians(this.getCoordinate().getLongitude());

        double latDest = Math.asin(
                Math.sin(lat1) * Math.cos(d) +
                Math.cos(lat1) * Math.sin(d) * Math.cos(hdg));
        double lngDest = lng1 + Math.atan2(
                Math.sin(hdg) * Math.sin(d) * Math.cos(lat1),
                Math.cos(d) - Math.sin(lat1) * Math.sin(latDest));
        latDest = Math.toDegrees(latDest);
        lngDest = Math.toDegrees(lngDest);
        this.setCoordinate(new Coordinate(latDest, lngDest));
    }

}
