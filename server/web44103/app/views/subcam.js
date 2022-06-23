_.provide("App.Views.SubCam");

App.Views.SubCam = Backbone.View.extend({
    initialize: function(options) {
        this.mapOptions = {
            tilt: 45,
            heading: 90,
            draggable: false,
            scrollwheel: false,
            disableDoubleClickZoom: true,
            zoom: 19,
            scaleControl: false,
            mapTypeControl: false,
            disableDefaultUI: true,
            overviewMapControl: false,
            mapTypeId: google.maps.MapTypeId.SATELLITE
        };
        $.extend(this.mapOptions, options.mapOptions || {});

        this.state = options.state;
        this.views = options.views;

        this.render();
        //this.camera_mjpeg();

        this.bind("update", this.update);
        this.bind("refresh", this.refresh);
    },
    render: function() {
        var self = this;

        this.mapDiv = document.createElement("div");
        this.mapDiv.style.position = 'relative';
        this.mapDiv.style.height = '100%';
        this.mapDiv.style.width = '100%';
        // here
        this.$mapDiv = $(this.mapDiv);
        this.$el.append(this.mapDiv);

        this.$mapDiv.gmap(this.mapOptions);
        this.map = this.$mapDiv.gmap("get", "map");

        this.$el.append($("#cross"));


        this.$mapDiv.gmap("refresh");

    },
    update: function() {
        var target = this.views.target;
        if (target) {
            if (target.isSimulated()) {
                this.$mapDiv.show();

                if (this.$imageDiv) {
                    this.$imageDiv.hide();
                }
            } else {
                var url = this.camera_url(null);
                if (this.imageDiv.alt !== url) {
                    this.imageDiv.src = url;
                    this.imageDiv.alt = url;
                }

                this.$mapDiv.hide();
                this.$imageDiv.show();
            }

            this.map.setCenter(target.getPosition());
            this.map.setZoom(19);

            this.$mapDiv.gmap("refresh");
        }
    }

});



