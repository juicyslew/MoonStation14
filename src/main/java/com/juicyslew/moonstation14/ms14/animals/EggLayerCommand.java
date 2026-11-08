package com.juicyslew.moonstation14.ms14.animals;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.player_body_control.action.BodyActionAuthority;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Minecraft command adaptation for SS14's controlled lay-egg action. */
@EventBusSubscriber(modid = MoonStation14.MOD_ID)
public final class EggLayerCommand {
    private EggLayerCommand() { }
    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("layegg").executes(ctx -> {
            var source = ctx.getSource();
            if (!(source.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer
                    || player.getServer() == null || !player.getServer().isSameThread()
                    || player.getServer().getPlayerList().getPlayer(player.getUUID()) != player) return 0;
            var authority = BodyActionAuthority.resolve(player).orElse(null);
            if (authority == null || !BodyActionAuthority.revalidate(player, authority)
                    || !EggLayerSystem.manualLay(player, authority.body())) {
                source.sendFailure(Component.literal("Your controlled body cannot lay an egg now."));
                return 0;
            }
            source.sendSuccess(() -> Component.literal("Your body lays an egg."), false);
            return 1;
        }));
    }
}
