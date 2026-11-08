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

/** Generated SS14 cow asset rig. CC-BY-SA3.0 art; retain ATTRIBUTION.md.
 * Native1.21.1 API, based on pinned Blockbench4.12.6 geometry conventions.
 * NOT a registered entity/renderer/AI/networking implementation.
 */
public final class AnimalCowModel<T extends LivingEntity> extends HierarchicalModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
        ResourceLocation.fromNamespaceAndPath("moonstation14", "animals/cow"), "main");
    private final ModelPart root;
    private final ModelPart mouse;
    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart horn_left;
    private final ModelPart horn_right;
    private final ModelPart front_left;
    private final ModelPart front_right;
    private final ModelPart hind_left;
    private final ModelPart hind_right;
    private final ModelPart tail_0;
    private final ModelPart tail_1;
    private final ModelPart gore;
    private final ModelPart front_left_knee;
    private final ModelPart front_right_knee;
    private final ModelPart hind_left_knee;
    private final ModelPart hind_right_knee;
    public AnimalCowModel(ModelPart root) {
        this.root = root;
        this.mouse = root.getChild("mouse");
        this.body = this.mouse.getChild("body");
        this.head = this.body.getChild("head");
        this.horn_left = this.head.getChild("horn_left");
        this.horn_right = this.head.getChild("horn_right");
        this.front_left = this.body.getChild("front_left");
        this.front_right = this.body.getChild("front_right");
        this.hind_left = this.body.getChild("hind_left");
        this.hind_right = this.body.getChild("hind_right");
        this.tail_0 = this.body.getChild("tail_0");
        this.tail_1 = this.tail_0.getChild("tail_1");
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
        PartDefinition body = mouse.addOrReplaceChild("body", CubeListBuilder.create().texOffs(1, 1).addBox(-7.0F, -3.25F, -12.5F, 14.0F, 6.5F, 25.0F).texOffs(81, 1).addBox(-5.5F, -5.44638767F, -10.25F, 11.0F, 3.0F, 20.5F).texOffs(146, 1).addBox(-5.5F, 2.44638767F, -10.25F, 11.0F, 3.0F, 20.5F).texOffs(1, 35).addBox(-5.25F, -5.59814211F, -11.25F, 10.5F, 1.5F, 22.5F).texOffs(174, 61).addBox(-2.25F, 4.85865905F, 3.94872379F, 4.5F, 2.5F, 4.5F), PartPose.offsetAndRotation(-0.0F, -15.7231662F, 1.23974476F, -0.0F, -0.0F, 0.0F));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(69, 35).addBox(-4.0F, -1.28037158F, -3.25F, 8.0F, 4.5F, 6.5F).texOffs(100, 35).addBox(-3.0F, -3.15047171F, -2.75F, 6.0F, 2.5F, 5.5F).texOffs(125, 35).addBox(-3.0F, 2.58972855F, -2.75F, 6.0F, 2.5F, 5.5F).texOffs(150, 35).addBox(-3.5F, -3.20444263F, -6.22948952F, 7.0F, 3.5F, 7.5F).texOffs(181, 35).addBox(-2.75F, -4.67877023F, -5.47948952F, 5.5F, 2.0F, 6.0F).texOffs(206, 35).addBox(-2.75F, -0.230115034F, -5.47948952F, 5.5F, 2.0F, 6.0F).texOffs(231, 35).addBox(-3.0F, -0.765185789F, -7.44872379F, 6.0F, 2.5F, 2.5F).texOffs(1, 61).addBox(-2.0F, -0.306074316F, -7.81244303F, 4.0F, 1.0F, 0.5F).texOffs(12, 61).addBox(-2.6F, -2.73014537F, -6.399134F, 1.0F, 1.0F, 0.5F).texOffs(17, 61).addBox(1.6F, -2.73014537F, -6.399134F, 1.0F, 1.0F, 0.5F).texOffs(22, 61).addBox(-6.3F, -3.69977379F, -2.48974476F, 3.5F, 1.0F, 2.5F).texOffs(42, 61).addBox(2.8F, -3.69977379F, -2.48974476F, 3.5F, 1.0F, 2.5F), PartPose.offsetAndRotation(-0.0F, -4.84814211F, -12.3974476F, -0.0F, -0.0F, 0.0F));
        PartDefinition horn_left = head.addOrReplaceChild("horn_left", CubeListBuilder.create().texOffs(36, 61).addBox(-0.5F, -1.77570274F, -0.5F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(-2.56666667F, -4.07243937F, -1.23974476F, -0.0F, -0.0F, -0.314159265F));
        PartDefinition horn_right = head.addOrReplaceChild("horn_right", CubeListBuilder.create().texOffs(56, 61).addBox(-0.5F, -1.77570274F, -0.5F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(2.56666667F, -4.07243937F, -1.23974476F, -0.0F, -0.0F, 0.314159265F));
        PartDefinition front_left = body.addOrReplaceChild("front_left", CubeListBuilder.create().texOffs(62, 61).addBox(-1.25F, -0.271991952F, -1.25F, 2.5F, 5.5F, 2.5F), PartPose.offsetAndRotation(-5.25F, 4.84814211F, -8.67821331F, -0.0F, -0.0F, 0.0F));
        PartDefinition front_right = body.addOrReplaceChild("front_right", CubeListBuilder.create().texOffs(90, 61).addBox(-1.25F, -0.271991952F, -1.25F, 2.5F, 5.5F, 2.5F), PartPose.offsetAndRotation(5.25F, 4.84814211F, -8.67821331F, -0.0F, -0.0F, 0.0F));
        PartDefinition hind_left = body.addOrReplaceChild("hind_left", CubeListBuilder.create().texOffs(118, 61).addBox(-1.25F, -0.271991952F, -1.25F, 2.5F, 5.5F, 2.5F), PartPose.offsetAndRotation(-5.25F, 4.84814211F, 7.43846855F, -0.0F, -0.0F, 0.0F));
        PartDefinition hind_right = body.addOrReplaceChild("hind_right", CubeListBuilder.create().texOffs(146, 61).addBox(-1.25F, -0.271991952F, -1.25F, 2.5F, 5.5F, 2.5F), PartPose.offsetAndRotation(5.25F, 4.84814211F, 7.43846855F, -0.0F, -0.0F, 0.0F));
        PartDefinition tail_0 = body.addOrReplaceChild("tail_0", CubeListBuilder.create().texOffs(194, 61).addBox(-0.5F, -0.177560631F, 0.491795807F, 1.0F, 8.5F, 1.0F), PartPose.offsetAndRotation(-0.0F, -2.90888526F, 12.3974476F, -0.0F, -0.0F, 0.0F));
        PartDefinition tail_1 = tail_0.addOrReplaceChild("tail_1", CubeListBuilder.create().texOffs(200, 61).addBox(-0.75F, -0.612148631F, -0.75F, 1.5F, 2.0F, 1.5F), PartPose.offsetAndRotation(-0.0F, 8.0479159F, 0.991795807F, -0.0F, -0.0F, 0.0F));
        PartDefinition gore = mouse.addOrReplaceChild("gore", CubeListBuilder.create().texOffs(208, 61).addBox(-1.51666667F, -0.0387851369F, -1.54968095F, 3.03333333F, 0.0290888526F, 3.0993619F), PartPose.offsetAndRotation(-0.0F, 0.0F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition front_left_knee = front_left.addOrReplaceChild("front_left_knee", CubeListBuilder.create().texOffs(74, 61).addBox(-1.5F, 4.8426526F, -2.24794895F, 3.0F, 1.0F, 4.0F).texOffs(223, 61).addBox(-1.25F, -0.175636541F, -1.25F, 2.5F, 5.5F, 2.5F), PartPose.offsetAndRotation(-0.0F, 5.03237151F, -8.8817842e-16F, -0.0F, -0.0F, 0.0F));
        PartDefinition front_right_knee = front_right.addOrReplaceChild("front_right_knee", CubeListBuilder.create().texOffs(102, 61).addBox(-1.5F, 4.8426526F, -2.24794895F, 3.0F, 1.0F, 4.0F).texOffs(235, 61).addBox(-1.25F, -0.175636541F, -1.25F, 2.5F, 5.5F, 2.5F), PartPose.offsetAndRotation(-0.0F, 5.03237151F, -8.8817842e-16F, -0.0F, -0.0F, 0.0F));
        PartDefinition hind_left_knee = hind_left.addOrReplaceChild("hind_left_knee", CubeListBuilder.create().texOffs(130, 61).addBox(-1.5F, 4.8426526F, -2.24794895F, 3.0F, 1.0F, 4.0F).texOffs(1, 73).addBox(-1.25F, -0.175636541F, -1.25F, 2.5F, 5.5F, 2.5F), PartPose.offsetAndRotation(-0.0F, 5.03237151F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition hind_right_knee = hind_right.addOrReplaceChild("hind_right_knee", CubeListBuilder.create().texOffs(158, 61).addBox(-1.5F, 4.8426526F, -2.24794895F, 3.0F, 1.0F, 4.0F).texOffs(13, 73).addBox(-1.25F, -0.175636541F, -1.25F, 2.5F, 5.5F, 2.5F), PartPose.offsetAndRotation(-0.0F, 5.03237151F, 0.0F, -0.0F, -0.0F, 0.0F));
        // Logical UV dimensions from spec; physical PNG has2x texels, NOT doubled geometry.
        return LayerDefinition.create(mesh, 256, 256);
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
            throw new IllegalArgumentException("Nonfinite cow presentation");
        root.getAllParts().forEach(ModelPart::resetPose);
        gore.visible = false; // Independent source blood state is OFF by default, even Dead.
        if (dead) clip = "down_hold";
        if (clip != null && !clip.equals("rest")) {
            KeyframeAnimations.animate(this, AnimalCowAnimations.byName(clip),
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
