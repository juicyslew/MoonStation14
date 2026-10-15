package com.juicyslew.moonstation14.ms14.chat.server;

import com.juicyslew.moonstation14.component.codec.json.EmoteRegistry;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.components.SpeechComponent;
import com.juicyslew.moonstation14.ms14.effect.EmoteEffect;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Player emotes routed through the authenticated committed-body speech authority. */
public final class EmoteCommand {
    private static final long COOLDOWN_NANOS = 2_000_000_000L;
    private static final Map<UUID, Long> LAST_EMOTE = new ConcurrentHashMap<>();
    private EmoteCommand() { }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("emote")
                .then(Commands.argument("id", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                                allowedEmotes(context.getSource().getPlayer()).stream().sorted(), builder))
                        .executes(context -> emit(context.getSource().getPlayerOrException(),
                                com.mojang.brigadier.arguments.StringArgumentType.getString(context, "id")))));
    }

    private static java.util.Set<String> allowedEmotes(ServerPlayer player) {
        var speaker = LocalSpeechServerHooks.authenticatedSpeaker(player);
        if (speaker == null || !(speaker.body() instanceof net.minecraft.world.entity.LivingEntity living)) return java.util.Set.of();
        return CharacterIdentitySystem.resolveForActor(living).flatMap(data -> data.component(SpeechComponent.class))
                .map(SpeechComponent::allowedEmotes).orElse(java.util.Set.of());
    }

    private static int emit(ServerPlayer player, String id) {
        var speaker = LocalSpeechServerHooks.authenticatedSpeaker(player);
        if (speaker == null || !(speaker.body() instanceof net.minecraft.world.entity.LivingEntity living)
                || !com.juicyslew.moonstation14.ms14.character.CharacterControlSystem.canAct(living)) return 0;
        var speech = CharacterIdentitySystem.resolveForActor(living).flatMap(data -> data.component(SpeechComponent.class)).orElse(null);
        if (speech == null || !speech.allowedEmotes().contains(id) || EmoteRegistry.find(id).isEmpty()) return 0;
        var plan = EmoteEffect.plan(id, true, living);
        if (plan == null) return 0;
        if (!commitCooldown(player.getUUID(), plan, System.nanoTime())) return 0;
        EmoteEffect.apply(id, true, living, (net.minecraft.server.level.ServerLevel) living.level(), EmoteEffect.SERVER);
        return 1;
    }

    static boolean commitCooldown(UUID account, EmoteEffect.Plan validatedPlan, long now) {
        if (account == null || validatedPlan == null) return false;
        Long last = LAST_EMOTE.putIfAbsent(account, now);
        if (last == null) return true;
        if (now - last < COOLDOWN_NANOS) return false;
        return LAST_EMOTE.replace(account, last, now);
    }
}
