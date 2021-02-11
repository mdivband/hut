import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class VisualizerExample {

    public static void main(String[] args) {
        System.out.println(perfromJsonGetRequest("http://localhost:8000/visualizer"));
    }

    /**
     * Carry out of JSON get request to the given address.
     * @param address - Address to perform the get request to.
     * @return A JSON string.
     */
    private static String perfromJsonGetRequest(String address) {
        StringBuilder jsonBuilder = new StringBuilder();
        BufferedReader reader;
        try {
            //Setup connection
            URL url = new URL(address);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("charset", "utf-8");
            connection.connect();

            //Read response from server
            reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String line;
            while((line = reader.readLine()) != null) {
                //Check for end of JSON
                if(line.contains("HTTP/1.1 200 OK")) {
                    jsonBuilder.append(']');
                    break;
                }
                jsonBuilder.append(line).append("\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return jsonBuilder.toString();
    }
}
