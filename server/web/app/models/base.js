_.provide("App.Models.IdObject");
_.provide("App.Models.MObject");

/**
 * Base class for objects with an id.
 */
App.Models.IdObject = Backbone.Model.extend({
    defaults: {
        id: null
    },
    getId: function() {
        return this.get("id");
    }
});

/**
 * Base class for map objects - have a position.
 */
App.Models.MObject = App.Models.IdObject.extend({
    defaults: {
        coordinate: {
            latitude: 0.0,
            longitude: 0.0
        }
    },
    getPosition: function() {
        var coordinate = this.get("coordinate");
        return _.position(coordinate.latitude, coordinate.longitude);
    }
});
