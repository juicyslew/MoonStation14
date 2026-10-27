package com.juicyslew.moonstation14.ms14.actions.fixture;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.hands.live.BodyGiveService;
import com.juicyslew.moonstation14.ms14.player_body_control.action.BodyActionAuthority;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Narrow operator fixture command for granting the registered debug action cap to a character body. */
@EventBusSubscriber(modid = MoonStation14.MOD_ID)
public final class BodyActionFixtureCommands {
    private static final ResourceLocation DEBUG_CAP = ResourceLocation.fromNamespaceAndPath("moonstation14", "debug_capability_cap");

    private BodyActionFixtureCommands() { }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ms14")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("capabilities")
                        .then(Commands.literal("debug_cap").executes(context -> grant(context.getSource())))));
    }

    private static int grant(net.minecraft.commands.CommandSourceStack source) {
        if (!source.hasPermission(2) || !(source.getEntity() instanceof ServerPlayer actor)
                || actor instanceof FakePlayer || actor.getServer() == null
                || !actor.getServer().isSameThread()
                || actor.getServer().getPlayerList().getPlayer(actor.getUUID()) != actor) {
            source.sendFailure(Component.literal("Debug cap grant requires an authenticated online operator character."));
            return 0;
        }
        var authority = BodyActionAuthority.resolve(actor).orElse(null);
        if (authority == null || !BodyActionAuthority.revalidate(actor, authority)) {
            source.sendFailure(Component.literal("Debug cap grant requires a committed authenticated CHARACTER body."));
            return 0;
        }
        Item item = BuiltInRegistries.ITEM.get(DEBUG_CAP);
        if (item == null || item == net.minecraft.world.item.Items.AIR
                || !DEBUG_CAP.equals(BuiltInRegistries.ITEM.getKey(item))) {
            source.sendFailure(Component.literal("Registered item moonstation14:debug_capability_cap is unavailable; nothing granted."));
            return 0;
        }
        if (!BodyGiveService.giveTrustedBody(authority.body(), new ItemStack(item),
                () -> BodyActionAuthority.revalidate(actor, authority))) {
            source.sendFailure(Component.literal("Debug cap could not be placed in the character body's hands or nearby world; worn gear was not changed."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Debug cap granted to the character body. Equip it in the head slot, then press K for Debug pulse."), false);
        return 1;
    }
}
