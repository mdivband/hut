var MapController = {
    /**
     * Binds all the methods to use the given context.
     *  This means the methods can be called just using MapController.method() without
     *  having to worry about calling from the correct context.
     * @param context - Context to bind to.
     */
    bind: function (context) {
        this.bindEvents = _.bind(this.bindEvents, context);
        this.onRunAutoAllocationClick = _.bind(this.onRunAutoAllocationClick, context);
        this.onAllocationUndoClick = _.bind(this.onAllocationUndoClick, context);
        this.onAllocationRedoClick = _.bind(this.onAllocationRedoClick, context);
        this.onAllocationResetClick = _.bind(this.onAllocationResetClick, context);
        this.onConfirmAllocationClick = _.bind(this.onConfirmAllocationClick, context);
        this.onViewModePressed = _.bind(this.onViewModePressed, context);
        this.onMonitorModePressed = _.bind(this.onMonitorModePressed, context);
        this.onEditModePressed = _.bind(this.onEditModePressed, context);
        this.swapMode = _.bind(this.swapMode, context);
        this.onTick = _.bind(this.onTick, context);
        this.onMapLeftClick = _.bind(this.onMapLeftClick, context);
        this.onMapRightClick = _.bind(this.onMapRightClick, context);
        this.onMapMarkerComplete = _.bind(this.onMapMarkerComplete, context);
        this.onMapPolylineComplete = _.bind(this.onMapPolylineComplete, context);
        this.onMapRectangleComplete = _.bind(this.onMapRectangleComplete, context);
        this.onTempAllocationChange = _.bind(this.onTempAllocationChange, context);
        this.onUndoRedoAvailableChange = _.bind(this.onUndoRedoAvailableChange, context);
        this.onCancelAllocationClick = _.bind(this.onCancelAllocationClick, context);
        this.abortAllocation = _.bind(this.abortAllocation, context);
        this.processWaypointChange = _.bind(this.processWaypointChange, context);
        this.processWaypointDelete = _.bind(this.processWaypointDelete, context);
    },
    /**
     * Bind listeners for map view.
     */
    bindEvents: function () {
        var self = this;

        $("#pan_mode").on('click', function () {
            self.setMode(self.ModeEnum.PAN);
        });
        $("#add_waypoint_task_mode").on('click', function () {
            self.setMode(self.ModeEnum.ADD_WAYPOINT_TASK);
        });
        $("#add_monitor_task_mode").on('click', function () {
            self.setMode(self.ModeEnum.ADD_MONITOR_TASK);
        });
        $("#add_patrol_task_mode").on('click', function () {
            self.setMode(self.ModeEnum.ADD_PATROL_TASK);
        });
        $("#add_region_task_mode").on('click', function () {
            self.setMode(self.ModeEnum.ADD_REGION_TASK);
        });
        $("#add_agent_mode").on('click', function () {
            self.setMode(self.ModeEnum.ADD_AGENT);
        });
        $("#allocation_undo").on('click', function () {
            MapController.onAllocationUndoClick()
        });
        $("#allocation_redo").on('click', function () {
            MapController.onAllocationRedoClick()
        });
        $("#allocation_reset").on('click', function () {
            MapController.onAllocationResetClick()
        });
        $("#run_auto_allocation").on('click', function () {
            MapController.onRunAutoAllocationClick()
        });
        $("#confirm_allocation").on('click', function () {
            MapController.onConfirmAllocationClick()
        });
        $("#cancel_allocation").on('click', function () {
            MapController.onCancelAllocationClick()
        });
        $("input:radio", "#view_mode").button().click(function () {
            MapController.onViewModePressed($(this).val())
        });

        //State listeners
        this.state.on("change:time", function () {
            MapController.onTick();
        });
        this.state.on("change:gameId", function () {
            $("#game_id").html("" + self.state.getGameId());
        });
        this.state.on("change:gameType", function () {
            var lat = self.state.getGameCentre().latitude;
            var lng = self.state.getGameCentre().longitude;
            if(lat !== 0 || lng !== 0)
                self.map.setCenter(new google.maps.LatLng(lat, lng));
        });
        this.state.on("change:tempAllocation", function () {
            MapController.onTempAllocationChange();
        });
        this.state.on("change:allocationUndoAvailable change:allocationRedoAvailable", function () {
            MapController.onUndoRedoAvailableChange();
        });
        this.state.on("change:editMode", function () {
            MapController.swapMode(self.state.isEdit(), false);
        });

        //Map listeners
        google.maps.event.addListener(this.map, "click", function (event) {
            MapController.onMapLeftClick(event);
        });
        google.maps.event.addListener(this.map, "rightclick", function (event) {
            MapController.onMapRightClick(event);
        });
        google.maps.event.addListener(this.drawing, "markercomplete", function (marker) {
            MapController.onMapMarkerComplete(marker);
        });
        google.maps.event.addListener(this.drawing, "polylinecomplete", function (polyline) {
            MapController.onMapPolylineComplete(polyline);
        });
        google.maps.event.addListener(this.drawing, "rectanglecomplete", function (rectangle) {
            MapController.onMapRectangleComplete(rectangle);
        });
    },
    onRunAutoAllocationClick: function () {
        $.post("/allocation/auto-allocate");
    },
    onAllocationUndoClick: function () {
        var self = this;
        $.post("/allocation/undo", function () {
            self.state.fetch({});
        });
    },
    onAllocationRedoClick: function () {
        var self = this;
        $.post("/allocation/redo", function () {
            self.state.fetch({});
        });
    },
    onAllocationResetClick: function () {
        var self = this;
        $.post("/allocation/reset", function () {
            self.state.fetch({});
        });
    },
    onConfirmAllocationClick: function () {
        var self = this;
        $.post("/allocation/confirm", function () {
            self.state.fetch({
                success: function () {
                    MapController.swapMode(false, true);
                }
            });
        });
    },
    onCancelAllocationClick: function () {
        MapController.abortAllocation();
    },
    onViewModePressed: function (viewModeValue) {
        if (viewModeValue === "monitor")
            MapController.onMonitorModePressed();
        else
            MapController.onEditModePressed();
    },
    onMonitorModePressed: function () {
        if(this.state.isEdit()) {
            var mainAllocation = this.state.getAllocation();
            var tempAllocation = this.state.getTempAllocation();
            if(_.compareAllocations(mainAllocation, tempAllocation))
                MapController.swapMode(false, true);
            else
                MapController.abortAllocation();
        }
    },
    onEditModePressed: function () {
        if(!this.state.isEdit())
            MapController.swapMode(true, true);
    },
    onTick: function () {
        var time = $.fromTime(this.state.getTime());
        $("#game_time").html("Time: " + time);
        this.updateAllocationRendering();
        MapHazardController.updateHeatmap(-1);
        MapHazardController.updateHeatmap(0);
        MapHazardController.updateHeatmap(1);
    },
    onMapLeftClick: function (event) {
        if (this.views.clickedAgent != null)
            this.updateClickedAgent(null);
    },
    onMapRightClick: function (event) {
        var lat = event.latLng.lat();
        var lng = event.latLng.lng();
        console.log('Right click on map event at ' + lat + ", " + lng);
    },
    onMapMarkerComplete: function (marker) {
        //Uses created marker to post a create message to the server.
        //Then deletes original marker to be replaced by newly created marker when server responds.
        var latlng = _.coordinate(marker.getPosition());
        if (this.mapMode === this.ModeEnum.ADD_WAYPOINT_TASK) {
            $.post("/tasks", {
                type: this.state.tasks.TASK_WAYPOINT,
                lat: latlng.latitude,
                lng: latlng.longitude
            });
        }
        else if (this.mapMode === this.ModeEnum.ADD_MONITOR_TASK) {
            $.post("/tasks", {
                type: this.state.tasks.TASK_MONITOR,
                lat: latlng.latitude,
                lng: latlng.longitude
            });
        }
        else if (this.mapMode === this.ModeEnum.ADD_AGENT) {
            $.post("/agents", {
                lat: latlng.latitude,
                lng: latlng.longitude,
                heading: 0.0
            });
        }
        marker.setMap(null);
        delete marker;
    },
    onMapPolylineComplete: function (polyline) {
        if (this.mapMode === this.ModeEnum.ADD_PATROL_TASK) {
            var path = [];
            for (var i = 0; i < polyline.getPath().length; i++) {
                var point = polyline.getPath().getAt(i);
                path.push(point.lat(), point.lng());
            }
            $.post("/tasks/patrol", {path: path.toString()});
            polyline.setMap(null);
            delete polyline;
        }
    },
    onMapRectangleComplete: function (rectangle) {
        if (this.mapMode === this.ModeEnum.ADD_REGION_TASK) {
            var corners = [];
            var bounds = rectangle.getBounds();
            var ne = bounds.getNorthEast();
            var sw = bounds.getSouthWest();
            var nw = new google.maps.LatLng(ne.lat(), sw.lng());
            var se = new google.maps.LatLng(sw.lat(), ne.lng());
            corners.push(nw.lat(), nw.lng());
            corners.push(ne.lat(), ne.lng());
            corners.push(se.lat(), se.lng());
            corners.push(sw.lat(), sw.lng());
            $.post("/tasks/region", {corners: corners.toString()});
            rectangle.setMap(null);
            delete rectangle;
        }
    },
    onTempAllocationChange: function () {
        this.updateAllocationRendering();
    },
    onUndoRedoAvailableChange: function () {
        $("#allocation_undo").prop('disabled', !this.state.isAllocationUndoAvailable());
        $("#allocation_redo").prop('disabled', !this.state.isAllocationRedoAvailable());
    },
    swapMode: function (toEditMode, sendUpdate) {
        if(toEditMode) {
            $("#monitor_accordions").hide();
            $("#edit_contexts").show();
            $("#edit_buttons_sub").show();
            $("#sandbox_buttons_sub").show();
            MapController.onUndoRedoAvailableChange();
        } else {
            $("#monitor_accordions").show();
            $("#edit_contexts").hide();
            $("#edit_buttons_sub").hide();
            $("#sandbox_buttons_sub").hide();
        }
        $('#monitor').prop("checked", !toEditMode);
        $('#editmode').prop("checked", toEditMode);
        this.drawing.setDrawingMode(null);
        this.hideForGametype();
        if(sendUpdate)
            this.state.toggleEdit(toEditMode);
    },
    abortAllocation: function() {
        var mainAllocation = this.state.getAllocation();
        var cancelConfirmed;
        if(Object.keys(mainAllocation).length)
            cancelConfirmed = confirm("Return to the original allocation?");
        else
            cancelConfirmed = confirm("This will result with an empty allocation. Continue?");
        if(cancelConfirmed)
            MapController.swapMode(false, true);
    },
    processWaypointChange: function(agentId, polyline, vertex, insert) {
        var path = polyline.getPath();
        var pathSize = path.length;

        //Hide undo button
        var undoImgEl = $('img[src=\"http://maps.gstatic.com/mapfiles/undo_poly.png\"]');
        if (undoImgEl)
            undoImgEl.hide();

        //If start waypoint dragged, insert new point at beginning of route
        // (route doesn't include start point hence index zero not one)
        if (vertex === 0) {
            $.post("/agents/route/add/" + agentId, {
                index: 0,
                lat: path.getAt(0).lat(),
                lng: path.getAt(0).lng()
            });
        }
        //If last waypoint dragged, insert new point before last point in route
        else if (vertex === pathSize - 1) {
            $.post("/agents/route/add/" + agentId, {
                index: pathSize - 2,
                lat: path.getAt(pathSize - 1).lat(),
                lng: path.getAt(pathSize - 1).lng()
            });
        }
        //If any other point is dragged, add or edit vertex as required.
        // Index passed to server is one less than index on client as server route doesn't include the start point
        // (current agent position)
        else {
            if (insert) {
                $.post("/agents/route/add/" + agentId, {
                    index: vertex - 1,
                    lat: path.getAt(vertex).lat(),
                    lng: path.getAt(vertex).lng()
                });
            }
            else {
                $.post("/agents/route/edit/" + agentId, {
                    index: vertex - 1,
                    lat: path.getAt(vertex).lat(),
                    lng: path.getAt(vertex).lng()
                });
            }
        }
    },
    processWaypointDelete: function(agentId, vertex) {
        $.ajax({
            url: "/agents/route/" + agentId,
            type: 'DELETE',
            data: {
                index: vertex - 1
            }
        });
    }
};