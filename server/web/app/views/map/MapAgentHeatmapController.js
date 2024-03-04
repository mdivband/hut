var MapAgentHeatmapController = {
    agentHeatmaps: [],
    addedGroups: [],
    taskMarkers: [],
    groupIdToAllocateManually: null,
    isManuallyAllocating: null,
    running: false,
    groupStatuses: [],
    agentMarkers: [],

    /**
     * Binds all the methods to use the given context.
     *  This means the methods can be called just using MapAgentController.method() without
     *  having to worry about calling from the correct context.
     * @param context - Context to bind to.
     */
    bind: function (context) {
        this.bindEvents = _.bind(this.bindEvents, context);
        this.drawAgentMaps = _.bind(this.drawAgentMaps, context);
        this.removeAgentMarkerFor = _.bind(this.removeAgentMarkerFor, context);
        this.addAgentMarkerFor = _.bind(this.addAgentMarkerFor, context);
        this.updateHeatmapAllocationRendering = _.bind(this.updateHeatmapAllocationRendering, context);
        this.updateAllAgentMarkers = _.bind(this.updateAllAgentMarkers, context);
        this.onAgentMarkerDrag = _.bind(this.onAgentMarkerDrag, context);
        this.onAgentMarkerDragEnd = _.bind(this.onAgentMarkerDragEnd, context);
        this.updateTaskRendering = _.bind(this.updateTaskRendering, context);
        this.adjustHeatmapLocation = _.bind(this.adjustHeatmapLocation, context);
        this.drawSubAllocation = _.bind(this.drawSubAllocation, context);
        this.drawHeatmapAllocation = _.bind(this.drawHeatmapAllocation, context);
        this.hideHeatmapPolyline = _.bind(this.hideHeatmapPolyline, context);
    },
    /**
     * Bind listeners for agent state add, change and remove events
     */
    bindEvents: function () {
        
    },
    drawAgentMaps: function (reset) {
        //console.log("========================================")
        if (reset) {
            //MapAgentHeatmapController.clearAll();
        }

        if (!reset && (MapAgentHeatmapController.running || MapAgentHeatmapController.arraysEqual(this.state.agents, MapAgentHeatmapController.addedGroups.flat(1)))) {
        //if (false) {
            // No need to redraw
        } else {
            MapAgentHeatmapController.running = true;
            if (!this.state.agents.isEmpty()) {
                let groups = [];
                let grouping_dist = 250;
                //let agents = []
                //this.state.agents.forEach(function (t) {
                //    agents.push(t);
                //});

                this.state.agents.forEach((t) => {
                    //console.log("Considering " + t.getId())
                    if (!MapAgentHeatmapController.checkIfIn2DList(t, groups)) {
                        //console.log("-pushed new list")
                        // This is not in any group so should start a new one
                        groups.push([t])
                    }

                    let new_group;
                    this.state.agents.forEach((n) => {
                        //console.log(t.getId() + " -> " + n.getId())
                        //if (t !== n && !MapHeatmapController.checkIfIn2DList(n, groups) && Math.abs(tasks[n] - tasks[t]) <= grouping_dist) {

                        if (t !== n && (t.getAgentTeam().includes(n.getId()) || ((t.getAgentTeam().length === 0 && n.getAgentTeam().length === 0) && !MapAgentHeatmapController.checkIfIn2DList(n, groups) && google.maps.geometry.spherical.computeDistanceBetween(n.getPosition(), t.getPosition()) <= grouping_dist))) {
                            //console.log("-newAdd")
                            groups.forEach((g) => {
                                if (g.includes(t) && !g.includes(n)) {
                                    g.push(n)
                                }
                            });
                        } else if (t !== n && (t.getAgentTeam().includes(n.getId()) || ((t.getAgentTeam().length === 0 && n.getAgentTeam().length === 0) && !MapAgentHeatmapController.checkIfInGroupOf(n, t, groups) && google.maps.geometry.spherical.computeDistanceBetween(n.getPosition(), t.getPosition()) <= grouping_dist))) {
                            // t and n are within range of each other, and n and t are not in the same group
                            //  n is in a group, t only might be.
                            const new_group = [];
                            let indexToReplace = null;
                            let indexToRemove = null;
                            groups.forEach((group) => {
                                if (((t.getAgentTeam().length === 0 && n.getAgentTeam().length === 0) || t.getAgentTeam().includes(n.getId())) && (group.includes(t) || group.includes(n))) {
                                    if (indexToReplace === null) {
                                        indexToReplace = groups.indexOf(group)
                                    } else if (indexToRemove === null) {
                                        indexToRemove = groups.indexOf(group)
                                    }
                                    group.forEach((g) => {
                                        new_group.push(g)
                                        //groups.splice(groups.indexOf(group), 1)
                                    });
                                }
                            });

                            if (!new_group.includes(n)) {
                                new_group.push(n)
                            }
                            if (!new_group.includes(t)) {
                                new_group.push(t)
                            }
                            //indicesToRemove.forEach((i) => {
                            //    groups.splice(i, 1)
                            //});
                            // groups.push(new_group)

                            if (indexToReplace !== null) {
                                groups[indexToReplace] = new_group;
                            }
                            if (indexToRemove !== null) {
                                groups[indexToRemove] = [];

                            }

                        }

                    });
                });

                // We have now built the groups that we need to make into maps
                for (let group_index = 0; group_index < groups.length; group_index++){
                    const group = groups[group_index];
                    if (group.length !== 0) {
                        var addedIndex = -1;
                        for (let i = 0; i < MapAgentHeatmapController.addedGroups.length; i++){
                            if (MapAgentHeatmapController.arraysEqual(MapAgentHeatmapController.addedGroups[i], group)) {
                                // This exact group is already here
                                addedIndex = i;
                                break
                            }
                        }

                        //if (addedIndex !== -1 && MapAgentHeatmapController.agentHeatmaps.hasOwnProperty(addedIndex) && MapAgentHeatmapController.agentHeatmaps[addedIndex] !== null) {
                        if (false) {
                            // Already have this heatmap
                            //alert("No change for " + addedIndex)
                            // TODO I think this is where we still need to update the location

                        } else {
                            var heatmapData = new google.maps.MVCArray();
                            group.forEach((g) => {
                                heatmapData.push({
                                    location: new google.maps.LatLng(g.getPosition().lat(), g.getPosition().lng()),
                                    weight: 0.15
                                });
                            })


                            // Remove the heatmap that contains these tasks (if any)

                            //console.log("Pre markers:")


                            var matchedMap = null;
                            for (let i = 0; i < MapAgentHeatmapController.addedGroups.length; i++) {
                                MapAgentHeatmapController.addedGroups[i].forEach((a) => {
                                    //console.log("AddGr: " + a.getId());
                                });
                                for (const g of group) {
                                    if (MapAgentHeatmapController.addedGroups[i].includes(g)) {
                                        //console.log("group: " + g.getId())
                                        matchedMap = i;
                                        break;
                                    }
                                }

                            }

                            if (matchedMap !== null) {

                                /*
                                MapAgentHeatmapController.addedGroups[matchedMap].forEach((g) => {
                                    heatmapData.push({
                                        location: new google.maps.LatLng(g.getPosition().lat(), g.getPosition().lng()),
                                        weight: 0.15
                                    });
                                });

                                 */

                                var heatmap = new google.maps.visualization.HeatmapLayer({
                                    data: heatmapData
                                });


                                MapAgentHeatmapController.addedGroups[matchedMap] = group;

                                if (MapAgentHeatmapController.agentHeatmaps[matchedMap] !== null)  {// || MapAgentHeatmapController.agentHeatmaps[matchedMap].isEmpty())) {
                                    try {
                                        MapAgentHeatmapController.agentHeatmaps[matchedMap].setMap(null);
                                        delete MapAgentHeatmapController.agentHeatmaps[matchedMap];
                                    } catch (e) {}
                                }

                                MapAgentHeatmapController.agentHeatmaps[matchedMap] = heatmap;
                            } else {
                                // Otherwise the index is the end of the list

                                var heatmap = new google.maps.visualization.HeatmapLayer({
                                    data: heatmapData
                                });

                                matchedMap = MapAgentHeatmapController.addedGroups.length;
                                MapAgentHeatmapController.addedGroups.push(group);
                                MapAgentHeatmapController.agentHeatmaps.push(heatmap);
                            }

                            //MapTaskHeatmapController.addTaskMarkerFor(group, matchedMap);
                            const gradient = [
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

                            heatmap.setOptions({radius: 150, gradient: gradient, zIndex: 0})
                            heatmap.setMap(this.map);
                        }
                    } else {
                        // This is an empty group so we should remove its heatmap and task marker
                        //alert("removing " + group_index)
                        try {
                            MapAgentHeatmapController.agentHeatmaps[group_index].setMap(null);
                            delete MapAgentHeatmapController.agentHeatmaps[group_index];
                        } catch (e) {}
                        MapAgentHeatmapController.removeAgentMarkerFor(group_index)
                        MapAgentHeatmapController.agentHeatmaps[group_index] = [];
                        MapAgentHeatmapController.addedGroups[group_index] = [];
                        //group[group_index] = [];

                        // TODO doesn't add marker immediately

                    }
                }

                MapAgentHeatmapController.addedGroups = groups

                // console.log("Groups after heatmap bit: " + groups.length)
                // groups.forEach((group) => {
                //    console.log(" g: ")
                //    group.forEach((g) => {
                //        console.log("    " + g.getId() + "     (" + g.getAgentTeam() + ")")
                //    });
                // });
                // console.log("AddedGroups after heatmap bit: " + MapAgentHeatmapController.addedGroups.length)
                // MapAgentHeatmapController.addedGroups.forEach((group) => {
                //     console.log(" g: ")
                //     group.forEach((g) => {
                //         console.log("    " + g.getId())
                //     });
                // });
            }
            MapAgentHeatmapController.updateAllAgentMarkers();
            MapAgentHeatmapController.running = false;
        }
    },
    updateHeatmapAllocationRendering: function () {
        //console.log("Heatmap alloc render")
        var self = this;
        var mainAllocation = this.state.getAllocation();
        var tempAllocation = this.state.getTempAllocation();
        var droppedAllocation = this.state.getDroppedAllocation();
        if(MapTaskHeatmapController.addedGroups.length > 0) {
            // Allocation will be agent -> task as normal
            var groupAllocation = [];  // index 0 === agent group 0; contains the index of the task group this maps to
            for (let i = 0; i < MapAgentHeatmapController.addedGroups.length; i++) {
                groupAllocation[i] = null;
                //if (MapTaskHeatmapController.addedGroups[i] !== null && MapTaskHeatmapController.addedGroups.hasOwnProperty(i)) {
                if (MapAgentHeatmapController.addedGroups[i] !== null && MapAgentHeatmapController.addedGroups.hasOwnProperty(i)) {
                    var agentGroup = MapAgentHeatmapController.addedGroups[i]
                //if (true) {
                    for (let j = 0; j < MapTaskHeatmapController.addedGroups.length; j++) {
                        if (MapTaskHeatmapController.addedGroups[j] !== null && MapTaskHeatmapController.addedGroups.hasOwnProperty(j)) {
                            const taskGroup = MapTaskHeatmapController.addedGroups[j];
                            // We can check each agent and lookup what task it maps to. If agent group X contains any tasks that are
                            // mapped to task group Y, then X->Y is always true
                            for (let agentIndex = 0; agentIndex < agentGroup.length; agentIndex++) {
                                //const agentToConsider = taskGroup[taskGroupIndex];
                                const agentToConsider = agentGroup[agentIndex]
                                //if (MapTaskHeatmapController.addedGroups[j].filter(g => g.id === mainAllocation[agentToConsider.getId()]).length > 0) {
                                if (taskGroup.filter(t => t.id === mainAllocation[agentToConsider.getId()]).length > 0) {
                                    //console.log(a.getId() + " assigned")
                                    groupAllocation[i] = j//taskGroupIndex
                                    break
                                }
                            }
                        }
                    }
                }
            }

            //console.log("Computed group mapping as: ")


            for (let i = 0; i < groupAllocation.length; i++) {
                const g = groupAllocation[i];
                //console.log("    " + i + " -> " + g)

                var groupId = "AgentGroup-" + i;
                var mainLineId = groupId + "main";
                var tempLineId = groupId + "temp";
                var droppedLineId = groupId + "dropped";
                var taskGroupId = "TaskGroup-" + g

                //Draw or hide 'real' allocation.
                if (g !== null) {
                    //console.log("drawing from " + groupId + " to " + taskGroupId)
                    MapAgentHeatmapController.drawHeatmapAllocation(mainLineId, "green", groupId, taskGroupId);
                    for (let agentIndex = 0; agentIndex < MapAgentHeatmapController.addedGroups[i].length; agentIndex++){
                        MapAgentHeatmapController.drawSubAllocation(MapAgentHeatmapController.addedGroups[i][agentIndex],
                            MapAgentHeatmapController.addedGroups[i][agentIndex].getAllocatedTaskId())
                    }

                } else {
                    MapAgentHeatmapController.hideHeatmapPolyline(mainLineId);
                }
            }
        }

        //Colour task marker that is being hovered over when manually allocating
        if (MapAgentHeatmapController.groupIdToAllocateManually) {
            MapTaskHeatmapController.updateTaskRendering(MapAgentHeatmapController.groupIdToAllocateManually, self.MarkerColourEnum.BLUE);
        }
    },
    drawSubAllocation: function (agent, taskId) {
        console.log("SubAlloc: " + agent.getId() + " -> " + taskId)
        // AGENT

        var subMarker = this.$el.gmap("get", "markers")["sub_"+agent.getId()];
        if (!subMarker) {
            this.$el.gmap("addMarker", {
                marker: MarkerWithLabel,
                //draggable: true, //Allows use of drag and dragend events even though the marker shouldn't be moved by dragging.
                //labelContent: id,
                //labelAnchor: new google.maps.Point(22, -18),
                //labelClass: "labels",
                //labelStyle: {opacity: 1.0},
                id: "sub_"+agent.getId(),
                position: agent.getPosition(),
                heading: agent.getHeading(),
                raiseOnDrag: false,
                zIndex: -20000000,
            });
            subMarker = this.$el.gmap("get", "markers")["sub_"+agent.getId()];
            MapAgentHeatmapController.agentMarkers.push(subMarker);
            var icon = this.icons.UAVMini;
            subMarker.setIcon(icon.Image);
        } else {
            var icon = this.icons.UAVMini;
            //subMarker.setOptions({clickable: false, draggable: false})

            subMarker.setIcon(icon.Image);
            subMarker.setPosition(agent.getPosition());
            //Rotate agent marker - seems clunky but GoogleMapsAPI doesn't allow for marker rotation...
            if (subMarker.icon) {
                //Add agent id to end of marker url, this makes them unique.
                subMarker.icon.url = subMarker.icon.url + "#" + agent.getId();
                //Grab actual marker element by the (now unique) image src and rotate it by the agent's heading
                var markerImgEl = $('img[src=\"' + subMarker.icon.url + '\"]');
                markerImgEl.css({
                    'transform': 'rotate(' + agent.getHeading() + 'deg)'
                });
                //MapAgentController.drawAgentBattery(markerImgEl.parent(), agent);
            }

        }




        // TASKS (lines)

        var thisTask = null;
        MapTaskHeatmapController.addedGroups.forEach((group) => {
            group.forEach((g) => {
                if (g.getId() === taskId) {
                    thisTask = g;
                }
            });
        });

        if (thisTask !== null) {
            var path = [];
            var route = [agent.getPosition(), thisTask.getPosition()]
            var convertedRoute = route.map(function (c) {
                return new google.maps.LatLng(c.lat(), c.lng());
            });
            path = path.concat(convertedRoute);
            var polyline = this.$el.gmap("get", "overlays > Polyline", [])["sub_" + agent.getId()];

            if (polyline) {
                //Only render arrow on the end of the polyline if the last leg is long enough
                var dist = google.maps.geometry.spherical.computeDistanceBetween(path[path.length - 2], path[path.length - 1]);
                var relativeSize = 0.05;
                var bounds = this.map.getBounds();
                var center = this.map.getCenter();
                if (bounds && center) {
                    var ne = bounds.getNorthEast();
                    var radius = google.maps.geometry.spherical.computeDistanceBetween(center, ne);
                }
                if (dist < relativeSize * radius)
                    polyline.setOptions({icons: []});
                else
                    polyline.setOptions({icons: [this.polylineIcon]});

                //Show polyline if currently hidden.
                if (!polyline.getMap())
                    polyline.setMap(this.map);

                //Update polyline path if path doesn't match. Add listeners here since the path object is overwritten.
                if (!_.doPathsMatch(polyline.getPath(), new google.maps.MVCArray(path))) {
                    polyline.setOptions({path: path});
                    google.maps.event.addListener(polyline.getPath(), 'insert_at', function (vertex) {
                        MapController.processWaypointChange(agentId, polyline, vertex, true);
                    });
                    google.maps.event.addListener(polyline.getPath(), 'set_at', function (vertex) {
                        MapController.processWaypointChange(agentId, polyline, vertex, false);
                    });
                    google.maps.event.addListener(polyline.getPath(), 'remove_at', function (vertex) {
                        MapController.processWaypointDelete(agentId, vertex);
                    });
                }
            }
            //If arrow doesn't exist, create it
            else {
                this.$el.gmap("addShape", "Polyline", {
                    id: "sub_" + agent.getId(),
                    editable: false,
                    icons: [this.polylineIcon],
                    strokeOpacity: 0.2,
                    strokeColor: "red",
                    strokeWeight: 1,
                    zIndex: 2
                });
            }
        }
    },
    drawHeatmapAllocation: function (lineId, lineColour, agentId, taskId) {
        var self = this;
        var isTempLine = lineId.endsWith('temp');
        var agentMarker = this.$el.gmap("get", "markers")[agentId];
        var taskMarker = this.$el.gmap("get", "markers")[taskId];
        var polyline = this.$el.gmap("get", "overlays > Polyline", [])[lineId];

        //Get polyline path
        var path = []//[agentMarker.getPosition()];
        var route = [agentMarker.getPosition(), taskMarker.getPosition()]

        var convertedRoute = route.map(function (c) {
            return new google.maps.LatLng(c.lat(), c.lng());
        });
        path = path.concat(convertedRoute);

        //Alter existing polyline if it exists
        if (polyline) {
            //Only render arrow on the end of the polyline if the last leg is long enough
            var dist = google.maps.geometry.spherical.computeDistanceBetween(path[path.length - 2], path[path.length - 1]);
            var relativeSize = 0.05;
            var bounds = this.map.getBounds();
            var center = this.map.getCenter();
            if (bounds && center) {
                var ne = bounds.getNorthEast();
                var radius = google.maps.geometry.spherical.computeDistanceBetween(center, ne);
            }
            if (dist < relativeSize * radius)
                polyline.setOptions({icons: []});
            else
                polyline.setOptions({icons: [this.polylineIcon]});

            //Show polyline if currently hidden.
            if (!polyline.getMap())
                polyline.setMap(this.map);

            //Update polyline path if path doesn't match. Add listeners here since the path object is overwritten.
            if (!_.doPathsMatch(polyline.getPath(), new google.maps.MVCArray(path))) {
                polyline.setOptions({path: path});
                google.maps.event.addListener(polyline.getPath(), 'insert_at', function (vertex) {
                    MapController.processWaypointChange(agentId, polyline, vertex, true);
                });
                google.maps.event.addListener(polyline.getPath(), 'set_at', function (vertex) {
                    MapController.processWaypointChange(agentId, polyline, vertex, false);
                });
                google.maps.event.addListener(polyline.getPath(), 'remove_at', function (vertex) {
                    MapController.processWaypointDelete(agentId, vertex);
                });
            }
        }
        //If arrow doesn't exist, create it
        else {
            this.$el.gmap("addShape", "Polyline", {
                id: lineId,
                editable: isTempLine,
                icons: [this.polylineIcon],
                strokeOpacity: 0.8,
                strokeColor: lineColour,
                strokeWeight: 5,
                zIndex: 1
            });
            //Polyline right click listener for temp lines
            if (isTempLine) {
                polyline = this.$el.gmap("get", "overlays > Polyline", [])[lineId];
                google.maps.event.addListener(polyline, "rightclick", function (event) {
                    if (event.vertex === undefined || event.vertex === 0 || event.vertex === (polyline.getPath().length - 1)) {
                        self.$el.gmap("openInfoWindow", {minWidth: 300}, null, function (iw) {
                            var property = document.createElement("div");
                            property.innerHTML = _.template($("#allocation_edit").html(), {
                                agent_id: agentId,
                                task_id: taskId
                            });
                            iw.setContent(property);
                            iw.setPosition(event.latLng);

                            google.maps.event.addListener(iw, 'domready', function () {
                                $(property).on("click", "#allocation_edit_delete", function () {
                                    $.ajax({
                                        url: "/allocation/" + agentId,
                                        type: 'DELETE'
                                    });
                                    iw.close();
                                });
                            });
                        });
                    } else {
                        self.$el.gmap("openInfoWindow", {minWidth: 300}, null, function (iw) {
                            var property = document.createElement("div");
                            property.innerHTML = _.template($("#waypoint_remove").html(), {});
                            iw.setContent(property);
                            iw.setPosition(event.latLng);

                            $(property).on("click", "#waypoint_remove_button", function () {
                                polyline.getPath().removeAt(event.vertex);
                                iw.close();
                            });
                        });
                    }
                });
            }
        }

    },
    hideHeatmapPolyline: function (lineId) {
        var polyline = this.$el.gmap("get", "overlays > Polyline", [])[lineId];
        if (polyline)
            polyline.setMap(null);
    },
    updateAllAgentMarkers: function () {
        for (let i = 0; i < MapAgentHeatmapController.addedGroups.length; i++){
            if (MapAgentHeatmapController.addedGroups[i].length === 0) {
                //console.log("Removing " + i + " groupsize = " + MapAgentHeatmapController.addedGroups[i].length)
                MapAgentHeatmapController.removeAgentMarkerFor(i);
            } else {
                //console.log("Adding " + i + " groupsize = " + MapAgentHeatmapController.addedGroups[i].length)
                MapAgentHeatmapController.addAgentMarkerFor(MapAgentHeatmapController.addedGroups[i], i);
            }
        }

        /*
        // TODO this lookup is not very efficient. A better data structure or else a hashmap of Agent -> (i, j) could be
        //  used to make this quicker -WH
        for (let i = 0; i < MapAgentHeatmapController.addedGroups.length; i++) {
            for (let j = 0; j < MapAgentHeatmapController.addedGroups[i].length; j++) {
                MapAgentHeatmapController.addedGroups[i][j] = self.state.agents.get(MapAgentHeatmapController.addedGroups[i][j]);
            }
        }

         */
    },
    adjustHeatmapLocation: function (agent) {
        for (let i = 0; i < MapAgentHeatmapController.addedGroups.length; i++){
            for (let j = 0; j < MapAgentHeatmapController.addedGroups[i].length; j++){
               if (MapAgentHeatmapController.addedGroups[i][j].getId() === agent.getId()) {

                   //pointArray.setAt(pointArray.indexOf(oldLatLng), newLatLng)`

                   MapAgentHeatmapController.addedGroups[i][j] = agent;
                   //MapAgentHeatmapController.agentHeatmaps[i][j] = {
                   //console.log(MapAgentHeatmapController.agentHeatmaps[i])
                   try {
                       MapAgentHeatmapController.agentHeatmaps[i].data.setAt(j, {
                           location: new google.maps.LatLng(agent.getPosition().lat(), agent.getPosition().lng()),
                           weight: 0.15
                       })
                       //var marker = this.$el.gmap("get", "markers")["AgentGroup-"+i];
                       //if (marker) {
                       //     var newPos =
                       //    marker.setPosition(newPos);
                       //}
                   } catch (e) {}
                  //     location: new google.maps.LatLng(agent.getPosition().lat(), agent.getPosition().lng()),
                  //     weight: 0.15
                   //};
               }
            }
        }
    },
    addAgentMarkerFor: function (group, index) {
        //console.log("Group size " + group.length + " index = " + index)
        // 1. Find centre of coords
        var latSum = 0;
        var lngSum = 0;
        group.forEach((g) => {
            latSum += g.getPosition().lat();
            lngSum += g.getPosition().lng();
        });
        var newPos = _.position(latSum / group.length, lngSum / group.length);
        // TODO this (after adjusting/creating marker)? this.views.control.trigger("update:agents");
        // Check if we already have it
        var marker = this.$el.gmap("get", "markers")["AgentGroup-"+index];
        if (marker) {
            marker.setPosition(newPos);
            marker.setOptions({labelContent: "[" + index + "] " + group.length + " Agents"});
            //marker.setOptions({labelContent: group.length + " Agents"});
        } else {


            // 2. Add a new marker with label "X tasks" and related to this group
            this.$el.gmap("addMarker", {
                bounds: false,
                draggable: true,
                id: "AgentGroup-"+index,
                centrePos: newPos,
                position: newPos,
                marker: MarkerWithLabel,
                labelContent: "[" + index + "] " + group.length + " Agents",
                //labelContent: group.length + " Agents",
                labelAnchor: new google.maps.Point(25, 65),
                labelClass: "labels",
                labelStyle: {opacity: 1.0},
                raiseOnDrag: false,
                zIndex: 3
            });
            marker = this.$el.gmap("get", "markers")["AgentGroup-"+index];
            MapAgentHeatmapController.agentMarkers.push(marker);

            $(marker).drag(function () {
                MapAgentHeatmapController.onAgentMarkerDrag(marker);
            }).dragend(function () {
                MapAgentHeatmapController.onAgentMarkerDragEnd(marker);
            });
            MapAgentHeatmapController.updateTaskRendering("AgentGroup-"+index, this.MarkerColourEnum.GREEN)
        }
        marker.setMap(this.map)
    },
    removeAgentMarkerFor: function (index) {
        var marker = this.$el.gmap("get", "markers")["AgentGroup-"+index];
        for (let i = 0; i < MapAgentHeatmapController.agentMarkers.length; i++){
            //if (MapAgentHeatmapController.agentMarkers[i] === marker) {
            //    MapAgentHeatmapController.agentMarkers.slice(i);
            //}
        }
        if (marker) {
            marker.setMap(null);
            delete marker;
        }

    },
    removeAgentMarkerForAgentWithTask: function (task) {
        for (let i = 0; i < MapAgentHeatmapController.addedGroups.length; i++){
            for (let j = 0; j < MapAgentHeatmapController.addedGroups[i].length; j++){
                if (MapAgentHeatmapController.addedGroups[i][j].getAllocatedTaskId() === task.getId()) {
                    MapAgentHeatmapController.removeAgentMarkerFor(i)
                }
            }
        }
    },
    checkIfIn2DList: function (itemToCheck, lists) {
        let result = false;
        lists.forEach((list) => {
            list.forEach((l) => {
                //console.log("Checking if " + l.getId() + " === " + itemToCheck.getId())
                if (l.getId() === itemToCheck.getId()) {
                    //console.log("It does!")
                    result = true;
                    return true;  // Breaks the loop
                }
            });
        });
        //console.log("false")
        return result;
    },
    checkIfInGroupOf: function (n, t, groups) {
        var ret = false;
        groups.forEach((group) => {
            if (group.includes(t) && group.includes(n)) {
                ret = true;
            }
        });
        return ret;
    },
    /**
     * https://stackoverflow.com/questions/3115982/how-to-check-if-two-arrays-are-equal-with-javascript
     */
    arraysEqual: function (a, b) {
        if (a === b) return true;
        if (a == null || b == null) return false;
        if (a.length !== b.length) return false;

        // If you don't care about the order of the elements inside
        // the array, you should sort both arrays here.
        // Please note that calling sort on an array will modify that array.
        // you might want to clone your array first.

        for (var i = 0; i < a.length; ++i) {
            if (a[i] !== b[i]) return false;
        }
        return true;
    },
    onAgentMarkerDrag: function (marker) {
        var self = this;
        MapAgentHeatmapController.isManuallyAllocating = true
        var agentGroup = MapAgentHeatmapController.addedGroups[marker.id.split("-")[1]];

        var latSum = 0;
        var lngSum = 0;
        var groupLen = 0
        agentGroup.forEach((agent) => {
            self.state.agents.forEach((a) => {
                if (a.getId() === agent.id) {
                    latSum += agent.getPosition().lat();
                    lngSum += agent.getPosition().lng();
                    groupLen += 1;
                }
            })
        });
        var groupPos = _.position(latSum / groupLen, lngSum / groupLen);

         marker.setOptions({groupPos});

        //Grab marker position (under cursor) then reposition marker to agent position so it doesn't actually move.
        var cursorPosition = marker.getPosition();
        marker.setPosition(groupPos);

        //Get path from agent position to cursor or task marker that is hovered over.
        var arrowEnd = MapAgentHeatmapController.groupIdToAllocateManually ?
            this.$el.gmap("get", "markers")[MapAgentHeatmapController.groupIdToAllocateManually].getPosition() : cursorPosition;
        var path = [groupPos, arrowEnd];

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
                zIndex: 1
            });
        }
    },
    onAgentMarkerDragEnd: function (marker) {
        var agentGroup = MapAgentHeatmapController.addedGroups[marker.id.split("-")[1]];

        var groupPos = marker.groupPos


        //No longer manually allocating since drag has ended
        MapAgentHeatmapController.isManuallyAllocating = false;

        //If a task marker is being hovered over, allocate the agent to it
        if(MapAgentHeatmapController.groupIdToAllocateManually) {
            var agentsIdsToPass = []
            MapAgentHeatmapController.addedGroups[marker.id.split("-")[1]].forEach((a) => {
                agentsIdsToPass.push(a.id)
            });

            var taskIdsToPass = []
            try {
                MapTaskHeatmapController.addedGroups[MapAgentHeatmapController.groupIdToAllocateManually.split("-")[1]].forEach((t) => {
                    taskIdsToPass.push(t.id)
                });
                $.post("/allocation/groupAllocate", {
                    agentIds: agentsIdsToPass.toString(),
                    taskIds: taskIdsToPass.toString()
                });
            } catch (e) {}

        }

        //Reposition marker to agent position so it doesn't actually move.
        marker.setPosition(groupPos);

        MapAgentHeatmapController.groupIdToAllocateManually = null;
        this.hidePolyline('manual_allocation')
        //console.log("end")
    },
    updateTaskRendering: function (agentId, colourOptions) {
        var marker = this.$el.gmap("get", "markers")[agentId];
        var icon = this.icons.MarkerMonitor;
        marker.setIcon(icon.Image);
        if (marker.icon) {
            //Add task id to end of marker url, this makes them unique.
            marker.icon.url = marker.icon.url + "#" + agentId;
            var h = colourOptions['h'];
            var s = colourOptions['s'];
            var l = colourOptions['l'];


            //Grab actual marker element by the (now unique) image src and set its colour
            $('img[src=\"' + marker.icon.url + '\"]').css({
                '-webkit-filter': 'hue-rotate(' + h + 'deg) saturate(' + s + ') brightness(' + l + ')',
                'filter': 'hue-rotate(' + h + 'deg) saturate(' + s + ') brightness(' + l + ')'
            });
        }
    },
    clearAll: function () {
        MapAgentHeatmapController.agentHeatmaps.forEach((h) => {
            try {
                h.setMap(null);
                delete h;
            } catch (e) {}
        })

        for (let i = 0; i < MapAgentHeatmapController.addedGroups.length; i++){
            try {
                MapAgentHeatmapController.removeAgentMarkerFor(i)
                MapAgentHeatmapController.hideHeatmapPolyline("AgentGroup-"+i+ "main")
            } catch (e) {}
        }


        for (let i = 0; i < MapAgentHeatmapController.agentMarkers.length; i++){
            try {
                MapAgentHeatmapController.agentMarkers[i].setMap(null);
                delete MapAgentHeatmapController.agentMarkers[i];
            } catch (e) {}

        }
        MapAgentHeatmapController.agentMarkers = [];
        MapAgentHeatmapController.agentHeatmaps = [];
        MapAgentHeatmapController.addedGroups = [];
        MapAgentHeatmapController.taskMarkers = [];
        MapAgentHeatmapController.groupIdToAllocateManually = null;
        MapAgentHeatmapController.isManuallyAllocating = null;
        MapAgentHeatmapController.running = false;
        MapAgentHeatmapController.groupStatuses = [];

        console.log("Cleared all markers")
        console.log(MapAgentHeatmapController.agentMarkers)
    }
};
