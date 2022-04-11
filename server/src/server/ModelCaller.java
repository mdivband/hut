package server;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class ModelCaller {
    private final double result = 1;
    private boolean ready = true;
    private Thread currentTask;
    private double counter;

    public void startThread() {
        if (!ready) {//(currentTask != null && currentTask.isAlive()) {
            currentTask.interrupt();
        }
        currentTask = new Thread(this::run);
        currentTask.start();
    }

    public boolean run() {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("python3", "toCall.py");
            processBuilder.redirectErrorStream(true);
            ready = false;
            Process process = processBuilder.start();
            //List<String> results = readProcessOutput(process.getInputStream(), process.getErrorStream());
            OutputStream o = process.getOutputStream();
            String s;
            BufferedReader stdOut = new BufferedReader(new
                    InputStreamReader(process.getInputStream()));
            while ((s = stdOut.readLine()) != null) {
                System.out.println(s);
            }

            int exitCode = process.waitFor();
            counter++;
            System.out.println("RUN - Finished with exit code " + exitCode);
            ready = true;

        } catch (IOException e) {
            System.out.println("RUN - An IO error occured.");
            //e.printStackTrace();
            return false;
        } catch (InterruptedException e) {
            System.out.println("RUN - Process interrupted.");
            return false;
        }
        currentTask.interrupt();
        return true;
    }

    private List<String> readProcessOutput(InputStream inputStream, InputStream errorStream) throws IOException {
        List<String> ret = new ArrayList<>();
        BufferedReader stdInput = new BufferedReader(new
                InputStreamReader(inputStream));

        BufferedReader stdError = new BufferedReader(new
                InputStreamReader(errorStream));

        // read the output from the command
        System.out.println("Here is the standard output of the command:\n");
        String s;
        while ((s = stdInput.readLine()) != null) {
            System.out.println(s);
        }

        // read any errors from the attempted command
        System.out.println("Here is the standard error of the command (if any):\n");
        while ((s = stdError.readLine()) != null) {
            System.out.println(s);
        }
        return ret;

    }

    public double getResult() {
        return counter;
        //return result;
    }

    public boolean isReady() {
        return ready;
    }

    public void setReady(boolean ready) {
        this.ready = ready;
    }


}
