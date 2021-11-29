var MapImageController = {
    /**
     * Binds all the methods to use the given context.
     *  This means the methods can be called just using MapAgentController.method() without
     *  having to worry about calling from the correct context.
     * @param context - Context to bind to.
     */
    bind: function (context) {
        this.bindEvents = _.bind(this.bindEvents, context);
    },
    /**
     * Bind listeners for agent state add, change and remove events
     */
    bindEvents: function () {

    },
};
