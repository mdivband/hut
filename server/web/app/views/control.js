 _.provide("App.Views.Control");

App.Views.Control = Backbone.View.extend({
	initialize: function(options) {
		this.state = options.state;
		this.views = options.views;

		this.render();
		this.binder();

	},
	binder: function() {
		var self = this;
		
		this.bind("update:agent", this.update_agent);
		this.bind("update:tasks", this.update_tasks);
	},
	update_agent: function(model) {
		if (model) {
			this.agent = model;
			var id = model.getId();

			if (!model.isSimulated()) {
				this.setup_ardrone(model);
			}
			this.set_mode(model.getMode());
			
			$("#canvas_small_info_agentid").text(id);
			
			$("#canvas_small_info_speed_val").text(this.agent.getSpeed());	

			if(model.getMode() == "teleop"){
				$("#canvas_small_info_mode_val").text("TeleOpe").css('color', 'MediumTurquoise');				
			}else{
				$("#canvas_small_info_mode_val").text("AUTO").css('color', 'red');
			}

			if (!model.isSimulated()) {
				if (this.ardrone) {
					$("#info_battery").text(this.ardrone.getBattery()+"%");
				
				}
			} else {
				var batterylevel= model.getBattery();
				var img_url = "";
				if(batterylevel > 75){
					img_url = "icons/battery_full.png";
				}else if(batterylevel > 50){
					img_url = "icons/battery_mid.png";
				}else if(batterylevel > 25){
					img_url = "icons/battery_low.png";
				}else{
					img_url = "icons/battery_empty.png";
				}

				$("#canvas_small_info_battery > img ").attr({'src':img_url});	
				$("#canvas_small_info_battery_text").text(batterylevel +"%");
			}
			
			$("#canvas_small_info_tasks").empty();
			// var current_scheduleagents = this.state.schedules.get("current").getScheduleAgents();
			// var allocation_text = null;
			// current_scheduleagents.forEach(function(row){
			// 	if(row.agent_id == id ){
			// 		if(allocation_text != null){
			// 			allocation_text = allocation_text+"<div>&darr;</div><div>"+row.task_id+"</div>";
			// 		}else{
			// 			allocation_text = "<div style='padding-top: 10px; '>"+row.task_id+"</div>";
			// 		}
			// 	}
			// });

			if(allocation_text == null) {
				allocation_text = "<p>No Allocation </p>";
			}
			$("#canvas_small_info_tasks").append(allocation_text);

			if (!$("#speed_slider").data("ishover")) {
				$("#speed_value").text(model.getSpeed().toFixed(1));
				$("#speed_slider").slider("value", model.getSpeed());
			}
			
			if (!$("#altitude_slider").data("ishover")) {
				$("#altitude_value").text(model.getAltitude());
				$("#altitude_slider").slider("value", model.getAltitude());
			}
			
			if (!$("#allocation_options").data("ishover")) {
				var tasks = this.state.getAllocation();
				if (tasks[id]) {
					$("#allocation_options").val(tasks[id]);
				} else {
					$("#allocation_options").val("auto");
				}
			}
		}
	},
	setup_ardrone: function(model) {
		this.ardrones = this.ardrones || {};

		var id = model.getId();
		if (!this.ardrones[id]) {
			this.ardrones[id] = new App.Devices.ARDrone({
				host: null,
				localization: {
					postPose: function(data) {
						if (data) {
							var threshold = 1;
							if (Math.abs(data.x) > threshold ||
								Math.abs(data.y) > threshold ||
								Math.abs(data.z) > threshold) {
								
								$.post("/ardrone_pos", {
									id: model.getId(),
									x: data.x,
									y: data.y,
									z: data.z,
									a: data.a
								});
								data.x = 0; data.y = 0; data.z = 0;
							}
						}
					},
					postAngle: function(data) {
						var angle = data * 180 / Math.PI;
						var center = model.getPosition();
						$("#camerainfo").html("<font color='white'>" + 
								"Lat: " + center.lat().toFixed(6) + 
								" Lng: " + center.lng().toFixed(6) + 
								" N: " + angle.toFixed(2) + "</font>");
					}
				}
			});
		}
		if (this.ardrone != this.ardrones[id]) {
			this.ardrone = this.ardrones[id];
		}
	},
	update_tasks: function() {
		if (this.state.tasks) {
			var self = this;
			
			this.tasks = this.tasks || {};
			$("#allocation_options").empty();
			
			var auto = document.createElement("option");
			auto.value = "auto";
			auto.innerHTML = "auto";
			self.tasks["auto"] = auto;
			$("#allocation_options").append(auto);
			
			this.state.tasks.forEach(function(task) {
				var id = task.get("id");
				
				var option = document.createElement("option");
				option.value = id;
				option.innerHTML = id;
				
				self.tasks[id] = option;
				$("#allocation_options").append(option);
			});
		}
	},
	render: function() {
		var self = this;
		
		$("#switch_view").button({
			icons: {
				primary: "ui-icon-transferthick-e-w"
			} 
		}).css({ width: "140px" });
		
		$("#change_mode").button({
			icons: {
				primary: "ui-icon-person"
			}
		}).css({ width: "140px" });
		
		$("#speed_slider").slider({
			min: 0,
			max: 10,
			step: 0.1,
			value: 1.0,
			range: "min",
			animate: true,
			orientation: "horizontal",
			slide: function(event, ui) {
				$("#speed_value").text(ui.value.toFixed(1));
			},
			stop: function(event, ui) {
				$.post("/agents/" + self.agent.getId(), {
					speed: ui.value
				});
			}
		});
		$("#speed_slider").mouseover(function() {
			$("#speed_slider").data("ishover", true);
		}).mouseout(function() {
			$("#speed_slider").data("ishover", false);
		});
		

		/// for controller-manual
		$("#speed_slider_manual").slider({
			min: 0,
			max: 10,
			step: 0.1,
			value: 1.0,
			range: "min",
			animate: true,
			orientation: "vertical",
			slide: function(event, ui) {
				$("#speed_value").text(ui.value.toFixed(1));
			},
			stop: function(event, ui) {
                $.post("/agents/" + self.agent.getId(), {
                    speed: ui.value
                });
			}
		});
		$("#speed_slider_manual").mouseover(function() {
			$("#speed_slider").data("ishover", true);
		}).mouseout(function() {
			$("#speed_slider_manual").data("ishover", false);
		});

		$("#altitude_slider").slider({
			min: 0,
			max: 6,
			step: 1,
			value: 3,
			range: "min",
			animate: true,
			orientation: "horizontal",
			slide: function(event, ui) {
				$("#altitude_value").text(ui.value);
			},
			stop: function(event, ui) {
				$.post("/agents/" + self.agent.getId(), {
                    altitude: ui.value
				});
			}
		});
		$("#altitude_slider").mouseover(function() {
			$("#altitude_slider").data("ishover", true);
		}).mouseout(function() {
			$("#altitude_slider").data("ishover", false);
		});
		
		$("#allocation_options").change(function() {
			var agent = self.views.clickedAgent;
			var task = $("option:selected", this).val();
			$.post("/assign", {agentId:agent, taskId: task});
		});
		$("#allocation_options").mouseover(function() {
			$("#allocation_options").data("ishover", true);
		}).mouseout(function() {
			$("#allocation_options").data("ishover", false);
		});
		
		$("#ardrone_ctrl").dialog({
			title: "AR Drone Controller",
			position: {
				my: "right",
				at: "left top",
				of: ".ui-layout-east"
			},
			width: 340,
			autoOpen: false
		});
		this.ardrone_pad();

	},
	ardrone_pad: function() {
		var self = this;
		/*
		this.pfdview = new App.Devices.PFDView("pfdview");
		this.pfdview.timer = window.setInterval(function() {
			if (self.ardrone && self.ardrone.navdata.message) {
				var data = self.ardrone.navdata.message;
				var vx = data.vx, vy = data.vy;
				var ax = data.ax, ay = data.ay;
				self.pfdview.update({
					speed: Math.sqrt(vx*vx+vy*vy) * 0.1,
					targetSpeed: Math.sqrt((vx+ax)*(vx+ax)+(vy+ay)*(vy+ay)) * 0.1,
					altitude: data.altd * 0.1,
					targetAltitude: (data.altd + data.vz + data.az) * 0.1,
					attitude: {
						pitch: data.rotY * Math.PI / 180.0,
						roll: data.rotX * Math.PI / 180.0
					}
				});
				
				$("#ardrone_ctrl").dialog({
					title: "AR Drone Controller: " + self.ardrone.getState()
				});
				
				var tagscount = self.ardrone.tagsCount();
				if (tagscount > 0) {
					if (!self.pfdview.show) {
						$.popText(tagscount + " object is found!", 5);
						self.pfdview.show = true;
					}
				} else {
					self.pfdview.show = false;
				}
			}
		}, 400);
		*/
		$('#takeoff_btn').click(function() {
			var el = $('#takeoff_btn');
			if (el.data("flying")) {
				self.ardrone.landOn();
				el.data("flying", false);
				el.text("Take Off")
			} else {
				self.ardrone.takeOff();
				el.data("flying", true);
				el.text("Land");
			}
		});
		$('#togglecam_btn').click(function() {
			self.ardrone.toggleCamera();
		});
		$('#moveup_btn').mousedown(function() {
			self.ardrone.moveUp();
		}).mouseup(function() {
			self.ardrone.stopMove();
		});
		$('#moveup_btn').on({
			'touchstart': function(){
				self.ardrone.moveUp();
			},
			'touchend': function() {
				self.ardrone.stopMove();
			}
		})
		
		$('#movedown_btn').mousedown(function() {
			self.ardrone.moveDown();
		}).mouseup(function() {
			self.ardrone.stopMove();
		});
		$('#movedown_btn').on({
			'touchstart': function(){
				self.ardrone.moveDown();
			},
			'touchend': function() {
				self.ardrone.stopMove();
			}
		})
		
		$('#turnleft_btn').mousedown(function() {
			self.ardrone.rotateLeft(1.0);
		}).mouseup(function() {
			self.ardrone.stopMove();
		});
		$('#turnleft_btn').on({
			'touchstart': function(){
				self.ardrone.rotateLeft(1.0);
			},
			'touchend': function() {
				self.ardrone.stopMove();
			}
		})
		
		$('#turnright_btn').mousedown(function() {
			self.ardrone.rotateRight(1.0);
		}).mouseup(function() {
			self.ardrone.stopMove();
		});
		$('#turnright_btn').on({
			'touchstart': function(){
				self.ardrone.rotateRight(1.0);
			},
			'touchend': function() {
				self.ardrone.stopMove();
			}
		})
		
//		$('#hover_btn').click(function() {
//			self.ardrone.stopMove();
//		});
//		
//		$('#reset_btn').click(function() {
//			self.ardrone.reset();
//		});
	},
	events: {
		"click #switch_view" : "switch_view",
		"click #change_mode" : "change_mode"
	},
	switch_view: function() {
		var $el = $("#switch_view");
		
		if ($el.data("view")) {
			$el.button({ label: "Camera" });
			$el.data("view", false);
			
			$("#map_canvas").show();
			$("#camera_canvas").hide();
			
			$("#map_canvas_s").hide();
			$("#camera_canvas_s").show();
			
			$("#camera").detach().appendTo("#camera_canvas_s");
			
			this.views.map.trigger("refresh");
			this.views.camera.trigger("refresh");
		} else {
			$el.button({ label: "Map"} );
			$el.data("view", true);
			
			$("#map_canvas").hide();
			$("#camera_canvas").show();
			
			$("#map_canvas_s").show();
			$("#camera_canvas_s").hide();
			
			$("#camera").detach().appendTo("#camera_canvas");
			
			this.views.submap.trigger("refresh");
			this.views.camera.trigger("refresh");
		}
	},
	change_mode: function() {
		var $el = $("#change_mode");
		
		if ($el.data("mode")) {
			$.post("/agents/" + this.agent.getId(), {
				mode: "auto"
			});
			$el.data("mode", false);
			
			this.set_mode("auto");
		} else {
            $.post("/agents/" + this.agent.getId(), {
                mode: "teleop"
            })
			$el.data("mode", true);
			
			this.set_mode("teleop");
		}
	},
	set_mode: function(mode) {
		var $el = $("#change_mode");
		
		if (mode !== "teleop") {
			$("#auto").show();
			$("#manual").hide();
			$el.button({ label: "Teleoperate" });
			
			if ($("#ardrone_ctrl").dialog("isOpen")) {
				$("#ardrone_ctrl").dialog("close");
			}
		} else {
			$("#auto").hide();
			$("#manual").show();
			$el.button({ label: "Autopilot" });
			
			if (this.agent.isSimulated()) {
				if ($("#ardrone_ctrl").dialog("isOpen")) {
					$("#ardrone_ctrl").dialog("close");
				}
			} else {
				if (!$("#ardrone_ctrl").dialog("isOpen")) {
					$("#ardrone_ctrl").dialog("open");
				}
			}
		}
	}
});