package server.controller;

import server.Simulator;

import java.util.logging.Logger;

abstract class AbstractController {

    final Simulator simulator;
    final Logger LOGGER;

    AbstractController(Simulator simulator, String controllerName) {
        this.simulator = simulator;
        this.LOGGER = Logger.getLogger(controllerName);
    }
}
