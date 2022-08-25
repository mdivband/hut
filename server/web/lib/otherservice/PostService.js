
var PostService = function(){

	function pop_text(text, timeout, options) {
            if (timeout && timeout >= 0) {
                timeout = timeout * 1000;
            } else {
                timeout = false;
            }
            
            options = options || {};
            var type = options.type || 'alert';
            var layout = options.layout || 'bottomLeft';
            
            noty({ text: text, timeout: timeout, type: type, layout: layout });
        }  

	return {	
		postAO: function(targetid, value, game_id){
            console.log("targetid " + targetid + " value " + value + " gameid " + game_id);
	            $.post("http://holt.mrl.nott.ac.uk:49992/game/" + game_id + "/find_target", '{"target_id":' + targetid + ',"type_id":'+value+'}', function(str) {
	                 if (str) {
	                        var data = $.parseJSON(str);
	                                if (data.state === "ok") {
	                                    pop_text("Target-"+targetid + ": target submitted", 2, {type: "information"});
	                                } else if (data.state === "error") {
	                                    pop_text("Target-"+targetid + ": " + data.msg, 2, {type: "error"});
	                                }
	                            }
	                        }).fail(function() {
	                            pop_text(targetid + ": server failed", 2, {type: "error"});
	                });        
		 },
		postAONEW: function(targetid, value, lat, lng, game_id){
			$.post("http://holt.mrl.nott.ac.uk:49992/game/" + game_id + "/find_target", '{"target_id":' + targetid + ',"type_id":'+value+',"latitude":'+lat+',"longitude":'+lng +'}', function(str) {
          			if (str) {
                        var data = $.parseJSON(str);
                                if (data.state === "ok") {
                                    pop_text("New Target-"+targetid + ": a new target added.", 2, {type: "information"});
                                } else if (data.state === "error") {
                                    pop_text("Target-"+targetid + ": " + data.msg, 2, {type: "error"});
                                }
                            }
                        }).fail(function() {
                           pop_text(targetid + ": server failed", 2, {type: "error"});
                });       

		},
		postPrePROV:function(targetid, value, target_version, game_id, lat, lng){
			console.log("postPrePROV target: "+ targetid +" targetType: "+ value +"  version: "+target_version+" game_id: "+game_id+ "lat: "+lat+" lng: "+lng);
			var url = "https://provenance.ecs.soton.ac.uk/atomicorchid/prov/log_entries/"+game_id;
			var timestamp = Date.now();
			target_version++;
			var preprovjson = {
				"uavtask": {
					"id": targetid,
					"version": target_version,
					"type": value,
					"longitude": lng,
					"latitude": lat,
					"state": 3,
					"players": ""
				},
				"ackid": 0,
				"time_stamp":timestamp
			};
			var strjson = JSON.stringify(preprovjson);

			$.post(url, JSON.stringify(preprovjson), function(str){
				console.log(str);
			});
		},
		initProv: function(api, commander, game_id){
			var d = prov.document();
			var ao = d.addNamespace("ao", "https://provenance.ecs.soton.ac.uk/atomicorchid/ns#");
			d.setDefaultNamespace("https://provenance.ecs.soton.ac.uk/atomicorchid/data/" + game_id + "/");
	 		var bundle = d.scope.getProvJSON();
			
			/// commnet out for logging with provenence ///
			// api.submitDocument("Operation " + game_id + "　UAV Controller", bundle, true,
		 //               	function() {
		 //               	  var new_document_id =  	
		 //                     $.post("/provdoc", { id: new_document_id });
		 //                     console.log("OK new prov_doc id is " +new_document_id + ". Operation id is "+ game_id) ;
		 //                },
		 //                    function(error) {
		 //                        console.error(error);
		 //                }
			// 	);
	/// end of commnet out for logging with provenence ///
	/// dammy log id
	$.post("/provdoc", { id: "operation" });
		},
		postTaskPROV: function(api, game_id, prov_doc, task_id, coordinate, waypoints, agent, group, priority, version, isNew){
			var commander = 'uav_silver_commander';
			var d = prov.document();
			var ao = d.addNamespace("ao", "https://provenance.ecs.soton.ac.uk/atomicorchid/ns#");
			d.setDefaultNamespace("https://provenance.ecs.soton.ac.uk/atomicorchid/data/" + game_id + "/");
			
			// Getting the timestamp to generate unique identifiers
	    		var now = new Date();
	    		var timestamp = now / 1000;
	    		var activity_id = 'activity/uav_create_task/' + timestamp;
			var operator_id = 'uav_sliver_commander';
	    		// if coordinate size = 1 then single task otherwise region
	    		var asset_type;
	    		if(coordinate.size==1){
	    			asset_type = "singletask";
	    		}else{
	    			asset_type = "region";
	    		}
	    		//TODO add type task/region, 
	    		var task, task_v0, task_v1;

	    		if(isNew){
	    			task = 'task/'+ task_id;
	    			task_v1 = "task/"+task_id+".0";
	    			d.entity('ao:asset_type', ao.qn(asset_type)); 
	    		}else{
	    			// add task version
	    		}
			d.agent(operator_id).attr('prov:type', ao.qn('UAVOperator')).attr('prov:type', prov.ns.qn('Person'));
	    		d.activity(activity_id, null, now);
	    		d.wasAssociatedWith(activity_id, operator_id);
	    		d.wasGeneratedBy(target_v1, activity_id);
	    		d.wasAttributedTo(target_v1, operator_id);
	 		var bundle = d.scope.getProvJSON(),
	        		bundle_identifier = 'bundle/uav_create_task/' + timestamp;
			api.addBundle(provdoc, bundle_identifier, bundle, 
	  			function(response) {

	  			}, 
	  			function(response) {
	  				console.log("postTaskPROV - ERROR")
	  			});

		},
		postPROV: function(api, target_id, value, target_version, game_id, provdoc, commander, isChange, isNew){
			var incident_types = {
			    0: 'WaterSource',
			    1: 'InfrastructureDamage',
			    2: 'MedicalEmergency',
			    3: 'Crime'
			};				
			console.log(isChange +" change and new " + isNew);
			var d = prov.document();
			var ao = d.addNamespace("ao", "https://provenance.ecs.soton.ac.uk/atomicorchid/ns#");
			d.setDefaultNamespace("https://provenance.ecs.soton.ac.uk/atomicorchid/data/" + game_id + "/");
			
			// Getting the timestamp to generate unique identifiers
	    		var now = new Date();
	    		var timestamp = now / 1000;
	    		var activity_id = 'activity/uav_verification/' + timestamp;
	    		// operator IDs
	    		var operator_id = commander;

	    		var target, target_v0, target_v1;
	    		var target_type_attribute;
	    		if(isNew){ //create new target from silver or bronze
  				target = 'target/' + target_id;
				target_v1 = 'uav/target/' + target_id + '.' + target_version;
				d.entity(target_v1, ['ao:asset_type', target_type_attribute]);
	    		}else if(isChange){ // RE-annotate target
		    		target = 'uav/target/' + target_id;
				target_v0 = 'uav/target/' + target_id + '.' + target_version;
				target_version++;	
				target_v1 = 'uav/target/' + target_id + '.' + target_version;
	    		}else{ // annotate target
		    		target = 'cs/target/' + target_id;
				target_v0 = 'cs/target/' + target_id + '.' + target_version;
				target_version++;	
				target_v1 = 'uav/target/' + target_id + '.' + target_version;
			}
			var activity_id = 'activity/uav_verification/' + timestamp;

			if(value != -1){ // if it is NOT invalid
				target_type_attribute = ao.qn(incident_types[value]); 
				d.entity(target_v1, ['ao:asset_type', target_type_attribute]);
			}else{ // if it is invalid 
				d.entity(target_v0);
				d.wasInvalidatedBy(target_v0, activity_id);
			}
			d.agent(operator_id).attr('prov:type', ao.qn('UAVOperator')).attr('prov:type', prov.ns.qn('Person'));
	    		d.activity(activity_id, null, now);
	    		d.wasAssociatedWith(activity_id, operator_id);
	    		d.wasGeneratedBy(target_v1, activity_id);
	    		if(!isNew){
	    			d.wasDerivedFrom(target_v1, target_v0);
	    			d.used(activity_id, target_v0);

		    		var actor,msg;		
				if(value ==-1){
					msg = "Target-Annotation,"+target_id+"-invalid";
				}else{
					msg = "Target-Annotation,"+target_id+"-"+incident_types[value];				
				}

				if(commander=="uav_silver_commander"){
					actor = "silver-ope"
				}else if(commander=="uav_silver_monitor_commander"){
					actor = "silver-monitor"
				}else{
					actor = "bronze"
				}
				$.post("/logger", {msg: msg, actor:actor});

	    		}
	    		d.wasAttributedTo(target_v1, operator_id);

	    		if(isChange){
	    			d.wasInvalidatedBy(target_v0, activity_id);
	    		}
	 		var bundle = d.scope.getProvJSON(),
	        		bundle_identifier = 'bundle/uav_verification/' + timestamp;

	    		if(provdoc == null){
	    			// instead of commander, use "Game13 UAV Controller" as the name
	    			api.submitDocument(commander, bundle, true,
		               	function(new_document_id) {
		                     $.post("/provdoc", { id: new_document_id });
		                     console.log("OK new prov_doc id is " +new_document_id)
		                },
		                    function(error) {
		                        console.error(error);
		                }
				);
	    		}else{
				api.addBundle(provdoc, bundle_identifier, bundle, 
		  			function(response) {
		  				//console.log(response);
		  			}, 
		  			function(response) {
		  				//console.log(response);
		  			});
    			}
		}
	};
	
};