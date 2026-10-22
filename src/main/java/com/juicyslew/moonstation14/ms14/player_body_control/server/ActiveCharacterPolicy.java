package com.juicyslew.moonstation14.ms14.player_body_control.server;

import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.util.Optional;

/** Server-side character-profile routing for active experimental mind sessions. */
public final class ActiveCharacterPolicy {
    private ActiveCharacterPolicy() { }

    /** True for the exact connected player object participating in an experimental session. */
    public static boolean isCarrier(Entity entity) {
        return entity instanceof ServerPlayer player && (GhostMobHarnessControl.isExperimentalCarrier(player)
                || CommittedSpectatorGuard.isTransportCarrier(player));
    }

    /** Active carriers do not act as character-profile actors; explicit bodies require live bindings. */
    public static Optional<CharacterData> resolveActor(Entity entity) {
        if (isCarrier(entity)) return Optional.empty();
        return entity instanceof LivingEntity living ? CharacterIdentitySystem.resolveForActor(living) : Optional.empty();
    }

    /** Returns only an active character body; ghost sessions and pending handoffs have no body result. */
    public static Optional<Mob> activeCharacterBody(ServerPlayer player) {
        return GhostMobHarnessControl.activeCharacterBody(player);
    }

    /** True only for a native player or the exact body of that player's authenticated active session. */
    public static boolean isPlayerCharacterBody(LivingEntity body, net.minecraft.server.level.ServerLevel level) {
        if (body instanceof ServerPlayer player) return !isCarrier(player);
        if (!(body instanceof Mob mob)) return false;
        return level.getServer().getPlayerList().getPlayers().stream()
                .anyMatch(player -> authenticatedActiveCharacterBody(player).filter(mob::equals).isPresent());
    }

    /**
     * Core classification must recognize both character-session owners. Prefer the committed lifecycle
     * authority; retain the legacy experimental owner as a separate, authenticated route.
     */
    private static Optional<Mob> authenticatedActiveCharacterBody(ServerPlayer player) {
        var committed = com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server
                .LifecycleCharacterSessionControl.activeCharacterBody(player);
        return committed.map(com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server
                .LifecycleCharacterSessionControl.ActiveCharacterBody::body)
                .or(() -> activeCharacterBody(player));
    }
}
