package com.juicyslew.moonstation14.ms14.character;

import com.juicyslew.moonstation14.ms14.character.components.AngularRecoveryComponent;
import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.server.level.ServerLevel;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import org.joml.Vector3d;

import java.util.Optional;

/** Authoritative transient angular state consumer for prototype-enabled CHARACTER bodies. */
public final class CharacterAngularRecoverySystem {
    private static final AngularRecoveryModel.Vec3 UP = new AngularRecoveryModel.Vec3(0, 1, 0);
    private static final double STEP_SECONDS = com.juicyslew.moonstation14.ms14.player_body_control.movement.GroundedHarnessPhysics.TICK_SECONDS;
    private CharacterAngularRecoverySystem() { }

    public static Optional<AngularRecoveryModel.State> state(Mob body) {
        return body instanceof PlayerCharacterHarnessEntity character ? character.authoritativeAngularRecoveryState() : Optional.empty();
    }

    /** Reset guard for owners that supersede ordinary character physics; never advances state. */
    public static boolean clearIfSuperseded(PlayerCharacterHarnessEntity body) {
        if (body.level().isClientSide) return false;
        boolean contained = body.level() instanceof ServerLevel level
                && com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.forLevel(level)
                        .ownerOf(body).isPresent();
        boolean alive = body.isAlive() && com.juicyslew.moonstation14.ms14.mobs.MobStateSystem.isAlive(body);
        if (!shouldClearSupersededState(alive, contained)) return false;
        body.setAngularRecoveryState(Optional.empty());
        return true;
    }

    static boolean shouldClearSupersededState(boolean alive, boolean contained) {
        return !alive || contained;
    }

    /** Sample only a live frame with exact current actor point and matching native floor witness. */
    public static SupportSample beforeWorldMove(Mob body) {
        AngularRecoveryModel.Quat orientation = body instanceof PlayerCharacterHarnessEntity character
                ? character.authoritativeAngularRecoveryState().map(AngularRecoveryModel.State::orientation)
                    .orElse(new AngularRecoveryModel.Quat(1, 0, 0, 0))
                : new AngularRecoveryModel.Quat(1, 0, 0, 0);
        AngularRecoveryModel.Vec3 omega = new AngularRecoveryModel.Vec3(0, 0, 0);
        if (!body.onGround() || !(body.level() instanceof ServerLevel level)
                || !(body instanceof EntityMovementExtension movement))
            return new SupportSample(orientation, omega, null, 0f);
        var point = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.currentActorPoint(level, body);
        var info = movement.sable$getCollisionInfo();
        if (point == null || point.frameId() == null || info == null || !info.verticalCollision
                || !info.verticalCollisionBelow || info.trackingSubLevel == null
                || !point.frameId().equals(info.trackingSubLevel.getUniqueId()))
            return new SupportSample(orientation, omega, null, 0f);
        var frame = info.trackingSubLevel;
        if (!(frame instanceof ServerSubLevel serverFrame) || frame.isRemoved() || frame.getLevel() != level || frame.getPlot() == null
                || SubLevelContainer.getContainer(level).getSubLevel(frame.getUniqueId()) != frame)
            return new SupportSample(orientation, omega, null, 0f);
        try {
            Vector3d sampled = RigidBodyHandle.of(serverFrame).getAngularVelocity(new Vector3d());
            if (sampled != null && Double.isFinite(sampled.x) && Double.isFinite(sampled.y)
                    && Double.isFinite(sampled.z)) omega = AngularRecoveryModel.Vec3.of(sampled);
        } catch (RuntimeException ignored) { /* Unsupported/non-rigid support has zero inherited spin. */ }
        var pose = serverFrame.logicalPose();
        var frameRotation = new org.joml.Quaterniond(pose.orientation()).normalize();
        float frameYaw = com.juicyslew.moonstation14.ms14.standing.WorldUpTwist.degrees(frameRotation);
        return new SupportSample(orientation, omega, frame.getUniqueId(), frameYaw);
    }

    public record SupportSample(AngularRecoveryModel.Quat orientation,
                                AngularRecoveryModel.Vec3 worldAngularVelocity,
                                java.util.UUID actualFrameId, float worldFrameYaw) {
        public boolean supportedFrame() { return actualFrameId != null; }
    }

    /** Advance only after an owned physical move; absent policy explicitly opts out. */
    public static void afterWorldMove(Mob body, CharacterData data, boolean wasGrounded, boolean grounded,
                                      SupportSample support) {
        if (!(body instanceof PlayerCharacterHarnessEntity character)) return;
        AngularRecoveryComponent policy = data.angularRecoveryPolicy().orElse(null);
        if (policy == null) { character.setAngularRecoveryState(Optional.empty()); return; }
        AngularRecoveryModel.State current = character.authoritativeAngularRecoveryState().orElse(null);
        var result = AngularRecoveryModel.afterMove(current, wasGrounded, grounded,
                support.orientation(), support.worldAngularVelocity(), UP, STEP_SECONDS,
                policy.graceSeconds(), policy.recoverySeconds());
        if (!wasGrounded && grounded && result.impactState() != null) {
            AngularRecoveryModel.State impact = result.impactState();
            AngularRecoveryModel.Landing landing = AngularRecoveryModel.landing(impact.orientation(),
                    impact.worldAngularVelocity(), UP, policy.badLandingTiltDegrees(), policy.badLandingAngularSpeed());
            if (landing.bad() && data.component(com.juicyslew.moonstation14.ms14.character.components.StunnableComponent.class).isPresent())
                CharacterControlSystem.applyParalyze(body, (float) policy.knockdownSeconds());
        }
        character.setAngularRecoveryState(Optional.ofNullable(result.nextState()));
    }
}
