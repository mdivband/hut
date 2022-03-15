package server.controller.handler;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import server.Simulator;
import server.controller.AgentController;
import server.model.agents.Agent;
import server.model.State;
import tool.HttpServer.Request;
import tool.HttpServer.Response;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class RootHandler extends RestHandler {

    public RootHandler(String handlerName, Simulator simulator) {
        super(handlerName, simulator);
    }

    @Override
    public void handlePost(Request req, Response resp) throws IOException, UnregisteredPathException {
        switch (req.getPath()) {
            case "/provdoc":
                handleProvdoc(req, resp);
                break;
            case "/changeview":
                handleChangeView(req, resp);
                break;
            case "/teleop":
                handleTeleop(req, resp);
                break;
            case "/ardrone_pos":
                handleARDronePos(req, resp);
                break;
            case "/configjson":
                handleConfigJson(req, resp);
                break;
            case "/reset":
                handleReset(resp);
                break;
            case "/logger":
                //TODO logger endpoint not implemented.
                break;
            case "/register":
                handleRegister(req, resp);
                break;
            default:
                throw new UnregisteredPathException("No method for handling POST request on " + req.getPath());
        }
    }

    @Override
    public void handleGet(Request req, Response resp) throws IOException, UnregisteredPathException {
        switch (req.getPath()) {
            case "/state.json":
                handleGetState(resp);
                break;
            default:
                throw new UnregisteredPathException("No method for handling GET request on " + req.getPath());
        }
    }

    private void handleProvdoc(Request req, Response resp) throws IOException {
        Map<String, String> params = req.getParams();
        List<String> expectedKeys = Collections.singletonList("id");
        if (!checkParams(params, expectedKeys, resp))
            return;
        simulator.setProvDoc(params.get("id"));
        resp.sendOkay();
    }

    private void handleChangeView(Request req, Response resp) throws IOException {
        Map<String, String> params = req.getParams();
        List<String> expectedKeys = Collections.singletonList("edit");
        if (!checkParams(params, expectedKeys, resp))
            return;
        simulator.changeView(Boolean.parseBoolean(params.get("edit")));
        LOGGER.info(String.format("%s; CHVW; Changing view to mode; %s ", Simulator.instance.getState().getTime(), Boolean.parseBoolean(params.get("edit"))));
        resp.sendOkay();
    }

    private void handleTeleop(Request req, Response resp) throws IOException {
        Map<String, String> params = req.getParams();
        List<String> expectedKeys = Arrays.asList("linear", "angular", "id");
        if (!checkParams(params, expectedKeys, resp))
            return;
        String id = params.get("id");
        if (!agentExists(id, resp))
            return;

        Double linear = Double.parseDouble(params.get("linear"));
        Double angular = Double.parseDouble(params.get("angular"));
        Agent agent = simulator.getState().getAgent(id);
        //TODO Teleop (if used?) doesn't actually control the agent.
        //agent.moveTowardsDirection(linear, Math.toRadians(angular));
        resp.sendOkay();
    }

    private void handleARDronePos(Request req, Response resp) throws IOException {
        Map<String, String> params = req.getParams();
        List<String> expectedKeys = Arrays.asList("id", "x", "y", "a");
        if (!checkParams(params, expectedKeys, resp))
            return;
        String id = params.get("id");
        if(!agentExists(id, resp))
            return;
        Double x = Double.parseDouble(params.get("x"));
        Double y = Double.parseDouble(params.get("y"));
        Double a = Double.parseDouble(params.get("a"));

        Agent agent = simulator.getState().getAgent(id);
        double distance = Math.sqrt(x * x + y * y);
        double angle = Math.atan2(x, -y) + a;

        agent.setCoordinate(agent.getCoordinate().getCoordinate(distance, angle));
        resp.sendOkay();
    }

    private void handleConfigJson(Request req, Response resp) throws IOException {
        Map<String, String> params = req.getParams();
        List<String> expectedKeys = Collections.singletonList("agent");
        if (!checkParams(params, expectedKeys, resp))
            return;

        String val = params.get("agent");
        String val2 = val.substring(1, val.length() - 1);
        ArrayList<String> items = new ArrayList<>(Arrays.asList(val2.split(",")));
        try {
            File file = new File("./web/configmap.json");
            if (!file.exists())
                file.createNewFile();
            String header = " {\"server\": { \"port\": 80,\"time\": \"10:00:00\",\"task_duration\": 10,\"game_id\": 46,\"target_size\": 20},\"agents\": [";
            String footer = "]}";

            FileWriter fw = new FileWriter(file.getAbsoluteFile());
            BufferedWriter bw = new BufferedWriter(fw);

            bw.write(header);
            String id = ",{\"id\": \"agent-";
            String lat = "\",\"lat\": ";
            String lng = ",\"lng\":";
            String vfooter = "}";

            for (int i = 0; i < items.size(); i++) {
                if (i == 0) {
                    id = id.substring(1);
                    id = id + "1";
                    lat = lat + items.get(i);
                } else if (i % 2 == 0) {
                    int num = i / 2 + 1;
                    id = id + Integer.toString(num);
                    lat = lat + items.get(i);
                } else {
                    lng = lng + items.get(i);
                    bw.write(id);
                    bw.write(lat);
                    bw.write(lng);
                    bw.write(vfooter);

                    // back to default values
                    id = ",{\"id\": \"agent-";
                    lat = "\",\"lat\": ";
                    lng = ",\"lng\":";
                }
            }
            bw.write(footer);
            bw.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        simulator.reset();
    }

    private void handleReset(Response resp) throws IOException {
        simulator.reset();
        resp.send(200,
                "<html><head><meta http-equiv='refresh' content='0; url=/' /><script type='text/javascript'>window.setTimeout(function(){window.location='/?'+(new Date()).getTime();},0);</script></head><body></body></html>");
    }

    private void handleGetState(Response resp) throws IOException {
        State state = simulator.getState();
        synchronized (state) {
            String stateString = simulator.getStateAsString();
            resp.getHeaders().add("Content-type", "application/json; charset=utf-8");
            resp.send(200, stateString);
        }

    }

    private void handleRegister(Request req, Response resp) throws IOException {
        //TODO sort out registration request so it can use normal methods
//        Map<String, String> params = req.getParams();
//        List<String> expectedKeys = Arrays.asList("lat", "lon");
//        if (!checkParams(params, expectedKeys, resp)) {
//            System.out.println("Test");
//            return;
//        }
//        double lat = Double.parseDouble(params.get("lat"));
//        double lng = Double.parseDouble(params.get("lon"));

        JsonObject jsonReq = new JsonParser().parse(req.getBodyContent()).getAsJsonObject();
        double lat = Double.parseDouble(jsonReq.get("lat").getAsString());
        double lng = Double.parseDouble(jsonReq.get("lon").getAsString());

        //TODO Send heading on register agent
        Agent agent = simulator.getAgentController().addRealAgent(lat, lng, 0d);
        simulator.getQueueManager().addQueue("UAV_TaskQueue_" + agent.getId());
        LOGGER.info(String.format("%s; RGAG; Registered agent (id, lat, lng); %s; %s; %s", Simulator.instance.getState().getTime(), agent.getId(), agent.getCoordinate().getLatitude(), agent.getCoordinate().getLongitude()));

        JsonObject jsonResp = new JsonObject();
        jsonResp.addProperty("URI", simulator.getQueueManager().getCloudURI());
        jsonResp.addProperty("ID", agent.getId());
        jsonResp.addProperty("Altitude", AgentController.nextAgentAltitude++);

        resp.getHeaders().add("Content-type", "application/json; charset=utf-8");
        resp.send(200, jsonResp.toString());
    }

}
