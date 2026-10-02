package dev.promptcraft.client;

/**
 * Клиентское хранилище черновика текста для вкладки «Изменить».
 */
public final class ModifyDraftState {
    private static volatile String draft = "";

    private ModifyDraftState() {
    }

    public static String get() {
        return draft;
    }

    public static void set(String text) {
        draft = text == null ? "" : text;
    }

    public static void clear() {
        draft = "";
    }
}
