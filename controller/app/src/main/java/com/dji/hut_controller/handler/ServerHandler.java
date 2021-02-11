package com.dji.hut_controller.handler;

import android.os.AsyncTask;
import android.util.Log;

import com.dji.hut_controller.DJIHutApplication;
import com.dji.hut_controller.activity.ConnectionActivity;
import com.dji.hut_controller.model.Mission;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeoutException;

/**
 * Handler for server based stuff.
 */
public class ServerHandler extends AbstractHandler {

    private static final String LOG_TAG = DJIHutApplication.MASTER_TAG + "_ServerHandler";
    private static final String serverAddress = "http://10.64.11.90:8000/";
    private ConnectionFactory connectionFactory;
    private boolean isConnected = false;

    private String queueName;
    //TODO droneID should probably be moved somewhere else, maybe droneHandler?
    private String droneID;

    public ServerHandler(DJIHutApplication application) {
        super(application);
        this.connectionFactory = new ConnectionFactory();

        //Setup broadcast listeners
        this.application.setupListener(DroneHandler.FLAG_DRONE_HEARTBEAT, new DroneHeartbeatListener());
    }

    /**
     * Attempt to connect to the server (using an async task).
     * @param activity - Reference to activity this was called from.
     * @param coordinates - Coordinates of drone when connection attempt called.
     */
    public void connectToServer(ConnectionActivity activity, Double[] coordinates) {
        ConnectToServerTask task = new ConnectToServerTask(activity);
        task.execute(coordinates);
    }

    /**
     * Create a consumer for the RabbitMQ drone channel.
     */
    private void setupConsumer() throws IOException, TimeoutException {
        Connection connection = connectionFactory.newConnection();
        Channel channel = connection.createChannel();
        com.rabbitmq.client.Consumer consumer = new Consumer(channel);

        channel.queueDeclare(queueName, false, false, false, null);
        channel.basicConsume(queueName, true, consumer);
    }

    private void sendDronePosition() {
        JsonObject data = new JsonObject();
        data.addProperty("ID", droneID);
        data.addProperty("MissionID", application.getMissionHandler().getCurrentMissionId());
        data.addProperty("Content", "Coordinates");
        data.add("Coordinates", application.getDroneHandler().getCurrentLocation().toJSONObject());
        sendDroneData(data.toString());
    }

    /**
     * Send drone data as JSON string to server by using an async task.
     * @param json JSON string to send to server.
     */
    private void sendDroneData(String json) {
        new SendDroneDataTask().execute(json);
    }

    /**
     * Uses drone heartbeat ({@link DroneHandler#droneHeartbeatBroadcaster}) to send drone position
     * to server every second.
     */
    private class DroneHeartbeatListener implements Runnable {
        @Override
        public void run() {
            sendDronePosition();
        }
    }

    /**
     * Consumer of RabbitMQ drone queue.
     */
    private class Consumer extends DefaultConsumer {

        private Consumer(Channel channel) {
            super(channel);
        }

        @Override
        public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
            JsonObject jsonObject = new JsonParser().parse(new String(body, "UTF-8")).getAsJsonObject();
            Mission incomingMission = Mission.fromJSONObject(jsonObject);
            switch (jsonObject.get("Content").getAsString()) {
                case ("MarkerFinished"):
                    //TODO process 'Marker Finished'
//                    mActivity.runOnUiThread(new Runnable() {
//                        @Override
//                        public void run() {
//                            ((MainActivity) mActivity).finishWaypoint(newMission.get("MarkerID").getAsInt());
//                        }
//                    });
                    break;
                case ("Mission"):
                    application.getMissionHandler().processNewMission(incomingMission);
                    break;
            }
        }
    }

    /**
     * Async task for sending drone data to server.
     */
    private class SendDroneDataTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... input) {
            try {
                if(isConnected) {
                    Connection connection = connectionFactory.newConnection();
                    Channel channel = connection.createChannel();
                    channel.basicPublish("", "Meta_Drone_Data", null, input[0].getBytes());
                    channel.close();
                    connection.close();
                }
            } catch (TimeoutException | IOException e) {
                Log.e(LOG_TAG, "Error sending drone data", e);
            }
            return null;
        }
    }

    /**
     * Async task for establishing connection with server.
     */
    private class ConnectToServerTask extends AsyncTask<Double, String, String> {

        ConnectionActivity activity;

        private ConnectToServerTask(ConnectionActivity activity) {
            this.activity = activity;
        }

        @Override
        protected String doInBackground(Double... coordinates) {
            //Register with server
            publishProgress("Registering with server...");
            try {
                this.registerWithServer(coordinates);
            } catch (Exception e) {
                Log.e(LOG_TAG, "Error when connection to server", e);
                return "Unable to connect: Error 1 - " + e.getClass().getCanonicalName();
            }

            //Setup mission consumer
            publishProgress("Setting up message consumer...");
            try {
                setupConsumer();
            } catch (Exception e) {
                Log.e(LOG_TAG, "Error when connection to server", e);
                return "Unable to connect: Error 2 - " + e.getClass().getCanonicalName();
            }

            //Success
            publishProgress("Success");
            isConnected = true;
            return "success";
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            this.activity.onServerConnectionCompletionCallback(result);
        }

        @Override
        protected void onProgressUpdate(String... values) {
            super.onProgressUpdate(values);
            Log.i(LOG_TAG, values[0]);
            this.activity.onServerConnectionProgressCallback(values[0]);
        }

        private void registerWithServer(Double... coordinates) throws IOException, NoSuchAlgorithmException, KeyManagementException, URISyntaxException {
            //Setup connection
            HttpURLConnection httpURLConnection = (HttpURLConnection) new URL(serverAddress + "/register").openConnection();
            httpURLConnection.setDoOutput(true);
            httpURLConnection.setRequestMethod("POST");
            httpURLConnection.setRequestProperty("Content-Type", "application/json");
            httpURLConnection.setRequestProperty("Accept", "application/json");

            //Create json object containing drone coordinates
            JsonObject toBeSent = new JsonObject();
            toBeSent.addProperty("lat", coordinates[0]);
            toBeSent.addProperty("lon", coordinates[1]);

            //Write json object onto server stream (i.e. send to server)
            Writer writer = new BufferedWriter(new OutputStreamWriter(httpURLConnection.getOutputStream()));
            writer.write(toBeSent.toString());
            writer.close();

            //Gather response
            StringBuilder response = new StringBuilder();
            if (httpURLConnection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                BufferedReader input = new BufferedReader(new InputStreamReader(httpURLConnection.getInputStream()));
                String strLine;
                while ((strLine = input.readLine()) != null) {
                    response.append(strLine);
                }
                input.close();
            }

            //Process response
            JsonObject jsonObject = new JsonParser().parse(response.toString()).getAsJsonObject();
            droneID = jsonObject.get("ID").getAsString().replace("\"", "");
            queueName = "UAV_TaskQueue_" + droneID;
            String uri = jsonObject.get("URI").toString().replace("\"", "");

            //TODO why is server returning an altitude on client registration?
            //altitude = jsonObject.get("Altitude").getAsFloat();
            connectionFactory.setUri(uri);
        }
    }
}
