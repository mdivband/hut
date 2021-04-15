/**
 * 
 */
package tool;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSerializer;

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
		return gson.toJson(obj);
	}

	public static Object fromJson(String json) {
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
