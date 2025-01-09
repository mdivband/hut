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
        // Validate that `group` is an array
        if (!Array.isArray(group)) {
            console.error("Invalid group passed to addTeamMarker. Expected array, got:", group);
            return;
        }

        // Calculate the center of the group's agents
        let latSum = 0, lngSum = 0;
        group.forEach(agent => {
            const position = agent.getPosition();
            latSum += position.lat();
            lngSum += position.lng();
        });
        const centerLat = latSum / group.length;
        const centerLng = lngSum / group.length;
        const newPos = new google.maps.LatLng(centerLat, centerLng);

        // Generate marker label (e.g., "Group-3")
        const markerLabel = `Group-${index}`;
        const markerId = `AgentGroup-${index}`;

        let marker = MapAgentHeatmapController.teamMarkers[markerId];
        if (marker) {
            // Update existing marker's position and label
            marker.setPosition(newPos);
            marker.setOptions({
                labelContent: markerLabel,
            });
        } else {
            // Add a new marker using MarkerWithLabel
            this.$el.gmap("addMarker", {
                bounds: false,
                marker: MarkerWithLabel,
                draggable: true,
                labelContent: markerLabel,
                labelAnchor:  new google.maps.Point(25, 65),
                labelClass: "labels",
                labelStyle: { opacity: 1.0 },
                id: markerId,
                position: newPos,
                raiseOnDrag: false,
                zIndex: 3,
            });

            marker = this.$el.gmap("get", "markers")[markerId];
            MapAgentHeatmapController.teamMarkers[markerId] = marker;

            // Add drag event listeners
            $(marker).drag(() => {
                MapAgentHeatmapController.onAgentMarkerDrag(marker);
            }).dragend(() => {
                MapAgentHeatmapController.onAgentMarkerDragEnd(marker);
            });
        }

        // Update the marker's icon using the existing updateTaskMarkerIcon method
        MapTaskController.updateTaskMarkerIcon(markerId, {
            h: 180, // Example hue adjustment
            s: 1,   // Saturation
            l: 1,   // Brightness
        });

        // Set the marker on the map
        marker.setMap(this.map);

        // Debugging: Log marker and label positions
        console.log(`Marker for Group-${index} centered at:`, newPos);
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
        for (let i = 0; i < MapAgentHeatmapController.addedGroups.length; i++) {
            for (let j = 0; j < MapAgentHeatmapController.addedGroups[i].length; j++) {
                if (MapAgentHeatmapController.addedGroups[i][j].getAllocatedTaskId() === task.getId()) {
                    MapAgentHeatmapController.removeAgentMarkerFor(i);
                }
            }
        }
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
};