var MapAgentHeatmapController = {
    teamHeatmaps: {}, // Store heatmaps by team
    teamAgentData: {}, // Store agent positions by team
    teamMarkers: {},

    /**
     * Binds all the methods to use the given context.
     *  This means the methods can be called just using MapAgentController.method() without
     *  having to worry about calling from the correct context.
     * @param context - Context to bind to.
     */
    bind: function (context) {
        this.drawAgentMaps = _.bind(this.drawAgentMaps, context);
        this.moveAllMaps = _.bind(this.moveAllMaps, context);
        this.getTeamGradient = _.bind(this.getTeamGradient, context);
        this.clearAllHeatmaps = _.bind(this.clearAllHeatmaps, context);
        this.removeAgentMarkerFor = _.bind(this.removeAgentMarkerFor, context);
        this.removeAgentMarkerForAgentWithTask = _.bind(this.removeAgentMarkerForAgentWithTask, context);
        this.addTeamMarker = _.bind(this.addTeamMarker, context);
        this.onAgentMarkerDrag = _.bind(this.onAgentMarkerDrag, context);
        this.onAgentMarkerDragEnd = _.bind(this.onAgentMarkerDragEnd, context);
        this.drawAllocation = _.bind(this.drawAllocation, context);
        this.updateHeatmapAllocationRendering = _.bind(this.updateHeatmapAllocationRendering, context);
        this.hidePolyline = _.bind(this.hidePolyline, context);
        this.clearAllArrows = _.bind(this.clearAllArrows, context);
        this.printDebugInfo = _.bind(this.printDebugInfo, context);
    },

    /**
     * Bind listeners for agent state add, change and remove events
     */
    bindEvents: function () {

    },

    drawAgentMaps: function () {
        // Clear existing heatmaps and markers
        MapAgentHeatmapController.clearAllHeatmaps();
        MapAgentHeatmapController.removeAllTeamMarkers();

        // Group agents by team and store positions
        const teams = {};
        this.state.agents.forEach(agent => {
            // Normalize team key (ensure consistent grouping)
            const teamKey = Array.isArray(agent.getAgentTeam())
                ? agent.getAgentTeam().join(",")
                : agent.getAgentTeam().toString();

            if (!Array.isArray(teams[teamKey])) {
                teams[teamKey] = [];
            }
            teams[teamKey].push(agent);
        });

        MapAgentHeatmapController.teamAgentData = teams; // Save agent positions for dynamic updates

        // Create heatmaps and markers for each team
        let groupCounter = 1; // Start a counter for group indices
        Object.entries(teams).forEach(([team, teamAgents]) => {
            // Combine all agents in the team into a single heatmap
            const heatmapData = new google.maps.MVCArray(
                teamAgents.map(agent => ({
                    location: new google.maps.LatLng(agent.getPosition().lat(), agent.getPosition().lng()),
                    weight: 3, // Adjust weight as needed
                }))
            );

            const heatmap = new google.maps.visualization.HeatmapLayer({
                data: heatmapData,
                radius: 50, // Adjust radius as needed
                opacity: 0.6,
                gradient: MapAgentHeatmapController.getTeamGradient(team),
            });

            heatmap.setMap(this.map);
            MapAgentHeatmapController.teamHeatmaps[team] = heatmap;

            // Add marker for this team with a numeric group index
            MapAgentHeatmapController.addTeamMarker(teamAgents, groupCounter);
            groupCounter++; // Increment the group counter

            console.log("HM done for team: ", team);
        });
    },

    drawAllocationArrow: function (agentId, taskId) {
        const agentMarker = this.$el.gmap("get", "markers")[agentId];
        const taskMarker = this.$el.gmap("get", "markers")[taskId];

        if (agentMarker && taskMarker) {
            const path = [agentMarker.getPosition(), taskMarker.getPosition()];

            // Add or update polyline for the allocation
            const allocationId = `${agentId}-${taskId}`;
            const polyline = this.$el.gmap("get", "overlays > Polyline", [])[allocationId];
            if (polyline) {
                polyline.setOptions({ path });
            } else {
                this.$el.gmap("addShape", "Polyline", {
                    id: allocationId,
                    editable: false,
                    path: path,
                    icons: [{
                        icon: {
                            scale: 4,
                            path: google.maps.SymbolPath.FORWARD_OPEN_ARROW
                        },
                        offset: '100%'
                    }],
                    strokeOpacity: 0.8,
                    strokeColor: 'blue',
                    strokeWeight: 3,
                });
            }
        }
    },


    /**
     * Move all heatmaps and update marker positions based on updated agent positions.
     */
    moveAllMaps: function () {
        // Update positions for each team
        const teams = {};
        this.state.agents.forEach(agent => {
            const teamKey = Array.isArray(agent.getAgentTeam())
                ? agent.getAgentTeam().join(",")
                : agent.getAgentTeam().toString();

            if (!Array.isArray(teams[teamKey])) {
                teams[teamKey] = [];
            }
            teams[teamKey].push(agent);
        });

        let groupCounter = 1; // Start a counter for group indices
        Object.entries(MapAgentHeatmapController.teamHeatmaps).forEach(([team, heatmap]) => {
            if (teams[team]) {
                // Update heatmap data with new positions
                const heatmapData = new google.maps.MVCArray(
                    teams[team].map(agent => ({
                        location: new google.maps.LatLng(agent.getPosition().lat(), agent.getPosition().lng()),
                        weight: 1,
                    }))
                );

                heatmap.setData(heatmapData);

                // Update marker position for the team with a numeric group index
                MapAgentHeatmapController.addTeamMarker(teams[team], groupCounter);
                groupCounter++; // Increment the group counter
            }
        });

        // Update the stored team-agent data
        MapAgentHeatmapController.teamAgentData = teams;
    },

    addTeamMarker: function (group, index) {
        if (!Array.isArray(group)) {
            console.error("Invalid group passed to addTeamMarker. Expected array, got:", group);
            return;
        }

        let latSum = 0, lngSum = 0;
        group.forEach(agent => {
            const position = agent.getPosition();
            latSum += position.lat();
            lngSum += position.lng();
        });
        const centerLat = latSum / group.length;
        const centerLng = lngSum / group.length;
        const newPos = new google.maps.LatLng(centerLat, centerLng);

        const markerId = `AgentGroup-${index}`;
        let marker = MapAgentHeatmapController.teamMarkers[markerId];

        if (marker) {
            marker.setPosition(newPos);
        } else {
            this.$el.gmap("addMarker", {
                bounds: false,
                marker: MarkerWithLabel,
                draggable: true,
                id: markerId,
                position: newPos,
                labelContent: `AgentGroup-${index}`,
                labelAnchor: new google.maps.Point(25, 65),
                labelClass: "labels",
                labelStyle: { opacity: 1.0 },
                zIndex: 3,
            });

            marker = this.$el.gmap("get", "markers")[markerId];
            marker.centrePos = newPos;
            MapAgentHeatmapController.teamMarkers[markerId] = marker;

            // **Store Agent IDs in the marker**
            marker.agentIds = group.map(agent => agent.getId());

            // Add drag event listeners
            google.maps.event.addListener(marker, "drag", () => {
                MapAgentHeatmapController.onAgentMarkerDrag(marker, group);
            });
            google.maps.event.addListener(marker, "dragend", () => {
                MapAgentHeatmapController.onAgentMarkerDragEnd(marker, group);
            });
        }

        marker.setMap(this.map);
    },



    onAgentMarkerDrag: function (marker, group) {
        if (!MapAgentHeatmapController.isManuallyAllocating) {
            MapAgentHeatmapController.isManuallyAllocating = true;
        }

        const groupPosition = marker.centrePos || marker.getPosition();
        const cursorPosition = marker.getPosition();

        // Prevent marker from moving
        marker.setPosition(groupPosition);

        const arrowEnd = MapAgentHeatmapController.groupIdToAllocateManually
            ? this.$el.gmap("get", "markers")[MapAgentHeatmapController.groupIdToAllocateManually].getPosition()
            : cursorPosition;

        const path = [groupPosition, arrowEnd];
        let polyline = this.$el.gmap("get", "overlays > Polyline", [])["manual_allocation"];

        if (polyline) {
            if (!polyline.getMap()) {
                polyline.setMap(this.map); // Ensure the polyline is on the map
            }
            polyline.setOptions({
                path: path, // Update the path
            });
        } else {
            // Create a new polyline with an arrowhead
            this.$el.gmap("addShape", "Polyline", {
                id: "manual_allocation",
                editable: false,
                path: path,
                icons: [
                    {
                        icon: {
                            path: google.maps.SymbolPath.FORWARD_OPEN_ARROW,
                            scale: 4, // Adjust the size of the arrowhead
                            strokeColor: "blue",
                        },
                        offset: "100%", // Position the arrowhead at the end of the line
                    },
                ],
                strokeColor: "blue",
                strokeOpacity: 0.8,
                strokeWeight: 5,
                zIndex: 2,
            });
        }
    },


    onAgentMarkerDragEnd: function (marker, group) {
        MapAgentHeatmapController.isManuallyAllocating = false;

        const groupPosition = marker.centrePos || marker.getPosition();
        marker.setPosition(groupPosition);

        if (MapAgentHeatmapController.groupIdToAllocateManually) {
            const agentsIdsToPass = group.map(agent => agent.getId());
            const taskGroupId = MapAgentHeatmapController.groupIdToAllocateManually;

            console.log(`Agent IDs: ${agentsIdsToPass}`);
            console.log(`Hovered Task Group ID: ${taskGroupId}`);

            const taskGroupIndex = taskGroupId.startsWith("TaskGroup-")
                ? parseInt(taskGroupId.split("-")[1], 10)
                : null;

            console.log(`Resolved Task Group Index: ${taskGroupIndex}`);

            if (taskGroupIndex !== null) {
                // Send the allocation POST request with the entire task group
                $.post("/allocation/groupAllocate", {
                    agentIds: agentsIdsToPass.toString(),
                    taskGroupIndex: taskGroupIndex
                }).done(() => {
                    console.log("Allocation successful!");
                }).fail((error) => {
                    console.error("Failed to allocate tasks:", error);
                });
            } else {
                console.error("Failed to resolve task group ID.");
            }
        } else {
            console.log("No task group was hovered over during allocation.");
        }

        // Hide the allocation arrow
        const polyline = this.$el.gmap("get", "overlays > Polyline", [])["manual_allocation"];
        if (polyline) {
            polyline.setMap(null);
        }

        MapAgentHeatmapController.groupIdToAllocateManually = null;
    },


    removeAgentMarkerFor: function (index) {
        const markerId = "AgentGroup-" + index;
        let marker = this.$el.gmap("get", "markers")[markerId];

        if (marker) {
            marker.setMap(null);
            delete marker;
        }
    },

    removeAgentMarkerForAgentWithTask: function (task) {
        // Validate that the addedGroups array exists and has elements
        if (!MapAgentHeatmapController.addedGroups || MapAgentHeatmapController.addedGroups.length === 0) {
            console.warn("No added groups to process.");
            return;
        }

        // Iterate over all added groups
        for (let i = 0; i < MapAgentHeatmapController.addedGroups.length; i++) {
            const group = MapAgentHeatmapController.addedGroups[i];

            // Ensure the group exists and is an array
            if (!Array.isArray(group)) {
                console.warn(`Group at index ${i} is not valid:`, group);
                continue;
            }

            // Check if the task exists in the current group
            for (let j = 0; j < group.length; j++) {
                const agent = group[j];

                // Ensure agent exists and validate task association
                if (agent && agent.getAllocatedTaskId && agent.getAllocatedTaskId() === task.getId()) {
                    console.log(`Removing marker for group ${i} due to task completion:`, task.getId());

                    // Remove the agent's marker for this task
                    MapAgentHeatmapController.removeAgentMarkerFor(i);

                    // Break the loop once the marker is removed
                    return;
                }
            }
        }

        console.warn("Task not found in any group:", task.getId());
    },

    /**
     * Remove all team markers.
     */
    removeAllTeamMarkers: function () {
        Object.values(MapAgentHeatmapController.teamMarkers).forEach(marker => {
            marker.setMap(null);
        });
        MapAgentHeatmapController.teamMarkers = {};
    },

    /**
     * Get a gradient for a team (can be customised for each team).
     * @param team - Team identifier
     * @returns {Array} - Gradient array
     */
    getTeamGradient: function (team) {
        const defaultGradient = [
            "rgba(0, 255, 255, 0)",
            "rgba(0, 255, 255, 1)",
            "rgba(0, 191, 255, 1)",
            "rgba(0, 127, 255, 1)",
            "rgba(0, 63, 255, 1)",
            "rgba(0, 0, 255, 1)",
            "rgba(0, 0, 223, 1)",
            "rgba(0, 0, 191, 1)",
            "rgba(0, 0, 159, 1)",
            "rgba(0, 0, 127, 1)",
            "rgba(63, 0, 91, 1)",
            "rgba(127, 0, 63, 1)",
            "rgba(191, 0, 31, 1)",
            "rgba(255, 0, 0, 1)",
        ];
        return defaultGradient; // Customize gradient based on the team if needed
    },

    /**
     * Clear all heatmaps.
     */
    clearAllHeatmaps: function () {
        Object.values(MapAgentHeatmapController.teamHeatmaps).forEach(heatmap => {
            heatmap.setMap(null);
        });
        MapAgentHeatmapController.teamHeatmaps = {};
        MapAgentHeatmapController.teamAgentData = {};
    },

    drawAllocation: function (lineId, color, agentGroupId, taskGroupId) {
        console.log(`looking for: ${agentGroupId}`)
        // Retrieve the agent group marker
        const agentGroupMarker = this.$el.gmap("get", "markers")[`${agentGroupId}`];
        console.log(`looking for: TaskGroup-${taskGroupId}`)
        // Retrieve the task group marker
        const taskGroupMarker = this.$el.gmap("get", "markers")[`TaskGroup-${taskGroupId}`];

        // Check if markers exist
        if (!agentGroupMarker || !taskGroupMarker) {
            console.warn(`Markers not found for Agent Group ${agentGroupId} or Task Group ${taskGroupId}`);
            return;
        }

        // Define the path for the polyline
        const path = [
            agentGroupMarker.getPosition(),
            taskGroupMarker.getPosition(),
        ];

        // Check if the polyline already exists
        let polyline = this.$el.gmap("get", "overlays > Polyline", [])[lineId];

        if (polyline) {
            // Update the existing arrow
            polyline.setOptions({
                path: path,
                strokeColor: color,
            });
        } else {
            // Create a new arrow
            this.$el.gmap("addShape", "Polyline", {
                id: lineId,
                editable: false,
                path: path,
                icons: [{
                    icon: {
                        scale: 4,
                        path: google.maps.SymbolPath.FORWARD_OPEN_ARROW,
                    },
                    offset: '100%',
                }],
                strokeOpacity: 0.8,
                strokeColor: color,
                strokeWeight: 5,
                zIndex: 2,
            });
        }
    },


    hidePolyline: function (lineId) {
        const polylines = this.$el.gmap("get", "overlays > Polyline", []);
        Object.keys(polylines).forEach(key => {
            if (key.startsWith(lineId)) {
                const polyline = polylines[key];
                if (polyline) {
                    console.log(`Removing polyline: ${key}`);
                    polyline.setMap(null);
                    delete polylines[key];
                }
            }
        });
    },

    updateHeatmapAllocationRendering: function () {
        console.log("====================================================================");
        console.log("Rendering heatmap allocation updates...");

        const mainAllocation = this.state.getAllocation();
        const tempAllocation = this.state.getTempAllocation();

        console.log("Main Allocation:", mainAllocation);
        console.log("Temporary Allocation:", tempAllocation);
        console.log("Current Team Agent Data:", MapAgentHeatmapController.teamAgentData);

        MapAgentHeatmapController.printDebugInfo();

        // Iterate over all agent group markers
        Object.keys(MapAgentHeatmapController.teamMarkers).forEach((teamMarkerId) => {
            console.log(`Processing team marker: ${teamMarkerId}`);

            const marker = MapAgentHeatmapController.teamMarkers[teamMarkerId];
            const agentIdsInMarker = marker.agentIds || [];
            let matchedTeamKey = null;
            let matchedAgents = null;

            // Ensure exact matching of agent groups
            Object.entries(MapAgentHeatmapController.teamAgentData).forEach(([teamKey, agents]) => {
                const agentIdsInTeam = teamKey.split(",").sort();
                if (JSON.stringify(agentIdsInTeam) === JSON.stringify(agentIdsInMarker.sort())) {
                    matchedTeamKey = teamKey;
                    matchedAgents = agents;
                }
            });

            if (!matchedTeamKey || !matchedAgents) {
                console.warn(`No matching team found for marker: ${teamMarkerId}`);
                this.hidePolyline(`${teamMarkerId}_main`);
                this.hidePolyline(`${teamMarkerId}_temp`);
                return;
            }

            console.log(`Found agents for team: ${matchedTeamKey} -> ${matchedAgents.map(agent => agent.getId()).join(", ")}`);

            // Ensure the selected team has an allocated task
            let selectedAgent = null;
            let allocatedTaskId = null;

            for (const agent of matchedAgents) {
                allocatedTaskId = mainAllocation[agent.getId()];
                if (allocatedTaskId) {
                    selectedAgent = agent;
                    break;
                }
            }

            if (!selectedAgent || !allocatedTaskId) {
                console.warn(`No allocated task found for team: ${matchedTeamKey}`);
                this.hidePolyline(`${teamMarkerId}_main`);
                this.hidePolyline(`${teamMarkerId}_temp`);
                return;
            }

            console.log(`Selected agent: ${selectedAgent.getId()} with task ID: ${allocatedTaskId}`);

            const allocatedTask = this.state.tasks.find(task => task.getId() === allocatedTaskId);
            if (!allocatedTask) {
                console.warn(`Task with ID ${allocatedTaskId} not found.`);
                this.hidePolyline(`${teamMarkerId}_main`);
                this.hidePolyline(`${teamMarkerId}_temp`);
                return;
            }

            console.log(`Found allocated task:`, allocatedTask);

            const taskGroupId = allocatedTask.getTaskGroup();
            console.log(`Task belongs to TaskGroup ID: ${taskGroupId}`);

            const taskMarkerId = `TaskGroup-${taskGroupId}`;
            const taskMarker = this.$el.gmap("get", "markers")[taskMarkerId];

            if (!taskMarker) {
                console.warn(`TaskGroup marker for TaskGroup-${taskGroupId} not found.`);
                this.hidePolyline(`${teamMarkerId}_main`);
                this.hidePolyline(`${teamMarkerId}_temp`);
                return;
            }

            console.log(`Drawing main allocation: Agent Group ${teamMarkerId} -> Task Group ${taskGroupId}`);
            MapAgentHeatmapController.drawAllocation(`${teamMarkerId}_main`, "green", teamMarkerId, taskGroupId);

            // Handle temporary allocation if in EditMode 2
            if (this.state.getEditMode() === 2) {
                const tempTaskId = tempAllocation[selectedAgent.getId()];
                if (tempTaskId) {
                    const tempTask = this.state.tasks.find(task => task.getId() === tempTaskId);
                    if (tempTask) {
                        const tempTaskGroupId = tempTask.getTaskGroup();
                        const tempTaskMarkerId = `TaskGroup-${tempTaskGroupId}`;
                        const tempTaskMarker = this.$el.gmap("get", "markers")[tempTaskMarkerId];

                        if (tempTaskMarker) {
                            console.log(`Drawing temporary allocation: Agent Group ${teamMarkerId} -> Task Group ${tempTaskGroupId}`);
                            MapAgentHeatmapController.drawAllocation(`${teamMarkerId}_temp`, "orange", teamMarkerId, tempTaskGroupId);
                        } else {
                            console.warn(`Temporary TaskGroup marker for TaskGroup-${tempTaskGroupId} not found.`);
                            this.hidePolyline(`${teamMarkerId}_temp`);
                        }
                    } else {
                        console.warn(`Temporary Task with ID ${tempTaskId} not found.`);
                        this.hidePolyline(`${teamMarkerId}_temp`);
                    }
                } else {
                    console.log(`No temporary allocation for Agent Group ${teamMarkerId} or not in EditMode 2.`);
                    this.hidePolyline(`${teamMarkerId}_temp`);
                }
            } else {
                console.log(`Not in EditMode 2. Hiding temporary allocation polyline.`);
                this.hidePolyline(`${teamMarkerId}_temp`);
            }
        });
    },


    clearAllArrows: function () {
        const polylines = this.$el.gmap("get", "overlays > Polyline", []);
        Object.keys(polylines).forEach(key => {
            console.log(`Clearing polyline: ${key}`);
            polylines[key].setMap(null);
            delete polylines[key];
        });
    },

    printDebugInfo: function() {
        console.log("=== Debugging Info ===");

        console.log("teamHeatmaps:");
        Object.entries(MapAgentHeatmapController.teamHeatmaps).forEach(([teamKey, heatmap]) => {
            console.log(`Team: ${teamKey}`);
            console.log("Heatmap Data:", heatmap.getData());
        });

        console.log("\nteamAgentData:");
        Object.entries(MapAgentHeatmapController.teamAgentData).forEach(([teamKey, agents]) => {
            console.log(`Team: ${teamKey}`);
            agents.forEach(agent => {
                console.log(`  Agent ID: ${agent.getId()}, Position: ${agent.getPosition().toString()}`);
            });
        });

        console.log("\nteamMarkers:");
        Object.entries(MapAgentHeatmapController.teamMarkers).forEach(([markerId, marker]) => {
            console.log(`Marker ID: ${markerId}`);
            console.log("  Position:", marker.getPosition().toString());
            console.log("  Label:", marker.getLabel ? marker.getLabel() : "No label");
            console.log("  Agent IDs:", marker.agentIds || "No agent IDs stored");
        });

        console.log("======================");
    },



};