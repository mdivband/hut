package tool;

import java.util.Random;

public class temp {

    public static void main(String[] args) {
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i<15;i++){
            double lat = random.nextDouble(50.928926124950884, 50.93364856179109);
            double lng = random.nextDouble(-1.4179953608513318, -1.40081423902516);
            sb.append("{\n\"lat\": ").append(lat).append(",\n \"lng\": ").append(lng).append(",\n \"type\": 4\n},\n");
        }
        System.out.println(sb);
    }

}
