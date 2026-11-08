package com.juicyslew.moonstation14.ms14.combat.client;

import com.juicyslew.moonstation14.ms14.combat.MeleeArcProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import com.mojang.math.Axis;

/** Rotation for a captured attack's local +Z plane, independent of the actor's later pose. */
final class CapturedAttackFramePose {
    private CapturedAttackFramePose() { }

    static Quaternionf rotation(Vec3 direction, float frozenYaw, boolean swingLeft) {
        Vec3 normalized = direction == null || direction.lengthSqr() < 1.0e-12
                ? new Vec3(-Math.sin(Math.toRadians(frozenYaw)), 0, Math.cos(Math.toRadians(frozenYaw)))
                : direction.normalize();
        Quaternionf align = new Quaternionf().rotationTo(new Vector3f(0, 0, 1),
                new Vector3f((float) normalized.x, (float) normalized.y, (float) normalized.z));
        return align.mul(Axis.ZP.rotation((float) Math.toRadians(swingLeft ? 35 : -35)));
    }

    static void apply(PoseStack pose, Vec3 direction, float frozenYaw, boolean swingLeft) {
        pose.mulPose(rotation(direction, frozenYaw, swingLeft));
    }

    /** Orients a held icon's configured in-plane blade axis toward captured aim, with a small in-frame pitch. */
    static Quaternionf weaponRotation(Vec3 centralDirection, Vec3 direction, float frozenYaw,
                                      MeleeArcProfile.WeaponPose weaponPose,
                                      com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest.Mode mode,
                                      float wideAnimationRotation) {
        var basis = com.juicyslew.moonstation14.ms14.standing.BodyInteractionRay.headLocalBasis(centralDirection, frozenYaw);
        Vec3 forward = direction == null || direction.lengthSqr() < 1.0e-12 ? centralDirection.normalize() : direction.normalize();
        Vec3 up = basis == null ? new Vec3(0, 1, 0) : basis.up();
        Vec3 right = up.cross(forward).normalize();
        up = forward.cross(right).normalize();
        org.joml.Matrix3f axes = new org.joml.Matrix3f();
        axes.setColumn(0, (float)right.x, (float)right.y, (float)right.z);
        axes.setColumn(1, (float)up.x, (float)up.y, (float)up.z);
        axes.setColumn(2, (float)forward.x, (float)forward.y, (float)forward.z);
        Quaternionf frame = new Quaternionf().setFromUnnormalized(axes);
        float forwardAngle = (float) Math.toRadians(weaponPose.forwardDegrees());
        float oblique = (float) Math.toRadians(weaponPose.obliqueDegrees());
        Vector3f iconAxis = new Vector3f((float)Math.cos(forwardAngle), (float)Math.sin(forwardAngle), 0);
        Quaternionf retarget = new Quaternionf().rotationTo(iconAxis, new Vector3f(0, 0, 1));
        Quaternionf obliqueRotation = Axis.XP.rotationDegrees((float) -weaponPose.obliqueDegrees());
        Quaternionf bank = mode == com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest.Mode.WIDE
                ? Axis.ZP.rotationDegrees(wideAnimationRotation) : new Quaternionf();
        return frame.mul(bank).mul(obliqueRotation).mul(retarget);
    }

    /** Finds the model point furthest along captured aim, so that point contacts the strike tip. */
    static Vec3 weaponAnchor(net.minecraft.client.resources.model.BakedModel model,
                             com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile profile,
                              float appearance, Quaternionf rotation, Vec3 direction, Vec3 tip) {
        return weaponAnchor(model, profile, appearance, rotation, direction, tip, 1.0f);
    }

    static org.joml.Matrix4f rigidWeaponTransform(Quaternionf rotation, float scale) {
        return new org.joml.Matrix4f().rotate(rotation).scale(scale);
    }

    static Vec3 weaponAnchor(net.minecraft.client.resources.model.BakedModel model,
                             com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile profile,
                             float appearance, Quaternionf rotation, Vec3 direction, Vec3 tip, float scale) {
        if (direction == null || direction.lengthSqr() < 1.0e-12) return null;
        PoseStack local = new PoseStack();
        local.mulPose(rotation);
        local.scale(scale, scale, scale);
        var extent = com.juicyslew.moonstation14.ms14.loose_objects.client.LoosePresentationBounds.flatSpriteProjection(
                model, profile, appearance, local.last().pose(),
                new Vector3f((float) direction.x, (float) direction.y, (float) direction.z));
        return extent == null ? null : tip.subtract(extent.maxPoint());
    }

    record ConstrainedWeapon(Vec3 anchor, float axialScale) { }
    /** World-axis compression shared by support measurement and the item render pose. */
    static org.joml.Matrix4f axialTransform(Vec3 direction, float scale, Quaternionf rotation, float weaponScale) {
        Vec3 d=direction.normalize(); float x=(float)d.x,y=(float)d.y,z=(float)d.z,k=scale-1;
        var world=new org.joml.Matrix4f();
        world.m00(1+k*x*x).m01(k*x*y).m02(k*x*z);
        world.m10(k*y*x).m11(1+k*y*y).m12(k*y*z);
        world.m20(k*z*x).m21(k*z*y).m22(1+k*z*z);
        return world.mul(new org.joml.Matrix4f().rotate(rotation).scale(weaponScale));
    }

    /** Fit actual transformed support between captured rear-clearance plane and the bounded tip. */
    static ConstrainedWeapon constrainedWeapon(net.minecraft.client.resources.model.BakedModel model,
            com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile profile, float appearance,
            Quaternionf rotation, Vec3 direction, Vec3 tip, float scale, double clearance) {
        if(direction==null || direction.lengthSqr()<1e-12 || !Double.isFinite(clearance)) return null;
        Vec3 d=direction.normalize();
        PoseStack p=new PoseStack(); p.mulPose(rotation); p.scale(scale,scale,scale);
        var extent=com.juicyslew.moonstation14.ms14.loose_objects.client.LoosePresentationBounds.flatSpriteProjection(
                model,profile,appearance,p.last().pose(),new Vector3f((float)d.x,(float)d.y,(float)d.z));
        if(extent==null) return null;
        double tipProjection=tip.dot(d), available=tipProjection-clearance;
        double length=extent.max()-extent.min();
        if(available<=0 || length<=0) return null;
        float axial=(float)Math.min(1,available/length);
        // The support range is scaled about the model's transformed origin; recompute the actual leading vertex
        // after this same world-axis transform before anchoring it at the captured tip.
        var m=axialTransform(d,axial,rotation,scale);
        var transformed=com.juicyslew.moonstation14.ms14.loose_objects.client.LoosePresentationBounds.flatSpriteProjection(model,profile,appearance,m,
                new Vector3f((float)d.x,(float)d.y,(float)d.z));
        if(transformed==null || transformed.maxPoint()==null) return null;
        return new ConstrainedWeapon(tip.subtract(transformed.maxPoint()),axial);
    }

    static double ownerClearance(double bodyClearance, double physicalAvailable, Vec3 source, Vec3 camera,
                                 Vec3 strikeDirection, Vec3 cameraForward, double nearDepth) {
        Vec3 d=strikeDirection.normalize(), f=cameraForward.normalize();
        double alignment=d.dot(f);
        if (alignment<=1e-6) return Double.NaN;
        double t=(nearDepth-source.subtract(camera).dot(f))/alignment;
        double viewClearance=source.dot(d)+t;
        return physicalAvailable<=.2 ? viewClearance : Math.max(bodyClearance,viewClearance);
    }
}
