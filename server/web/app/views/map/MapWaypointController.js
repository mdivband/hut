var MapWaypointController = {
    context: null,

    /**
     * Binds all methods to the provided context (the map view).
     * This creates new functions where 'this' is permanently locked.
     */
    bind: function (context) {
        this.context = context;
        this.bindEvents = _.bind(this.bindEvents, this);
        this.drawAgentRoute = _.bind(this.drawAgentRoute, this);
        this.clearAgentRoute = _.bind(this.clearAgentRoute, this);
        this.renderAllRoutes = _.bind(this.renderAllRoutes, this);
        this.clearAllRoutes = _.bind(this.clearAllRoutes, this);
        this.clearAgentWaypointMarkers = _.bind(this.clearAgentWaypointMarkers, this);
    },

    /**
     * Binds listeners for state changes using the correct context.
     */
    bindEvents: function () {
        this.context.state.on("change:time", function () {
            this.clearAllRoutes();
            if (this.context.state.get('pathPlanning')) {
                this.renderAllRoutes();
            }
        }, this);
    },

    clearAgentWaypointMarkers: function(agent) {
        var allCircles = this.context.$el.gmap("get", "overlays > Circle");
        var prefix = agent.getId() + "_waypoint_";
        for (var key in allCircles) {
            if (key.startsWith(prefix)) {
                var circle = allCircles[key];
                if (circle) {
                    circle.setMap(null);
                    delete allCircles[key];
                }
            }
        }
    },

    drawAgentRoute: function(agent) {
        this.clearAgentWaypointMarkers(agent); // Clear dots

        var fullRoute = agent.getRoute();
        var predictionDepth = MapController.predictionLength;
        var routeToDraw = fullRoute ? fullRoute.slice(0, predictionDepth) : [];

        if (!routeToDraw || routeToDraw.length === 0) {
            this.clearAgentRoute(agent);
            return;
        }

        var routeId = agent.getId() + "_route";
        var polyline = this.context.$el.gmap("get", "overlays > Polyline", [])[routeId];

        var path = routeToDraw.map(function(c) {
            return new google.maps.LatLng(c.latitude, c.longitude);
        });
        let hash = MapWaypointController.generateHash(agent.getId());

        if (path.length < 2) {
            this.clearAgentRoute(agent);
        } else {
            if (polyline) {
                polyline.setPath(path);
                polyline.setMap(this.context.map);
            } else {
                this.context.$el.gmap("addShape", "Polyline", {
                    id: routeId, path: path, strokeColor: '#000000',
                    strokeOpacity: 0.5+(hash/2), strokeWeight: 4, zIndex: 1,
                    icons: [{ icon: { path: google.maps.SymbolPath.FORWARD_OPEN_ARROW }, offset: '100%' }]
                });
            }
        }

        routeToDraw.forEach((waypoint, index) => {
            var waypointId = agent.getId() + "_waypoint_" + index;
            this.context.$el.gmap("addShape", "Circle", {
                id: waypointId,
                center: new google.maps.LatLng(waypoint.latitude, waypoint.longitude),
                radius: 5, fillColor: '#000000', fillOpacity: 0.5+(hash/2),
                strokeColor: '#FFFFFF', strokeWeight: 1,
                clickable: false, zIndex: 2
            });
        });
    },

    drawAgentRoute: function(agent) {
        this.clearAgentWaypointMarkers(agent);

        var fullRoute = agent.getRoute();
        var predictionDepth = MapController.predictionLength;
        var routeToDraw = fullRoute ? fullRoute.slice(0, predictionDepth) : [];

        if (!routeToDraw || routeToDraw.length === 0) {
            this.clearAgentRoute(agent);
            return;
        }

        const allAgents = this.context.state.agents;
        const totalAgents = allAgents.length;

        const agentIndex = allAgents.indexOf(agent);

        // calculate opacity
        const minOpacity = 0.3;
        const maxOpacity = 1.0;
        let opacity;

        if (totalAgents <= 1) {
            opacity = maxOpacity;
        } else {
            const opacityRange = maxOpacity - minOpacity;
            const step = opacityRange / (totalAgents - 1);
            opacity = minOpacity + (agentIndex * step);
        }

        var path = routeToDraw.map(function(c) {
            return new google.maps.LatLng(c.latitude, c.longitude);
        });

        if (path.length < 2) {
            this.clearAgentRoute(agent);
            return;
        }

        var polylineOptions = {
            path: path,
            strokeColor: '#000000',
            strokeOpacity: opacity,
            strokeWeight: 2,
            zIndex: 1,
            icons: [
                {
                    icon: {
                        path: google.maps.SymbolPath.FORWARD_OPEN_ARROW
                    },
                    offset: '100%'
                }
            ]
        };

        var polyline = this.context.$el.gmap("get", "overlays > Polyline", [])[agent.getId() + "_route"];
        if (polyline) {
            polyline.setOptions(polylineOptions);
            polyline.setMap(this.context.map);
        } else {
            this.context.$el.gmap("addShape", "Polyline", {
                id: agent.getId() + "_route",
                ...polylineOptions
            });
        }

        routeToDraw.forEach((waypoint, index) => {
            var waypointId = agent.getId() + "_waypoint_" + index;
            var circleOptions = {
                center: new google.maps.LatLng(waypoint.latitude, waypoint.longitude),
                radius: 7,
                fillColor: '#000000',
                fillOpacity: 0.0,
                strokeColor: '#000000',
                strokeOpacity: opacity,
                strokeWeight: 2,
                clickable: false,
                zIndex: 2
            };

            this.context.$el.gmap("addShape", "Circle", {
                id: waypointId,
                ...circleOptions
            });
        });
    },

    clearAgentRoute: function(agent) {
        this.clearAgentWaypointMarkers(agent); // Clear the dots

        var routeId = agent.getId() + "_route";
        var polyline = this.context.$el.gmap("get", "overlays > Polyline", [])[routeId];
        if (polyline) {
            polyline.setMap(null);
        }
    },

    renderAllRoutes: function() {
        this.context.state.agents.each(this.drawAgentRoute, this);
    },

    clearAllRoutes: function() {
        this.context.state.agents.each(this.clearAgentRoute, this);
    },

    // hash between 0 and 1
    generateHash: (string) => {
        let hash = 0;
        for (const char of string) {
            hash = (hash << 5) - hash + char.charCodeAt(0);
            hash |= 0; // Constrain to 32bit
        }
        return (hash >>> 0) / 0xFFFFFFFF;
    }
};