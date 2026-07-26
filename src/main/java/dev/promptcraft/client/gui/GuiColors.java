package dev.promptcraft.client.gui;

/** Чистые преобразования цвета. Никакого состояния. */
public final class GuiColors {

    private GuiColors() {
    }

    public static int parseArgb(String hex, int fallback) {
        if (hex == null) return fallback;
        try {
            return 0xFF000000 | Integer.parseInt(hex.replace("#", ""), 16);
        } catch (Exception e) {
            return fallback;
        }
    }

    /** @return {hue, sat, val} в диапазоне 0..1, либо null если hex некорректен. */
    public static float[] hexToHsv(String hex) {
        try {
            int rgb = Integer.parseInt(hex.replace("#", ""), 16);

            float rf = ((rgb >> 16) & 0xFF) / 255f;
            float gf = ((rgb >> 8) & 0xFF) / 255f;
            float bf = (rgb & 0xFF) / 255f;

            float max = Math.max(rf, Math.max(gf, bf));
            float min = Math.min(rf, Math.min(gf, bf));
            float d = max - min;

            float val = max;
            float sat = max == 0 ? 0 : d / max;
            float hue;

            if (d == 0) {
                hue = 0;
            } else if (max == rf) {
                hue = ((gf - bf) / d + 6) % 6 / 6f;
            } else if (max == gf) {
                hue = ((bf - rf) / d + 2) / 6f;
            } else {
                hue = ((rf - gf) / d + 4) / 6f;
            }

            return new float[]{hue, sat, val};
        } catch (Exception ignored) {
            return null;
        }
    }

    public static String hsvToHex(float h, float s, float v) {
        return String.format("#%06X", hsvToRgb(h, s, v));
    }

    public static int hsvToRgb(float h, float s, float v) {
        int i = (int) (h * 6);
        float f = h * 6 - i;

        float p = v * (1 - s);
        float q = v * (1 - f * s);
        float t = v * (1 - (1 - f) * s);

        float r = 0;
        float g = 0;
        float b = 0;

        switch (i % 6) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            case 5 -> { r = v; g = p; b = q; }
        }

        return ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }

    /** true — на светлом фоне лучше рисовать чёрным текстом. */
    public static boolean isLight(float value) {
        return value > 0.45f;
    }
}