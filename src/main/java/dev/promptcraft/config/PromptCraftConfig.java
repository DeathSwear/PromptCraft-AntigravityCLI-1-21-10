package dev.promptcraft.config;

public class PromptCraftConfig {
    public String provider = "agy";
    public String baseUrl = "";
    public String model = "gemini-3.8-flash-high";
    public String accessMode = "admins_only";
    public String themeColor = "#17b95f"; // Default neon green
    public String language = "en"; // en or ru
    
    public String generationMode = "manual"; // "manual" или "free"
    public String buildMode = "creative"; // "creative" (высокая креативность) или "precise" (строго по промпту)

    public boolean showProcessMessages = true;
    public boolean enableDestructionAnimation = true;
    public boolean showSelectionPreview = true;
    public boolean proceduralTexturing = true;

    public boolean thickSelectionOutline = true;
    public float selectionFillOpacity = 0.075f;
    public boolean selectionOutlineThroughBlocks = false;

    public boolean selectionLimitEnabled = false;
    public int maxSelectionWidth = 64;
    public int maxSelectionHeight = 64;
    public int maxSelectionDepth = 64;

    public int maxRepairAttempts = 3;
    public int maxOutputTokens = 16384;

    public boolean isAccessEveryone() {
        return "everyone".equalsIgnoreCase(accessMode);
    }
}