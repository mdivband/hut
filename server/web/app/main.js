var simulator = {
    initialisedState: false,
    init: function () {
        try {
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

            this.views.review = new App.Views.Review({
                el: $("#image_review"),
                state: this.state,
                views: this.views,
                ctx: $("#image_review_canvas").get(0).getContext("2d"),
                canvas: $("#image_review_canvas").get(0),
                forEditMode: true
            });

            this.views.camera = new App.Views.Camera({
                el: $("#camera"),
                mapOptions: {
                    center: _.position(50.937175, -1.40507)
                },
                state: this.state,
                views: this.views
            });

            this.views.editgraph = new App.Views.Graph({
                el: $("#accordion_agent_schedule_m"),
                state: this.state,
                views: this.views,
                ctx: $("#schedule").get(0).getContext("2d"),
                canvas: $("#schedule").get(0),
                forEditMode: false
            });

            this.views.graph = new App.Views.Graph({
                el: $("#accordion_agent_schedule_e"),
                state: this.state,
                views: this.views,
                ctx: $("#edit_schedule").get(0).getContext("2d"),
                canvas: $("#edit_schedule").get(0),
                forEditMode: true
            });

            this.views.prediction = new App.Views.Prediction({
                el: $("#prediction_canvas"),
                state: this.state,
                views: this.views,
                type: "allocation",
                canvas: $("#prediction").get(0)
            });

            this.views.missionPrediction = new App.Views.Prediction({
                el: $("#mission_prediction_canvas"),
                state: this.state,
                views: this.views,
                type: "mission",
                canvas: $("#mission_prediction").get(0)
            });

            this.views.boundedPrediction = new App.Views.Prediction({
                el: $("#bounded_prediction_canvas"),
                state: this.state,
                views: this.views,
                type: "bounded",
                canvas: $("#bounded_prediction").get(0)
            });

            this.views.images = new App.Views.Images({
                el: $("#scans_list"),
                state: this.state,
                views: this.views,
                //ctx: $("#scans_button_panel").get(0).getContext("2d"),
                //canvas: $("#scans_button_panel").get(0),
                forEditMode: true
            });

            /*
            try {
                this.views.subCam = new App.Views.SubCam({
                    el: $("#camera_det"),
                    state: this.state,
                    views: this.views,
                    //ctx: $("#scans_button_panel").get(0).getContext("2d"),
                    //canvas: $("#scans_button_panel").get(0),
                    forEditMode: true
                });
            } catch (e) {
                alert("error creating subCam: " + e)
            }
             */

            // setup accordion for jquery ui
            $("#accordion_smallview").accordion({
                collapsible: true,
                active: false
            });
            $("#accordion_agent_schedule_m").accordion({
                collapsible: true,
                //active: false
            });
            $("#accordion_score").accordion({
                collapsible: true
            });
            $("#accordion_slider").accordion({
                collapsible: true
            });
            $("#accordion_sotp_m").accordion({
                collapsible: true,
                active: false
            });
            $("#accordion_otherlayer_m").accordion({
                collapsible: true,
                active: false
            });
            $("#accordion_agent_schedule_e").accordion({
                collapsible: true,
            });
            $("#prediction_canvas").accordion({
                collapsible: true,
                heightStyle: "content"
            });
            $("#mission_prediction_canvas").accordion({
                collapsible: true,
                heightStyle: "content",
                active: false
            });
            $("#bounded_prediction_canvas").accordion({
                collapsible: true,
                heightStyle: "content"
            });
            $("#score_canvas").accordion({
                collapsible: true
            });
            $("#accordion_sotp_e").accordion({
                collapsible: true,
            });
            $("#camera_canvas_s").append($("#camera"));

            $("#image_review_canvas").draggable({
                drag: function (event, ui) {
                    if (ui.position.top > 0) {
                        ui.position.top = 0;
                    }
                    var maxtop = ui.helper.parent().height() - ui.helper.height();
                    if (ui.position.top < maxtop) {
                        ui.position.top = maxtop;
                    }
                    if (ui.position.left > 0) {
                        ui.position.left = 0;
                    }
                    var maxleft = ui.helper.parent().width() - ui.helper.width();
                    if (ui.position.left < maxleft) {
                        ui.position.left = maxleft;
                    }
                }
            });

            // This defines the zoom and pan function including restriction of view
            var self = this
            $("#image_review_canvas").bind('mousewheel', function (e) {
                var cursorX = e.pageX;
                var cursorY = e.pageY - 167;  //TODO make general

                // Centre of IMAGE
                var centreX = $(this).position().left + $(this).width() / 2;
                var centreY = $(this).position().top + $(this).height() / 2;

                var oldSizeX = $(this).width();
                var oldSizeY = $(this).height();

                if (e.originalEvent.wheelDelta / 120 > 0) {
                    // Scroll up (zoom in)
                    if (self.views.review.scale < 20) {
                        self.views.review.scale += 0.5
                        $(this).width(self.views.review.originalWidth * self.views.review.scale);
                        $(this).height(self.views.review.originalHeight * self.views.review.scale);
                    }
                } else {
                    // Scroll down (zoom out)
                    // these statements ensure the image always fills canvas in both dimensions
                    //if (($(this).height()/1.5) > $("#image_review").height() || ($(this).width()/1.5) > $("#image_review").width()) {
                    if (self.views.review.originalWidth * (self.views.review.scale - 0.5) > $("#image_review").width()
                        && self.views.review.originalHeight * (self.views.review.scale - 0.5) > $("#image_review").height()) {
                        self.views.review.scale -= 0.5
                        $(this).width(self.views.review.originalWidth * self.views.review.scale);
                        $(this).height(self.views.review.originalHeight * self.views.review.scale);
                    }
                }

                var x = $(this).width() - oldSizeX;
                var y = $(this).height() - oldSizeY;

                var diffX = cursorX - centreX;
                var diffY = cursorY - centreY;
                var relX = diffX / ($(this).width() / 2)
                var relY = diffY / ($(this).height() / 2)

                var newCentreX = centreX + (-relX * x / 2);
                var newCentreY = centreY + (-relY * y / 2);
                var newL = newCentreX - $(this).width() / 2;
                var newT = newCentreY - $(this).height() / 2;

                if (newL > 0) {
                    newL = 0;
                } else if (newL + $(this).width() < $("#image_review").width()) {
                    newL = $("#image_review").width() - $(this).width();
                }

                if (newT > 0) {
                    newT = 0;
                } else if (newT + $(this).height() < $("#image_review").height()) {
                    newT = $("#image_review").height() - $(this).height();
                }

                $(this).css({top: newT, left: newL, position: 'relative'});
            });

            this.views.control = new App.Views.Control({
                el: $("#control"),
                state: this.state,
                views: this.views
            });
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
        } catch (e) {
            alert("Main creation error : " + e);
        }
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
                        MapController.swapMode(self.state.getEditMode(), false);

                        /*
                        if (self.state.getUserName() === "" && self.state.isLoggingById()) {
                            // TODO get their name, also log it in backend
                            var name = null;
                            while (name == null || name === "") {
                                name = prompt("Please enter your prolific ID", "");
                            }
                            $.post("/mode/scenario/registerUser", {
                                userName: name
                            });
                        }
                         */

                        if (self.state.attributes.prov_doc == null) {
                            var api = new $.provStoreApi({
                                username: 'atomicorchid',
                                key: '2ce8131697d4edfcb22e701e78d72f512a94d310'
                            });
                            var ps = new PostService();
                            ps.initProv(api, 'uav_silver_commander', self.state.getGameId());
                        }

                        if (self.state.getGameType() === self.state.GAME_TYPE_SCENARIO && !self.state.isInProgress()) {
                            var description_panel = document.createElement("div");
                            description_panel.innerHTML = _.template($("#description_panel").html(), {
                                title: "Scenario " + self.state.getGameId(),
                                description: self.state.getGameDescription()
                            });
                            $.blockWithContent(description_panel);
                            $('#start_scenario').on('click', function () {
                                $.post("/mode/scenario/start", {}, function () {
                                    $.unblockUI();
                                    self.run();
                                });
                            });
                        }
                    } else if (!self.state.isInProgress()) {
                        self.views.map.clearAll()
                        var scenario_end_panel = document.createElement("div");
                        if (!self.state.hasPassthrough()) {
                            // Return to menu
                            scenario_end_panel.innerHTML = _.template($("#scenario_end_panel").html(), {
                                title: "Scenario Ended",
                                description: "This scenario has ended, please close."
                            });
                            $.blockWithContent(scenario_end_panel);
                            $('#end_scenario').on('click', function () {
                                $.post("/reset");
                                window.history.back();
                            });
                        } else {
                            // Has a scenario to pass through too
                            scenario_end_panel.innerHTML = _.template($("#scenario_passthrough_panel").html(), {
                                title: "Scenario Ended",
                                description: "This scenario has ended, please press close to continue to the next experiment"
                            });
                            $.blockWithContent(scenario_end_panel);
                            var nextScenarioDiv = $("#next_scenario");

                            nextScenarioDiv.on('click', function () {
                                var fileName = self.state.getNextFileName();
                                //$.post("/reset");
                                $.post('/mode/scenario', {'file-name': fileName}, function () {
                                    nextScenarioDiv[0].style = 'animation: popout 0.5s forwards;';
                                    nextScenarioDiv[0].addEventListener("animationend", function () {
                                        window.location = "/sandbox.html";
                                    })
                                }).fail(function () {
                                    showError("Unable to start scenario.");
                                });

                                var description_panel = document.createElement("div");
                                description_panel.innerHTML = _.template($("#description_panel").html(), {
                                    title: self.state.getGameId(),
                                    description: self.state.getGameDescription()
                                });
                                $.blockWithContent(description_panel);
                                $('#start_scenario').on('click', function () {
                                    $.post("/mode/scenario/start", {}, function () {
                                        $.unblockUI();
                                        self.run();
                                    });
                                });
                            });
                        }
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