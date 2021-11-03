package server.controller;

import server.Simulator;

public class MessageController extends AbstractController {

    public MessageController(Simulator simulator) {
        super(simulator, AgentController.class.getName());
    }

}
