function ImgMapType(options) {
	options = options || {};
	
    function MapType() {};
    MapType.prototype.maxZoom = options.maxZoom || 20;
    MapType.prototype.minZoom = options.minZoom || 0;    
    MapType.prototype.tileSize = options.tileSize || new google.maps.Size(256,256);
    MapType.prototype.name = options.name || "ImgMapType";
    MapType.prototype.alt = options.alt || "";
    MapType.prototype.getTile = function(coord, zoom, ownerDocument) { 
    	return ownerDocument.createElement('DIV'); 
    };  
    
    this.map = options.map;
    this.map.mapTypes.set(options.name, new MapType());
    this.map.setMapTypeId(options.name);
    
    this.overlayer = new google.maps.GroundOverlay(options.image, options.imageBounds);
    this.overlayer.setMap(this.map);
};