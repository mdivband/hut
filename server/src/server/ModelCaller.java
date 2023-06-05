package server;

import verification.Model;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class ModelCaller {
    private Thread currentThread = null;
    private Thread underThread = null;
    private Thread overThread = null;
    ArrayList<ArrayList<double[][]>> currentConfig = null;
    private Logger LOGGER = Logger.getLogger(ModelCaller.class.getName());
    private String style = "justOn";
    private String webRef;
    private int currentSalt;
    private double startTime;

    public void reset() {
        style = "justOn";
        currentThread = null;
        underThread = null;
        overThread = null;
        Simulator.instance.getState().setMissionSuccessChance(-1);
        Simulator.instance.getState().setMissionSuccessOverChance(-1);
        Simulator.instance.getState().setMissionSuccessUnderChance(-1);
    }

    /**
     * Starts the first run, which in turn runs 1 over and 1 under.
     * Also side effects the chances in State.
     */
    public void startThread(String webRef, ArrayList<ArrayList<double[][]>> config) {
        currentSalt = Simulator.instance.getRandom().nextInt(10000);
        startTime = Simulator.instance.getState().getTime();
        this.webRef = webRef;
        // TODO edit the prediction python to take arguments of files, then we can run all 3 in parallel
        if (currentThread != null) {
            //System.out.println("INTERRUPTING");
            //System.out.println("dest");
            currentThread.interrupt();
            currentThread = null;
        }
        if (underThread != null) {
            underThread.interrupt();
            underThread = null;
        }
        if (overThread != null) {
            overThread.interrupt();
            overThread = null;
        }

        currentConfig = config;
        currentThread = new Thread(this::runOn);
        // TODO not properly interrupted
        currentThread.start();

        if (style.equals("parallel")) {
            underThread = new Thread(this::runUnder);
            underThread.start();
            overThread = new Thread(this::runOver);
            overThread.start();
        }

        Simulator.instance.getState().setMissionSuccessChance(-1);
        Simulator.instance.getState().setMissionSuccessOverChance(-1);
        Simulator.instance.getState().setMissionSuccessUnderChance(-1);

        Simulator.instance.getState().setEstimatedCompletionTime(-1);
        Simulator.instance.getState().setEstimatedCompletionOverTime(-1);
        Simulator.instance.getState().setEstimatedCompletionUnderTime(-1);

    }

    /**
     * Run the script for the model. Used for every run
     *
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
    private ArrayList<String> runScript(String modelName, int procIndex, ArrayList<double[][]> currentConfigTuple) throws IOException, InterruptedException {
        Model model = new Model(webRef, currentConfigTuple.get(0), currentConfigTuple.get(1), modelName);
        return model.call();
    }

    /**
     * Run a model for the actual number of drones
     */
    public void runOn() {
        try {
            LOGGER.info(String.format("%s; MDSTO; Model starting on the current number of agents (#); %s", Simulator.instance.getState().getTime(), Simulator.instance.getState().getAgents().size()));

            double startTime = System.nanoTime();
            ArrayList<String> output = runScript("curr", 1, currentConfig.get(1));

            double boundedResult = readTimeBoundedResultAsTime(output, 0.95);

            Simulator.instance.getState().setEstimatedCompletionTime(boundedResult * 100);

            double elapsed = (System.nanoTime() - startTime) / 10E8;
            LOGGER.info(String.format("%s; MDDNO; Model done on the current number of agents in time (result, elapsed time); %s; %s", Simulator.instance.getState().getTime(), boundedResult, elapsed));
        } catch (IOException e) {
            System.out.println("RUN - An IO error occurred.");
        } catch (InterruptedException e) {
            System.out.println("RUN - Process interrupted.");
        }

        if (style.equals("series")) {
            overThread = new Thread(this::runOver);
            overThread.start();
        }
    }

    /**
     * Run a model for one drone over this number
     */

    private void runOver() {
        //addAgentToParameters();
        try {
            LOGGER.info(String.format("%s; MDSTV; Model starting at 1 over the current number of agents;", Simulator.instance.getState().getTime()));

            double startTime = System.nanoTime();
            ArrayList<String> output = runScript("add1", 2, currentConfig.get(2));

            double boundedResult = readTimeBoundedResultAsTime(output, 0.95);

            Simulator.instance.getState().setEstimatedCompletionOverTime(boundedResult * 100);

            double elapsed = (System.nanoTime() - startTime) / 10E8;
            LOGGER.info(String.format("%s; MDDNV; Model done at 1 over the current number of agents in time (result, elapsed time); %s; %s", Simulator.instance.getState().getTime(), boundedResult, elapsed));
        } catch (IOException e) {
            System.out.println("RUN OVER - An IO error occurred.");
        } catch (InterruptedException e) {
            System.out.println("RUN OVER - Process interrupted.");
        }
        //overThread.interrupt();
        overThread = null;

        if (style.equals("series")) {
            underThread = new Thread(this::runUnder);
            underThread.start();
        }
    }



    /**
     * Run a model for 1 drone under this number
     */

    private void runUnder() {
        try {
            LOGGER.info(String.format("%s; MDSTU; Model starting at 1 under the current number of agents;", Simulator.instance.getState().getTime()));

            double startTime = System.nanoTime();
            ArrayList<String> output = runScript("rem1", 0, currentConfig.get(0));

            double boundedResult = readTimeBoundedResultAsTime(output, 0.95);

            double elapsed = (System.nanoTime() - startTime) / 10E8;
            Simulator.instance.getState().setEstimatedCompletionUnderTime(boundedResult * 100);
            LOGGER.info(String.format("%s; MDDNU; Model done at 1 under the current number of agents in time (result, elapsed time); %s; %s", Simulator.instance.getState().getTime(), boundedResult, elapsed));
        } catch (IOException e) {
            System.out.println("RUN UNDER - An IO error occurred.");
        } catch (InterruptedException e) {
            System.out.println("RUN UNDER - Process interrupted.");
        }
        //underThread.interrupt();
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
    private double readResult(String fileName) {
        return -1;
        /*
        try {
            BufferedReader reader = new BufferedReader(
                    new FileReader(webRef+"/ModelFiles/"+fileName)
            );
            double d = Double.parseDouble(reader.readLine());
            reader.close();
            return d;
        } catch (Exception e) {
            System.out.println("ERROR READING RESULT. RETURNING 0");
            return 0;
        }

         */
    }

    /**
     * Read result from file. In future this may take an argument in future
     * @return
     */
    private double readTimeBoundedResult(String fileName) {
        try {
            BufferedReader reader = new BufferedReader(
                    new FileReader(webRef + "/ModelFiles/" + fileName)
            );

            //e.g 18000	0.998
            String s;
            double d = 0;
            while ((s = reader.readLine()) != null) {
                if (s.contains("18000")) {
                    d = Double.parseDouble(s.split("\t")[1]);
                    break;
                }
            }
            reader.close();
            return d;
        } catch (FileNotFoundException e) {
            System.out.println("File " + fileName + " not found");
            return 0;
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("ERROR READING RESULT. RETURNING 0");
            return 0;
        }
    }

    /**
     * Read result from file. In future this may take an argument in future
     * @return
     */
    private double readTimeBoundedResultAsTime(ArrayList<String> lines, double confidence) {
        //e.g 18000	0.998
        String s;
        int timeToComplete = 0;
        for (String line : lines) {
            String[] split = line.split("\t");
            if (Double.parseDouble(split[1]) >= confidence) {
                timeToComplete = Integer.parseInt(split[0]);
                break;
            }
        }
        if (timeToComplete == 0) {
            timeToComplete = 30000;
        }
        return (int) (startTime + (timeToComplete / 10));
    }

    public void setStyle(String modelStyle) {
        style = modelStyle;
    }

}
