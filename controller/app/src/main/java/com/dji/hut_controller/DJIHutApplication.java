package com.dji.hut_controller;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.multidex.MultiDex;

import com.dji.hut_controller.handler.DroneHandler;
import com.dji.hut_controller.handler.MissionHandler;
import com.dji.hut_controller.handler.ServerHandler;

import java.util.ArrayList;
import java.util.List;

public class DJIHutApplication extends Application {

    public static final String MASTER_TAG = "DJIHut";
    private static DJIHutApplication appInstance;
    private Handler appHandler;
    private DroneHandler droneHandler;
    private ServerHandler serverHandler;
    private MissionHandler missionHandler;

    public static DJIHutApplication instance() {
        return appInstance;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        appInstance = this;
        appHandler = new Handler(Looper.getMainLooper());

        //Setup various handlers.
        droneHandler = new DroneHandler(this);
        serverHandler = new ServerHandler(this);
        missionHandler = new MissionHandler(this);
    }

    protected void attachBaseContext(Context base){
        super.attachBaseContext(base);
        MultiDex.install(this);
    }

    public Handler getAppHandler() {
        return appHandler;
    }

    public DroneHandler getDroneHandler() {
        return droneHandler;
    }

    public ServerHandler getServerHandler() {
        return serverHandler;
    }

    public MissionHandler getMissionHandler() {
        return missionHandler;
    }

    /**
     * Create a broadcast producer on a specific flag.
     * @param flag Name of broadcast.
     * @return Instance of broadcaster.
     */
    public CustomBroadcast createBroadcast(final String flag) {
        return new CustomBroadcast(flag);
    }

    /**
     * Setup a broadcast listener based on a specified broadcast flag.
     * @param flag Name of broadcast to listen to.
     * @param onReceive Executable to run when broadcast is sent.
     * @return Instance of BroadcastReceiver that is setup to listen to given flag.
     */
    public BroadcastReceiver setupListener(String flag, final Runnable onReceive) {
        IntentFilter filter;
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                onReceive.run();
            }
        };
        filter = new IntentFilter();
        filter.addAction(flag);
        registerReceiver(receiver, filter);
        return receiver;
    }

    public void destroyListener(BroadcastReceiver listener) {
        unregisterReceiver(listener);
    }

    public class CustomBroadcast implements Runnable {

        private String flag;
        private Bundle content;

        private CustomBroadcast(String flag) {
            this.flag = flag;
        }

        public void setContent(Bundle content) {
            this.content = content;
        }

        @Override
        public void run() {
            Intent intent = new Intent(flag);
            intent.putExtra("content", content);
            sendBroadcast(intent);
        }
    }
}
