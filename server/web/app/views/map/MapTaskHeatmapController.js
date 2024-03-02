var MapTaskHeatmapController = {
    taskHeatmaps: [],
    addedGroups: [],
    taskMarkers: [],
    running: false,

    /**
     * Binds all the methods to use the given context.
     *  This means the methods can be called just using MapAgentController.method() without
     *  having to worry about calling from the correct context.
     * @param context - Context to bind to.
     */
    bind: function (context) {
        this.bindEvents = _.bind(this.bindEvents, context);
        this.drawTaskMaps = _.bind(this.drawTaskMaps, context);
        this.removeTaskMarkerFor = _.bind(this.removeTaskMarkerFor, context);
        this.addTaskMarkerFor = _.bind(this.addTaskMarkerFor, context);
        this.updateAllTaskMarkers = _.bind(this.updateAllTaskMarkers, context);
        this.updateFor = _.bind(this.updateFor, context);
        this.updateTaskRendering = _.bind(this.updateTaskRendering, context);
        this.clearAll = _.bind(this.clearAll, context);
    },
    /**
     * Bind listeners for agent state add, change and remove events
     */
    bindEvents: function () {
        
    },
    drawTaskMaps: function () {
        //console.log("========================================")

        //if (MapTaskHeatmapController.running || MapTaskHeatmapController.arraysEqual(this.state.tasks, MapTaskHeatmapController.addedGroups.flat(1))) {
        //if(MapTaskHeatmapController.arraysEqual(this.state.tasks, MapTaskHeatmapController.addedGroups.flat(1))) {
            // No need to redraw
        //} else {
        //MapTaskHeatmapController.running = true;
        if (!this.state.tasks.isEmpty()) {
            let groups = [];
            let grouping_dist = 250;
            let tasks = []
            this.state.tasks.forEach(function (t) {
                tasks.push(t);
            });

            tasks.forEach((t) => {
                //console.log("Considering " + t.getId())
                if (!MapTaskHeatmapController.checkIfIn2DList(t, groups)) {
                    //console.log("-pushed new list")
                    // This is not in any group so should start a new one
                    groups.push([t])
                }

                let new_group;
                tasks.forEach((n) => {
                    //console.log(t.getId() + " -> " + n.getId())
                    //if (t !== n && !MapHeatmapController.checkIfIn2DList(n, groups) && Math.abs(tasks[n] - tasks[t]) <= grouping_dist) {
                    if (t !== n && !MapTaskHeatmapController.checkIfIn2DList(n, groups) && google.maps.geometry.spherical.computeDistanceBetween(n.getPosition(), t.getPosition()) <= grouping_dist) {
                        //console.log("-newAdd")
                        groups.forEach((g) => {
                            if (g.includes(t) && !g.includes(n)) {
                                g.push(n)
                            }
                        });
                    } else if (t !== n && !MapTaskHeatmapController.checkIfInGroupOf(n, t, groups) && google.maps.geometry.spherical.computeDistanceBetween(n.getPosition(), t.getPosition()) <= grouping_dist) {
                        // t and n are within range of each other, and n and t are not in the same group
                        //  n is in a group, t only might be.
                        const new_group = [];
                        let indexToReplace = null;
                        let indexToRemove = null;
                        groups.forEach((group) => {
                            if (group.includes(t) || group.includes(n)) {
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

            for (let group_index = 0; group_index < groups.length; group_index++){
                const group = groups[group_index];
                if (group.length !== 0) {
                    var added = false;
                    MapTaskHeatmapController.addedGroups.forEach((addedGroup) => {
                        if (MapTaskHeatmapController.arraysEqual(addedGroup, group)) {
                            // This exact group is already here
                            added = true;
                        }
                    });

                    if (added) {
                        // Already have this heatmap
                    } else {
                        var heatmapData = []
                        group.forEach((g) => {
                            heatmapData.push({
                                location: new google.maps.LatLng(g.getPosition().lat(), g.getPosition().lng()),
                                weight: 0.15
                            });
                        })


                        // Remove the heatmap that contains these tasks (if any)

                        //console.log("Pre markers:")


                        var matchedMap = null;
                        for (let i = 0; i < MapTaskHeatmapController.addedGroups.length; i++) {
                            for (const g of group) {
                                if (MapTaskHeatmapController.addedGroups[i].includes(g)) {
                                    //console.log("group: " + g.getId())
                                    matchedMap = i;
                                    break;
                                }
                            }

                        }

                        if (matchedMap !== null) {

                            MapTaskHeatmapController.addedGroups[matchedMap].forEach((g) => {
                                heatmapData.push({
                                    location: new google.maps.LatLng(g.getPosition().lat(), g.getPosition().lng()),
                                    weight: 0.15
                                });
                            });

                            var heatmap = new google.maps.visualization.HeatmapLayer({
                                data: heatmapData
                            });

                            //MapHeatmapController.addedGroups.splice(matchedMap, 1)
                            MapTaskHeatmapController.addedGroups[matchedMap] = group;

                            MapTaskHeatmapController.taskHeatmaps[matchedMap].setMap(null);
                            delete MapTaskHeatmapController.taskHeatmaps[matchedMap];
                            MapTaskHeatmapController.taskHeatmaps[matchedMap] = heatmap;
                        } else {
                            // Otherwise the index is the end of the list

                            var heatmap = new google.maps.visualization.HeatmapLayer({
                                data: heatmapData
                            });

                            matchedMap = MapTaskHeatmapController.addedGroups.length;
                            MapTaskHeatmapController.addedGroups.push(group);
                            MapTaskHeatmapController.taskHeatmaps.push(heatmap);
                        }

                        //MapTaskHeatmapController.addTaskMarkerFor(group, matchedMap);

                        heatmap.setOptions({radius: 150, zIndex: -1})
                        heatmap.setMap(this.map);
                    }
                } else {
                    // This is an empty group so we should remove its heatmap and task marker
                    //alert("removing " + group_index)
                    MapTaskHeatmapController.taskHeatmaps[group_index] = [];
                    MapTaskHeatmapController.addedGroups[group_index] = [];

                    // TODO doesn't add marker immediately

                }
            }

            //MapTaskHeatmapController.addedGroups = groups

            //console.log("Groups after heatmap bit: " + groups.length)
            //groups.forEach((group) => {
            //    console.log(" g: ")
            //    group.forEach((g) => {
            //        console.log("    " + g.getId())
            //    });
            //});
            //console.log("AddedGroups after heatmap bit: " + MapTaskHeatmapController.addedGroups.length)
            //MapTaskHeatmapController.addedGroups.forEach((group) => {
            //    console.log(" g: ")
            //    group.forEach((g) => {
            //        console.log("    " + g.getId())
            //    });
            //});
        }
        MapTaskHeatmapController.updateAllTaskMarkers();
        //MapTaskHeatmapController.running = false;

    },
    updateAllTaskMarkers: function () {
        for (let i = 0; i < MapTaskHeatmapController.addedGroups.length; i++){
            if (MapTaskHeatmapController.addedGroups[i].length === 0) {
                //console.log("Removing " + i + " groupsize = " + MapTaskHeatmapController.addedGroups[i].length)
                MapTaskHeatmapController.removeTaskMarkerFor(i);
            } else {
                //console.log("Adding " + i + " groupsize = " + MapTaskHeatmapController.addedGroups[i].length)
                MapTaskHeatmapController.addTaskMarkerFor(MapTaskHeatmapController.addedGroups[i], i);
            }
        }
    },
    updateFor: function (task) {
        // TODO remove this marker's group. The whole map is then refreshed above
        for (let i = 0; i < MapTaskHeatmapController.addedGroups.length; i++){
            const g = MapTaskHeatmapController.addedGroups[i];
            if (g.includes(task)) {
                MapTaskHeatmapController.addedGroups[i] = []
                MapTaskHeatmapController.removeTaskMarkerFor(i);
                try {
                    MapTaskHeatmapController.taskHeatmaps[i].setMap(null);
                    delete MapTaskHeatmapController.taskHeatmaps[i];
                } catch (e) {}
            }

        }
    },
    addTaskMarkerFor: function (group, index) {
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
        var marker = this.$el.gmap("get", "markers")["TaskGroup-"+index];
        if (marker) {
            marker.setPosition(newPos);
            marker.setOptions({labelContent: "[" + index + "] " + group.length + " Tasks"});
            //marker.setOptions({labelContent: group.length + " Tasks"});
        } else {


            // 2. Add a new marker with label "X tasks" and related to this group
            this.$el.gmap("addMarker", {
                bounds: false,
                draggable: false,
                id: "TaskGroup-" + index,
                position: newPos,
                centrePos: newPos,
                marker: MarkerWithLabel,
                labelContent: "[" + index + "] " + group.length + " Tasks",
                //labelContent: group.length + " Tasks",
                labelAnchor: new google.maps.Point(25, 65),
                labelClass: "labels",
                labelStyle: {opacity: 1.0},
                raiseOnDrag: false,
                zIndex: 3
            });
            marker = this.$el.gmap("get", "markers")["TaskGroup-" + index];

            // TODO what is this???
            //MapTaskController.updateTaskRendering("TaskGroup-" + index, this.MarkerColourEnum.RED);

            $(marker).mouseover(function () {
                MapTaskHeatmapController.onTaskMarkerMouseover(marker);
            }).mouseout(function () {
                MapTaskHeatmapController.onTaskMarkerMouseout(marker);
            });
            MapTaskHeatmapController.updateTaskRendering("TaskGroup-" + index, this.MarkerColourEnum.BLUE)

        }
        marker.setMap(this.map)
    },
    removeTaskMarkerFor: function (index) {
        var marker = this.$el.gmap("get", "markers")["TaskGroup-"+index];
        if (marker) {
            //alert("Removing marker " + index)
            marker.setMap(null);
            delete marker;
        }
    },
    removeTaskMarkerForTask: function (task) {
        for (let i = 0; i < MapTaskHeatmapController.addedGroups.length; i++){
            if (MapTaskHeatmapController.addedGroups[i].includes(task)) {
                MapTaskHeatmapController.removeTaskMarkerFor(i)
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
    checkIfInGroupOf(n, t, groups) {
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
     * @param a
     * @param b
     * @returns {boolean}
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
    onTaskMarkerMouseover: function (marker) {
        // TODO update this to a list of tasks
        //If in manual allocation mode, update the task id that will be manually allocated
        //console.log("mouseover")
        if(MapAgentHeatmapController.isManuallyAllocating) {
            MapAgentHeatmapController.groupIdToAllocateManually = marker.id;
            //console.log("here")
            //this.updateAllocationRendering();
        }
    },
    onTaskMarkerMouseout: function (marker) {
        MapAgentHeatmapController.groupIdToAllocateManually = null;
        //this.updateAllocationRendering();
    },
    updateTaskRendering: function (taskId, colourOptions) {
        var marker = this.$el.gmap("get", "markers")[taskId];
        var icon = this.icons.MarkerMonitor;
        marker.setIcon(icon.Image);
        if (marker.icon) {
            //Add task id to end of marker url, this makes them unique.
            marker.icon.url = marker.icon.url + "#" + taskId;
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
        MapTaskHeatmapController.taskHeatmaps.forEach((h) => {
            try {
                h.setMap(null);
                delete h;
            } catch (e) {}
        })

        for (let i = 0; i < MapTaskHeatmapController.addedGroups.length; i++){
            try {
                MapTaskHeatmapController.removeTaskMarkerFor(i)
            } catch (e) {}
        }

        MapTaskHeatmapController.taskHeatmaps = [];
        MapTaskHeatmapController.addedGroups = [];
        MapTaskHeatmapController.taskMarkers = [];
        MapTaskHeatmapController.running = false;
    }
};
