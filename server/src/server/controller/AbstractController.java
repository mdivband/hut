package server.controller;

import server.Simulator;

import java.util.logging.FileHandler;
import java.util.logging.Logger;

abstract class AbstractController {

    final Simulator simulator;
    final Logger LOGGER;

    AbstractController(Simulator simulator, String controllerName, Logger LOGGER) {
        this.simulator = simulator;
        this.LOGGER = LOGGER;
    }

    public void resetLogger(FileHandler fileHandler) {
        LOGGER.addHandler(fileHandler);
    }

}
