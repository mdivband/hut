_.provide("App.Views.Detection");

App.Views.Detection = Backbone.View.extend({
    initialize: function(options) {
            this.state = options.state;
            this.views = options.views;

            this.render();

    },
    render: function() {
        var self = this;

        this.detDiv = document.createElement("div");
        this.detDiv.style.position = 'relative';
        this.detDiv.style.height = '100%';
        this.detDiv.style.width = '100%';
        // here
        this.$detDiv = $(this.detDiv);
        this.$el.append(this.detDiv);

    },
    update: function() {
        var agent = this.state.agents.get(this.views.clickedAgent);

    },

    refresh: function() { // change map or satellite

    },
});
