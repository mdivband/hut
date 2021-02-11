package server.controller.handler;

import java.util.HashMap;
import java.util.Map;

public class RestHandlerFactory {

    private static final Map<String, RestHandler> restHandlerMap = new HashMap<>();

    public static void registerRestHandler(RestHandler restHandler) {
        String name = restHandler.getHandlerName();
        if(!restHandlerMap.containsKey(name))
            restHandlerMap.put(name, restHandler);
        else
            throw new IllegalArgumentException("Cannot register REST handler - a handler is already registered under the name " + name);
    }

    public static RestHandler getRestHandler(String name) {
        return restHandlerMap.get(name);
    }

    public static void unregisterAllHandlers() {
        restHandlerMap.clear();
    }

}
