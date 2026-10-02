package dev.promptcraft.client.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.promptcraft.config.PromptCraftConfigManager;
import net.minecraft.client.MinecraftClient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Загрузка списка моделей у провайдера. Никакой отрисовки. */
public final class ModelListClient {

    private ModelListClient() {
    }

    /**
     * Колбэки гарантированно вызываются в главном потоке клиента.
     *
     * @param onError текст ошибки, уже готовый к показу
     */
    public static void fetch(String providerCode,
                             String apiKey,
                             Consumer<List<String>> onSuccess,
                             Consumer<String> onError) {

        ProviderRegistry.Provider provider = ProviderRegistry.byCode(providerCode);

        if (provider.responseStyle() == ProviderRegistry.ResponseStyle.STATIC_LIST || "agy".equals(providerCode)) {
            fetchAgyModels(onSuccess, onError);
            return;
        }

        String url = provider.modelsUrl();

        if (url == null || url.isBlank()) {
            onError.accept("unsupported");
            return;
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET();

        switch (provider.authStyle()) {
            case GOOGLE_API_KEY -> builder.header("x-goog-api-key", apiKey);
            case ANTHROPIC -> {
                builder.header("x-api-key", apiKey);
                builder.header("anthropic-version", "2023-06-01");
            }
            default -> builder.header("Authorization", "Bearer " + apiKey);
        }

        HttpClient.newHttpClient()
                .sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> onClient(() -> {
                    if (response.statusCode() != 200) {
                        onError.accept("Error: " + response.statusCode());
                        return;
                    }
                    try {
                        onSuccess.accept(parse(provider, response.body()));
                    } catch (Exception e) {
                        onError.accept("Parse Error!");
                    }
                }))
                .exceptionally(ex -> {
                    onClient(() -> onError.accept("Network Error!"));
                    return null;
                });
    }

    private static void onClient(Runnable action) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        client.execute(action);
    }

    static List<String> parse(ProviderRegistry.Provider provider, String body) {
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        List<String> models = new ArrayList<>();

        switch (provider.responseStyle()) {
            case GEMINI_MODELS -> {
                JsonArray arr = root.getAsJsonArray("models");
                for (JsonElement e : arr) {
                    String name = e.getAsJsonObject().get("name").getAsString();
                    if (name.startsWith("models/")) {
                        name = name.substring("models/".length());
                    }
                    models.add(name);
                }
            }
            case ANTHROPIC_DATA -> {
                // {"data":[{"id":"claude-sonnet-4-20250514","max_tokens":128000,...}]}
                JsonArray arr = root.getAsJsonArray("data");
                for (JsonElement e : arr) {
                    JsonObject obj = e.getAsJsonObject();
                    if (!obj.has("id")) continue;

                    String id = obj.get("id").getAsString();
                    models.add(id);

                    if (obj.has("max_tokens") && !obj.get("max_tokens").isJsonNull()) {
                        PromptCraftConfigManager.MODEL_MAX_TOKENS.put(id, obj.get("max_tokens").getAsInt());
                    }
                }
            }
            default -> {
                JsonArray data = root.getAsJsonArray("data");
                for (JsonElement e : data) {
                    JsonObject obj = e.getAsJsonObject();
                    if (obj.has("id")) models.add(obj.get("id").getAsString());
                }
            }
        }

        return models;
    }

    private static void fetchAgyModels(Consumer<List<String>> onSuccess, Consumer<String> onError) {
        CompletableFuture.supplyAsync(() -> {
            List<String> models = new ArrayList<>();
            String agyExec = dev.promptcraft.ai.AgyUtil.findAgyExecutable();
            if (agyExec != null) {
                try {
                    Process process = new ProcessBuilder(agyExec, "models").start();
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            line = line.trim();
                            if (line.isEmpty() || line.startsWith("Fetching")) continue;
                            String[] parts = line.split("\\t");
                            if (parts.length > 0 && !parts[0].isBlank()) {
                                models.add(parts[0].trim());
                            }
                        }
                    }
                    process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
                } catch (Exception ignored) {
                }
            }
            if (models.isEmpty()) {
                models.addAll(ProviderRegistry.AGY_DEFAULT_MODELS);
            }
            return models;
        }).thenAccept(models -> onClient(() -> onSuccess.accept(models)))
          .exceptionally(ex -> {
              onClient(() -> onSuccess.accept(ProviderRegistry.AGY_DEFAULT_MODELS));
              return null;
          });
    }
}