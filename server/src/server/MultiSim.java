package server;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;

/**
 * To run multiple instances of the simulator in the same JVM. THis is a primitive version for demonstration purposes
 *  I have a separate project for running a parallel simulator and redirector; might integrate that into this eventually,
 *  otherwise ask me -WH
 * @author William Hunt
 */
public class MultiSim {
    /**
     * Run method for the classloader. Opens a jar and looks for server.SimRunner
     * Here I have my own PC's absolute path reference. In the HutHandler code I have separate, we do this more intelligently
     */
    public void run() {
        try {
            ClassLoader loader = URLClassLoader.newInstance(
                    new URL[] { new URL("file:///mnt/280e3671-cfd4-48e7-b8f1-2192dd22c430/Files/TAS/HUT/GIT_LOCAL_ROOT/hut/server/hut8001.jar") },
                    getClass().getClassLoader()
            );
            Class<?> clazz = Class.forName("server.SimRunner", true, loader);
            Class<? extends Runnable> runClass = clazz.asSubclass(Runnable.class);
            Constructor<? extends Runnable> ctor = runClass.getConstructor();
            Runnable doRun = ctor.newInstance();
            doRun.run();


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        MultiSim multiSim = new MultiSim();
        multiSim.run();
    }

}