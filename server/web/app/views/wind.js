_.provide("App.Views.Wind");

/**
 * View responsible for rendering the schedules.
 */
App.Views.Wind = Backbone.View.extend({
    initialize: function (options) {
        this.state = options.state;
        this.views = options.views;
        this.canvas = options.canvas;
        this.ctx = options.ctx;
        this.rowHeight = 30;
        this.stepWidth = 0.1;

        var self = this;
        this.state.on("change", function () {
            self.updateGraph();
        });
        //Hide on edit mode change so canvas is correctly redrawn before being rendered.
        this.state.on("change:editMode", function () {
            $(self.canvas).hide();
        });
    },
    updateGraph: function () {
        var labels = ["Wind Speed"];
        var timeDataMain = [this.state.getWindSpeed()];
        var self = this;
        var height = 50;

        this.canvas.style.width = '100%';
        this.canvas.width = this.canvas.offsetWidth;
        this.canvas.height = height;
        this.canvas.offsetHeight = height;

        var datasets;
        datasets = [this.generateMainDataset(timeDataMain)];

        var chartData = {
            labels: labels,
            datasets: datasets
        };

        var options = {
            scaleOverride: true,
            scaleStartValue: 0,
            scaleSteps: 10,
            scaleStepWidth: this.stepWidth
        };

        new Chart(this.ctx).HorizontalBar(chartData, options, this.ctx);

        $(self.canvas).show();
        $("#wind_dial_m").css({
            'transform': 'rotate(' + this.state.getWindHeading() + 'deg)'
        });
        $("#wind_dial_e").css({
            'transform': 'rotate(' + this.state.getWindHeading() + 'deg)'
        });
    },
    generateMainDataset: function (data) {
        return {
            fillColor: "rgba(79,191,59,0.5)",
            data: data
        }
    }
});