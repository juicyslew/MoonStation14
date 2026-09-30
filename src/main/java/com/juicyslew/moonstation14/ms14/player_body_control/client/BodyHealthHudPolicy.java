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

    public record View(String label, boolean damageFlash) { }

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
                + (flashUntil > tick ? " - DAMAGE" : ""), flashUntil > tick);
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
