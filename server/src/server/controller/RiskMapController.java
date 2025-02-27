package server.controller;

import com.google.gson.internal.StringMap;
import server.Simulator;
import tool.GsonUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public class RiskMapController extends AbstractController{
    private String riskFileName;// = "web/HARIS-maps/FireRisk_Mean_ByFeature.geojson";


    public RiskMapController(Simulator simulator) {
        super(simulator, RiskMapController.class.getName());
    }

    public void convertRiskMapToHeatmapForm() {
        // TODO: A 2D array is fine for now, but unclear
        ArrayList<HashMap<String, ArrayList<Double>>> riskHeatMap = new ArrayList<HashMap<String, ArrayList<Double>>>();

        try {
            String json = GsonUtils.readFile(this.riskFileName);
            Object obj = GsonUtils.fromJson(json);
            ArrayList features = GsonUtils.getValue(obj, "features");

            for(Object loc : features){
                Object properties = GsonUtils.getValue(loc, "properties");
                if(!GsonUtils.hasKey(properties, "FIRE")) continue;

                Double left = GsonUtils.getValue(properties, "left");
                Double right = GsonUtils.getValue(properties, "right");
                Double top = GsonUtils.getValue(properties, "top");
                Double bottom = GsonUtils.getValue(properties, "bottom");
                Double fire = GsonUtils.getValue(properties, "FIRE");

                HashMap<String, ArrayList<Double>> newPlace = new HashMap<String, ArrayList<Double>>();

                // average LR and TB to get center

                newPlace.put("coordinates", new ArrayList<Double>(Arrays.asList(0.5*(left+right),0.5*(top+bottom))));
                newPlace.put("FIRE", new ArrayList<Double>(Arrays.asList(fire)));
                riskHeatMap.add(newPlace);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        simulator.getState().setRiskMap(riskHeatMap);
    }


    public void convertRiskMapToHexBinForm() {
        // TODO: A 2D array is fine for now, but VERY unclear
        // NOTE: hexagon sizes are *inconsistent* in the data so calculating
        // them based on purely their center would be inaccurate. Therefore
        // we will need to record the coordinates of the hexagon fully
        ArrayList<HashMap<String, ArrayList<Double>>> riskHeatMap = new ArrayList<HashMap<String, ArrayList<Double>>>();

        HashMap<String,Double> features = new HashMap<String,Double>();
        features.put("FIRE"     ,0.210384);
        features.put("Cities"   ,0.063707);
        features.put("NDVI"     ,0.091138);
        features.put("Roads"    ,0.076670);
        features.put("Trails"   ,0.148578);
        features.put("aspect"   ,0.126356);
        features.put("elevation",0.107507);
        features.put("slope"    ,0.175661);

        try {
            String json = GsonUtils.readFile(this.riskFileName);
            Object obj = GsonUtils.fromJson(json);
            ArrayList attributes = GsonUtils.getValue(obj, "features");

            for(Object loc : attributes){
                Object properties = GsonUtils.getValue(loc, "properties");
                Object geometry = GsonUtils.getValue(loc, "geometry");

                boolean skip = false;
                for(String key : features.keySet()){
                    if(!GsonUtils.hasKey(properties, key)) {
                        skip = true;
                        break;
                    }
                }
                if(skip) continue;



                // annoyingly wrapped in a single value array
                ArrayList<ArrayList<ArrayList<Double>>> coordinates = GsonUtils.getValue(geometry, "coordinates");

                HashMap<String, ArrayList<Double>> newPlace = new HashMap<String, ArrayList<Double>>();
                newPlace.put("coordinates", new ArrayList<Double>());

                for(ArrayList<Double> point : coordinates.get(0)){
                    newPlace.get("coordinates").add(point.get(0));
                    newPlace.get("coordinates").add(point.get(1));
                }
                for(String feature : features.keySet()){
                    Double value = GsonUtils.getValue(properties, feature);
                    newPlace.put(feature, new ArrayList<Double>(Arrays.asList(value)));
                }
                riskHeatMap.add(newPlace);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        simulator.getState().setRiskMap(riskHeatMap);
    }


    public void setFireRiskFile(String fireRiskFile) {
        this.riskFileName = "web/HARIS-maps/"+fireRiskFile;
    }
}
