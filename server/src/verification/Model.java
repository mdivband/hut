package verification;

import server.ModelCaller;
import server.Simulator;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.FileHandler;
import java.util.logging.Logger;

public class Model {
    private Logger LOGGER = Logger.getLogger(Model.class.getName());
    private String prismDir = "./libs/prism-4.8/bin/prism.bat";
    private String prismModelDir;
    private String prismOutputDir;
    private String modelName;
    private String webRef;
    private double[][] droneRep;
    private double[][] taskRep;
    private boolean debug = true;
    private Process thisProcess;
    private ProcessBuilder thisPb;
    private int samples = 1000;

    public Model(String webRef, double[][] droneRep, double[][] taskRep, String modelName) {
        this.webRef = webRef;
        this.droneRep = droneRep;
        this.taskRep = taskRep;
        LOGGER.addHandler(Simulator.instance.getLoggingFileHandler());
        prismModelDir = webRef+"/verification";

        this.prismOutputDir = prismModelDir + "/" + modelName + ".txt";
        this.modelName = modelName;
    }

    public void setup(int samples){
        this.samples = samples;
    }

    public ArrayList<String> call() throws IOException, InterruptedException {
        // Process parameters
        //  Note the hardcoded length 8 line

        int[] taskConfiguration = new int[8];
        int[] boundaries = new int[]{899, 799, 699, 599, 499, 399, 299, 199};
        int[] taskBoundaries = new int[]{899, 799, 699, 599, 499, 399, 299, 199};
        double[] newTaskRep = new double[taskRep.length];
        for (int i=0; i<droneRep.length; i++) {
            int dist = (int) droneRep[i][0];
            int effectiveDist = 0;
            for (int b=0; b<8; b++) {
                if (dist > boundaries[b]) {
                    effectiveDist = 8 - b;
                    break;
                }
            }
            droneRep[i][0] = effectiveDist;

            int taskDist = (int) droneRep[i][1];
            int effectiveTaskDist = 1;
            for (int b=0; b<8; b++) {
                if (taskDist > taskBoundaries[b]) {
                    effectiveTaskDist = 8 - b;
                    break;
                }
            }
            droneRep[i][1] = effectiveTaskDist;

            if (droneRep[i][2] < 0.15) {
                droneRep[i][2] = 4;
            } else if (droneRep[i][2] < 0.4) {
                droneRep[i][2] = 0;
            } else if (droneRep[i][2] < 0.6) {
                droneRep[i][2] = 1;
            } else if (droneRep[i][3] < 0.8) {
                droneRep[i][2] = 2;
            } else {
                droneRep[i][2] = 3;
            }

            if (droneRep[i][0] == 0) {
                droneRep[i][3] = 0;
                droneRep[i][5] = 0;
            }
        }

        for (int j=0; j<taskRep.length; j++) {
            int dist = (int) taskRep[j][0];
            int effectiveDist = 0;
            for (int b=0; b<8; b++) {
                if (dist > boundaries[b]) {
                    effectiveDist = 8 - b;
                    break;
                }
            }
            newTaskRep[j] = effectiveDist;
        }

        int[][] droneRepAsInt = new int[droneRep.length][droneRep[0].length];
        for (int row = 0; row < droneRep.length; row++) {
            for (int col = 0; col < droneRep[0].length; col++) {
                droneRepAsInt[row][col] = (int) droneRep[row][col];
            }
        }

        int[] taskRepAsInt = new int[taskRep.length];
        for (int elem = 0; elem < taskRep.length; elem++) {
            taskRepAsInt[elem] = (int) newTaskRep[elem];
        }

        for (int r = 0; r < 8; r++) {
            int finalR = r;
            taskConfiguration[r] = (int) Arrays.stream(taskRepAsInt).filter(c -> c == finalR).count();
        }

        StringBuilder parameterBuilder = new StringBuilder();
        StringBuilder agentBuilder = new StringBuilder();
        for (int i = 1; i<droneRep.length; i++) {
            parameterBuilder
                    .append("const int initPlace").append(i).append(";").append("\n")
                    .append("const int initTaskLoc").append(i).append(";").append("\n")
                    .append("const int initBattery").append(i).append(";").append("\n")
                    .append("const int initDelivered").append(i).append(";").append("\n")
                    .append("const int initCharge").append(i).append(";").append("\n")
                    .append("const int initReturn").append(i).append(";").append("\n")
                    .append("const int initAlive").append(i).append(";").append("\n")
                    .append("const int initTurning").append(i).append(";").append("\n")
                    .append("\n");

            agentBuilder.append("module agent").append(i).append("=agent0[").append("\n")
                    .append("drainC0=drainC").append(i).append(",").append("\n")
                    .append("moveC0=moveC").append(i).append(",").append("\n")
                    .append("place0=place").append(i).append(",").append("\n")
                    .append("taskLoc0=taskLoc").append(i).append(",").append("\n")
                    .append("battery0=battery").append(i).append(",").append("\n")
                    .append("ready0=ready").append(i).append(",").append("\n")
                    .append("return0=return").append(i).append(",").append("\n")
                    .append("delivered0=delivered").append(i).append(",").append("\n")
                    .append("charge0=charge").append(i).append(",").append("\n")
                    .append("alive0=alive").append(i).append(",").append("\n")
                    .append("turning0=turning").append(i).append(",").append("\n")
                    .append("initPlace0=initPlace").append(i).append(",").append("\n")
                    .append("initTaskLoc0=initTaskLoc").append(i).append(",").append("\n")
                    .append("initBattery0=initBattery").append(i).append(",").append("\n")
                    .append("initDelivered0=initDelivered").append(i).append(",").append("\n")
                    .append("initCharge0=initCharge").append(i).append(",").append("\n")
                    .append("initReturn0=initReturn").append(i).append(",").append("\n")
                    .append("initAlive0=initAlive").append(i).append(",").append("\n")
                    .append("initTurning0=initTurning").append(i).append("\n")
                    .append("]").append("\n")
                    .append("endmodule").append("\n").append("\n");
        }

        String paramText = parameterBuilder.toString();
        String agentText = agentBuilder.toString();

        ArrayList<String> modelLines = new ArrayList<>();
        // BufferedReader bf = new BufferedReader(new FileReader(prismModelDir + "/template_" + modelName + ".txt"));
        // 20230806_1739h - Ayo Abioye (aoa1f15@soton.ac.uk) removing port number from modelName for template .txt file
        String modelNameNoPort = "";
        if (modelName.contains("curr")){
            modelNameNoPort = "curr";
        } else if (modelName.contains("add1")) {
            modelNameNoPort = "add1";
        } else {
            modelNameNoPort = "rem1";
        }
        BufferedReader bf = new BufferedReader(new FileReader(prismModelDir + "/template_" + modelNameNoPort + ".txt"));
        String line;
        while((line = bf.readLine()) != null){
            modelLines.add(line);
        }
        bf.close();

        BufferedWriter bw = new BufferedWriter(new FileWriter(prismModelDir + "/" + modelName + ".txt"));
        for (String l : modelLines) {
            if (l.contains("ADD TEXT FOR PARAMETERS")) {
                bw.write(paramText + "\n");
            } else if (l.contains("ADD TEXT FOR AGENTS")) {
                bw.write(agentText + "\n");
            } else {
                bw.write(l + "\n");
            }
        }
        bw.close();
        File contestedModel = new File(prismModelDir + "/" + modelName + ".pm");
        contestedModel.delete();
        File oldFile = new File(prismModelDir + "/" + modelName + ".txt");
        File newFile = new File(prismModelDir + "/" + modelName + ".pm");
        boolean success = oldFile.renameTo(newFile);
        //System.out.println("renaming success? = " + success);

        ArrayList<String> cmd = new ArrayList<>();
        cmd.add(prismDir);
        cmd.add(prismModelDir + "/" + modelName + ".pm");
        cmd.add(prismModelDir + "/property.pctl");
        // TODO precision here
        cmd.add("-prop");
        cmd.add("bounded");
        cmd.add("-sim");
        cmd.add("-simconf");
        cmd.add("0.0001");
        cmd.add("-simsamples");
        cmd.add(String.valueOf(samples));
        cmd.add("-exportresults");
        cmd.add(prismOutputDir);
        cmd.add("-const");

        StringBuilder cmdSb = new StringBuilder();
        for (int i=0; i<droneRep.length; i++) {
            cmdSb
                    .append("initPlace").append(i).append("=").append(droneRepAsInt[i][0]).append(",")
                    .append("initTaskLoc").append(i).append("=").append(droneRepAsInt[i][1]).append(",")
                    .append("initBattery").append(i).append("=").append(droneRepAsInt[i][2]).append(",")
                    .append("initDelivered").append(i).append("=").append(droneRepAsInt[i][3]).append(",")
                    .append("initCharge").append(i).append("=").append(droneRepAsInt[i][4]).append(",")
                    .append("initReturn").append(i).append("=").append(droneRepAsInt[i][5]).append(",")
                    .append("initAlive").append(i).append("=").append(droneRepAsInt[i][6]).append(",")
                    .append("initTurning").append(i).append("=").append(droneRepAsInt[i][7]).append(",");
        }

        for (int j=0; j<8; j++) {
            cmdSb.append("initTaskP").append(j + 1).append('=').append(taskConfiguration[j]).append(",");
        }

        cmdSb.append("T=200:200:24000");
        cmd.add(cmdSb.toString());

        ProcessBuilder processBuilder = new ProcessBuilder(cmd);
        if (debug) {
            processBuilder.redirectErrorStream(true);
        }
        thisPb = processBuilder.inheritIO();

        if (!debug) {
            thisPb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        }

        thisProcess = processBuilder.start();

        try {
            if (debug) {
                String s;
                BufferedReader stdOut = new BufferedReader(new
                        InputStreamReader(thisProcess.getInputStream()));
                while ((s = stdOut.readLine()) != null) {
                    System.out.println(s);
                }
            }

            int exitCode = thisProcess.waitFor();
            //System.out.println("RUN - Finished with exit code " + exitCode);
        } catch (InterruptedException e) {
            thisProcess.destroy();
            //System.out.println("destroyed");
            throw e;
        }



        int exitCode = thisProcess.waitFor();
        //System.out.println("RUN - Finished with exit code " + exitCode);

        ArrayList<String> outLines = new ArrayList<>();
        bf = new BufferedReader(new FileReader(prismOutputDir));
        while((line = bf.readLine()) != null){
            outLines.add(line);
        }
        bf.close();

        /*
        bw = new BufferedWriter(new FileWriter(prismOutputDir));
        for (String l : outLines.subList(1, outLines.size())) {
            bw.write(l);
        }
        bw.close();
        */

        File oldModel = new File(prismModelDir + "/" + modelName + ".pm");
        oldModel.delete();

        outLines.remove(0);
        return outLines;

    }

    public void cancel() {
        try {
            thisProcess.descendants().forEach(ProcessHandle::destroy);
        } catch (Exception e) {
            //System.out.println("Did not need to destroy descendants");
        }

        try {
            thisProcess.destroyForcibly();
        } catch (Exception e) {
            //System.out.println("Did not need to destroy process");
        }

    }


    public ArrayList<String> fakeCall(int delayTime, int delayBound) throws InterruptedException{
        int delay = delayTime + (2 * Simulator.instance.getRandom().nextInt(delayBound) - delayBound);
        //System.out.println("Waiting for " + delay + " seconds");
        Thread.sleep(delay * 1000L);
        //System.out.println("Done");
        ArrayList<String> fakeResult = new ArrayList<>(1);
        int fakePrediction = Simulator.instance.getRandom().nextInt(10000, 20000);
        fakeResult.add(fakePrediction + "\t 1");
        return fakeResult;
    }
}
