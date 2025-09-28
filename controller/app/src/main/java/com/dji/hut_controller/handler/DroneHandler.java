package com.dji.hut_controller.handler;

import android.os.Bundle;
import android.util.Log;

import com.dji.hut_controller.DJIHutApplication;
import com.dji.hut_controller.model.Coordinates;

import java.util.Timer;
import java.util.TimerTask;

import dji.sdk.FlightController.DJIFlightController;
import dji.sdk.FlightController.DJIFlightControllerDataType;
import dji.sdk.Products.DJIAircraft;
import dji.sdk.RemoteController.DJIRemoteController;
import dji.sdk.SDKManager.DJISDKManager;
import dji.sdk.base.DJIBaseComponent;
import dji.sdk.base.DJIBaseProduct;
import dji.sdk.base.DJIError;
import dji.sdk.base.DJISDKError;

/**
 * Handler class responsible for interacting the drone.
 */
public class DroneHandler extends AbstractHandler {

    private static final String LOG_TAG = DJIHutApplication.MASTER_TAG + "_DroneHandler";
    public static final String FLAG_CONNECTION_CHANGE = "dji_sdk_connection_change";
    public static final String FLAG_DRONE_HEARTBEAT = "drone_heartbeat";
    private static DJIAircraft drone;
    private DJIHutApplication.CustomBroadcast djisdkConnectionChangeBroadcaster, droneHeartbeatBroadcaster;

    public DroneHandler(DJIHutApplication application) {
        super(application);

        //Setup listeners
        this.djisdkConnectionChangeBroadcaster = application.createBroadcast(FLAG_CONNECTION_CHANGE);
        this.droneHeartbeatBroadcaster = application.createBroadcast(FLAG_DRONE_HEARTBEAT);

        //Setup call back for DJISDK
        DJISDKManager.getInstance().initSDKManager(application, DJISDKManagerCallback);

        this.setupHeartbeat();
    }

    public DJIAircraft getDrone() {
        return drone;
    }

    public boolean isDroneConnected() {
        return drone != null && drone.isConnected();
    }

    public boolean isControllerConnected() {
        DJIRemoteController controller = getRemoteController();
        return controller != null && controller.isConnected();
    }

    public String getDroneType() {
        return drone != null ? drone.getModel().getDisplayName() : "";
    }

    private DJIRemoteController getRemoteController() {
        return drone != null ? drone.getRemoteController() : null;
    }

    /**
     * Callback for the DJISDK Manager. Handles DJISDK registration and DJI product changes.
     */
    private DJISDKManager.DJISDKManagerCallback DJISDKManagerCallback = new DJISDKManager.DJISDKManagerCallback() {

        @Override
        public void onGetRegisteredResult(DJIError result) {
            if (result == DJISDKError.REGISTRATION_SUCCESS) {
                Log.i(LOG_TAG, "Successful registration with DJI SDK");
                DJISDKManager.getInstance().startConnectionToProduct();
            } else {
                Log.e(LOG_TAG, "Unable to register with DJI SDK: " + result.getDescription());
            }
        }

        @Override
        public void onProductChanged(DJIBaseProduct oldProduct, DJIBaseProduct newProduct) {
            //Update drone reference
            drone = (DJIAircraft) newProduct;

            //Update drone listener
            if (drone != null)
                drone.setDJIBaseProductListener(DJIBaseProductListener);

            //Broadcast change
            notifyStatusChange();
        }
    };

    /**
     * DJI Product listener (i.e. drone listener). Handles connection and component changes.
     */
    private DJIBaseProduct.DJIBaseProductListener DJIBaseProductListener = new DJIBaseProduct.DJIBaseProductListener() {

        @Override
        public void onComponentChange(DJIBaseProduct.DJIComponentKey key, DJIBaseComponent oldComponent, DJIBaseComponent newComponent) {
            if (newComponent != null)
                newComponent.setDJIComponentListener(new DJIBaseComponent.DJIComponentListener() {
                    @Override
                    public void onComponentConnectivityChanged(boolean isConnected) {
                        notifyStatusChange();
                    }
                });
            notifyStatusChange();
        }

        @Override
        public void onProductConnectivityChanged(boolean isConnected) {
            notifyStatusChange();
        }
    };

    public Coordinates getCurrentLocation() {
        DJIFlightControllerDataType.DJILocationCoordinate3D coordinate3D = drone.getFlightController().getCurrentState().getAircraftLocation();
        return new Coordinates(coordinate3D.getLatitude(), coordinate3D.getLongitude(), coordinate3D.getAltitude());
    }

    public Double[] getCurrentLocationDoubles() {
        Coordinates location = getCurrentLocation();
        return new Double[]{location.getLatitude(), location.getLongitude()};
    }

    /**
     * Broadcast DJISDK connection change.
     */
    private void notifyStatusChange() {
        //Post connection change to notify external listeners
        this.application.getAppHandler().removeCallbacks(this.djisdkConnectionChangeBroadcaster);
        this.application.getAppHandler().post(this.djisdkConnectionChangeBroadcaster);
    }

    /**
     * Schedule broadcasts of drone data every second.
     */
    private void setupHeartbeat() {
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                DJIFlightController flightController;
                if (isDroneConnected() && (flightController = drone.getFlightController()) != null) {
                    DJIFlightControllerDataType.DJIFlightControllerCurrentState state = flightController.getCurrentState();
                    DJIFlightControllerDataType.DJILocationCoordinate3D loc = state.getAircraftLocation();
                    broadcastDroneHeartbeat(loc.getLatitude(), loc.getLongitude(), state.getAircraftHeadDirection());
                }
            }
        }, 0, 1000);
    }

    private void broadcastDroneHeartbeat(double lat, double lng, int hdg) {
        //Set broadcast information
        Bundle content = new Bundle();
        content.putDouble("lat", lat);
        content.putDouble("lng", lng);
        content.putInt("hdg", hdg);
        droneHeartbeatBroadcaster.setContent(content);

        this.application.getAppHandler().removeCallbacks(this.droneHeartbeatBroadcaster);
        this.application.getAppHandler().post(this.droneHeartbeatBroadcaster);
    }

}
