_.provide("App.Views.Prediction");

/**
 * View responsible for rendering the schedules.
 */
App.Views.Prediction = Backbone.View.extend({
    initialize: function (options) {
        this.state = options.state;
        this.views = options.views;
        this.canvas = options.canvas;
        this.type = options.type;
        this.enabled = false;
        //this.ctx = options.ctx;

        var self = this;
        if (this.type === "allocation") {
            this.state.on("change:successChance", function () {
                self.update();
            });
        } else if (this.type === "mission") {
            this.state.on("change:missionSuccessChance", function () {
                self.update();
            });
            /*
            this.state.on("change:missionSuccessOverChance", function () {
                self.updateOverBound();
            });
            this.state.on("change:missionSuccessUnderChance", function () {
                self.updateUnderBound();
            });

             */
        } else if (this.type === "bounded") {
            this.state.on("change:estimatedCompletionTime", function () {
                self.update();
            });
            this.state.on("change:estimatedCompletionOverTime", function () {
                if (self.state.getEstimatedCompletionTime() !== -1) {
                    self.updateOverBound();
                }
            });
            this.state.on("change:estimatedCompletionUnderTime", function () {
                if (self.state.getEstimatedCompletionTime() !== -1) {
                    self.updateUnderBound();
                }
            });
            this.state.on("change:scheduledRemovals", function () {
                self.updateRemovalsButton()
            });
            this.state.on("change:tempCanAddRemAgents", function () {
                //alert(self.state.getTempCanAddRemAgents())
                self.tempUpdateAddRemButtons()
            });
        }
        self.update();
    },
    tempUpdateAddRemButtons: function () {
        let self = this;

        // 20230904_0948h - Ayo Abioye (a.o.abioye@soton.ac.uk) added add/remove button disabling to UI when limit is reached
        if (self.state.getTempCanAddRemAgents() === -1){
            console.log("Can't remove one. Turning it off")
            $("#remove_agent").addClass("add_remove_agent_button_disabled");
        } else {
            $("#remove_agent").removeClass("add_remove_agent_button_disabled");
        }

        if (self.state.getTempCanAddRemAgents() === 1){
            console.log("Can't add one. Turning it off")
            $("#add_agent").addClass("add_remove_agent_button_disabled");
        } else {
            $("#add_agent").removeClass("add_remove_agent_button_disabled");
        }

        // if (self.state.getTempCanAddRemAgents() === -1) {
        //     // Not allowed to remove one
        //     console.log("Can't remove one. Turning it off")
        //     var remButton = document.getElementById("remove_agent");
        //     $("#remove_agent").removeClass("remove_agent").addClass("remove_agent_greyed")
        //     $("#remove_agent").prop('disabled', true);
        //
        //     var addButton = document.getElementById("add_agent");
        //     $("#add_agent").removeClass("add_agent_greyed").addClass("add_agent")
        //     $("#add_agent").prop('disabled', false);
        // } else if (self.state.getTempCanAddRemAgents() === 1) {
        //     // Not allowed to add one
        //     console.log("Can't add one. Turning it off")
        //     var addButton = document.getElementById("add_agent");
        //     $("#add_agent").removeClass("add_agent").addClass("add_agent_greyed")
        //     $("#add_agent").prop('disabled', true);
        //
        //     var remButton = document.getElementById("remove_agent");
        //     $("#remove_agent").removeClass("remove_agent_greyed").addClass("remove_agent")
        //     $("#remove_agent").prop('disabled', false);
        //
        // }
    },
    update: function () {
        if (this.enabled) {
            var chance;
            var pred;
            var background;
            var self = this;
            if (this.type === "allocation") {
                chance = this.state.getSuccessChance();
                pred = document.getElementById("prediction_text");
                pred.innerHTML = parseFloat(chance.toFixed(1)).toString() + "%";
                background = document.getElementById("prediction_circle");
                background.style.background = this.getAllocColor(chance.toFixed(0));
            } else if (this.type === "mission") {
                chance = this.state.getMissionSuccessChance();
                pred = document.getElementById("mission_prediction_text");
                background = document.getElementById("mission_prediction_circle");
                if (chance === -1) {
                    pred.innerHTML = "?%";
                    background.style.background = "rgb(255,255,255)";
                } else {
                    pred.innerHTML = parseFloat(chance.toFixed(1)).toString() + "%";
                    background.style.background = this.getMissionColor(chance.toFixed(0));
                }
            } else if (this.type === "bounded") {
                pred = document.getElementById("bounded_prediction_text");
                background = document.getElementById("bounded_prediction_circle");
                if (this.state.getEstimatedCompletionTime() === -1) {
                    // var current = pred.innerHTML;
                    // pred.innerHTML = "?" + current;
                    // 20230822_1501h - Ayo Abioye (a.o.abioye@soton.ac.uk) added loading spinner to completion time prediction
                    background.classList.add("loader");
                    pred.classList.remove("indicator_text");
                    pred.classList.add("indicator_text_v2");
                    //background.style.background = "rgb(255,255,255)";
                } else {
                    console.log(this.state.getEstimatedCompletionTime())
                    var eta;
                    if (this.state.getEstimatedCompletionTime() >= 300000) {
                        eta = "10:00+"
                    } else {
                        eta = self.convertToCompletionTime(this.state.getEstimatedCompletionTime())
                    }
                    pred.innerHTML = eta;//parseFloat(chance.toFixed(1)).toString() + "%";
                    var scaledTime = ((this.state.getEstimatedCompletionTime() / 500) / 360)
                    console.log(eta + " -> " + scaledTime)

                    background.style.background = this.getMissionColor(100 * scaledTime);
                    background.classList.remove("loader");
                    pred.classList.remove("indicator_text_v2");
                    pred.classList.add("indicator_text");
                }
                // IMPORTANT: Because these are deltas wrt the current prediction, they must be recomputed when the base
                //  prediction comes through
                self.updateUnderBound()
                self.updateOverBound()
            }
        }
    },
    updateOverBound : function () {
        let scoreInfo = this.state.getScoreInfo();
        var addButton = document.getElementById("add_agent");
        if (this.enabled) {
            var timeDelta = (parseFloat(this.state.getEstimatedCompletionOverTime()) / 100) - (parseFloat(this.state.getEstimatedCompletionTime()) / 100);
            timeDelta = timeDelta / 5
            if (this.state.getEstimatedCompletionOverTime() === -1) {
                addButton.innerText = "Add Agent (?)"
            } else if (timeDelta === 0) {
                addButton.innerText = "Add Agent (~0s, £" + parseFloat(scoreInfo["upkeep_add_agent"]).toFixed(2) + ")"
            } else {
                if (timeDelta > 0) {
                    addButton.innerText = "Add Agent (~0s, £" + parseFloat(scoreInfo["upkeep_add_agent"]).toFixed(2) + ")"
                } else {
                    // No +, as - is included in the string
                    addButton.innerText = "Add Agent (" + timeDelta + "s, £" + parseFloat(scoreInfo["upkeep_add_agent"]).toFixed(2) + ")";
                }
            }
        } else {
            addButton.innerText = "Add Agent"
        }
    },
    updateUnderBound : function () {
        let scoreInfo = this.state.getScoreInfo();
        var removeButton = document.getElementById("remove_agent");
        if (this.enabled) {
            var timeDelta = (parseFloat(this.state.getEstimatedCompletionUnderTime()) / 100) - (parseFloat(this.state.getEstimatedCompletionTime()) / 100);
            timeDelta = timeDelta / 5
            if (this.state.getEstimatedCompletionUnderTime() === -1) {
                removeButton.innerText = "Remove Agent (?)"
            } else if (timeDelta === 0) {
                removeButton.innerText = "Remove Agent (~0s, £" + parseFloat(scoreInfo["upkeep_rm_agent"]).toFixed(2) + ")"
            } else {
                if (timeDelta < 0) {
                    removeButton.innerText = "Remove Agent (~0s, £" + parseFloat(scoreInfo["upkeep_rm_agent"]).toFixed(2) + ")"
                } else {
                    removeButton.innerText = "Remove Agent (+" + timeDelta + "s, £" + parseFloat(scoreInfo["upkeep_rm_agent"]).toFixed(2) + ")";
                }
            }
        } else {
            removeButton.innerText = "Remove Agent"
        }
    },
    updateRemovalsButton: function () {
        //alert("===== FOR AYO ==== ")
        //alert("Here is the scheduled removals: " + this.state.getScheduledRemovals())
    },
    convertToCompletionTime: function (time) {
        console.log("unaltered: " + time)
        time = time / 100;
        console.log("reduced to sim seconds " + time)
        time = time / 5
        console.log("reduced to real seconds " + time)
        var predTime = _.convertToTime(time)
        console.log("predTime " + predTime)

        return predTime
    },
    getAllocColor: function (p) {
        var red = p < 50 ? 255 : Math.round(256 - (p - 50) * 5.12);
        var green = p > 50 ? 255 : Math.round((p) * 5.12);
        return "rgb(" + red + "," + green + ",0)";
    },
    getMissionColor: function (p) {
        // p < boundary ? 256 : round( 256 - (p - boundary) * (256/boundary) )
        var red = p > 80 ? 255 : Math.round((p) * 3.2);
        var green = p < 80 ? 255 : Math.round(256 - (p - 80) * 5.12);
        return "rgb(" + red + "," + green + ",0)";
    },
    activate: function (){
        this.enabled = true
        this.update()
    },
    deactivate: function () {
        this.enabled = false
    }

});