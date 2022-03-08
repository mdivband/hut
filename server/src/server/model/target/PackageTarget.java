package server.model.target;

import server.model.Coordinate;
import server.model.agents.Agent;

public class PackageTarget extends Target {

    public PackageTarget(String id, Coordinate coordinate) {
        super(id, coordinate, Target.PACKAGE);
    }


}
