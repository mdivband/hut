function ImgTileLayer(options) {
	this.map = options.map;
	this.base = options.base;
	this.tiles = options.tiles;
	
	this.overlayers = {}
	for (var i in this.tiles) {
		var image = this.base + "/" + this.tiles[i].image;
		var bounds = new google.maps.LatLngBounds(
				 new google.maps.LatLng(this.tiles[i].south, this.tiles[i].west), //sw
				 new google.maps.LatLng(this.tiles[i].north, this.tiles[i].east)  //ne
		);
		this.overlayers[i] = new google.maps.GroundOverlay(image, bounds);
	}
	
	var self = this;
	google.maps.event.addListener(this.map, "bounds_changed", function() {
		var viewport = self.map.getBounds();
		for (var i in self.overlayers) {
			var bounds = self.overlayers[i].getBounds();
			if (viewport.intersects(bounds)) {
				self.overlayers[i].setMap(self.map);
			} else {
				self.overlayers[i].setMap(null);
			}
		}
	});
};