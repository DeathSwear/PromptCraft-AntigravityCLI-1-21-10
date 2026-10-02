package dev.promptcraft.ai;

import java.io.File;

public final class AgyUtil {

    private AgyUtil() {
    }

    public static String findAgyExecutable() {
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null) {
            File f = new File(localAppData, "agy/bin/agy.exe");
            if (f.exists() && f.canExecute()) return f.getAbsolutePath();
            File f2 = new File(localAppData, "agy/bin/agy");
            if (f2.exists() && f2.canExecute()) return f2.getAbsolutePath();
        }

        String userHome = System.getProperty("user.home");
        if (userHome != null) {
            File f = new File(userHome, "AppData/Local/agy/bin/agy.exe");
            if (f.exists() && f.canExecute()) return f.getAbsolutePath();
            File f2 = new File(userHome, "AppData/Local/agy/bin/agy");
            if (f2.exists() && f2.canExecute()) return f2.getAbsolutePath();
            File f3 = new File(userHome, ".agy/bin/agy");
            if (f3.exists() && f3.canExecute()) return f3.getAbsolutePath();
        }

        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            String[] dirs = pathEnv.split(File.pathSeparator);
            for (String dir : dirs) {
                File f1 = new File(dir, "agy.exe");
                if (f1.exists() && f1.canExecute()) return f1.getAbsolutePath();
                File f2 = new File(dir, "agy");
                if (f2.exists() && f2.canExecute()) return f2.getAbsolutePath();
            }
        }

        return System.getProperty("os.name", "").toLowerCase().contains("win") ? "agy.exe" : "agy";
    }

    public static String stripAnsi(String input) {
        if (input == null) return null;
        return input.replaceAll("\u001B\\[[;?0-9]*[a-zA-Z]", "");
    }
}
