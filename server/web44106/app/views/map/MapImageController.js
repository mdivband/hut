var MapImageController = {
    requestedIds : [],
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
        this.resetCurrentImageData = _.bind(this.resetCurrentImageData, context);
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
        this.views.images.reset()
    },

    /**
     * Request the next image reference from the model
     * @param type Whether this is a shallow or deep search
     * @param target Either the targetID or location (TBD)
     */
    requestImage: function (type, target) {

    },
    resetCurrentImageData: function () {
        return this.views.review.currentImageName = "";
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
        var self = this;
        try {
            var img = MapController.getCurrentImage();
            var tgtId = this.views.review.currentImageName;
            if (!MapTargetController.classifiedIds.includes(tgtId)) {
                MapTargetController.classifiedIds.push(tgtId);
                $.post("/review/classify", {
                    ref: img,
                    status: status,
                });
            }
            var marker = self.$el.gmap("get", "markers")[tgtId];
            if (marker) {
                var position = marker.getPosition();
                MapTargetController.clearReviewedTarget(marker);
                MapTargetController.placeEmptyTargetMarker(position, tgtId, status);

            }
        } catch (e) {
            alert("class : " + e)
        }
    },
    referDeep: function () {
        var self = this;
        var tgtId = this.views.review.currentImageName;

        if (!MapImageController.requestedIds.includes(tgtId)) {
            MapImageController.requestedIds.push(tgtId);
            var marker = self.$el.gmap("get", "markers")[tgtId];

            var newId = "[" + tgtId + "]";
            var existingMarker = self.$el.gmap("get", "markers")[newId];

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

    }

};
