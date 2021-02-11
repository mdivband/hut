package com.dji.hut_controller.activity;

import android.content.BroadcastReceiver;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.TextureView;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.ToggleButton;

import com.dji.hut_controller.DJIHutApplication;
import com.dji.hut_controller.R;
import com.dji.hut_controller.handler.DroneHandler;
import com.dji.hut_controller.model.Coordinates;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import dji.sdk.Camera.DJICamera;
import dji.sdk.Codec.DJICodecManager;
import dji.sdk.FlightController.DJIFlightController;
import dji.sdk.FlightController.DJIFlightControllerDataType;
import dji.sdk.FlightController.DJIFlightControllerDelegate;
import dji.sdk.Products.DJIAircraft;

/**
 * The main activity of the application.
 * Displays drone position on map and input from the drone's camera.
 * Has UI elements for governing drone control.
 */
public class OverviewActivity extends AppCompatActivity {

    private Switch swh_manual_control;
    private SupportMapFragment mapRenderer;
    private TextureView cameraRenderer;
    private CameraManager cameraManager;
    private MapManager mapManager;
    private BroadcastReceiver djiBroadcastReceiver;
    private DJIHutApplication application;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //Grab application context
        this.application = DJIHutApplication.instance();

        //Setup broadcast listeners
        djiBroadcastReceiver = application.setupListener(DroneHandler.FLAG_CONNECTION_CHANGE, new DJIConnectionListener());

        //Display view
        setContentView(R.layout.activity_overview);

        //Grab required components
        swh_manual_control = (Switch) findViewById(R.id.swh_manual_control);
        mapRenderer = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        cameraRenderer = (TextureView) findViewById(R.id.camera_input);

        //Add button listeners
        swh_manual_control.setOnCheckedChangeListener(new ManualControlListener());

        //Initialise camera manager
        cameraManager = new CameraManager();
        cameraManager.setupCamera(application.getDroneHandler().getDrone().getCamera(), cameraRenderer);

        //Initialise map manager
        mapManager = new MapManager();
        mapManager.setupFlightControllerCallback();
    }

    /**
     * Runnable interface that executes whenever the DJI connection changes,
     * i.e. when a drone/controller is connected or disconnected.
     */
    private class DJIConnectionListener implements Runnable {
        @Override
        public void run() {
            mapManager.setupFlightControllerCallback();
        }
    }

    @Override
    protected void onDestroy() {
        this.application.destroyListener(djiBroadcastReceiver);
        cameraManager.destroy();
        super.onDestroy();
    }

    private class MapManager implements OnMapReadyCallback {

        private GoogleMap gMap;
        private UiSettings mapUISettings;
        private Marker droneMarker;

        private MapManager() {
            //Request setup map
            mapRenderer.getMapAsync(this);
        }

        @Override
        public void onMapReady(GoogleMap googleMap) {
            //Process map setup
            gMap = googleMap;
            gMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);

            mapUISettings = gMap.getUiSettings();
            mapUISettings.setZoomControlsEnabled(false);
            mapUISettings.setAllGesturesEnabled(false);
        }

        /**
         * Create a callback from the drone's flight controller for tracking the position of the
         * drone.
         * Used to update the position of the drone marker on the map UI.
         */
        private void setupFlightControllerCallback() {
            DJIFlightController flightController;
            DJIAircraft drone = application.getDroneHandler().getDrone();
            if (drone.isConnected() && (flightController = drone.getFlightController()) != null) {
                flightController.setUpdateSystemStateCallback(new DJIFlightControllerDelegate.FlightControllerUpdateSystemStateCallback() {
                    @Override
                    public void onResult(DJIFlightControllerDataType.DJIFlightControllerCurrentState state) {
                        DJIFlightControllerDataType.DJILocationCoordinate3D loc = state.getAircraftLocation();
                        updateDroneMarker(loc.getLatitude(), loc.getLongitude(), state.getAircraftHeadDirection());
                    }
                });
            }
        }

        private void updateDroneMarker(double lat, double lng, final int hdg) {
            if (Coordinates.isValid(lat, lng)) {
                final LatLng pos = new LatLng(lat, lng);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        //Create marker if it doesn't exist
                        if (droneMarker == null) {
                            MarkerOptions markerOptions = new MarkerOptions();
                            markerOptions.position(pos);
                            markerOptions.icon(BitmapDescriptorFactory.fromResource(R.drawable.aircraft));
                            droneMarker = gMap.addMarker(markerOptions);
                        }
                        //Move marker
                        droneMarker.setPosition(pos);
                        droneMarker.setRotation(hdg);
                        CameraUpdate cu = CameraUpdateFactory.newLatLngZoom(pos, 18);
                        gMap.moveCamera(cu);
                    }
                });
            }
        }

    }

    private class CameraManager implements TextureView.SurfaceTextureListener {

        private DJICodecManager codecManager;
        private DJICamera.CameraReceivedVideoDataCallback cameraCallback;

        private void setupCodecManager(SurfaceTexture surface, int width, int height) {
            codecManager = new DJICodecManager(OverviewActivity.this, surface, width, height);
            cameraCallback = new DJICamera.CameraReceivedVideoDataCallback() {
                @Override
                public void onResult(byte[] videoBuffer, int size) {
                    if (codecManager != null)
                        codecManager.sendDataToDecoder(videoBuffer, size);
                }
            };
        }

        private void setupCamera(DJICamera camera, TextureView renderer) {
            renderer.setSurfaceTextureListener(this);
            camera.setDJICameraReceivedVideoDataCallback(cameraCallback);
        }

        private void destroy() {
            application.getDroneHandler().getDrone().getCamera().setDJICameraReceivedVideoDataCallback(null);
        }

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            setupCodecManager(surface, width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            setupCodecManager(surface, width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            if (codecManager != null) {
                codecManager.cleanSurface();
                codecManager = null;
            }
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {}

    }

    private class ManualControlListener implements ToggleButton.OnCheckedChangeListener {
        @Override
        public void onCheckedChanged(CompoundButton button, boolean isChecked) {
            //TODO process manual control button
        }
    }

}
