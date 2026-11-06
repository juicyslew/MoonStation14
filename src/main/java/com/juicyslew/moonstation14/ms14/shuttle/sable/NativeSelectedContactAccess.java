package com.juicyslew.moonstation14.ms14.shuttle.sable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.UUID;

/** Selected native SAT blockers attached to one exact CollisionInfo instance. */
public interface NativeSelectedContactAccess {
    record Entry(UUID frameId, BlockPos pos, Vec3 normal, boolean verticalCollision,
                 Vec3 entityPosition, Vec3 entityVelocity, long serverTick, long levelGameTime, boolean moveInFlight) {
        public Entry {
            java.util.Objects.requireNonNull(frameId);
            pos = java.util.Objects.requireNonNull(pos).immutable();
            java.util.Objects.requireNonNull(normal);
            entityPosition = copy(entityPosition);
            entityVelocity = copy(entityVelocity);
        }
        private static Vec3 copy(Vec3 v) { return v == null ? null : new Vec3(v.x, v.y, v.z); }
        @Override public Vec3 entityPosition() { return copy(entityPosition); }
        @Override public Vec3 entityVelocity() { return copy(entityVelocity); }
    }

    List<Entry> moonstation14$selectedContacts();
    boolean moonstation14$selectedContactsOverflowed();
    void moonstation14$recordSelectedContact(UUID frameId, BlockPos pos, Vec3 normal, boolean verticalCollision,
                                              Vec3 entityPosition, Vec3 entityVelocity, long serverTick, long levelGameTime, boolean moveInFlight);
}
