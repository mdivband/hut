var MapHeatmapController = {
    taskHeatmaps: [],
    addedGroups: [],
    taskMarkers: [],

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
    },
    /**
     * Bind listeners for agent state add, change and remove events
     */
    bindEvents: function () {
        
    },
    drawTaskMaps: function () {
        var mainAllocation = this.state.getAllocation();
        var tempAllocation = this.state.getTempAllocation();
        var droppedAllocation = this.state.getDroppedAllocation();

        if (MapHeatmapController.arraysEqual(this.state.tasks, MapHeatmapController.addedGroups.flat(1))) {
            // No need to redraw
        } else {
            if (!this.state.tasks.isEmpty()) {
                let groups = [];
                let grouping_dist = 150;
                let tasks = []
                this.state.tasks.forEach(function (t) {
                    tasks.push(t);
                });

                tasks.forEach((t) => {
                    //console.log("Considering " + t.getId())
                    if (!MapHeatmapController.checkIfIn2DList(t, groups)) {
                        //console.log("-pushed new list")
                        // This is not in any group so should start a new one
                        groups.push([t])
                    }

                    let new_group;
                    tasks.forEach((n) => {
                        //console.log(t.getId() + " -> " + n.getId())
                        //if (t !== n && !MapHeatmapController.checkIfIn2DList(n, groups) && Math.abs(tasks[n] - tasks[t]) <= grouping_dist) {
                        if (t !== n && !MapHeatmapController.checkIfIn2DList(n, groups) && google.maps.geometry.spherical.computeDistanceBetween(n.getPosition(), t.getPosition()) <= grouping_dist) {
                            //console.log("-newAdd")
                            groups.forEach((g) => {
                                if (g.includes(t) && !g.includes(n)) {
                                    g.push(n)
                                }
                            });
                        } else if (t !== n && !MapHeatmapController.checkIfInGroupOf(n, t, groups) && google.maps.geometry.spherical.computeDistanceBetween(n.getPosition(), t.getPosition()) <= grouping_dist) {
                            // t and n are within range of each other, and n is in a group (t might also be)
                            //console.log("-groupMerge")

                            new_group = []

                            // TODO fiddling about with this will probably fix things. Problem at is map merging
                            let indexToReplace = null;
                            let indexToRemove = null;
                            groups.forEach((group) => {
                                if (group.includes(t) || group.includes(n)) {
                                    group.forEach((g) => {
                                        new_group.push(g)
                                        if (indexToReplace === null) {
                                            indexToReplace = groups.indexOf(group)
                                        } else if (indexToRemove === null) {
                                            indexToRemove = groups.indexOf(group)
                                        }

                                        //groups.splice(groups.indexOf(group), 1)

                                    });
                                }
                            });
                            //indicesToRemove.forEach((i) => {
                            //    groups.splice(i, 1)
                            //});
                            // groups.push(new_group)

                            if (indexToReplace !== null) {
                                groups[indexToReplace] = new_group;
                            }
                            if (indexToRemove !== null) {
                                groups[indexToReplace] = [];

                            }

                        }

                    });
                });

                console.log("Groups: " + groups.length)
                groups.forEach((group) => {
                    console.log(" g: ")
                    group.forEach((g) => {
                        console.log("    " + g.getId())
                    });
                });

                for (let group_index = 0; group_index < groups.length; group_index++){
                    const group = groups[group_index];
                    if (group.length !== 0) {
                        var added = false;
                        MapHeatmapController.addedGroups.forEach((addedGroup) => {
                            if (MapHeatmapController.arraysEqual(addedGroup, group)) {
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
                                    weight: 0.1
                                });
                            })
                            var heatmap = new google.maps.visualization.HeatmapLayer({
                                data: heatmapData
                            });

                            // Remove the heatmap that contains these tasks (if any)

                            console.log("Pre markers:")


                            var matchedMap = null;
                            for (let i = 0; i < MapHeatmapController.addedGroups.length; i++) {
                                console.log("i = " + i)
                                MapHeatmapController.addedGroups[i].forEach((a) => {
                                    console.log("AddGr: " + a.getId());
                                });
                                group.forEach((g) => {
                                    if (MapHeatmapController.addedGroups[i].includes(g)) {
                                        console.log("group: " + g.getId())
                                        matchedMap = i;
                                    }
                                });
                            }

                            if (matchedMap !== null) {
                                //MapHeatmapController.addedGroups.splice(matchedMap, 1)
                                MapHeatmapController.addedGroups[matchedMap] = group;

                                MapHeatmapController.taskHeatmaps[matchedMap].setMap(null);
                                delete MapHeatmapController.taskHeatmaps[matchedMap];
                                MapHeatmapController.taskHeatmaps[matchedMap] = heatmap;

                                console.log("A " + matchedMap);
                                // TODO this sometimes gets the wrong index for late merging additions
                            } else {
                                // Otherwise the index is the end of the list
                                matchedMap = MapHeatmapController.addedGroups.length;
                                MapHeatmapController.addedGroups.push(group);
                                MapHeatmapController.taskHeatmaps.push(heatmap);
                                console.log("B " + matchedMap)
                            }

                            // TODO add a marker for this heatmap
                            MapHeatmapController.addTaskMarkerFor(group, matchedMap);

                            heatmap.setOptions({radius: 150})
                            heatmap.setMap(this.map);
                        }
                    } else {
                        // This is an empty group so we should remove its heatmap and task marker
                        MapHeatmapController.taskHeatmaps[group_index] = [];
                        MapHeatmapController.addedGroups[group_index] = [];

                        MapHeatmapController.removeTaskMarkerFor(group_index);
                    }
                }
            }
        }
    },
    addTaskMarkerFor: function (group, index) {
        console.log("Group size " + group.length + " index = " + index)
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
        var marker = this.$el.gmap("get", "markers")["Group-"+index];
        if (marker) {
            marker.setPosition(newPos);
            marker.setOptions({labelContent: "[" + index + "] " + group.length + " Tasks"});
        } else {


            // 2. Add a new marker with label "X tasks" and related to this group
            this.$el.gmap("addMarker", {
                bounds: false,
                draggable: true,
                id: "Group-" + index,
                position: newPos,
                marker: MarkerWithLabel,
                labelContent: "[" + index + "] " + group.length + " Tasks",
                labelAnchor: new google.maps.Point(25, 65),
                labelClass: "labels",
                labelStyle: {opacity: 1.0},
                raiseOnDrag: false,
                zIndex: 3
            });
            var marker = this.$el.gmap("get", "markers")["Group-" + index];

            // TODO what is this???
            MapTaskController.updateTaskRendering("Group-" + index, this.MarkerColourEnum.RED);

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
    },
    removeTaskMarkerFor: function (index) {
        var marker = this.$el.gmap("get", "markers")["Group-"+index];
        if (marker) {
            marker.setMap(null);
            delete marker;
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
    }
};
