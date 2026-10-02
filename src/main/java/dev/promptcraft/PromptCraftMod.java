package dev.promptcraft;

import dev.promptcraft.config.PromptCraftConfigManager;
import dev.promptcraft.network.PromptCraftNetworking;
import dev.promptcraft.session.PromptSessionManager;
import dev.promptcraft.task.TaskManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PromptCraftMod implements ModInitializer {
    public static final String MOD_ID = "promptcraft";
    public static final Logger LOGGER = LoggerFactory.getLogger("PromptCraft");

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing PromptCraft...");
        PromptCraftConfigManager.load();
        PromptCraftItems.register();
        PromptCraftItemGroups.register();
        PromptCraftCommands.register();
        dev.promptcraft.network.PromptCraftPayloads.register();
        PromptCraftNetworking.registerServerReceivers();
        TaskManager.init();

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            var player = handler.getPlayer();
            PromptSessionManager.getActiveGeneration(player).ifPresent(session -> {
                if (session.isGhostPending()) {
                    session.cancel();
                    session.abortHttpRequest();
                    PromptSessionManager.clearGeneration(player);
                }
            });
        });

        LOGGER.info("PromptCraft initialized.");
    }
}
