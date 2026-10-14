package com.juicyslew.moonstation14.ms14.standing;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Mth;

/** Persisted server posture; medical MobState remains an independent concern. */
public record PostureData(boolean down, boolean voluntaryAutoStand, long knockdownUntil, long getUpKey,
                           int totalHands, int freeHands, boolean pulling, boolean faceUp, boolean ownCrawling,
                           float movementFacingYaw) {
    public static final PostureData STANDING = new PostureData(false, true, 0, 0, 0, 0, false, false, false, 0f);
    public PostureData(boolean down, boolean voluntaryAutoStand, long knockdownUntil, long getUpKey) {
        this(down, voluntaryAutoStand, knockdownUntil, getUpKey, 0, 0, false, false, false);
    }
    public static final Codec<PostureData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.fieldOf("down").forGetter(PostureData::down),
            Codec.BOOL.fieldOf("voluntary_auto_stand").forGetter(PostureData::voluntaryAutoStand),
            Codec.LONG.fieldOf("knockdown_until").forGetter(PostureData::knockdownUntil),
            Codec.LONG.fieldOf("get_up_key").forGetter(PostureData::getUpKey),
            Codec.intRange(0, 32).optionalFieldOf("total_hands", 0).forGetter(PostureData::totalHands),
            Codec.intRange(0, 32).optionalFieldOf("free_hands", 0).forGetter(PostureData::freeHands),
            Codec.BOOL.optionalFieldOf("pulling", false).forGetter(PostureData::pulling),
            Codec.BOOL.optionalFieldOf("face_up", false).forGetter(PostureData::faceUp),
            Codec.BOOL.optionalFieldOf("own_crawling", false).forGetter(PostureData::ownCrawling),
            Codec.floatRange(-180f, 180f).optionalFieldOf("movement_facing_yaw", 0f).forGetter(PostureData::movementFacingYaw)
    ).apply(i, PostureData::new));
    public PostureData { if (knockdownUntil < 0 || getUpKey < 0 || totalHands < 0 || totalHands > 32 || freeHands < 0 || freeHands > totalHands || !Float.isFinite(movementFacingYaw) || movementFacingYaw < -180f || movementFacingYaw > 180f) throw new IllegalArgumentException("invalid posture data"); }
    public PostureData(boolean down, boolean voluntaryAutoStand, long knockdownUntil, long getUpKey, int totalHands, int freeHands, boolean pulling, boolean faceUp, boolean ownCrawling) { this(down, voluntaryAutoStand, knockdownUntil, getUpKey, totalHands, freeHands, pulling, faceUp, ownCrawling, 0f); }
    public PostureData(boolean down, boolean voluntaryAutoStand, long knockdownUntil, long getUpKey, int totalHands, int freeHands, boolean pulling) {
        this(down, voluntaryAutoStand, knockdownUntil, getUpKey, totalHands, freeHands, pulling, false, false);
    }
    public PostureData(boolean down, boolean voluntaryAutoStand, long knockdownUntil, long getUpKey, int totalHands, int freeHands, boolean pulling, boolean faceUp) {
        this(down, voluntaryAutoStand, knockdownUntil, getUpKey, totalHands, freeHands, pulling, faceUp, false);
    }
    public PostureData movement(int total, int free, boolean isPulling) { return new PostureData(down, voluntaryAutoStand, knockdownUntil, getUpKey, total, free, isPulling, faceUp, ownCrawling, movementFacingYaw); }
    public PostureData crawling(boolean value) { return new PostureData(down, voluntaryAutoStand, knockdownUntil, getUpKey, totalHands, freeHands, pulling, faceUp, down && value, movementFacingYaw); }
    public PostureData facing(float yaw) {
        if (!Float.isFinite(yaw)) throw new IllegalArgumentException("invalid posture facing yaw");
        return new PostureData(down, voluntaryAutoStand, knockdownUntil, getUpKey, totalHands, freeHands, pulling, faceUp, ownCrawling, Mth.wrapDegrees(yaw));
    }
    public static float yawForWorldMovement(double worldX, double worldZ) {
        if (!Double.isFinite(worldX) || !Double.isFinite(worldZ) || worldX * worldX + worldZ * worldZ < 1.0e-8)
            throw new IllegalArgumentException("movement direction must be finite and nonzero");
        return (float)Math.toDegrees(Math.atan2(-worldX, worldZ));
    }
    public PostureData forceFaceUp(long until) { return new PostureData(true, true, Math.max(knockdownUntil, until), 0, totalHands, freeHands, pulling, true, false, movementFacingYaw); }
    public PostureData voluntaryRoll() { return down ? new PostureData(true, voluntaryAutoStand, knockdownUntil, getUpKey, totalHands, freeHands, pulling, !faceUp, false, movementFacingYaw) : this; }
    public PostureData voluntaryToggle(long now, long graceTicks) {
        if (!down) return new PostureData(true, false, Math.addExact(now, graceTicks), 0, totalHands, freeHands, pulling, false, false, movementFacingYaw);
        return this;
    }
    public PostureData forceDown(long until) { return forceDown(until, DownTransition.KNOCKDOWN); }
    public enum DownTransition { KNOCKDOWN, MEDICAL }
    public PostureData forceDown(long until, DownTransition transition) {
        boolean orientation = down ? faceUp : transition == DownTransition.MEDICAL;
        return new PostureData(true, true, Math.max(knockdownUntil, until), 0, totalHands, freeHands,
                pulling, orientation, false, movementFacingYaw);
    }
    /** Damage refreshes the knockdown deadline without changing voluntary/forced stand policy. */
    public PostureData refreshKnockdown(long until) {
        return down ? new PostureData(true, voluntaryAutoStand, Math.max(knockdownUntil, until), getUpKey,
                totalHands, freeHands, pulling, faceUp, false, movementFacingYaw) : this;
    }
    public PostureData beginGetUp(long key) { return down && key > 0 ? new PostureData(true, voluntaryAutoStand, knockdownUntil, key, totalHands, freeHands, pulling, faceUp, false, movementFacingYaw) : this; }
    public PostureData completeGetUp(boolean fits) { return fits ? STANDING.movement(totalHands, freeHands, pulling).facing(movementFacingYaw) : new PostureData(true, voluntaryAutoStand, knockdownUntil, 0, totalHands, freeHands, pulling, faceUp, false, movementFacingYaw); }
    public boolean mayStand(long now, boolean canMove, boolean conscious) { return down && canMove && conscious && now >= knockdownUntil; }
    public static double crawlSpeed(boolean down, double crawlerSpeed, int freeHands, int totalHands) {
        if (!down) return 1d;
        if (totalHands <= 0) return 0d;
        return crawlerSpeed * Math.max(0, Math.min(freeHands, totalHands)) / totalHands;
    }
    public static double standSeconds(double baseSeconds, int freeHands, int totalHands) {
        if (totalHands <= 0 || freeHands <= 0) return baseSeconds;
        int free = Math.min(freeHands, totalHands);
        return baseSeconds * totalHands / (free + totalHands);
    }
}

