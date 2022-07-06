_.provide("App.Devices.ARDrone");

App.Devices.ARDrone = function (options) {
	var self = this;
	options = options || {};
	
	this.host = options.host;
	this.port = options.port || 9090;
	this.ros = new ROSLIB.Ros({
		url: 'ws://' + this.host + ':' + this.port
	});
	
	this.takeoff = new ROSLIB.Topic({
		ros: this.ros,
		name: App.Devices.ARDrone.Topics.CONTROL_TAKEOFF,
		messageType: 'std_msgs/Empty'
	});
	
	this.land = new ROSLIB.Topic({
		ros: this.ros,
		name: App.Devices.ARDrone.Topics.CONTROL_LAND,
		messageType: 'std_msgs/Empty'	
	});
	
	this.teleop = new ROSLIB.Topic({
	    ros : this.ros,
	    name : App.Devices.ARDrone.Topics.CONTROL_TELEOP,
	    messageType : 'geometry_msgs/Twist'
	 });
	
	this.navdata = new ROSLIB.Topic({
		ros: this.ros,
		name: App.Devices.ARDrone.Topics.SENSOR_NAVDATA,
		messageType: 'ardrone_autonomy/Navdata'
	});
	this.localization = {pose: {x:0, y:0, z:0, a:0}, time:null};
	
	this.navdata.subscribe(function(message) {
		if (message) {
			var a = message.rotZ * Math.PI / 180.0;
			
			if (self.localization.time) {
				var t = (message.tm - self.localization.time)/1000000;
				var x = message.vx * t / 1000;
				var y = message.vy * t / 1000;
				var z = message.vz * t / 1000;
				
				self.localization.pose.x += x;
				self.localization.pose.y += y;
				self.localization.pose.z += z;
			}
			
			self.localization.pose.a = a;
			self.localization.time = message.tm;
			
			if (options.localization) {
				if (options.localization.postPose) {
					options.localization.postPose(self.localization.pose);
				}
				if (options.localization.postAngle) {
					options.localization.postAngle(self.localization.pose.a);
				}
			}
		}
		
		self.setNavdata_(options, message);
		self.navdata.message = message;
	});
	
	this.imu = new ROSLIB.Topic({
		ros: this.ros,
		name: App.Devices.ARDrone.Topics.SENSOR_IMU,
		messageType: 'sensor_msgs/Imu'
	});
	this.imu.subscribe(function(message) {
		self.imu.message = message;
	});
	
	this.camera = {};
	
	if (options.camera) {
		this.camera.divID = options.camera.divID;
		this.camera.width = options.camera.width || 400;
		this.camera.height = options.camera.height || 400;	
		this.camera.quality = options.camera.quality || 0;
		
		this.camera.host = options.camera.host || this.host;
		this.camera.port = options.camera.port || 8080;
	
		this.camera.img = document.createElement("img");
		this.camera.img.width = this.camera.width;
		this.camera.img.height = this.camera.height;
		this.camera.img.src = this.getStream_(App.Devices.ARDrone.Topics.CAMERA_MAIN);
		
		if (this.camera.divID instanceof jQuery) {
			this.camera.divID.append(this.camera.img);
		} else if (this.camera.divID instanceof Object) {
			this.camera.divID.appendChild(this.camera.img);
		} else {
			document.getElementById(this.camera.divID).appendChild(this.camera.img);
		}
	}
	
	this.camera.toggle = new ROSLIB.Service({
		ros: this.ros,
		name: App.Devices.ARDrone.Services.CAMERA_TOGGLE,
		serviceType: 'std_srvs/Empty'
	});
	this.camera.channel = new ROSLIB.Service({
		ros: this.ros,
		name: App.Devices.ARDrone.Services.CAMERA_CHANNEL,
		serviceType: 'uint8'
	});
	
	this.led = new ROSLIB.Service({
		ros: this.ros,
		name: App.Devices.ARDrone.Services.LED_ANIMATION,
		serviceType: 'ardrone_autonomy/LedAnim'
	});
	
	this.flight = new ROSLIB.Service({
		ros: this.ros,
		name: App.Devices.ARDrone.Services.FLIGHT_ANIMATION,
		serviceType: 'ardrone_autonomy/FlightAnim'
	});
	
	if (options.keyboard) {
		this.keyboardControl_();
	}
}

App.Devices.ARDrone.Topics = {
	CONTROL_TAKEOFF: '/ardrone/takeoff',
	CONTROL_LAND: '/ardrone/land',
	CONTROL_TELEOP: '/cmd_vel', 
	
	SENSOR_IMU: '/ardrone/imu',
	SENSOR_NAVDATA: '/ardrone/navdata',
	
	CAMERA_MAIN: '/ardrone/image_raw',
	CAMERA_FRONT: '/ardrone/front/image_raw',
	CAMERA_BOTTOM: '/ardrone/bottom/image_raw'
}

App.Devices.ARDrone.Services = {
	CAMERA_TOGGLE: '/ardrone/togglecam',
	CAMERA_CHANNEL: '/ardrone/setcamchannel',
	LED_ANIMATION: '/ardrone/setledanimation',
	FLIGHT_ANIMATION: '/ardrone/setflightanimation'
}

App.Devices.ARDrone.prototype.setDiv_ = function(div, html) {
	var e = document.getElementById(div);
	if (e) e.innerHTML = html;
}

App.Devices.ARDrone.prototype.setNavdata_ = function(options, message) {
	if (options.navdata) {
		for (var key in options.navdata) {
			this.setDiv_(options.navdata[key], message[key].toFixed(2));
		}
		if (options.navdata.state) {
			var states = ['Unknown', 'Inited', 'Landed', 'Flying', 'Hovering', 
			              'Test', 'Taking Off', 'Flying', 'Landing', 'Looping'];
			this.setDiv_(options.navdata.state, states[message.state]);
		}
	}
}

App.Devices.ARDrone.prototype.toggleCam_ = function() {
	this.camera.toggle.callService(new ROSLIB.ServiceRequest(), function(result){});
}

App.Devices.ARDrone.prototype.setCamChannel_ = function(channel) {
	this.camera.channel.callService(new ROSLIB.ServiceRequest({
		channel: channel
	}), function(result){});
}

App.Devices.ARDrone.prototype.setLedAnim_ = function(type, freq, duration) {
	this.led.callService(new ROSLIB.ServiceRequest({
		type: type,
		freq: freq,
		duration: duration
	}), function(result){});
}

App.Devices.ARDrone.prototype.setFlightAnim_ = function(type, duration) {
	this.flight.callService(new ROSLIB.ServiceRequest({
		type: type,
		duration: duration
	}), function(result){});
}

App.Devices.ARDrone.prototype.getStream_ = function(topic) {
	this.camera.topic = topic;
	var src = 'http://' + this.camera.host + ':' + this.camera.port + '/stream?topic=' + topic + 
			  '?width=' + this.camera.width + '?height=' + this.camera.height;
	if (this.camera.quality > 0) {
		src += '?quality=' + this.camera.quality;
	}
	return src;
}

App.Devices.ARDrone.prototype.keyboardControl_ = function() {
	var self = this;
	var body = document.getElementsByTagName('body')[0];
	body.addEventListener('keydown', function(e) {
	    switch (e.keyCode) {
	    case 33: // page up
	    	self.moveUp(1.0);
	    	break;
	    case 34: // page down
	    	self.moveDown(1.0);
	    	break;
	    case 35: // end
	    	self.landOn();
	    	break;
	    case 36: // home
	    	self.takeOff();
	    	break;
	    case 37: // left
	    	if (e.shiftKey) {
	    		self.moveLeft(1.0);
	    	} else {
	    		self.rotateLeft(0.5);
	    	}
	    	break;
	    case 38: // up
	    	self.moveForward(1.0);
	    	break;
	    case 39: // right
	    	if (e.shiftKey) {
	    		self.moveRight(1.0);
	    	} else {
	    		self.rotateRight(0.5);
	    	}
	    	break;
	    case 40: // down
	    	self.moveBackward(1.0);
	    	break;
	    }
	}, false);
	body.addEventListener('keyup', function(e) {
	    self.stopMove();
	}, false);	
}

App.Devices.ARDrone.prototype.getBattery = function() {
	if (this.navdata.message) {
		return this.navdata.message.batteryPercent;
	}
}

App.Devices.ARDrone.prototype.getState = function() {
	if (this.navdata.message) {
		var states = ['Unknown', 'Inited', 'Landed', 'Flying', 'Hovering', 
		              'Test', 'Taking Off', 'Flying', 'Landing', 'Looping'];
		return states[this.navdata.message.state];
	}
}

App.Devices.ARDrone.prototype.toggleCamera = function() {
	this.toggleCam_();
}

App.Devices.ARDrone.prototype.switchCamera = function(topic) {
	this.camera.img.src = this.getStream_(topic);
	
	var channel = 0;
	if (topic == App.Devices.ARDrone.Topics.CAMERA_BOTTOM) {
		channel = 1;
	} else {
		channel = 0;
	}
	this.setCamChannel_(channel);
}

App.Devices.ARDrone.prototype.takeOff = function() {
	this.takeoff.publish(new ROSLIB.Message());
}

App.Devices.ARDrone.prototype.landOn = function() {
	this.land.publish(new ROSLIB.Message());
}

App.Devices.ARDrone.prototype.hover = function() {
	this.teleop.publish(new ROSLIB.Message({
		linear : { x: 0, y: 0, z: 0 },		
		angular: { x: 0, y: 0, z: 0 }
	}));
}

App.Devices.ARDrone.prototype.stop = function() {
	this.teleop.publish(new ROSLIB.Message({
		linear : { x: 0, y: 0, z: 0 },		
		angular: { x: 1, y: 1, z: 0 }
	}));
}

App.Devices.ARDrone.prototype.move = function(vx, vy, vz) {
	vx = _.minmax(-1.0, vx, 1.0);
	vy = _.minmax(-1.0, vy, 1.0);
	vz = _.minmax(-1.0, vz, 1.0);
	
	this.teleop.publish(new ROSLIB.Message({
		linear : { x: vx, y: vy, z: vz },		
		angular: { x: 0, y: 0, z: 0 }
	}));
}

App.Devices.ARDrone.prototype.rotate = function(vx, vy, vz) {
	vx = _.minmax(-1.0, vx, 1.0);
	vy = _.minmax(-1.0, vy, 1.0);
	vz = _.minmax(-1.0, vz, 1.0);
	
	this.teleop.publish(new ROSLIB.Message({
		linear : { x: 0, y: 0, z: 0 },		
		angular: { x: vx, y: vy, z: vz }
	}));
}

App.Devices.ARDrone.prototype.stopMove = function() {
//	this.stop();
	this.hover();
}

App.Devices.ARDrone.prototype.moveForward = function(speed) {
	this.move(speed || 1.0, 0, 0);
}

App.Devices.ARDrone.prototype.moveBackward = function(speed) {
	this.move(-speed || -1.0, 0, 0);
}

App.Devices.ARDrone.prototype.moveLeft = function(speed) {
	this.move(0, speed || 1.0, 0);
}

App.Devices.ARDrone.prototype.moveRight = function(speed) {
	this.move(0, -speed || -1.0, 0);
}

App.Devices.ARDrone.prototype.moveUp = function(speed) {
	this.move(0, 0, speed || 1.0);
}

App.Devices.ARDrone.prototype.moveDown = function(speed) {
	this.move(0, 0, -speed || -1.0);
}

App.Devices.ARDrone.prototype.moveTo = function(linear, angular) {
	var vx = linear * Math.cos(angular);
	var vy = linear * Math.sin(angular);
	this.move(vx, vy, 0);
}

App.Devices.ARDrone.prototype.rotateLeft = function(speed) {
	this.rotate(0, 0, speed || 0.5);
}

App.Devices.ARDrone.prototype.rotateRight = function(speed) {
	this.rotate(0, 0, -speed || -0.5);
}