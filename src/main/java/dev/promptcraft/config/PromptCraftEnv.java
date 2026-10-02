package dev.promptcraft.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class PromptCraftEnv {
    private static final String[] PROVIDERS = {"agy", "nvidia", "openai", "anthropic", "deepseek", "gemini", "xai", "openrouter"};

    public static String getApiKey(String provider) {
        Properties props = loadProperties();
        return props.getProperty(provider.toUpperCase() + "_API_KEY", "").trim();
    }

    public static Map<String, String> getAllApiKeys() {
        Properties props = loadProperties();
        Map<String, String> keys = new HashMap<>();
        for (String p : PROVIDERS) {
            keys.put(p, props.getProperty(p.toUpperCase() + "_API_KEY", "").trim());
        }
        return keys;
    }

    public static void saveApiKeys(Map<String, String> keys) {
        Properties props = loadProperties();

        for (Map.Entry<String, String> entry : keys.entrySet()) {
            props.setProperty(entry.getKey().toUpperCase() + "_API_KEY", entry.getValue());
        }

        try (OutputStream out = Files.newOutputStream(PromptCraftConfigManager.getEnvPath())) {
            props.store(out, "PromptCraft API secrets");
        } catch (IOException ignored) {
        }
    }

    private static Properties loadProperties() {
        Properties props = new Properties();
        try {
            if (Files.exists(PromptCraftConfigManager.getEnvPath())) {
                try (InputStream in = Files.newInputStream(PromptCraftConfigManager.getEnvPath())) {
                    props.load(in);
                }
            }
        } catch (IOException ignored) {
        }
        return props;
    }
}