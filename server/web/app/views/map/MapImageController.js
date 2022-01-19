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
        this.classify = _.bind(this.classify, context);
        this.referDeep = _.bind(this.referDeep, context);
        this.getCurrentImageId = _.bind(this.getCurrentImageId, context);
        this.triggerImage = _.bind(this.triggerImage, context);
        this.reset = _.bind(this.reset, context);
    },
    /**
     * Bind listeners for agent state add, change and remove events
     */
    bindEvents: function () {
        $("#rev_real").on('click', function () {
            MapImageController.classify(true);
        });
        $("#rev_false").on('click', function () {
            MapImageController.classify(false);
        });
        $("#rev_deep").on('click', function () {
            MapImageController.referDeep();
        });
    },
    reset: function () {
        this.views.review.reset();
    },

    /**
     * Request the next image reference from the model
     * @param type Whether this is a shallow or deep search
     * @param target Either the targetID or location (TBD)
     */
    requestImage: function (type, target) {

    },
    showImage: function (task) {
        var self = this;
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
    },
    /**
     * This is just a passthrough method
     * @param id
     * @param iRef
     * @param update
     */
    triggerImage: function (id, iRef, update) {
        MapController.pushImage(id, iRef, update);
    },
    getCurrentImageId: function () {
        return this.views.review.currentImageName;
    },
    classify: function (status) {
        var img = MapController.getCurrentImage();
        $.post("/review/classify", {
            ref: img,
            status: status,
        });
        var tgtId = this.views.review.currentImageName;
        var marker = this.$el.gmap("get", "markers")[tgtId];
        var position = marker.getPosition();
        MapTargetController.clearReviewedTarget(marker);
        MapTargetController.placeEmptyTargetMarker(position, tgtId, status);
    },
    referDeep: function () {
        var tgtId = this.views.review.currentImageName;
        var marker = this.$el.gmap("get", "markers")[tgtId];

        var newId = "[" + tgtId + "]";
        var existingMarker = this.$el.gmap("get", "markers")[newId];

        // TODO If you reselect you can add this again, must fix
        if (!existingMarker) {
            marker.setOptions({labelContent: newId});
            MapTaskController.addDeepScanTask(marker.getPosition());
            var icon = self.icons.TargetDeepScan;
            marker.setIcon(icon.Image)
        } else {
            console.log("Already pressed")
        }

        MapController.clearReviewImage();
    }

};
