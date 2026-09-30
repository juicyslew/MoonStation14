package com.juicyslew.moonstation14.ms14.player_body_control.action;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Operator-only, read-only inspection of an exact BODY component; never executes an action. */
@EventBusSubscriber(modid = MoonStation14.MOD_ID)
public final class BodyActionDiagnosticCommand {
    private BodyActionDiagnosticCommand() { }

    enum Result {
        NO_EXACT_BODY("No exact eligible committed CHARACTER body is current."),
        NO_COMPONENT("Exact CHARACTER body has no enabled ComplexInteraction component; no action executed."),
        STUNNED("Exact CHARACTER body has ComplexInteraction enabled but is stunned; no action executed."),
        COMPONENT_ENABLED("Exact CHARACTER body has ComplexInteraction enabled; no action executed.");

        final String message;

        Result(String message) { this.message = message; }
    }

    /** Pure, bounded diagnostic outcome; enabled state is not a world-action grant. */
    static Result decision(boolean exactBody, boolean current, boolean componentEnabled, boolean stunned) {
        if (!exactBody || !current) return Result.NO_EXACT_BODY;
        if (!componentEnabled) return Result.NO_COMPONENT;
        return stunned ? Result.STUNNED : Result.COMPONENT_ENABLED;
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ms14actioncheck")
                .requires(source -> source.hasPermission(2))
                .executes(context -> check(context.getSource())));
    }

    private static int check(CommandSourceStack source) {
        if (!source.hasPermission(2) || !(source.getEntity() instanceof ServerPlayer player)
                || player instanceof FakePlayer || player.getServer() == null
                || !player.getServer().isSameThread()
                || player.getServer().getPlayerList().getPlayer(player.getUUID()) != player)
            return 0;

        var snapshot = BodyActionAuthority.resolve(player);
        boolean current = snapshot.isPresent() && BodyActionAuthority.revalidate(player, snapshot.orElseThrow());
        boolean componentEnabled = current && snapshot.orElseThrow().identity().complexInteractionEnabled();
        boolean stunned = componentEnabled && CharacterControlSystem.isStunned(snapshot.orElseThrow().body());
        var result = decision(snapshot.isPresent(), current, componentEnabled, stunned);
        source.sendSuccess(() -> Component.literal(result.message), false);
        return 1;
    }
}
