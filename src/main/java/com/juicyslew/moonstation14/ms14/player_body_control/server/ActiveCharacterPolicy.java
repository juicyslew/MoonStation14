package com.juicyslew.moonstation14.ms14.player_body_control.server;

import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

import java.util.Optional;

/** Server-side character-profile routing for active experimental mind sessions. */
public final class ActiveCharacterPolicy {
    private ActiveCharacterPolicy() { }

    /** True for the exact connected player object participating in an experimental session. */
    public static boolean isCarrier(Entity entity) {
        return entity instanceof ServerPlayer player && GhostMobHarnessControl.isExperimentalCarrier(player);
    }

    /** Active carriers do not act as character-profile actors; ordinary entities keep legacy resolution. */
    public static Optional<CharacterData> resolveActor(Entity entity) {
        if (isCarrier(entity)) return Optional.empty();
        return CharacterIdentitySystem.resolve(entity);
    }

    /** Returns only an active character body; ghost sessions and pending handoffs have no body result. */
    public static Optional<Mob> activeCharacterBody(ServerPlayer player) {
        return GhostMobHarnessControl.activeCharacterBody(player);
    }
}
