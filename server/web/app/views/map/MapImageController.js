var MapImageController = {
    /**
     * Binds all the methods to use the given context.
     *  This means the methods can be called just using MapAgentController.method() without
     *  having to worry about calling from the correct context.
     * @param context - Context to bind to.
     */
    bind: function (context) {
        this.bindEvents = _.bind(this.bindEvents, context);
        this.showImage = _.bind(this.showImage, context);
    },
    /**
     * Bind listeners for agent state add, change and remove events
     */
    bindEvents: function () {

    },
    /**
     * Request the next image reference from the model
     * @param type Whether this is a shallow or deep search
     * @param target Either the targetID or location (TBD)
     */
    requestImage: function (type, target) {

    },
    showImage: function (task) {
        // TODO pass this to be handled by the ImageController, with the result to be conveyed to the
        //  TargetController for UI changes
        self = this;
        //alert("si")
        try {
            var target = MapTargetController.getTargetAt(task.getPosition())
            if (target != null) {
                var images = self.state.getStoredImages();
                var iRef = images[target.getId()]

                var myWindow = window.open("", "", "width=800,height=600");
                myWindow.document.write("<img src=" + iRef + ">");

            }
        } catch (e) {
            alert("er: " + e)
        }

    }
};
