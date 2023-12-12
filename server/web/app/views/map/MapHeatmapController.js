var MapHeatmapController = {

    /**
     * Binds all the methods to use the given context.
     *  This means the methods can be called just using MapAgentController.method() without
     *  having to worry about calling from the correct context.
     * @param context - Context to bind to.
     */
    bind: function (context) {
        this.bindEvents = _.bind(this.bindEvents, context);
        this.drawTaskMaps = _.bind(this.drawTaskMaps, context)
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

        //console.log(mainAllocation)
        //console.log()
        //console.log(tempAllocation)
        //console.log()
        //console.log(droppedAllocation)
        //console.log()

        if (!this.state.tasks.isEmpty()) {
            let groups = [];
            let grouping_dist = 200;
            let tasks = []
            this.state.tasks.forEach(function (t) {
                tasks.push(t);
            });

            tasks.forEach((t) => {
                if (!MapHeatmapController.checkIfIn2DList(t, groups)) {
                    // This is not in any group so should start a new one
                    groups.push([t])
                }

                let new_group;
                tasks.forEach((n) => {
                    //if (t !== n && !MapHeatmapController.checkIfIn2DList(n, groups) && Math.abs(tasks[n] - tasks[t]) <= grouping_dist) {
                    if (t !== n && !MapHeatmapController.checkIfIn2DList(n, groups) && google.maps.geometry.spherical.computeDistanceBetween(n.getPosition(), t.getPosition()) <= grouping_dist) {
                        groups.forEach((g) => {
                            if (g.includes(t) && !g.includes(n)) {
                                g.push(n)
                                // TODO check this is pushing right
                            }
                        });
                    } else if (t !== n && google.maps.geometry.spherical.computeDistanceBetween(n.getPosition(), t.getPosition()) <= grouping_dist) {
                        // t and n are within range of each other, and n is in a group (t might also be)
                        new_group = []

                        let indicesToRemove = []
                        groups.forEach((group) => {
                            if (group.includes(t) || group.includes(n)) {
                                group.forEach((g) => {
                                    new_group.push(g)
                                    indicesToRemove.push(groups.indexOf(group))
                                    //groups.splice(groups.indexOf(group), 1)

                                });
                            }
                        });
                        indicesToRemove.forEach((i) => {
                            groups.splice(i, 1)
                        });
                        groups.push(new_group)
                    }

                });
            });

            console.log("========================END OF COMPUTATION====================")
            groups.forEach((group) => {
                console.log("-sublist")
                group.forEach((g) => {
                    console.log("-----------> " + g.getId())
                });
            })
            console.log("    ")

            // TODO All we need to do now is not add the normal markers (and replace them with a better one)
            //  And make the heatmap list a property so it isn't overwritten

            groups.forEach((group) => {
                var heatmapData = []
                group.forEach((g) => {
                    heatmapData.push({location: new google.maps.LatLng(g.getPosition().lat(), g.getPosition().lng())});
                })
                var heatmap = new google.maps.visualization.HeatmapLayer({
                    data: heatmapData
                });
                heatmap.setOptions({radius: 50})
                heatmap.setMap(this.map);
            });
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
    }
    
};
