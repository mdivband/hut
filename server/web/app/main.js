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
    
    // Removed initializeButtons and handleButtonAction since master-view.js handles this
    
    toggleDroneView: function() {
        // Implement drone view logic
        // Add actual implementation here when needed
    },
    
    toggleMissionView: function() {
        // Implement mission view logic
        // Add actual implementation here when needed
    },
    
    toggleFireView: function() {
        // Implement fire view logic
        // Add actual implementation here when needed
    },
    
    toggleDDSLog: function() {
        // Implement DDS log logic
        // Add actual implementation here when needed
    },
    
    togglePathPlanningView: function() {
        // Implement path planning view logic
        // Add actual implementation here when needed
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