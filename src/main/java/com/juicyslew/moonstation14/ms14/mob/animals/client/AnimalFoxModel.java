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

/** Generated SS14 fox asset rig. CC-BY-SA3.0 art; retain ATTRIBUTION.md.
 * Native1.21.1 API, based on pinned Blockbench4.12.6 geometry conventions.
 * NOT a registered entity/renderer/AI/networking implementation.
 */
public final class AnimalFoxModel<T extends LivingEntity> extends HierarchicalModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
        ResourceLocation.fromNamespaceAndPath("moonstation14", "animals/fox"), "main");
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
    private final ModelPart tail_0;
    private final ModelPart tail_1;
    private final ModelPart tail_2;
    private final ModelPart gore;
    private final ModelPart front_left_knee;
    private final ModelPart front_right_knee;
    private final ModelPart hind_left_knee;
    private final ModelPart hind_right_knee;
    public AnimalFoxModel(ModelPart root) {
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
        this.tail_0 = this.body.getChild("tail_0");
        this.tail_1 = this.tail_0.getChild("tail_1");
        this.tail_2 = this.tail_1.getChild("tail_2");
        this.gore = this.mouse.getChild("gore");
        this.front_left_knee = this.front_left.getChild("front_left_knee");
        this.front_right_knee = this.front_right.getChild("front_right_knee");
        this.hind_left_knee = this.hind_left.getChild("hind_left_knee");
        this.hind_right_knee = this.hind_right.getChild("hind_right_knee");
        this.gore.visible = false;
    }
    @Override public ModelPart root() { return root; }
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition base = mesh.getRoot();
        PartDefinition mouse = base.addOrReplaceChild("mouse", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 24.0F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition body = mouse.addOrReplaceChild("body", CubeListBuilder.create().texOffs(1, 1).addBox(-3.0F, -1.75F, -6.0F, 6.0F, 3.5F, 12.0F).texOffs(39, 1).addBox(-2.5F, -2.58999914F, -4.75F, 5.0F, 1.5F, 9.5F).texOffs(70, 1).addBox(-2.25F, 0.997999184F, -4.96000009F, 4.5F, 1.5F, 9.0F).texOffs(36, 27).addBox(-1.75F, -1.65800004F, -6.04600117F, 3.5F, 3.5F, 0.5F), PartPose.offsetAndRotation(-0.0F, -5.90499748F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(99, 1).addBox(-2.5F, -2.25F, -2.32500012F, 5.0F, 4.5F, 3.5F).texOffs(1, 19).addBox(-1.5F, -0.675000268F, -3.66500049F, 3.0F, 2.5F, 2.5F).texOffs(14, 19).addBox(-0.75F, 0.0749997315F, -4.04500076F, 1.5F, 1.0F, 0.5F).texOffs(20, 19).addBox(-2.156F, -1.01749976F, -2.63050048F, 1.0F, 1.0F, 0.5F).texOffs(25, 19).addBox(1.156F, -1.01749976F, -2.63050048F, 1.0F, 1.0F, 0.5F).texOffs(24, 27).addBox(-2.25F, -0.0600003222F, -2.65350048F, 4.5F, 1.5F, 0.5F).texOffs(84, 27).addBox(-1.75F, -0.0950030607F, -0.774998959F, 3.5F, 3.5F, 3.0F).texOffs(99, 27).addBox(-1.25F, -0.0450030607F, -1.09499896F, 2.5F, 3.0F, 0.5F), PartPose.offsetAndRotation(-0.0F, -2.74999946F, -5.77500104F, -0.0F, -0.0F, 0.0F));
        PartDefinition ear_left = head.addOrReplaceChild("ear_left", CubeListBuilder.create().texOffs(30, 19).addBox(-0.75F, -1.90562458F, -0.5F, 1.5F, 2.0F, 1.0F).texOffs(37, 19).addBox(-0.5F, -1.65562458F, -0.572000065F, 1.0F, 1.5F, 0.5F), PartPose.offsetAndRotation(-1.656F, -1.83999914F, 0.0F, -0.0F, -0.0F, -0.20943951F));
        PartDefinition ear_right = head.addOrReplaceChild("ear_right", CubeListBuilder.create().texOffs(42, 19).addBox(-0.75F, -1.90562458F, -0.5F, 1.5F, 2.0F, 1.0F).texOffs(49, 19).addBox(-0.5F, -1.65562458F, -0.572000065F, 1.0F, 1.5F, 0.5F), PartPose.offsetAndRotation(1.656F, -1.83999914F, 0.0F, -0.0F, -0.0F, 0.20943951F));
        PartDefinition front_left = body.addOrReplaceChild("front_left", CubeListBuilder.create().texOffs(54, 19).addBox(-0.5F, -0.249750341F, -0.5F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(-2.0125F, 1.83999914F, -3.68000074F, -0.0F, -0.0F, 0.0F));
        PartDefinition front_right = body.addOrReplaceChild("front_right", CubeListBuilder.create().texOffs(68, 19).addBox(-0.5F, -0.249750341F, -0.5F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(2.0125F, 1.83999914F, -3.68000074F, -0.0F, -0.0F, 0.0F));
        PartDefinition hind_left = body.addOrReplaceChild("hind_left", CubeListBuilder.create().texOffs(82, 19).addBox(-0.5F, -0.249750341F, -0.5F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(-2.0125F, 1.83999914F, 3.68000074F, -0.0F, -0.0F, 0.0F));
        PartDefinition hind_right = body.addOrReplaceChild("hind_right", CubeListBuilder.create().texOffs(96, 19).addBox(-0.5F, -0.249750341F, -0.5F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(2.0125F, 1.83999914F, 3.68000074F, -0.0F, -0.0F, 0.0F));
        PartDefinition tail_0 = body.addOrReplaceChild("tail_0", CubeListBuilder.create().texOffs(110, 19).addBox(-1.5F, -1.5F, -0.291666474F, 3.0F, 3.0F, 2.5F), PartPose.offsetAndRotation(-0.0F, -0.459999785F, 5.75000116F, 0.261799388F, -0.20943951F, 0.0F));
        PartDefinition tail_1 = tail_0.addOrReplaceChild("tail_1", CubeListBuilder.create().texOffs(1, 27).addBox(-1.25F, -1.25F, -0.291666474F, 2.5F, 2.5F, 2.5F), PartPose.offsetAndRotation(-0.0F, 0.0F, 1.91666705F, 0.261799388F, 0.20943951F, 0.0F));
        PartDefinition tail_2 = tail_1.addOrReplaceChild("tail_2", CubeListBuilder.create().texOffs(13, 27).addBox(-1.0F, -1.0F, -0.291666474F, 2.0F, 2.0F, 2.5F), PartPose.offsetAndRotation(-0.0F, 0.0F, 1.91666705F, 0.261799388F, -0.20943951F, 0.0F));
        PartDefinition gore = mouse.addOrReplaceChild("gore", CubeListBuilder.create().texOffs(46, 27).addBox(-1.495F, -0.0459999785F, -1.43750029F, 2.99F, 0.0344999839F, 2.87500058F), PartPose.offsetAndRotation(-0.0F, 0.0F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition front_left_knee = front_left.addOrReplaceChild("front_left_knee", CubeListBuilder.create().texOffs(60, 19).addBox(-0.75F, 1.46649908F, -0.980000046F, 1.5F, 1.0F, 1.5F).texOffs(60, 27).addBox(-0.5F, -0.131750405F, -0.5F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(-0.0F, 1.59849925F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition front_right_knee = front_right.addOrReplaceChild("front_right_knee", CubeListBuilder.create().texOffs(74, 19).addBox(-0.75F, 1.46649908F, -0.980000046F, 1.5F, 1.0F, 1.5F).texOffs(66, 27).addBox(-0.5F, -0.131750405F, -0.5F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(-0.0F, 1.59849925F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition hind_left_knee = hind_left.addOrReplaceChild("hind_left_knee", CubeListBuilder.create().texOffs(88, 19).addBox(-0.75F, 1.46649908F, -0.980000046F, 1.5F, 1.0F, 1.5F).texOffs(72, 27).addBox(-0.5F, -0.131750405F, -0.5F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(-0.0F, 1.59849925F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition hind_right_knee = hind_right.addOrReplaceChild("hind_right_knee", CubeListBuilder.create().texOffs(102, 19).addBox(-0.75F, 1.46649908F, -0.980000046F, 1.5F, 1.0F, 1.5F).texOffs(78, 27).addBox(-0.5F, -0.131750405F, -0.5F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(-0.0F, 1.59849925F, 0.0F, -0.0F, -0.0F, 0.0F));
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
            throw new IllegalArgumentException("Nonfinite fox presentation");
        root.getAllParts().forEach(ModelPart::resetPose);
        gore.visible = false; // Independent source blood state is OFF by default, even Dead.
        if (dead) clip = "down_hold";
        if (clip != null && !clip.equals("rest")) {
            KeyframeAnimations.animate(this, AnimalFoxAnimations.byName(clip),
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
