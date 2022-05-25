var userRole = $("body").attr('id');
console.log(userRole)

var simulator = {
    surveyDone: false,
    initialisedState: false,
    waiting: false,
    waitingForPlanner: false,
    scenarioNumber: 0,
    passedThrough: false,
    prolificID: "undefined",
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

        if (userRole == "planner") {
            this.views.wind = new App.Views.Wind({
                el: $("#accordion_wind_m"),
                state: this.state,
                views: this.views,
                ctx: $("#wind_m").get(0).getContext("2d"),
                canvas: $("#wind_m").get(0)
            });

            this.views.editwind = new App.Views.Wind({
                el: $("#accordion_wind_e"),
                state: this.state,
                views: this.views,
                ctx: $("#wind_e").get(0).getContext("2d"),
                canvas: $("#wind_e").get(0)
            });
        }

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
            collapsible: true
        });
        $("#accordion_smallview").hide();
        $("#accordion_agent_schedule_m").accordion({
            collapsible: true,
            //active: false
        });
        $("#accordion_agent_schedule_m").hide();
        $("#accordion_sotp_m").accordion({
            collapsible: true
        });
        $("#accordion_sotp_m").hide();
        $("#accordion_wind_m").accordion({
            collapsible: true
        });
        $("#accordion_wind_e").accordion({
            collapsible: true
        });
        $("#accordion_chat").accordion({
            collapsible: true
        });
        $("#accordion_otherlayer_m").accordion({
            collapsible: true,
            active: false
        });
        $("#accordion_otherlayer_m").hide();
        $("#accordion_agent_schedule_e").accordion({
            collapsible: true,
        });
        $("#accordion_agent_schedule_e").hide();
        $("#accordion_sotp_e").accordion({
            collapsible: true,
        });
        $("#accordion_sotp_e").hide();

        $("#camera_canvas_s").append($("#camera"));

        $("#image_review_canvas").draggable({
            drag: function(event, ui) {
                if (ui.position.top > 0) {
                    ui.position.top = 0;
                }
                var maxtop = ui.helper.parent().height() - ui.helper.height();
                if ( ui.position.top < maxtop) {
                    ui.position.top = maxtop;
                }
                if ( ui.position.left > 0) {
                    ui.position.left = 0;
                }
                var maxleft = ui.helper.parent().width() - ui.helper.width();
                if ( ui.position.left < maxleft) {
                    ui.position.left = maxleft;
                }
            }
        });

        $("#button_send_msg").on('click', function () {
            $.post('/chat/send', {
                userRole: userRole,
                message: $("#message_box").val()
            }, function() {
                $("#message_box").val("");
            });
        });

        var self = this;
        this.state.on("change:chatLog", function () {
            $("#chat_history").empty();
            var chatLog = self.state.getChatLog();
            if (chatLog.length > 0) {
                chatLog.forEach(function (item) {
                    var newLine = $("<p>").appendTo($("#chat_history"));
                    newLine.text(item);
                })
            }
            $("#chat_history").scrollTop($("#chat_history")[0].scrollHeight);
            if (chatLog.length > 1 && !chatLog[chatLog.length - 1].toLowerCase().startsWith(userRole)) {
                var uid = "message_" + chatLog.length;
                var content = _.template($("#popup_left_right").html(), {
                    left_content: "New chat message: " + chatLog[chatLog.length - 1],
                    right_content: "View",
                    uid: uid
                });

                spop({
                    template: content,
                    style: 'default',
                    autoclose: 10000
                });

                $("#" + uid).on('click', function() {
                    MapController.swapMode(1, true);
                });
            }
        });

        // This defines the zoom and pan function including restriction of view
        $("#image_review_canvas").bind('mousewheel', function(e) {
            var cursorX = e.pageX;
            var cursorY = e.pageY - 167;  //TODO make general

            // Centre of IMAGE
            var centreX = $(this).position().left + $(this).width() / 2;
            var centreY = $(this).position().top + $(this).height() / 2;

            var oldSizeX = $(this).width();
            var oldSizeY = $(this).height();

            if(e.originalEvent.wheelDelta /120 > 0) {
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

            var newCentreX = centreX + (-relX * x/2);
            var newCentreY = centreY + (-relY * y/2);
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

            $(this).css({top: newT, left: newL, position:'relative'});
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
            position  : 'bottom-left',
            autoclose: 15000
        };

        this.run();
    },
    passthrough: function (self) {
        $("#overlay_div").empty();
        $("#overlay_div").hide();

        var scenario_end_panel = document.createElement("div");
        if (!self.state.isPassthrough()) {
            // Return to menu
            scenario_end_panel.innerHTML = _.template($("#scenario_end_panel").html(), {
                title: "Scenario Ended",
                description: "This scenario has ended, thank you for taking part in this experiment. You may now close this window"
            });

            $.blockWithContent(scenario_end_panel);

            $('#end_scenario').on('click', function () {
                $.post("/reset");
                window.history.back();
            });
        } else {
            // Has a scenario to pass through too
            scenario_end_panel.innerHTML = _.template($("#scenario_end_panel").html(), {
                title: "Scenario Ended",
                description: "This scenario has ended, please press close to continue to the next experiment"
            });

            $.blockWithContent(scenario_end_panel);
            var endScenarioDiv = $("#end_scenario");

            endScenarioDiv.on('click', function () {
                self.views.map.clearAll()
                if (userRole == "planner") {
                    var fileName = self.state.getNextFileName();
                    $.post('/reset');
                    $.post('/mode/scenario', {'file-name': fileName}, function () {
                        endScenarioDiv[0].style = 'animation: popout 0.5s forwards;';
                        endScenarioDiv[0].addEventListener("animationend", function () {
                            window.location = "/sandbox.html";
                        })
                    }).fail(function () {
                        showError("Unable to start scenario.");
                    });
                }

                self.surveyDone = true;
                self.initialisedState = false;
                self.waiting = false;
                self.waitingForPlanner = false;
                self.passedThrough = true;
                MapTargetController.revealedNumber = 0;
                _.bind(self.run, self)();
            });
        }
    },
    run: function () {
        var waitTime = 400;
        var self = this;
        var startTime = (new Date()).getTime();
        var contingency = window.setTimeout(_.bind(self.run, self), 2000);
        this.state.fetch()
            .done(function () {
                if (self.passedThrough) {
                    // need to fetch state once more so self.state contains new scenario game description etc.
                    self.passedThrough = false;
                    _.bind(self.run, self)();
                } else if (!self.surveyDone && self.state.getCompletedSurveys() < self.state.getRequiredUsers()) {
                    var closeSurvey = $('<button id="close_survey" style="cursor: pointer;">Close Survey</button>').appendTo($("#overlay_div"));
                    $('<br>').appendTo($("#overlay_div"));
                    var initialSurvey = "https://forms.office.com/Pages/ResponsePage.aspx?id=-XhTSvQpPk2-iWadA62p2CmPPgx944RCrlRRT-uovIBUN0JLOVk4SEhUOEtaTTA5MThXUUc3MldYQi4u&embed=true";
                    var surveySource = initialSurvey;
                    var surveyFrame = $('<iframe width="40%" height= "90%" src=' + surveySource + ' frameborder= "0" marginwidth= "0" marginheight= "0" style= "border: none; max-width:100%; max-height:100vh" allowfullscreen webkitallowfullscreen mozallowfullscreen msallowfullscreen> </iframe>').appendTo($("#overlay_div"));
                    closeSurvey.on('click', function () {
                        var isSure = confirm("Have you completed and submitted the survey?");
                        if (isSure) {
                            self.surveyDone = true;
                            $.post("/mode/scenario/closeSurvey", {}, function () {
                                $("#overlay_div").empty();
                                $("#overlay_div").css('opacity', '1.0');
                                var closeVideo = $('<button id="close_video" style="cursor: pointer;">Close Video</button>').appendTo($("#overlay_div"));
                                $('<br>').appendTo($("#overlay_div"));
                                var videoFrame = $('<iframe width="90%" height="90%" src="https://www.youtube.com/embed/se0vuA1uVmk" title="YouTube video player" frameborder="0" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture" allowfullscreen></iframe>').appendTo($("#overlay_div"));
                                closeVideo.on('click', function () {
                                    var isSure = confirm("Have you watched the tutorial video?");
                                    if (isSure) {
                                        $("#overlay_div").empty();
                                        $("#overlay_div").css('opacity', '0.8');
                                        var closeVideoSurvey = $('<button id="close_survey" style="cursor: pointer;">Close Survey</button>').appendTo($("#overlay_div"));
                                        $('<br>').appendTo($("#overlay_div"));
                                        var videoSurvey = "https://forms.office.com/Pages/ResponsePage.aspx?id=-XhTSvQpPk2-iWadA62p2CmPPgx944RCrlRRT-uovIBUREk2WkxIWDVOU1lERUNKRUVJSUZCTVRQVS4u&embed=true";
                                        var surveySource = videoSurvey;
                                        var videoSurveyFrame = $('<iframe width="40%" height= "90%" src=' + surveySource + ' frameborder= "0" marginwidth= "0" marginheight= "0" style= "border: none; max-width:100%; max-height:100vh" allowfullscreen webkitallowfullscreen mozallowfullscreen msallowfullscreen> </iframe>').appendTo($("#overlay_div"));
                                        closeVideoSurvey.on('click', function () {
                                            var isSure = confirm("Have you completed and submitted the survey?");
                                            if (isSure) {
                                                $("#overlay_div").empty();
                                                $("#overlay_div").hide();
                                                _.bind(self.run, self)();
                                            }
                                        });
                                    }
                                });
                            });
                        }
                    });
                } else if (!self.initialisedState) {
                    $("#overlay_div").hide();
                    self.initialisedState = true;
                    MapController.swapMode(self.state.getEditMode(), false);

                    if (!self.state.getChatEnabled()) {
                        $("#accordion_chat").hide();
                    }

                    self.scenarioNumber = self.state.getScenarioNumber();

                    if (self.state.getUserNames().length == 0 && userRole == "planner") {
                        // TODO get their name, also log it in backend
                        var name = null;
                        if (self.prolificID != "undefined") {
                            name = self.prolificID;
                        }
                        while (name == null || name === "") {
                            name = prompt("Please enter your prolific ID", "");
                        }
                        $.post("/mode/scenario/registerUser", {
                            userName: name
                        });
                        self.prolificID = name;
                    } else if (self.state.getUserNames().length > 0 && self.state.getUserNames().length < self.state.getRequiredUsers()) {
                        var name = null;
                        if (self.prolificID != "undefined") {
                            name = self.prolificID;
                        }
                        while (name == null || name === "") {
                            name = prompt("Please enter your prolific ID", "");
                        }
                        $.post("/mode/scenario/registerUser", {
                            userName: name
                        });
                        self.prolificID = name;
                    }

                    if (self.state.attributes.prov_doc == null) {
                        var api = new $.provStoreApi({
                            username: 'atomicorchid',
                            key: '2ce8131697d4edfcb22e701e78d72f512a94d310'
                        });
                        var ps = new PostService();
                        ps.initProv(api, 'uav_silver_commander', self.state.getGameId());
                    }

                    if(self.state.getGameType() === self.state.GAME_TYPE_SCENARIO && !self.state.isInProgress()) {
                        $.unblockUI();
                        var description_panel = document.createElement("div");
                        description_panel.innerHTML = _.template($("#description_panel").html(), {
                            title: self.state.getGameId(),
                            description: self.state.getGameDescription() + "\n Your role is: " + userRole
                        });
                        $.blockWithContent(description_panel);
                        $('#start_scenario').on('click', function () {
                            $.post("/mode/scenario/start", {}, function () {
                                $.unblockUI();
                                self.waiting = true;
                                var wait_panel = document.createElement("div");
                                wait_panel.innerHTML = _.template($("#wait_panel").html(), {
                                    title: "Waiting for Other User",
                                    description: "Get ready, the scenario will begin as soon as the other user is ready."
                                });
                                $.blockWithContent(wait_panel);
                                self.run();
                            });
                        });
                    }

                    if (userRole != "planner" && self.state.getReadyUsers() == 0) {
                        self.waitingForPlanner = true;
                        var wait_panel = document.createElement("div");
                        wait_panel.innerHTML = _.template($("#wait_panel").html(), {
                            title: "Waiting for Other User",
                            description: "The other user is setting up the scenario."
                        });
                        $.blockWithContent(wait_panel);
                        _.bind(self.run, self)();
                    }
                } else if (self.waitingForPlanner) {
                    if (userRole != "planner" && self.state.getReadyUsers() > 0) {
                        self.waitingForPlanner = false;
                        self.initialisedState = false;
                    }
                    _.bind(self.run, self)();
                } else if (self.waiting) {
                    if (self.state.getReadyUsers() == self.state.getRequiredUsers()) {
                        $.unblockUI();
                        self.waiting = false;
                    }
                    _.bind(self.run, self)();
                } else if (!self.state.isInProgress()) {
                    self.views.map.clearAll()
                    $("#overlay_div").empty()
                    var closeSurvey = $('<button id="close_survey" style="cursor: pointer;">Close Survey</button>').appendTo($("#overlay_div"));
                    $('<br>').appendTo($("#overlay_div"));
                    var postScenario1Survey = "https://forms.office.com/Pages/ResponsePage.aspx?id=-XhTSvQpPk2-iWadA62p2CmPPgx944RCrlRRT-uovIBUQjNUNE5SOUxPNE1LM1VSQ0VOTUpBMFQxNy4u&embed=true";
                    var postScenario2Survey = "https://forms.office.com/Pages/ResponsePage.aspx?id=-XhTSvQpPk2-iWadA62p2CmPPgx944RCrlRRT-uovIBUMjBSRkU2UlRNSTdYVUlGUlM1MzNTSExFNi4u&embed=true";
                    if (self.scenarioNumber == 1) {
                        var surveySource = postScenario1Survey;
                    } else {
                        var surveySource = postScenario2Survey;
                    }
                    var surveyFrame = $('<iframe width="40%" height= "90%" src=' + surveySource + ' frameborder= "0" marginwidth= "0" marginheight= "0" style= "border: none; max-width:100%; max-height:100vh" allowfullscreen webkitallowfullscreen mozallowfullscreen msallowfullscreen> </iframe>').appendTo($("#overlay_div"));
                    $("#overlay_div").show();
                    closeSurvey.on('click', function () {
                        var isSure = confirm("Have you completed and submitted the survey?");
                        if (isSure) {
                            self.surveyDone = true;
                            $.post("/mode/scenario/closeSurvey", {}, function () {
                                self.passthrough(self);
                            });
                        }
                    });

                    if (self.scenarioNumber == 0) {
                        self.passthrough(self);
                    }
                }
            })
            .fail(function () {
                _.bind(self.run, self)();
            })
            .always(function () {
                window.clearTimeout(contingency);
                if (self.state.isInProgress()) {
                    var elapsedTime = ((new Date()).getTime() - startTime);
                    if (elapsedTime < waitTime) {
                        window.setTimeout(_.bind(self.run, self), waitTime - elapsedTime);
                    } else {
                        _.bind(self.run, self)();
                    }
                }
            });
        $('#view_mode').buttonset().css({
            "margin-right": "0px"
        }).find("label").width("50%");
    }
};