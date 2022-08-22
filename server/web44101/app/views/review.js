_.provide("App.Views.Review");


App.Views.Review = Backbone.View.extend({
    initialize: function(options) {
        this.state = options.state;
        this.views = options.views;
        this.canvas = options.canvas;
        this.ctx = options.ctx;

        this.currentTargetId = "";

        this.originalWidth = 0;
        this.originalHeight = 0;

        this.scale = 1;

        this.render();

        this.bind("update", this.update);

    },
    update: function() {

    },
    reset: function () {
        this.currentTargetId = "";

        this.originalWidth = 0;
        this.originalHeight = 0;

        this.scale = 1;
        this.clearImage();
        this.render();
    },
    displayImage: function (id, update) {
        if (update) {
            this.currentTargetId = id;
        }
        // TODO:
        //  1. Get the knownImages and knownText arrays (This will require backend changes too
        //  2. Determine the number and pick. Probably 1 vs 3x2 etc. Pick one of 2 or 3 methods to run this
        var knownImages = this.state.getTargetData();
        var data = knownImages[id]
        var self = this;
        var totalWidth = 1920;
        var totalHeight = 1080;
        try {
            console.log(data)
            if (data.length === 1) {
                // We need to determine what type of data this is
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
                    self.scale += 0.2;
                    $("#image_review_canvas").width(self.originalWidth * self.scale);
                    $("#image_review_canvas").height(self.originalHeight * self.scale);
                }

                self.canvas.width = totalWidth;
                self.canvas.height = totalHeight;
                $("#image_review_canvas").css({top: 0, left: 0, position: 'relative'});

                if (data[0].includes(".png")) {
                    // IMAGE
                    var iRef = data[0];
                    var img = new Image();
                    img.onload = function () {
                        self.ctx.lineWidth = 3;
                        self.ctx.drawImage(img, 0, 0, img.width, img.height);
                        self.ctx.strokeRect(0, 0, img.width, img.height);
                    };
                    img.src = iRef;  // Use the argument, so it works regardless of update flag
                } else {
                    try {
                        // We assume text
                        var text = data[0];

                        self.ctx.font = "bold 36px Helvetica, Arial, sans-serif";
                        console.log("canvas dims = " + self.canvas.width + " x " + self.canvas.height)

                        var splitText = text.split('\n')
                        for (var i = 0; i<splitText.length; i++) {
                            self.ctx.fillText(splitText[i], self.canvas.width / 6, (self.canvas.height / 6) + (i*36), (self.canvas.width - (2 * (self.canvas.width / 6))));
                            self.ctx.strokeRect(0, 0, self.canvas.width, self.canvas.height);
                        }

                        //self.ctx.fillText(textString, 25, 25, 250, 100);

                    } catch (e) {
                        alert(e);
                    }
                }

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
                    self.scale += 0.2;
                    $("#image_review_canvas").width(self.originalWidth * self.scale);
                    $("#image_review_canvas").height(self.originalHeight * self.scale);
                }

                self.canvas.width = totalWidth;
                self.canvas.height = totalHeight;
                $("#image_review_canvas").css({top: 0, left: 0, position: 'relative'});

                // 0->(0,0); 1->(1,0); 2->(0,1); 3->(1,1)
                // Even -> x=1
                // > 1 -> y=2
                for (let i = 0; i < 4; i++) {
                    let x = i % 2;
                    let y = 0;
                    if (i > 1) {
                        y = 1;
                    }

                    if (data[i].includes(".png")) {
                        console.log("i = " + i);
                        let img = new Image();
                        img.onload = function () {
                            self.ctx.lineWidth = 3;
                            self.ctx.drawImage(img, (self.canvas.width / 2) * x, (self.canvas.height / 2) * y, self.canvas.width / 2, self.canvas.height / 2);
                            self.ctx.strokeRect((self.canvas.width / 2) * x, (self.canvas.height / 2) * y, self.canvas.width / 2, self.canvas.height / 2);
                            console.log("i = " + i + "placing at " + x + ", " + y)
                        };
                        console.log("img.src = " + data[i] + " img = " + img)
                        img.src = data[i];  // Use the argument, so it works regardless of update flag

                    } else {
                        self.ctx.font = "bold 36px Helvetica, Arial, sans-serif";
                        // We assume text
                        var text = data[i];
                        var splitText = text.split('\n')
                        for (var l = 0; l < splitText.length; l++) {
                            // We use dim/5 to indent and approximately centre
                            self.ctx.fillText(splitText[l], (self.canvas.width / 8) + ((self.canvas.width / 2) * x), (self.canvas.height / 8) + ((self.canvas.height / 2) * y) + (l * 36), ((self.canvas.width / 2) - (2 * (self.canvas.width / 8))));
                            self.ctx.strokeRect((self.canvas.width / 2) * x, (self.canvas.height / 2) * y, self.canvas.width / 2, self.canvas.height / 2);
                        }
                    }
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
                    self.scale += 0.2;
                    $("#image_review_canvas").width(self.originalWidth * self.scale);
                    $("#image_review_canvas").height(self.originalHeight * self.scale);
                }

                self.canvas.width = totalWidth;
                self.canvas.height = totalHeight;
                $("#image_review_canvas").css({top: 0, left: 0, position: 'relative'});

                // 0->(0,0); 1->(1,0); 2->(0,1); 3->(1,1)
                // Even -> x=1
                // > 1 -> y=2
                var first = true;
                for (let i = 0; i < 6; i++) {
                    let x = i % 3;
                    let y = 0;
                    if (i > 2) {
                        y = 1;
                    }

                    if (data[i].includes(".png")) {
                        console.log("i = " + i);
                        let img = new Image();
                        img.onload = function () {
                            self.ctx.lineWidth = 3;
                            self.ctx.drawImage(img, (img.width / 3) * x, (img.height / 3) * y, img.width / 3, img.height / 3);
                            self.ctx.strokeRect((img.width / 3) * x, (img.height / 3) * y, img.width / 3, img.height / 3);
                            console.log("i = " + i + "placing at " + x + ", " + y)
                        };
                        console.log("img.src = " + data[i] + " img = " + img)
                        img.src = data[i];  // Use the argument, so it works regardless of update flag
                    } else {
                        self.ctx.font = "bold 36px Helvetica, Arial, sans-serif";
                        // We assume text
                        var text = data[i];
                        var splitText = text.split('\n')
                        for (var l = 0; l < splitText.length; l++) {
                            // We use dim/5 to indent and approximately centre
                            self.ctx.fillText(splitText[l], (self.canvas.width / 10) + ((self.canvas.width / 3) * x), (self.canvas.height / 10) + ((self.canvas.height / 3) * y) + (l * 36), ((self.canvas.width / 3) - (2 * (self.canvas.width / 10))));
                            self.ctx.strokeRect((self.canvas.width / 3) * x, (self.canvas.height / 3) * y, self.canvas.width / 3, self.canvas.height / 3);
                        }
                    }

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
