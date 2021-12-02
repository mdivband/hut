_.provide("App.Views.Images");


App.Views.Images = Backbone.View.extend({
    initialize: function(options) {
        this.state = options.state;
        this.views = options.views;
        //this.canvas = options.canvas;
        //this.ctx = options.ctx;
        this.viewButtons = document.getElementById("button_panel");
        this.addedIds = [];
        this.addedRefs = [];
        this.render();

        var self = this;
        this.state.on("change", function () {
            self.update();
        });

        this.bind("update", this.update);
        //this.bind("refresh", this.refresh);
    },
    setup: function() {

    },
    update: function() {
        //var myWindow = window.open("", "", "width=800,height=600");
        //myWindow.document.write("<img src=" + iRef + ">");
        var self = this;
        try {
            var knownImages = self.state.getStoredImages();
            this.state.targets.each(function (target) {
                var id = target.getId()
                var iRef = knownImages[id]
                if (iRef !== undefined) {
                    if (!self.addedIds.includes(id)) {
                        // It is yet to be added
                        var button = document.createElement("button");
                        button.id = id;
                        button.innerHTML = id;
                        button.className = "image_select_buttons";
                        self.viewButtons.append(button);

                        button.addEventListener("click", function (event) {
                            //alert(id + " => " + iRef)
                            var myWindow = window.open("", "", "width=1400,height=1000");
                            myWindow.document.write("<img src=" + iRef + ">");
                        });

                        self.addedIds.push(id)
                        self.addedRefs.push(iRef)
                    } else if (!self.addedRefs.includes(id)) {
                        // Update button
                        var button = document.getElementById(id);
                        button.remove()

                        var button = document.createElement("button");
                        button.id = id;
                        button.innerHTML = id;
                        button.className = "image_select_buttons";
                        self.viewButtons.append(button);

                        button.addEventListener("click", function (event) {
                            //alert(id + " => " + iRef)
                            var myWindow = window.open("", "", "width=1400,height=1000");
                            myWindow.document.write("<img src=" + iRef + ">");
                        });

                        self.addedRefs.push(iRef)
                    }

                }

            });
        } catch (e) {
            alert("eee : " + e)
        }

    },

});
