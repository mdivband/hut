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
        this.fireInfoWindow = new google.maps.InfoWindow();
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
            labelAnchor: new google.maps.Point(22, -18),
            labelClass: "labels",
            labelStyle: { opacity: 0.75 },
            position: fire.getPosition(),
            draggable: false,
            // Set initial visibility based on the global toggle and the fire's own property
            visible: this.context.state.fireView() && fire.isVisible(),
            zIndex: 100
        });

        this.updateFireMarkerIcon(fire);
        var marker = this.context.$el.gmap("get", "markers")[id];

        var self = this;
        $(marker).click(function () {
            // get the corresponding fire model from the state
            var clickedFire = self.context.state.fires.get(marker.id);
            if (!clickedFire) return;

            var isAlreadyOpen = self.fireInfoWindow.getAnchor() === marker && self.fireInfoWindow.getMap();

            if (isAlreadyOpen) {
                // if its already open on this marker, close it.
                self.fireInfoWindow.close();
            } else {
                var imageSrc = "data:image/png;base64," + clickedFire.get("image");
                var contentString = '<div class="fire-popup-content">' +
                    '<img src="' + imageSrc + '" width="250" height="250" />' +
                    '</div>';

                self.fireInfoWindow.setContent(contentString);
                self.fireInfoWindow.open(self.context.map, marker);
            }
        });
    },

    onFireChange: function (fire) {
        var marker = this.context.$el.gmap("get", "markers")[fire.getId()];
        if (marker) {
            marker.setPosition(fire.getPosition());
            this.updateFireMarkerIcon(fire);

            if (this.fireInfoWindow.getAnchor() === marker && this.fireInfoWindow.getMap()) {
                var imageSrc = "data:image/png;base64," + fire.get("image");
                var newContent = '<div class="fire-popup-content">' +
                    '<img src="' + imageSrc + '" width="250" height="250" />' +
                    '</div>';
                this.fireInfoWindow.setContent(newContent);
            }
        }
    },

    onFireRemove: function (fire) {
        console.log('Fire removed: ' + fire.getId());
        var marker = this.context.$el.gmap("get", "markers")[fire.getId()];
        if (marker) {
            if (this.fireInfoWindow.getAnchor() === marker) {
                this.fireInfoWindow.close();
            }
            marker.setMap(null);
            delete marker;
        }
        fire.destroy();
    },

    updateFireMarkerIcon: function (fire) {
        var marker = this.context.$el.gmap("get", "markers")[fire.getId()];
        if (marker) {
            var icon = this.context.icons.TargetFireOverlay;
            if (icon) {
                marker.setIcon(icon.Image);
            } else {
                console.warn("Icon 'TargetFireOverlay' not found in map.js icons configuration.");
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
        if (this.fireInfoWindow) {
            this.fireInfoWindow.close();
        }
        this.context.state.fires.each(function (fire) {
            var marker = this.context.$el.gmap("get", "markers")[fire.getId()];
            if (marker) {
                marker.setVisible(false);
            }
        }, this); // Pass 'this' to provide the map view context
    }
};