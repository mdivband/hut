var MapTargetController = {
    revealDistance: 50,
    classifiedIds: [],
    overrideVisible: true,
    overrideShowPopups: true,
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
        this.updateTargetVisibility = _.bind(this.updateTargetVisibility, context);
        this.updateTargetPopupVisibility = _.bind(this.updateTargetPopupVisibility, context);
        this.getTargetAt = _.bind(this.getTargetAt, context);
        this.openScanWindow = _.bind(this.openScanWindow, context);
        this.clearReviewedTarget = _.bind(this.clearReviewedTarget, context);
        this.placeEmptyTargetMarker = _.bind(this.placeEmptyTargetMarker, context);
        this.placeEmptyTargetMarkerByPrio = _.bind(this.placeEmptyTargetMarkerByPrio, context);
        this.checkIcon = _.bind(this.checkIcon, context);
    },
    bindEvents: function () {
        this.state.targets.on("add", function (target) {
            MapTargetController.onTargetAdd(target);
        });

        this.state.targets.on("change:visible", function (target) {
            MapTargetController.updateTargetMarkerVisibility(target);
        });
        $('#lens_target_toggle').change(function () {
            MapTargetController.updateTargetVisibility($(this).is(":checked"));
        });
        $('#lens_target_popup_toggle').change(function () {
            MapTargetController.updateTargetPopupVisibility($(this).is(":checked"));
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
            MapTargetController.openScanWindow(target, marker);
        })


        MapTargetController.updateTargetMarkerIcon(target);
        MapTargetController.updateTargetMarkerVisibility(target);
    },
    openScanWindow : function (target, marker) {
        try {
            self = this;
            this.$el.gmap("openInfoWindow", {minWidth: 300}, null, function (iw) {
                var property = document.createElement("div");


                // NOTE: This is a clumsy way of doing this but I used two templates due to problems with selectively
                //  hiding buttons. In future this should be reworked properly -WH
                if (self.state.getDeepAllowed()) {
                    property.innerHTML = _.template($("#target_scan_edit_dp").html(), {});
                    google.maps.event.addListener(iw, 'domready', function () {
                        //Update task if values changed

                        $(property).on("click", "#scan_shallow_dp", function () {
                            //alert("Scanning shallow");
                            var newId = "(" + target.getId() + ")";
                            marker.setOptions({labelContent: newId});
                            MapTaskController.addShallowScanTask(target.getPosition());
                            MapTargetController.updateTargetMarkerIcon(target);
                            icon = self.icons.TargetShallowScan;
                            marker.setIcon(icon.Image)
                            self.$el.gmap("closeInfoWindow");

                        });
                        $(property).on("click", "#scan_deep_dp", function () {
                            var newId = "[" + target.getId() + "]";
                            marker.setOptions({labelContent: newId});
                            MapTaskController.addDeepScanTask(target.getPosition());
                            MapTargetController.updateTargetMarkerIcon(target);
                            icon = self.icons.TargetDeepScan;
                            marker.setIcon(icon.Image)
                            self.$el.gmap("closeInfoWindow");
                        });
                    });

                } else {
                    property.innerHTML = _.template($("#target_scan_edit").html(), {});
                        google.maps.event.addListener(iw, 'domready', function () {
                            //Update task if values changed

                            $(property).on("click", "#scan_shallow", function () {
                                //alert("Scanning shallow");
                                var newId = "(" + target.getId() + ")";
                                marker.setOptions({labelContent: newId});
                                MapTaskController.addShallowScanTask(target.getPosition());
                                MapTargetController.updateTargetMarkerIcon(target);
                                icon = self.icons.TargetShallowScan;
                                marker.setIcon(icon.Image)
                                self.$el.gmap("closeInfoWindow");
                            });
                        });
                    }
                iw.setContent(property);
                iw.setPosition(target.getPosition());

                self.views.clickedTarget = target;

                });

        } catch (e) {
            alert("Scan window error: " + e)
        }
    },
    checkIcon : function (targetId) {
        if (MapTargetController.classifiedIds.includes(targetId)) {
            var marker = this.$el.gmap("get", "markers")[targetId];
            if (marker) {
                alert("PROBLEM. REMOVE HERE")
            }
        }

    },
    updateTargetMarkerIcon: function (target) {
        var marker = this.$el.gmap("get", "markers")[target.getId()];
        var icon;
        try {
            switch (target.getType()) {
                case this.state.targets.HUMAN:
                    icon = this.icons.TargetHuman;
                    break;
                case this.state.targets.ADJUSTABLE:
                    icon = this.icons.TargetUnknown;
                    break;
                case this.state.targets.ADJ_DEEP_SCAN:
                    icon = this.icons.TargetDeepScan;
                    break;
                case this.state.targets.ADJ_SHALLOW_SCAN:
                    icon = this.icons.TargetShallowScan;
                    break;
                case this.state.targets.ADJ_DISMISSED:
                    icon = this.icons.TargetDismissed;
                    break;
                case this.state.targets.ADJ_FOUND:
                    icon = this.icons.TargetFound;
                    break;
                default:
                    console.log("No icon found for target type " + target.getType());
            }
        } catch (e) {
           alert("eee + " + e)
        }
        if (icon) {
            marker.setIcon(icon.Image);
            marker.setPosition(target.getPosition());
        }
    },
    updateTargetVisibility: function(setting) {
        //alert("pressed, OV was: " + MapTargetController.overrideVisible + " and is now being changed to " + setting)
        MapTargetController.overrideVisible = setting;
        this.state.targets.each(function (target) {
            //alert("updating: " + target.getId() + " and TIV = " + target.isVisible + " and OV = " + MapTargetController.overrideVisible);
            MapTargetController.updateTargetMarkerVisibility(target)
        });
    },

    updateTargetPopupVisibility: function(setting) {
        //alert("pressed, OV was: " + MapTargetController.overrideVisible + " and is now being changed to " + setting)
        MapTargetController.overrideShowPopups = setting;
    },
    updateTargetMarkerVisibility: function (target) {
        //alert("entering, OV = " + MapTargetController.overrideVisible);
        var marker = this.$el.gmap("get", "markers")[target.getId()];
        //  NOTE: If we also want to suppress the popup in the corner, we will need an extra variable here I think
        //        This is because it re-pushes the popup when we re-enable visibility. It may need to only popup on the first time it's used
        if (!marker.getVisible() && target.isVisible() && MapTargetController.overrideShowPopups)
            MapTargetController.popupTargetFound(target);

        //alert("Final step, tgt = " + target.getId() + " and TIV = " + target.isVisible + " and OV = " + MapTargetController.overrideVisible)
        marker.setVisible(target.isVisible() && MapTargetController.overrideVisible);
        //marker.setVisible(target.isVisible());
    },
    checkForReveal: function (agent) {
        // Only check for this reveal if it's not a programmed agent (it shouldn't report yet)
        if (agent.getType() !== "programmed" && agent.getType() !== "leader") {
            this.state.targets.each(function (target) {
                if (!target.isVisible()) {
                    var dist = google.maps.geometry.spherical.computeDistanceBetween(agent.getPosition(), target.getPosition());
                    if (dist < MapTargetController.revealDistance) {
                        $.post("/targets/reveal/" + target.getId());
                    }
                }
            });
        }
    },
    getTargetAt : function (position) {
        var ret = null;
        this.state.targets.each(function (target) {
            var dist = google.maps.geometry.spherical.computeDistanceBetween(position, target.getPosition());
            if (dist === 0) {  // might need to use an epsilon error to cover rounding
                ret = target;
            }
        });
        return ret;
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
    },
    clearReviewedTarget: function (marker) {
        if (marker) {
            marker.setMap(null);
            marker = null;
            delete marker;
        }
    },
    placeEmptyTargetMarker: function (position, targetId, real) {
        try {
            var icon;
            var label;
            if (real) {
                icon = this.icons.TargetHuman;
                label = "CASUALTY"
            } else {
                icon = this.icons.TargetDismissed;
                label = "NO CASUALTY"
            }

            var thisId = targetId + "_done";
            console.log('EmptyMarker added ' + thisId);
            this.$el.gmap("addMarker", {
                bounds: false, //Centre in map if real agent
                marker: MarkerWithLabel,
                draggable: false,
                labelContent: label,
                labelAnchor: new google.maps.Point(50, -18),
                labelClass: "labels",
                labelStyle: {opacity: 0.6},
                id: thisId,
                position: position,
                zIndex: 1,
                opacity: 0.6,
            });

            var marker = this.$el.gmap("get", "markers")[thisId];
            marker.setIcon(icon.Image);
        } catch (e) {
            alert("Placing empty marker error " + e);
        }
    },
    placeEmptyTargetMarkerByPrio: function (position, targetId, prio) {
        try {
            var icon;
            var label;
            if (prio === 3) {
                icon = this.icons.TargetDeepScan;
                label = "HIGH PRIO"
            } else if (prio === 2) {
                icon = this.icons.TargetDeepScan;
                label = "MEDIUM PRIO"
            } else if (prio === 1) {
                icon = this.icons.TargetDeepScan;
                label = "LOW PRIO"
            }

            var thisId = targetId + "_done";
            console.log('EmptyMarker added ' + thisId);
            this.$el.gmap("addMarker", {
                bounds: false, //Centre in map if real agent
                marker: MarkerWithLabel,
                draggable: false,
                labelContent: label,
                labelAnchor: new google.maps.Point(50, -18),
                labelClass: "labels",
                labelStyle: {opacity: 0.6},
                id: thisId,
                position: position,
                zIndex: 1,
                opacity: 0.6,
            });

            var marker = this.$el.gmap("get", "markers")[thisId];
            marker.setIcon(icon.Image);
        } catch (e) {
            alert(e);
        }

    }
};
