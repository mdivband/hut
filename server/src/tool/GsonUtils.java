/**
 * 
 */
package tool;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSerializer;

import server.Simulator;
import server.model.State;

import java.io.*;
import java.util.Map;

/**
 * @author Feng Wu
 *
 */
public class GsonUtils {

    private static final GsonBuilder builder = new GsonBuilder().setPrettyPrinting();
	private static Gson gson;

	public static void registerTypeAdapter(Class type, JsonSerializer serializer) {
        builder.registerTypeAdapter(type, serializer);
    }

    public static void create() {
        gson = builder.create();
    }

	public static <T> String toJson(T obj) {
		gson = new Gson();
		return gson.toJson(obj);
	}

	public static Object fromJson(String json) {
		gson = new Gson();
		return gson.fromJson(json, Object.class);
	}
	
	@SuppressWarnings("unchecked")
	public static <T> T getValue(Object obj, String key) {
		assert (obj != null);
		return ((Map<String, T>)obj).get(key);
	}

	public static <T> Boolean hasKey(Object obj, String key) {
		return ((Map<String, T>)obj).containsKey(key);
	}
	
	private static String readStream(InputStream stream) throws IOException {
		StringBuilder sb = new StringBuilder();
		BufferedReader in = new BufferedReader(new InputStreamReader(stream));
		
		String line;
		while ((line = in.readLine()) != null) {
			sb.append(line);
		}
		in.close();

		return sb.toString();
	}
	
	public static String readFile(String filename) throws IOException {
		return readStream(new FileInputStream(filename));
	}
}
