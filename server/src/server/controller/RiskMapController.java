package server.controller;

import server.Simulator;

public class RiskMapController extends AbstractController{
    private String riskFileName = "server/web/HARIS-maps/FireRisk_Mean_ByFeature.geojson";


    public RiskMapController(Simulator simulator, String controllerName) {
        super(simulator, controllerName);
    }

    public void convertRiskMapToHeatmapForm() {
        // 1. Iterate through each item, and add it to a 2d array or similar
        //  each should have a lat, lng, and risk value, drawn from the "FIRE" value in the geojson

        // 2. Save this in some format we can easily use to add the heatmap in the frontend
        simulator.getState().setRiskMap(theNewMap);
        // ^ Note that I've assumed it to be a 2d array of doubles, but it may need to be dict with (lat, lng) -> Double or something like that

        // 3. Now the new riskmap is saved in state, so the frontend can use it.

    }



}
