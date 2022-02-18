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
        this.ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);
        var chance = this.state.getSuccessChance();

        if (chance > 90) {
            this.ctx.fillStyle = 'rgb(20,255,0)';
        } else if (chance > 80) {
            this.ctx.fillStyle = 'rgb(51,148,51)';
        } else if (chance > 70) {
            this.ctx.fillStyle = 'rgb(165,210,115)';
        } else if (chance > 60) {
            this.ctx.fillStyle = 'rgb(218,178,118)';
        } else if (chance > 50) {
            this.ctx.fillStyle = 'rgb(169,116,49)';
        } else if (chance > 40) {
            this.ctx.fillStyle = 'rgb(206,104,67)';
        } else if (chance > 30) {
            this.ctx.fillStyle = 'rgb(196,59,22)';
        } else if (chance > 20) {
            this.ctx.fillStyle = 'rgb(152,0,0)';
        } else if (chance > 10) {
            this.ctx.fillStyle = 'rgba(200,0,0)';
        } else {
            this.ctx.fillStyle = 'rgba(255,0,0)';
        }

        this.ctx.font = "Bold 80px Arial";
        var textToFill = parseFloat(chance.toFixed(2)).toString() + "%";
        this.ctx.fillText(textToFill, 30, 100);

    }

});