package com.juicyslew.moonstation14.ms14.combat.client;

import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.client.ArticulatedHarnessModel;
import net.minecraft.world.entity.HumanoidArm;

/** Applies the analytic two-link solution to the native upper-arm and forearm parts. */
public final class ArticulatedArmPose {
    private ArticulatedArmPose() { }

    public record Pose(GunArmAimMath.Vec elbow, GunArmAimMath.Vec hand,
                       boolean reached, boolean floorContact) { }

    /** Coordinates and lengths are in model units (pixels divided by 16). */
    public static Pose solveAndApply(ArticulatedHarnessModel<?> model, HumanoidArm arm,
                                     GunArmAimMath.Vec target, GunArmAimMath.Vec nativeGripLocal,
                                     GunArmAimMath.Vec floorNormal, double floorHeight) {
        var upper = model.armPart(arm);
        var shoulder = new GunArmAimMath.Vec((upper.x + model.slimHandOffset(arm)) / 16.0, upper.y / 16.0, upper.z / 16.0);
        var a = new GunArmAimMath.Vec(0, model.upperLength(arm), 0);
        var gripFromElbow = nativeGripLocal.sub(new GunArmAimMath.Vec(0, .25, 0));
        double lowerLength = gripFromElbow.length();
        var solved = TwoBoneArmSolve.solve(shoulder, target, a.length(), lowerLength,
                new GunArmAimMath.Vec(arm == HumanoidArm.LEFT ? -1 : 1, 0, 0), floorNormal, floorHeight);

        setAim(upper, a, solved.elbow().sub(shoulder));
        var forearm = model.forearm(arm);
        var localLower = gripFromElbow;
        var desiredLower = inverseUpperDirection(upper, solved.hand().sub(solved.elbow()));
        var rotation = GunArmAimMath.align(localLower, new GunArmAimMath.Vec(0, 0, 1),
                desiredLower, new GunArmAimMath.Vec(0, 0, 1));
        forearm.setRotation(rotation.x(), rotation.y(), rotation.z());
        return new Pose(solved.elbow(), solved.hand(), solved.reached(), solved.floorContact());
    }

    private static GunArmAimMath.Vec inverseUpperDirection(net.minecraft.client.model.geom.ModelPart upper,
                                                              GunArmAimMath.Vec direction) {
        var inverse = new org.joml.Quaternionf().rotationZYX(upper.zRot, upper.yRot, upper.xRot).invert();
        var local = inverse.transform(new org.joml.Vector3f((float) direction.x(), (float) direction.y(), (float) direction.z()));
        return new GunArmAimMath.Vec(local.x, local.y, local.z);
    }

    private static void setAim(net.minecraft.client.model.geom.ModelPart part, GunArmAimMath.Vec local,
                               GunArmAimMath.Vec direction) {
        var r = GunArmAimMath.align(local, new GunArmAimMath.Vec(0, 0, 1), direction, new GunArmAimMath.Vec(0, 0, 1));
        part.setRotation(r.x(), r.y(), r.z());
    }
}
