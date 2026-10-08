package com.juicyslew.moonstation14.ms14.medical.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.LivingEntity;

/** Cached detached, non-ticking snapshot used for a static analyzer portrait. */
final class NeutralPortrait {
    private static int cachedEntityId = Integer.MIN_VALUE;
    private static java.util.UUID cachedUuid;
    private static net.minecraft.world.level.Level cachedLevel;
    private static LivingEntity cached;

    private NeutralPortrait() { }

    static boolean render(GuiGraphics graphics, LivingEntity source, int x, int y, int size) {
        if (cachedLevel != source.level()) {
            cached = null; cachedEntityId = Integer.MIN_VALUE; cachedUuid = null; cachedLevel = source.level();
        }
        if (cached == null || source.getId() != cachedEntityId || !source.getUUID().equals(cachedUuid)
                || !com.juicyslew.moonstation14.ms14.medical.MedicalPortraitSnapshot.sameAppearance(source, cached)) {
            cached = detachedCopy(source);
            cachedEntityId = source.getId();
            cachedUuid = source.getUUID();
        }
        if (cached == null) return false;
        cached.setYRot(180.0F);
        cached.setYBodyRot(180.0F);
        cached.setYHeadRot(180.0F);
        cached.setXRot(0.0F);
        cached.yRotO = cached.yBodyRotO = cached.yHeadRotO = 180.0F;
        cached.xRotO = 0.0F;
        cached.tickCount = 0;
        float scale = Math.min(size * 0.82f / Math.max(cached.getBbHeight(), 0.1f),
                size * 0.72f / Math.max(cached.getBbWidth(), 0.1f));
        net.minecraft.client.gui.screens.inventory.InventoryScreen.renderEntityInInventory(graphics,
                x + size / 2, y + size - 1, scale, new org.joml.Vector3f(0, 0, 0),
                uprightPose(), new org.joml.Quaternionf(), cached);
        return true;
    }

    /** InventoryScreen's direct overload does not apply the Z(pi) flip used by its higher-level helper. */
    static org.joml.Quaternionf uprightPose() {
        return new org.joml.Quaternionf().rotationZ((float) Math.PI);
    }

    private static LivingEntity detachedCopy(LivingEntity source) {
        return com.juicyslew.moonstation14.ms14.medical.MedicalPortraitSnapshot.create(source);
    }
}
