var MapFireViewController = {
    context: null,

    bind: function (context) {
        this.context = context;
        this.bindEvents = _.bind(this.bindEvents, this);
        this.onFireAdd = _.bind(this.onFireAdd, this);
        this.onFireChange = _.bind(this.onFireChange, this);
        this.onFireRemove = _.bind(this.onFireRemove, this);
        this.updateFireMarkerIcon = _.bind(this.updateFireMarkerIcon, this);
        this.updateFireMarkerVisibility = _.bind(this.updateFireMarkerVisibility, this);
        this.renderAllFires = _.bind(this.renderAllFires, this);
        this.clearAllFires = _.bind(this.clearAllFires, this);
    },

    bindEvents: function () {
        // Listen for events on the dedicated 'fires' collection
        this.context.state.fires.on("add", this.onFireAdd);
        this.context.state.fires.on("remove", this.onFireRemove);
        this.context.state.fires.on("change:visible", this.updateFireMarkerVisibility);
        this.context.state.fires.on("change", this.onFireChange);

        // Listen for the global fireView toggle
        this.context.state.on("change:fireView", function (model, isEnabled) {
            if (isEnabled) {
                this.renderAllFires();
            } else {
                this.clearAllFires();
            }
        }, this);
    },

    onFireAdd: function (fire) {
        var id = fire.getId();
        console.log('Fire added: ' + id);

        this.context.$el.gmap("addMarker", {
            id: id,
            marker: MarkerWithLabel,
            labelContent: id,
            labelAnchor: new google.maps.Point(-4, -45),
            labelClass: "labels",
            labelStyle: { opacity: 1.0},
            position: fire.getPosition(),
            draggable: false,
            // Set initial visibility based on the global toggle and the fire's own property
            visible: this.context.state.fireView() && fire.isVisible(),
            zIndex: 100
        });

        this.updateFireMarkerIcon(fire);
        var marker = this.context.$el.gmap("get", "markers")[id];
        $(marker).click(function () { console.log("Clicked on fire: " + marker.id); });
    },

    onFireChange: function (fire) {
        var marker = this.context.$el.gmap("get", "markers")[fire.getId()];
        if (marker) {
            marker.setPosition(fire.getPosition());
            this.updateFireMarkerIcon(fire);
        }
    },

    onFireRemove: function (fire) {
        console.log('Fire removed: ' + fire.getId());
        var marker = this.context.$el.gmap("get", "markers")[fire.getId()];
        if (marker) {
            marker.setMap(null);
            delete marker;
        }
        fire.destroy();
    },

    updateFireMarkerIcon: function (fire) {
        var marker = this.context.$el.gmap("get", "markers")[fire.getId()];
        if (marker) {
            var icon = this.context.icons.TargetFire; // Assumes 'TargetFire' icon exists in map.js
            if (icon) {
                marker.setIcon(icon.Image);
            } else {
                console.warn("Icon 'TargetFire' not found in map.js icons configuration.");
            }
        }
    },

    updateFireMarkerVisibility: function (fire) {
        var marker = this.context.$el.gmap("get", "markers")[fire.getId()];
        if (marker) {
            var isVisible = this.context.state.fireView() && fire.isVisible();
            marker.setVisible(isVisible);
        }
    },

    renderAllFires: function () {
        console.log("Rendering all fire views.");
        this.context.state.fires.each(function (fire) {
            this.updateFireMarkerVisibility(fire);
        }, this); // Pass 'this' to provide the map view context
    },

    clearAllFires: function () {
        console.log("Clearing all fire views.");
        this.context.state.fires.each(function (fire) {
            var marker = this.context.$el.gmap("get", "markers")[fire.getId()];
            if (marker) {
                marker.setVisible(false);
            }
        }, this); // Pass 'this' to provide the map view context
    }
};