var MapTaskHeatmapController = {
    taskGroups: {},


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
        this.onTaskMarkerDrag = _.bind(this.onTaskMarkerDrag, context);
        this.onTaskMarkerDragEnd = _.bind(this.onTaskMarkerDragEnd, context);
        this.getTaskGradient = _.bind(this.getTaskGradient, context);
        this.calculateGroupCenter = _.bind(this.calculateGroupCenter, context);
        this.onTaskMarkerMouseover = _.bind(this.onTaskMarkerMouseover, context);
        this.onTaskMarkerMouseout = _.bind(this.onTaskMarkerMouseout, context);
        this.groupTasks = _.bind(this.groupTasks, context);
    },
    /**
     * Bind listeners for agent state add, change and remove events
     */
    bindEvents: function () {
        
    },

    drawTaskMaps: function () {
        if (!this.state.tasks.isEmpty()) {
            const groups = MapTaskHeatmapController.groupTasks(); // Ensure this groups tasks correctly
            groups.forEach((group, index) => {
                if (group.length > 0) {
                    console.log(`Creating TaskGroup with index: ${index}, group size: ${group.length}`);
                    MapTaskHeatmapController.addTaskMarkerFor(group, index);
                } else {
                    console.warn(`Skipped empty group at index: ${index}`);
                }
            });
        }
    },

    groupTasks: function () {
        const groups = [];
        const groupingDist = 250; // Adjust as needed
        const tasks = [];

        this.state.tasks.forEach((task) => {
            tasks.push(task);
        });

        tasks.forEach((task) => {
            let addedToGroup = false;

            groups.forEach((group) => {
                group.forEach((existingTask) => {
                    const distance = google.maps.geometry.spherical.computeDistanceBetween(
                        existingTask.getPosition(),
                        task.getPosition()
                    );
                    if (distance <= groupingDist) {
                        group.push(task);
                        addedToGroup = true;
                        return;
                    }
                });
                if (addedToGroup) return;
            });

            if (!addedToGroup) {
                groups.push([task]);
            }
        });

        return groups;
    },


    addTaskMarkerFor: function (group, index) {
        if (!group || group.length === 0) {
            console.error("Attempted to create marker for an empty or invalid group.");
            return;
        }

        const markerId = `TaskGroup-${index}`;
        console.log(`Creating marker with ID: ${markerId}`);

        // Calculate the center of the group for marker placement
        const newPos = MapTaskHeatmapController.calculateGroupCenter(group);

        // Add marker
        this.$el.gmap("addMarker", {
            bounds: false,
            draggable: false,
            id: markerId,
            position: new google.maps.LatLng(newPos.lat, newPos.lng),
            marker: MarkerWithLabel,
            labelContent: `[${index}] ${group.length} Tasks`,
            labelAnchor: new google.maps.Point(25, 65),
            labelClass: "labels",
            labelStyle: { opacity: 1.0 },
            raiseOnDrag: false,
            zIndex: 3
        });

        const marker = this.$el.gmap("get", "markers")[markerId];
        if (!marker) {
            console.error(`Failed to create marker for TaskGroup-${index}`);
            return;
        }

        $(marker).mouseover(() => this.onTaskMarkerMouseover(marker));
        $(marker).mouseout(() => this.onTaskMarkerMouseout(marker));
        MapTaskHeatmapController.taskMarkers[index] = marker;

        marker.setMap(this.map);
    },


    getTaskGradient: function (taskGroup) {
        const defaultGradient = [
            "rgba(0, 255, 0, 0)",   // Transparent green
            "rgba(0, 255, 0, 1)",   // Bright green
            "rgba(127, 255, 0, 1)", // Lime green
            "rgba(255, 255, 0, 1)", // Yellow
            "rgba(255, 127, 0, 1)", // Orange
            "rgba(255, 0, 0, 1)"    // Red
        ];

        // You can customize the gradient per taskGroup here if needed
        return defaultGradient;
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

    calculateGroupCenter: function (group) {
        let latSum = 0, lngSum = 0;
        group.forEach(task => {
            latSum += task.getPosition().lat();
            lngSum += task.getPosition().lng();
        });
        return {
            lat: latSum / group.length,
            lng: lngSum / group.length
        };
    },

    onTaskMarkerDrag: function (marker, group) {
        const cursorPosition = marker.getPosition();
        const groupTasks = this.taskGroups[group];

        // Update the allocation rendering with a polyline
        const path = groupTasks.map(task => ({
            lat: task.getPosition().lat(),
            lng: task.getPosition().lng(),
        }));
        path.push(cursorPosition);

        this.drawAllocation(`TaskAllocation-${group}`, "blue", path);
    },

    onTaskMarkerDragEnd: function (marker, group) {
        const groupTasks = this.taskGroups[group];
        const taskIds = groupTasks.map(task => task.getId());

        $.post("/allocation/groupAllocate", {
            taskIds: taskIds.toString(),
        });

        this.hidePolyline(`TaskAllocation-${group}`);
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
        console.log("Mouseover on marker:", marker.id);
        // Highlight task marker or trigger any specific functionality
        marker.setOptions({ zIndex: 999 });
    },

    onTaskMarkerMouseout: function (marker) {
        console.log("Mouseout from marker:", marker.id);
        // Reset task marker or remove highlight
        marker.setOptions({ zIndex: 3 });
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
