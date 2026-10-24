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

/** Generated SS14 kobold asset rig. CC-BY-SA3.0 art; retain ATTRIBUTION.md.
 * Native1.21.1 API, based on pinned Blockbench4.12.6 geometry conventions.
 * NOT a registered entity/renderer/AI/networking implementation.
 */
public final class AnimalKoboldModel<T extends LivingEntity> extends HierarchicalModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
        ResourceLocation.fromNamespaceAndPath("moonstation14", "animals/kobold"), "main");
    private final ModelPart root;
    private final ModelPart mouse;
    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart horn_left;
    private final ModelPart arm_left;
    private final ModelPart forearm_left;
    private final ModelPart foot_left;
    private final ModelPart horn_right;
    private final ModelPart arm_right;
    private final ModelPart forearm_right;
    private final ModelPart foot_right;
    private final ModelPart tail_0;
    private final ModelPart tail_1;
    private final ModelPart tail_2;
    private final ModelPart gore;
    public AnimalKoboldModel(ModelPart root) {
        this.root = root;
        this.mouse = root.getChild("mouse");
        this.body = this.mouse.getChild("body");
        this.head = this.body.getChild("head");
        this.horn_left = this.head.getChild("horn_left");
        this.arm_left = this.body.getChild("arm_left");
        this.forearm_left = this.arm_left.getChild("forearm_left");
        this.foot_left = this.body.getChild("foot_left");
        this.horn_right = this.head.getChild("horn_right");
        this.arm_right = this.body.getChild("arm_right");
        this.forearm_right = this.arm_right.getChild("forearm_right");
        this.foot_right = this.body.getChild("foot_right");
        this.tail_0 = this.body.getChild("tail_0");
        this.tail_1 = this.tail_0.getChild("tail_1");
        this.tail_2 = this.tail_1.getChild("tail_2");
        this.gore = this.mouse.getChild("gore");
        this.gore.visible = false;
    }
    @Override public ModelPart root() { return root; }
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition base = mesh.getRoot();
        PartDefinition mouse = base.addOrReplaceChild("mouse", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 24.0F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition body = mouse.addOrReplaceChild("body", CubeListBuilder.create().texOffs(1, 1).addBox(-4.0F, -2.5F, -2.5F, 8.0F, 5.0F, 5.0F).texOffs(29, 1).addBox(-3.25F, -4.36816502F, -2.25F, 6.5F, 2.5F, 4.5F).texOffs(53, 1).addBox(-3.25F, 1.86816502F, -2.25F, 6.5F, 2.5F, 4.5F).texOffs(77, 1).addBox(-4.5F, 4.01717064F, -2.09255702F, 9.0F, 2.5F, 5.5F).texOffs(1, 13).addBox(-3.5F, 3.36399638F, -1.59255702F, 7.0F, 1.0F, 4.5F).texOffs(26, 13).addBox(-3.5F, 6.1703449F, -1.59255702F, 7.0F, 1.0F, 4.5F), PartPose.offsetAndRotation(-0.0F, -11.8110584F, 0.547869152F, -0.0F, -0.0F, 0.0F));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(51, 13).addBox(-4.75F, -2.0F, -2.75F, 9.5F, 4.0F, 5.5F).texOffs(83, 13).addBox(-3.75F, -3.26066964F, -2.25F, 7.5F, 2.0F, 4.5F).texOffs(1, 25).addBox(-3.75F, 1.26066964F, -2.25F, 7.5F, 2.0F, 4.5F).texOffs(27, 25).addBox(-3.0F, -0.341222458F, -6.74252705F, 6.0F, 3.0F, 4.5F).texOffs(50, 25).addBox(-3.06726094F, -1.44809072F, -3.09891959F, 1.0F, 1.0F, 0.5F).texOffs(55, 25).addBox(2.06726094F, -1.44809072F, -3.09891959F, 1.0F, 1.0F, 0.5F).texOffs(13, 44).addBox(-2.75F, 2.13358532F, -6.85210088F, 5.5F, 1.0F, 4.5F).texOffs(35, 44).addBox(-2.0F, -0.0786263485F, -6.60528217F, 4.0F, 1.0F, 0.5F), PartPose.offsetAndRotation(-0.0F, -6.32060477F, -0.767016813F, -0.0F, -0.0F, 0.0F));
        PartDefinition horn_left = head.addOrReplaceChild("horn_left", CubeListBuilder.create().texOffs(60, 25).addBox(-0.5F, -2.30343413F, -0.5F, 1.0F, 2.5F, 1.0F), PartPose.offsetAndRotation(-2.99513776F, -2.10686826F, -0.328721491F, -0.0F, -0.0F, -0.523598776F));
        PartDefinition arm_left = body.addOrReplaceChild("arm_left", CubeListBuilder.create().texOffs(66, 25).addBox(-1.57050243F, -0.419848807F, -1.05478692F, 2.0F, 4.0F, 2.0F), PartPose.offsetAndRotation(-3.56564019F, -3.16030239F, -0.547869152F, -0.0F, -0.0F, 0.0F));
        PartDefinition forearm_left = arm_left.addOrReplaceChild("forearm_left", CubeListBuilder.create().texOffs(76, 25).addBox(-0.928687196F, -0.169848807F, -1.07872149F, 2.0F, 3.5F, 1.5F).texOffs(85, 25).addBox(-0.857374392F, 2.66030239F, -1.65744298F, 2.0F, 1.0F, 2.0F), PartPose.offsetAndRotation(-1.14100486F, 3.16030239F, -0.10957383F, -0.0F, -0.0F, 0.0F));
        PartDefinition foot_left = body.addOrReplaceChild("foot_left", CubeListBuilder.create().texOffs(95, 25).addBox(-1.64262561F, -0.380535633F, -1.53085234F, 3.0F, 3.5F, 3.5F).texOffs(110, 25).addBox(-1.14262561F, 2.25304969F, -0.232983187F, 2.0F, 3.5F, 2.0F).texOffs(1, 35).addBox(-1.89262561F, 5.04388771F, -3.56488597F, 3.5F, 1.5F, 4.5F), PartPose.offsetAndRotation(-2.56726094F, 5.26717064F, 0.547869152F, -0.0F, -0.0F, 0.0F));
        PartDefinition horn_right = head.addOrReplaceChild("horn_right", CubeListBuilder.create().texOffs(19, 35).addBox(-0.5F, -2.30343413F, -0.5F, 1.0F, 2.5F, 1.0F), PartPose.offsetAndRotation(2.99513776F, -2.10686826F, -0.328721491F, -0.0F, -0.0F, 0.523598776F));
        PartDefinition arm_right = body.addOrReplaceChild("arm_right", CubeListBuilder.create().texOffs(25, 35).addBox(-0.429497569F, -0.419848807F, -1.05478692F, 2.0F, 4.0F, 2.0F), PartPose.offsetAndRotation(3.56564019F, -3.16030239F, -0.547869152F, -0.0F, -0.0F, 0.0F));
        PartDefinition forearm_right = arm_right.addOrReplaceChild("forearm_right", CubeListBuilder.create().texOffs(35, 35).addBox(-1.0713128F, -0.169848807F, -1.07872149F, 2.0F, 3.5F, 1.5F).texOffs(44, 35).addBox(-1.14262561F, 2.66030239F, -1.65744298F, 2.0F, 1.0F, 2.0F), PartPose.offsetAndRotation(1.14100486F, 3.16030239F, -0.10957383F, -0.0F, -0.0F, 0.0F));
        PartDefinition foot_right = body.addOrReplaceChild("foot_right", CubeListBuilder.create().texOffs(54, 35).addBox(-1.35737439F, -0.380535633F, -1.53085234F, 3.0F, 3.5F, 3.5F).texOffs(69, 35).addBox(-0.857374392F, 2.25304969F, -0.232983187F, 2.0F, 3.5F, 2.0F).texOffs(79, 35).addBox(-1.60737439F, 5.04388771F, -3.56488597F, 3.5F, 1.5F, 4.5F), PartPose.offsetAndRotation(2.56726094F, 5.26717064F, 0.547869152F, -0.0F, -0.0F, 0.0F));
        PartDefinition tail_0 = body.addOrReplaceChild("tail_0", CubeListBuilder.create().texOffs(97, 35).addBox(-1.25F, -1.0F, -0.289015594F, 2.5F, 2.0F, 3.5F), PartPose.offsetAndRotation(-0.0F, 5.26717064F, 2.84891959F, -0.122173048F, -0.20943951F, 0.0F));
        PartDefinition tail_1 = tail_0.addOrReplaceChild("tail_1", CubeListBuilder.create().texOffs(111, 35).addBox(-1.0F, -0.75F, -0.289015594F, 2.0F, 1.5F, 3.5F), PartPose.offsetAndRotation(-0.0F, 0.0F, 2.92196881F, -0.122173048F, 0.20943951F, 0.0F));
        PartDefinition tail_2 = tail_1.addOrReplaceChild("tail_2", CubeListBuilder.create().texOffs(1, 44).addBox(-0.75F, -0.5F, -0.289015594F, 1.5F, 1.0F, 3.5F), PartPose.offsetAndRotation(-0.0F, 0.0F, 2.92196881F, -0.122173048F, -0.20943951F, 0.0F));
        PartDefinition gore = mouse.addOrReplaceChild("gore", CubeListBuilder.create().texOffs(46, 44).addBox(-1.8541329F, -0.0421373651F, -1.36967288F, 3.7082658F, 0.0316030239F, 2.73934576F), PartPose.offsetAndRotation(-0.0F, 0.0F, 0.0F, -0.0F, -0.0F, 0.0F));
        // Logical UV dimensions from spec; physical PNG has2x texels, NOT doubled geometry.
        return LayerDefinition.create(mesh, 128, 128);
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
            throw new IllegalArgumentException("Nonfinite kobold presentation");
        root.getAllParts().forEach(ModelPart::resetPose);
        gore.visible = false; // Independent source blood state is OFF by default, even Dead.
        if (dead) clip = "down_hold";
        if (clip != null && !clip.equals("rest")) {
            KeyframeAnimations.animate(this, AnimalKoboldAnimations.byName(clip),
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
        applyPresentation(dead ? "down_hold" : limbSwingAmount > .025F ? "walk" : "idle_breathe",
            dead, ageInTicks / 20.0F, -netHeadYaw, -headPitch, -1);
    }
}
