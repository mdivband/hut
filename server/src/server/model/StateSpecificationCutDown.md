class State {
    - time: double                               // Elapsed time in the simulation
    - timeLimit: Integer                         // Time limit for the scenario if any

    // ────── Collections ──────
    - agents: Collection<Agent>                  // Active agents in the scenario

    // ────── Scenario State ──────
    - gameCentre: Coordinate                     // Central coordinate of the game map

    // ────── Hub & Location ──────
    - hubLocation: Coordinate                    // Location of the hub on the map

    // ────── Risk Mapping ──────
    - riskMap: ArrayList<HashMap<String, ArrayList<Double>>> // Multi-layered risk representation
    - riskMapWeights: HashMap<String, Double>    // Weights for dynamic risk layers
    - riskMapWeightsConst: HashMap<String, Double> // Constant baseline weights for risk layers
}

class Agent {
  // ────── Client-Side Fields ──────
  - altitude: double                              // Current altitude of the agent
  - battery: double                               // Remaining battery level
  - heading: double                               // Heading direction in radians
  - marker: String                                // Marker label for UI or logical grouping
  - manuallyControlled: boolean                   // Whether the agent is under manual control
  - route: List<Coordinate>                       // Planned route for the agent
  - tempRoute: List<Coordinate>                   // Temporary working route
  - speed: double                                 // Max movement speed
  - timedOut: boolean                             // Whether this agent has timed out

  - type: String                                  // Agent type/classification

  // ────── Server-Side Transient Fields ──────
  - lastHeartbeat: long [transient]               // Last known active timestamp
  - stopped: boolean [transient]                  // Whether the agent is stopped
}

class Coordinate {
    - latitude: double                              // Latitude position
    - longitude: double                             // Longitude position
}

class Target{
    - type: int                                     // Type/category of the target
}
