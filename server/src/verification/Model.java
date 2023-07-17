package verification;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Model {
    private String prismDir = "./libs/prism-4.8/bin/prism.bat";
    private String prismModelDir;
    private String prismOutputDir;
    private String modelName;
    private String webRef;
    private double[][] droneRep;
    private double[][] taskRep;
    private boolean debug = false;
    private Process thisProcess;
    private ProcessBuilder thisPb;

    public Model(String webRef, double[][] droneRep, double[][] taskRep, String modelName) {
        this.webRef = webRef;
        this.droneRep = droneRep;
        this.taskRep = taskRep;

        prismModelDir = webRef+"/verification";

        this.prismOutputDir = prismModelDir + "/" + modelName + ".txt";
        this.modelName = modelName;
    }

    public ArrayList<String> call() throws IOException, InterruptedException {
        // Process parameters
        //  Note the hardcoded length 8 line

        int[] taskConfiguration = new int[7];
        int[] boundaries = new int[]{899, 799, 699, 599, 499, 399, 299};
        int[] taskBoundaries = new int[]{799, 699, 599, 499, 399, 299};
        double[] newTaskRep = new double[taskRep.length];
        for (int i=0; i<droneRep.length; i++) {
            int dist = (int) droneRep[i][0];
            int effectiveDist = 0;
            for (int b=0; b<7; b++) {
                if (dist > boundaries[b]) {
                    effectiveDist = 7 - b;
                    break;
                }
            }
            droneRep[i][0] = effectiveDist;

            int taskDist = (int) droneRep[i][1];
            int effectiveTaskDist = 1;
            for (int b=0; b<6; b++) {
                if (taskDist > taskBoundaries[b]) {
                    effectiveTaskDist = 7 - b;
                    break;
                }
            }
            droneRep[i][1] = effectiveTaskDist;

            if (droneRep[i][2] < 0.15) {
                droneRep[i][2] = 3;
            } else if (droneRep[i][2] < 0.4) {
                droneRep[i][2] = 0;
            } else if (droneRep[i][3] < 0.8) {
                droneRep[i][2] = 1;
            } else {
                droneRep[i][2] = 2;
            }

            if (droneRep[i][0] == 0) {
                droneRep[i][3] = 0;
                droneRep[i][5] = 0;
            }
        }

        for (int j=0; j<taskRep.length; j++) {
            int dist = (int) taskRep[j][0];
            int effectiveDist = 0;
            for (int b=0; b<7; b++) {
                if (dist > boundaries[b]) {
                    effectiveDist = 7 - b;
                    break;
                }
            }
            newTaskRep[j] = Math.min(effectiveDist + 1, 6);
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

        for (int r = 0; r < 7; r++) {
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
        BufferedReader bf = new BufferedReader(new FileReader(prismModelDir + "/template_" + modelName + ".txt"));
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

        File oldFile = new File(prismModelDir + "/" + modelName + ".txt");
        File newFile = new File(prismModelDir + "/" + modelName + ".pm");
        boolean success = oldFile.renameTo(newFile);
        System.out.println("renaming success? = " + success);

        ArrayList<String> cmd = new ArrayList<>();
        cmd.add(prismDir);
        cmd.add(prismModelDir + "/" + modelName + ".pm");
        cmd.add(prismModelDir + "/property.pctl");
        cmd.add("-prop");
        cmd.add("bounded");
        cmd.add("-sim");
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

        for (int j=0; j<7; j++) {
            cmdSb.append("initTaskP").append(j + 1).append('=').append(taskConfiguration[j]).append(",");
        }

        cmdSb.append("T=300:300:30000");
        cmd.add(cmdSb.toString());

        ProcessBuilder processBuilder = new ProcessBuilder(cmd);
        processBuilder.redirectErrorStream(true);
        thisPb = processBuilder.inheritIO();
        thisProcess = processBuilder.start();

        if (debug) {
            try {
                String s;
                BufferedReader stdOut = new BufferedReader(new
                        InputStreamReader(thisProcess.getInputStream()));
                while ((s = stdOut.readLine()) != null) {
                    System.out.println(s);
                }

                int exitCode = thisProcess.waitFor();
                System.out.println("RUN - Finished with exit code " + exitCode);
            } catch (InterruptedException e) {
                thisProcess.destroy();
                System.out.println("destroyed");
                throw e;
            }
        }



        int exitCode = thisProcess.waitFor();
        System.out.println("RUN - Finished with exit code " + exitCode);

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
        System.out.println(thisProcess.info());
        System.out.println("proc before -> " + thisProcess);

        thisProcess.descendants().forEach(ProcessHandle::destroy);
        Process code1 = thisProcess.destroyForcibly();

        try {
            int code2 = thisProcess.waitFor();
            System.out.println(thisProcess.info());
            System.out.println(code1 + " , " + code2);
            System.out.println();
        } catch (Exception e) {
            System.out.println("excep");
        }

        System.out.println("proc after -> " + thisProcess);
        System.out.println();

    }


}
