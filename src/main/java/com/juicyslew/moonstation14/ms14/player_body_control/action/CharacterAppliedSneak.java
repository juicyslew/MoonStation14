package com.juicyslew.moonstation14.ms14.player_body_control.action;

import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.LifecycleCharacterSessionControl;
import com.juicyslew.moonstation14.ms14.player_body_control.server.GhostMobHarnessControl;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

/** Read-only server evidence that Shift was present in the latest successfully applied CHARACTER intent. */
public final class CharacterAppliedSneak {
    private CharacterAppliedSneak() { }

    /** Requires the exact carrier, body and committed epoch; stale or absent applied intents return false. */
    public static boolean isApplied(ServerPlayer actor, Mob body, long committedEpoch) {
        if (actor == null || body == null || committedEpoch <= 0) return false;
        if (body instanceof PlayerCharacterHarnessEntity lifecycleBody
                && LifecycleCharacterSessionControl.hasAppliedSneak(actor, lifecycleBody, committedEpoch)) return true;
        return GhostMobHarnessControl.hasAppliedSneak(actor, body, committedEpoch);
    }

    /** Applied movement is committed in PlayerTickEvent.Post; block-use can arrive one frame earlier.
     * Allow one tick of scheduling skew only (with the corresponding one-tick released-Shift risk).
     */
    public static boolean freshAppliedSneak(boolean appliedSneak, long appliedTick, long currentTick) {
        long age = currentTick - appliedTick;
        return appliedSneak && age >= 0 && age <= 1;
    }
}

