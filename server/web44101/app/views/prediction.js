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
            pred.innerHTML = parseFloat(chance.toFixed(1)).toString() + "%";
            background = document.getElementById("mission_prediction_circle");
            background.style.background = this.getColor(chance.toFixed(0));
        }
    },
    updateUnderAndOverBounds : function () {
        var addButton = document.getElementById("add_agent");
        addButton.innerText = "Add Agent (" + parseFloat(this.state.getMissionSuccessOverChance().toFixed(1)).toString() + "%)";

        var removeButton = document.getElementById("remove_agent");
        removeButton.innerText = "Remove Agent (" + parseFloat(this.state.getMissionSuccessUnderChance().toFixed(1)).toString() + "%)";
    },
    getColor: function (p) {
        var red = p < 50 ? 255 : Math.round(256 - (p - 50) * 5.12);
        var green = p > 50 ? 255 : Math.round((p) * 5.12);
        return "rgb(" + red + "," + green + ",0)";
    }


});