_.provide("App.Models.Target");
_.provide("App.Collections.Targets");

App.Models.Target = App.Models.MObject.extend({
    defaults: {
        type: null,
        visible: null
    },
    destroy: function() {
        this.trigger('destroy', this, this.collection); //Remove from collection without posting DELETE request
    },
    getType: function() {
        return this.get("type");
    },
    isVisible: function() {
        return this.get("visible");
    }
});

App.Collections.Targets = Backbone.Collection.extend({
    model: App.Models.Target,
    HUMAN: 0
});