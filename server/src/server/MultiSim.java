package server;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

public class MultiSim {

    public void run() {
        loadAndRunClass(8001);
        loadAndRunClass(8008);
    }

    private void loadAndRunClass(int port) {
        try {
            URLClassLoader child = new URLClassLoader(new URL[]{new URL("file://./hut-server-a.jar")}, MultiSim.class.getClassLoader());
            Class classToLoad = Class.forName("server.Simulator", true, child);

            // Not sure how this works yet
            Class[] cArg = new Class[1];
            cArg[0] = Integer.class;

            Method method = classToLoad.getDeclaredMethod("start", cArg);
            Object instance = classToLoad.newInstance();
            Object result = method.invoke(instance, port);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void nonCLRun() {
        Simulator simulator = new Simulator();
        simulator.start();
    }

    public static void main(String[] args) {
        MultiSim multiSim = new MultiSim();
        multiSim.run();
    }

}