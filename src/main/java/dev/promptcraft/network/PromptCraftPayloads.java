package dev.promptcraft.network;

import dev.promptcraft.PromptCraftMod;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;

public final class PromptCraftPayloads {

    private PromptCraftPayloads() {}

    public static void register() {
        // C2S (Client -> Server)
        PayloadTypeRegistry.playC2S().register(RequestOpenGuiPayload.ID, RequestOpenGuiPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SaveGuiPayload.ID, SaveGuiPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(GuiActionPayload.ID, GuiActionPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ConfirmPlacementPayload.ID, ConfirmPlacementPayload.CODEC);

        // S2C (Server -> Client)
        PayloadTypeRegistry.playS2C().register(SelectionSyncPayload.ID, SelectionSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenGuiPayload.ID, OpenGuiPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(AiStreamPayload.ID, AiStreamPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(StartFreePlacementPayload.ID, StartFreePlacementPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CancelFreePlacementPayload.ID, CancelFreePlacementPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BuildProgressPayload.ID, BuildProgressPayload.CODEC);
    }

    static void writeGuiSettings(
            String provider, Map<String, String> apiKeys, String model,
            boolean showPreview, String language, String themeColor,
            boolean thickOutline, float fillOpacity, boolean outlineThroughBlocks,
            boolean selectionLimitEnabled, int maxSelectionWidth, int maxSelectionHeight, int maxSelectionDepth,
            PacketByteBuf buf
    ) {
        buf.writeString(provider != null ? provider : "");
        buf.writeInt(apiKeys != null ? apiKeys.size() : 0);
        if (apiKeys != null) {
            for (Map.Entry<String, String> entry : apiKeys.entrySet()) {
                buf.writeString(entry.getKey());
                buf.writeString(entry.getValue());
            }
        }
        buf.writeString(model != null ? model : "");
        buf.writeBoolean(showPreview);
        buf.writeString(language != null ? language : "");
        buf.writeString(themeColor != null ? themeColor : "");
        buf.writeBoolean(thickOutline);
        buf.writeFloat(fillOpacity);
        buf.writeBoolean(outlineThroughBlocks);
        buf.writeBoolean(selectionLimitEnabled);
        buf.writeInt(maxSelectionWidth);
        buf.writeInt(maxSelectionHeight);
        buf.writeInt(maxSelectionDepth);
    }

    static SaveGuiPayload readSaveGui(PacketByteBuf buf) {
        String provider = buf.readString();
        int keyCount = buf.readInt();
        Map<String, String> apiKeys = new HashMap<>();
        for (int i = 0; i < keyCount; i++) {
            apiKeys.put(buf.readString(), buf.readString());
        }
        return new SaveGuiPayload(
                provider, apiKeys, buf.readString(), buf.readBoolean(),
                buf.readString(), buf.readString(), buf.readBoolean(),
                buf.readFloat(), buf.readBoolean(), buf.readBoolean(),
                buf.readInt(), buf.readInt(), buf.readInt()
        );
    }

    static OpenGuiPayload readOpenGui(PacketByteBuf buf) {
        String provider = buf.readString();
        int keyCount = buf.readInt();
        Map<String, String> apiKeys = new HashMap<>();
        for (int i = 0; i < keyCount; i++) {
            apiKeys.put(buf.readString(), buf.readString());
        }
        return new OpenGuiPayload(
                provider, apiKeys, buf.readString(), buf.readBoolean(),
                buf.readString(), buf.readString(), buf.readBoolean(),
                buf.readFloat(), buf.readBoolean(), buf.readBoolean(),
                buf.readInt(), buf.readInt(), buf.readInt()
        );
    }

    // --- C2S Payloads ---

    public record RequestOpenGuiPayload() implements CustomPayload {
        public static final CustomPayload.Id<RequestOpenGuiPayload> ID = new CustomPayload.Id<>(Identifier.of(PromptCraftMod.MOD_ID, "request_open_gui"));
        public static final PacketCodec<PacketByteBuf, RequestOpenGuiPayload> CODEC = PacketCodec.unit(new RequestOpenGuiPayload());
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record ConfirmPlacementPayload(BlockPos anchor, int rotationSteps) implements CustomPayload {
        public static final CustomPayload.Id<ConfirmPlacementPayload> ID = new CustomPayload.Id<>(Identifier.of(PromptCraftMod.MOD_ID, "confirm_placement"));
        public static final PacketCodec<PacketByteBuf, ConfirmPlacementPayload> CODEC = PacketCodec.of(
                (val, buf) -> {
                    buf.writeBlockPos(val.anchor);
                    buf.writeInt(val.rotationSteps);
                },
                buf -> new ConfirmPlacementPayload(buf.readBlockPos(), buf.readInt())
        );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record GuiActionPayload(String action, String promptText) implements CustomPayload {
        public static final CustomPayload.Id<GuiActionPayload> ID = new CustomPayload.Id<>(Identifier.of(PromptCraftMod.MOD_ID, "gui_action"));
        public static final PacketCodec<PacketByteBuf, GuiActionPayload> CODEC = PacketCodec.of(
                (val, buf) -> {
                    buf.writeString(val.action);
                    buf.writeString(val.promptText);
                },
                buf -> new GuiActionPayload(buf.readString(), buf.readString())
        );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record SaveGuiPayload(
            String provider, Map<String, String> apiKeys, String model,
            boolean showPreview, String language, String themeColor,
            boolean thickOutline, float fillOpacity, boolean outlineThroughBlocks,
            boolean selectionLimitEnabled, int maxSelectionWidth, int maxSelectionHeight, int maxSelectionDepth
    ) implements CustomPayload {
        public static final CustomPayload.Id<SaveGuiPayload> ID = new CustomPayload.Id<>(Identifier.of(PromptCraftMod.MOD_ID, "save_gui"));
        public static final PacketCodec<PacketByteBuf, SaveGuiPayload> CODEC = PacketCodec.of(
                (val, buf) -> writeGuiSettings(val.provider, val.apiKeys, val.model, val.showPreview, val.language, val.themeColor, val.thickOutline, val.fillOpacity, val.outlineThroughBlocks, val.selectionLimitEnabled, val.maxSelectionWidth, val.maxSelectionHeight, val.maxSelectionDepth, buf),
                PromptCraftPayloads::readSaveGui
        );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    // --- S2C Payloads ---

    public record SelectionSyncPayload(boolean hasFirst, BlockPos first, boolean hasSecond, BlockPos second) implements CustomPayload {
        public static final CustomPayload.Id<SelectionSyncPayload> ID = new CustomPayload.Id<>(Identifier.of(PromptCraftMod.MOD_ID, "selection_sync"));
        public static final PacketCodec<PacketByteBuf, SelectionSyncPayload> CODEC = PacketCodec.of(
                (val, buf) -> {
                    buf.writeBoolean(val.hasFirst);
                    if (val.hasFirst) buf.writeBlockPos(val.first);
                    buf.writeBoolean(val.hasSecond);
                    if (val.hasSecond) buf.writeBlockPos(val.second);
                },
                buf -> {
                    boolean hasFirst = buf.readBoolean();
                    BlockPos first = hasFirst ? buf.readBlockPos() : null;
                    boolean hasSecond = buf.readBoolean();
                    BlockPos second = hasSecond ? buf.readBlockPos() : null;
                    return new SelectionSyncPayload(hasFirst, first, hasSecond, second);
                }
        );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record OpenGuiPayload(
            String provider, Map<String, String> apiKeys, String model,
            boolean showPreview, String language, String themeColor,
            boolean thickOutline, float fillOpacity, boolean outlineThroughBlocks,
            boolean selectionLimitEnabled, int maxSelectionWidth, int maxSelectionHeight, int maxSelectionDepth
    ) implements CustomPayload {
        public static final CustomPayload.Id<OpenGuiPayload> ID = new CustomPayload.Id<>(Identifier.of(PromptCraftMod.MOD_ID, "open_gui"));
        public static final PacketCodec<PacketByteBuf, OpenGuiPayload> CODEC = PacketCodec.of(
                (val, buf) -> writeGuiSettings(val.provider, val.apiKeys, val.model, val.showPreview, val.language, val.themeColor, val.thickOutline, val.fillOpacity, val.outlineThroughBlocks, val.selectionLimitEnabled, val.maxSelectionWidth, val.maxSelectionHeight, val.maxSelectionDepth, buf),
                PromptCraftPayloads::readOpenGui
        );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record AiStreamPayload(String eventType, String payload) implements CustomPayload {
        public static final CustomPayload.Id<AiStreamPayload> ID = new CustomPayload.Id<>(Identifier.of(PromptCraftMod.MOD_ID, "ai_stream"));
        public static final PacketCodec<PacketByteBuf, AiStreamPayload> CODEC = PacketCodec.of(
                (val, buf) -> {
                    buf.writeString(val.eventType);
                    buf.writeString(val.payload == null ? "" : val.payload);
                },
                buf -> new AiStreamPayload(buf.readString(), buf.readString())
        );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record StartFreePlacementPayload(String structureJson) implements CustomPayload {
        public static final CustomPayload.Id<StartFreePlacementPayload> ID = new CustomPayload.Id<>(Identifier.of(PromptCraftMod.MOD_ID, "start_free_placement"));
        public static final PacketCodec<PacketByteBuf, StartFreePlacementPayload> CODEC = PacketCodec.of(
                (val, buf) -> buf.writeString(val.structureJson),
                buf -> new StartFreePlacementPayload(buf.readString())
        );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record CancelFreePlacementPayload() implements CustomPayload {
        public static final CustomPayload.Id<CancelFreePlacementPayload> ID = new CustomPayload.Id<>(Identifier.of(PromptCraftMod.MOD_ID, "cancel_free_placement"));
        public static final PacketCodec<PacketByteBuf, CancelFreePlacementPayload> CODEC = PacketCodec.unit(new CancelFreePlacementPayload());
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record BuildProgressPayload(int percent, boolean active) implements CustomPayload {
        public static final CustomPayload.Id<BuildProgressPayload> ID = new CustomPayload.Id<>(Identifier.of(PromptCraftMod.MOD_ID, "build_progress"));
        public static final PacketCodec<PacketByteBuf, BuildProgressPayload> CODEC = PacketCodec.of(
                (val, buf) -> {
                    buf.writeInt(val.percent);
                    buf.writeBoolean(val.active);
                },
                buf -> new BuildProgressPayload(buf.readInt(), buf.readBoolean())
        );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
}
