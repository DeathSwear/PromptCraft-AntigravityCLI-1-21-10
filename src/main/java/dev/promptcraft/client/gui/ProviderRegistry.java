package dev.promptcraft.client.gui;

import dev.promptcraft.PromptCraftMod;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * Единый источник правды по AI-провайдерам.
 * Раньше эти данные были разбросаны по PROVIDER_OPTIONS / PROVIDER_CODES /
 * getProviderIcon / getDefaultModelForProvider / getModelsUrlForProvider —
 * добавление провайдера требовало шести согласованных правок в GUI-классе.
 */
public final class ProviderRegistry {

    public record Provider(
            String code,
            String displayName,
            Identifier icon,
            String defaultModel,
            String modelsUrl,
            AuthStyle authStyle,
            ResponseStyle responseStyle
    ) {
    }

    public enum AuthStyle {
        NONE,            // Local CLI / No auth header
        BEARER,          // Authorization: Bearer <key>
        GOOGLE_API_KEY,  // x-goog-api-key: <key>
        ANTHROPIC        // x-api-key + anthropic-version
    }

    public enum ResponseStyle {
        STATIC_LIST,      // Static predefined or CLI-retrieved list
        OPENAI_DATA_ID,   // {"data":[{"id":...}]}
        GEMINI_MODELS,    // {"models":[{"name":"models/..."}]}
        ANTHROPIC_DATA    // {"data":[{"id":..., "max_tokens":...}]}
    }

    private static Identifier icon(String file) {
        return Identifier.of(PromptCraftMod.MOD_ID, "textures/gui/" + file);
    }

    public static final List<String> AGY_DEFAULT_MODELS = List.of(
            "gemini-3.8-flash-high",
            "gemini-3.8-flash-medium",
            "gemini-3.8-flash-low",
            "gemini-3.7-flash-high",
            "gemini-3.7-flash-low",
            "gemini-3.6-flash-high",
            "gemini-3.6-flash-medium",
            "gemini-3.6-flash-low",
            "gemini-3.1-pro-high",
            "gemini-3.1-pro-low",
            "claude-sonnet-4-6",
            "claude-opus-4-6-thinking",
            "gpt-oss-120b-medium"
    );

    public static final Provider AGY = new Provider(
            "agy", "Antigravity (AGY)", icon("agy.png"),
            "gemini-3.8-flash-high",
            "",
            AuthStyle.NONE, ResponseStyle.STATIC_LIST
    );

    public static final Provider FALLBACK = new Provider(
            "nvidia", "NVIDIA", icon("nvidia.png"),
            "meta/llama-3.1-70b-instruct",
            "https://integrate.api.nvidia.com/v1/models",
            AuthStyle.BEARER, ResponseStyle.OPENAI_DATA_ID
    );

    /** Порядок этого списка = порядок пунктов в дропдауне. */
    public static final List<Provider> ALL = List.of(
            AGY,
            new Provider("anthropic", "Anthropic", icon("anthropic.png"),
                    "claude-sonnet-4-5",
                    "https://api.anthropic.com/v1/models",
                    AuthStyle.ANTHROPIC, ResponseStyle.ANTHROPIC_DATA),
            new Provider("openai", "OpenAI", icon("openai.png"),
                    "gpt-5.5",
                    "https://api.openai.com/v1/models",
                    AuthStyle.BEARER, ResponseStyle.OPENAI_DATA_ID),
            new Provider("openrouter", "OpenRouter", icon("openrouter.png"),
                    "openai/gpt-5.2",
                    "https://openrouter.ai/api/v1/models",
                    AuthStyle.BEARER, ResponseStyle.OPENAI_DATA_ID),
            new Provider("gemini", "Google Gemini", icon("gemini.png"),
                    "gemini-3.5-flash",
                    "https://generativelanguage.googleapis.com/v1beta/models",
                    AuthStyle.GOOGLE_API_KEY, ResponseStyle.GEMINI_MODELS),
            new Provider("deepseek", "DeepSeek", icon("deepseek.png"),
                    "deepseek-v4-flash",
                    "https://api.deepseek.com/models",
                    AuthStyle.BEARER, ResponseStyle.OPENAI_DATA_ID),
            new Provider("xai", "xAI (Grok)", icon("xai.png"),
                    "grok-4.3",
                    "https://api.x.ai/v1/models",
                    AuthStyle.BEARER, ResponseStyle.OPENAI_DATA_ID),
            FALLBACK
    );

    private ProviderRegistry() {
    }

    public static Provider byCode(String code) {
        for (Provider p : ALL) {
            if (p.code().equals(code)) return p;
        }
        return FALLBACK;
    }

    public static String displayName(String code) {
        return byCode(code).displayName();
    }

    public static Identifier iconFor(String code) {
        return byCode(code).icon();
    }

    public static String defaultModel(String code) {
        return byCode(code).defaultModel();
    }

    public static String modelsUrl(String code) {
        return byCode(code).modelsUrl();
    }
}