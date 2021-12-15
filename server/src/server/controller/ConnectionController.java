package server.controller;

import server.Simulator;
import server.controller.handler.*;
import tool.HttpServer;
import tool.HttpServer.*;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * @author Feng Wu
 */
public class ConnectionController extends AbstractController {

    private HttpServer httpserver;

    private Logger LOGGER = Logger.getLogger(ConnectionController.class.getName());

    public ConnectionController(Simulator simulator) {
        super(simulator, ConnectionController.class.getName());
    }

    public void init(int port) {
        httpserver = new HttpServer(port);
        LOGGER.info("Server port: " + port);

        final VirtualHost host = httpserver.getVirtualHost(null);
        host.setAllowGeneratedIndex(true);

        try {
            final File dir = new File("web/");
            if (!dir.canRead())
                throw new IOException(dir + " cannot read.");
            LOGGER.info("Server home: " + dir);

            host.addContext("/", new ContextHandler() {
                ContextHandler fileHandler = new FileContextHandler(dir, "/");

                @Override
                public int serve(Request req, Response resp) throws IOException {
                    resp.getHeaders().add("Cache-Control", "no-cache, no-store, private, max-age=0");
                    resp.getHeaders().add("Content-Language", "en");
                    resp.getHeaders().add("Pragma", "no-cache");
                    resp.getHeaders().add("Expires", "0");

                    //Attempt to handle as endpoint
                    if (handleEndpoint(req, resp))
                        return 200;

                    //If not endpoint then handle as file request.
                    return fileHandler.serve(req, resp);
                }
            });

            RestHandlerFactory.unregisterAllHandlers();
            RestHandlerFactory.registerRestHandler(new RootHandler("/", this.simulator));
            RestHandlerFactory.registerRestHandler(new AgentHandler("/agents", this.simulator));
            RestHandlerFactory.registerRestHandler(new TaskHandler("/tasks", this.simulator));
            RestHandlerFactory.registerRestHandler(new TargetHandler("/targets", this.simulator));
            RestHandlerFactory.registerRestHandler(new AllocationHandler("/allocation", this.simulator));
            RestHandlerFactory.registerRestHandler(new ModeHandler("/mode", this.simulator));
            RestHandlerFactory.registerRestHandler(new VisualizerHandler("/visualizer", this.simulator));
            RestHandlerFactory.registerRestHandler(new ReviewHandler("/review", this.simulator));
            RestHandlerFactory.registerRestHandler(new PresetHandler("/preset", this.simulator));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Handle a request using the registered RestHandlers.
     * @return True if request was handled by the REST endpoint handlers(successfully or not)
     *    or false if no REST handlers were able to process the request.
     */
    private boolean handleEndpoint(Request req, Response resp) throws IOException {
        RestHandler restHandler = getHandlerForPath(req.getPath());
        try {
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
    private RestHandler getHandlerForPath(String path) {
        if(path == null || path.equals(""))
            return RestHandlerFactory.getRestHandler("/");

        RestHandler handler = RestHandlerFactory.getRestHandler(path);
        if(handler != null)
            return handler;
        return getHandlerForPath(path.substring(0, path.lastIndexOf("/")));
    }

}
