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
            this.state.on("change:missionBoundedSuccessChance", function () {
                self.update();
            });
            this.state.on("change:missionBoundedSuccessOverChance", function () {
                self.updateOverBound();
            });
            this.state.on("change:missionBoundedSuccessUnderChance", function () {
                self.updateUnderBound();
            });
            // TODO over and under here
        }
        self.update();
    },
    update: function () {
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
            if (this.state.getMissionBoundedSuccessChance() === -1) {
                pred.innerHTML = "?%";
                background.style.background = "rgb(255,255,255)";
            }  else {
                console.log("unaltered: " + this.state.getMissionBoundedSuccessChance())
                chance = parseFloat(this.state.getMissionBoundedSuccessChance().toFixed(0)) / 1000;
                console.log("reduced to sim seconds " + chance)
                chance = chance / 5
                console.log("reduced to real seconds " + chance)
                try {
                    console.log("pred " + chance)
                    var predTime = _.convertToTime(chance)
                    console.log("predTime " + predTime)
                } catch (e) {
                    alert(e)
                }

                pred.innerHTML = predTime;//parseFloat(chance.toFixed(1)).toString() + "%";
                var scaledTime = 1 - (chance / 360)
                console.log(predTime + " -> " + scaledTime)
                background.style.background = this.getMissionColor(0.8+scaledTime);
            }
        }
    },
    updateOverBound : function () {
        var addButton = document.getElementById("add_agent");
        //var chance = this.state.getMissionSuccessOverChance();
        var chance = this.state.getMissionBoundedSuccessOverChance();
        if (chance === -1) {
            addButton.innerText = "Add Agent (?%)"
        } else {
            addButton.innerText = "Add Agent (" + parseFloat(chance.toFixed(1)).toString() + "%)";
        }
    },
    updateUnderBound : function () {
        var removeButton = document.getElementById("remove_agent");
        //var chance = this.state.getMissionSuccessUnderChance()
        var chance = this.state.getMissionBoundedSuccessUnderChance()
        if (chance === -1) {
            removeButton.innerText = "Remove Agent (?%)"
        } else {
            removeButton.innerText = "Remove Agent (" + parseFloat(chance.toFixed(1)).toString() + "%)";
        }
    },
    getAllocColor: function (p) {
        var red = p < 50 ? 255 : Math.round(256 - (p - 50) * 5.12);
        var green = p > 50 ? 255 : Math.round((p) * 5.12);
        return "rgb(" + red + "," + green + ",0)";
    },
    getMissionColor: function (p) {
        // p < boundary ? 256 : round( 256 - (p - boundary) * (256/boundary) )
        var red = p < 80 ? 255 : Math.round(256 - (p - 80) * 3.2);
        var green = p > 80 ? 255 : Math.round((p) * 3.2);
        return "rgb(" + red + "," + green + ",0)";
    }


});