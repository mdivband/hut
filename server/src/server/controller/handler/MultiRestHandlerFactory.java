package server.controller.handler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class MultiRestHandlerFactory {

    private static final int maxUsers = 2;
    private static final ArrayList<Map<String, RestHandler>> restHandlerMaps = new ArrayList<>();
    private static int[] record = new int[2];

    public static void registerRestHandler(RestHandler restHandler, int index) {
        if (record[index] != index) {
            record[index] = index;
        }

        if (restHandlerMaps.isEmpty()) {
            for (int i=0; i<maxUsers;i++) {
                restHandlerMaps.add(new HashMap<>());
            }
        }

        String name = restHandler.getHandlerName();
        if (!restHandlerMaps.get(index).containsKey(name)) {
            restHandlerMaps.get(index).put(name, restHandler);
        } else
            throw new IllegalArgumentException("Cannot register REST handler - a handler is already registered under the name " + name);
    }

    public static RestHandler getRestHandler(String name, int index) {
        return restHandlerMaps.get(index).get(name);
    }

    public static void unregisterAllHandlers() {
        for (Map<String, RestHandler> m : restHandlerMaps) {
            m.clear();
        }
    }

}
