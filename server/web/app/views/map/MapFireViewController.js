var MapFireViewController = {
    context: null,
    fireImageCache: {}, // a map of the previous fire images per fire (key=fire ID)

    bind: function (context) {
        this.context = context;
        this.fireImageCache = {};
        this.bindEvents = _.bind(this.bindEvents, this);
        this.onFireAdd = _.bind(this.onFireAdd, this);
        this.onFireChange = _.bind(this.onFireChange, this);
        this.onFireRemove = _.bind(this.onFireRemove, this);
        this.updateFireMarkerIcon = _.bind(this.updateFireMarkerIcon, this);
        this.updateFireMarkerVisibility = _.bind(this.updateFireMarkerVisibility, this);
        this.renderAllFires = _.bind(this.renderAllFires, this);
        this.clearAllFires = _.bind(this.clearAllFires, this);
        this.openFireInfoWindow = _.bind(this.openFireInfoWindow, this);
    },

    bindEvents: function () {
        this.fireInfoWindow = new google.maps.InfoWindow({
            content: '<div></div>' // Placeholder
        });
        this.context.state.fires.on("add", this.onFireAdd);
        this.context.state.fires.on("remove", this.onFireRemove);
        this.context.state.fires.on("change:visible", this.updateFireMarkerVisibility);
        this.context.state.fires.on("change", this.onFireChange);

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

        var imageSrc = "data:image/png;base64," + fire.get("image");
        this.fireImageCache[id] = [imageSrc];
        console.log(`Initialized cache for ${id}.`);

        this.context.$el.gmap("addMarker", {
            id: id,
            marker: MarkerWithLabel,
            labelContent: id,
            labelAnchor: new google.maps.Point(-2, -45),
            labelClass: "labels",
            labelStyle: { opacity: 1.0},
            position: fire.getPosition(),
            draggable: false,
            visible: this.context.state.fireView() && fire.isVisible(),
            zIndex: 100
        });

        this.updateFireMarkerIcon(fire);
        var marker = this.context.$el.gmap("get", "markers")[id];

        var self = this;
        $(marker).click(function () {
            var clickedFire = self.context.state.fires.get(marker.id);
            if (!clickedFire) return;

            var isAlreadyOpen = self.fireInfoWindow.getAnchor() === marker && self.fireInfoWindow.getMap();
            if (isAlreadyOpen) {
                self.fireInfoWindow.close();
            } else {
                self.openFireInfoWindow(clickedFire, marker);
            }
        });
    },

    // this has a minor chance of causing a sync issue. difficult to reproduce
    onFireChange: function (fire) {
        var id = fire.getId();
        var marker = this.context.$el.gmap("get", "markers")[id];
        if (marker) {
            marker.setPosition(fire.getPosition());
            this.updateFireMarkerIcon(fire);

            var newImageSrc = "data:image/png;base64," + fire.get("image");
            if (this.fireImageCache[id]) {
                var imageHistory = this.fireImageCache[id];
                var lastImage = imageHistory[imageHistory.length - 1];

                if (newImageSrc !== lastImage) {
                    imageHistory.push(newImageSrc);
                    console.log(`Cached new image for ${id}. Total versions: ${imageHistory.length}`);

                    if (this.fireInfoWindow.getAnchor() === marker && this.fireInfoWindow.getMap()) {
                        this.openFireInfoWindow(fire, marker);
                    }
                }
            }
        }
    },

    // fire satellite history info popup
    openFireInfoWindow: function(fire, marker) {
        var self = this;
        var fireId = fire.getId();
        var imageHistory = this.fireImageCache[fireId];

        if (!imageHistory || imageHistory.length === 0) {
            return; // Should not happen, but a good safeguard
        }

        var currentIndex = imageHistory.length - 1;
        var maxIndex = imageHistory.length - 1;

        // create the HTML content for the InfoWindow
        var contentString = `
            <div class="fire-popup-container" data-fire-id="${fireId}">
                <img id="fire-popup-image-${fireId}" src="${imageHistory[currentIndex]}" width="250" height="250" />
                <div class="fire-popup-controls">
                    <input type="range" id="fire-popup-slider-${fireId}" min="0" max="${maxIndex}" value="${currentIndex}" style="width: 100%;">
                    <span id="fire-popup-label-${fireId}" style="display: block; text-align: center; margin-top: 5px;">
                        Timestamp ${currentIndex + 1} of ${maxIndex + 1}
                    </span>
                </div>
            </div>`;

        this.fireInfoWindow.setContent(contentString);
        this.fireInfoWindow.open(this.context.map, marker);

        // the 'domready' event fires after the content has been attached to the DOM.
        google.maps.event.addListenerOnce(this.fireInfoWindow, 'domready', function() {
            var slider = document.getElementById(`fire-popup-slider-${fireId}`);
            var image = document.getElementById(`fire-popup-image-${fireId}`);
            var label = document.getElementById(`fire-popup-label-${fireId}`);

            if (slider && image && label) {
                slider.addEventListener('input', function(e) {
                    var newIndex = parseInt(e.target.value, 10);
                    image.src = self.fireImageCache[fireId][newIndex];
                    label.textContent = `Timestamp ${newIndex + 1} of ${imageHistory.length}`;
                });
            }
        });
    },

    onFireRemove: function (fire) {
        var id = fire.getId();
        console.log('Fire removed: ' + id);
        delete this.fireImageCache[id];
        console.log(`Cleared cache for removed fire ${id}.`);

        var marker = this.context.$el.gmap("get", "markers")[id];
        if (marker) {
            if (this.fireInfoWindow.getAnchor() === marker) {
                this.fireInfoWindow.close();
            }
            marker.setMap(null);
            delete this.context.$el.gmap("get", "markers")[id];
        }
    },

    updateFireMarkerIcon: function (fire) {
        var marker = this.context.$el.gmap("get", "markers")[fire.getId()];
        if (marker) {
            var icon = this.context.icons.TargetFire;
            if (icon) { marker.setIcon(icon.Image); }
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
        this.context.state.fires.each(this.updateFireMarkerVisibility, this);
    },

    clearAllFires: function () {
        console.log("Clearing all fire views.");
        this.fireImageCache = {};
        console.log("Cleared all fire image history.");
        if (this.fireInfoWindow) { this.fireInfoWindow.close(); }
        this.context.state.fires.each(function (fire) {
            var marker = this.context.$el.gmap("get", "markers")[fire.getId()];
            if (marker) { marker.setVisible(false); }
        }, this);
    }
};