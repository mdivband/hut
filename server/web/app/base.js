/**
 * Sets up utility functions and adds to jQuery prototype.
 *
 * Adds to both Underscore.js and jQuery by using
 *  _.mixin and $.extend respectively.
 * Adds to the jQuery prototype $.fn
 */

//Use Underscore.js mixin to add new methods to _
// https://underscorejs.org/#mixin
_.mixin({
    provide: function (namespace) {
        var variable = window;
        var namelist = namespace.split(".");
        for (var i = 0; i < namelist.length; ++i) {
            variable[namelist[i]] = variable[namelist[i]] || {};
            variable = variable[namelist[i]];
        }
    },
    inherits: function (childCtor, parentCtor) {
        function tempCtor() {
        };
        tempCtor.prototype = parentCtor.prototype;
        childCtor.superClass_ = parentCtor.prototype;
        childCtor.prototype = new tempCtor();
        childCtor.prototype.constructor = childCtor;
    },
    minmax: function (min, value, max) {
        if (value <= min)
            return min;
        if (value >= max)
            return max;
        return value;
    },
    position: function (lat, lng) {
        return new google.maps.LatLng(lat, lng);
    },
    coordinate: function (latLng) {
        return {
            latitude: latLng.lat(),
            longitude: latLng.lng()
        };
    },
    latlng2point: function (map, latLng) {
        var overlay = new google.maps.OverlayView();
        overlay.draw = function () {
        };
        overlay.setMap(map);

        var projection = overlay.getProjection();
        var point = projection.fromLatLngToContainerPixel(latLng);

        return point;
    },
    time: function () {
        return (new Date()).getTime();
    },
    /**
     * Compare two allocations to see if they are equal
     */
    compareAllocations: function (allocation1, allocation2) {
        var keys1 = Object.keys(allocation1);
        var keys2 = Object.keys(allocation2);
        if(keys1.length !== keys2.length)
            return false;
        for(var key1 in keys1) {
            var val1 = allocation1[key1];
            var val2 = allocation2[key1];
            if(val2 === undefined || val1 !== val2)
                return false;
        }
        return true;
    },
    convertToTime: function (timeInSecs) {
        var mins = Math.floor(timeInSecs/60);
        var secs = Math.floor(timeInSecs%60);

        if (mins < 10)
            mins = "0" + mins;
        if (secs < 10)
            secs = "0" + secs;

        return (mins + ":" + secs);
    },
    doPathsMatch: function (path1, path2) {
        if (path1.getLength() !== path2.getLength())
            return false;
        for (var i = 0; i < path1.getLength(); i++) {
            var pos1 = path1.getAt(i);
            var pos2 = path2.getAt(i);
            if(pos1.lat() !== pos2.lat() || pos1.lng() !== pos2.lng())
                return false;
        }
        return true;
    }
});

//Merge objects - jQuery.extend(target[, object1][, objectN])
// https://api.jquery.com/jquery.extend/
// No target object given -> target is jQuery object itself.
// TODO any particular reason why this is being used as well as _.mixin?
$.extend({
    fromTime: function (time) {
        var h = Math.floor(time / 3600);
        if (h < 0) h = 0;

        var m = Math.floor((time - 3600 * h) / 60);
        if (m < 0) m = 0;

        var s = Math.floor(time - 3600 * h - 60 * m);
        if (s < 0) s = 0;

        h = (h > 9) ? h : "0" + h;
        m = (m > 9) ? m : "0" + m;
        s = (s > 9) ? s : "0" + s;

        return h + ":" + m + ":" + s;
    },
    loadIcon: function (image, shadow, offsetx, offsety) {
        return {
            Image: new google.maps.MarkerImage(image,
                null, null, new google.maps.Point(offsetx, offsety)),
            Shadow: new google.maps.MarkerImage(shadow,
                null, null, new google.maps.Point(offsetx, offsety))
        };
    },
    //Block page and render given element in the centre of the page
    blockWithContent: function (content) {
        $.blockUI({
            message: content,
            css: {
                border: 'none',
                padding: '15px',
                backgroundColor: '#000',
                '-webkit-border-radius': '10px',
                '-moz-border-radius': '10px',
                '-ms-border-radius': '10px',
                '-o-border-radius': '10px',
                'border-radius': '10px',
                opacity: 0.8,
                color: '#fff',
                cursor: 'default'
            }
        });
    },
    popText: function (text, timeout, options) {
        if (timeout && timeout >= 0) {
            timeout = timeout * 1000;
        } else {
            timeout = false;
        }

        options = options || {};
        var type = options.type || 'alert';
        var layout = options.layout || 'bottomLeft';

        noty({
            text: text,
            timeout: timeout,
            type: type,
            layout: layout
        });
    }
});

//Add to jQuery prototype
$.fn.extend({
    toggleStyle: function (style, value1, value2) {
        if (this.data(style)) {
            this.css(style, value1);
            this.data(style, false);
        } else {
            this.css(style, value2);
            this.data(style, true);
        }
        return this.data(style);
    }
});