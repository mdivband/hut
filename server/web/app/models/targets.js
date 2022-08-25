_.provide("App.Models.Target");
_.provide("App.Collections.Targets");

App.Models.Target = App.Models.MObject.extend({
    defaults: {
        type: null,
        visible: null
        //status: null
    },
    destroy: function() {
        this.trigger('destroy', this, this.collection); //Remove from collection without posting DELETE request
    },
    getType: function() {
        //alert("getting: " + this.get("type"))
        return this.get("type");
    },
    isVisible: function() {
        return this.get("visible");
    },
    //getStatus: function() {
    //    return this.get("status");
    //}

});

App.Collections.Targets = Backbone.Collection.extend({
    model: App.Models.Target,
    HUMAN: 0,
    ADJUSTABLE: 1,

    // Second set of constants just stored here for now
    //ADJ_UNKNOWN: 0,
    ADJ_DEEP_SCAN: 2,
    ADJ_SHALLOW_SCAN: 3,
    ADJ_DISMISSED: 4,
    ADJ_FOUND: 5

});

