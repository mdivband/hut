function LatLngTooltip(options) {
	this.map_ = options.map;
	this.setMap(this.map_);
	
	this.div_ = document.createElement("div");
	this.div_.style.position = "absolute";
	this.div_.style.padding = "5px";
	this.div_.style.border = "1px solid gray";
	this.div_.style.fontSize = "12px";
	this.div_.style.fontFamily = "Verdana";
	this.div_.style.backgroundColor = "#fff";
	this.div_.style.whiteSpace = "nowrap";
}

LatLngTooltip.prototype = new google.maps.OverlayView();

LatLngTooltip.prototype.onAdd = function() {
	var self = this;
	this.listeners_ = [
		google.maps.event.addListener(this.map_, "mousemove", function (e) {
			self.event = e;
			self.updateTip(e);
		}),
		google.maps.event.addListener(this.map_, "mouseout", function (e) {
			self.mouseover = false;
			self.set("show", false);
		}),
		google.maps.event.addListener(this.map_, "mouseover", function (e) {
			self.mouseover = true;
			if (self.shiftdown) {
				self.set("show", true) || self.addTip();
			}
		}),
		google.maps.event.addDomListener(document, "keydown", function (e) {
			if (!e) e = window.event;
			if (e.keyCode == 16 || e.which == 16) {
				self.shiftdown = true;
				if (self.mouseover) {
					self.addTip();
				}
			}
		}),
		google.maps.event.addDomListener(document, "keyup", function (e) {
			self.shiftdown = false;
			self.removeTip();
		})
	];
};

LatLngTooltip.prototype.draw = function() {
	if (typeof this.event != "undefined") {
		this.updateTip(this.event);
	}
};

LatLngTooltip.prototype.onRemove = function() {
	this.removeTip();
	for (var i = 0; i < this.listeners_.length; i++) {
		google.maps.event.removeListener(this.listeners_[i]);
	}
};

LatLngTooltip.prototype.addTip = function() {
	this.set("show", true);
	console.log(this.div_.innerHTML);
	this.width_ = this.div_.offsetWidth;
    this.getPanes().floatPane.appendChild(this.div_);
};

LatLngTooltip.prototype.removeTip = function() {
	var parent = this.div_.parentNode;
    if (parent) parent.removeChild(this.div_)
};

LatLngTooltip.prototype.updateTip = function(e) {
	if (e) {
		var projection = this.getProjection();
		
		var pixel = projection.fromLatLngToDivPixel(e.latLng);
		var lat = e.latLng.lat(), lng = e.latLng.lng();
		var zoom = this.map_.getZoom();
		
		lat = lat.toFixed(Math.round(zoom / 4 + 1));
		lng = lng.toFixed(Math.round(zoom / 4 + 1));
		this.div_.innerHTML = lat + ", " + lng;
		
		var x = pixel.x + 7;
		var y = pixel.y + 7;
		
		this.div_.style.left = x + "px";
		this.div_.style.top = y + "px";
	}
};
