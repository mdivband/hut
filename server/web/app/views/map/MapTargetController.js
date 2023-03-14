var MapTargetController = {
    revealDistance: 50,
    classifiedIds: [],
    currentImageName: "",
    currentImageRef: "",
    originalWidth: 0,
    originalHeight: 0,
    scale: 1,
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
        this.onTargetTypeChange = _.bind(this.onTargetTypeChange, context);
        this.checkForReveal = _.bind(this.checkForReveal, context);
        this.popupTargetFound = _.bind(this.popupTargetFound, context);
        this.getTargetAt = _.bind(this.getTargetAt, context);
        this.openScanWindow = _.bind(this.openScanWindow, context);
        this.displayImage = _.bind(this.displayImage, context);
        this.clearReviewedTarget = _.bind(this.clearReviewedTarget, context);
        this.placeEmptyTargetMarker = _.bind(this.placeEmptyTargetMarker, context);
        this.checkIcon = _.bind(this.checkIcon, context);
    },
    bindEvents: function () {
        var self = this;
        this.state.targets.on("add", function (target) {
            MapTargetController.onTargetAdd(target);
        });

        this.state.targets.on("change:visible", function (target) {
            MapTargetController.updateTargetMarkerVisibility(target);
        });

        this.state.targets.on("change:type", function (target) {
            MapTargetController.onTargetTypeChange(target);
        });

        this.state.targets.on("change:highResFileName", function (target) {
            if (self.currentImageName === target.getId() && self.currentImageRef !== target.getHighResFileName()) {
               // alert("received update: " + self.currentImageName + " =?= " + target.getId() + " && " + self.currentImageRef + " =?= " + target.getHighResFileName())
                //var property =  document.getElementById("rev_div");
                //alert(self.property)
                MapTargetController.clearImage(self.property)
                MapTargetController.displayImage(target.getId(), target.getHighResFileName(), self.property)
            }
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
        var thisImg = target.getHighResFileName();
        console.log("thisImg = " + thisImg);
        //google.maps.event.removeListener(this);

        // TODO There is documentation here https://developers.google.com/maps/documentation/javascript/infowindows#maps_infowindow_simple-typescript
        //  that indicates we can create multiple windows (iw) so they can coexist. Probably an array of them, or making
        //  new ones that are hidden but id referenced to the target
        this.$el.gmap("openInfoWindow", {minWidth: 300}, null, function (iw) {
            self.property =  document.getElementById("rev_div");
            if (!self.property) {
                self.property = document.createElement("rev_div");
                self.property.innerHTML = _.template($("#target_scan_edit").html(), {});
            } else {
                //google.maps.event.clearInstanceListeners(iw);
            }
            self.popupListener = google.maps.event.addListener(iw, 'domready', function () {
                google.maps.event.clearListeners(this,'domready');
                //Update task if values changed
                // TODO should really just make a new function for these as only change is boolean status passed
                $(self.property).on("click", "#decide_casualty", function () {
                    var thisId = "(" + target.getId() + ")";

                    // TODO get the current image setting (might be easier in backend)
                    if (!MapTargetController.classifiedIds.includes(thisId)) {
                        MapTargetController.classifiedIds.push(thisId);
                        $.post("/review/classify", {
                            id: target.getId(),
                            ref: thisImg,
                            status: true,
                        });
                    }
                    //var marker = self.$el.gmap("get", "markers")[thisId];
                    if (marker) {
                        var position = marker.getPosition();
                        MapTargetController.clearReviewedTarget(marker);
                        MapTargetController.placeEmptyTargetMarker(position, thisId, true);

                    }
                    self.$el.gmap("closeInfoWindow");

                });

                $(self.property).on("click", "#decide_no_casualty", function () {
                    var thisId = "(" + target.getId() + ")";

                    // TODO get the current image setting (might be easier in backend)
                    if (!MapTargetController.classifiedIds.includes(thisId)) {
                        MapTargetController.classifiedIds.push(thisId);
                        $.post("/review/classify", {
                            id: target.getId(),
                            ref: thisImg,
                            status: false,
                        });
                    }
                    //var marker = self.$el.gmap("get", "markers")[thisId];
                    if (marker) {
                        var position = marker.getPosition();
                        MapTargetController.clearReviewedTarget(marker);
                        MapTargetController.placeEmptyTargetMarker(position, thisId, false);

                    }
                    self.$el.gmap("closeInfoWindow");

                });
                MapTargetController.clearImage(self.property)
                MapTargetController.displayImage(target.getId(), thisImg, self.property)

            });

            iw.setContent(self.property);
            iw.setPosition(target.getPosition());

            self.views.clickedTarget = target;

        });

    },
    clearImage: function (property) {
        try {
            this.canvas = $("#image_review_canvas").get(0)
            this.ctx = $("#image_review_canvas").get(0).getContext("2d")
            this.ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);
        } catch (e) {
            
        }
    },
    displayImage: function (id, iRef, property) {
        console.log("updating image id=" + id + ", iref=" + iRef)
        var self = this;

        this.currentImageName = id
        this.currentImageRef = iRef

        //property.innerHTML += '<canvas id="new_canv"></canvas>'; // the += means we add this to the inner HTML of body
        //document.getElementById('image_review').innerHTML = '<canvas id="new_canv"></canvas>';
        //document.getElementById('rev_div').innerHTML = '<canvas id="new_canv"></canvas>';
        var totalWidth = 640;
        var totalHeight = 360;
        self.canvas = $("#image_review_canvas").get(0)
        self.ctx = $("#image_review_canvas").get(0).getContext("2d")

        //================================

        // We need to determine what type of data this is
        self.scale = 1;
        self.originalWidth = $("#image_review_canvas").width();
        self.originalHeight = $("#image_review_canvas").height();
        console.log("w (rev vs canv): " + $("#image_review").width() + " vs " + $("#image_review_canvas").width())
        console.log("h (rev vs canv): " + $("#image_review_canvas").height() + " vs " + $("#image_review_canvas").height())

        /*
        while ($("#image_review_canvas").width() > $("#image_review").width() || $("#image_review_canvas").height() > $("#image_review").height()) {
            self.scale -= 0.2;
            console.log("rescaling: w = " + $("#image_review_canvas").width() + "; h = " + $("#image_review_canvas").height() + "; (scale = " + self.scale + ")")
            $("#image_review_canvas").width(self.originalWidth * self.scale);
            $("#image_review_canvas").height(self.originalHeight * self.scale);
        }
        
         */

        // TODO maybe just imrev
        self.canvas.width = totalWidth;
        self.canvas.height = totalHeight;
        $("#image_review_canvas").css({top: 0, left: 0, position: 'relative'});

        var img = new Image();
        img.onload = function () {
            self.ctx.lineWidth = 3;
            self.ctx.drawImage(img, 0, 0, self.canvas.width, self.canvas.height);
            self.ctx.strokeRect(0, 0, self.canvas.width, self.canvas.height);
        };
        img.src = "adjustedImages/"+iRef;  // Use the argument, so it works regardless of update flag

        //============================


    },
    checkIcon : function (targetId) {
        if (MapTargetController.classifiedIds.includes(targetId)) {
            var marker = this.$el.gmap("get", "markers")[targetId];
            if (marker) {
                alert("PROBLEM - REMOVE HERE")
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
                case this.state.targets.ADJ_CASUALTY:
                    icon = this.icons.TargetDismissed;
                    break;
                case this.state.targets.ADJ_NO_CASUALTY:
                    icon = this.icons.TargetFound;
                    break;
                default:
                    console.log("No icon found for target type " + target.getType());
            }
        } catch (e) {
           alert("error at mtc + " + e)
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
    onTargetTypeChange: function (target) {
        var self = this;
        var marker = this.$el.gmap("get", "markers")[target.getId()];
        if (marker) {
            //alert("replacing " + target.getId() + " type = " + target.getType())
            if (target.getType() === self.state.targets.ADJ_CASUALTY) {
                MapTargetController.clearReviewedTarget(marker);
                MapTargetController.placeEmptyTargetMarker(target.getPosition(), target.getId(), true);
            } else if (target.getType() === self.state.targets.ADJ_NO_CASUALTY) {
                MapTargetController.clearReviewedTarget(marker);
                MapTargetController.placeEmptyTargetMarker(target.getPosition(), target.getId(), false);
            }
        }
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
