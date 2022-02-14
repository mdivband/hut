_.provide("App.Models.State");

App.Models.State  = Backbone.Model.extend({
    GAME_TYPE_SANDBOX: 0,
    GAME_TYPE_SCENARIO: 1,
	defaults: {
		time: 0,
		allocation: {},
        tempAllocation: {},
        droppedAllocation: {},
        editMode: 1,
        inProgress: false,
		gameId: null,
        gameType: null,
        gameDescription: null,
        gameCentre: {
            latitude: 0.0,
            longitude: 0.0
        },
		prov_doc: null,
        allocationUndoAvailable: false,
        allocationRedoAvailable: false,
        hazardHits: {
            '-1': [],
            '0': [],
            '1': []
        },
        uiOptions: {},
        uncertaintyRadius: 0,
        storedImages : {},
        deepScannedIds: {},
        pendingIds: {},
        passthrough: false,
        nextFileName: "",
        deepAllowed: false,
        timeLimit: 0,
        userName: "",
        markers: []
	},
    url: function() {
       return "state.json?" + _.time();
    },
    initialize: function() {
        this.agents = new App.Collections.Agents();
        this.tasks = new App.Collections.Tasks();
        this.completedTasks = new App.Collections.CompletedTasks();
        this.hazards = new App.Collections.Hazards();
        this.targets = new App.Collections.Targets();
    },
    parse: function(resp) {
	    //Pass lists straight onto collections
        // Means stuff doesn't need to be kept in sync.
        // See https://github.com/jashkenas/backbone/issues/56#issuecomment-15646745
		// remove:false prevents firing of removed events (might not be wanted)
		//  but also prevents DELETE HTTP requests being sent (probably wanted!).
        this.agents.update(resp.agents, {parse:true});
        this.tasks.update(resp.tasks, {parse:true});
        this.completedTasks.update(resp.completedTasks, {parse:true});
        this.hazards.update(resp.hazards, {parse:true});
        this.targets.update(resp.targets, {parse:true});

        delete resp.agents;
        delete resp.tasks;
        delete resp.completedTasks;
        delete resp.hazards;
        delete resp.targets;

        return resp;
    },
    toJSON: function() {
	    //Because collections aren't kept in attributes, they need adding back in
        // on serialisation.
        // See https://github.com/jashkenas/backbone/issues/56#issuecomment-15646745
        var attrs = _.clone(this.attributes);
        attrs.agents = this.agents.toJSON();
        attrs.tasks = this.tasks.toJSON();
        attrs.completedTasks = this.completedTasks.toJSON();
        attrs.hazards = this.hazards.toJSON();
        attrs.targets = this.targets.toJSON();
        return attrs;
    },
	getProvDoc: function(){
		return this.get("prov_doc");
	},
	getGameId: function() {
		return this.get("gameId");
	},
    getGameType: function() {
	    return this.get("gameType");
    },
    getGameCentre: function() {
        return this.get("gameCentre");
    },
    getGameDescription: function() {
        return this.get("gameDescription");
    },
	getTime: function() {
		return this.get("time");
	},
	getAllocation: function() {
		return this.get("allocation");
	},
    getTempAllocation: function() {
	    return this.get("tempAllocation");
    },
    getDroppedAllocation: function() {
	    return this.get("droppedAllocation");
    },
    getHazardHits: function(type) {
        return this.get("hazardHits")[type];
    },
	getEditMode:function(){
		return this.get("editMode");
	},
    isAllocationUndoAvailable: function (){
	    return this.get("allocationUndoAvailable");
    },
    isAllocationRedoAvailable: function (){
        return this.get("allocationRedoAvailable");
    },
    isInProgress: function (){
        return this.get("inProgress");
    },
    pushMode: function(modeFlag) {
        // modeflag 1 = monitor
        //          2 = edit
        //          3 = images
        if (modeFlag === 2) {
            this.set("editMode", 2);
            $("#map_title").html("Edit Mode");
        } else if (modeFlag === 1) {
            this.set("editMode", 1);
            $("#map_title").html("Monitor Mode");
        }

        // TODO Swithcing modes causes issues, probably due to backend not handling it correctly

        else if (modeFlag === 3) {
            this.set("editMode", 3);
            $("#map_title").html("Image Review");
        }

		 $.post("/changeview", {edit: modeFlag});

	},
    getUiOptions: function () {
        return this.get("uiOptions");
    },
    getUncertaintyRadius: function () {
        return this.get("uncertaintyRadius");
    },
    getStoredImages: function () {
        return this.get("storedImages")
    },
    getDeepScannedIds: function () {
        return this.get("deepScannedIds");
    },
    isPassthrough: function () {
        return this.get("passthrough");
    },
    getNextFileName: function () {
        return this.get("nextFileName");
    },
    getDeepAllowed: function () {
        return this.get("deepAllowed");
    },
    getTimeLimit: function () {
        // TODO this doesn't use the actual gamespeed
        return this.get("timeLimit") * 6;
    },
    getUserName: function () {
        return this.get("userName");
    },
    getMarkers: function () {
        return this.get("markers");
    },
    getPendingIds: function () {
        return this.get("pendingIds");
    }
});