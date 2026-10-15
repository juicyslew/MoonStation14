package com.juicyslew.moonstation14.ms14.mobs;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.character.components.MobStateComponent;
import com.juicyslew.moonstation14.ms14.damage.DamageSystem;
import com.juicyslew.moonstation14.ms14.medical_observer.ObserverSession;
import com.juicyslew.moonstation14.ms14.medical_observer.ObserverSystem;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Operator recovery of only the exact linked DEAD_CLAIM character; never selects/recreates entities. */
@EventBusSubscriber(modid = MoonStation14.MOD_ID)
public final class LinkedCharacterReviveCommand {
    private LinkedCharacterReviveCommand() { }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ms14").requires(source -> source.hasPermission(2))
                .then(Commands.literal("revive").requires(source -> source.hasPermission(2))
                        .executes(context -> revive(context.getSource()))));
    }

    private static int revive(net.minecraft.commands.CommandSourceStack source) {
        if (!source.hasPermission(2) || !(source.getEntity() instanceof ServerPlayer player)
                || player.getServer() == null || !player.getServer().isSameThread()
                || player.isRemoved() || player.serverLevel().getEntity(player.getUUID()) != player) {
            source.sendFailure(Component.literal("Run this operator command as a connected player."));
            return 0;
        }
        ObserverSession session = ObserverSystem.query(player);
        if (session == null || session.boundAuthority() == null
                || (session.mode() != ObserverSession.Mode.GHOST && session.mode() != ObserverSession.Mode.CORPSE)) {
            source.sendFailure(Component.literal("No linked dead character is available to revive."));
            return 0;
        }
        var level = player.serverLevel();
        var entity = level.getEntity(session.corpseBodyId());
        if (!(entity instanceof net.minecraft.world.entity.LivingEntity body) || body.isRemoved()
                || !session.ownerId().equals(player.getUUID())
                || !session.boundAuthority().valid(player, body, session)
                || body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.MOB_STATE.get()) == null
                || body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.MOB_STATE.get()).data().state()
                    != MobStateComponent.State.DEAD) {
            source.sendFailure(Component.literal("The exact linked dead character is unavailable or no longer dead."));
            return 0;
        }
        DamageSystem.clearTypedDamageForRecovery(body);
        if (!MobStateSystem.explicitRevive(body)) {
            source.sendFailure(Component.literal("Linked character recovery failed; no body was replaced."));
            return 0;
        }
        String message = session.mode() == ObserverSession.Mode.GHOST
                ? "Linked character revived. Press R to return to your body."
                : "Linked character revived; normal character control will resume automatically.";
        source.sendSuccess(() -> Component.literal(message), false);
        return 1;
    }
}

