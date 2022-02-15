_.provide("App.Views.Prediction");

/**
 * View responsible for rendering the schedules.
 */
App.Views.Prediction = Backbone.View.extend({
    initialize: function (options) {
        this.state = options.state;
        this.views = options.views;
        this.canvas = options.canvas;
        this.ctx = options.ctx;

        var self = this;
        this.state.on("change:successChance", function () {
            self.update();
        });

        self.update();
    },
    update: function () {
        this.ctx.font = "Bold 80px Arial";
        this.ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);
        var textToFill = parseFloat(this.state.getSuccessChance().toFixed(2)).toString() + "%";
        this.ctx.fillText(textToFill, 30, 100);


    }

});