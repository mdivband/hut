_.provide("App.Views.Graph");

/**
 * View responsible for rendering the schedules.
 */
App.Views.Graph = Backbone.View.extend({
    initialize: function (options) {
        this.state = options.state;
        this.views = options.views;
        this.canvas = options.canvas;
        this.forEditMode = options.forEditMode;
        this.ctx = options.ctx;
        this.rowHeight = 30;
        this.stepWidth = 10;

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
        var labels = [];
        var timeDataMain = [];
        var timeDataTemp = [];
        var self = this;
        var maxTime = 0;

        if (this.state.agents.length === 0)
            labels.push("");
        else {
            var mainAllocation = this.state.getAllocation();
            var tempAllocation = this.state.getTempAllocation();

            this.state.agents.each(function (agent) {
                var mainTask, tempTask;
                if (mainAllocation[agent.getId()])
                    mainTask = self.state.tasks.get(mainAllocation[agent.getId()]);
                if (tempAllocation[agent.getId()])
                    tempTask = self.state.tasks.get(tempAllocation[agent.getId()]);

                var mainTime = 0;
                var tempTime = 0;

                if(!agent.isWorking()) {
                    var dist, endPoint;
                    if (mainTask) {
                        endPoint = agent.getRoute()[agent.getRoute().length - 1];
                        dist = google.maps.geometry.spherical.computeDistanceBetween(agent.getPosition(), _.position(endPoint.latitude, endPoint.longitude));
                        mainTime = dist / agent.getSpeed();
                        if (mainTime > maxTime)
                            maxTime = mainTime;
                    }
                    if (tempTask) {
                        endPoint = agent.getTempRoute()[agent.getTempRoute().length - 1];
                        dist = google.maps.geometry.spherical.computeDistanceBetween(agent.getPosition(), _.position(endPoint.latitude, endPoint.longitude));
                        tempTime = dist / agent.getSpeed();
                        if (tempTime > maxTime)
                            maxTime = tempTime;
                    }
                }


                labels.push(agent.getId());
                timeDataMain.push(mainTime);
                timeDataTemp.push(tempTime);
            });
        }

        var numRows = this.state.agents.length;
        var height = this.rowHeight * numRows;

        if (height < 150)
            height = 150;

        this.canvas.style.width = '100%';
        this.canvas.width = this.canvas.offsetWidth;
        this.canvas.height = height;
        this.canvas.offsetHeight = height;

        var datasets;
        if (this.forEditMode)
            datasets = [this.generateMainDataset(timeDataMain), this.generateTempDataset(timeDataTemp)];
        else
            datasets = [this.generateMainDataset(timeDataMain)];

        var chartData = {
            labels: labels,
            datasets: datasets
        };

        //Only update step width in edit mode to keep step consistent when monitoring.
        if(this.state.isEdit()) {
            this.stepWidth = 10;
            if (maxTime !== 0)
                this.stepWidth = 10 * Math.ceil(maxTime / 80);
        }

        var options = {
            scaleOverride: true,
            scaleStartValue: 0,
            scaleSteps: 8,
            scaleStepWidth: this.stepWidth
        };

        new Chart(this.ctx).HorizontalBar(chartData, options, this.ctx);

        $(self.canvas).show();
    },
    generateMainDataset: function (data) {
        return {
            fillColor: "rgba(79,191,59,0.5)",
            data: data
        }
    },
    generateTempDataset: function (data) {
        return {
            fillColor: "rgba(221,153,16,0.5)",
            data: data
        }
    }
});