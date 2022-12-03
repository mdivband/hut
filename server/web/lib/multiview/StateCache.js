function StateCache() {
    this._cache = {};
}

StateCache.prototype = {

    get: function() {
        return this._cache || undefined;
    },
    set: function(v) {
        this._cache = v;
        return v;
    },

    clear: function() {
        delete this._cache;
    },

    update: function() {
        var self = this; // Because the scope will change
        $.getJSON("/state.json", function(data) {
            self.set(data);
        }).done(function() {
            //console.log("State cache updated successfully.");
            //console.log(this._cache);
        }).fail(function() {
            console.log("Failed to update state cache from server.");
        }).always(function() {
            setTimeout(function() {
                self.update()
            }, 100);
        });
    },

    start: function() {
        var foo = this;
        this.update();
    }
};