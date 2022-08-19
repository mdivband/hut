_.provide("App.Views.Review");


App.Views.Review = Backbone.View.extend({
    initialize: function(options) {
        this.state = options.state;
        this.views = options.views;
        this.canvas = options.canvas;
        this.ctx = options.ctx;

        this.currentImageName = "";
        this.currentImageRef = "";

        this.originalWidth = 0;
        this.originalHeight = 0;

        this.scale = 1;

        this.render();

        this.bind("update", this.update);

    },
    update: function() {

    },
    reset: function () {
        this.currentImageName = "";
        this.currentImageRef = "";

        this.originalWidth = 0;
        this.originalHeight = 0;

        this.scale = 1;
        this.clearImage();
        this.render();
    },
    displayImage: function (id, update) {
        if (update) {
            this.currentImageName = id;
        }
        // TODO:
        //  1. Get the knownImages and knownText arrays (This will require backend changes too
        //  2. Determine the number and pick. Probably 1 vs 3x2 etc. Pick one of 2 or 3 methods to run this
        var knownImages = this.state.getTargetData();
        var data = knownImages[id]
        var self = this;
        try {
            if (data.length === 1) {
                var iRef = data[0];
                self.scale = 1;
                if (self.originalHeight !== 0) {
                    // TODO check if it's a ref or a textual here
                    // Not the first run, so reset canvas
                    $("#image_review_canvas").width(self.originalWidth);
                    $("#image_review_canvas").height(self.originalHeight);
                }

                try {
                    var img = new Image();
                    img.onload = function() {

                        self.originalWidth = $("#image_review_canvas").width();
                        self.originalHeight = $("#image_review_canvas").height();

                        while ($("#image_review_canvas").width() < $("#image_review").width() || $("#image_review_canvas").height() < $("#image_review").height()) {
                            self.scale += 0.25;
                            $("#image_review_canvas").width(self.originalWidth * self.scale);
                            $("#image_review_canvas").height(self.originalHeight * self.scale);

                        }

                        self.canvas.width = img.width;
                        self.canvas.height = img.height;
                        $("#image_review_canvas").css({top: 0, left: 0, position:'relative'});
                        self.ctx.lineWidth = 3;
                        self.ctx.drawImage(img, 0, 0, img.width / 2, img.height / 2);
                        self.ctx.strokeRect(0, 0, img.width / 2, img.height / 2);
                    };
                    img.src = iRef;  // Use the argument, so it works regardless of update flag
                } catch (e) {
                    alert("update error: " + e)
                }

                /*
                var img = new Image();
                img.onload = function () {

                    self.originalWidth = $("#image_review_canvas").width();
                    self.originalHeight = $("#image_review_canvas").height();

                    while ($("#image_review_canvas").width() < $("#image_review").width() || $("#image_review_canvas").height() < $("#image_review").height()) {
                        self.scale += 0.25;
                        $("#image_review_canvas").width(self.originalWidth * self.scale);
                        $("#image_review_canvas").height(self.originalHeight * self.scale);

                    }

                    self.canvas.width = img.width;
                    self.canvas.height = img.height;
                    $("#image_review_canvas").css({top: 0, left: 0, position: 'relative'});
                    self.ctx.lineWidth = 3;
                    self.ctx.drawImage(img, 0, 0, img.width, img.height);
                    self.ctx.strokeRect(0, 0, img.width, img.height);
                };
                img.src = iRef;  // Use the argument, so it works regardless of update flag

                 */

            } else if (data.length === 4) {
                self.scale = 1;
                if (self.originalHeight !== 0) {
                    // TODO check if it's a ref or a textual here
                    // Not the first run, so reset canvas
                    $("#image_review_canvas").width(self.originalWidth);
                    $("#image_review_canvas").height(self.originalHeight);
                }

                self.originalWidth = $("#image_review_canvas").width();
                self.originalHeight = $("#image_review_canvas").height();

                while ($("#image_review_canvas").width() < $("#image_review").width() || $("#image_review_canvas").height() < $("#image_review").height()) {
                    self.scale += 0.25;
                    $("#image_review_canvas").width(self.originalWidth * self.scale);
                    $("#image_review_canvas").height(self.originalHeight * self.scale);

                }

                $("#image_review_canvas").css({top: 0, left: 0, position: 'relative'});

                // 0->(0,0); 1->(1,0); 2->(0,1); 3->(1,1)
                // Even -> x=1
                // > 1 -> y=2
                console.log("Canvas ready. DATA: ")
                console.log(data);
                var first = true;
                for (let i = 0; i < 4; i++) {
                    console.log("i = " + i);
                    let img = new Image();
                    img.onload = function () {

                        if (first) {
                            self.canvas.width = img.width;
                            self.canvas.height = img.height;
                            $("#image_review_canvas").css({top: 0, left: 0, position: 'relative'});
                            first = false;
                        }

                        self.ctx.lineWidth = 3;
                        var x = 0;
                        if (i % 2 === 1) {
                            x = 1;
                        }
                        var y = 0;
                        if (i > 1) {
                            y = 1;
                        }
                        self.ctx.drawImage(img, (img.width / 2) * x, (img.height / 2) * y, img.width / 2, img.height / 2);
                        self.ctx.strokeRect((img.width / 2) * x, (img.height / 2) * y, img.width / 2, img.height / 2);

                        console.log("i = " + i + "placing at " + x + ", " + y)
                    };
                    console.log("img.src = " + data[i] + " img = " + img)
                    img.src = data[i];  // Use the argument, so it works regardless of update flag

                }
            } else if (data.length === 6) {
                self.scale = 1;
                if (self.originalHeight !== 0) {
                    // TODO check if it's a ref or a textual here
                    // Not the first run, so reset canvas
                    $("#image_review_canvas").width(self.originalWidth);
                    $("#image_review_canvas").height(self.originalHeight);
                }

                self.originalWidth = $("#image_review_canvas").width();
                self.originalHeight = $("#image_review_canvas").height();

                while ($("#image_review_canvas").width() < $("#image_review").width() || $("#image_review_canvas").height() < $("#image_review").height()) {
                    self.scale += 0.25;
                    $("#image_review_canvas").width(self.originalWidth * self.scale);
                    $("#image_review_canvas").height(self.originalHeight * self.scale);

                }

                $("#image_review_canvas").css({top: 0, left: 0, position: 'relative'});

                // 0->(0,0); 1->(1,0); 2->(0,1); 3->(1,1)
                // Even -> x=1
                // > 1 -> y=2
                console.log("Canvas ready. DATA: ")
                console.log(data);
                var first = true;
                for (let i = 0; i < 6; i++) {
                    console.log("i = " + i);
                    let img = new Image();
                    img.onload = function () {

                        if (first) {
                            self.canvas.width = img.width;
                            self.canvas.height = img.height;
                            $("#image_review_canvas").css({top: 0, left: 0, position: 'relative'});
                            first = false;
                        }

                        self.ctx.lineWidth = 3;
                        var x = 0;
                        if (i % 3 === 0) {
                            x = 2;
                        } else if (i % 3 === 1) {
                            x = 0;
                        } else if (i % 3 === 2) {
                            x = 1;
                        }
                        var y = 0;
                        if (i > 3) {
                            y = 1;
                        }
                        self.ctx.drawImage(img, (img.width / 3) * x, (img.height / 3) * y, img.width / 3, img.height / 3);
                        self.ctx.strokeRect((img.width / 3) * x, (img.height / 3) * y, img.width / 3, img.height / 3);

                        console.log("i = " + i + "placing at " + x + ", " + y)
                    };
                    console.log("img.src = " + data[i] + " img = " + img)
                    img.src = data[i];  // Use the argument, so it works regardless of update flag

                }
            } else {
                alert("Unhandled")
            }

        } catch (e) {
            alert(e);
        }

        /*
        try {
            var img = new Image();
            img.onload = function() {

                self.originalWidth = $("#image_review_canvas").width();
                self.originalHeight = $("#image_review_canvas").height();

                while ($("#image_review_canvas").width() < $("#image_review").width() || $("#image_review_canvas").height() < $("#image_review").height()) {
                    self.scale += 0.25;
                    $("#image_review_canvas").width(self.originalWidth * self.scale);
                    $("#image_review_canvas").height(self.originalHeight * self.scale);

                }

                self.canvas.width = img.width;
                self.canvas.height = img.height;
                $("#image_review_canvas").css({top: 0, left: 0, position:'relative'});
                self.ctx.lineWidth = 3;
                self.ctx.drawImage(img, 0, 0, img.width / 2, img.height / 2);
                self.ctx.strokeRect(0, 0, img.width / 2, img.height / 2);
            };
            img.src = iRef;  // Use the argument, so it works regardless of update flag

            var img2 = new Image();
            img2.onload = function() {

                self.ctx.lineWidth = 3;
                self.ctx.drawImage(img2, img2.width / 2, img2.height / 2, img2.width / 2, img2.height / 2);
                self.ctx.strokeRect(img2.width / 2, img2.height / 2, img2.width / 2, img2.height / 2);
            };
            img2.src = iRef2;  // Use the argument, so it works regardless of update flag

        } catch (e) {
            alert("update error: " + e)
        }

         */

    },
    clearImage : function () {
        this.ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);
        this.render();
    },
});
