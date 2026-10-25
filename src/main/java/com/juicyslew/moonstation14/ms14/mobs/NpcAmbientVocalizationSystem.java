package com.juicyslew.moonstation14.ms14.mobs;

import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.components.AmbientVocalizationComponent;
import com.juicyslew.moonstation14.ms14.player_body_control.character.MindControlledMob;
import com.juicyslew.moonstation14.ms14.player_body_control.character.NpcMovementDriven;
import com.juicyslew.moonstation14.sounds.ModSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-authoritative spatial NPC ambience; never creates a chat/speech message. */
public final class NpcAmbientVocalizationSystem {
    private static final Map<UUID, Long> NEXT_VOCALIZATION = new HashMap<>();
    private NpcAmbientVocalizationSystem() { }

    public static void tick(LivingEntity entity, ServerLevel level) {
        if (!(entity instanceof Mob mob) || !(mob instanceof NpcMovementDriven driven)) return;
        if (!driven.moonstation14$isNpcMovementDriven()
                || mob instanceof MindControlledMob controlled && controlled.moonstation14$isMovementOwned()
                || !MobStateSystem.isAlive(mob) || mob.isRemoved()) {
            NEXT_VOCALIZATION.remove(mob.getUUID());
            return;
        }
        var resolved = CharacterIdentitySystem.resolveForActor(mob).orElse(null);
        var policy = resolved == null ? null : resolved.component(AmbientVocalizationComponent.class).orElse(null);
        if (policy == null) { NEXT_VOCALIZATION.remove(mob.getUUID()); return; }
        var sound = ModSounds.ambientSound(policy.sound());
        if (sound == null) {
            NEXT_VOCALIZATION.remove(mob.getUUID());
            return;
        }
        long now = level.getGameTime();
        Long due = NEXT_VOCALIZATION.get(mob.getUUID());
        if (due == null) { schedule(mob, level, policy, now); return; }
        if (now < due) return;
        level.playSound(null, mob, sound.get(), SoundSource.NEUTRAL, policy.volume(), 1f);
        schedule(mob, level, policy, now);
    }

    private static void schedule(Mob mob, ServerLevel level, AmbientVocalizationComponent policy, long now) {
        int span = policy.maximumIntervalTicks() - policy.minimumIntervalTicks() + 1;
        NEXT_VOCALIZATION.put(mob.getUUID(), now + policy.minimumIntervalTicks() + mob.getRandom().nextInt(span));
    }

    static Long scheduledTickForTest(UUID id) { return NEXT_VOCALIZATION.get(id); }

    public static void onLeaveLevel(LivingEntity entity) { NEXT_VOCALIZATION.remove(entity.getUUID()); }
    public static void onServerStopped(ServerStoppedEvent event) { NEXT_VOCALIZATION.clear(); }
}
