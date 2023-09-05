var MapAgentController = {
    //Is in manual allocation mode
    isManuallyAllocating: false,
    //The task id that will be manually allocated once the user stops dragging the allocation arrow
    taskIdToAllocateManually: null,
    /**
     * Binds all the methods to use the given context.
     *  This means the methods can be called just using MapAgentController.method() without
     *  having to worry about calling from the correct context.
     * @param context - Context to bind to.
     */
    bind: function (context) {
        this.bindEvents = _.bind(this.bindEvents, context);
        this.onAgentAdd = _.bind(this.onAgentAdd, context);
        this.onAgentChange = _.bind(this.onAgentChange, context);
        this.onAgentRemove = _.bind(this.onAgentRemove, context);
        this.onAgentTimeOut = _.bind(this.onAgentTimeOut, context);
        this.onAgentReconnect = _.bind(this.onAgentReconnect, context);
        this.onAgentMarkerLeftClick = _.bind(this.onAgentMarkerLeftClick, context);
        this.onAgentMarkerRightClick = _.bind(this.onAgentMarkerRightClick, context);
        this.onAgentMarkerDrag = _.bind(this.onAgentMarkerDrag, context);
        this.onAgentMarkerDragEnd = _.bind(this.onAgentMarkerDragEnd, context);
        this.updateAgentMarkerIcon = _.bind(this.updateAgentMarkerIcon, context);
        this.updateAllAgentMarkerIcons = _.bind(this.updateAllAgentMarkerIcons, context);
        this.drawAgentBattery = _.bind(this.drawAgentBattery, context);
        this.updateAgentMarkerVisibility = _.bind(this.updateAgentMarkerVisibility, context);
        this.onGhostAdd = _.bind(this.onGhostAdd, context);
        this.onGhostChange = _.bind(this.onGhostChange, context);
    },
    /**
     * Bind listeners for agent state add, change and remove events
     */
    bindEvents: function () {
        this.state.agents.on("add", function (agent) {
            MapAgentController.onAgentAdd(agent);
        });
        this.state.ghosts.on("add", function (agent) {
            MapAgentController.onGhostAdd(agent);
        });
        this.state.agents.on("change", function (agent) {
            MapAgentController.onAgentChange(agent);
        });
        this.state.ghosts.on("change", function (agent) {
            MapAgentController.onGhostChange(agent);
        });
        this.state.agents.on("remove", function (agent) {
            MapAgentController.onAgentRemove(agent);
        });
        this.state.ghosts.on("remove", function (agent) {
            MapAgentController.onAgentRemove(agent);
        });
        this.state.agents.on("change:timedOut", function (agent) {
            if (agent.isTimedOut())
                MapAgentController.onAgentTimeOut(agent);
            else
                MapAgentController.onAgentReconnect(agent);
        });
        this.state.agents.on("change:visible", function (agent) {
            MapAgentController.updateAgentMarkerVisibility(agent)
        });
        this.state.ghosts.on("change:visible", function (agent) {
            MapAgentController.updateAgentMarkerVisibility(agent)
        });
    },
    onAgentAdd: function (agent) {
        console.log('Agent added ' + agent.getId());
        var id = agent.getId();

        this.$el.gmap("addMarker", {
            bounds: !agent.isSimulated(), //Centre in map if real agent
            marker: MarkerWithLabel,
            draggable: true, //Allows use of drag and dragend events even though the marker shouldn't be moved by dragging.
            labelContent: id,
            labelAnchor: new google.maps.Point(22, -18),
            labelClass: "labels",
            labelStyle: {opacity: 1.0},
            id: id,
            position: agent.getPosition(),
            heading: agent.getHeading(),
            raiseOnDrag: false,
            zIndex: 2,
            visible: agent.isVisible(),
        });

        var uid = agent.getId() + "_add";
        var content = _.template($("#popup_left_right").html(), {
            left_content: agent.getId() + " added",
            right_content: "View",
            uid: uid
        });

        spop({
            template: content,
            style: 'default'
        });

        //If real agent is added, zoom to it
        if (!agent.isSimulated())
            this.map.setZoom(19);

        MapAgentController.updateAgentMarkerIcon(agent);
        var marker = this.$el.gmap("get", "markers")[id];

        $(marker).click(function () {
            MapAgentController.onAgentMarkerLeftClick(marker);
        }).rightclick(function () {
            MapAgentController.onAgentMarkerRightClick(marker);
        }).drag(function () {
            MapAgentController.onAgentMarkerDrag(marker);
        }).dragend(function () {
            MapAgentController.onAgentMarkerDragEnd(marker);
        });
    },
    /**
     * Adds a ghost agent marker to the map
     * @param agent
     */
    onGhostAdd: function (agent) {
        console.log('Ghost added ' + agent.getId());
        var id = agent.getId();

        this.$el.gmap("addMarker", {
            marker: MarkerWithLabel,
            draggable: false, //Allows use of drag and dragend events even though the marker shouldn't be moved by dragging.
            labelContent: id,
            labelAnchor: new google.maps.Point(22, -18),
            labelClass: "labels",
            labelStyle: {opacity: 1.0},
            id: id,
            position: agent.getPosition(),
            heading: agent.getHeading(),
            raiseOnDrag: false,
            zIndex: 2,
        });
        MapAgentController.updateAgentMarkerIcon(agent);
    },
    onAgentChange: function (agent) {
        var marker = this.$el.gmap("get", "markers")[agent.getId()];
        if (marker)
            MapAgentController.updateAgentMarkerIcon(agent);
        this.updateTable();
        MapTargetController.checkForReveal(agent);
    },
    onGhostChange: function (agent) {
        var marker = this.$el.gmap("get", "markers")[agent.getId()];
        if (marker)
            MapAgentController.updateAgentMarkerIcon(agent);
        this.updateTable();
    },
    onAgentRemove: function (agent) {
        console.log('Agent removed ' + agent.getId());

        var uid = agent.getId() + "_removed";
        var content = _.template($("#popup_left_right").html(), {
            left_content: agent.getId() + " has been removed",
            right_content: "View",
            uid: uid
        });

        spop({
            template: content,
            style: 'default'
        });


        var marker = this.$el.gmap("get", "markers")[agent.getId()];
        if (marker) {
            marker.setMap(null);
            marker = null;
            delete marker;
        }
        agent.destroy();

        var id = agent.getId();
        var mainLineId = id + "main";
        var mainMarker = this.$el.gmap("get", "overlays > Polyline", [])[mainLineId];
        if (mainMarker) {
            mainMarker.setMap(null);
            mainMarker = null;
            delete mainMarker;
        }
    },
    onAgentTimeOut: function (agent) {
        var self = this;
        var uid = agent.getId() + "_dropout";
        var content = _.template($("#popup_left_right").html(), {
            left_content: "Lost communication with " + agent.getId(),
            right_content: "View",
            uid: uid
        });

        spop({
            template: content,
            style: 'error'
        });

        $("#" + uid).on('click', function() {
            self.map.panTo(agent.getPosition());
            self.map.setZoom(19);
        });
    },
    onAgentReconnect: function (agent) {
        var self = this;
        var uid = agent.getId() + "_reconnect";
        var content = _.template($("#popup_left_right").html(), {
            left_content: agent.getId() + " reconnected",
            right_content: "View",
            uid: uid
        });

        spop({
            template: content,
            style: 'info'
        });

        $("#" + uid).on('click', function() {
            self.map.panTo(agent.getPosition());
            self.map.setZoom(19);
        });
    },
    onAgentMarkerLeftClick: function (marker) {
        var agent = this.state.agents.get(marker.id);
        if (agent.getId() === this.views.clickedAgent)
            this.updateClickedAgent(null);
        else
            this.updateClickedAgent(agent);
        MapAgentController.updateAllAgentMarkerIcons();
    },
    onAgentMarkerRightClick: function (marker) {
        var self = this;
        var agent = this.state.agents.get(marker.id);

        this.$el.gmap("openInfoWindow", {minWidth: 300}, marker, function (iw) {
            var property = document.createElement("div");

            property.innerHTML = _.template($("#agent_edit").html(), {
                agent_id: agent.getId(),
                simulated: agent.isSimulated()
            });
            iw.setContent(property);

            google.maps.event.addListener(iw, 'domready', function(){
                var dropoutButton = $("#agent_edit_dropout");
                var deleteButton = $("#agent_edit_delete");

                if(!agent.isSimulated() || self.state.getGameType() === self.state.GAME_TYPE_SCENARIO) {
                    deleteButton.hide();
                    dropoutButton.hide();
                }
                else if (!self.state.isEdit())
                    deleteButton.hide();

                if(agent.isTimedOut())
                    dropoutButton.html('Reconnect');
                else
                    dropoutButton.html('Trigger Blackout');

                dropoutButton.on('click', function () {
                    console.log("Drop out clicked");
                    $.post("/agents/time-out/" + agent.getId(), {timedOut: !agent.isTimedOut()});
                    iw.close();
                });
                deleteButton.on('click', function () {
                    if (confirm("Are you sure you want to delete " + agent.getId() + "?")) {
                        $.ajax({
                            url: "/agents/" + agent.getId(),
                            type: 'DELETE'
                        });
                    }
                });
            });
        });
    },
    onAgentMarkerDrag: function (marker) {
        var agent = this.state.agents.get(marker.id);
        if(this.state.isEdit() && !agent.isTimedOut() && !agent.getManuallyControlled()) {
            //Agent marker dragging is used for manual allocation
            MapAgentController.isManuallyAllocating = true;

            //Grab marker position (under cursor) then reposition marker to agent position so it doesn't actually move.
            var cursorPosition = marker.getPosition();
            marker.setPosition(agent.getPosition());

            //Get path from agent position to cursor or task marker that is hovered over.
            var arrowEnd = MapAgentController.taskIdToAllocateManually ?
                this.$el.gmap("get", "markers")[MapAgentController.taskIdToAllocateManually].getPosition() : cursorPosition;
            var path = [agent.getPosition(), arrowEnd];

            //Draw arrow
            var polyline = this.$el.gmap("get", "overlays > Polyline", [])['manual_allocation'];
            if (polyline) {
                if (!polyline.getMap())
                    polyline.setMap(this.map);
                polyline.setOptions({path: path});
            }
            else {
                this.$el.gmap("addShape", "Polyline", {
                    id: 'manual_allocation',
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
                    strokeWeight: 5,
                    zIndex: 3
                });
            }
        }
        else {
            //Reposition marker to agent position so it doesn't actually move.
            marker.setPosition(agent.getPosition());
            //Ensure allocation arrow is hidden.
            this.hidePolyline('manual_allocation')
        }
    },
    onAgentMarkerDragEnd: function (marker) {
        var agent = this.state.agents.get(marker.id);

        //No longer manually allocating since drag has ended
        MapAgentController.isManuallyAllocating = false;

        //If a task marker is being hovered over, allocate the agent to it
        if(MapAgentController.taskIdToAllocateManually)
            $.post("/allocation/allocate", {agentId: agent.getId(), taskId: MapAgentController.taskIdToAllocateManually});

        //Reposition marker to agent position so it doesn't actually move.
        marker.setPosition(agent.getPosition());

        MapAgentController.taskIdToAllocateManually = null;
        this.hidePolyline('manual_allocation')
    },
    updateAgentMarkerIcon: function (agent) {
        var marker = this.$el.gmap("get", "markers")[agent.getId()];
        var icon;
        if (agent.getId() === this.views.clickedAgent)
            icon = this.icons.UAVSelected;
        else {
            if(agent.getType() === "hub") {
                icon = this.icons.FLAG;
                marker.setOptions({clickable: false, draggable: false})
            } else if (agent.getType() === "ghost") {
                icon = this.icons.UAVTimedOut;
                marker.setOptions({clickable: false, draggable: false})
            } else if(agent.getManuallyControlled() || agent.getType() === "leader") {
                icon = this.icons.UAVManual;
            } else if (agent.getType() === "withpack") {
                icon = this.icons.UAVWithPack;
            } else if(agent.isTimedOut()) {
                icon = this.icons.UAVTimedOut;
            } else {
                icon = this.icons.UAV;
            }
        }
        marker.setIcon(icon.Image);
        marker.setPosition(agent.getPosition());
        //Rotate agent marker - seems clunky but GoogleMapsAPI doesn't allow for marker rotation...
        if (marker.icon) {
            //Add agent id to end of marker url, this makes them unique.
            marker.icon.url = marker.icon.url + "#" + agent.getId();
            //Grab actual marker element by the (now unique) image src and rotate it by the agent's heading
            var markerImgEl = $('img[src=\"' + marker.icon.url + '\"]');
            markerImgEl.css({
                'transform': 'rotate(' + agent.getHeading() + 'deg)'
            });
            MapAgentController.drawAgentBattery(markerImgEl.parent(), agent);
        }

    },
    updateAgentMarkerVisibility: function (agent) {
        var marker = this.$el.gmap("get", "markers")[agent.getId()];
        marker.setVisible(agent.isVisible());
        console.log("set " + agent.getId() + " is now " + agent.isVisible())
    },
    updateAllAgentMarkerIcons: function () {
        this.state.agents.each(function (agent) {
            MapAgentController.updateAgentMarkerIcon(agent);
        });
    },
    drawAgentBattery: function (container, agent, rotation) {
        var id = "MarkerCanvas_" + agent.getId();
        var canvasEl = $('#' + id);
        var canvas;
        if(canvasEl.length) {
            canvas = canvasEl[0];
        } else {
            canvas = document.createElement('canvas');
            canvas.id = id;
            canvas.width = container.width();
            canvas.height = container.height() + 15;
            container.append(canvas);
        }

        var batteryLevel = agent.getBattery();
        var maxBatteryWidth = 50;
        var batteryWidth = maxBatteryWidth*batteryLevel;

        var ctx = canvas.getContext("2d");
        ctx.clearRect(0, 0, canvas.width, canvas.height);
        ctx.filter = 'hue-rotate(' + (10 + batteryLevel*100) + 'deg) brightness(1.5)';
        ctx.fillStyle = "red";
        ctx.fillRect(5, 71, batteryWidth, 4);


        container.css({
            'overflow': 'visible'
        });
    }
};