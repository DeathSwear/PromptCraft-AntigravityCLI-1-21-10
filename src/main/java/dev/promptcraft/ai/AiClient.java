package dev.promptcraft.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.promptcraft.config.PromptCraftConfig;
import dev.promptcraft.config.PromptCraftConfigManager;
import dev.promptcraft.config.PromptCraftEnv;
import dev.promptcraft.config.PromptCraftLang;
import dev.promptcraft.network.PromptCraftNetworking;
import dev.promptcraft.session.GenerationSession;
import dev.promptcraft.structure.PromptCraftStructure;
import dev.promptcraft.structure.StructureValidator;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AiClient {

    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(45)).build();
    private static final ExecutorService STREAM_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "PromptCraft-AI-Stream");
        t.setDaemon(true);
        return t;
    });

    private static final long REQUEST_TIMEOUT_SECONDS = 300L;
    private static final int FREE_MODE_SAFETY_CEILING = 96;
    private static final double CREATIVE_TEMPERATURE = 0.85;
    private static final double PRECISE_TEMPERATURE = 0.2;

    // === PUBLIC ENTRY POINTS =================================================

    /** Ручной режим: коробка = выделение игрока, границы жёсткие (enforce + переспрос). */
    public static CompletableFuture<PromptCraftStructure> requestBuild(
            ServerPlayerEntity player, String prompt, int width, int height, int depth, GenerationSession session) {
        return requestBuildInternal(player, prompt, "", width, height, depth, session, false, true, 0, 0);
    }

    /**
     * Свободный режим. Если лимит включён - коробка = макс. размер из настроек, границы жёсткие.
     * Если лимит выключен - используем безопасный потолок и молча клемпим (без переспроса).
     */
    public static CompletableFuture<PromptCraftStructure> requestFreeBuild(
            ServerPlayerEntity player, String prompt, boolean limitEnabled,
            int maxWidth, int maxHeight, int maxDepth, GenerationSession session) {
        int width = limitEnabled ? maxWidth : FREE_MODE_SAFETY_CEILING;
        int height = limitEnabled ? maxHeight : FREE_MODE_SAFETY_CEILING;
        int depth = limitEnabled ? maxDepth : FREE_MODE_SAFETY_CEILING;
        return requestBuildInternal(player, prompt, "", width, height, depth, session, true, limitEnabled, 0, 0);
    }

    /**
     * Режим модификации (In-painting): передаёт в ИИ существующую структуру выделенной области
     * и запрос пользователя на доработку.
     */
    public static CompletableFuture<PromptCraftStructure> requestModify(
            ServerPlayerEntity player, String prompt, int width, int height, int depth,
            String existingBlocksJson, GenerationSession session) {
        String modifyContext = "\n\n=== EXISTING STRUCTURE IN THIS AREA ===\n" +
                "The selected area already contains the following existing blocks (local coordinates relative to [0,0,0]):\n" +
                existingBlocksJson + "\n\n" +
                "=== INSTRUCTION FOR MODIFICATION ===\n" +
                "You are modifying or adding to the existing structure above.\n" +
                "- Do NOT rebuild the entire structure from scratch unless requested.\n" +
                "- Only output the new or modified blocks needed to fulfill the user's request.\n" +
                "- To carve new openings or windows into existing walls, place 'minecraft:air' at those positions.\n" +
                "- To add new features (e.g. balconies, roofs, decorations, furniture), place them in the empty coordinates.\n" +
                "- All coordinates must stay within 0<=x<=" + (width - 1) + ", 0<=y<=" + (height - 1) + ", 0<=z<=" + (depth - 1) + ".\n";

        return requestBuildInternal(player, prompt, modifyContext, width, height, depth, session, false, true, 0, 0);
    }

    // === CORE ================================================================

    private static final int MAX_TRANSIENT_RETRIES = 3;

    private static <T> CompletableFuture<T> retryAfterDelay(java.util.function.Supplier<CompletableFuture<T>> action) {
        return CompletableFuture
                .supplyAsync(() -> (T) null,
                        CompletableFuture.delayedExecutor(600, java.util.concurrent.TimeUnit.MILLISECONDS, STREAM_EXECUTOR))
                .thenCompose(ignored -> action.get());
    }

    /** Ленивый разбор: строгий -> извлечение объекта -> спасение обрезанного массива операций. */
    private static PromptCraftStructure parseStructureLenient(String raw) {
        String json = extractJsonObject(raw);
        if (json == null) return null;
        try {
            PromptCraftStructure s = GSON.fromJson(json, PromptCraftStructure.class);
            if (s != null && s.operations != null && !s.operations.isEmpty()) return s;
        } catch (Exception ignored) {
            // упало -> пробуем спасти то, что успело прийти
        }
        return salvageOperations(json);
    }

    /** Отрезает прозу/`<think>`/фенсы: берём от первой '{' до последней '}'. Если закрытия нет (обрыв) - до конца. */
    private static String extractJsonObject(String raw) {
        if (raw == null) return null;
        String s = raw.replace("```json", "").replace("```", "").trim();
        int start = s.indexOf('{');
        if (start < 0) return null;
        int end = s.lastIndexOf('}');
        return (end > start) ? s.substring(start, end + 1) : s.substring(start);
    }

    /**
     * Спасает обрезанный ответ: проходит по массиву operations с учётом строк и вложенности,
     * собирает только ПОЛНЫЕ объекты {...}, недописанный последний выбрасывает, и пересобирает JSON.
     */
    private static PromptCraftStructure salvageOperations(String json) {
        int arrStart = json.indexOf('[');
        if (arrStart < 0) return null;

        java.util.List<String> objs = new java.util.ArrayList<>();
        int depth = 0, objStart = -1;
        boolean inString = false, escape = false;

        for (int i = arrStart + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inString) {
                if (escape) escape = false;
                else if (c == '\\') escape = true;
                else if (c == '"') inString = false;
                continue;
            }
            if (c == '"') { inString = true; }
            else if (c == '{') { if (depth == 0) objStart = i; depth++; }
            else if (c == '}') {
                depth--;
                if (depth == 0 && objStart >= 0) { objs.add(json.substring(objStart, i + 1)); objStart = -1; }
            } else if (c == ']' && depth == 0) {
                break;
            }
        }
        if (objs.isEmpty()) return null;
        try {
            return GSON.fromJson("{\"operations\":[" + String.join(",", objs) + "]}", PromptCraftStructure.class);
        } catch (Exception e) {
            return null;
        }
    }

    // === CORE ================================================================

    private static CompletableFuture<PromptCraftStructure> requestBuildInternal(
            ServerPlayerEntity player, String originalPrompt, String correctionNote,
            int width, int height, int depth, GenerationSession session,
            boolean freeChoice, boolean enforceBounds, int attempt, int transientAttempt) {

        PromptCraftConfig config = PromptCraftConfigManager.get();
        boolean isAgy = "agy".equals(config.provider);
        String apiKey = PromptCraftEnv.getApiKey(config.provider);
        if (!isAgy && (apiKey == null || apiKey.isEmpty())) {
            player.sendMessage(Text.literal(PromptCraftLang.t("API Key is missing! Please use /pmenu", "API-ключ отсутствует! Используйте /pmenu")).formatted(Formatting.RED), false);
            PromptCraftNetworking.sendAiStreamEvent(player, "error", "API key is missing.");
            return CompletableFuture.completedFuture(null);
        }
        if (session.isCancelled()) {
            return CompletableFuture.completedFuture(null);
        }

        PromptCraftNetworking.sendAiStreamEvent(player, "start", "");

        boolean precise = "precise".equals(config.buildMode);
        String systemPrompt = buildSystemPrompt(width, height, depth, freeChoice, precise);
        String userPrompt = "Build the following, respecting ALL rules above: " + originalPrompt + correctionNote;

        if (isAgy) {
            return requestBuildAgy(player, config, originalPrompt, correctionNote, systemPrompt, userPrompt,
                    width, height, depth, session, freeChoice, enforceBounds, attempt, transientAttempt);
        }

        HttpRequest request = buildRequest(config, apiKey, systemPrompt, userPrompt);

        CompletableFuture<HttpResponse<InputStream>> httpFuture =
                HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream());
        session.setHttpFuture(httpFuture);

        // true -> сбой транзиентный (пусто/парс/сеть), можно молча повторить.
        final boolean[] retriable = {false};

        CompletableFuture<PromptCraftStructure> parseFuture = httpFuture
                .thenApplyAsync(response -> {
                    if (session.isCancelled()) return null;
                    if (response.statusCode() != 200) {
                        String body = readAll(response.body());
                        if (!session.isCancelled()) {
                            String msg = PromptCraftLang.t("API Error: ", "Ошибка API: ") + response.statusCode() + " - " + body;
                            player.sendMessage(Text.literal(msg).formatted(Formatting.RED), false);
                            PromptCraftNetworking.sendAiStreamEvent(player, "error", msg);
                        }
                        return null; // не ретраим статусные ошибки (ключ/квота)
                    }
                    session.setActiveStream(response.body());
                    try {
                        String content = switch (config.provider) {
                            case "anthropic" -> streamAnthropic(player, response.body(), session);
                            case "gemini" -> streamGemini(player, response.body(), session);
                            default -> streamOpenAiCompatible(player, response.body(), session);
                        };
                        if (session.isCancelled()) return null;
                        if (content == null || content.isBlank()) {
                            retriable[0] = true; // пусто -> тихий ретрай
                            return null;
                        }
                        PromptCraftStructure parsed = parseStructureLenient(content);
                        if (parsed == null || parsed.operations == null || parsed.operations.isEmpty()) {
                            retriable[0] = true; // не разобрали ничего годного -> ретрай
                            return null;
                        }
                        return parsed;
                    } catch (Exception e) {
                        if (!session.isCancelled()) retriable[0] = true;
                        return null;
                    }
                }, STREAM_EXECUTOR)
                .exceptionally(ex -> {
                    if (!session.isCancelled()) retriable[0] = true;
                    return null;
                });

        return parseFuture.thenCompose(structure -> {
            if (session.isCancelled()) return CompletableFuture.completedFuture(null);

            if (structure == null) {
                if (retriable[0] && transientAttempt < MAX_TRANSIENT_RETRIES) {
                    // Тихо повторяем, игрока не спамим.
                    return retryAfterDelay(() -> requestBuildInternal(
                            player, originalPrompt, correctionNote, width, height, depth,
                            session, freeChoice, enforceBounds, attempt, transientAttempt + 1));
                }
                if (retriable[0]) {
                    String msg = PromptCraftLang.t(
                            "AI request failed after several attempts. Please try again.",
                            "Запрос к ИИ не удался после нескольких попыток. Попробуйте ещё раз.");
                    notifyPlayer(player, msg, msg, Formatting.RED);
                    if (player.getEntityWorld().getServer() != null) {
                        player.getEntityWorld().getServer().execute(() -> PromptCraftNetworking.sendAiStreamEvent(player, "error", msg));
                    }
                }
                return CompletableFuture.completedFuture(null);
            }
            return validateAndRepair(player, originalPrompt, structure, width, height, depth, session, freeChoice, enforceBounds, attempt);
        });
    }

    private static CompletableFuture<PromptCraftStructure> requestBuildAgy(
            ServerPlayerEntity player, PromptCraftConfig config,
            String originalPrompt, String correctionNote,
            String systemPrompt, String userPrompt,
            int width, int height, int depth, GenerationSession session,
            boolean freeChoice, boolean enforceBounds, int attempt, int transientAttempt) {

        final boolean[] retriable = {false};

        CompletableFuture<PromptCraftStructure> processFuture = CompletableFuture.supplyAsync(() -> {
            if (session.isCancelled()) return null;

            String agyExecutable = AgyUtil.findAgyExecutable();
            if (agyExecutable == null) {
                String msg = PromptCraftLang.t("AGY CLI executable not found!", "Исполняемый файл AGY CLI не найден!");
                notifyPlayer(player, msg, msg, Formatting.RED);
                PromptCraftNetworking.sendAiStreamEvent(player, "error", msg);
                return null;
            }

            String model = (config.model != null && !config.model.isBlank()) ? config.model : "gemini-3.8-flash-high";

            List<String> cmd = new ArrayList<>();
            cmd.add(agyExecutable);
            cmd.add("--model");
            cmd.add(model);
            cmd.add("--disable-slash-commands");
            cmd.add("--dangerously-skip-permissions");

            ProcessBuilder pb = new ProcessBuilder(cmd);
            String userHome = System.getProperty("user.home");
            if (userHome != null) {
                File homeDir = new File(userHome);
                if (homeDir.exists()) pb.directory(homeDir);
            }

            Process process;
            try {
                process = pb.start();
            } catch (IOException e) {
                String msg = PromptCraftLang.t("Failed to start AGY CLI: ", "Не удалось запустить AGY CLI: ") + e.getMessage();
                notifyPlayer(player, msg, msg, Formatting.RED);
                PromptCraftNetworking.sendAiStreamEvent(player, "error", msg);
                return null;
            }

            session.setActiveProcess(process);

            // Feed prompt via stdin
            String combinedPrompt = systemPrompt + "\n\n=== USER REQUEST ===\n" + userPrompt;
            try (OutputStream os = process.getOutputStream();
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8))) {
                writer.write(combinedPrompt);
                writer.flush();
            } catch (IOException e) {
                if (!session.isCancelled()) {
                    process.destroyForcibly();
                    retriable[0] = true;
                }
                return null;
            }

            // Capture stderr asynchronously
            StringBuilder stderrAccumulator = new StringBuilder();
            Thread stderrThread = new Thread(() -> {
                try (BufferedReader errReader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                    String errLine;
                    while ((errLine = errReader.readLine()) != null) {
                        stderrAccumulator.append(errLine).append('\n');
                    }
                } catch (Exception ignored) {}
            }, "PromptCraft-AGY-Stderr");
            stderrThread.setDaemon(true);
            stderrThread.start();

            // Stream stdout
            StringBuilder stdoutAccumulator = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                boolean jsonStarted = false;
                while (!session.isCancelled() && (line = reader.readLine()) != null) {
                    stdoutAccumulator.append(line).append('\n');
                    if (!jsonStarted) {
                        if (line.trim().startsWith("{")) {
                            jsonStarted = true;
                        } else if (!line.isBlank()) {
                            sendReasoning(player, line + "\n");
                        }
                    }
                }
            } catch (IOException e) {
                if (!session.isCancelled()) {
                    retriable[0] = true;
                }
                return null;
            }

            try {
                boolean finished = process.waitFor(REQUEST_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    String msg = PromptCraftLang.t("AGY CLI timed out.", "AGY CLI превысил время ожидания.");
                    notifyPlayer(player, msg, msg, Formatting.RED);
                    PromptCraftNetworking.sendAiStreamEvent(player, "error", msg);
                    return null;
                }
            } catch (InterruptedException e) {
                process.destroyForcibly();
                return null;
            }

            if (session.isCancelled()) {
                process.destroyForcibly();
                return null;
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                String errText = stderrAccumulator.toString().trim();
                if (errText.isEmpty()) errText = "exit code " + exitCode;
                String msg = PromptCraftLang.t("AGY CLI error: ", "Ошибка AGY CLI: ") + errText;
                notifyPlayer(player, msg, msg, Formatting.RED);
                PromptCraftNetworking.sendAiStreamEvent(player, "error", msg);
                return null;
            }

            String content = AgyUtil.stripAnsi(stdoutAccumulator.toString());
            if (content.isBlank()) {
                retriable[0] = true;
                return null;
            }

            PromptCraftStructure parsed = parseStructureLenient(content);
            if (parsed == null || parsed.operations == null || parsed.operations.isEmpty()) {
                retriable[0] = true;
                return null;
            }
            return parsed;
        }, STREAM_EXECUTOR);

        return processFuture.thenCompose(structure -> {
            if (session.isCancelled()) return CompletableFuture.completedFuture(null);

            if (structure == null) {
                if (retriable[0] && transientAttempt < MAX_TRANSIENT_RETRIES) {
                    return retryAfterDelay(() -> requestBuildInternal(
                            player, originalPrompt, correctionNote, width, height, depth,
                            session, freeChoice, enforceBounds, attempt, transientAttempt + 1));
                }
                if (retriable[0]) {
                    String msg = PromptCraftLang.t(
                            "AI request failed after several attempts. Please try again.",
                            "Запрос к ИИ не удался после нескольких попыток. Попробуйте ещё раз.");
                    notifyPlayer(player, msg, msg, Formatting.RED);
                    if (player.getEntityWorld().getServer() != null) {
                        player.getEntityWorld().getServer().execute(() -> PromptCraftNetworking.sendAiStreamEvent(player, "error", msg));
                    }
                }
                return CompletableFuture.completedFuture(null);
            }
            return validateAndRepair(player, originalPrompt, structure, width, height, depth, session, freeChoice, enforceBounds, attempt);
        });
    }

    private static CompletableFuture<PromptCraftStructure> validateAndRepair(
            ServerPlayerEntity player, String originalPrompt, PromptCraftStructure structure,
            int width, int height, int depth, GenerationSession session,
            boolean freeChoice, boolean enforceBounds, int attempt) {

        if (structure == null || session.isCancelled()) {
            return CompletableFuture.completedFuture(structure);
        }
        if (StructureValidator.isWithinBounds(structure, width, height, depth)) {
            return CompletableFuture.completedFuture(structure);
        }

        int maxAttempts = Math.max(0, PromptCraftConfigManager.get().maxRepairAttempts);

        // Границы жёсткие -> просим ИИ переделать (не режем), пока есть попытки.
        if (enforceBounds && attempt < maxAttempts) {
            int shown = attempt + 1;
            notifyPlayer(player,
                    "AI build didn't fit the area, asking it to adjust... (" + shown + "/" + maxAttempts + ")",
                    "Постройка ИИ не вписалась в область, просим переделать... (" + shown + "/" + maxAttempts + ")",
                    Formatting.YELLOW);
            String correction = StructureValidator.buildCorrectionNote(structure, width, height, depth);
            return requestBuildInternal(player, originalPrompt, correction, width, height, depth, session, freeChoice, enforceBounds, attempt + 1, 0);
        }

        // Попытки исчерпаны (или лимит выключен) -> крайняя мера: обрезаем по границам.
        if (enforceBounds) {
            notifyPlayer(player,
                    "AI couldn't fit the build after several tries. Trimming it to stay inside the area.",
                    "ИИ не смог вписать постройку за несколько попыток. Обрезаю её по границам области.",
                    Formatting.YELLOW);
        }
        return CompletableFuture.completedFuture(StructureValidator.clamp(structure, width, height, depth));
    }

    private static void notifyPlayer(ServerPlayerEntity player, String en, String ru, Formatting color) {
        if (player.getEntityWorld().getServer() != null) {
            player.getEntityWorld().getServer().execute(() ->
                    player.sendMessage(Text.literal(PromptCraftLang.t(en, ru)).formatted(color), false));
        }
    }

    // =========================================================================
    // === STREAM PARSERS  (без изменений)
    // =========================================================================

    private static String streamOpenAiCompatible(ServerPlayerEntity player, InputStream body, GenerationSession session) throws IOException {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            String line;
            while (!session.isCancelled() && (line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) continue;
                String data = line.substring(5).trim();
                if (data.isEmpty()) continue;
                if ("[DONE]".equals(data)) break;
                try {
                    JsonObject obj = GSON.fromJson(data, JsonObject.class);
                    if (obj == null || !obj.has("choices")) continue;
                    JsonArray choices = obj.getAsJsonArray("choices");
                    if (choices.isEmpty()) continue;
                    JsonObject choice = choices.get(0).getAsJsonObject();
                    if (!choice.has("delta")) continue;
                    JsonObject delta = choice.getAsJsonObject("delta");
                    String reasoning = getStringOrNull(delta, "reasoning_content");
                    if (reasoning == null) reasoning = getStringOrNull(delta, "reasoning");
                    if (reasoning != null && !reasoning.isEmpty()) {
                        sendReasoning(player, reasoning);
                    }
                    String contentChunk = getStringOrNull(delta, "content");
                    if (contentChunk != null && !contentChunk.isEmpty()) {
                        content.append(contentChunk);
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (IOException e) {
            if (!session.isCancelled()) throw e;
        }
        return content.toString();
    }

    private static String streamAnthropic(ServerPlayerEntity player, InputStream body, GenerationSession session) throws IOException {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            String line;
            while (!session.isCancelled() && (line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) continue;
                String data = line.substring(5).trim();
                if (data.isEmpty()) continue;
                try {
                    JsonObject obj = GSON.fromJson(data, JsonObject.class);
                    if (obj == null || !obj.has("type")) continue;
                    String type = obj.get("type").getAsString();
                    if ("content_block_delta".equals(type) && obj.has("delta")) {
                        JsonObject delta = obj.getAsJsonObject("delta");
                        String deltaType = delta.has("type") ? delta.get("type").getAsString() : "";
                        if ("thinking_delta".equals(deltaType)) {
                            String thinking = getStringOrNull(delta, "thinking");
                            if (thinking != null) sendReasoning(player, thinking);
                        } else if ("text_delta".equals(deltaType)) {
                            String text = getStringOrNull(delta, "text");
                            if (text != null) content.append(text);
                        }
                    } else if ("error".equals(type) && obj.has("error")) {
                        JsonObject err = obj.getAsJsonObject("error");
                        String msg = err.has("message") ? err.get("message").getAsString() : "Unknown Anthropic error";
                        sendReasoning(player, "\n[Anthropic error: " + msg + "]\n");
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (IOException e) {
            if (!session.isCancelled()) throw e;
        }
        return content.toString();
    }

    private static String streamGemini(ServerPlayerEntity player, InputStream body, GenerationSession session) throws IOException {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            String line;
            while (!session.isCancelled() && (line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) continue;
                String data = line.substring(5).trim();
                if (data.isEmpty()) continue;
                try {
                    JsonObject obj = GSON.fromJson(data, JsonObject.class);
                    if (obj == null || !obj.has("candidates")) continue;
                    JsonArray candidates = obj.getAsJsonArray("candidates");
                    if (candidates.isEmpty()) continue;
                    JsonObject candidate = candidates.get(0).getAsJsonObject();
                    if (!candidate.has("content")) continue;
                    JsonObject contentObj = candidate.getAsJsonObject("content");
                    if (!contentObj.has("parts")) continue;
                    JsonArray parts = contentObj.getAsJsonArray("parts");
                    for (JsonElement partEl : parts) {
                        JsonObject part = partEl.getAsJsonObject();
                        String text = getStringOrNull(part, "text");
                        if (text == null || text.isEmpty()) continue;
                        boolean isThought = part.has("thought") && !part.get("thought").isJsonNull() && part.get("thought").getAsBoolean();
                        if (isThought) {
                            sendReasoning(player, text);
                        } else {
                            content.append(text);
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (IOException e) {
            if (!session.isCancelled()) throw e;
        }
        return content.toString();
    }

    private static void sendReasoning(ServerPlayerEntity player, String chunk) {
        if (chunk == null || chunk.isEmpty()) return;
        PromptCraftNetworking.sendAiStreamEvent(player, "reasoning", chunk);
    }

    private static String getStringOrNull(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return null;
        try {
            return obj.get(key).getAsString();
        } catch (Exception e) {
            return null;
        }
    }

    private static String readAll(InputStream in) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        } catch (Exception e) {
            return "<unreadable body>";
        }
    }

    private static boolean isOpenAiReasoningModel(String model) {
        if (model == null) return false;
        String m = model.toLowerCase();
        return m.startsWith("o1") || m.startsWith("o3") || m.startsWith("o4") || m.contains("reasoning");
    }

    /** Динамический потолок вывода для Anthropic по ID модели. */
    private static int anthropicMaxTokens(String model) {
        if (model == null) return 128000;
        String m = model.toLowerCase();
        if (m.contains("haiku")) return 64000; // Haiku 4.5 и другие haiku: потолок 64k
        return 128000;                          // Sonnet / Opus / Fable / Mythos и пр.
    }

    // =========================================================================
    // === REQUEST BUILDING  (без изменений)
    // =========================================================================

    private static String buildSystemPrompt(int width, int height, int depth, boolean freeChoice, boolean precise) {
        String blockList = BlockCatalog.getBlockListForPrompt();
        String areaNote = freeChoice
                ? "\n\nNote: this bounding box is a generous safety ceiling, not a target you must fill. " +
                  "Choose whatever footprint naturally fits the request - as long as every coordinate stays inside the box."
                : "";

        StringBuilder sb = new StringBuilder();

        if (precise) {
            sb.append("You are a precise, literal Minecraft Java Edition builder. Your ONLY job is to build EXACTLY what the ")
              .append("user describes, following their instructions to the letter. Output the design as a precise sequence of ")
              .append("build operations in JSON.\n\n")
              .append("=== STRICT FIDELITY MANDATE (READ FIRST) ===\n")
              .append("- Build EXACTLY what the user asks for - no more, no less. Do NOT add decorative elements, extra detail, ")
              .append("landscaping, furniture, or stylistic flourishes the user did not explicitly request.\n")
              .append("- Honor the user's specified materials, dimensions, shapes and layout precisely. If they say 'a stone tower', ")
              .append("build a stone tower from the block they named; do not substitute or embellish.\n")
              .append("- If the user does NOT mention a detail, keep it simple and neutral rather than inventing something.\n")
              .append("- Make only the minimal structural choices needed for the build to be valid. When in doubt, pick the ")
              .append("simplest interpretation of the user's words.\n\n");
        } else {
            sb.append("You are a WORLD-CLASS Minecraft Java Edition master builder, the kind whose creations go viral for their ")
              .append("detail and craftsmanship. You do NOT build boring boxes. Every structure you output looks like it was ")
              .append("hand-crafted by a professional builder, not auto-generated. Output the design as a precise sequence of ")
              .append("build operations in JSON.\n\n")
              .append("=== CREATIVE MANDATE (READ FIRST) ===\n")
              .append("A plain solid box of a single block type is a FAILURE. Even the simplest request must be elevated:\n")
              .append("- MIX A PALETTE: never build from one block. Combine a main block, a secondary/trim block, an accent, and ")
              .append("detail blocks (e.g. for stone: stone_bricks + cobblestone + andesite + stone_brick_stairs + cobblestone_wall + ")
              .append("cracked/mossy variants for texture).\n")
              .append("- ADD DEPTH & RELIEF: break flat surfaces. Use offsets, insets, protruding trims, corner pillars, string ")
              .append("courses, and stair/slab detailing so walls are never one flat plane.\n")
              .append("- REAL ROOFS: build sloped, layered roofs with stairs and slabs (spires, overhangs, ridges). Flat single-layer ")
              .append("roofs only when the theme truly calls for it.\n")
              .append("- SILHOUETTE: give the build an interesting outline - battlements/crenellations, overhangs, tapering, a ")
              .append("decorative top. Avoid a featureless rectangular profile.\n")
              .append("- DETAILS SELL IT: lanterns/torches for lighting, trapdoors and stairs as decorative accents, chains, ")
              .append("flower pots, item frames, leaves/vines for greenery, glass panes for windows, doors, a proper entrance.\n")
              .append("- LANDSCAPING: anchor the build to the ground with a small base of grass/leaves/vines/path blocks and a ")
              .append("foundation lip so it doesn't look like it's floating.\n\n")
              .append("=== WORKED EXAMPLE: 'a stone tower' ===\n")
              .append("Do NOT output a hollow cobblestone rectangle. Instead design something like:\n")
              .append("1. A slightly wider stone-brick foundation ring (with cobblestone/mossy accents) so the base flares out.\n")
              .append("2. A tall cylindrical-ish or octagonal stone-brick shaft, walls textured with scattered cracked/mossy ")
              .append("stone bricks, cobblestone patches and andesite, plus vertical stone-brick-stair pilasters at the corners.\n")
              .append("3. Narrow windows with glass panes and stone-brick-stair sills, a proper door at the base with a stair ")
              .append("landing, and torches/lanterns beside it.\n")
              .append("4. A crenellated battlement at the top (alternating full blocks and gaps, walls + stairs for the merlons), ")
              .append("slightly overhanging the shaft on stair corbels.\n")
              .append("5. A tall wooden spire roof above the battlement: stacked rings of stairs (e.g. spruce_stairs) stepping ")
              .append("inward to a point, with an overhang, a hanging lantern underneath, and a fence/end-rod finial on top.\n")
              .append("6. Vines/leaves and a few grass blocks around the base, maybe a small path.\n\n");
        }

        // --- Общая техническая часть (одинаковая для обоих режимов) ---
        sb.append("=== OUTPUT FORMAT ===\n")
          .append("Output ONLY a single valid JSON object. No explanation, no comments, no markdown fences. ")
          .append("Your entire response must be parseable as JSON.\n\n")
          .append("Schema: {\"operations\":[ <operation>, <operation>, ... ]}\n\n")
          .append("Each <operation> is one of:\n")
          .append("1. \"place\" - one block: {\"type\":\"place\",\"pos\":[x,y,z],\"block\":\"minecraft:<block_id>\"}\n")
          .append("2. \"fill\" - solid box (inclusive): {\"type\":\"fill\",\"from\":[x1,y1,z1],\"to\":[x2,y2,z2],\"block\":\"minecraft:<block_id>\"}\n")
          .append("3. \"hollow_box\" - hollow shell (walls+floor+ceiling, 1 thick): {\"type\":\"hollow_box\",\"from\":[x1,y1,z1],\"to\":[x2,y2,z2],\"block\":\"minecraft:<block_id>\"}\n\n")
          .append("=== BLOCK STATES (ORIENTATION) ===\n")
          .append("Append states in square brackets, comma-separated, no spaces: \"minecraft:<block_id>[prop=value,...]\".\n")
          .append("- Stairs: minecraft:stone_brick_stairs[facing=north,half=bottom,shape=straight]\n")
          .append("- Slabs: minecraft:stone_brick_slab[type=bottom|top|double]\n")
          .append("- Doors (TWO place ops, y and y+1): [facing=north,half=lower,hinge=left] then [facing=north,half=upper,hinge=left]\n")
          .append("- Trapdoors: minecraft:oak_trapdoor[facing=north,half=bottom,open=false]\n")
          .append("- Directional blocks (lantern hanging=true, furnace/observer facing, etc.).\n\n")
          .append("AUTO-CONNECTING BLOCKS - IMPORTANT: for glass panes, iron bars, fences, walls and redstone, DO NOT specify ")
          .append("north/south/east/west connection states - just place the plain block id; the engine connects them ")
          .append("automatically. For a window, fill the ENTIRE opening span with panes so they connect edge-to-edge to the ")
          .append("frame; do not leave a 1-block air gap between the pane and the wall or it will look broken.\n\n")
          .append("If unsure of a property's valid values, omit the brackets - a plain block beats an invalid state.\n\n")
          .append("=== COORDINATE SYSTEM ===\n")
          .append("- Origin [0,0,0] is the bottom-north-west corner. X=width, Y=height (0=floor, up=+), Z=depth.\n")
          .append("- CRITICAL: every coordinate MUST satisfy 0<=x<=").append(width - 1).append(", 0<=y<=").append(height - 1)
          .append(", 0<=z<=").append(depth - 1).append(". Never go outside this range.\n")
          .append("- Area starts as air. Later operations overwrite earlier ones at the same position: build the shell first, ")
          .append("then carve windows/doors by placing \"minecraft:air\", then add panes/details on top.\n\n");

        if (precise) {
            sb.append("=== BUILD PROCESS ===\n")
              .append("Build ONLY what was requested: (1) the structure exactly as described, using the user's stated materials ")
              .append("and dimensions, (2) carve any openings the user asked for by placing \"minecraft:air\", (3) add ONLY the ")
              .append("elements the user explicitly named. Do not add anything the user did not ask for. Keep it clean, literal ")
              .append("and faithful. NEVER exceed the bounding box.\n\n");
        } else {
            sb.append("=== DESIGN PROCESS ===\n")
              .append("Plan mentally: (1) foundation/base, (2) walls/shell with a mixed palette and relief, (3) a real layered roof, ")
              .append("(4) carve openings, (5) windows with panes + doors + entrance, (6) interior hints + exterior lighting + ")
              .append("landscaping. If the user names a material (cherry, deepslate, copper), use that block family consistently ")
              .append("(planks/log/stairs/slab/door/fence). Fill the bounding box ambitiously but NEVER exceed it.\n\n");
        }

        sb.append("=== AVAILABLE BLOCKS ===\n")
          .append("Use ONLY block IDs from this list (prefix each with \"minecraft:\"). Do NOT invent IDs. If a block you want ")
          .append("isn't here, pick the closest available substitute.\n")
          .append(blockList).append("\n\n")
          .append("=== BUILD AREA ===\n")
          .append("Bounding box: width=").append(width).append(", height=").append(height).append(", depth=").append(depth)
          .append(" (coords [0,0,0] to [").append(width - 1).append(",").append(height - 1).append(",").append(depth - 1)
          .append("] inclusive).").append(areaNote);

        return sb.toString();
    }

    private static HttpRequest buildRequest(PromptCraftConfig config, String apiKey, String systemPrompt, String userPrompt) {
        double temperature = "precise".equals(config.buildMode) ? PRECISE_TEMPERATURE : CREATIVE_TEMPERATURE;
        JsonObject payload = new JsonObject();
        String url;
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS));

        switch (config.provider) {
            case "anthropic":
                url = "https://api.anthropic.com/v1/messages";
                requestBuilder.header("x-api-key", apiKey).header("anthropic-version", "2023-06-01");
                payload.addProperty("model", config.model);
                payload.addProperty("system", systemPrompt);
                payload.addProperty("stream", true);
                payload.addProperty("temperature", temperature);
                payload.addProperty("max_tokens", anthropicMaxTokens(config.model));
                JsonObject anthropicMsg = new JsonObject();
                anthropicMsg.addProperty("role", "user");
                anthropicMsg.addProperty("content", userPrompt);
                payload.add("messages", GSON.toJsonTree(new JsonObject[]{anthropicMsg}));
                break;
            case "gemini":
                url = "https://generativelanguage.googleapis.com/v1beta/models/" + config.model + ":streamGenerateContent?alt=sse";
                requestBuilder.header("x-goog-api-key", apiKey);
                JsonObject sysPart = new JsonObject(); sysPart.addProperty("text", systemPrompt);
                JsonArray sysParts = new JsonArray(); sysParts.add(sysPart);
                JsonObject sysInst = new JsonObject(); sysInst.add("parts", sysParts);
                JsonObject usrPart = new JsonObject(); usrPart.addProperty("text", userPrompt);
                JsonArray usrParts = new JsonArray(); usrParts.add(usrPart);
                JsonObject usrContent = new JsonObject(); usrContent.addProperty("role", "user"); usrContent.add("parts", usrParts);
                JsonArray contents = new JsonArray(); contents.add(usrContent);
                JsonObject genConfig = new JsonObject(); genConfig.addProperty("temperature", temperature);
                JsonObject thinkingConfig = new JsonObject();
                thinkingConfig.addProperty("includeThoughts", true);
                genConfig.add("thinkingConfig", thinkingConfig);
                payload.add("systemInstruction", sysInst);
                payload.add("contents", contents);
                payload.add("generationConfig", genConfig);
                break;
            default:
                url = switch (config.provider) {
                    case "openai" -> "https://api.openai.com/v1/chat/completions";
                    case "deepseek" -> "https://api.deepseek.com/chat/completions";
                    case "openrouter" -> "https://openrouter.ai/api/v1/chat/completions";
                    case "xai" -> "https://api.x.ai/v1/chat/completions";
                    default -> "https://integrate.api.nvidia.com/v1/chat/completions";
                };
                requestBuilder.header("Authorization", "Bearer " + apiKey);
                if (config.provider.equals("openrouter")) {
                    requestBuilder.header("HTTP-Referer", "PromptCraft");
                    requestBuilder.header("X-OpenRouter-Title", "PromptCraft Mod");
                    payload.addProperty("include_reasoning", true);
                    JsonObject reasoningObj = new JsonObject();
                    reasoningObj.addProperty("effort", "high");
                    payload.add("reasoning", reasoningObj);
                }
                if (config.provider.equals("nvidia")) {
                    JsonObject templateKwargs = new JsonObject();
                    templateKwargs.addProperty("enable_thinking", true);
                    payload.add("chat_template_kwargs", templateKwargs);
                }
                if (config.provider.equals("xai")) {
                    payload.addProperty("reasoning_effort", "high");
                }
                boolean openAiReasoningModel = "openai".equals(config.provider) && isOpenAiReasoningModel(config.model);
                JsonObject sysMsg = new JsonObject();
                sysMsg.addProperty("role", openAiReasoningModel ? "developer" : "system");
                sysMsg.addProperty("content", systemPrompt);
                JsonObject usrMsg = new JsonObject();
                usrMsg.addProperty("role", "user");
                usrMsg.addProperty("content", userPrompt);
                payload.addProperty("model", config.model);
                if (openAiReasoningModel) {
                    payload.addProperty("reasoning_effort", "high");
                    // без лимита вывода: провайдер сам берёт максимум модели
                } else {
                    payload.addProperty("temperature", temperature);
                    // без max_tokens: без лимита вывода
                }
                payload.addProperty("stream", true);
                payload.add("messages", GSON.toJsonTree(new JsonObject[]{sysMsg, usrMsg}));
                break;
        }

        return requestBuilder.uri(URI.create(url)).POST(HttpRequest.BodyPublishers.ofString(payload.toString())).build();
    }
}