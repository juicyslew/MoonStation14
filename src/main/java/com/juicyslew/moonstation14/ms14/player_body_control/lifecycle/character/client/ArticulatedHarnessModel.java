package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.HumanoidArm;
import java.util.EnumSet;
import java.util.HashMap;

/** Articulated arms reserved for the persistent controlled character renderer. */
public final class ArticulatedHarnessModel<T extends net.minecraft.world.entity.LivingEntity> extends PlayerModel<T> {
    private static final EnumSet<Direction> SIDES = EnumSet.of(Direction.NORTH, Direction.SOUTH,
            Direction.EAST, Direction.WEST);
    private final ModelPart leftForearm;
    private final ModelPart rightForearm;
    private final ModelPart leftForearmSleeve;
    private final ModelPart rightForearmSleeve;
    public static final String UPPER_GEOMETRY_CHILD = "upper_geometry";
    public static final String LOWER_GEOMETRY_CHILD = "lower_geometry";
    public static final String UPPER_GEOMETRY = UPPER_GEOMETRY_CHILD;
    private final ModelPart leftUpperGeometry, rightUpperGeometry, leftUpperSleeve, rightUpperSleeve;
    private final ModelPart leftLowerGeometry, rightLowerGeometry, leftLowerSleeve, rightLowerSleeve;
    private boolean supportedFrame;
    private final ModelPart leftNativeArm, rightNativeArm, leftNativeSleeve, rightNativeSleeve;
    private final boolean slim;

    public ArticulatedHarnessModel(ModelPart nativeRoot, boolean slim) {
        super(articulatedRoot(nativeRoot, slim), slim);
        this.slim = slim;
        leftForearm = leftArm.getChild("forearm");
        rightForearm = rightArm.getChild("forearm");
        leftForearmSleeve = leftSleeve.getChild("forearm");
        rightForearmSleeve = rightSleeve.getChild("forearm");
        leftUpperGeometry=leftArm.getChild(UPPER_GEOMETRY); rightUpperGeometry=rightArm.getChild(UPPER_GEOMETRY);
        leftUpperSleeve=leftSleeve.getChild(UPPER_GEOMETRY); rightUpperSleeve=rightSleeve.getChild(UPPER_GEOMETRY);
        leftLowerGeometry=leftForearm.getChild(LOWER_GEOMETRY_CHILD); rightLowerGeometry=rightForearm.getChild(LOWER_GEOMETRY_CHILD);
        leftLowerSleeve=leftForearmSleeve.getChild(LOWER_GEOMETRY_CHILD); rightLowerSleeve=rightForearmSleeve.getChild(LOWER_GEOMETRY_CHILD);
        leftNativeArm = leftArm.getChild("neutral_full"); rightNativeArm = rightArm.getChild("neutral_full");
        leftNativeSleeve = leftSleeve.getChild("neutral_full"); rightNativeSleeve = rightSleeve.getChild("neutral_full");
        syncArmMeshVisibility();
    }

    private static ModelPart articulatedRoot(ModelPart original, boolean slim) {
        HashMap<String, ModelPart> children = new HashMap<>();
        for (String name : new String[]{"head", "hat", "body", "right_arm", "left_arm", "right_leg",
                "left_leg", "ear", "cloak", "left_sleeve", "right_sleeve", "left_pants", "right_pants", "jacket"}) {
            if (!original.hasChild(name)) continue;
            ModelPart part = original.getChild(name);
            if (name.equals("left_arm") || name.equals("right_arm")) part = arm(part, slim, name.equals("left_arm"), false);
            else if (name.equals("left_sleeve") || name.equals("right_sleeve")) part = arm(part, slim, name.equals("left_sleeve"), true);
            children.put(name, part);
        }
        return new ModelPart(java.util.List.of(), children);
    }

    private static ModelPart arm(ModelPart nativePart, boolean slim, boolean left, boolean sleeve) {
        ModelPart replacement = new ModelPart(java.util.List.of(), new HashMap<>());
        replacement.copyFrom(nativePart);
        float width = slim ? 3 : 4;
        float x = left ? -1 : -width + 1;
        int u = sleeve ? (left ? 48 : 40) : (left ? 32 : 40);
        int v = sleeve ? (left ? 48 : 32) : (left ? 48 : 16);
        float grow = sleeve ? .25f : 0;
        var upper = new ModelPart.Cube(u, v, x, -2, -2, width, 6, 4, grow, grow, grow,
                false, 64, 64, EnumSet.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN));
        var upperJoint = new ModelPart.Cube(u, v + 6, x, -2, -2, width, 6, 4, grow, grow, grow,
                false, 64, 64, EnumSet.of(Direction.UP));
        var lower = new ModelPart.Cube(u, v + 6, x, 0, -2, width, 6, 4, grow, grow, grow,
                false, 64, 64, EnumSet.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN));
        var wrist = new ModelPart.Cube(u, v, x, 0, -2, width, 6, 4, grow, grow, grow,
                false, 64, 64, EnumSet.of(Direction.UP));
        ModelPart lowerGeometry = new ModelPart(java.util.List.of(lower, wrist), new HashMap<>());
        lowerGeometry.setPos(0,0,0);
        ModelPart forearm = new ModelPart(java.util.List.of(), new HashMap<>(java.util.Map.of(LOWER_GEOMETRY_CHILD,lowerGeometry)));
        forearm.setPos(0, 4, 0);
        forearm.setInitialPose(PartPose.offset(0, 4, 0));
        forearm.loadPose(forearm.getInitialPose());
        ModelPart neutralFull = new ModelPart(java.util.List.of(new ModelPart.Cube(u, v, x, -2, -2,
                width, 12, 4, grow, grow, grow, false, 64, 64, EnumSet.allOf(Direction.class))), new HashMap<>());
        neutralFull.setPos(0, 0, 0);
        ModelPart upperGeometry=new ModelPart(java.util.List.of(upper,upperJoint),new HashMap<>());
        replacement = new ModelPart(java.util.List.of(), new HashMap<>(java.util.Map.of(UPPER_GEOMETRY,upperGeometry,"forearm", forearm, "neutral_full", neutralFull)));
        replacement.copyFrom(nativePart);
        replacement.setPos(nativePart.x, nativePart.y, nativePart.z);
        replacement.setRotation(nativePart.xRot, nativePart.yRot, nativePart.zRot);
        replacement.setInitialPose(nativePart.getInitialPose());
        return replacement;
    }

    public void setForearmRotation(HumanoidArm arm, float x, float y, float z) {
        ModelPart part = forearm(arm); part.xRot = x; part.yRot = y; part.zRot = z;
    }
    public ModelPart forearm(HumanoidArm arm) { return arm == HumanoidArm.LEFT ? leftForearm : rightForearm; }
    public float upperLength(HumanoidArm arm) { return forearm(arm).y / 16.0f; }
    public float palmWidthPixels() { return slim ? 3f : 4f; }
    public ModelPart armPart(HumanoidArm arm) { return arm == HumanoidArm.LEFT ? leftArm : rightArm; }
    /** Additional vanilla hand-frame translation for slim arms, in pixels. */
    public float slimHandOffset(HumanoidArm arm) { return slim ? (arm == HumanoidArm.LEFT ? -.5f : .5f) : 0; }
    public void resetForearms() { leftForearm.setRotation(0, 0, 0); rightForearm.setRotation(0, 0, 0); }
    public void setFrameMode(boolean supported) {
        supportedFrame=supported;
        float upper=supported?2:0, lower=supported?6:4;
        for(ModelPart p:new ModelPart[]{leftUpperGeometry,rightUpperGeometry,leftUpperSleeve,rightUpperSleeve}) p.setPos(0,upper,0);
        for(ModelPart p:new ModelPart[]{leftForearm,rightForearm,leftForearmSleeve,rightForearmSleeve}) p.setPos(0,lower,0);
        for(ModelPart p:new ModelPart[]{leftNativeArm,rightNativeArm,leftNativeSleeve,rightNativeSleeve}) p.setPos(0,upper,0);
        for(ModelPart p:new ModelPart[]{leftLowerGeometry,rightLowerGeometry,leftLowerSleeve,rightLowerSleeve}) p.yScale=supported?8f/6f:1f;
    }
    public int palmEndpointPixels() { return supportedFrame?12:10; }

    public void syncArmMeshVisibility() {
        leftForearmSleeve.copyFrom(leftForearm);
        rightForearmSleeve.copyFrom(rightForearm);
        setArmMeshMode(leftUpperGeometry,leftForearm,leftNativeArm,supportedFrame);
        setArmMeshMode(rightUpperGeometry,rightForearm,rightNativeArm,supportedFrame);
        setArmMeshMode(leftUpperSleeve,leftForearmSleeve,leftNativeSleeve,supportedFrame);
        setArmMeshMode(rightUpperSleeve,rightForearmSleeve,rightNativeSleeve,supportedFrame);
    }

    private static void setArmMeshMode(ModelPart upper, ModelPart forearm, ModelPart neutralFull, boolean supported) {
        boolean neutral = forearm.xRot == 0 && forearm.yRot == 0 && forearm.zRot == 0;
        boolean useNative = neutral && !supported;
        upper.skipDraw = useNative;
        upper.visible = !useNative;
        forearm.visible = !useNative;
        neutralFull.visible = useNative;
    }

    /** Restore parts that the aiming presentation may have moved before vanilla animation runs. */
    void resetPoseForFrame() {
        body.loadPose(body.getInitialPose());
        head.loadPose(head.getInitialPose());
        hat.loadPose(hat.getInitialPose());
        leftArm.loadPose(leftArm.getInitialPose());
        rightArm.loadPose(rightArm.getInitialPose());
        setFrameMode(false);
        resetForearms();
    }

    @Override public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                                    float netHeadYaw, float headPitch) {
        resetPoseForFrame();
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        syncArmMeshVisibility();
    }

    /** Vanilla hand frame with the elbow child inserted; neutral rotation is precisely vanilla. */
    @Override public void translateToHand(HumanoidArm arm, PoseStack pose) {
        super.translateToHand(arm, pose);
        ModelPart lower = forearm(arm);
        lower.translateAndRotate(pose);
        pose.translate(0, -4.0 / 16.0, 0);
    }
}
