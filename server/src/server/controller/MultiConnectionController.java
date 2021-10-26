package server.controller;

import server.ParallelizedSimulator;
import server.Simulator;
import server.controller.handler.*;
import tool.HttpServer;
import tool.MultiHttpServer;
import tool.MultiHttpServer.*;
import tool.HttpServer.*;
import tool.HttpServer;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Logger;

/**
 * @author Feng Wu
 */
public class MultiConnectionController {

    private MultiHttpServer httpserver;

    private ParallelizedSimulator[] sims;
    private int maxUsers = 2;

    private Logger LOGGER = Logger.getLogger(MultiConnectionController.class.getName());

    public static void main(String[] args) {
        MultiConnectionController multiConnectionController = new MultiConnectionController();
        new Thread(multiConnectionController::start).start();
        //multiConnectionController.init(8000);

    }

    public MultiConnectionController() {
        sims = new ParallelizedSimulator[maxUsers];


        for (int i=0; i<maxUsers; i++) {
            ParallelizedSimulator simulator = new ParallelizedSimulator(this);
            new Thread(simulator::start).start();
            //simulator.start();
            sims[i] = simulator;
        }

        init(8000);

    }

    public void init(int port) {
        httpserver = new MultiHttpServer(port);
        LOGGER.info("Server port: " + port);

        final VirtualHost host = httpserver.getVirtualHost(null);
        host.setAllowGeneratedIndex(true);

        MultiRestHandlerFactory.unregisterAllHandlers();
        for (int i=0; i<maxUsers; i++) {

            MultiRestHandlerFactory.registerRestHandler(new RootHandler("/", sims[i]), i);
            MultiRestHandlerFactory.registerRestHandler(new AgentHandler("/agents", sims[i]), i);
            MultiRestHandlerFactory.registerRestHandler(new TaskHandler("/tasks", sims[i]), i);
            MultiRestHandlerFactory.registerRestHandler(new TargetHandler("/targets", sims[i]), i);
            MultiRestHandlerFactory.registerRestHandler(new AllocationHandler("/allocation", sims[i]), i);
            MultiRestHandlerFactory.registerRestHandler(new ModeHandler("/mode", sims[i]), i);
            MultiRestHandlerFactory.registerRestHandler(new VisualizerHandler("/visualizer", sims[i]), i);
        }

        for (int i=0; i<maxUsers; i++) {

            try {
                final File dir = new File("web"+i+"/");
                if (!dir.canRead())
                    throw new IOException(dir + " cannot read.");
                LOGGER.info("Server home: " + dir);

                int finalI = i;
                host.addContext("/"+ finalI +"/", new ContextHandler() {
                    ContextHandler fileHandler = new MultiHttpServer.FileContextHandler(dir, "/"+ finalI +"/");

                    @Override
                    public int serve(Request req, Response resp) throws IOException {
                        resp.getHeaders().add("Cache-Control", "no-cache, no-store, private, max-age=0");
                        resp.getHeaders().add("Content-Language", "en");
                        resp.getHeaders().add("Pragma", "no-cache");
                        resp.getHeaders().add("Expires", "0");

                        //LOGGER.severe("This cookie: " + req.getHeaders().get("Cookie"));

                        //if (req.getHeaders().contains("Thread")) {
                        //   resp.getHeaders().add("Thread", req.getHeaders().get("Thread"));
                        //}

                        //Attempt to handle as endpoint
                        if (handleEndpoint(req, resp))
                            return 200;

                        //If not endpoint then handle as file request.
                        return fileHandler.serve(req, resp);
                    }

                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


        //MultiRestHandlerFactory.registerRestHandler(new EntryHandler(), maxUsers);

    }

    /**
     * Handle a request using the registered RestHandlers.
     * @return True if request was handled by the REST endpoint handlers(successfully or not)
     *    or false if no REST handlers were able to process the request.
     */
    private boolean handleEndpoint(Request req, Response resp) throws IOException {
        //int thread = Integer.parseInt(resp.getHeaders().get("Thread"));
        String[] pathSplit = req.getPath().split("/");
        int thread = Integer.parseInt(pathSplit[1]);  // Should be in form "/X/something/foo/bar"
        String trimmedPath = "/" + String.join("/", Arrays.stream(pathSplit).toList().subList(2, pathSplit.length));
          // ^ Strips out the first part of the URl, then rejoins with "/" to separate and reform the URL



        RestHandler restHandler = getHandlerForPath(trimmedPath, thread);
        try {
            // HERE
            restHandler.handle(req, resp);
        } catch (UnregisteredPathException e) {
            //Report error for missing path in non root handlers ONLY.
            //Missing path in root handler means it's probably a request for the file context
            // so don't need to report error.
            if(!(restHandler instanceof RootHandler))
                LOGGER.severe("Unable to handle request using handler for " + restHandler.getHandlerName() + " - unregistered path " + req.getPath() + ". " + e.getMessage());
            return false;
        }

        return true;
    }

    public void start() {
        if (httpserver != null) {
            try {
                httpserver.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void stop() {
        if (httpserver != null) {
            httpserver.stop();
        }
    }

    /**
     * Get a handler for the given path.
     * Recursively iterates backwards through /'s until handler is found.
     */
    private RestHandler getHandlerForPath(String path, int index) {
        if(path == null || path.equals(""))
            return MultiRestHandlerFactory.getRestHandler("/", index);

        RestHandler handler = MultiRestHandlerFactory.getRestHandler(path, index);
        if(handler != null)
            return handler;
        return getHandlerForPath(path.substring(0, path.lastIndexOf("/")), index);
    }

}
