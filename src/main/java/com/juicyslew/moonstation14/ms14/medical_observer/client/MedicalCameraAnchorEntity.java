package com.juicyslew.moonstation14.ms14.medical_observer.client;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Client-only camera proxy. It is never added to a level or assigned the observed body's identity. */
public final class MedicalCameraAnchorEntity extends Entity {
    MedicalCameraAnchorEntity(Level level) { super(EntityType.MARKER, level); }

    void follow(Entity body) {
        MedicalCameraFollowPolicy samples = new MedicalCameraFollowPolicy(
                body.getEyePosition(0.0F), body.getEyePosition(1.0F));
        Vec3 previous = samples.previous();
        Vec3 current = samples.current();
        xo = xOld = previous.x;
        yo = yOld = previous.y;
        zo = zOld = previous.z;
        setPos(current.x, current.y, current.z);
        // Keep the anchor's independently controlled look stable without collapsing position samples.
        yRotO = getYRot();
        xRotO = getXRot();
    }

    @Override public net.minecraft.world.entity.EntityDimensions getDimensions(Pose pose) { return getType().getDimensions(); }
    @Override public void tick() { }
    @Override protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) { }
    @Override protected void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) { }
    @Override protected void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) { }
}
