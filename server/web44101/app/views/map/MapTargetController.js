var MapTargetController = {
    revealDistance: 50,
    classifiedIds: [],
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
        this.getTargetAt = _.bind(this.getTargetAt, context);
        this.openScanWindow = _.bind(this.openScanWindow, context);
        this.clearReviewedTarget = _.bind(this.clearReviewedTarget, context);
        this.placeEmptyTargetMarker = _.bind(this.placeEmptyTargetMarker, context);
        this.checkIcon = _.bind(this.checkIcon, context);
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
            MapTargetController.openScanWindow(target, marker);
        })


        MapTargetController.updateTargetMarkerIcon(target);
        MapTargetController.updateTargetMarkerVisibility(target);
    },
    openScanWindow : function (target, marker) {
        self = this;
        this.$el.gmap("openInfoWindow", {minWidth: 300}, null, function (iw) {
            var property = document.createElement("div");
            property.innerHTML = _.template($("#target_scan_edit").html(), {});
            google.maps.event.addListener(iw, 'domready', function () {
                //Update task if values changed
                // TODO should really just make a new function for these as only change is boolean status passed
                $(property).on("click", "#decide_casualty", function () {
                    var thisId = "(" + target.getId() + ")";
                    //var self = this;
                    // TODO get the current image setting (might be easier in backend)
                    //var img = MapController.getCurrentImage();
                    //var tgtId = this.views.review.currentImageName;
                    if (!MapTargetController.classifiedIds.includes(thisId)) {
                        MapTargetController.classifiedIds.push(thisId);
                        $.post("/review/classify", {
                            ref: img,
                            status: true,
                        });
                    }
                    var marker = self.$el.gmap("get", "markers")[thisId];
                    if (marker) {
                        var position = marker.getPosition();
                        MapTargetController.clearReviewedTarget(marker);
                        MapTargetController.placeEmptyTargetMarker(position, thisId, true);

                    }
                    self.$el.gmap("closeInfoWindow");

                });

                $(property).on("click", "#decide_no_casualty", function () {
                    var thisId = "(" + target.getId() + ")";
                    // ADDED
                    //var self = this;
                    // TODO get the current image setting (might be easier in backend)
                    //var img = MapController.getCurrentImage();
                    //var tgtId = this.views.review.currentImageName;
                    if (!MapTargetController.classifiedIds.includes(thisId)) {
                        MapTargetController.classifiedIds.push(thisId);
                        $.post("/review/classify", {
                            ref: img,
                            status: false,
                        });
                    }
                    var marker = self.$el.gmap("get", "markers")[thisId];
                    if (marker) {
                        var position = marker.getPosition();
                        MapTargetController.clearReviewedTarget(marker);
                        MapTargetController.placeEmptyTargetMarker(position, thisId, false);

                    }
                    self.$el.gmap("closeInfoWindow");

                });
            });

            iw.setContent(property);
            iw.setPosition(target.getPosition());

            self.views.clickedTarget = target;

            });
    },
    checkIcon : function (targetId) {
        if (MapTargetController.classifiedIds.includes(targetId)) {
            var marker = this.$el.gmap("get", "markers")[targetId];
            if (marker) {
                //console.log("problem, rmeove here")
                alert("PROIBLEM> REMOVE HERE")
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
            alert(e);
        }
    }
};
