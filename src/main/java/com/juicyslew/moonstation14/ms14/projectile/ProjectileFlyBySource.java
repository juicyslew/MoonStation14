package com.juicyslew.moonstation14.ms14.projectile;

import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** Shared tracked projectile data required by local fly-by audio; contains no client API. */
public interface ProjectileFlyBySource {
    ProjectileFlyByProfile flyByProfile();
    UUID flyByShooterIdentity();
    Vec3 flyByPosition();
}
