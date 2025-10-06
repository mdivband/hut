/**
* configmap.js
* Author Y.Ikuno
*/

/**
 * ***********************************************************************
 * GLOBAL VARIABLES
 */
var map;
var markers=[];

/************* End of GLOBAL VARIABLES *************/

/**
 *************************************************************************
 * Functions
 *
 */

 /**
 * Initialize of the map
 */
function initMap() {
  // Create a map object and specify the DOM element for display.
    map = new google.maps.Map(document.getElementById('map'), {
    center: {lat: 50.9313192, lng: -1.3983454},
    zoom: 15,
  });

  // Create the search box and link it to the UI element.
  var input = document.getElementById('pac-input');
  var searchBox = new google.maps.places.SearchBox(input);
  map.controls[google.maps.ControlPosition.TOP_LEFT].push(input);

  // Bias the SearchBox results towards current map's viewport.
  map.addListener('bounds_changed', function() {
    searchBox.setBounds(map.getBounds());
  });

  // [START region_getplaces]
  // Listen for the event fired when the user selects a prediction and retrieve
  // more details for that place.
  searchBox.addListener('places_changed', function() {
    var places = searchBox.getPlaces();

    if (places.length == 0) {
      return;
    }

    // For each place, get the icon, name and location.
    var bounds = new google.maps.LatLngBounds();
    places.forEach(function(place) {
      var icon = {
        url: place.icon,
        size: new google.maps.Size(71, 71),
        origin: new google.maps.Point(0, 0),
        anchor: new google.maps.Point(17, 34),
        scaledSize: new google.maps.Size(25, 25)
      };

      if (place.geometry.viewport) {
        // Only geocodes have viewport.
        bounds.union(place.geometry.viewport);
      } else {
        bounds.extend(place.geometry.location);
      }
    });
    map.fitBounds(bounds);
  });
  // [END region_getplaces]

  //[ Map click event - add uav]//
  map.addListener('click', function(e){
  	if($('#adduav').hasClass('active')){
  		addMarker(e.latLng);
  	}
  });
  //[END of map click event]//
}


function changeBtns(btn_id){
	// btn_id = {0:add, 1:del}
	if(btn_id==0){
		if($('#deluav').hasClass('active')) $('#deluav').removeClass('active');
		if($('#adduav').hasClass('active')) $('#adduav').removeClass('active');
		else $('#adduav').addClass('active');
	}else if(btn_id==1){
		if($('#adduav').hasClass('active')) $('#adduav').removeClass('active');
		if($('#deluav').hasClass('active')) $('#deluav').removeClass('active');
		else $('#deluav').addClass('active')
	}	
}

function btnListener(){
	$( ".btn3d" ).on( "click", function(){
		changeBtns($(this).attr('name'));
	});
	$('#setupmap').on('click', function(){
		var arysize = markers.filter(function(value){ return value !== undefined }).length;
		if(arysize>0){
			console.log(markers);
			if(confirm("Are you sure you will set up this configuration?")){
				setupConfig();
			}
		}else{
			alert("Please add at least one UAV");
		}
	});
}

/**
* Add marker, if delete btn is activated will delete from map and marker array
*/
function addMarker(latlng){
	var marker = new google.maps.Marker({
		position: latlng,
		map: map,
		id: markers.length,
		title: "UAV"+markers.length,
		icon: '/icons/plane.png '
	});
	marker.addListener('click', function(e){
		if($('#deluav').hasClass('active')){
			markers[this.id].setMap(null);
			delete markers[this.id];
		}
	});
	markers.push(marker);
}

/**
* 
*/
function setupConfig(){
	var data=[];
	_.each(markers, function(ele){
		if(ele!==undefined){
			data.push(ele.getPosition().lat());
			data.push(ele.getPosition().lng());
		}
	});
	$.post("/configjson", {agent: JSON.stringify(data)} )
	.always(function(){
		window.location.replace("http://localhost:8000");
	});
}

/************* End of Functions*************/

/**
 *****************************************************************************
 *  main code
 *
 */
google.maps.event.addDomListener(window, 'load', initMap);
btnListener();


/************* End of Main code *************/

