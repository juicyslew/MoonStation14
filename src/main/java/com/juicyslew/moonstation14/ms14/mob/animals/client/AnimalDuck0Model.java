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

/** Generated SS14 duck_0 asset rig. CC-BY-SA3.0 art; retain ATTRIBUTION.md.
 * Native1.21.1 API, based on pinned Blockbench4.12.6 geometry conventions.
 * NOT a registered entity/renderer/AI/networking implementation.
 */
public final class AnimalDuck0Model<T extends LivingEntity> extends HierarchicalModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
        ResourceLocation.fromNamespaceAndPath("moonstation14", "animals/duck_0"), "main");
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
    public AnimalDuck0Model(ModelPart root) {
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
        PartDefinition body = mouse.addOrReplaceChild("body", CubeListBuilder.create().texOffs(1, 1).addBox(-1.75F, 1.4527027F, -4.5F, 3.5F, 1.0F, 9.0F).texOffs(28, 1).addBox(-2.5F, 0.717567568F, -4.75F, 5.0F, 1.0F, 9.5F).texOffs(59, 1).addBox(-3.0F, -1.0F, -5.0F, 6.0F, 2.0F, 10.0F).texOffs(93, 1).addBox(-2.5F, -1.71756757F, -4.75F, 5.0F, 1.0F, 9.5F).texOffs(1, 15).addBox(-1.75F, -2.4527027F, -4.5F, 3.5F, 1.0F, 9.0F).texOffs(28, 15).addBox(-1.5F, 0.868243243F, 1.31278851F, 3.0F, 1.0F, 4.0F).texOffs(44, 15).addBox(-2.0F, 0.327702703F, 1.31278851F, 4.0F, 1.0F, 4.0F).texOffs(62, 15).addBox(-2.25F, -0.817567568F, 1.31278851F, 4.5F, 1.5F, 4.0F).texOffs(81, 15).addBox(-2.0F, -1.46283784F, 1.31278851F, 4.0F, 1.0F, 4.0F).texOffs(99, 15).addBox(-1.5F, -2.00337838F, 1.31278851F, 3.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(-0.0F, -4.28378378F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(115, 15).addBox(-1.25F, -0.128378378F, -1.25F, 2.5F, 3.5F, 2.5F).texOffs(1, 27).addBox(-1.5F, 0.800675676F, -2.00482989F, 3.0F, 1.0F, 3.5F).texOffs(16, 27).addBox(-2.0F, 0.260135135F, -2.00482989F, 4.0F, 1.0F, 3.5F).texOffs(33, 27).addBox(-2.25F, -0.885135135F, -2.25482989F, 4.5F, 1.5F, 4.0F).texOffs(52, 27).addBox(-2.0F, -1.53040541F, -2.00482989F, 4.0F, 1.0F, 3.5F).texOffs(69, 27).addBox(-1.5F, -2.07094595F, -2.00482989F, 3.0F, 1.0F, 3.5F).texOffs(84, 27).addBox(-1.5F, -0.0945945946F, -4.23942126F, 3.0F, 1.0F, 3.0F).texOffs(98, 27).addBox(-1.44076923F, -0.655405405F, -2.53721149F, 0.5F, 0.5F, 0.5F).texOffs(102, 27).addBox(0.940769231F, -0.655405405F, -2.53721149F, 0.5F, 0.5F, 0.5F).texOffs(106, 27).addBox(-1.25F, 1.64189189F, -1.25F, 2.5F, 0.5F, 2.5F), PartPose.offsetAndRotation(-0.0F, -4.05405405F, -3.31278851F, -0.0F, -0.0F, 0.0F));
        PartDefinition wing_left = body.addOrReplaceChild("wing_left", CubeListBuilder.create().texOffs(1, 35).addBox(-0.480769231F, 1.51689189F, -2.61775517F, 0.5F, 1.0F, 6.0F).texOffs(16, 35).addBox(-0.730769231F, 1.06283784F, -2.86775517F, 1.0F, 1.0F, 6.5F).texOffs(33, 35).addBox(-0.730769231F, 0.0608108108F, -3.11775517F, 1.0F, 1.5F, 7.0F).texOffs(51, 35).addBox(-0.730769231F, -0.441216216F, -2.86775517F, 1.0F, 1.0F, 6.5F).texOffs(68, 35).addBox(-0.480769231F, -0.89527027F, -2.61775517F, 0.5F, 1.0F, 6.0F), PartPose.offsetAndRotation(-2.38461538F, -0.945945946F, 0.50965977F, -0.0F, -0.0F, 0.0F));
        PartDefinition foot_left = body.addOrReplaceChild("foot_left", CubeListBuilder.create().texOffs(83, 35).addBox(-0.5F, -0.0743243243F, -0.5F, 1.0F, 1.5F, 1.0F).texOffs(89, 35).addBox(-1.0F, 0.986486486F, -1.63707471F, 2.0F, 1.0F, 2.0F), PartPose.offsetAndRotation(-1.15384615F, 2.2972973F, -0.764489655F, -0.0F, -0.0F, 0.0F));
        PartDefinition wing_right = body.addOrReplaceChild("wing_right", CubeListBuilder.create().texOffs(99, 35).addBox(-0.0192307692F, 1.51689189F, -2.61775517F, 0.5F, 1.0F, 6.0F).texOffs(1, 46).addBox(-0.269230769F, 1.06283784F, -2.86775517F, 1.0F, 1.0F, 6.5F).texOffs(18, 46).addBox(-0.269230769F, 0.0608108108F, -3.11775517F, 1.0F, 1.5F, 7.0F).texOffs(36, 46).addBox(-0.269230769F, -0.441216216F, -2.86775517F, 1.0F, 1.0F, 6.5F).texOffs(53, 46).addBox(-0.0192307692F, -0.89527027F, -2.61775517F, 0.5F, 1.0F, 6.0F), PartPose.offsetAndRotation(2.38461538F, -0.945945946F, 0.50965977F, -0.0F, -0.0F, 0.0F));
        PartDefinition foot_right = body.addOrReplaceChild("foot_right", CubeListBuilder.create().texOffs(68, 46).addBox(-0.5F, -0.0743243243F, -0.5F, 1.0F, 1.5F, 1.0F).texOffs(74, 46).addBox(-1.0F, 0.986486486F, -1.63707471F, 2.0F, 1.0F, 2.0F), PartPose.offsetAndRotation(1.15384615F, 2.2972973F, -0.764489655F, -0.0F, -0.0F, 0.0F));
        PartDefinition tail_0 = body.addOrReplaceChild("tail_0", CubeListBuilder.create().texOffs(84, 46).addBox(-0.75F, -0.75F, -0.261576054F, 1.5F, 1.5F, 1.5F), PartPose.offsetAndRotation(-0.0F, -0.675675676F, 4.84176782F, 0.261799388F, -0.20943951F, 0.0F));
        PartDefinition tail_1 = tail_0.addOrReplaceChild("tail_1", CubeListBuilder.create().texOffs(92, 46).addBox(-0.75F, -0.5F, -0.261576054F, 1.5F, 1.0F, 1.5F), PartPose.offsetAndRotation(-0.0F, 0.0F, 0.976847893F, 0.261799388F, 0.20943951F, 0.0F));
        PartDefinition tail_2 = tail_1.addOrReplaceChild("tail_2", CubeListBuilder.create().texOffs(100, 46).addBox(-0.5F, -0.5F, -0.261576054F, 1.0F, 1.0F, 1.5F), PartPose.offsetAndRotation(-0.0F, 0.0F, 0.976847893F, 0.261799388F, -0.20943951F, 0.0F));
        PartDefinition gore = mouse.addOrReplaceChild("gore", CubeListBuilder.create().texOffs(107, 46).addBox(-2.0F, -0.0540540541F, -1.59268678F, 4.0F, 0.0405405405F, 3.18537356F), PartPose.offsetAndRotation(-0.0F, 0.0F, 0.0F, -0.0F, -0.0F, 0.0F));
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
            throw new IllegalArgumentException("Nonfinite duck_0 presentation");
        root.getAllParts().forEach(ModelPart::resetPose);
        gore.visible = false; // Independent source blood state is OFF by default, even Dead.
        if (dead) clip = "down_hold";
        if (clip != null && !clip.equals("rest")) {
            KeyframeAnimations.animate(this, AnimalDuck0Animations.byName(clip),
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
