package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.movement.MovementStartupGate;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterAppearance;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterAppearanceService;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterBodyShape;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity;
import com.juicyslew.moonstation14.ms14.player_body_control.server.MindGhostStartupGate;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Minimal owner-only, durable-before-entity appearance controls. */
@EventBusSubscriber(modid = MoonStation14.MOD_ID)
public final class LifecycleAppearanceCommands {
    private LifecycleAppearanceCommands() { }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ms14char")
                .then(Commands.literal("skin")
                        .then(Commands.literal("default").executes(context -> changeSkin(context.getSource().getPlayerOrException(), "default")))
                        .then(Commands.literal("alex").executes(context -> changeSkin(context.getSource().getPlayerOrException(), "alex"))))
                .then(Commands.literal("shape")
                        .then(Commands.literal("wide").executes(context -> changeShape(context.getSource().getPlayerOrException(), "wide")))
                        .then(Commands.literal("slim").executes(context -> changeShape(context.getSource().getPlayerOrException(), "slim")))));
    }

    static PlayerCharacterAppearance parseSkin(String option) {
        if ("default".equals(option)) return PlayerCharacterAppearance.DEFAULT;
        if ("alex".equals(option)) return PlayerCharacterAppearance.ALEX;
        return null;
    }

    static PlayerCharacterBodyShape parseShape(String option) {
        if ("wide".equals(option)) return PlayerCharacterBodyShape.WIDE;
        if ("slim".equals(option)) return PlayerCharacterBodyShape.SLIM;
        return null;
    }

    private static int changeSkin(ServerPlayer player, String option) {
        PlayerCharacterAppearance value = parseSkin(option);
        return value == null ? invalid(player) : apply(player, "appearance", value.name(), value, null);
    }

    private static int changeShape(ServerPlayer player, String option) {
        PlayerCharacterBodyShape value = parseShape(option);
        return value == null ? invalid(player) : apply(player, "bodyShape", value.name(), null, value);
    }

    private static int apply(ServerPlayer player, String field, String storedValue,
                             PlayerCharacterAppearance appearance, PlayerCharacterBodyShape shape) {
        if (!MindGhostStartupGate.enabledForServer() || MovementStartupGate.enabledForServer())
            return reply(player, "Character appearance is unavailable under the current server gates.");
        if (player == null || !(player.level() instanceof ServerLevel level) || level.getServer() == null
                || !level.getServer().isSameThread()) return 0;
        LifecycleServerContext context = LifecycleStartupRuntime.contextFor(level.getServer()).orElse(null);
        if (context == null) return reply(player, "Character appearance is unavailable; retry later.");
        PlayerCharacterHarnessEntity body = level.getEntity(player.getCamera().getUUID()) instanceof PlayerCharacterHarnessEntity candidate
                ? candidate : null;
        var binding = body == null ? null : body.playerCharacterBinding();
        var identity = body == null ? null : body.getExistingDataOrNull(ModDataAttachments.CHARACTER_IDENTITY.get());
        var profile = context.lifecycle().profile(player.getUUID()).orElse(null);
        long generation = profile == null ? -1 : profile.connectionGeneration();
        if (binding == null || identity == null || !identity.isBound()
                || !ModCharacters.HUMAN_ID.equals(identity.characterId()) || CharacterIdentitySystem.resolve(body).isEmpty()
                || !LifecycleCharacterSessionControl.ownsCommittedAppearanceSession(player, body, context.lifecycle(), generation))
            return reply(player, "Appearance changes require your active character body.");
        boolean saved;
        try {
            saved = context.writeActiveAppearance(player.getUUID(), binding.profileKey(), profile.mindId().value(),
                    body.getUUID(), generation, field, storedValue);
        } catch (java.io.IOException | RuntimeException failure) {
            saved = false;
        }
        if (!saved) return reply(player, "Appearance was not changed; retry or request recovery.");
        boolean applied;
        try {
            applied = appearance != null
                    ? PlayerCharacterAppearanceService.setAppearance(body, player, appearance, context.lifecycle(), generation)
                    : PlayerCharacterAppearanceService.setBodyShape(body, player, shape, context.lifecycle(), generation);
        } catch (RuntimeException | Error failure) {
            applied = false;
        }
        if (!applied) {
            LifecycleCharacterSessionControl.failAppearanceSession(player, body, generation);
            return reply(player, "Appearance was saved but could not be applied; session suspended for recovery.");
        }
        return reply(player, "Character " + field + " set to " + storedValue.toLowerCase(java.util.Locale.ROOT) + ".");
    }

    private static int invalid(ServerPlayer player) { return reply(player, "Invalid appearance option."); }

    private static int reply(ServerPlayer player, String message) {
        if (player != null) player.sendSystemMessage(Component.literal(message));
        return 0;
    }
}
