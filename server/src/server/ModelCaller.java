package server;

import server.model.agents.AgentVirtual;

import java.io.*;

public class ModelCaller {
    private double result = 1;
    private Thread currentThread = null;
    private Thread underThread;
    private Thread overThread;

    /**
     * Starts the first run, which in turn runs 1 over and 1 under.
     * Also side effects the chances in State.
     */
    public void startThread() {
        // TODO edit the prediction python to take arguments of files, then we can run all 3 in parallel
        if (currentThread != null) {
            currentThread.interrupt();
            underThread.interrupt();
            overThread.interrupt();
        }

        result = -1;
        Simulator.instance.getState().setMissionSuccessChance(-1);
        Simulator.instance.getState().setMissionSuccessOverChance(-1);
        Simulator.instance.getState().setMissionSuccessUnderChance(-1);

        currentThread = new Thread(this::runOn);
        currentThread.start();
    }

    /**
     * Run the script for the model. Used for every run
     * @throws IOException
     * @throws InterruptedException
     */
    private void runScript() throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder("python3", "integration.py");
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        String s;
        BufferedReader stdOut = new BufferedReader(new
                InputStreamReader(process.getInputStream()));
        while ((s = stdOut.readLine()) != null) {
            System.out.println(s);
        }
        int exitCode = process.waitFor();
        System.out.println("RUN - Finished with exit code " + exitCode);
    }

    /**
     * Run a model for the actual number of drones
     */
    public void runOn() {
        try {
            runScript();
            result = readResult();
            Simulator.instance.getState().setMissionSuccessChance(result * 100);
            System.out.println("on: " + result);
        } catch (IOException e) {
            System.out.println("RUN - An IO error occurred.");
        } catch (InterruptedException e) {
            System.out.println("RUN - Process interrupted.");
        }
        currentThread.interrupt();
        currentThread = null;

        overThread = new Thread(this::runOver);
        overThread.start();
    }

    /**
     * Run a model for one drone over this number
     */
    private void runOver() {
        addAgentToParameters();
        try {
            runScript();
            double overResult = readResult();
            Simulator.instance.getState().setMissionSuccessOverChance(overResult * 100);
            System.out.println("over: " + overResult);
        } catch (IOException e) {
            System.out.println("RUN OVER - An IO error occurred.");
        } catch (InterruptedException e) {
            System.out.println("RUN OVER - Process interrupted.");
        }
        overThread.interrupt();
        overThread = null;

        underThread = new Thread(this::runUnder);
        underThread.start();
    }

    /**
     * Run a model for 1 drone under this number
     */
    private void runUnder() {
        removeAgentFromParameters();
        try {
            runScript();
            double underResult = readResult();
            Simulator.instance.getState().setMissionSuccessUnderChance(underResult * 100);
            System.out.println("under: " + underResult);
        } catch (IOException e) {
            System.out.println("RUN UNDER - An IO error occurred.");
        } catch (InterruptedException e) {
            System.out.println("RUN UNDER - Process interrupted.");
        }
        underThread.interrupt();
        underThread = null;
    }

    /**
     * Add an extra agent to the drones file
     */
    private void addAgentToParameters() {
        try {
            BufferedReader reader = new BufferedReader(
                    new FileReader("drones.txt")
            );
            StringBuilder sb =  new StringBuilder();
            String s;
            while ((s = reader.readLine()) != null) {
                sb.append(s).append("\n");
            }
            reader.close();

            FileWriter myWriter = new FileWriter("drones.txt");
            String rep = "0.0 0.0 1.0 1 0 1 1 \n";
            sb.append(rep);
            myWriter.write(sb.toString());
            //System.out.println("produced: " + sb);
            //System.out.println("Wrote to drones.txt");
            myWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Remove 2 agents (the just added one, and the last one in the list after)
     */
    private void removeAgentFromParameters() {
        try {
            BufferedReader reader = new BufferedReader(
                    new FileReader("drones.txt")
            );
            StringBuilder sb =  new StringBuilder();
            String s;
            int numAgents = Simulator.instance.getState().getAgents().size(); // We have also added an extra one (+1)
            int i = 0;
            while ((s = reader.readLine()) != null) {
                i++;
                if (i < numAgents - 1) {
                    sb.append(s).append("\n");
                } else {
                    break;
                }

            }

            //System.out.println("produced: " + sb);
            reader.close();

            FileWriter myWriter = new FileWriter("drones.txt");
            myWriter.write(sb.toString());
            //System.out.println("Wrote to drones.txt");
            myWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Read result from file. In future this may take an argument in future
     * @return
     */
    private double readResult() {
        try {
            BufferedReader reader = new BufferedReader(
                    new FileReader("results.txt")
            );
            double d = Double.parseDouble(reader.readLine());
            reader.close();
            return d;
        } catch (Exception e) {
            System.out.println("ERROR READING RESULT. RETURNING 0");
            return 0;
        }
    }

    public double getResult() {
        return 100 * result;
    }

}
