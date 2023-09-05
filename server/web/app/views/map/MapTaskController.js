var MapTaskController = {
    /**
     * Binds all the methods to use the given context.
     *  This means the methods can be called just using MapTaskController.method() without
     *  having to worry about calling from the correct context.
     * @param context - Context to bind to.
     */
    bind: function (context) {
        this.bindEvents = _.bind(this.bindEvents, context);
        this.onTaskAdd = _.bind(this.onTaskAdd, context);
        this.onTaskChange = _.bind(this.onTaskChange, context);
        this.onTaskRemove = _.bind(this.onTaskRemove, context)
        this.onTaskMarkerLeftClick = _.bind(this.onTaskMarkerLeftClick, context);
        this.onTaskMarkerRightClick = _.bind(this.onTaskMarkerRightClick, context);
        this.onTaskMarkerDrag = _.bind(this.onTaskMarkerDrag, context);
        this.onTaskMarkerDragEnd = _.bind(this.onTaskMarkerDragEnd, context);
        this.onTaskMarkerMouseover = _.bind(this.onTaskMarkerMouseover, context);
        this.onTaskMarkerMouseout = _.bind(this.onTaskMarkerMouseout, context);
        this.onTaskCompleted = _.bind(this.onTaskCompleted, context);
        this.updateTaskRendering = _.bind(this.updateTaskRendering, context);
        this.updateTaskMarkerIcon = _.bind(this.updateTaskMarkerIcon, context);
        this.processPatrolTaskPathChange = _.bind(this.processPatrolTaskPathChange, context);
        this.onPatrolTaskRightClick = _.bind(this.onPatrolTaskRightClick, context);
        this.openTaskEditWindow = _.bind(this.openTaskEditWindow, context);
        this.processRegionTaskChange = _.bind(this.processRegionTaskChange, context);
    },
    /**
     * Bind listeners for task state add, change and remove events
     */
    bindEvents: function () {
        this.state.tasks.on("add", function (task) {
            MapTaskController.onTaskAdd(task);
        });
        this.state.tasks.on("change", function (task) {
            MapTaskController.onTaskChange(task);
        });
        this.state.tasks.on("remove", function (task) {
            MapTaskController.onTaskRemove(task);
        });

        this.state.completedTasks.on("add", function (task) {
            MapTaskController.onTaskCompleted(task);
        });
    },
    onTaskAdd: function (task) {
        console.log("Task added " + task.getId());
        if(task.getType() === this.state.tasks.TASK_WAYPOINT || task.getType() === this.state.tasks.TASK_MONITOR || task.getType() === this.state.tasks.TASK_VISIT) {
            this.$el.gmap("addMarker", {
                bounds: false,
                draggable: true,
                id: task.getId(),
                position: task.getPosition(),
                marker: MarkerWithLabel,
                labelContent: task.getId(),
                labelAnchor: new google.maps.Point(25, 65),
                labelClass: "labels",
                labelStyle: {opacity: 1.0},
                raiseOnDrag: false,
                zIndex: 3
            });
            var marker = this.$el.gmap("get", "markers")[task.getId()];
            MapTaskController.updateTaskRendering(task.getId(), this.MarkerColourEnum.RED);
            $(marker).click(function () {
                MapTaskController.onTaskMarkerLeftClick(marker);
            }).rightclick(function () {
                MapTaskController.onTaskMarkerRightClick(marker);
            }).drag(function () {
                MapTaskController.onTaskMarkerDrag(marker);
            }).dragend(function () {
                MapTaskController.onTaskMarkerDragEnd(marker);
            }).mouseover(function () {
                MapTaskController.onTaskMarkerMouseover(marker);
            }).mouseout(function () {
                MapTaskController.onTaskMarkerMouseout(marker);
            });
        }
        else if(task.getType() === this.state.tasks.TASK_PATROL) {
            var path = [];
            for (var i = 0; i < task.getPoints().length; i++) {
                var point = task.getPoints()[i];
                path.push(_.position(point.latitude, point.longitude));
            }

            this.$el.gmap("addShape", "Polyline", {
                id: task.getId(),
                editable: true,
                path: path,
                strokeOpacity: 0.8,
                strokeColor: 'black',
                strokeWeight: 5,
                zIndex: 0
            });
            var polyline = this.$el.gmap("get", "overlays > Polyline", [])[task.getId()];
            google.maps.event.addListener(polyline.getPath(), 'insert_at', function (vertex) {
                MapTaskController.processPatrolTaskPathChange(task.getId(), polyline, vertex);
            });
            google.maps.event.addListener(polyline.getPath(), 'set_at', function (vertex) {
                MapTaskController.processPatrolTaskPathChange(task.getId(), polyline, vertex);
            });
            google.maps.event.addListener(polyline.getPath(), 'remove_at', function (vertex) {
                MapTaskController.processPatrolTaskPathChange(task.getId(), polyline, vertex);
            });
            google.maps.event.addListener(polyline, "rightclick", function (event) {
                MapTaskController.onPatrolTaskRightClick(task.getId(), polyline, event)
            });

            this.$el.gmap("addMarker", {
                bounds: false,
                draggable: false,
                id: task.getId(),
                position: task.getPosition(),
                zIndex: 3
            });
            var marker = this.$el.gmap("get", "markers")[task.getId()];
            $(marker).mouseover(function () {
                MapTaskController.onTaskMarkerMouseover(marker);
            }).mouseout(function () {
                MapTaskController.onTaskMarkerMouseout(marker);
            });
        }
        else if(task.getType() === this.state.tasks.TASK_REGION) {
            var path = [];
            for (var i = 0; i < task.getPoints().length; i++) {
                var point = task.getPoints()[i];
                path.push(_.position(point.latitude, point.longitude));
            }
            this.$el.gmap("addShape", "Polyline", {
                id: task.getId(),
                editable: false,
                clickable: false,
                path: path,
                strokeOpacity: 0.3,
                strokeColor: 'black',
                strokeWeight: 5,
                zIndex: 0
            });

            var corners = task.getCorners();
            var sw = _.position(corners[3].latitude, corners[3].longitude);
            var ne = _.position(corners[1].latitude, corners[1].longitude);
            this.$el.gmap("addShape", "Rectangle", {
                id: task.getId(),
                editable: true,
                bounds: new google.maps.LatLngBounds(sw, ne),
                strokeOpacity: 0.8,
                strokeColor: 'black',
                strokeWeight: 5,
                zIndex: 0
            });
            var rectangle = this.$el.gmap("get", "overlays > Rectangle", [])[task.getId()];
            google.maps.event.addListener(rectangle, "bounds_changed", function() {
                MapTaskController.processRegionTaskChange(task.getId(), rectangle)
            });
            google.maps.event.addListener(rectangle, "rightclick", function(event) {
                MapTaskController.openTaskEditWindow(task, event.latLng);
            });

            this.$el.gmap("addMarker", {
                bounds: false,
                draggable: false,
                id: task.getId(),
                position: task.getPosition(),
                zIndex: 3
            });
            var marker = this.$el.gmap("get", "markers")[task.getId()];
            $(marker).mouseover(function () {
                MapTaskController.onTaskMarkerMouseover(marker);
            }).mouseout(function () {
                MapTaskController.onTaskMarkerMouseout(marker);
            });
        }
    },
    onTaskChange: function (task) {
        var marker = this.$el.gmap("get", "markers")[task.getId()];
        if (marker)
            marker.setPosition(task.getPosition());
        this.views.control.trigger("update:agents");
    },
    onTaskRemove: function (task) {
        console.log('Task removed ' + task.getId());
        if(task.getType() === this.state.tasks.TASK_WAYPOINT || task.getType() === this.state.tasks.TASK_MONITOR) {
            var marker = this.$el.gmap("get", "markers")[task.getId()];
            if (marker) {
                marker.setMap(null);
                delete marker;
            }
        }
        else if(task.getType() === this.state.tasks.TASK_PATROL) {
            var polyline = this.$el.gmap("get", "overlays > Polyline", [])[task.getId()];
            if (polyline) {
                polyline.setMap(null);
                delete polyline;
            }
        }
        else if(task.getType() === this.state.tasks.TASK_REGION) {
            var polyline = this.$el.gmap("get", "overlays > Polyline", [])[task.getId()];
            if (polyline) {
                polyline.setMap(null);
                delete polyline;
            }
            var rectangle = this.$el.gmap("get", "overlays > Rectangle", [])[task.getId()];
            if (rectangle) {
                rectangle.setMap(null);
                delete rectangle;
            }
        }
        task.destroy();
    },
    /**
     * This is redundant now, as the reporting of a completed task is handled later. I will leave it for now in case of
     *  rollback
     * @param task
     */
    onTaskCompleted: function (task) {
        console.log("Task completed " + task.getId());
        var self = this;
        /*
        var uid = task.getId() + "_completed";
        var content = _.template($("#popup_left_right").html(), {
            left_content: task.getId() + " has been completed",
            right_content: "View",
            uid: uid
        });

        spop({
            template: content,
            style: 'default'
        });

        $("#" + uid).on('click', function () {
            self.map.panTo(task.getPosition());
            self.map.setZoom(19);
        });

         */

        // TODO Determine if this is wrong to add here?
        var marker = this.$el.gmap("get", "markers")[task.getId()];
        if (marker) {
            marker.setMap(null);
            delete marker;
        }


    },
    onTaskMarkerLeftClick: function (marker) {},
    onTaskMarkerRightClick: function (marker) {
        var task = this.state.tasks.get(marker.id);
        MapTaskController.openTaskEditWindow(task, marker.getPosition());
    },
    onTaskMarkerDrag: function (marker) {
        var task = this.state.tasks.get(marker.id);
        //Keep marker in same place if not in edit mode.
        if(!this.state.isEdit())
            marker.setPosition(task.getPosition());
        this.updateAllocationRendering();
    },
    onTaskMarkerDragEnd: function (marker) {
        var task = this.state.tasks.get(marker.id);
        if (this.state.isEdit()) {
            if (this.state.tasks.get(marker.id)) {
                var latlng = _.coordinate(marker.getPosition());
                $.post("/tasks/" + marker.id, {
                    id: marker.id,
                    lat: latlng.latitude,
                    lng: latlng.longitude
                });
            }
        }
        //Keep marker in same place if not in edit mode.
        else
            marker.setPosition(task.getPosition());
    },
    onTaskMarkerMouseover: function (marker) {
        //If in manual allocation mode, update the task id that will be manually allocated
        if(MapAgentController.isManuallyAllocating) {
            MapAgentController.taskIdToAllocateManually = marker.id;
            this.updateAllocationRendering();
        }
    },
    onTaskMarkerMouseout: function (marker) {
        MapAgentController.taskIdToAllocateManually = null;
        this.updateAllocationRendering();
    },
    updateTaskRendering: function (taskId, colourOptions) {
        var task = this.state.tasks.get(taskId);
        var self = this;
        if (!task)
            return;
        if(task.getType() === this.state.tasks.TASK_MONITOR || task.getType() === this.state.tasks.TASK_WAYPOINT || task.getType() === this.state.tasks.TASK_VISIT) {
            MapTaskController.updateTaskMarkerIcon(taskId, colourOptions);
        }
        else if(task.getType() === this.state.tasks.TASK_PATROL) {
            var polyline = this.$el.gmap("get", "overlays > Polyline", [])[taskId];
            polyline.setOptions({
                strokeColor: colourOptions['name'],
                editable: self.state.isEdit()
            });
            MapTaskController.updateTaskMarkerIcon(taskId, colourOptions);
            var marker = this.$el.gmap("get", "markers")[taskId];
            marker.setVisible(MapAgentController.isManuallyAllocating);
        }
        else if(task.getType() === this.state.tasks.TASK_REGION) {
            var polyline = this.$el.gmap("get", "overlays > Polyline", [])[taskId];
            var path = [];
            for (var i = 0; i < task.getPoints().length; i++) {
                var point = task.getPoints()[i];
                path.push(_.position(point.latitude, point.longitude));
            }
            polyline.setOptions({
                strokeColor: colourOptions['name'],
                visible: $('#region_path_toggle').is(":checked"),
                path: path
            });
            var rect = this.$el.gmap("get", "overlays > Rectangle", [])[taskId];
            rect.setOptions({
                fillColor: colourOptions['name'],
                strokeColor: colourOptions['name'],
                editable: self.state.isEdit()
            });
            MapTaskController.updateTaskMarkerIcon(taskId, colourOptions);
            var marker = this.$el.gmap("get", "markers")[taskId];
            marker.setVisible(MapAgentController.isManuallyAllocating);
        }
    },
    /**
     * Update the icon of a task marker.
     * @param taskId - Id of task to change.
     * @param colourOptions - Alter the hue, saturation and brightness of the colour relative to the base red icon.
     *  hue - hue-rotate centered as zero=red
     *   see http://www.johnpaulcaponigro.com/blog/http://www.johnpaulcaponigro.com/blog/wp-content/themes/zinfandel-blue-10/images/hue_clock.jpg
     *
     */
    updateTaskMarkerIcon: function (taskId, colourOptions) {
        var marker = this.$el.gmap("get", "markers")[taskId];
        var task = this.state.tasks.get(taskId);
        if (!task || !marker)
            return;

        var icon = this.icons.Marker;
        if(task.getType() === this.state.tasks.TASK_MONITOR)
            icon = this.icons.MarkerMonitor;
        // TODO specific visit marker
        marker.setIcon(icon.Image);
        if (marker.icon) {
            //Add task id to end of marker url, this makes them unique.
            marker.icon.url = marker.icon.url + "#" + taskId;
            var h = colourOptions['h'];
            var s = colourOptions['s'];
            var l = colourOptions['l'];

            //  UNCOMMENT FOR DISCO MODE!!
            //         \(*_*)
            //          (  (>
            //          /  \
            // h = Math.random()*360;

            //Grab actual marker element by the (now unique) image src and set its colour
            $('img[src=\"' + marker.icon.url + '\"]').css({
                '-webkit-filter': 'hue-rotate(' + h + 'deg) saturate(' + s + ') brightness(' + l + ')',
                'filter': 'hue-rotate(' + h + 'deg) saturate(' + s + ') brightness(' + l + ')'
            });
        }
    },
    processPatrolTaskPathChange: function (taskId, polyline, vertex) {
        var path = polyline.getPath();

        //Hide undo button
        var undoImgEl = $('img[src=\"http://maps.gstatic.com/mapfiles/undo_poly.png\"]');
        if (undoImgEl)
            undoImgEl.hide();

        //Ensure start and end points are same so patrol is a loop
        if(path.getAt(path.length - 1) !== path.getAt(0)) {
            if(vertex === 0 )
                path.setAt(path.length - 1, path.getAt(0));
            else if (vertex === path.length - 1)
                path.setAt(0, path.getAt(path.length - 1));
        }

        //Send path to server for update.
        var newPath = [];
        for (var i = 0; i < path.length; i++) {
            var point = path.getAt(i);
            newPath.push(point.lat(), point.lng());
        }
        $.post("/tasks/patrol/update/" + taskId, {
            path: newPath.toString()
        });
    },
    onPatrolTaskRightClick: function (taskId, polyline, event) {
        if (event.vertex === undefined || polyline.getPath().length === 4)
            MapTaskController.openTaskEditWindow(this.state.tasks.get(taskId), event.latLng);
        else {
            this.$el.gmap("openInfoWindow", {minWidth: 300}, null, function (iw) {
                var property = document.createElement("div");
                property.innerHTML = _.template($("#waypoint_remove").html(), {});
                iw.setContent(property);
                iw.setPosition(event.latLng);

                $(property).on("click", "#waypoint_remove_button", function () {
                    var path = polyline.getPath();
                    path.removeAt(event.vertex);
                    if(event.vertex === 0)
                        path.setAt(path.length - 1, path.getAt(0));
                    else if(event.vertex === path.length)
                        path.setAt(0, path.getAt(path.length - 1));
                    iw.close();
                });
            });
        }
    },
    openTaskEditWindow: function(task, position) {
        self = this;
        this.$el.gmap("openInfoWindow", {minWidth: 300}, null, function (iw) {
            var property = document.createElement("div");

            property.innerHTML = _.template($("#task_edit").html(), {
                task_id: task.getId(),
                group_size: task.getGroup(),
                task_priority: task.getPriority(),
                max_size: self.state.agents.size()
            });
            iw.setContent(property);
            iw.setPosition(position);

            if (!self.state.isEdit()) {
                $("#task_edit_update").hide();
                $("#task_edit_delete").hide();
                $("#task_priority").attr("readonly","readonly");
                $("#group_size").attr("readonly","readonly");
            }

            google.maps.event.addListener(iw, 'domready', function () {
                //Update task if values changed
                $(property).on("click", "#task_edit_update", function () {
                    var group_size = $(property).find("#group_size").val();
                    var priority = $(property).find("#task_priority").val();
                    if (group_size >= 0 && group_size <= self.state.agents.size()) {
                        $.post("/tasks/" + task.getId(), {
                            group: group_size,
                            priority: priority
                        });
                        iw.close();
                    }
                    else
                        alert("Group size is out of bounds: " + group_size);
                });
                //Delete task if delete pressed
                $(property).on("click", "#task_edit_delete", function () {
                    if (confirm("Are you sure you want to delete " + task.getId() + "?")) {
                        $.ajax({
                            url: "/tasks/" + task.getId(),
                            type: 'DELETE'
                        });
                    }
                    iw.close();
                });
            });
        });
    },
    processRegionTaskChange: function (taskId, rectangle) {
        //Hide undo button
        var undoImgEl = $('img[src=\"http://maps.gstatic.com/mapfiles/undo_poly.png\"]');
        if (undoImgEl)
            undoImgEl.hide();

        //Send new bounds to server
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
        $.post("/tasks/region/update/" + taskId, {
            corners: corners.toString()
        });
    }
};
