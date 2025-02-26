package server.controller;

import com.google.gson.internal.StringMap;
import server.Simulator;
import tool.GsonUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

public class RiskMapController extends AbstractController{
    private String riskFileName;// = "web/HARIS-maps/FireRisk_Mean_ByFeature.geojson";


    public RiskMapController(Simulator simulator) {
        super(simulator, RiskMapController.class.getName());
    }

    public void convertRiskMapToHeatmapForm() {
        // TODO: A 2D array is fine for now, but unclear
        ArrayList<ArrayList<Double>> riskHeatMap = new ArrayList<ArrayList<Double>>();

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

                ArrayList<Double> newPlace = new ArrayList<Double>();

                // average LR and TB to get center
                newPlace.add(0.5*(left+right));
                newPlace.add(0.5*(top+bottom));
                newPlace.add(fire);
                riskHeatMap.add(new ArrayList<Double>(newPlace));
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        simulator.getState().setRiskMap(riskHeatMap);
    }


    public void convertRiskMapToHexBinForm() {
        // TODO: A 2D array is fine for now, but unclear
        // NOTE: hexagon sizes are *inconsistent* in the data so calculating
        // them based on purely their center would be inaccurate. Therefore
        // we will need to record the coordinates of the hexagon fully
        ArrayList<ArrayList<Double>> riskHeatMap = new ArrayList<ArrayList<Double>>();

        double FIRE = 0.210384;
        double Cities = 0.063707;
        double NDVI = 0.091138;
        double Roads = 0.076670;
        double Trails = 0.148578;
        double aspect = 0.126356;
        double elevation = 0.107507;
        double slope = 0.175661;

        try {
            String json = GsonUtils.readFile(this.riskFileName);
            Object obj = GsonUtils.fromJson(json);
            ArrayList features = GsonUtils.getValue(obj, "features");

            for(Object loc : features){
                Object properties = GsonUtils.getValue(loc, "properties");
                Object geometry = GsonUtils.getValue(loc, "geometry");
                if(!GsonUtils.hasKey(properties, "FIRE")) continue;

                // annoyingly wrapped in a single value array
                ArrayList<ArrayList<ArrayList<Double>>> coordinates = GsonUtils.getValue(geometry, "coordinates");

                ArrayList<Double> newPlace = new ArrayList<Double>();

                for(ArrayList<Double> point : coordinates.get(0)){
                    newPlace.add(point.get(0));
                    newPlace.add(point.get(1));
                }
                Double fire = GsonUtils.getValue(properties, "FIRE");
                newPlace.add(fire);
                riskHeatMap.add(new ArrayList<Double>(newPlace));
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
