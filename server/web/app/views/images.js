_.provide("App.Views.Images");


App.Views.Images = Backbone.View.extend({
    initialize: function(options) {
        this.state = options.state;
        this.views = options.views;
        this.viewButtons = document.getElementById("button_panel");
        this.addedIds = [];
        this.addedRefs = [];
        this.pendingIds = [];
        this.render();

        var self = this;

        this.state.on("change", function () {
            self.update();
        });

        this.state.on("change:storedImages", function () {
            self.checkForRemoval();
        });

        this.checkAndUpdateDeepButton();

        this.bind("update", this.update);
    },
    setup: function() {

    },
    reset: function () {
        this.addedIds = [];
        this.addedRefs = [];
        this.pendingIds = [];
        this.render();
    },
    update: function() {
        this.checkAndUpdateDeepButton();
        var self = this;
        try {
            var knownImages = self.state.getStoredImages();
            this.state.targets.each(function (target) {
                var id = target.getId()
                var iRef = knownImages[id]
                if (iRef !== undefined) {
                    if (!self.addedIds.includes(id) ) {
                        // It is yet to be added
                        var button = document.createElement("button");
                        button.id = id;
                        button.innerHTML = id;
                        button.className = "image_select_buttons";
                        self.viewButtons.append(button);

                        button.addEventListener("click", function (event) {
                            console.log("i 1")
                            if (!self.pendingIds.includes(id)) {
                                console.log("i 1.1")
                                MapImageController.triggerImage(id, iRef, true);
                                button.focus();
                                $("#rev_deep").removeClass("rev_buttons_greyed").addClass("rev_buttons");
                                $("#rev_deep").prop('disabled', false);
                                self.pendingIds.push(id);
                            } else {
                                console.log("i 1.2")
                                // Already awaiting a deep scan, only visually update
                                MapImageController.triggerImage(id, iRef, false);
                                $("#rev_deep").removeClass("rev_buttons").addClass("rev_buttons_greyed");
                                button.focus();
                                $("#rev_deep").prop('disabled', true);
                            }

                        });

                        self.addedIds.push(id)
                        self.addedRefs.push(iRef)
                    }
                    else if (!self.addedRefs.includes(iRef)) {
                        console.log("i 2")
                        // Update button
                        var button = document.getElementById(id);
                        button.remove()

                        var button = document.createElement("button");
                        button.id = id;
                        button.innerHTML = id;
                        button.className = "image_select_buttons";
                        self.viewButtons.append(button);
                        self.pendingIds.remove(id)
                        // TODO pendingids remove this

                        button.addEventListener("click", function (event) {
                            console.log("i 2.1")
                            MapImageController.triggerImage(id, iRef, true);
                            $("#rev_deep").removeClass("rev_buttons").addClass("rev_buttons_greyed");
                            button.focus();
                            $("#rev_deep").prop('disabled', true);
                        });

                        self.addedRefs.push(iRef)

                        try {
                            // If we are currently viewing this image, update it to deep
                            console.log(MapImageController.getCurrentImageId());

                            if (MapImageController.getCurrentImageId() === id) {
                                console.log(1)
                                MapImageController.triggerImage(id, iRef, true);
                                console.log(2)
                                $("#rev_deep").removeClass("rev_buttons").addClass("rev_buttons_greyed");
                                console.log(3)
                                $("#rev_deep").prop('disabled', true);
                                console.log(4)
                            }

                        } catch (e) {
                            console.log(e)
                        }
                    }
                }
            });
        } catch (e) {
            alert("eee : " + e)
        }

    },
    checkAndUpdateDeepButton: function () {
        if (this.state.getDeepAllowed()) {
            $("#rev_deep").show();
        } else {
            $("#rev_deep").hide();
        }
    },
    checkForRemoval: function () {
        try {
            var self = this;
            var lim = self.addedIds.length;
            if (lim > 0) {
                for (var i = 0; i < lim; i++) {
                    var found = false;
                    self.state.targets.each(function (target) {
                        if (target.getId() === self.addedIds[i]) {
                            found = true;
                        }
                    });
                    if (!found) {
                        // TODO Check this. I think we can just leave the iRef there. It becomes lost and I doubt this
                        //  will incur measurable memory leakage unless the number of tasks explodes (remember that
                        //  this array is only storing references
                        this.safeRemoveId(self.addedIds[i])
                    }

                }
            }

        } catch (e) {
            alert("cfr " + e)
        }
    },
    /**
     * This is essentially a manual trash collection on the array. Note the newIndex variable to remove gaps as it updates
     * @param id
     */
    safeRemoveId : function (id) {
        var self = this;
        var newArray = [];
        var newIndex = 0;
        for (var i = 0; i < self.addedIds.length; i++) {
            if (self.addedIds[i] !== id) {
                newArray[newIndex] = self.addedIds[i]
                newIndex++;
            } else {
                var button = document.getElementById(self.addedIds[i]);
                button.remove()
            }
        }
        this.addedIds = newArray;
        MapController.clearReviewImage();
        this.update();
    }
});
