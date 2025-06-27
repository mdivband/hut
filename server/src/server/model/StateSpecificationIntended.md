# Intended Specification
This is basically the specification for the state of the mission which we will collate, update, and distribute with the MCS. Any and all algorithms should be able to pull the information they need from here, and update the relevant information (most notably routes for each agent). We will also provide an API of getters and setters etc, and perhaps some helper functions depending on team size. This should remove the overhead of needing to deal with the backend and inner workings of the MCS, and allow the ML team to focus on algorithms and logic.

## State
Not much from the state should be needed as most information is collated from drones (Agents). Anything mission-wide that should be known by all drones should be here also.
```
class State {
    - time: double                               // Elapsed time in the simulation
    - timeLimit: Integer                         // Time limit for the scenario if any

    // ────── Collections ──────
    - agents: Collection<Agent>                  // Active agents in the scenario. Will include FOBs, MOBs, and all drones

    // ────── Scenario State ──────
    - gameCentre: Coordinate                     // Central coordinate of the game map
    - gameCorners: List<Coordinate>              // Map corners for boundary definition (TL, TR, BR, BL)

    // ────── Hub & Location ──────
    - hubLocation: Coordinate                    // Location of the hub on the map

    // ────── Risk Mapping ──────
    - riskMap: ArrayList<HashMap<String, ArrayList<Double>>> // Multi-layered risk representation
    - riskMapWeights: HashMap<String, Double>    // Weights for dynamic risk layers
    - riskMapWeightsConst: HashMap<String, Double> // Constant baseline weights for risk layers
}
```

## Agent
Each agent has these properties. They are stored as a list in the State class.
```
class Agent {
    // ────── Client-Side Fields ──────
    - altitude: double                              // Current altitude of the agent in some agreed unit (metres probably? Whatever we standardise to)
    - battery: double                               // Remaining battery level (I assume some double can represent this?)
    - heading: double                               // Heading in radians
    - marker: String                                // Marker label for UI or logical grouping
    - manuallyControlled: boolean                   // Whether the agent is under manual control
    - route: List<Coordinate>                       // Planned route for the agent
    - tempRoute: List<Coordinate>                   // Temporary working route
    - speed: double                                 // Max movement speed
    - currentSpeed: double                          // Current speed of the agent - I assume we need this in some aeronautical format? Airspeed/Groundspeed? Multidimensional including vertical speed?
    - timedOut: boolean                             // Whether this agent has timed out

    - type: String                                  // Agent type/classification -> Tico or Fat Sparrow; We will probably use two different subclasses of Agent for this, but can include a text label for ease

    // ────── Server-Side Transient Fields ──────
    - lastHeartbeat: long [transient]               // Last known active timestamp
    - stopped: boolean [transient]                  // Whether the agent is stopped
}
```

## Coordinate
Basically a data type for lat, lng, etc. Standardises order of saving. We may also use this to compress positional format (i.e if we know the grid, a relative coord e.g. [104, 652] instead of a full lat/lng)
```
class Coordinate {
    - latitude: double                              // Latitude position
    - longitude: double                             // Longitude position
    - altitude: double                              // Altitude. May leave this at a default -1 if not relevant along a path, i.e. -1 means no change. -> Might be a dumb idea so let me know if this is stupid
}
```

## Target
No idea if you want this. We currently store Targets, Hazards, and Tasks in our backend, but perhaps it is easier fo you to handle this mostly yourselves from a logical perspective. Either way, a reported fire etc needs to be represented in the backend for visualisation.
```
class Target{
    - type: int                                     // Type/category of the target
    - location: Coordinate                          // Location of the target
}
```
