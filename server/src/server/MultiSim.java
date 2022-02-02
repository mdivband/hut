package server;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;

public class MultiSim {

    /*

    public void run() {
        // TODO maybe make this runnable?
        loadAndRunClass(8001);

        loadAndRunClass(8002);
    }

    private void loadAndRunClass(int port) {
        try {
            String jarName = "file://./sim"+port+".jar";
            URLClassLoader child = new URLClassLoader(new URL[]{new URL(jarName)}, MultiSim.class.getClassLoader());
            Class classToLoad = Class.forName("server.Simulator", true, child);

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

*/

    public void run() {
        try {
            /*
            URLClassLoader classLoader1 = new URLClassLoader(new URL[]{new URL("file:///mnt/280e3671-cfd4-48e7-b8f1-2192dd22c430/Files/TAS/HUT/GIT_LOCAL_ROOT/hut/server/hut8001.jar")}, null);
            URLClassLoader classLoader2 = new URLClassLoader(new URL[]{new URL("file:///mnt/280e3671-cfd4-48e7-b8f1-2192dd22c430/Files/TAS/HUT/GIT_LOCAL_ROOT/hut/server/hut8002.jar")}, null);

// Load with classLoader1
            Class<?> myClass1 = classLoader1.loadClass("server.Simulator");
            Constructor<?> constructor1 = myClass1.getConstructor();
            Object instance1 = constructor1.newInstance();
            System.out.println(instance1);
//Method m1 = myClass1.getMethod("start", String[].class); // get the method you want to call
            //Integer[] args1 = new Integer[1]; // the arguments. Change this if you want to pass different args
            //args1[0] = 8001;
            //m1.invoke(instance1, args1);  // invoke the method
            Method m1 = myClass1.getMethod("start");
            m1.invoke(instance1);

            Class<?> myClass2 = classLoader2.loadClass("server.Simulator");
            Constructor<?> constructor2 = myClass2.getConstructor();
            Object instance2 = constructor2.newInstance();
            Method m2 = myClass2.getMethod("start");
            m2.invoke(instance2);

            //Method m2 = myClass2.getMethod("main", String[].class); // get the method you want to call
            //Integer[] args2 = new Integer[1]; // the arguments. Change this if you want to pass different args
            //args2[0] = 8002;
            //m2.invoke(instance2, args2);  // invoke the method
             */

            /*
            URL[] classLoaderUrls = new URL[]{new URL("file:///mnt/280e3671-cfd4-48e7-b8f1-2192dd22c430/Files/TAS/HUT/GIT_LOCAL_ROOT/hut/server/hut8001.jar")};
            URLClassLoader urlClassLoader = new URLClassLoader(classLoaderUrls);
            Class<?> beanClass = urlClassLoader.loadClass("server.Simulator");
            Constructor<?> constructor = beanClass.getConstructor();
            Object beanObj = constructor.newInstance();
            Method method = beanClass.getMethod("start", Integer.class);
            method.invoke(beanObj, 8001);

            URL[] classLoaderUrls2 = new URL[]{new URL("file:///mnt/280e3671-cfd4-48e7-b8f1-2192dd22c430/Files/TAS/HUT/GIT_LOCAL_ROOT/hut/server/hut8002.jar")};
            URLClassLoader urlClassLoader2 = new URLClassLoader(classLoaderUrls2);
            Class<?> beanClass2 = urlClassLoader2.loadClass("server.Simulator");
            Constructor<?> constructor2 = beanClass2.getConstructor();
            Object beanObj2 = constructor2.newInstance();
            Method method2 = beanClass2.getMethod("start", Integer.class);
            method2.invoke(beanObj2, 8002);

             */


            ClassLoader loader = URLClassLoader.newInstance(
                    new URL[] { new URL("file:///mnt/280e3671-cfd4-48e7-b8f1-2192dd22c430/Files/TAS/HUT/GIT_LOCAL_ROOT/hut/server/hut8001.jar") },
                    getClass().getClassLoader()
            );
            Class<?> clazz = Class.forName("server.SimRunner", true, loader);
            Class<? extends Runnable> runClass = clazz.asSubclass(Runnable.class);
// Avoid Class.newInstance, for it is evil.
            Constructor<? extends Runnable> ctor = runClass.getConstructor();
            Runnable doRun = ctor.newInstance();
            doRun.run();


            // TODO try:
            //JarClassLoader jcl = new JarClassLoader();
            //jcl.add("myjar.jar"); // Load jar file
            //jcl.add(new URL("http://myserver.com/myjar.jar")); // Load jar from a URL
            //jcl.add(new FileInputStream("myotherjar.jar")); // Load jar file from stream
            //jcl.add("myclassfolder/"); // Load class folder
            //jcl.add("myjarlib/"); // Recursively load all jar files in the folder/sub-folder(s)
            //
            //JclObjectFactory factory = JclObjectFactory.getInstance();
            //// Create object of loaded class
            //Object obj = factory.create(jcl, "mypackage.MyClass");


        } catch (Exception e) {
            e.printStackTrace();
        }

        /*
        try {
            SimRunner runner1 = new SimRunner(8001);
            new Thread(runner1::start).start();

            SimRunner runner2 = new SimRunner(8002);
            new Thread(runner2::start).start();
        } catch (Exception e) {
            System.out.println(e);
        }

         */
    }

    public static void main(String[] args) {
        MultiSim multiSim = new MultiSim();
        multiSim.run();
    }



}