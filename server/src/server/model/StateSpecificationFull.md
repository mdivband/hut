class State {
    // ────── Basic State ──────
    - inProgress: boolean                        // Indicates if the game is currently in progress
    - gameId: String                             // Unique identifier for simulated scenario
    - gameDescription: String                    // Descriptive text abou simulated scenario
    - gameType: int                              // Type of game (e.g., sandbox or scenario) ; constants ommitted in this spec
    - allocationMethod: String                   // Name of the method used for task allocation
    - allocationStyle: String                    // Style of allocation - dynamic, ad-hoc, etc
    - modelStyle: String                         // Model behavior style used in the simulation if any
    - taskGroupSize: Integer                     // Number of tasks grouped together, if applicable
    - flockingEnabled: Boolean                   // Whether agent flocking behavior is enabled
    - time: double                               // Elapsed time in the simulation
    - timeLimit: Integer                         // Time limit for the scenario if any
    - editMode: int                              // UI edit mode (1=monitor, 2=edit, 3=images)
    - hasPassthrough: boolean                    // Whether passthrough to another scenario is enabled
    - nextFileName: String                       // Name of the next file for passthrough
    - deepAllowed: boolean                       // Whether deep scanning is permitted
    - showReviewPanel: boolean                   // Whether to show the review panel in UI
    - prov_doc: String                           // Provenance document (data trace info)

    // ────── Collections ──────
    - agents: Collection<Agent>                  // Active agents in the scenario
    - ghosts: Collection<AgentGhost>             // Ghost agents (e.g., historical or placeholder)
    - tasks: Collection<Task>                    // Current active tasks
    - completedTasks: Collection<Task>           // Tasks that have been completed
    - hazards: Collection<Hazard>                // Hazards present in the environment

    // ────── Scenario State ──────
    - gameCentre: Coordinate                     // Central coordinate of the game map
    - targets: Collection<Target>                // Targets to be found or reached

    // ────── Client-only Flags ──────
    - allocationUndoAvailable: boolean           // Whether undo is available for allocation
    - allocationRedoAvailable: boolean           // Whether redo is available for allocation

    // ────── Allocations ──────
    - allocation: Map<String, String>            // Current confirmed allocation
    - tempAllocation: Map<String, String>        // Temporary working allocation (in-progress)
    - droppedAllocation: Map<String, String>     // Allocation based on dropped-out agents
    - hazardHits: HazardHitCollection            // Records of hazards found

    // ────── UI & Options ──────
    - uiOptions: HashMap<String, boolean[]>      // UI toggles/options by key
    - varianceOptions: Map<String, Double>       // Variance parameters per feature/setting
    - noiseOptions: Map<String, Double>          // Noise levels per feature/setting
    - uncertaintyRadius: double                  // Radius of positional uncertainty
    - communicationRange: double                 // Range limit for agent communication
    - communicationConstrained: boolean          // Whether communication is range-constrained

    // ────── Success Metrics ──────
    - successChance: double                      // Overall chance of mission success
    - missionSuccessChance: double               // Predicted success of entire mission
    - missionSuccessOverChance: double           // Predicted success of N+1 agents
    - missionSuccessUnderChance: double          // Predicted success of N-1 agents
    - missionBoundedSuccessChance: double        // Success chance within bounds
    - missionBoundedSuccessUnderChance: double   // Bounded success of N+1 agents
    - missionBoundedSuccessOverChance: double    // Bounded success of N-1 agents
    - loggingById: boolean                       // Whether events are logged by agent ID

    // ────── Dynamic UI & Scoring ──────
    - dynamicUIFeatures: List<List<String>>      // Configurable UI features
    - scoreInfo: Map<String, Double>             // Scoring breakdown by category/metric

    // ────── Hub & Location ──────
    - hub: Hub                                   // Central coordination hub
    - hubLocation: Coordinate                    // Location of the hub on the map

    // ────── User & Markers ──────
    - userName: String                           // Name of the user inputted from frontend
    - markers: List<String>                      // Visual or logical markers used in UI

    // ────── Images & IDs ──────
    - storedImages: ConcurrentHashMap<String, String> // Image cache keyed by ID
    - deepScannedIds: List<String>               // IDs of items deep scanned
    - pendingIds: List<String>                   // IDs pending processing
    - handledTargets: ArrayList<String>          // Target IDs already handled
    - pendingMap: HashMap<Coordinate, String>    // Pending items mapped by coordinate

    // ────── Performance & Speed ──────
    - workloadLevel: Integer                     // Workload intensity level
    - gameSpeed: Integer                         // Speed multiplier for the game. Note tickrate is divorced from state.

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
  - allocatedTaskId: String                       // ID of the task currently assigned
  - timeInAir: double                             // Total flight time (cumulative)
  - simulated: boolean                            // Whether this agent is simulated
  - timedOut: boolean                             // Whether this agent has timed out
  - working: boolean                              // Whether the agent is actively working

  - type: String                                  // Agent type/classification
  - visible: boolean                              // Whether the agent is visible in the UI

  // ────── Server-Side Transient Fields ──────
  - lastHeartbeat: long [transient]               // Last known active timestamp
  - startSearching: boolean [transient]           // Whether the agent is in search mode
  - stopped: boolean [transient]                  // Whether the agent is stopped

  // ────── Queues and Grouping ──────
  - taskQueue: List<Task>                         // List of upcoming tasks
  - coordQueue: List<Coordinate>                  // Coordinates to be visited
  - agentTeam: List<String>                       // Team/group membership by agent ID
}

class Coordinate {
    - serialVersionUID: long                        // Serialization identifier
    - latitude: double                              // Latitude position
    - longitude: double                             // Longitude position
}

class Target{
    - type: int                                     // Type/category of the target
    - visible: boolean                              // Whether the target is visible (found)
}
