_.provide("App.Models.Task");
_.provide("App.Collections.Tasks");
_.provide("App.Collections.CompletedTasks");

App.Models.Task = App.Models.MObject.extend({
	defaults: {
        agents: null,
        group: null,
		priority: null,
        type: null,
		//Patrol and region tasks only
		points: null,
		//Region task only
		corners: null
	},
	destroy: function() {
        this.trigger('destroy', this, this.collection); //Remove from collection without posting DELETE request
    },
	getAgents: function() {
		return this.get("agents");
	},
	getGroup: function() {
		return this.get("group");
	},
	getPriority: function() {
        return this.get("priority");
    },
    getType: function() {
	    return this.get("type");
    },
	getPoints: function() {
        return this.get("points");
	},
	getCorners: function() {
		return this.get('corners');
	}
});

App.Collections.Tasks = Backbone.Collection.extend({
	model: App.Models.Task,
	url: "/tasks",
    TASK_WAYPOINT: 0,
    TASK_MONITOR: 1,
	TASK_PATROL: 2,
	TASK_REGION: 3,
	TASK_VISIT: 4
});

App.Collections.CompletedTasks = Backbone.Collection.extend({
    model: App.Models.Task
});