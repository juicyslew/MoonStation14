package com.juicyslew.moonstation14.ms14.tesla;

import net.minecraft.world.level.block.entity.BlockEntity;

/** Persisted common structural integrity value for explicitly profiled machinery. */
public final class MachineryIntegrity {
    private static final String KEY = "moonstation14:machinery_structural_damage";
    private MachineryIntegrity() { }
    public static int nextDamage(int current, double amount) {
        if (!Double.isFinite(amount) || amount <= 0) return Math.max(0, current);
        return Math.addExact(Math.max(0, current), (int)Math.ceil(amount));
    }
    public static int damage(BlockEntity entity) {
        return entity == null ? 0 : Math.max(0, entity.getPersistentData().getInt(KEY));
    }
    public static int add(BlockEntity entity, int amount) {
        if (entity == null || amount <= 0) return damage(entity);
        int next = nextDamage(damage(entity), amount);
        entity.getPersistentData().putInt(KEY, next); entity.setChanged();
        return next;
    }
    public static void clear(BlockEntity entity) {
        if (entity != null) { entity.getPersistentData().remove(KEY); entity.setChanged(); }
    }
}
