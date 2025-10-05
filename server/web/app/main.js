var simulator = {
    initialisedState: false,
    init: function () {
        this.state = new App.Models.State();
        this.views = _.extend({}, Backbone.Events);

        this.views.map = new App.Views.Map({
            el: $("#map_canvas"),
            mapOptions: {
                center: _.position(18.538446, -72.345299),
                zoomControl: true,
                zoom: 19
            },
            state: this.state,
            views: this.views
        });

        this.views.submap = new App.Views.SubMap({
            el: $("#map_canvas_s"),
            mapOptions: {
                center: _.position(0.0, 0.0)
            },
            state: this.state,
            views: this.views
        });

        // This defines the zoom and pan function including restriction of view
        var self = this
        this.views.layout = new App.Views.Layout({
            el: $("body"),
            state: this.state,
            views: this.views
        });

        //Configure pop up defaults
        spop.defaults = {
            position: 'bottom-left',
            autoclose: 5000
        };

        this.run();
    },
    
    run: function () {
        try {
            var waitTime = 400;
            var self = this;
            var startTime = (new Date()).getTime();
            this.state.fetch()
                .done(function () {
                    if (!self.initialisedState) {
                        self.initialisedState = true;
                        MapController.showMap(false);

                    } else if (!self.state.isInProgress()) {
                        self.views.map.clearAll()
                        var scenario_end_panel = document.createElement("div");

                        // Scenario has ended
                        scenario_end_panel.innerHTML = _.template(
                            $("#scenario_end_panel").html(), {
                            title: "Scenario Ended",
                            description: "This scenario has ended, please close"
                        });
                        $.blockWithContent(scenario_end_panel);
                    }


                    // mock data for the fire data. simply following the agent
                    var agents = self.state.agents;
                    var fires = self.state.fires;

                    // the constant distance for the offset.
                    const FIRE_OFFSET_METERS = 100.0;

                    var newFireData = [];

                    agents.each(function(agent) {
                        var agentPos = agent.getPosition();

                        var randomHeading = Math.random() * 360;

                        // Geometry library to calculate the new position.
                        var fireLatLng = google.maps.geometry.spherical.computeOffset(
                            agentPos,           // Starting point (agent LatLng)
                            FIRE_OFFSET_METERS,
                            randomHeading
                        );

                        // data object for the new fire using the calculated coordinates.
                        newFireData.push({
                            id: agent.getId() + "_fire", // ID for the fire
                            coordinate: {
                                latitude: fireLatLng.lat(),  // new latitude
                                longitude: fireLatLng.lng() // new longitude
                            },
                            visible: agent.isVisible() // mirror the agent visibility
                        });
                    });

                    fires.update(newFireData, {parse: true});


                })
                .always(function () {
                    if (self.state.isInProgress()) {
                        var elapsedTime = ((new Date()).getTime() - startTime);
                        if (elapsedTime < waitTime)
                            window.setTimeout(_.bind(self.run, self), waitTime - elapsedTime);
                        else
                            _.bind(self.run, self)();
                    }
                });
            $('#view_mode').buttonset().css({
                "margin-right": "0px"
            }).find("label").width("50%");
        } catch (e) {
            alert("MainLoop error: " + e)
        }
    }
};