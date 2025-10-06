_.provide("App.Models.Fire");
_.provide("App.Collections.Fires");

// The model for a single fire object
App.Models.Fire = App.Models.MObject.extend({
    defaults: {
        intensity: 1.0,
        visible: true,
        image: ""
    },
    destroy: function() {
        this.trigger('destroy', this, this.collection);
    },
    getIntensity: function() {
        return this.get("intensity");
    },
    isVisible: function() {
        return this.get("visible");
    },
    getImage: function() {
        return this.get("image");
    }
});

App.Collections.Fires = Backbone.Collection.extend({
    model: App.Models.Fire
});