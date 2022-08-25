package server;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * We use this to run the simulator. This is essential for using classloaders for parallel runs in the same JVM
 * @author William Hunt
 */
public class SimRunner implements Runnable {
    private final int port;
    private Simulator simulator;

    public SimRunner() {
        this.port = 8000;
    }

    public SimRunner(Integer port) {
        this.port = port;
    }

    @Override
    public void run() {
        simulator = new Simulator();
        simulator.start(port);
    }

    public static void main(String[] args) {
        SimRunner simRunner = new SimRunner(44101);
        simRunner.run();
    }
}
