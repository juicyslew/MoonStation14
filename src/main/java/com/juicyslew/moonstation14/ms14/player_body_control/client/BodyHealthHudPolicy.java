package com.juicyslew.moonstation14.ms14.player_body_control.client;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.UUID;

/** Read-only presentation of observed client body health; never estimates damage or packet arrival. */
public final class BodyHealthHudPolicy {
    private static final long FLASH_TICKS = 12;

    /** The caller must supply the exact committed camera body, session epoch, carrier and level. */
    public record Key(UUID carrier, long epoch, Object level, UUID body, int entityId) {
        public Key {
            Objects.requireNonNull(carrier);
            Objects.requireNonNull(level);
            Objects.requireNonNull(body);
            if (epoch <= 0) throw new IllegalArgumentException("Uncommitted body session");
        }
    }

    public record View(String label, String debugLabel, boolean damageFlash) { }

    /** Clinical display uses typed damage and resolved state thresholds, not native projection health. */
    public static View medical(float damage, float criticalThreshold, float deadThreshold) {
        return medical(damage, criticalThreshold, deadThreshold, null);
    }

    /** Observed state wins over damage inference (notably, DEAD persists through healing). */
    public static View medical(float damage, float criticalThreshold, float deadThreshold,
                               com.juicyslew.moonstation14.ms14.character.components.MobStateComponent.State state) {
        if (!Float.isFinite(damage) || !Float.isFinite(deadThreshold) || deadThreshold <= 0
                || (!Float.isFinite(criticalThreshold) && !Float.isNaN(criticalThreshold))
                || (Float.isFinite(criticalThreshold) && (criticalThreshold <= 0 || deadThreshold <= criticalThreshold))) return null;
        boolean stateAuthoritative = state != null;
        if (state == com.juicyslew.moonstation14.ms14.character.components.MobStateComponent.State.DEAD)
            return new View("BODY HEALTH DEAD", null, false);
        float value = Math.max(0, damage);
        if (state == com.juicyslew.moonstation14.ms14.character.components.MobStateComponent.State.CRITICAL)
            return criticalDebug(value, deadThreshold);
        // With a synchronized state, thresholds describe transitions, not state authority:
        // rejected transitions must not be rendered as if they had occurred.
        if (!stateAuthoritative && value >= deadThreshold) return new View("BODY HEALTH DEAD", null, false);
        // Species without a critical threshold still get a meaningful 0–100 healthy bar up to death.
        if (!Float.isFinite(criticalThreshold)) criticalThreshold = deadThreshold;
        if (!stateAuthoritative && value >= criticalThreshold) return criticalDebug(value, deadThreshold);
        float percent = (criticalThreshold - value) * (100f / criticalThreshold);
        percent = Math.max(0f, Math.min(100f, percent));
        return new View("BODY HEALTH " + number(percent, 100f) + "/100", null, false);
    }

    private static View criticalDebug(float damage, float deathThreshold) {
        return new View("BODY HEALTH CRITICAL 0/100", "DEBUG Critical " + number(damage, deathThreshold)
                + "/" + number(deathThreshold, deathThreshold) + " damage · "
                + number(Math.max(0, deathThreshold - damage), deathThreshold) + " to death", false);
    }

    private Key key;
    private float previousHealth;
    private long flashUntil;
    private long previousTick;

    public void reset() {
        key = null;
        flashUntil = 0;
    }

    /** Null means the current client observation cannot be displayed; it is not a damage event. */
    public View observe(Key observedKey, float health, float maximum, long tick) {
        if (observedKey == null || !Float.isFinite(health) || !Float.isFinite(maximum) || maximum <= 0f) {
            reset();
            return null;
        }
        float cappedMax = Math.min(maximum, 1_000_000f);
        float displayedHealth = Math.max(0f, Math.min(cappedMax, health));
        if (!observedKey.equals(key) || tick < previousTick) {
            key = observedKey;
            flashUntil = 0;
        } else if (displayedHealth < previousHealth) {
            flashUntil = tick + FLASH_TICKS;
        }
        previousHealth = displayedHealth;
        previousTick = tick;
        return new View("BODY HEALTH " + number(displayedHealth, cappedMax) + "/" + number(cappedMax, cappedMax)
                + (flashUntil > tick ? " - DAMAGE" : ""), null, flashUntil > tick);
    }

    private static String number(float value, float maximum) {
        BigDecimal precise = new BigDecimal(Float.toString(value));
        BigDecimal tenths = precise.setScale(1, RoundingMode.HALF_UP);
        // Do not disguise a small loss as full health, or positive health as zero.
        if ((value > 0f && tenths.signum() == 0)
                || (value < maximum && tenths.floatValue() >= maximum))
            return precise.stripTrailingZeros().toPlainString();
        return tenths.toPlainString();
    }
}
