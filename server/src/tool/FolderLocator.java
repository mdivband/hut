package tool;

import java.io.File;

public class FolderLocator {
    public static void main(String[] args) {
        File currentDir = new File(System.getProperty("user.dir")); // Current working directory
        File scenariosDir = findScenariosFolder(currentDir);

        if (scenariosDir != null) {
            System.out.println("Scenarios folder found at: " + scenariosDir.getAbsolutePath());
        } else {
            System.out.println("Scenarios folder not found!");
        }
    }

    public static File findScenariosFolder(File startDir) {
        File dir = startDir;

        // Traverse upwards to find the "server/web/scenarios" directory
        while (dir != null) {
            File potentialScenariosDir = new File(dir, "server/web/scenarios");
            if (potentialScenariosDir.exists() && potentialScenariosDir.isDirectory()) {
                return potentialScenariosDir;
            }
            dir = dir.getParentFile(); // Move up one directory level
        }
        return null; // Folder not found
    }
}
