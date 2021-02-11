package com.dji.hut_controller.activity;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.dji.hut_controller.DJIHutApplication;
import com.dji.hut_controller.R;
import com.dji.hut_controller.handler.DroneHandler;

/**
 * Entry point for the android application.
 * Displays a connection interface for setting up the drone and controller.
 * Also initialises the connection to the server.
 */
public class ConnectionActivity extends AppCompatActivity {

    private BroadcastReceiver djiBroadcastReceiver;
    private ImageView img_rc, img_drone;
    private TextView txt_rc, txt_drone, txt_information, txt_error;
    private Button btn_connect;
    private ProgressBar pgb_connect;
    private DJIHutApplication application;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //Request permissions
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.VIBRATE,
                        Manifest.permission.INTERNET, Manifest.permission.ACCESS_WIFI_STATE,
                        Manifest.permission.WAKE_LOCK, Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.CHANGE_WIFI_STATE, Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS,
                        Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.SYSTEM_ALERT_WINDOW,
                        Manifest.permission.READ_PHONE_STATE,
                }
                , 1);

        //Grab application context
        this.application = DJIHutApplication.instance();

        //Setup listener for DJI connection changes
        djiBroadcastReceiver = this.application.setupListener(DroneHandler.FLAG_CONNECTION_CHANGE, new DJIConnectionListener());

        //Display view
        setContentView(R.layout.activity_connection);

        //Grab components that will need updating
        img_rc = (ImageView) findViewById(R.id.img_rc);
        img_drone = (ImageView) findViewById(R.id.img_drone);
        txt_rc = (TextView) findViewById(R.id.txt_rc_status);
        txt_drone = (TextView) findViewById(R.id.txt_drone_status);
        txt_information = (TextView) findViewById(R.id.txt_information_main);
        txt_error = (TextView) findViewById(R.id.txt_error);
        btn_connect = (Button) findViewById(R.id.btn_connect);
        pgb_connect = (ProgressBar) findViewById(R.id.pgb_connect);
    }

    /**
     * Runnable interface that executes whenever the DJI connection changes,
     * i.e. when a drone/controller is connected or disconnected.
     * Establishes the current state of the connections and updates the relevant on-screen elements.
     */
    private class DJIConnectionListener implements Runnable {
        @Override
        public void run() {
            //Get connections
            boolean rcConnected = application.getDroneHandler().isControllerConnected();
            boolean droneConnected = application.getDroneHandler().isDroneConnected();

            //Get new states
            int rc_img_id = rcConnected ? R.drawable.rc_icon_green : R.drawable.rc_icon_red;
            int drone_img_id = droneConnected ? R.drawable.drone_icon_green : R.drawable.drone_icon_red;

            //Update states
            img_rc.setImageDrawable(getResources().getDrawable(rc_img_id, getApplicationContext().getTheme()));
            img_drone.setImageDrawable(getResources().getDrawable(drone_img_id, getApplicationContext().getTheme()));
            txt_rc.setText(rcConnected ? R.string.txt_rc_status_on : R.string.txt_rc_status_off);
            txt_drone.setText(droneConnected ? R.string.txt_drone_status_on : R.string.txt_drone_status_off);
            if (rcConnected) {
                if (droneConnected) {
                    //RC and drone connected - show drone type and show button
                    txt_information.setText(application.getDroneHandler().getDroneType());
                    btn_connect.setVisibility(View.VISIBLE);
                } else {
                    //Only RC connected (not drone) - show connect drone text and hide button
                    txt_information.setText(R.string.txt_information_connect_drone);
                    btn_connect.setVisibility(View.INVISIBLE);
                }
            } else {
                // Neither connected (drone cannot be connected without RC)
                // Show connect controller text and hide button
                txt_information.setText(R.string.txt_information_connect_controller);
                btn_connect.setVisibility(View.INVISIBLE);
            }
        }
    }

    /**
     * Executed when the connect to server button is pressed.
     * Initiates async server connection task and replaces button with loading bar.
     */
    public void connectPressed(View view) {
        application.getServerHandler().connectToServer(this, application.getDroneHandler().getCurrentLocationDoubles());
        //Hide button and show loading bar
        btn_connect.setVisibility(View.INVISIBLE);
        pgb_connect.setVisibility(View.VISIBLE);
    }

    /**
     * Executed when the async server connection task completes.
     * Will progress onto next activity if connection is successful.
     * Otherwise shows error message and shows connection button again.
     * @param result - The result of the connection attempt - 'success' if connection established
     *               or a relevant error message otherwise.
     */
    public void onServerConnectionCompletionCallback(String result) {
        if(result.equals("success")) {
            Intent intent = new Intent(this, OverviewActivity.class);
            startActivity(intent);
        }
        else {
            txt_error.setText(result);
            //Show button and hide loading bar
            btn_connect.setVisibility(View.VISIBLE);
            pgb_connect.setVisibility(View.INVISIBLE);
        }
    }

    /**
     * Called when connecting to server to show progress in connection.
     * Useful for debugging where the connection may be failing.
     * @param progressMessage - A relevant message about the stage of the connection attempt.
     */
    public void onServerConnectionProgressCallback(String progressMessage) {
        txt_error.setText(progressMessage);
    }

    @Override
    protected void onDestroy() {
        this.application.destroyListener(djiBroadcastReceiver);
        super.onDestroy();
    }
}
