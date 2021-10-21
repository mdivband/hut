_.provide("App.Views.Camera");

var targetMarkers = [];

App.Views.CameraGMap = Backbone.View.extend({ 
	initialize: function(options) {

		this.mapOptions = {
                tilt: 45,
                heading: 90,
                draggable: false,
                scrollwheel: false, 
                disableDoubleClickZoom: true,
				zoom: 19,
				scaleControl: false,
				mapTypeControl: false,
				disableDefaultUI: true,
				overviewMapControl: false,
				mapTypeId: google.maps.MapTypeId.SATELLITE
		};
		$.extend(this.mapOptions, options.mapOptions || {});

		this.state = options.state;
		this.views = options.views;

		this.render();
		this.camera_mjpeg();
		
		this.bind("update", this.update);
		this.bind("refresh", this.refresh);
	},
	render: function() {
		var self = this;
		
		this.mapDiv = document.createElement("div");
		this.mapDiv.style.position = 'relative';
		this.mapDiv.style.height = '100%';
		this.mapDiv.style.width = '100%';
		// here
		this.$mapDiv = $(this.mapDiv);
		this.$el.append(this.mapDiv);
		
		this.$mapDiv.gmap(this.mapOptions);
		this.map = this.$mapDiv.gmap("get", "map");
		
		google.maps.event.addListenerOnce(this.map, 'idle', function(){
		});

		this.$el.append($("#cross"));
		this.$el.append($("#screenshot")); 
		$("#screenshot").click(function() { // bronze screen to take screenshot
			var center = self.map.getCenter();
			var zoom = self.map.getZoom(); 

			var width = $("#camera_canvas").width();
			var height = $("#camera_canvas").height();
			
			window.open("https://maps.googleapis.com/maps/api/staticmap?" +
					"sensor=false&maptype=satellite&format=jpg&scale=1&" + 
					"center=" + center.lat() + "," + center.lng() + "&" + 
					"zoom=" + zoom + "&size=" + width + "x" + height);
		});
		
		$("#camerainfo").draggable();
		this.$el.append($("#camerainfo"));
		
		google.maps.event.addListener(this.map, 'bounds_changed', function() {
			var bounds = self.map.getBounds();
			self.views.submap.trigger("camera:bounds", 
					bounds.getSouthWest(), 
					bounds.getNorthEast());
			
			self.views.map.trigger("camera:bounds", 
					bounds.getSouthWest(), 
					bounds.getNorthEast());
			
			var center = self.map.getCenter();
			var zoom = self.map.getZoom();
			$("#camerainfo").html("<font color='white'>" + 
					" Lat: " + center.lat().toFixed(6) + 
					" Lng: " + center.lng().toFixed(6) + 
					" Zoom: " + zoom +
					"</font>");
		});
		
		// google.maps.event.addListener(this.map, 'zoom_changed', function() {
		// 	var zoom = self.map.getZoom();
		// 	if (self.views.clickedAgent) {
		// 		$.post("/altitude", {id: self.views.clickedAgent, altitude: 21-zoom});
		// 	}
		// });

        google.maps.event.addListener(this.map, 'dblclick', function() {
            window.open("/camera.html?id=" + self.views.clickedAgent);
        });
		
		this.$mapDiv.gmap("refresh");

		window.setTimeout(function(){ self.add_target(self.map); },800);

	},
	update: function() {
		var agent = this.state.agents.get(this.views.clickedAgent);
		if (agent) {
			if (agent.isSimulated()) {
				this.$mapDiv.show();
				
				if (this.$imageDiv) {
					this.$imageDiv.hide();
				}
			} else {
				var url = this.camera_url(null);
				if (this.imageDiv.alt !== url) {
					this.imageDiv.src = url;
					this.imageDiv.alt = url;
				}
				
				this.$mapDiv.hide();
				this.$imageDiv.show();
			}
			
			this.map.setCenter(agent.getPosition());
			this.map.setZoom(19);
			
			this.$mapDiv.gmap("refresh");
		}
		
		var game_id = this.state.getGameId();
		if (game_id && game_id != this.game_id) {
			this.game_id = game_id;
			//this.camera_tasks();
		}
		this.update_targets(this.map);
		//console.log("update targets");

	},
	add_target: function(map) {
		_.each(this.state.attributes.targets, function(target){
			var marker = new google.maps.Marker({
				id: target.id,
				position: new google.maps.LatLng(target.coordinate.latitude, target.coordinate.longitude),
				title:target.id,
				icon: "icons/redquestion2.png"
			});
			targetMarkers.push(marker);
			marker.setMap(map);
		});
	},
	update_targets: function(map) {
		var self = this;
		for (var i = 0; i < targetMarkers.length; i++) {
			if (targetMarkers[i] != null) {
				var available = false;
				
				this.state.attributes.targets.forEach(function(target) {
					if (targetMarkers[i].id == target.id) {
						available = true;
						targetMarkers[i].setIcon(self.getIcon(target.targetType));
					}
				});

				if (!available) {
					targetMarkers[i].setMap(null);
					delete targetMarkers[i];
				}
			}
		}
	},
	getIcon: function(num){
			switch (num) {
                        case -1:
                            return "icons/redquestion5.png"; 
                        case 0:
                            return "icons/water_small.png"; 
                        case 1:
                            return "icons/infra_small.png"; 
                        case 2:
                            return "icons/medical_small.png"; 
                        case 3:
                            return "icons/crime_small.png"; 
                        case 4:
                            return "icons/invalid_small.png";   
                    }		
	},
	refresh: function() { // change map or satellite 
		if ($("#camera_canvas").is(":visible")) {
			$("#camerainfo").show();
			$("#screenshot").show();
		} else {
			$("#camerainfo").hide();
			$("#screenshot").hide();
		}
		this.$mapDiv.gmap("refresh");
	},
	camera_mjpeg: function() {
		this.imageDiv = document.createElement("img");
		this.imageDiv.style.position = 'relative';
		this.imageDiv.style.height = "100%";
		this.imageDiv.style.width = "100%";
		this.$imageDiv = $(this.imageDiv);
		
		var self = this;
		this.$imageDiv.click(function() {
			self.update();
		});

		this.$imageDiv.hide();
		this.$el.append(this.imageDiv);
	},
	camera_url: function(host) {
		host = host || "127.0.0.1";
		return "http://" + host + ":8080/stream?topic=/ardrone/image_raw"
	},
    	camera_tasks: function() {
	        var self = this;
	        var game_id = this.game_id;
	        var target_size = this.state.get("target_size");
	        $.getJSON("/jsonp?url=holt.mrl.nott.ac.uk:49992/game/" + game_id + "/status.json?task_only=true", function(data) {
	            if (!data.tasks || data.tasks.length == 0) {
	                console.log("status.json: task is empty.");
	                console.log("checkout: http://holt.mrl.nott.ac.uk:49992/game/" + game_id + "/status.json?task_only=true");
	            }

	            //console.log(data);
	            var size = target_size; 
	            _.each(data.tasks, function(val) {
	                var icon = new google.maps.MarkerImage(
	                    "http://holt.mrl.nott.ac.uk:49992/img/task_icon" + (val.type+1) + ".png",
	                    new google.maps.Size(size,size), null, null, new google.maps.Size(size,size));
	                self.$mapDiv.gmap("addMarker", {
	                    marker: MarkerWithLabel,
	                    labelStyle: {opacity: 1.0},
	                    labelAnchor: new google.maps.Point(12.5, -5),
	                    position: _.position(val.latitude, val.longitude),
	                    icon: icon,
	                    id: val.id,
	                    size: size
	                }).click(function(e) {
	                    this.labelClass = "labels";
	                    this.labelContent = val.id;
	                    google.maps.event.trigger(this, "labelclass_changed");
	                    google.maps.event.trigger(this, "labelcontent_changed");

	                    $.post("http://holt.mrl.nott.ac.uk:49992/game/" + game_id + "/find_target", '{"target_id":' + val.id + '}', function(str) {
	                            if (str) {
	                                var data = $.parseJSON(str);
	                                if (data.state === "ok") {
	                                    $.popText(val.id + ": target submitted", 2, {type: "information"});
	                                } else if (data.state === "error") {
	                                    $.popText(val.id + ": " + data.msg, 2, {type: "error"});
	                                }
	                            }
	                    }).fail(function() {
	                        $.popText(val.id + ": server failed", 2, {type: "error"});
	                    });
	                });
	            });
	        }).fail(function() {
	            console.log("load status.json failed.");
	        });

	        google.maps.event.addListener(this.map, 'zoom_changed', function() {
	            var zoom = self.map.getZoom();
	            if (zoom != 19) {
	                self.map.setZoom(19);
	            }

	            var defZoom = self.mapOptions.zoom;
	            var markers = self.$mapDiv.gmap("get", "markers");
	            _.each(markers, function(marker) {
	                var size = Math.round(marker.size*Math.pow(2,zoom-defZoom));
	                marker.setIcon(new google.maps.MarkerImage(marker.getIcon().url, null, null, null,
	                        new google.maps.Size(size, size)));
	            });
	        });
    	}
});

App.Views.Camera = App.Views.CameraGMap;
