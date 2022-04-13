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
                self.updateUnderAndOverBounds();
            });
        } else if (this.type === "mission") {
            this.state.on("change:missionSuccessChance", function () {
                self.update();
            });
        }
        self.update();
    },
    update: function () {
        var chance;
        var pred;
        var background;
        if (this.type === "allocation") {
            chance = this.state.getSuccessChance();
            pred = document.getElementById("prediction_text");
            pred.innerHTML = parseFloat(chance.toFixed(1)).toString() + "%";
            background = document.getElementById("prediction_circle");
            background.style.background = this.getColor(chance.toFixed(0));
        } else if (this.type === "mission") {
            chance = this.state.getMissionSuccessChance();
            pred = document.getElementById("mission_prediction_text");
            background = document.getElementById("mission_prediction_circle");
            if (chance === -1) {
                pred.innerHTML = "?%";
                background.style.background = "rgb(255,255,255)";
            } else {
                pred.innerHTML = parseFloat(chance.toFixed(1)).toString() + "%";
                background.style.background = this.getColor(chance.toFixed(0));
            }
        }
    },
    updateUnderAndOverBounds : function () {
        var addButton = document.getElementById("add_agent");
        var chance = this.state.getMissionSuccessOverChance();
        if (chance === -1) {
            addButton.innerText = "Add Agent (?%)"
        } else {
            addButton.innerText = "Add Agent (" + parseFloat(chance.toFixed(1)).toString() + "%)";
        }

        var removeButton = document.getElementById("remove_agent");
        chance = this.state.getMissionSuccessUnderChance()
        if (chance === -1) {
            removeButton.innerText = "Remove Agent (?%)"
        } else {
            removeButton.innerText = "Remove Agent (" + parseFloat(chance.toFixed(1)).toString() + "%)";
        }
    },
    getColor: function (p) {
        var red = p < 50 ? 255 : Math.round(256 - (p - 50) * 5.12);
        var green = p > 50 ? 255 : Math.round((p) * 5.12);
        return "rgb(" + red + "," + green + ",0)";
    }


});