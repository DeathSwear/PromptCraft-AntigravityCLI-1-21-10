package dev.promptcraft.client.gui;

import dev.promptcraft.config.PromptCraftConfigManager;

import java.util.HashMap;
import java.util.Map;

/**
 * Всё мутабельное состояние экрана настроек в одном месте.
 * Табы читают/пишут только через этот объект, поэтому у них нет доступа
 * к приватным полям Screen и нет соблазна лезть друг к другу.
 */
public final class SettingsState {

    public static final String DEFAULT_THEME_COLOR = "#17b95f";
    public static final long SAVE_DEBOUNCE_MS = 500L;

    private String provider;
    private Map<String, String> apiKeys;
    private String model;
    private boolean showPreview;
    private String language;
    private String themeColor;
    private boolean thickOutline;
    private float fillOpacity;
    private boolean outlineThroughBlocks;
    private boolean proceduralTexturing = true;

    private boolean selectionLimitEnabled;
    private int maxSelectionWidth;
    private int maxSelectionHeight;
    private int maxSelectionDepth;

    private float pickerHue = 0.33f;
    private float pickerSat = 0.85f;
    private float pickerVal = 0.72f;

    private boolean pendingSave = false;
    private long lastChangeTime = 0L;

    public SettingsState(
            String provider,
            Map<String, String> apiKeys,
            String model,
            boolean showPreview,
            String language,
            String themeColor,
            boolean thickOutline,
            float fillOpacity,
            boolean outlineThroughBlocks,
            boolean selectionLimitEnabled,
            int maxSelectionWidth,
            int maxSelectionHeight,
            int maxSelectionDepth,
            boolean proceduralTexturing
    ) {
        this.provider = provider != null && !provider.isBlank() ? provider : "nvidia";
        this.apiKeys = apiKeys != null ? apiKeys : new HashMap<>();
        this.model = model != null ? model : "";
        this.showPreview = showPreview;
        this.language = language != null ? language : "en";
        this.themeColor = themeColor != null && !themeColor.isEmpty() ? themeColor : DEFAULT_THEME_COLOR;
        this.thickOutline = thickOutline;
        this.fillOpacity = Math.max(0.0f, Math.min(1.0f, fillOpacity));
        this.outlineThroughBlocks = outlineThroughBlocks;
        this.proceduralTexturing = proceduralTexturing;

        this.selectionLimitEnabled = selectionLimitEnabled;
        this.maxSelectionWidth = Math.max(1, maxSelectionWidth);
        this.maxSelectionHeight = Math.max(1, maxSelectionHeight);
        this.maxSelectionDepth = Math.max(1, maxSelectionDepth);

        syncPickerFromHex();
    }

    // --- локализация ---

    public boolean isRussian() {
        return "ru".equals(language);
    }

    public String t(String en, String ru) {
        return isRussian() ? ru : en;
    }

    // --- dirty tracking ---

    public void markDirty() {
        pendingSave = true;
        lastChangeTime = System.currentTimeMillis();
    }

    public boolean isDirty() {
        return pendingSave;
    }

    public boolean shouldFlush() {
        return pendingSave && System.currentTimeMillis() - lastChangeTime >= SAVE_DEBOUNCE_MS;
    }

    public void clearDirty() {
        pendingSave = false;
    }

    // --- провайдер / ключи / модель ---

    public String provider() {
        return provider;
    }

    public void setProvider(String value) {
        this.provider = value;
        markDirty();
    }

    public Map<String, String> apiKeys() {
        return apiKeys;
    }

    public String apiKeyFor(String providerCode) {
        return apiKeys.getOrDefault(providerCode, "");
    }

    public void putApiKey(String providerCode, String key) {
        apiKeys.put(providerCode, key);
        markDirty();
    }

    /** Возвращает копию ключей с актуальным значением для текущего провайдера. */
    public Map<String, String> apiKeysWith(String providerCode, String key) {
        Map<String, String> copy = new HashMap<>(apiKeys);
        copy.put(providerCode, key);
        this.apiKeys = copy;
        return copy;
    }

    public String model() {
        return model;
    }

    public void setModel(String value) {
        this.model = value;
        markDirty();
    }

    // --- режимы (живут в конфиге, поведение как в оригинале) ---

    public String generationMode() {
        return PromptCraftConfigManager.get().generationMode;
    }

    public void setGenerationMode(String code) {
        PromptCraftConfigManager.get().generationMode = code;
        markDirty();
    }

    public String buildMode() {
        return PromptCraftConfigManager.get().buildMode;
    }

    public void setBuildMode(String code) {
        PromptCraftConfigManager.get().buildMode = code;
        markDirty();
    }

    // --- визуал ---

    public boolean showPreview() {
        return showPreview;
    }

    public void toggleShowPreview() {
        showPreview = !showPreview;
        markDirty();
    }

    public String language() {
        return language;
    }

    public void setLanguage(String code) {
        this.language = code;
        markDirty();
    }

    public boolean thickOutline() {
        return thickOutline;
    }

    public void toggleThickOutline() {
        thickOutline = !thickOutline;
        markDirty();
    }

    public boolean outlineThroughBlocks() {
        return outlineThroughBlocks;
    }

    public void toggleOutlineThroughBlocks() {
        outlineThroughBlocks = !outlineThroughBlocks;
        markDirty();
    }

    public boolean proceduralTexturing() {
        return proceduralTexturing;
    }

    public void toggleProceduralTexturing() {
        proceduralTexturing = !proceduralTexturing;
        markDirty();
    }

    public float fillOpacity() {
        return fillOpacity;
    }

    public void setFillOpacity(float value) {
        this.fillOpacity = Math.max(0.0f, Math.min(1.0f, value));
        markDirty();
    }

    // --- тема / color picker ---

    public String themeColor() {
        return themeColor;
    }

    public int themeColorArgb() {
        return GuiColors.parseArgb(themeColor, 0xFF17B95F);
    }

    /** Тихая установка без markDirty — для случаев, когда цвет пришёл из самого поля ввода. */
    public void setThemeColorQuiet(String hex) {
        this.themeColor = hex;
        syncPickerFromHex();
    }

    public void setThemeColor(String hex) {
        setThemeColorQuiet(hex);
        markDirty();
    }

    public void resetThemeColor() {
        setThemeColor(DEFAULT_THEME_COLOR);
    }

    public float pickerHue() {
        return pickerHue;
    }

    public float pickerSat() {
        return pickerSat;
    }

    public float pickerVal() {
        return pickerVal;
    }

    public void setSaturationValue(float sat, float val) {
        this.pickerSat = clamp01(sat);
        this.pickerVal = clamp01(val);
        this.themeColor = GuiColors.hsvToHex(pickerHue, pickerSat, pickerVal);
        markDirty();
    }

    public void setHue(float hue) {
        this.pickerHue = clamp01(hue);
        this.themeColor = GuiColors.hsvToHex(pickerHue, pickerSat, pickerVal);
        markDirty();
    }

    private void syncPickerFromHex() {
        float[] hsv = GuiColors.hexToHsv(themeColor);
        if (hsv == null) {
            pickerHue = 0.33f;
            pickerSat = 0.85f;
            pickerVal = 0.72f;
            return;
        }
        pickerHue = hsv[0];
        pickerSat = hsv[1];
        pickerVal = hsv[2];
    }

    // --- лимиты области ---

    public boolean selectionLimitEnabled() {
        return selectionLimitEnabled;
    }

    public void toggleSelectionLimit() {
        selectionLimitEnabled = !selectionLimitEnabled;
        markDirty();
    }

    public int maxSelectionWidth() {
        return maxSelectionWidth;
    }

    public int maxSelectionHeight() {
        return maxSelectionHeight;
    }

    public int maxSelectionDepth() {
        return maxSelectionDepth;
    }

    public void setMaxSelectionWidth(int value) {
        this.maxSelectionWidth = Math.max(1, value);
        markDirty();
    }

    public void setMaxSelectionHeight(int value) {
        this.maxSelectionHeight = Math.max(1, value);
        markDirty();
    }

    public void setMaxSelectionDepth(int value) {
        this.maxSelectionDepth = Math.max(1, value);
        markDirty();
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}