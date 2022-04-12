package server;

import server.model.agents.AgentVirtual;

import java.io.*;

public class ModelCaller {
    private double result = 1;
    private double overResult = 1;
    private double underResult = 1;
    //private boolean ready = true;
    private Thread currentThread = null;
    private Thread underThread;
    private Thread overThread;

    public void startThread() {
        //if (!ready && currentThread != null) {//(currentTask != null && currentTask.isAlive()) {
        if (currentThread != null) {
            currentThread.interrupt();
            underThread.interrupt();
            overThread.interrupt();
        }

        currentThread = new Thread(this::run);
        currentThread.start();
    }

    public boolean run() {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("python3", "integration.py");
            processBuilder.redirectErrorStream(true);
            //ready = false;
            Process process = processBuilder.start();
            //String s;
            //BufferedReader stdOut = new BufferedReader(new
            //        InputStreamReader(process.getInputStream()));
            //while ((s = stdOut.readLine()) != null) {
            //    System.out.println(s);
            //}
            int exitCode = process.waitFor();
            result = readResult();
            Simulator.instance.getState().setMissionSuccessChance(result * 100);
            System.out.println("on: " + result);
            System.out.println("RUN - Finished with exit code " + exitCode);
            //ready = true;

        } catch (IOException e) {
            System.out.println("RUN - An IO error occurred.");
            return false;
        } catch (InterruptedException e) {
            System.out.println("RUN - Process interrupted.");
            return false;
        }
        currentThread.interrupt();
        currentThread = null;
        System.out.println("int");

        overThread = new Thread(this::runOver);
        overThread.start();
        //runOver();

        return true;
    }

    private void runOver() {
        addAgentToParameters();
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("python3", "integration.py");
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            //String s;
            //BufferedReader stdOut = new BufferedReader(new
            //        InputStreamReader(process.getInputStream()));
            //while ((s = stdOut.readLine()) != null) {
            //    System.out.println(s);
            //}
            int exitCode = process.waitFor();
            overResult = readResult();
            Simulator.instance.getState().setMissionSuccessOverChance(overResult * 100);
            System.out.println("over: " + overResult);
            System.out.println("RUN OVER - Finished with exit code " + exitCode);
        } catch (IOException e) {
            System.out.println("RUN OVER - An IO error occured.");
        } catch (InterruptedException e) {
            System.out.println("RUN OVER - Process interrupted.");
        }
        overThread.interrupt();
        overThread = null;
        System.out.println("int2");

        underThread = new Thread(this::runUnder);
        underThread.start();
    }

    private void runUnder() {
        removeAgentFromParameters();
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("python3", "integration.py");
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            //String s;
            //BufferedReader stdOut = new BufferedReader(new
            //        InputStreamReader(process.getInputStream()));
            //while ((s = stdOut.readLine()) != null) {
            //    System.out.println(s);
            //}
            underResult = readResult();
            Simulator.instance.getState().setMissionSuccessUnderChance(underResult * 100);
            System.out.println("under: " + underResult);
            int exitCode = process.waitFor();
            System.out.println("RUN UNDER - Finished with exit code " + exitCode);
        } catch (IOException e) {
            System.out.println("RUN UNDER - An IO error occured.");
        } catch (InterruptedException e) {
            System.out.println("RUN UNDER - Process interrupted.");
        }
        underThread.interrupt();
        underThread = null;
    }

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
            System.out.println("produced: " + sb);
            System.out.println("Wrote to drones.txt");
            myWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

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

            System.out.println("produced: " + sb);
            reader.close();

            FileWriter myWriter = new FileWriter("drones.txt");
            myWriter.write(sb.toString());
            System.out.println("Wrote to drones.txt");
            myWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

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
/*
    public boolean isReady() {
        return ready;
    }

    public void setReady(boolean ready) {
        this.ready = ready;
    }

 */


}
