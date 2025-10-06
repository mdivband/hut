$(document).ready(function () {

    /////////////////////////////// data //////////////////////////////////////
    var agents = [];
    var titles = [];
    var flags = [];
    var maps = [];
    var targets_road = [];
    var targets_camera=[];
    var mainTargets = [];
    var tasks_road = [];
    var tasks_camera = [];
    var polylines = [];
    var regions_road =[];
    var regions_camera = [];
    var pre_task, pre_region;
    var targets;
    var _terrainOverlay;

    var data;
    var mapOptionsCamera, map_camera, mapOptionRoad, map_road;
    var selected_agent;
    var speed_manu=0;

    var moving;
    var game_id;
    var provdoc=null;
    var api = new $.provStoreApi({ username: 'atomicorchid', key: '2ce8131697d4edfcb22e701e78d72f512a94d310' });
    var ps = PostService();

    // ros
    var ros =null;
    var connected = false;

    var infowindow = new google.maps.InfoWindow({
        content: null,
        id: null
    });

    var state = new StateCache();
    state.start();

    // initialise
    setTimeout(function() { initialise(); }, 500);
    function initialise() {
        data = state.get();
        updateAgentButtons(data);

        var lat_focus = data.agents.length > 0 ? data.agents[0].coordinate.latitude : 50;
        var lon_focus = data.agents.length > 0 ? data.agents[0].coordinate.longitude : 1;

        mapOptionsCamera = {
            zoom: 19,
            tilt: 45,
            heading: 90,
            draggable: false,
            scrollwheel: false,
            disableDoubleClickZoom: true,
            disableDefaultUI: true,
            center: new google.maps.LatLng(lat_focus, lon_focus),
            mapTypeId: google.maps.MapTypeId.SATELLITE
        };
        map_camera =  new google.maps.Map(document.getElementById("control_camera_view"),
            mapOptionsCamera);

        // // double click and add new target
        /*
        google.maps.event.addListener(map_camera, 'dblclick', function(event) {
            var marker = new google.maps.Marker({
                position: event.latLng,
                map: map_camera,
                icon: "icons/redquestion2.png"
            });
            marker.setMap(map_camera);


            var lat = event.latLng.lat();
            var lng = event.latLng.lng();
            var incident_types = {
                0: 'WaterSource',
                1: 'InfrastructureDamage',
                2: 'MedicalEmergency',
                3: 'Crime'
            };
            var dialog = $("#dialog").dialog({
                resizable: false,
                modal: true,
                buttons: {
                    Cancel: function() {
                        marker.setMap(null);

                        $( this ).dialog( "close" );
                    },
                    "OK": function() {
                        marker.setMap(null);
                        var targetid = getNewTargetNum();
                        var type = $('#dialog_target_list [name=dialog_target_list_select]').val();
                        ps.postAONEW(targetid, type, lat, lng, game_id);
                        ps.postPROV(api, targetid, type, 0, game_id, provdoc, 'uav_bronze_commander', false, true);
                        ps.postPrePROV(targetid, type, 0, game_id, lat , lng);
                        $.post("/new-target",{
                            id: targetid,
                            targetType: type,
                            lat: lat,
                            lng: lng
                        }).done(function(){
                            $.post("/logger", {
                                actor: "bronze",
                                msg: "Target-new,"+incident_types[type]+",id:"+targetid+",lat:"+lat+",lng:"+lng
                            });
                            setTimeout(function() { update_target(); }, 1000);
                            setTimeout(function() { update_target(); }, 2000);
                            setTimeout(function() { update_target(); }, 3000);
                            console.log(targetid+" targets added "+ type);
                        });
                        $( this ).dialog( "close" );
                    }

                }
            });
            dialog.dialog("open");
        });
        */

        mapOptionsRoad = {
            zoom: 16,
            tilt: 45,
            heading: 90,
            draggable: true,
            scrollwheel: true,
            disableDoubleClickZoom: false,
            disableDefaultUI: true,
            center: new google.maps.LatLng(lat_focus, lon_focus),
            mapTypeId: google.maps.MapTypeId.ROADMAP
        };
        map_road =  new google.maps.Map(document.getElementById("map_content"),
            mapOptionsRoad);

        game_id = data.game_id;
        update_target();

        //setup ros
        setupMessageQueue();
        setupROS();

        window.setInterval(function() {
            if (!$("#myonoffswitch_c").is(':checked')) {
                update_ros(0, 0, null);
            }
        }, 5000);
    } // end of initialize

	function updateAgentButtons(data) {
        data.agents.sort(compare);
        if(selected_agent == undefined && data.agents.length > 0) {
            selected_agent = data.agents[0];
            $("#spi_agent_id_c").text(selected_agent.id);
            if (selected_agent.flag) {
                alert(selected_agent.id + " is already flagged!");
                // $.post("/agent_flag", {
                //     id: selected_agent.id,
                //     flag: false
                // });
            }
        }

        _.each(data.agents, function(v){
            var id = v.id;
            if ($("#"+id).length) {
            	return;
			}

			var content = "<option id='sidebtn_" + id + "' value='" + id + "'>" + id + "</option>"
			$("#spi_agent_id_c").append(content);

            // set up btns
            var contentbtn;
            //var tall = $(window).height()/6;
            //console.log( tall + " height dayo~");
            if(id == selected_agent.id){
                contentbtn = $('<div>', {
                    id: id,
                    class: "flag_btns selected",
                    text: id
                });
            }else if(v.flag == true ){
                contentbtn = $('<div>', {
                    id: id,
                    class: "flag_btns flagged",
                    text: id
                });
            }else{
                contentbtn = $('<div>', {
                    id: id,
                    class: "flag_btns ",
                    text: id
                });
            }
            flags.push(contentbtn);
            contentbtn.bind("click", function(data){
                _.each(flags ,function(v){
                    if(v.get(0).id == data.currentTarget.id){
                        if(v.hasClass("flagged")){
                            console.log("true");
                            v.removeClass("flagged");
                            v.addClass("selected");
                            agent_select(v.get(0).id);

                            // $.post("/agent_flag", {
                            //     id: v.get(0).id,
                            //     flag: false
                            // });
                            $.post("/logger", {actor:"bronze", msg:"Flagged:false,id:"+v.get(0).id})
                        }else{
                            v.addClass("selected");
                            agent_select(v.get(0).id);
                            $.post("/logger", {actor:"bronze", msg:"AgentCameraSelected:"+v.get(0).id});
                        }
                    }else{
                        v.removeClass("selected");
                    }
                });

            });

            $("#control_flag").append(contentbtn);
        });
	}

	var RMQ_client;
    var droneID;
    function setupMessageQueue() {
        //register the controller with the server
        var options = {
            enableHighAccuracy: true,
            timeout: 5000,
            maximumAge: 0
        };
        navigator.geolocation.getCurrentPosition(function(location) {
            var data = {lat:location.coords.latitude,
                lon:location.coords.longitude,
                type:"human"};

            $.post("/register", JSON.stringify(data), function(resp) {
                var msgquri = resp.URI;
                var params = msgquri.replace("amqp://","").split(/[:@/]/);
                var user = params[0];
                var pass = params[1];
                var hostname = params[2];
                // var port = params[3];
                var vhost = decodeURIComponent(params[4]);

                droneID = resp.ID;
                var queueName = "UAV_TaskQueue_" + droneID;


                var ws = new SockJS("https://"+hostname+":15671/stomp");
                // var ws = new SockJS("http://"+hostname+":15674/stomp");
                RMQ_client = Stomp.over(ws);

                // RabbitMQ SockJS does not support heartbeats so disable them
                RMQ_client.heartbeat.outgoing = 0;
                RMQ_client.heartbeat.incoming = 0;

                RMQ_client.debug = function(m) {
                    console.log("STOMP DEBUG", m);
                };

                // Make sure the user has limited access rights
                RMQ_client.connect(user, pass, function() {
                    console.log("Connected to RMQ");

                    var taskQueue_ID = RMQ_client.subscribe("/queue/"+queueName, RMQ_TaskQueue, {"durable":false, "exclusive":false, "auto-delete":false});

                }, function(e) {
                    console.log("rabbitmq error",e);
                }, vhost);

            });
        },
        function(data) {
            console.log("registering failed");
        },
        options);
    }

    function setupROS(){
        var self = this;

        ros = new ROSLIB.Ros();

        // If there is an error on the backend, an 'error' emit will be emitted.
        ros.on('error', function(error) {
            console.log(error);
        });

        // Find out exactly when we made a connection.
        ros.on('connection', function() {
            console.log('Connection made!');
            connected = true;
        });

        ros.on('close', function() {
            console.log('Connection closed.');
            connected = false;
        });

        // Create a connection to the rosbridge WebSocket server.
        ros.connect('ws://haymarket.ecs.soton.ac.uk:9090');

    }

    var curr_missionId;
    function RMQ_TaskQueue(data) {
        var message = JSON.parse(data.body);

        curr_missionId = message.ID;
        var lat = message.Coordinates[0].Latitude;
        var lon = message.Coordinates[0].Longitude;

        //do something to render these on a map
        console.log(curr_missionId, lat, lon);
    }
    function RMQ_SendCoords() {
        if(!RMQ_client)
            return;

        var options = {
            enableHighAccuracy: true,
            timeout: 5000,
            maximumAge: 0
        };
        navigator.geolocation.getCurrentPosition(function(location) {
            var data = {"ID":droneID,
                    "MissionID": curr_missionId,
                    "Content":"Coordinates",
                    "Coordinates":{
                        "Latitude":location.coords.latitude,
                        "Longitude":location.coords.longitude
                    }};

            RMQ_client.send("/queue/Meta_Drone_Data", {"content-type":"text/plain", "durable":false, "exclusive":false, "auto-delete":false}, JSON.stringify(data));
        },
        function() {
            console.log("failed to send curr pos");
        }, options);
    }

    function agent_select(value){
        _.each(data.agents, function(agent){
            if(agent.id == value){
                selected_agent = agent;
                update_target();
            }
        });
        update_target();
        $("#spi_agent_id_c").text(value);
    }

    function getNewTargetNum(){
        data = state.get();
        return data.target_new;
    }

    // updating every 0.5 sec
    window.setInterval(function(){ update_agent(); },500);
    function update_agent() {
        data = state.get();
        updateAgentButtons(data);

        //send data to RMQ
        RMQ_SendCoords();

        if(data.prov_doc !=null ){
            provdoc = data.prov_doc
        }
        var coord, heading, altitude;

        setAllMap(null, agents);
        agents = [];

        setAllMap(null, polylines);
        polylines = [];
        _.each(data.agents, function(agent){
            coord = agent.coordinate;
            heading = agent.heading;
            altitude = agent.altitude;

            var path = [{lat: coord.latitude, lng:coord.longitude}];
            for (var i in agent.route) {
                if (agent.route[i]) {
                    path.push({lat: agent.route[i].latitude, lng: agent.route[i].longitude});
                }
            }
            var color = "#80cbff";
            if(agent.type == 1) {
                color = "#3c94dc";
            }
            var flightPath_small = new google.maps.Polyline({
                path: path,
                geodesic: true,
                strokeColor: color,
                strokeOpacity: 1.0,
                strokeWeight: 2
            });
            flightPath_small.setMap(map_road);
            polylines.push(flightPath_small);

            if(selected_agent.id == agent.id){
                set_status(agent);

                agents.push(new google.maps.Marker({
                    id: agent.id,
                    map:map_road,
                    position: new google.maps.LatLng(agent.coordinate.latitude, agent.coordinate.longitude),
                    icon: "icons/plane.png"}));

                agents.push(new google.maps.Marker({
                    id: agent.id,
                    map:map_camera,
                    position: new google.maps.LatLng(agent.coordinate.latitude, agent.coordinate.longitude),
                    icon: "icons/measle_blue.png"}));

                map_camera.setCenter(new google.maps.LatLng(coord.latitude, coord.longitude));
                map_road.setCenter(new google.maps.LatLng(coord.latitude, coord.longitude));
                map_camera.setZoom(19);

                var flightPath = new google.maps.Polyline({
                    path: path,
                    geodesic: true,
                    strokeColor: color,
                    strokeOpacity: 1.0,
                    strokeWeight: 4,
                    icons: [{
                        icon: {
                            scale: 2,
                            path: google.maps.SymbolPath.FORWARD_OPEN_ARROW
                        },
                        offset: '100%'
                    }],
                });

                flightPath.setMap(map_camera);
                polylines.push(flightPath);
            }else{
                agents.push(new google.maps.Marker({
                    id: agent.id,
                    map:map_road,
                    position: new google.maps.LatLng(agent.coordinate.latitude, agent.coordinate.longitude),
                    icon: "icons/plane_small.png"}));
            }

            //update flag
            _.each(flags, function(v){
                if(v.get(0).id == agent.id){
                    if(v.hasClass("flagged") != agent.flag){
                        if(agent.flag){
                            v.addClass("flagged");
                        }else{
                            v.removeClass("flagged");
                        }
                    }
                }
            });
        });

        //draw onto a canvas
        var hut_state = JSON.parse(data.hut_state);
        var ppc = 10;
        var w = hut_state.terrain.length;
        var h = hut_state.terrain[0].length;

        var canvas = document.createElement('canvas');
        canvas.width = w * ppc;
        canvas.height = h * ppc;
        var ctx = canvas.getContext('2d');

        //draw belief and fog
        for (var x = 0; x < w; x++) {
            for (var y = 0; y < h; y++) {
                if (hut_state.fog[x][y] > 0) {
                    ctx.globalAlpha = hut_state.fog[x][y] * 0.8;
                    ctx.fillStyle = "#000";
                    ctx.fillRect(x * ppc, y * ppc, ppc, ppc);
                }
                if (hut_state.belief[x][y] > 0.05) {
                    ctx.globalAlpha = hut_state.belief[x][y] * 1;
                    ctx.fillStyle = "#ffa21d";
                    ctx.fillRect(x * ppc, y * ppc, ppc, ppc);
                }
            }
        }
        ctx.globalAlpha = 1.0;

        //draw terrain
        ctx.fillStyle = '#7c7c7c';
        for(var x=0; x<w; x++) {
            for(var y=0; y<h; y++) {
                if (hut_state.terrain[x][y] == 1) {
                    ctx.fillRect(x*ppc,y*ppc,ppc,ppc);
                }
            }
        }

        //draw the discrete grid
        // ctx.fillStyle = '#000';
        // for (var x = 0; x <= w; x++) {
        //     ctx.beginPath();
        //     ctx.moveTo(x * ppc, 0);
        //     ctx.lineTo(x * ppc, h * ppc);
        //     ctx.stroke();
        // }
        // for (var y = 0; y <= h; y++) {
        //     ctx.beginPath();
        //     ctx.moveTo(0, y * ppc);
        //     ctx.lineTo(w * ppc, y * ppc);
        //     ctx.stroke();
        // }

        var canvasImage = canvas.toDataURL('image/png');
        if (_terrainOverlay != null) {
            _terrainOverlay.setMap(null);
        }
        var swBound = new google.maps.LatLng(hut_state.southeast.Latitude, hut_state.northwest.Longitude);
        var neBound = new google.maps.LatLng(hut_state.northwest.Latitude, hut_state.southeast.Longitude);
        var imageBounds = new google.maps.LatLngBounds(swBound, neBound);
        var overlayOpts = { opacity:0.9 };
        _terrainOverlay = new google.maps.GroundOverlay(canvasImage, imageBounds , overlayOpts);
        _terrainOverlay.setMap(map_camera);

        update_task(map_camera, map_road);
        update_target();

    } // end of update_agent


    function update_ros(velocity, angle, alt) {
        var agentid = selected_agent.id.split("-")[0] + selected_agent.id.split("-")[1];

		/*-------- VELOCITY --------*/
        if (alt == null) {
            var velocity_topic = new ROSLIB.Topic({
                ros: ros,
                name: '/' + agentid + '/orchid/velocity',
                // name: '/' + agentid + 'bronze/velocity',
                messageType: 'ardrone_demo/BronzeVelocity'
            });

            //Publish messages to ROS topics
            var velMessage = new ROSLIB.Message({
                velocity: velocity, //value between 0 and 1
                angle: angle //angle in degrees (+/-) 0-180. 0 is hard right, +90 is forward -90 is back
            });

            velocity_topic.publish(velMessage);
        }
		/*-------- ALTITUDE --------*/
        if (velocity == null) {
            var altitude = new ROSLIB.Topic({
                ros: ros,
                name: '/' + agentid + '/orchid/altitude',
                //name: '/' + agentid + 'bronze/altitude',
                messageType: 'ardrone_demo/BronzeAltitude'
            });

            //Publish messages to ROS topics
            var altMessage = new ROSLIB.Message({
                relativeAltitude: alt//value between 0 and 1 specifying UAV altitude
                //as fraction of range between maxi and min allowed altitude
            });

            altitude.publish(altMessage);
        }
    }

    function notify_ros(mode) {
        var agentid = selected_agent.id.split("-")[0] + selected_agent.id.split("-")[1];

        var agent_mode = new ROSLIB.Topic({
            ros: ros,
            name: '/' + agentid + '/orchid/teleop_mode',
            messageType: 'ardrone_demo/BronzeMode'
        });

        var modeType = 0;
        if (mode == "auto") modeType = 1;
        else modeType = 2;

        var modeMessage = new ROSLIB.Message({
            mode: modeType
        });

        agent_mode.publish(modeMessage);
    }


    function set_status(agent){
        $("#canvas_small_info_tasks_c").empty();
        var current_scheduleagents = agent.task;
        var allocation_text = null;
        if(current_scheduleagents == undefined) {
            allocation_text = "No Allocation ";
        }else{
            allocation_text = current_scheduleagents.id;
        }
        $("#canvas_small_info_tasks_c").text(allocation_text);

        if(agent.mode==1){
            $("#canvas_small_info_speed_val_c").text(speed_manu);
        }else{
            $("#canvas_small_info_speed_val_c").text(agent.speed);
        }

        var batterylevel = Math.ceil(agent.battery*100);
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
        $("#canvas_small_info_battery_c > img ").attr({'src':img_url});
        $("#canvas_small_info_battery_text_c").text(batterylevel +"%");

        var alt = agent.altitude;
        if(agent.mode ==1){
            $("#myonoffswitch_c").prop('checked', false);
            $("#auto").hide();
            $("#manual").show();
            $("#altitude_value_manu").text(alt);
            slider_manu_al.slider( "value", alt );
        }else{
            $("#myonoffswitch_c").prop('checked', true);
            $("#auto").show();
            $("#manual").hide();
            $("#altitude_value").text(alt);
            slider_auto_al.slider("value", alt);
        }

    }


    function addClickFunction(marker) {
        google.maps.event.addListener(marker, 'click', function() {
            infowindow.open(marker.map, marker);
            infowindow.setOptions({
                id: marker.id,
                marker: marker
            });
        });
    }

    var self = this;

    function get_icon(num){
        switch(num){
            case -1: // question
                return "icons/redquestion5.png";
            case 0: // water
                return "icons/water.png";
            case 1: //infra
                return "icons/infra.png";
            case 2: // medical
                return "icons/medical.png";
            case 3: //crime
                return  "icons/crime.png";
            case 4: //invalid
                return "icons/invalid.png";
        }
    }

    function get_smallicon(num){
        switch(num){
            case -1: // question
                return "icons/redquestion2.png";
            case 0: // water
                return "icons/water_small.png";
            case 1: //infra
                return "icons/infra_small.png";
            case 2: // medical
                return "icons/medical_small.png";
            case 3: //crime
                return  "icons/crime_small.png";
            case 4: //invalid
                return "icons/invalid_small.png";
        }
    }

    function get_prover(targetid){
        var version, t_type, obj;
        _.each(data.targets, function(t){
            if(t.id == targetid){
                version = t.prov_version;
                t_type = t.targetType;
                obj = {
                    version: version,
                    type: t_type,
                    lat:t.coordinate.latitude,
                    lng:t.coordinate.longitude };
            }
        });
        return obj;
    }



    function update_target(){
        var self = this;
        if( targets_road.length == 0){
            _.each(data.targets, function(target){
                var ticon = get_icon(target.targetType);
                var tsicon = get_smallicon(target.targetType);
                targets_road.push(new google.maps.Marker({
                    id: target.id,
                    map:map_camera,
                    position: new google.maps.LatLng(target.coordinate.latitude, target.coordinate.longitude),
                    icon: ticon,
                    targetType: target.targetType
                }));

                targets_camera.push(new google.maps.Marker({
                    id: target.id,
                    map:map_road,
                    position: new google.maps.LatLng(target.coordinate.latitude, target.coordinate.longitude),
                    icon: tsicon
                }));
            });
            _.each(targets_road, function(marker){
                addClickFunction(marker);
            });
        }else if(data.targets.length != targets_road.length ){ // when to add new targets
            var is_new = true;
            _.each(data.targets, function(t){
                _.each(targets_road, function(targetL){
                    if(t.id == targetL.id){
                        is_new = false;
                    }
                });
                if(is_new){
                    var ticon = get_icon(t.targetType);
                    var tsicon = get_smallicon(t.targetType);
                    var marker = new google.maps.Marker({
                        id: t.id,
                        map:map_camera,
                        position: new google.maps.LatLng(t.coordinate.latitude, t.coordinate.longitude),
                        icon: ticon,
                        targetType: t.targetType
                    });
                    addClickFunction(marker);
                    targets_road.push(marker);
                    targets_camera.push(new google.maps.Marker({
                        id: t.id,
                        map:map_road,
                        position: new google.maps.LatLng(t.coordinate.latitude, t.coordinate.longitude),
                        icon: tsicon
                    }));
                }
                is_new = true;
            });
        }else{
            _.each(data.targets, function(targetR){
                _.each(targets_road, function(targetL){
                    if((targetR.id == targetL.id) && (targetR.targetType != targetL.targetType)){
                        var ticon = get_icon(targetR.targetType);
                        var tsicon = get_smallicon(targetR.targetType);
                        targetL.targetType = targetR.targetType;
                        targetL.prov_version = targetL.prov_version + 1;
                        targetL.setIcon(ticon);

                        _.each(targets_camera, function(t){
                            if(t.id == targetL.id){
                                t.setIcon(tsicon);
                            }
                        });
                    }
                });

            });
        }

    }


    function updateIcons(icon, markers) {
        for (var i = 0; i < markers.length; ++i) {
            if (markers[i].id == infowindow.id) {
                markers[i].setOptions({
                    icon: icon
                });
            }
        }
    }



    function update_task(map_camera, map_road){
        if(pre_task ==null ||  data.tasks.length != (pre_task+pre_region) ){
            setAllMap(null, tasks_camera);
            setAllMap(null, tasks_road);
            setAllMap(null, regions_road);
            tasks_camera = [];
            tasks_road =[];
            regions_road = [];

            _.each(data.tasks, function(task) {
                if(task.nw){
                    regions_road.push( new google.maps.Rectangle({
                        strokeColor: '#000000',
                        strokeOpacity: 0.3,
                        strokeWeight: 2,
                        fillColor: '#000000',
                        fillOpacity: 0.25,
                        map: map_road,
                        bounds: new google.maps.LatLngBounds(
                            new google.maps.LatLng(task.nw.latitude, task.nw.longitude),
                            new google.maps.LatLng(task.se.latitude, task.se.longitude))
                    }));
                }else{
                    tasks_camera.push(new google.maps.Marker({
                        position: new google.maps.LatLng(task.coordinate.latitude,task.coordinate.longitude) ,
                        map: map_camera,
                        icon: "icons/marker_green.png"
                    }));
                    tasks_road.push(new google.maps.Marker({
                        position: new google.maps.LatLng(task.coordinate.latitude,task.coordinate.longitude) ,
                        map: map_road,
                        icon: "icons/measle_green.png"
                    }));
                }
            });
            pre_task = tasks_road.length;
            pre_region = regions_road.length;
        }



    }

    // Sets the map on all markers in the array.
    function setAllMap(map,ary) {
        for (var i = 0; i < ary.length; i++) {
            ary[i].setMap(map);
        }

    }
    // helper function compare
    function compare(a,b) {
        if (a.id < b.id) return -1;
        if (a.id > b.id) return 1;
        return 0;
    }

    function setAgent(agent){
        var coord, heading, zoom;
        coord = agent.coordinate;
        zoom = 23 - agent.altitude;
    }




    /////////////////////////////// Layout ////////////////////////////////////
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
            console.log(ui.value.toFixed(1));
            $.post("/agents/" + selected_agent.getId(), {
                speed: ui.value.toFixed(1)
            });
            $.post("/logger",{actor:"bronze", msg:"SpeedChanged:AUTO,id:"+selected_agent.id+",speed:"+ui.value});
        }
    });
    $("#speed_slider").mouseover(function() {
        $("#speed_slider").data("ishover", true);

    }).mouseout(function() {
        $("#speed_slider").data("ishover", false);
    });


    var slider_auto_al = $("#altitude_slider").slider({
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
            $.post("/agents/" + selected_agent.id, {
                altitude: ui.value
            });
            $.post("/logger",{actor:"bronze", msg:"AltitudeChanged:AUTO,id:"+selected_agent.id+",altitude:"+ui.value});
        }
    });
    $("#altitude_slider").mouseover(function() {
        $("#altitude_slider").data("ishover", true);
    }).mouseout(function() {
        $("#altitude_slider").data("ishover", false);
    });

    var slider_manu_al = $("#altitude_slider_manual").slider({
        min: 0,
        max: 6,
        step: 1,
        value: 3,
        range: "min",
        animate: true,
        orientation: "vertical",

        slide: function(event, ui) {
            $("#altitude_value_manu").text(ui.value);
        },
        stop: function(event, ui) {
            console.log(connected);
            $.post("/agents/" + selected_agent.id, {
                altitude: ui.value
            });
            $.post("/logger",{actor:"bronze", msg:"AltitudeChanged:TELEOPE,id:"+selected_agent.id+",altitude:"+ui.value});
            if(connected){
                var alt = ui.value/10;
                update_ros(null, null, alt);
            }
        }
    });
    $("#altitude_slider_manual").mouseover(function() {
        $("#altitude_slider_manual").data("ishover", true);
    }).mouseout(function() {
        $("#altitude_slider_manual").data("ishover", false);
    });


    JoyStick('#joystick1', 280, function(magnitude, theta, ximpulse, yimpulse) {
        var speed, angle;
        if( magnitude > 100 ){
            speed = 10.0;
        }else{
            speed = magnitude/10;
        }
        if(angle>180){
            angle = - (360.0 - theta);
        }else{
            angle = theta;
        }

        joystick_cb(speed, theta)
        speed_manu = speed.toFixed();

        // for ros
        if(connected){
            var velocity = speed/10;
            update_ros(velocity, angle, null);
        }
    });

    var timer_id;
    function joystick_cb(linear, angular){
        this.teleop = this.teleop || {};

        this.teleop.linear = linear;
        this.teleop.angular = angular;

        if (linear === 0) {
            if (this.teleop.timer) {
                window.clearInterval(this.teleop.timer);
                this.teleop.timer = null;
                console.log("stop");
            }
        } else {
            if (!this.teleop.timer) {
                var self = this;
                this.teleop.timer =
                    window.setInterval(function() {
                        if (self.teleop.linear !== 0) {
                            $.post("/teleop", {
                                id: selected_agent.id,
                                linear: self.teleop.linear,
                                angular: self.teleop.angular });
                            $.post("/logger", {actor:"bronze",msg:"teleopeParam:"+selected_agent.id+",linear:"+self.teleop.linear+",angular:"+self.teleop.angular});
                        }
                        if(connected){
                            var velocity = linear/10;
                            update_ros(velocity, angular, null);
                        }
                    }, 400);
            }
        }
    }

    //mode button
    $("#myonoffswitch_c").change(function(e) {
        var alt = selected_agent.altitude;
        if($("#myonoffswitch_c").is(':checked')){
            $("#auto").show();
            $("#manual").hide();
            $.post("/agents/" + selected_agent.getId(), {
                mode: "auto"
            })
            $.post("/logger",{actor:"bronze", msg:"mode:AUTO,id:"+selected_agent.id});
            notify_ros("auto");
            $("#altitude_value").text(alt);
            slider_auto_al.slider("value", alt);
        }else{
            $("#auto").hide();
            $("#manual").show();
            $.post("/agents/" + selected_agent.getId(), {
                mode: "teleop"
            })
            $.post("/logger",{actor:"bronze", msg:"mode:TELEOPE,id:"+selected_agent.id});
            notify_ros("teleop");
            $("#altitude_value_manu").text(alt);
            slider_manu_al.slider( "value", alt );
        }
    });

    // $("#spi_agent_id_c").change(function(){
    // 	var value = $('option:selected').text();
    // 	_.each(data.agents, function(agent){
    // 		if(agent.id == value){
    // 			selected_agent = agent;
    // 			update_target();
    // 		}
    // 	});
    // });


});
