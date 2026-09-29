package com.juicyslew.moonstation14.ms14.ui.client.gallery;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/** Explicit, client-only entry point. No server command or packet is registered. */
public final class MachineUiGalleryCommands {
    private MachineUiGalleryCommands() {}

    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(LiteralArgumentBuilder.<CommandSourceStack>literal("ms14_ui_gallery")
                .executes(context -> {
                    Minecraft.getInstance().setScreen(new MachineUiGalleryScreen());
                    return 1;
                }));
    }
}
