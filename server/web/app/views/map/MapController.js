var MapController = {
    predictionLength: 0,
    showUncertainties: false,
    uncertaintyRadius: 10,
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
        this.onScanModePressed = _.bind(this.onScanModePressed, context)
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
        this.showPredictedPaths = _.bind(this.showPredictedPaths, context);
        this.pushImage = _.bind(this.pushImage, context);
        this.getCurrentImage = _.bind(this.getCurrentImage, context);
        this.clearReviewImage = _.bind(this.clearReviewImage, context);

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
        $('#prediction_slider').on('change', function() {
            if ($(this).val() === $(this).prop('max')) {
                MapController.showPredictedPaths(100);  // hardcoded max of 100 steps for performance simplicity
            } else if ($(this).val() === $(this).prop('min')) {
                MapController.showPredictedPaths(0);
            } else {
                MapController.showPredictedPaths($(this).val());
            }
        });

        $('#uncertainties_toggle').change(function () {
            MapController.toggleUncertainties( $(this).is(":checked"));
        });

        $('#exit_button').on('click', function () {
            var exitConfirmed = confirm("Only exit the scenario early if you are sure you have found and classified all " +
                "of the targets \n Are you sure you want to exit?");
            if (exitConfirmed) {
                self.views.map.clearAll()
                $.post("/reset");
                var scenario_end_panel = document.createElement("div");
                scenario_end_panel.innerHTML = _.template($("#scenario_end_panel").html(), {
                    title: "Scenario Ended",
                    description: "This scenario has ended, please close your browser tab"
                });
                $.blockWithContent(scenario_end_panel);
                //$('#end_scenario').on('click', function () {
                    //window.history.back();
                //});
            }

        });

        //State listeners
        this.state.on("change:time", function () {
            MapController.onTick();
        });
        this.state.on("change:gameId", function () {
            // We don't display this, for fairness
            //$("#game_id").html("" + self.state.getGameId());
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
            MapController.swapMode(self.state.getEditMode(), false);
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
    showPredictedPaths: function (setting) {
        MapController.predictionLength = setting;
   },
    toggleUncertainties: function (setting) {
        MapController.showUncertainties = setting;
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
                    MapController.swapMode(1, true);
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
        else if (viewModeValue === "editmode")
            MapController.onEditModePressed();
        else {
            MapController.onScanModePressed();
        }
    },
    onMonitorModePressed: function () {
        if(this.state.getEditMode() === 2) {
            try {
                var mainAllocation = this.state.getAllocation();
                var tempAllocation = this.state.getTempAllocation();
                if (_.compareAllocations(mainAllocation, tempAllocation))
                    MapController.swapMode(1, true);
                else
                    MapController.abortAllocation();
            } catch (e) {
                console.log("MMP : " + e);
            }
        } else if (this.state.getEditMode() !== 1) {
            MapController.swapMode(1, true);
        }
    },
    onEditModePressed: function () {
        if(this.state.getEditMode() !== 2)
            try {
                MapController.swapMode(2, true);
            } catch (e) {
                console.log("EMP : " + e);
            }
    },
    onScanModePressed: function () {
        if(this.state.getEditMode() !== 3)
            try {
                MapController.swapMode(3, true);
            } catch (e) {
                console.log("SMP : " + e);
            }
    },
    onTick: function () {
        // TODO this is a temp feature and should be hardcoded
        var tempTime = this.state.getTime();
        var tempLimit = this.state.getTimeLimit();
        var time = $.fromTime(tempTime / 1);
        var limit = $.fromTime(tempLimit / 6);
        /*
        var time = $.fromTime(this.state.getTime());
        var limit = $.fromTime(this.state.getTimeLimit());
        //$("#game_time").html("Time: " + time);
         */
        $("#game_time").css('font-size', 24);
        $("#game_time").html("Time: " + time + "/" + limit);
        this.updateAllocationRendering();
        if (MapController.predictionLength > 0) {
            this.drawPredictedPath(MapController.predictionLength);
        } else {
            this.clearPredictions();
        }
        if (MapController.showUncertainties) {
            this.drawUncertainties(MapController.uncertaintyRadius);
        } else {
            this.clearUncertainties();
        }

        this.drawMarkers();

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
    /**
     * Swaps the UI mode (typically monitor/task view)
     * I have added a check for UI options specified in the scenario file -WH
     * @param modeFlag
     * @param sendUpdate
     */
    swapMode: function (modeFlag, sendUpdate) {
        // modeflag 1 = monitor
        //          2 = edit
        //          3 = images
        self = this;
        this.state.getUiOptions().forEach(function (option) {
            if (option === "predictions") {
                $("#prediction_wrapper_div").show();
            } else if (option === "uncertainties") {
                $("#uncertainties_wrapper_div").show();
            }
        });
        try {
            MapController.uncertaintyRadius = this.state.getUncertaintyRadius();
        } catch (e) {
           alert(e);
        }


        if(modeFlag === 2) {  // edit
            $("#monitor_accordions").hide();
            $("#edit_contexts").show();
            $("#edit_buttons_sub").show();
            $("#sandbox_buttons_sub").show();
            MapController.onUndoRedoAvailableChange();
            $("#scan_view").hide();

            $("#map_canvas").show();
            $("#image_review").hide();
            $("#review_panel").hide();


            $('#scanmode').prop("checked", false);
            $('#editmode').prop("checked", true);
            $('#monitor').prop("checked", false);
        } else if (modeFlag === 1) { // monitor
            $("#monitor_accordions").show();
            $("#edit_contexts").hide();
            $("#edit_buttons_sub").hide();
            $("#sandbox_buttons_sub").hide();
            $("#scan_view").hide();

            $("#map_canvas").show();
            $("#image_review").hide();
            $("#review_panel").hide();

            $('#scanmode').prop("checked", false);
            $('#editmode').prop("checked", false);
            $('#monitor').prop("checked", true);
        } else {  // scans
            $("#monitor_accordions").hide();
            $("#edit_contexts").hide();
            $("#edit_buttons_sub").hide();
            $("#sandbox_buttons_sub").hide();
            $("#scan_view").show();

            $("#map_canvas").hide();
            $("#image_review").show();
            $("#review_panel").show();

            self.views.images.checkAndUpdateDeepButton();
            MapImageController.resetCurrentImageData();
            self.views.review.update();

            $('#scanmode').prop("checked", true);
            $('#editmode').prop("checked", false);
            $('#monitor').prop("checked", false);
        }

        this.drawing.setDrawingMode(null);
        this.hideForGametype();
        if(sendUpdate)
            this.state.pushMode(modeFlag);
    },
    pushImage: function (id, iRef, update) {
        try {
            this.views.review.displayImage(id, iRef, update);
        } catch (e) {
            alert("PI: " + e)
        }
    },
    getCurrentImage: function () {
        return this.views.review.currentImageRef;
    },
    clearReviewImage : function () {
        this.views.review.clearImage();
    },
    abortAllocation: function() {
        var mainAllocation = this.state.getAllocation();
        var cancelConfirmed;
        if(Object.keys(mainAllocation).length)
            cancelConfirmed = confirm("Return to the original allocation?");
        else
            cancelConfirmed = confirm("This will result with an empty allocation. Continue?");
        if(cancelConfirmed)
            MapController.swapMode(1, true);
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