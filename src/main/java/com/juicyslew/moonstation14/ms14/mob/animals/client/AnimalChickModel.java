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

/** Generated SS14 chick asset rig. CC-BY-SA3.0 art; retain ATTRIBUTION.md.
 * Native1.21.1 API, based on pinned Blockbench4.12.6 geometry conventions.
 * NOT a registered entity/renderer/AI/networking implementation.
 */
public final class AnimalChickModel<T extends LivingEntity> extends HierarchicalModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
        ResourceLocation.fromNamespaceAndPath("moonstation14", "animals/chick"), "main");
    private final ModelPart root;
    private final ModelPart mouse;
    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart wing_left;
    private final ModelPart foot_left;
    private final ModelPart wing_right;
    private final ModelPart foot_right;
    private final ModelPart tail_0;
    private final ModelPart tail_1;
    private final ModelPart tail_2;
    private final ModelPart gore;
    public AnimalChickModel(ModelPart root) {
        this.root = root;
        this.mouse = root.getChild("mouse");
        this.body = this.mouse.getChild("body");
        this.head = this.body.getChild("head");
        this.wing_left = this.body.getChild("wing_left");
        this.foot_left = this.body.getChild("foot_left");
        this.wing_right = this.body.getChild("wing_right");
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
        PartDefinition body = mouse.addOrReplaceChild("body", CubeListBuilder.create().texOffs(1, 1).addBox(-2.0F, -1.25F, -1.5F, 4.0F, 2.5F, 3.0F).texOffs(17, 1).addBox(-1.5F, -1.85334963F, -1.25F, 3.0F, 1.0F, 2.5F).texOffs(30, 1).addBox(-1.5F, 0.853349633F, -1.25F, 3.0F, 1.0F, 2.5F).texOffs(43, 1).addBox(-1.5F, -1.0F, -1.30463226F, 3.0F, 2.0F, 1.0F).texOffs(53, 1).addBox(-1.25F, -1.67741418F, -1.30463226F, 2.5F, 1.0F, 1.0F).texOffs(1, 9).addBox(-1.25F, 0.677414181F, -1.30463226F, 2.5F, 1.0F, 1.0F), PartPose.offsetAndRotation(-0.0F, -2.74584352F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(10, 9).addBox(-0.75F, -0.0722493888F, -0.5F, 1.5F, 1.5F, 1.0F).texOffs(17, 9).addBox(-1.5F, -0.75F, -1.0F, 3.0F, 1.5F, 2.0F).texOffs(29, 9).addBox(-1.25F, -1.30603912F, -0.75F, 2.5F, 1.0F, 1.5F).texOffs(39, 9).addBox(-1.25F, 0.30603912F, -0.75F, 2.5F, 1.0F, 1.5F).texOffs(49, 9).addBox(-0.75F, -0.284841076F, -1.68389903F, 1.5F, 1.0F, 1.0F).texOffs(56, 9).addBox(-1.39723077F, -0.443643032F, -1.14054352F, 1.0F, 0.5F, 0.5F).texOffs(1, 15).addBox(0.397230769F, -0.443643032F, -1.14054352F, 1.0F, 0.5F, 0.5F), PartPose.offsetAndRotation(-0.0F, -1.72127139F, -0.955500809F, -0.0F, -0.0F, 0.0F));
        PartDefinition wing_left = body.addOrReplaceChild("wing_left", CubeListBuilder.create().texOffs(7, 15).addBox(-0.724307692F, 0.274156479F, -0.706644489F, 1.0F, 1.5F, 2.0F).texOffs(15, 15).addBox(-0.724307692F, -0.355520782F, -0.706644489F, 1.0F, 1.0F, 2.0F).texOffs(23, 15).addBox(-0.724307692F, 1.40383374F, -0.706644489F, 1.0F, 1.0F, 2.0F), PartPose.offsetAndRotation(-1.64492308F, -0.841271394F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition foot_left = body.addOrReplaceChild("foot_left", CubeListBuilder.create().texOffs(31, 15).addBox(-0.5F, -0.112713936F, -0.25F, 1.0F, 1.0F, 0.5F).texOffs(36, 15).addBox(-0.75F, 0.416992665F, -1.1690793F, 1.5F, 0.5F, 1.5F), PartPose.offsetAndRotation(-0.897230769F, 1.82885086F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition wing_right = body.addOrReplaceChild("wing_right", CubeListBuilder.create().texOffs(44, 15).addBox(-0.275692308F, 0.274156479F, -0.706644489F, 1.0F, 1.5F, 2.0F).texOffs(52, 15).addBox(-0.275692308F, -0.355520782F, -0.706644489F, 1.0F, 1.0F, 2.0F).texOffs(1, 21).addBox(-0.275692308F, 1.40383374F, -0.706644489F, 1.0F, 1.0F, 2.0F), PartPose.offsetAndRotation(1.64492308F, -0.841271394F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition foot_right = body.addOrReplaceChild("foot_right", CubeListBuilder.create().texOffs(9, 21).addBox(-0.5F, -0.112713936F, -0.25F, 1.0F, 1.0F, 0.5F).texOffs(14, 21).addBox(-0.75F, 0.416992665F, -1.1690793F, 1.5F, 0.5F, 1.5F), PartPose.offsetAndRotation(0.897230769F, 1.82885086F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition tail_0 = body.addOrReplaceChild("tail_0", CubeListBuilder.create().texOffs(22, 21).addBox(-0.75F, -0.5F, -0.108859318F, 1.5F, 1.0F, 1.0F), PartPose.offsetAndRotation(-0.0F, -0.475501222F, 1.25723791F, 0.261799388F, -0.20943951F, 0.0F));
        PartDefinition tail_1 = tail_0.addOrReplaceChild("tail_1", CubeListBuilder.create().texOffs(29, 21).addBox(-0.5F, -0.5F, -0.108859318F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(-0.0F, 0.0F, 0.782281364F, 0.261799388F, 0.20943951F, 0.0F));
        PartDefinition tail_2 = tail_1.addOrReplaceChild("tail_2", CubeListBuilder.create().texOffs(35, 21).addBox(-0.5F, -0.5F, -0.108859318F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(-0.0F, 0.0F, 0.782281364F, 0.261799388F, -0.20943951F, 0.0F));
        PartDefinition gore = mouse.addOrReplaceChild("gore", CubeListBuilder.create().texOffs(41, 21).addBox(-1.8F, -0.0430317848F, -1.04769826F, 3.6F, 0.0322738386F, 2.09539651F), PartPose.offsetAndRotation(-0.0F, 0.0F, 0.0F, -0.0F, -0.0F, 0.0F));
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
            throw new IllegalArgumentException("Nonfinite chick presentation");
        root.getAllParts().forEach(ModelPart::resetPose);
        gore.visible = false; // Independent source blood state is OFF by default, even Dead.
        if (dead) clip = "down_hold";
        if (clip != null && !clip.equals("rest")) {
            KeyframeAnimations.animate(this, AnimalChickAnimations.byName(clip),
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
