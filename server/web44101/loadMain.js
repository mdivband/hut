$LAB.setOptions({
    CacheBust: true
})
    //Use LABjs to load javascript
    // ----- Libraries -----
    // JQuery API: http://api.jquery.com
    .script("lib/jquery/jquery-1.9.1.min.js")
    // JQuery UI: http://api.jqueryui.com
    .script("lib/jquery/ui/jquery-ui-1.10.1.min.js")
    // JQuery UI Layout: http://layout.jquery-dev.net
    .script("lib/jquery/layout/jquery.layout-1.3.0.min.js")
    // JQuery Touch Punch: http://touchpunch.furf.com
    .script("lib/jquery/touch/jquery.ui.touch-punch.min.js")
    // JQuery BlockUI: http://www.malsup.com/jquery/block/
    .script("lib/jquery/block/jquery.blockUI.js")
    // JQuery DataTables: http://www.datatables.net
    .script("lib/jquery/table/jquery.dataTables.min.js")
    // JQuery Noty: http://needim.github.io/noty
    .script("lib/jquery/noty/jquery.noty.js")
    .script("lib/jquery/noty/themes/default.js")
    .script("lib/jquery/noty/layouts/bottomLeft.js")
    // JQuery UI Google Map: code.google.com/p/jquery-ui-map/
    .script("lib/jquery/map/jquery.ui.map.js")
    .script("lib/jquery/map/jquery.ui.map.overlays.js")
    .script("lib/jquery/map/jquery.ui.map.services.js")
    .script("lib/jquery/map/jquery.ui.map.extensions.js")
    // JavaScript Vector Library: http://raphaeljs.com
    .script("lib/jquery/joystick/raphael.js")
    // JQuery Joystick: https://github.com/mattes/joystick-js/
    // .script("lib/jquery/joystick/joystick.jquery.js")
    // .script("lib/joystick/JoyStick.js")
    // .script("lib/joystick/jquery.ui.touch-punch.min.js")
    // GMap Utility API: http://google-maps-utility-library-v3.googlecode.com
    .script("lib/map/google/latlngtooltip.js")
    .script("lib/map/google/infobubble_packed.js")
    .script("lib/map/google/markerwithlabel_packed.js")
    .script("lib/map/google/markerclusterer_packed.js")
    .script("lib/map/google/maplabel.js")
    // Chart.js for drawing charts
    .script("lib/charts/Chart.js")
    // Underscore: http://underscorejs.org
    .script("lib/backbone/underscore-1.4.4.min.js")
    // Backbone: http://backbonejs.org
    .script("lib/backbone/backbone-0.9.10.min.js")
    // KineticJS: http://kineticjs.com
    .script("lib/kinetic/kinetic-v4.1.2.min.js")
    // // Toggle.js
    // .script("lib/jquery/toggles/togglemin.js")
    //Provenance and post
    .script("lib/prov/provapi.js")
    .script("lib/prov/prov.js")
    .script("lib/otherservice/PostService.js")
    //SmallPop http://silvio-r.github.io/spop/
    .script("lib/spop/spop.js")
    //Ros
    .script("lib/ros/eventemitter2.min.js")
    .script("lib/ros/roslib.min.js")
    //Heatmap
    .script("lib/heatmap/heatmap.js")
    .script("lib/heatmap/gmaps-heatmap.js")

    // ----- Add base functions -----
    .script("app/base.js")

    // ----- Devices -----
    .script("app/devices/ardrone.js")
    .script("app/devices/pfdview.js")

    // ----- Views -----
    .script("app/views/control.js")
    .script("app/views/layout.js")
    .script("app/views/graph.js")
    .script("app/views/camera.js")
    .script("app/views/map.js")
    .script("app/views/prediction.js")
    .script("app/views/map/MapController.js")
    .script("app/views/map/MapAgentController.js")
    .script("app/views/map/MapTaskController.js")
    .script("app/views/map/MapHazardController.js")
    .script("app/views/map/MapTargetController.js")

    // ----- Models -----
    .script("app/models/base.js")
    .script("app/models/agents.js")
    .script("app/models/ghosts.js")
    .script("app/models/tasks.js")
    .script("app/models/hazards.js")
    .script("app/models/targets.js")
    .script("app/models/state.js")

    // ----- Main -----
    .script("app/main.js")

    .wait(function () {
        $(document).ready(function () {
            window.app = simulator.init();
            $('body')[0].style.visibility = 'visible';
            $('body').addClass('ready');
        });
    });
