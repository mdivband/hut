var MapHazardController = {
    heatmap: {},
    emptyHeatmap: null,
    visibleCount: 0,
    /**
     * Binds all the methods to use the given context.
     *  This means the methods can be called just using MapAgentController.method() without
     *  having to worry about calling from the correct context.
     * @param context - Context to bind to.
     */
    bind: function (context) {
        this.bindEvents = _.bind(this.bindEvents, context);
        this.addHeatmap = _.bind(this.addHeatmap, context);
        this.updateHeatmap = _.bind(this.updateHeatmap, context);
        this.setHeatmapVisibility = _.bind(this.setHeatmapVisibility, context)
    },
    bindEvents: function () {
        $('#explored_overlay_toggle').change(function () {
            MapHazardController.setHeatmapVisibility(-1, $(this).is(":checked"));
        });
        $('#hazard_overlay_toggle').change(function () {
            MapHazardController.setHeatmapVisibility(0, $(this).is(":checked"));
            MapHazardController.setHeatmapVisibility(1, $(this).is(":checked"));
        });

        //Add empty heat map - used to give consistent background colour when multiple heatmaps are active.
        MapHazardController.emptyHeatmap = new HeatmapOverlay(this.map,
            {
                // "backgroundColor": '#00000044'
                "backgroundColor": '#00000010'
            }
        );

        //Add heatmaps
        MapHazardController.addHeatmap(this.state.hazards.NONE, 'blue');
        MapHazardController.addHeatmap(this.state.hazards.FIRE, 'red');
        MapHazardController.addHeatmap(this.state.hazards.DEBRIS, 'black');

        //Make sure visible count is zero after maps are added and emtpty heatmap is not visible
        MapHazardController.visibleCount = 0;
        MapHazardController.emptyHeatmap.container.style.display = "none";
    },
    addHeatmap: function (hazardType, colour) {
        if (hazardType in MapHazardController.heatmap)
            console.log("Cannot add heatmap - heatmap already existing for hazard type " + hazardType);
        else {
            MapHazardController.heatmap[hazardType] = new HeatmapOverlay(this.map,
                {
                    "radius": 0.0005,
                    // "maxOpacity": 0.4,
                    "maxOpacity": 0.6,
                    "scaleRadius": true,
                    "useLocalExtrema": false,
                    "gradient": {
                        '0': 'black',
                        '1': colour
                    },
                    latField: 'latitude',
                    lngField: 'longitude',
                    valueField: 'weight'
                }
            );
            MapHazardController.setHeatmapVisibility(hazardType, false);
        }
    },
    updateHeatmap: function (hazardType) {
        var data = this.state.getHazardHits(hazardType).map(function(hit) {
            var hitObj = {};
            $.extend(hitObj, hit.location, {'weight': hit.weight});
            return hitObj;
        });
        MapHazardController.heatmap[hazardType].setData({data: data, max:1, min:0});
    },
    setHeatmapVisibility: function (hazardType, visible) {
        MapHazardController.heatmap[hazardType].container.style.display = visible ? "block" : "none";

        MapHazardController.visibleCount += visible ? 1 : -1;
        if(visible)
            MapHazardController.emptyHeatmap.container.style.display = "block";
        else if(MapHazardController.visibleCount === 0)
            MapHazardController.emptyHeatmap.container.style.display = "none";
    }
};
