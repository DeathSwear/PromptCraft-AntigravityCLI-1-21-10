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
        BEARER,          // Authorization: Bearer <key>
        GOOGLE_API_KEY,  // x-goog-api-key: <key>
        ANTHROPIC        // x-api-key + anthropic-version
    }

    public enum ResponseStyle {
        OPENAI_DATA_ID,   // {"data":[{"id":...}]}
        GEMINI_MODELS,    // {"models":[{"name":"models/..."}]}
        ANTHROPIC_DATA    // {"data":[{"id":..., "max_tokens":...}]}
    }

    private static Identifier icon(String file) {
        return Identifier.of(PromptCraftMod.MOD_ID, "textures/gui/" + file);
    }

    public static final Provider FALLBACK = new Provider(
            "nvidia", "NVIDIA", icon("nvidia.png"),
            "meta/llama-3.1-70b-instruct",
            "https://integrate.api.nvidia.com/v1/models",
            AuthStyle.BEARER, ResponseStyle.OPENAI_DATA_ID
    );

    /** Порядок этого списка = порядок пунктов в дропдауне. */
    public static final List<Provider> ALL = List.of(
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