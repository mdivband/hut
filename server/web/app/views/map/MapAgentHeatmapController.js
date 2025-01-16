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
        this.drawAllocationArrow = _.bind(this.drawAllocationArrow, context);
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
                labelContent: `Group-${index}`,
                labelAnchor: new google.maps.Point(25, 65),
                labelClass: "labels",
                labelStyle: { opacity: 1.0 },
                zIndex: 3,
            });

            marker = this.$el.gmap("get", "markers")[markerId];
            marker.centrePos = newPos;
            MapAgentHeatmapController.teamMarkers[markerId] = marker;

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
        marker.setPosition(groupPosition); // Prevent movement

        const arrowEnd = MapAgentHeatmapController.groupIdToAllocateManually
            ? this.$el.gmap("get", "markers")[MapAgentHeatmapController.groupIdToAllocateManually].getPosition()
            : cursorPosition;

        const path = [groupPosition, arrowEnd];
        let polyline = this.$el.gmap("get", "overlays > Polyline", [])["manual_allocation"];

        if (polyline) {
            polyline.setPath(path);
        } else {
            this.$el.gmap("addShape", "Polyline", {
                id: "manual_allocation",
                editable: false,
                path,
                strokeColor: "blue",
                strokeOpacity: 0.8,
                strokeWeight: 2,
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
                const taskGroup = MapTaskHeatmapController.addedGroups[taskGroupIndex];
                if (taskGroup) {
                    const taskIdsToPass = taskGroup.map(task => task.getId());
                    console.log(`Task IDs to Allocate: ${taskIdsToPass}`);

                    // Send the allocation POST request
                    $.post("/allocation/groupAllocate", {
                        agentIds: agentsIdsToPass.toString(),
                        taskIds: taskIdsToPass.toString(),
                    }).done(() => {
                        console.log("Allocation successful!");
                    }).fail((error) => {
                        console.error("Failed to allocate tasks:", error);
                    });
                } else {
                    console.error(`Task group not found for index ${taskGroupIndex}.`);
                }
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

    drawAllocation: function (lineId, color, agentId, taskId) {
        const agentMarker = this.$el.gmap("get", "markers")[`AgentGroup-${agentId}`];
        const taskMarker = this.$el.gmap("get", "markers")[`TaskGroup-${taskId}`];

        if (!agentMarker || !taskMarker) {
            console.warn(`Markers not found for agent ${agentId} or task ${taskId}`);
            return;
        }

        const path = [
            agentMarker.getPosition(),
            taskMarker.getPosition(),
        ];

        let polyline = this.$el.gmap("get", "overlays > Polyline", [])[lineId];

        if (polyline) {
            // Update existing arrow
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

    updateHeatmapAllocationRendering: function () {
        const self = this;
        const mainAllocation = this.state.getAllocation();
        const tempAllocation = this.state.getTempAllocation();
        const droppedAllocation = this.state.getDroppedAllocation();

        // Iterate over agents and update allocation arrows
        this.state.agents.each(function (agent) {
            const agentId = agent.getId();

            // Main allocation
            if (agentId in mainAllocation) {
                const mainTaskId = mainAllocation[agentId];
                MapAgentHeatmapController.drawAllocation(`${agentId}_main`, "green", agentId, mainTaskId);
            } else {
                MapAgentHeatmapController.hidePolyline(`${agentId}_main`);
            }

            // Temporary allocation
            if (self.state.getEditMode() === 2 && agentId in tempAllocation) {
                const tempTaskId = tempAllocation[agentId];
                MapAgentHeatmapController.drawAllocation(`${agentId}_temp`, "orange", agentId, tempTaskId);
            } else {
                MapAgentHeatmapController.hidePolyline(`${agentId}_temp`);
            }

            // Dropped allocation
            if (self.state.getEditMode() === 2 && agentId in droppedAllocation) {
                const droppedTaskId = droppedAllocation[agentId];
                MapAgentHeatmapController.drawAllocation(`${agentId}_dropped`, "grey", agentId, droppedTaskId);
            } else {
                MapAgentHeatmapController.hidePolyline(`${agentId}_dropped`);
            }
        });
    },

    hidePolyline: function (lineId) {
        const polyline = this.$el.gmap("get", "overlays > Polyline", [])[lineId];
        if (polyline) {
            polyline.setMap(null); // Remove from map
            delete this.$el.gmap("get", "overlays > Polyline", [])[lineId]; // Delete from storage
        }
    },



};