package com.juicyslew.moonstation14.ms14.mob.animals.client;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.animation.KeyframeAnimations;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3f;

/** Generated SS14 bat asset rig. CC-BY-SA3.0 art; retain ATTRIBUTION.md.
 * Native1.21.1 API, based on pinned Blockbench4.12.6 geometry conventions.
 * NOT a registered entity/renderer/AI/networking implementation.
 */
public final class AnimalBatModel<T extends LivingEntity> extends HierarchicalModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
        ResourceLocation.fromNamespaceAndPath("moonstation14", "animals/bat"), "main");
    private final ModelPart root;
    private final ModelPart mouse;
    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart ear_left;
    private final ModelPart ear_right;
    private final ModelPart wing_left;
    private final ModelPart wingtip_left;
    private final ModelPart foot_left;
    private final ModelPart wing_right;
    private final ModelPart wingtip_right;
    private final ModelPart foot_right;
    private final ModelPart gore;
    public AnimalBatModel(ModelPart root) {
        this.root = root;
        this.mouse = root.getChild("mouse");
        this.body = this.mouse.getChild("body");
        this.head = this.body.getChild("head");
        this.ear_left = this.head.getChild("ear_left");
        this.ear_right = this.head.getChild("ear_right");
        this.wing_left = this.body.getChild("wing_left");
        this.wingtip_left = this.wing_left.getChild("wingtip_left");
        this.foot_left = this.body.getChild("foot_left");
        this.wing_right = this.body.getChild("wing_right");
        this.wingtip_right = this.wing_right.getChild("wingtip_right");
        this.foot_right = this.body.getChild("foot_right");
        this.gore = this.mouse.getChild("gore");
        this.gore.visible = false;
    }
    @Override public ModelPart root() { return root; }
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition base = mesh.getRoot();
        PartDefinition mouse = base.addOrReplaceChild("mouse", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 24.0F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition body = mouse.addOrReplaceChild("body", CubeListBuilder.create().texOffs(1, 1).addBox(-1.55655262F, -0.847568522F, -1.25714286F, 3.11310525F, 1.69513704F, 2.51428571F).texOffs(15, 1).addBox(-1.21411105F, -1.46132504F, -1.03085714F, 2.42822209F, 0.75988902F, 2.06171429F).texOffs(26, 1).addBox(-1.21411105F, 0.701436018F, -1.03085714F, 2.42822209F, 0.75988902F, 2.06171429F), PartPose.offsetAndRotation(-0.0F, -5.47996889F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(37, 1).addBox(-1.47872499F, -0.508541113F, -1.00571429F, 2.95744998F, 1.01708223F, 2.01142857F).texOffs(49, 1).addBox(-1.15340549F, -0.876795023F, -0.824685714F, 2.30681099F, 0.455933412F, 1.64937143F).texOffs(1, 8).addBox(-1.15340549F, 0.420861611F, -0.824685714F, 2.30681099F, 0.455933412F, 1.64937143F).texOffs(11, 8).addBox(-1.08958684F, -0.510550435F, -1.19542857F, 0.435834734F, 0.319664852F, 0.228571429F).texOffs(15, 8).addBox(0.653752101F, -0.510550435F, -1.19542857F, 0.435834734F, 0.319664852F, 0.228571429F).texOffs(31, 8).addBox(-0.996193678F, 0.0904194867F, -1.36571429F, 1.99238736F, 0.593663297F, 0.4F), PartPose.offsetAndRotation(-0.0F, -0.935248024F, -1.63428571F, -0.0F, -0.0F, 0.0F));
        PartDefinition ear_left = head.addOrReplaceChild("ear_left", CubeListBuilder.create().texOffs(19, 8).addBox(-0.610168628F, -1.64399067F, -0.285714286F, 1.22033726F, 1.64399067F, 0.571428571F), PartPose.offsetAndRotation(-0.996193678F, -1.34807235F, 0.948571429F, -0.0F, -0.0F, -0.20943951F));
        PartDefinition ear_right = head.addOrReplaceChild("ear_right", CubeListBuilder.create().texOffs(25, 8).addBox(-0.610168628F, -1.64399067F, -0.285714286F, 1.22033726F, 1.64399067F, 0.571428571F), PartPose.offsetAndRotation(0.996193678F, -1.34807235F, 0.948571429F, -0.0F, -0.0F, 0.20943951F));
        PartDefinition wing_left = body.addOrReplaceChild("wing_left", CubeListBuilder.create().texOffs(38, 8).addBox(-4.18401345F, -0.159832426F, -1.11428571F, 4.18401345F, 0.228332037F, 0.4F).texOffs(1, 13).addBox(-4.18401345F, -0.0593663297F, -1.2F, 4.18401345F, 0.118732659F, 3.2F), PartPose.offsetAndRotation(-1.36976631F, -0.456664074F, 0.0F, -0.0F, -0.0F, 0.314159265F));
        PartDefinition wingtip_left = wing_left.addOrReplaceChild("wingtip_left", CubeListBuilder.create().texOffs(18, 13).addBox(-4.18401345F, -0.0547996889F, -1.16571429F, 4.18401345F, 0.109599378F, 1.07428571F).texOffs(31, 13).addBox(-3.05084314F, -0.0547996889F, -0.137142857F, 3.05084314F, 0.109599378F, 1.07428571F).texOffs(42, 13).addBox(-1.91767283F, -0.0547996889F, 0.891428571F, 1.91767283F, 0.109599378F, 1.07428571F).texOffs(50, 13).addBox(-4.00967956F, -0.18266563F, -1.26857143F, 4.00967956F, 0.127865941F, 0.16F), PartPose.offsetAndRotation(-4.18401345F, 0.0F, -0.114285714F, -0.0F, -0.0F, 0.20943951F));
        PartDefinition foot_left = body.addOrReplaceChild("foot_left", CubeListBuilder.create().texOffs(1, 19).addBox(-0.24904842F, -0.14795916F, -0.971428571F, 0.498096839F, 0.821995334F, 1.25714286F), PartPose.offsetAndRotation(-0.716014206F, 1.34441903F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition wing_right = body.addOrReplaceChild("wing_right", CubeListBuilder.create().texOffs(7, 19).addBox(2.22044605e-16F, -0.159832426F, -1.11428571F, 4.18401345F, 0.228332037F, 0.4F).texOffs(19, 19).addBox(2.22044605e-16F, -0.0593663297F, -1.2F, 4.18401345F, 0.118732659F, 3.2F), PartPose.offsetAndRotation(1.36976631F, -0.456664074F, 0.0F, -0.0F, -0.0F, -0.314159265F));
        PartDefinition wingtip_right = wing_right.addOrReplaceChild("wingtip_right", CubeListBuilder.create().texOffs(36, 19).addBox(0.0F, -0.0547996889F, -1.16571429F, 4.18401345F, 0.109599378F, 1.07428571F).texOffs(49, 19).addBox(0.0F, -0.0547996889F, -0.137142857F, 3.05084314F, 0.109599378F, 1.07428571F).texOffs(1, 25).addBox(0.0F, -0.0547996889F, 0.891428571F, 1.91767283F, 0.109599378F, 1.07428571F).texOffs(9, 25).addBox(0.0F, -0.18266563F, -1.26857143F, 4.00967956F, 0.127865941F, 0.16F), PartPose.offsetAndRotation(4.18401345F, 0.0F, -0.114285714F, -0.0F, -0.0F, -0.20943951F));
        PartDefinition foot_right = body.addOrReplaceChild("foot_right", CubeListBuilder.create().texOffs(20, 25).addBox(-0.24904842F, -0.14795916F, -0.971428571F, 0.498096839F, 0.821995334F, 1.25714286F), PartPose.offsetAndRotation(0.716014206F, 1.34441903F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition gore = mouse.addOrReplaceChild("gore", CubeListBuilder.create().texOffs(26, 25).addBox(-1.61881473F, -0.0365331259F, -1.42857143F, 3.23762946F, 0.0273998445F, 2.85714286F), PartPose.offsetAndRotation(-0.0F, 0.0F, 0.0F, -0.0F, -0.0F, 0.0F));
        // Logical UV dimensions from spec; physical PNG has2x texels, NOT doubled geometry.
        return LayerDefinition.create(mesh, 64, 64);
    }
    /** Reset every bone FIRST. Host may call after setupAnim with authoritative
     * state. Negative flinch time disables optional PORT hurt overlay.
     * Critical: down_hold,false. Dead: down_hold,true. Never vanilla corpse flip.
     * Look inputs are canonical +Y yaw/+X pitch. Mojmaps netHeadYaw/headPitch
     * are negated by setupAnim below; host adapters must do that ONCE, not twice.
     */
    public void applyPresentation(String clip, boolean dead, float elapsedSeconds,
            float yawDegrees, float pitchDegrees, float flinchSeconds) {
        if (!Float.isFinite(elapsedSeconds) || !Float.isFinite(yawDegrees) ||
            !Float.isFinite(pitchDegrees) || !Float.isFinite(flinchSeconds))
            throw new IllegalArgumentException("Nonfinite bat presentation");
        root.getAllParts().forEach(ModelPart::resetPose);
        gore.visible = false; // Independent source blood state is OFF by default, even Dead.
        if (dead) clip = "down_hold";
        if (clip != null && !clip.equals("rest")) {
            KeyframeAnimations.animate(this, AnimalBatAnimations.byName(clip),
                Math.round(Math.max(0, elapsedSeconds) * 1000.0F), 1.0F, new Vector3f());
        }
        boolean aliveLook = !dead && (clip == null || clip.equals("rest") ||
            clip.equals("walk") || clip.equals("run") || clip.equals("idle_breathe"));
        if (aliveLook) {
            head.yRot -= Math.max(-25, Math.min(25, yawDegrees)) * (float)(Math.PI / 180);
            head.xRot -= Math.max(-12, Math.min(12, pitchDegrees)) * (float)(Math.PI / 180);
        }
    }
    /** Call AFTER applyPresentation only if source state explicitly selects blood. */
    public void setSourceBloodVisible(boolean enabled) {
        gore.visible = enabled && false;
    }
    @Override public void setupAnim(T entity, float limbSwing, float limbSwingAmount,
            float ageInTicks, float netHeadYaw, float headPitch) {
        // Safe vanilla fallback ONLY. Host authoritative Critical/sprint/feeding/
        // swim/airborne state must override through applyPresentation; see contract.
        boolean dead = entity.isDeadOrDying();
        applyPresentation(dead ? "down_hold" : limbSwingAmount > .025F ? "fly" : "idle_breathe",
            dead, ageInTicks / 20.0F, -netHeadYaw, -headPitch, -1);
    }
}
