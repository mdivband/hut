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
            //alert("got targ = " + target)
            if (target != null) {
                //$.post("/targets/requestImage" + target.getId());
                var images = self.state.getStoredImages();
                //alert("images = " + images)
                //alert("Attempting to get image for id = " + target.getId());
                var iRef = images[target.getId()]


                //var img = loadImage('web/images/tempHighResFP.jpg'); // Load the image
                // TODO display the image, probably in a popup. It would be ideal to freeze execution too

                var myWindow = window.open("", "", "width=800,height=600");
                myWindow.document.write("<img src=" + iRef + ">");

                //var ctx = newWin.document.getElementById('canvas').getContext('2d');
                //ctx.drawImage(img, 0, 0);

                //newWin.document.write("Hello, world!");
                //alert("got " + iRef)
            }
        } catch (e) {
            alert("er: " + e)
        }

    }
};
