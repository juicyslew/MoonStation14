package com.juicyslew.moonstation14.ms14.shuttle.sable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Bounded immutable selected-contact history for one native collision result. */
public final class NativeSelectedContactJournal {
    public static final int CAPACITY = 8;
    private final List<NativeSelectedContactAccess.Entry> entries = new ArrayList<>(CAPACITY);
    private boolean overflowed;

    public synchronized void record(UUID frameId, BlockPos pos, Vec3 normal, boolean verticalCollision,
                                    Vec3 entityPosition, Vec3 entityVelocity, long serverTick, long levelGameTime, boolean moveInFlight) {
        if (frameId == null || pos == null || normal == null || !Double.isFinite(normal.x)
                || !Double.isFinite(normal.y) || !Double.isFinite(normal.z)) return;
        double length = normal.length();
        if (!Double.isFinite(length) || length <= 1.0e-12) return;
        Vec3 unit = normal.scale(1.0 / length);
        BlockPos immutablePos = pos.immutable();
        for (var entry : entries) {
            if (entry.frameId().equals(frameId) && entry.pos().equals(immutablePos)
                    && entry.normal().dot(unit) >= .999) return;
        }
        if (entries.size() == CAPACITY) { overflowed = true; return; }
        entries.add(new NativeSelectedContactAccess.Entry(frameId, immutablePos,
                new Vec3(unit.x, unit.y, unit.z), verticalCollision, entityPosition, entityVelocity, serverTick, levelGameTime, moveInFlight));
    }

    public synchronized List<NativeSelectedContactAccess.Entry> snapshot() { return List.copyOf(entries); }
    public synchronized boolean overflowed() { return overflowed; }
}
