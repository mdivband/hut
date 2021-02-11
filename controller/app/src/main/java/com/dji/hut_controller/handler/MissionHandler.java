package com.dji.hut_controller.handler;

import android.util.Log;

import com.dji.hut_controller.DJIHutApplication;
import com.dji.hut_controller.model.Coordinates;
import com.dji.hut_controller.model.Mission;

import java.util.LinkedList;
import java.util.List;

import dji.sdk.FlightController.DJIFlightController;
import dji.sdk.MissionManager.DJICustomMission;
import dji.sdk.MissionManager.DJIMission;
import dji.sdk.MissionManager.DJIMissionManager;
import dji.sdk.MissionManager.MissionStep.DJIGoToStep;
import dji.sdk.MissionManager.MissionStep.DJIMissionStep;
import dji.sdk.base.DJIBaseComponent;
import dji.sdk.base.DJIError;

/**
 * Handler for mission data.
 */
public class MissionHandler extends AbstractHandler {

    private static final String LOG_TAG = DJIHutApplication.MASTER_TAG + "_MissionHandler";
    private DJIMissionManager missionManager;
    private DJIFlightController flightController;
    private String currentMissionId = "";

    public MissionHandler(DJIHutApplication application) {
        super(application);
    }

    protected String getCurrentMissionId() {
        return currentMissionId;
    }

    /**
     * Process incoming mission from server.
     * @param mission Mission object (pre-processed from JSON).
     */
    protected void processNewMission(final Mission mission) {
        //Ignore mission if same as current mission.
        //TODO what if mission parameters are different?
        if (mission.getId().equals(currentMissionId))
            return;

        Log.i(LOG_TAG, "Processing new mission: " + mission.getId());
        missionManager = application.getDroneHandler().getDrone().getMissionManager();
        flightController = application.getDroneHandler().getDrone().getFlightController();

        //Cancel current mission
        if (missionManager.mIsCustomMissionExecuting)
            missionManager.stopMissionExecution(new DJIBaseComponent.DJICompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                }
            });
        try {
            List<DJIMissionStep> djiMissionSteps = new LinkedList<>();

            //TODO move drone to target altitude
//                if(this.flightController.getCurrentState().getAircraftLocation().getAltitude()<altitude-1) {
//                    DJIGoToStep step = new DJIGoToStep(altitude, new DJIBaseComponent.DJICompletionCallback() {
//                        @Override
//                        public void onResult(DJIError djiError) {
//
//                        }
//                    });
//                    step.setFlightSpeed(4f);
//                    djiMissionSteps.add(step);
//                }

            //Create step based on mission coordinates
            //TODO multi step missions?
            Coordinates nextCoordinates = mission.getCoordinates();
            DJIGoToStep step = new DJIGoToStep(nextCoordinates.getLatitude(), nextCoordinates.getLongitude(), nextCoordinates.getAltitude(), new DJIBaseComponent.DJICompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    //TODO on mission step completion
                }
            });
            step.setFlightSpeed(4f);
            djiMissionSteps.add(step);

            //Prepare mission and initiate once prepared.
            DJICustomMission customMission = new DJICustomMission(djiMissionSteps);
            missionManager.prepareMission(customMission,
                    new DJIMission.DJIMissionProgressHandler() {
                        @Override
                        public void onProgress(DJIMission.DJIProgressType djiProgressType, float v) {}
                    }, new DJIBaseComponent.DJICompletionCallback() {
                        @Override
                        public void onResult(DJIError djiError) {
                            currentMissionId = mission.getId();
                            missionManager.startMissionExecution(new DJIBaseComponent.DJICompletionCallback() {
                                @Override
                                public void onResult(DJIError djiError) {
                                    Log.e(LOG_TAG, "Unable to start mission " + currentMissionId + " - " + djiError.getDescription());
                                }
                            });
                        }
                    });
        } catch (Exception e) {
            Log.e(LOG_TAG, "Error processing mission", e);
        }
    }
}
