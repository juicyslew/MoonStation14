package com.juicyslew.moonstation14.ms14.damage.client;

import com.juicyslew.moonstation14.ms14.damage.DamageFeedbackPayload;
import com.juicyslew.moonstation14.ms14.damage.DamageFeedbackProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import com.juicyslew.moonstation14.entities.LooseItemEntity;

import java.util.Map;
import java.util.WeakHashMap;

/** Short-lived client cache scoped by live entity identity, never a persistent health flag. */
public final class DamageFeedbackClientState {
    private static final Map<Entity, Flags> FLAGS = new WeakHashMap<>();
    private DamageFeedbackClientState() { }
    public static void receive(DamageFeedbackPayload payload) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        var entity = level.getEntity(payload.entityId());
        if (!acceptsTarget(entity, payload.entityId(), payload.entityUuid(),
                entity instanceof LivingEntity || entity instanceof LooseItemEntity)) return;
        if (!payload.redFlash() && !payload.cameraShake()) return;
        synchronized (FLAGS) {
            FLAGS.put(entity, new Flags(payload.redFlash(), payload.cameraShake(), level.getGameTime() + payload.duration()));
        }
        // The ordinary damage-event packet is deliberately omitted for partial profiles.
        // Set only presentation counters; vanilla health, mitigation and invulnerability are untouched.
        if (entity instanceof LivingEntity living) {
            living.hurtTime = payload.duration();
            living.hurtDuration = payload.duration();
        }
    }
    public static DamageFeedbackProfile profile(Entity entity) {
        Flags flags = flags(entity);
        return flags == null ? DamageFeedbackProfile.PHYSICAL
                : new DamageFeedbackProfile(flags.redFlash, flags.cameraShake, false, false);
    }
    public static boolean feedbackActive(Entity entity) { return flags(entity) != null; }
    public static float redFlashProgress(Entity entity) {
        Flags f = flags(entity);
        if (f == null || !f.redFlash) return 0f;
        var level = Minecraft.getInstance().level;
        if (level == null) return 0f;
        return Math.max(0f, Math.min(1f, (f.expiresAt - level.getGameTime()) / 6f));
    }
    /** Pure identity/duration policy shared by receive and focused regression tests. */
    public static boolean acceptsTarget(Entity current, int packetId, java.util.UUID packetUuid, boolean supported) {
        return supported && current != null && !current.isRemoved() && current.getId() == packetId
                && current.getUUID().equals(packetUuid);
    }
    private static Flags flags(Entity entity) {
        synchronized (FLAGS) {
            Flags flags = FLAGS.get(entity);
            var level = Minecraft.getInstance().level;
            if (flags != null && (level == null || level.getGameTime() > flags.expiresAt)) {
                FLAGS.remove(entity);
                return null;
            }
            return flags;
        }
    }
    private record Flags(boolean redFlash, boolean cameraShake, long expiresAt) { }
}
