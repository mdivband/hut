_.provide("App.Models.Hazard");
_.provide("App.Collections.Hazards");

App.Models.Hazard = App.Models.MObject.extend({
    defaults: {
        type: null
    },
    destroy: function() {
        this.trigger('destroy', this, this.collection); //Remove from collection without posting DELETE request
    },
    getType: function() {
        return this.get("type");
    }
});

App.Collections.Hazards = Backbone.Collection.extend({
    model: App.Models.Hazard,
    NONE: -1,
    FIRE: 0,
    DEBRIS: 1
});