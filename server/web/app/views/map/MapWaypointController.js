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
        const onAgentChange = (agent) => {
            if (this.context.state.get('pathPlanning')) {
                this.drawAgentRoute(agent);
            } else {
                this.clearAgentRoute(agent);
            }
        };

        this.context.state.agents.each((agent) => {
            agent.on('change:coordinate change:route', onAgentChange, this);
        });

        this.context.state.agents.on('add', (agent) => {
            agent.on('change:coordinate change:route', onAgentChange, this);
            onAgentChange(agent);
        }, this);

        this.context.state.agents.on('remove', (agent) => {
            agent.off('change:coordinate change:route', onAgentChange, this);
            this.clearAgentRoute(agent);
        }, this);


        this.context.state.on('change:pathPlanning', (model, isEnabled) => {
            if (isEnabled) {
                this.renderAllRoutes();
            } else {
                this.clearAllRoutes();
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

    // TODO: cache last waypoint index
    _findCurrentWaypointIndex: function(agentPosition, fullRoute) {
        if (!fullRoute || fullRoute.length === 0) {
            return -1;
        }
        if (fullRoute.length === 1) {
            return 0;
        }

        // some projection calculations to locate within a segment
        const getProjectionOnSegment = (p, v, w) => {
            const l2 = google.maps.geometry.spherical.computeDistanceBetween(v, w);
            if (l2 === 0.0) return { point: v, distance: google.maps.geometry.spherical.computeDistanceBetween(p, v), onSegment: true };

            const heading_vw = google.maps.geometry.spherical.computeHeading(v, w);
            const heading_vp = google.maps.geometry.spherical.computeHeading(v, p);
            const dist_vp = google.maps.geometry.spherical.computeDistanceBetween(v, p);

            const angle = (heading_vp - heading_vw) * (Math.PI / 180.0);
            const projectionDistance = Math.cos(angle) * dist_vp;

            let closestPoint;
            let onSegment = false;
            if (projectionDistance < 0) {
                closestPoint = v;
            } else if (projectionDistance > l2) {
                closestPoint = w;
            } else {
                closestPoint = google.maps.geometry.spherical.computeOffset(v, projectionDistance, heading_vw);
                onSegment = true;
            }

            const perpendicularDistance = google.maps.geometry.spherical.computeDistanceBetween(p, closestPoint);
            return { point: closestPoint, distance: perpendicularDistance, onSegment: onSegment };
        };

        for (let i = 0; i < fullRoute.length - 1; i++) {
            const startWpPos = new google.maps.LatLng(fullRoute[i].latitude, fullRoute[i].longitude);
            const endWpPos = new google.maps.LatLng(fullRoute[i+1].latitude, fullRoute[i+1].longitude);

            const segmentLength = google.maps.geometry.spherical.computeDistanceBetween(startWpPos, endWpPos);
            const tolerance = segmentLength / 20;

            const projection = getProjectionOnSegment(agentPosition, startWpPos, endWpPos);

            if (projection.distance <= tolerance && projection.onSegment) {
                return i;
            }
        }

        // Fallback
        let closestIndex = 0;
        let minDistance = Number.MAX_VALUE;
        fullRoute.forEach((waypoint, index) => {
            const waypointPosition = new google.maps.LatLng(waypoint.latitude, waypoint.longitude);
            const distance = google.maps.geometry.spherical.computeDistanceBetween(agentPosition, waypointPosition);
            if (distance < minDistance) {
                minDistance = distance;
                closestIndex = index;
            }
        });
        return closestIndex;
    },

    drawAgentRoute: function(agent) {
        this.clearAgentWaypointMarkers(agent);

        const fullRoute = agent.getRoute();
        const agentPosition = agent.getPosition();
        const predictionDistance = MapController.predictionLength;

        if (!fullRoute || fullRoute.length === 0) {
            this.clearAgentRoute(agent);
            return;
        }

        const currentIndex = this._findCurrentWaypointIndex(agentPosition, fullRoute);
        if (currentIndex === -1) {
            this.clearAgentRoute(agent);
            return;
        }

        const startIndex = Math.max(0, currentIndex - predictionDistance);
        const endIndex = Math.min(fullRoute.length - 1, currentIndex + predictionDistance);

        const routeToDraw = fullRoute.slice(startIndex, endIndex + 1);

        const allAgents = this.context.state.agents;
        const totalAgents = allAgents.length;
        const agentIndex = allAgents.indexOf(agent);
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

        const waypointLatLngs = routeToDraw.map(function(c) {
            return new google.maps.LatLng(c.latitude, c.longitude);
        });

        const displayPath = [agentPosition, ...waypointLatLngs];

        if (displayPath.length < 2) {
            this.clearAgentRoute(agent);
            return;
        }

        var polylineOptions = {
            path: displayPath,
            strokeColor: '#000000',
            strokeOpacity: opacity,
            strokeWeight: 2,
            zIndex: 1,
            icons: [{
                icon: { path: google.maps.SymbolPath.FORWARD_OPEN_ARROW },
                offset: '100%'
            }]
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

        waypointLatLngs.forEach((waypoint, index) => {
            var waypointId = agent.getId() + "_waypoint_" + (startIndex + index);
            var circleOptions = {
                center: waypoint,
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