package com.dji.hut_controller.handler;

import com.dji.hut_controller.DJIHutApplication;

/**
 * Base class for handler classes.
 */
public abstract class AbstractHandler {

    protected DJIHutApplication application;

    AbstractHandler(DJIHutApplication application) {
        this.application = application;
    }

}
