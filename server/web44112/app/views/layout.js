_.provide("App.Views.Layout");

// just for layout

App.Views.Layout = Backbone.View.extend({
	initialize: function(options) {
		this.state = options.state;
		this.views = options.views;
		
		var self = this;
		var refresh = function() {
			self.views.map.trigger("refresh");
			self.views.camera.trigger("refresh");
		};
		this.layoutOptions = { 
				east__size: 415,
				//east__initClosed: true,
				east__initHidden: false,
				onopen: refresh,
				onclose: refresh,
				onresize: refresh,
				margin:0
		};
		$.extend(this.layoutOptions, options.layoutOptions || {});
		
		this.render();
		
		this.bind("open:east", function() {
			self.$el.layout().open("east");
		});
	},
	render: function() {
		this.$el.layout(this.layoutOptions);	
	}
});