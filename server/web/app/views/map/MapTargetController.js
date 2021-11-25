var MapTargetController = {
    revealDistance: 50,
    /**
     * Binds all the methods to use the given context.
     *  This means the methods can be called just using MapAgentController.method() without
     *  having to worry about calling from the correct context.
     * @param context - Context to bind to.
     */
    bind: function (context) {
        this.bindEvents = _.bind(this.bindEvents, context);
        this.onTargetAdd = _.bind(this.onTargetAdd, context);
        this.updateTargetMarkerIcon = _.bind(this.updateTargetMarkerIcon, context);
        this.updateTargetMarkerVisibility = _.bind(this.updateTargetMarkerVisibility, context);
        this.checkForReveal = _.bind(this.checkForReveal, context);
        this.popupTargetFound = _.bind(this.popupTargetFound, context);
        this.openScanWindow = _.bind(this.openScanWindow, context);
    },
    bindEvents: function () {
        this.state.targets.on("add", function (target) {
            MapTargetController.onTargetAdd(target);
        });

        this.state.targets.on("change:visible", function (target) {
            MapTargetController.updateTargetMarkerVisibility(target);
        });
    },
    onTargetAdd: function (target) {
        console.log('Target added ' + target.getId());
        var id = target.getId();
        this.$el.gmap("addMarker", {
            bounds: false, //Centre in map if real agent
            marker: MarkerWithLabel,
            draggable: false,
            labelContent: id,
            labelAnchor: new google.maps.Point(50, -18),
            labelClass: "labels",
            labelStyle: {opacity: 1.0},
            id: id,
            position: target.getPosition(),
            zIndex: 1
        });

        var marker = this.$el.gmap("get", "markers")[id];
        self = this;
        $(marker).click(function () {
            MapTargetController.openScanWindow(id, marker, marker.getPosition());
        })


        MapTargetController.updateTargetMarkerIcon(target);
        MapTargetController.updateTargetMarkerVisibility(target);
    },
    openScanWindow : function (id, marker, position) {
        try {
            self = this;
            this.$el.gmap("openInfoWindow", {minWidth: 300}, null, function (iw) {
                var property = document.createElement("div");

                property.innerHTML = _.template($("#target_scan_edit").html(), {});
                iw.setContent(property);
                iw.setPosition(position);

                //if (!self.state.isEdit()) {
                //    $("#task_edit_update").hide();
                //    $("#task_edit_delete").hide();
                //    $("#task_priority").attr("readonly","readonly");
                //    $("#group_size").attr("readonly","readonly");
                //}

                google.maps.event.addListener(iw, 'domready', function () {
                    //Update task if values changed
                    $(property).on("click", "#scan_shallow", function () {
                        //alert("Scanning shallow");
                        var newId = "(" + id + ")";
                        marker.setOptions({labelContent: newId});
                        MapTaskController.addShallowScanTask(position);
                        // TODO send instant image back (or maybe send a drone to this target?)
                    });
                    $(property).on("click", "#scan_deep", function () {
                        try {
                            var newId = "[" + id + "]";
                            marker.setOptions({labelContent: newId});
                            MapTaskController.addDeepScanTask(position);
                            // TODO change the sprite to denote change
                            // TODO consider also making this now not possible to scan again
                        } catch (e) {
                            alert("1112: " + e)
                        }
                    });

                });
            });
        } catch (e) {
            alert(e)
        }
    },
    updateTargetMarkerIcon: function (target) {
        var marker = this.$el.gmap("get", "markers")[target.getId()];
        var icon;
        switch (target.getType()) {
            case this.state.targets.HUMAN:
                icon = this.icons.TargetHuman;
                break;
            default:
                console.log("No icon found for target type " + target.getType());
        }
        if (icon) {
            marker.setIcon(icon.Image);
            marker.setPosition(target.getPosition());
        }
    },
    updateTargetMarkerVisibility: function (target) {
        var marker = this.$el.gmap("get", "markers")[target.getId()];
        if (!marker.getVisible() && target.isVisible())
            MapTargetController.popupTargetFound(target);
        marker.setVisible(target.isVisible());
    },
    checkForReveal: function (agent) {
        this.state.targets.each(function (target) {
            if (!target.isVisible()) {
                var dist = google.maps.geometry.spherical.computeDistanceBetween(agent.getPosition(), target.getPosition());
                if (dist < MapTargetController.revealDistance) {
                    $.post("/targets/reveal/" + target.getId());
                }
            }
        });
    },
    popupTargetFound: function (target) {
        var self = this;
        var uid = target.getId() + "_found";
        var content = _.template($("#popup_left_right").html(), {
            left_content: "A target has been found",
            right_content: "View",
            uid: uid
        });

        spop({
            template: content,
            style: 'default'
        });

        $("#" + uid).on('click', function () {
            self.map.panTo(target.getPosition());
            self.map.setZoom(19);
        });
    }
};
