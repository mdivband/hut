package tool;

import server.model.Coordinate;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Random;

/**
 * A messy class to generate scenario files. This isn't a complete, self-contained tool, more a set of methods to help
 */
public class ScenarioGenerator {
    private double[] home;
    private Random random;

    public ScenarioGenerator(double[] home) {
        this.home = home;
        this.random = new Random();
    }

    public static void main(String[] args) {
        double[] home = new double[]{50.92893260355405, -1.4088842718500894};
        ScenarioGenerator sg = new ScenarioGenerator(home);

        HashMap<Integer, Integer> distMap = new HashMap<>();
        distMap.put(299, 2);
        distMap.put(399, 2);
        distMap.put(499, 2);
        distMap.put(599, 1);
        distMap.put(699, 1);
        distMap.put(799, 0);
        distMap.put(899, 0);

        sg.generateTasks(distMap);
    }

    private HashMap<Integer, Integer> convertToDistanceDict(double[][] coords) {
        double[] dists = new double[coords.length];
        final int R = 6371;
        for (int i = 0; i < coords.length; i++) {
            double latDistance = Math.toRadians(home[0] - coords[i][0]);
            double lonDistance = Math.toRadians(home[1] - coords[i][1]);
            double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                    + Math.cos(Math.toRadians(coords[i][0])) * Math.cos(Math.toRadians(home[0]))
                    * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
            double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
            dists[i] = R * c * 1000;
        }

        HashMap<Integer, Integer> distMap = new HashMap<>();

        int[] taskBoundaries = new int[]{299, 399, 499, 599, 699, 799, 899};
        for (int b=0; b<7; b++) {
            int count = 0;
            for (int i = 0; i < coords.length; i++) {
                if (dists[i] < taskBoundaries[b]) {
                    count++;
                    System.out.println(dists[i]);
                    dists[i] = 1000000;
                }
            }

            distMap.put(taskBoundaries[b], count);
        }
        System.out.println(Arrays.toString(dists));
        distMap.forEach((k, v) -> System.out.println(k + " -> " + v));
        return distMap;
    }

    private void generateTasks(HashMap<Integer, Integer> distMap) {
        double[][] tasks = new double[40][2];
        int[] taskBoundaries = new int[]{299, 399, 499, 599, 699, 799, 899};
        int head = 0;
        double lat = 0;
        double lng = 0;
        for (int i = 0; i < taskBoundaries.length; i++) {
            int b = taskBoundaries[i];
            int numToGenerate = distMap.get(b);
            int botBound = (i == 0) ? 199 : taskBoundaries[i - 1];
            int topBound = (i == taskBoundaries.length - 1) ? 899 : taskBoundaries[i];
            for (int j = 0; j < numToGenerate; j++) {
                boolean taskValid = false;
                while (!taskValid) {
                    taskValid = true;
                    int dist = random.nextInt(botBound, topBound);
                    double angle = random.nextDouble(Math.PI / 3, 2 * Math.PI / 3);
                    double north_south_distance = dist * Math.sin(angle);
                    double east_west_distance = dist * Math.cos(angle);
                    lat = home[0] + north_south_distance / (60.0 * 1852.0);
                    lng = home[1] + east_west_distance / (60.0 * 1852.0 * Math.cos(home[0] * Math.PI / 180.0));
                    Coordinate thisCoord = new Coordinate(lat, lng);
                    for (double[] d : tasks) {
                        Coordinate origin = new Coordinate(d[0], d[1]);
                        double separationDist = thisCoord.getDistance(origin);
                        if (separationDist < 75) {
                            System.out.println("Within 75m, retrying");
                            taskValid = false;
                            break;
                        }
                    }
                }

                tasks[head] = new double[]{lat, lng};
                head++;
            }
        }
        System.out.println(Arrays.deepToString(tasks));

        StringBuilder sb = new StringBuilder();

        sb.append("\"tasks\": [\n");
        for (int i = 0; i < 40; i++) {
            sb.append("{\n");
            sb.append("\t\"lat\": ");
            sb.append(tasks[i][0]);
            sb.append(",\n");
            sb.append("\t\"lng\": ");
            sb.append(tasks[i][1]);
            sb.append(",\n");
            sb.append("\t\"type\": 4");
            sb.append("\n},\n");
        }
        System.out.println(sb);
    }


}
