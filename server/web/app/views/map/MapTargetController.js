var MapTargetController = {
    revealDistance: 50,
    revealedNumber: 0,
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

        MapTargetController.updateTargetMarkerIcon(target);
        MapTargetController.updateTargetMarkerVisibility(target);
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
        var id = target.getId();
        var marker = this.$el.gmap("get", "markers")[id];
        if (!marker.getVisible() && target.isVisible()) {
            MapTargetController.popupTargetFound(target);
            MapTargetController.revealedNumber++;
            marker.set("labelContent", id.replace(/\d/g, MapTargetController.revealedNumber));
        }
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
