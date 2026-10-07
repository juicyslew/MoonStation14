package com.juicyslew.moonstation14.ms14.damage.client;

import com.juicyslew.moonstation14.ms14.damage.DamageFeedbackPayload;
import com.juicyslew.moonstation14.ms14.damage.DamageFeedbackProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.WeakHashMap;

/** Short-lived client cache scoped by live entity identity, never a persistent health flag. */
public final class DamageFeedbackClientState {
    private static final Map<LivingEntity, Flags> FLAGS = new WeakHashMap<>();
    private DamageFeedbackClientState() { }
    public static void receive(DamageFeedbackPayload payload) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        var entity = level.getEntity(payload.entityId());
        if (!(entity instanceof LivingEntity living) || !living.getUUID().equals(payload.entityUuid())) return;
        if (!payload.redFlash() && !payload.cameraShake()) return;
        synchronized (FLAGS) {
            FLAGS.put(living, new Flags(payload.redFlash(), payload.cameraShake(), level.getGameTime() + payload.duration()));
        }
        // The ordinary damage-event packet is deliberately omitted for partial profiles.
        // Set only presentation counters; vanilla health, mitigation and invulnerability are untouched.
        living.hurtTime = payload.duration();
        living.hurtDuration = payload.duration();
    }
    public static DamageFeedbackProfile profile(LivingEntity entity) {
        Flags flags = flags(entity);
        return flags == null ? DamageFeedbackProfile.PHYSICAL
                : new DamageFeedbackProfile(flags.redFlash, flags.cameraShake, false, false);
    }
    public static boolean feedbackActive(LivingEntity entity) { return flags(entity) != null; }
    private static Flags flags(LivingEntity entity) {
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
