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

/** Generated SS14 hamster asset rig. CC-BY-SA3.0 art; retain ATTRIBUTION.md.
 * Native1.21.1 API, based on pinned Blockbench4.12.6 geometry conventions.
 * NOT a registered entity/renderer/AI/networking implementation.
 */
public final class AnimalHamsterModel<T extends LivingEntity> extends HierarchicalModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
        ResourceLocation.fromNamespaceAndPath("moonstation14", "animals/hamster"), "main");
    private final ModelPart root;
    private final ModelPart mouse;
    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart ear_left;
    private final ModelPart ear_right;
    private final ModelPart front_left;
    private final ModelPart front_right;
    private final ModelPart hind_left;
    private final ModelPart hind_right;
    private final ModelPart gore;
    public AnimalHamsterModel(ModelPart root) {
        this.root = root;
        this.mouse = root.getChild("mouse");
        this.body = this.mouse.getChild("body");
        this.head = this.body.getChild("head");
        this.ear_left = this.head.getChild("ear_left");
        this.ear_right = this.head.getChild("ear_right");
        this.front_left = this.body.getChild("front_left");
        this.front_right = this.body.getChild("front_right");
        this.hind_left = this.body.getChild("hind_left");
        this.hind_right = this.body.getChild("hind_right");
        this.gore = this.mouse.getChild("gore");
        this.gore.visible = false;
    }
    @Override public ModelPart root() { return root; }
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition base = mesh.getRoot();
        PartDefinition mouse = base.addOrReplaceChild("mouse", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 24.0F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition body = mouse.addOrReplaceChild("body", CubeListBuilder.create().texOffs(1, 1).addBox(-3.0F, -1.25F, -2.5F, 6.0F, 2.5F, 5.0F).texOffs(25, 1).addBox(-2.5F, -1.87096058F, -2.0F, 5.0F, 1.0F, 4.0F).texOffs(45, 1).addBox(-2.25F, 0.802412554F, -2.48275862F, 4.5F, 1.0F, 4.0F), PartPose.offsetAndRotation(-0.0F, -2.58092231F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(1, 11).addBox(-2.75F, -1.75F, -2.35344828F, 5.5F, 3.5F, 3.5F).texOffs(21, 11).addBox(-2.0F, -0.387964025F, -2.79310345F, 4.0F, 2.0F, 1.0F).texOffs(33, 11).addBox(-0.25F, 0.362035975F, -3.21965517F, 0.5F, 0.5F, 0.5F).texOffs(37, 11).addBox(-2.44013312F, -1.05083238F, -2.50689655F, 1.0F, 1.0F, 0.5F).texOffs(42, 11).addBox(1.44013312F, -1.05083238F, -2.50689655F, 1.0F, 1.0F, 0.5F), PartPose.offsetAndRotation(-0.0F, -0.856850365F, -1.81034483F, -0.0F, -0.0F, 0.0F));
        PartDefinition ear_left = head.addOrReplaceChild("ear_left", CubeListBuilder.create().texOffs(47, 11).addBox(-1.0F, -1.77116533F, -0.5F, 2.0F, 2.0F, 1.0F).texOffs(55, 11).addBox(-0.5F, -1.52116533F, -0.587931034F, 1.0F, 1.5F, 0.5F), PartPose.offsetAndRotation(-1.94013312F, -1.53008994F, 0.0F, -0.0F, -0.0F, -0.20943951F));
        PartDefinition ear_right = head.addOrReplaceChild("ear_right", CubeListBuilder.create().texOffs(1, 20).addBox(-1.0F, -1.77116533F, -0.5F, 2.0F, 2.0F, 1.0F).texOffs(9, 20).addBox(-0.5F, -1.52116533F, -0.587931034F, 1.0F, 1.5F, 0.5F), PartPose.offsetAndRotation(1.94013312F, -1.53008994F, 0.0F, -0.0F, -0.0F, 0.20943951F));
        PartDefinition front_left = body.addOrReplaceChild("front_left", CubeListBuilder.create().texOffs(14, 20).addBox(-0.5F, -0.26742633F, -0.5F, 1.0F, 1.0F, 1.0F).texOffs(20, 20).addBox(-0.75F, 0.356850365F, -0.99137931F, 1.5F, 1.0F, 1.5F), PartPose.offsetAndRotation(-2.04791829F, 1.22407195F, -1.54482759F, -0.0F, -0.0F, 0.0F));
        PartDefinition front_right = body.addOrReplaceChild("front_right", CubeListBuilder.create().texOffs(28, 20).addBox(-0.5F, -0.26742633F, -0.5F, 1.0F, 1.0F, 1.0F).texOffs(34, 20).addBox(-0.75F, 0.356850365F, -0.99137931F, 1.5F, 1.0F, 1.5F), PartPose.offsetAndRotation(2.04791829F, 1.22407195F, -1.54482759F, -0.0F, -0.0F, 0.0F));
        PartDefinition hind_left = body.addOrReplaceChild("hind_left", CubeListBuilder.create().texOffs(42, 20).addBox(-0.5F, -0.26742633F, -0.5F, 1.0F, 1.0F, 1.0F).texOffs(48, 20).addBox(-0.75F, 0.356850365F, -0.99137931F, 1.5F, 1.0F, 1.5F), PartPose.offsetAndRotation(-2.04791829F, 1.22407195F, 1.54482759F, -0.0F, -0.0F, 0.0F));
        PartDefinition hind_right = body.addOrReplaceChild("hind_right", CubeListBuilder.create().texOffs(56, 20).addBox(-0.5F, -0.26742633F, -0.5F, 1.0F, 1.0F, 1.0F).texOffs(1, 25).addBox(-0.75F, 0.356850365F, -0.99137931F, 1.5F, 1.0F, 1.5F), PartPose.offsetAndRotation(2.04791829F, 1.22407195F, 1.54482759F, -0.0F, -0.0F, 0.0F));
        PartDefinition gore = mouse.addOrReplaceChild("gore", CubeListBuilder.create().texOffs(9, 25).addBox(-2.00172465F, -0.048962878F, -1.50862069F, 4.00344929F, 0.0367221585F, 3.01724138F), PartPose.offsetAndRotation(-0.0F, 0.0F, 0.0F, -0.0F, -0.0F, 0.0F));
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
            throw new IllegalArgumentException("Nonfinite hamster presentation");
        root.getAllParts().forEach(ModelPart::resetPose);
        gore.visible = false; // Independent source blood state is OFF by default, even Dead.
        if (dead) clip = "down_hold";
        if (clip != null && !clip.equals("rest")) {
            KeyframeAnimations.animate(this, AnimalHamsterAnimations.byName(clip),
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
