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

/** Generated SS14 spider_tarantula asset rig. CC-BY-SA3.0 art; retain ATTRIBUTION.md.
 * Native1.21.1 API, based on pinned Blockbench4.12.6 geometry conventions.
 * NOT a registered entity/renderer/AI/networking implementation.
 */
public final class AnimalSpiderTarantulaModel<T extends LivingEntity> extends HierarchicalModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
        ResourceLocation.fromNamespaceAndPath("moonstation14", "animals/spider_tarantula"), "main");
    private final ModelPart root;
    private final ModelPart mouse;
    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart leg_0_left;
    private final ModelPart leg_0_left_tip;
    private final ModelPart leg_0_right;
    private final ModelPart leg_0_right_tip;
    private final ModelPart leg_1_left;
    private final ModelPart leg_1_left_tip;
    private final ModelPart leg_1_right;
    private final ModelPart leg_1_right_tip;
    private final ModelPart leg_2_left;
    private final ModelPart leg_2_left_tip;
    private final ModelPart leg_2_right;
    private final ModelPart leg_2_right_tip;
    private final ModelPart leg_3_left;
    private final ModelPart leg_3_left_tip;
    private final ModelPart leg_3_right;
    private final ModelPart leg_3_right_tip;
    private final ModelPart gore;
    public AnimalSpiderTarantulaModel(ModelPart root) {
        this.root = root;
        this.mouse = root.getChild("mouse");
        this.body = this.mouse.getChild("body");
        this.head = this.body.getChild("head");
        this.leg_0_left = this.body.getChild("leg_0_left");
        this.leg_0_left_tip = this.leg_0_left.getChild("leg_0_left_tip");
        this.leg_0_right = this.body.getChild("leg_0_right");
        this.leg_0_right_tip = this.leg_0_right.getChild("leg_0_right_tip");
        this.leg_1_left = this.body.getChild("leg_1_left");
        this.leg_1_left_tip = this.leg_1_left.getChild("leg_1_left_tip");
        this.leg_1_right = this.body.getChild("leg_1_right");
        this.leg_1_right_tip = this.leg_1_right.getChild("leg_1_right_tip");
        this.leg_2_left = this.body.getChild("leg_2_left");
        this.leg_2_left_tip = this.leg_2_left.getChild("leg_2_left_tip");
        this.leg_2_right = this.body.getChild("leg_2_right");
        this.leg_2_right_tip = this.leg_2_right.getChild("leg_2_right_tip");
        this.leg_3_left = this.body.getChild("leg_3_left");
        this.leg_3_left_tip = this.leg_3_left.getChild("leg_3_left_tip");
        this.leg_3_right = this.body.getChild("leg_3_right");
        this.leg_3_right_tip = this.leg_3_right.getChild("leg_3_right_tip");
        this.gore = this.mouse.getChild("gore");
        this.gore.visible = false;
    }
    @Override public ModelPart root() { return root; }
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition base = mesh.getRoot();
        PartDefinition mouse = base.addOrReplaceChild("mouse", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 24.0F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition body = mouse.addOrReplaceChild("body", CubeListBuilder.create().texOffs(1, 1).addBox(-11.0F, -3.82128519F, -4.58518449F, 22.0F, 7.5F, 16.5F).texOffs(1, 27).addBox(-8.25F, -4.93401882F, -2.83518449F, 16.5F, 2.5F, 13.0F).texOffs(62, 27).addBox(-8.25F, 1.92348325F, -2.33518449F, 16.5F, 9.0F, 12.0F), PartPose.offsetAndRotation(-0.0F, -17.4689814F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(1, 50).addBox(-8.25F, -3.75F, -4.25F, 16.5F, 7.5F, 8.5F).texOffs(53, 50).addBox(1.73953207F, 1.40843591F, -6.72627423F, 2.0F, 3.0F, 2.0F).texOffs(63, 50).addBox(-3.73953207F, 1.40843591F, -6.72627423F, 2.0F, 3.0F, 2.0F).texOffs(112, 68).addBox(-3.14F, -2.525F, -4.66F, 1.0F, 1.0F, 0.5F).texOffs(117, 68).addBox(-6.275F, -2.525F, -4.66F, 1.0F, 1.0F, 0.5F).texOffs(122, 68).addBox(-3.14F, -0.425F, -4.66F, 1.0F, 1.0F, 0.5F).texOffs(1, 84).addBox(-6.275F, -0.425F, -4.66F, 1.0F, 1.0F, 0.5F).texOffs(6, 84).addBox(2.14F, -2.525F, -4.66F, 1.0F, 1.0F, 0.5F).texOffs(11, 84).addBox(5.275F, -2.525F, -4.66F, 1.0F, 1.0F, 0.5F).texOffs(16, 84).addBox(2.14F, -0.425F, -4.66F, 1.0F, 1.0F, 0.5F).texOffs(21, 84).addBox(5.275F, -0.425F, -4.66F, 1.0F, 1.0F, 0.5F), PartPose.offsetAndRotation(-0.0F, 0.0F, -8.24583489F, -0.0F, -0.0F, 0.0F));
        PartDefinition leg_0_left = body.addOrReplaceChild("leg_0_left", CubeListBuilder.create().texOffs(73, 50).addBox(-0.75F, -0.222888369F, -0.75F, 1.5F, 9.0F, 1.5F), PartPose.offsetAndRotation(-8.17946004F, 0.969478636F, -6.87152907F, -0.0F, 0.314159265F, 0.959931089F));
        PartDefinition leg_0_left_tip = leg_0_left.addOrReplaceChild("leg_0_left_tip", CubeListBuilder.create().texOffs(81, 50).addBox(-0.75F, -0.215232089F, -0.75F, 1.5F, 12.0F, 1.5F), PartPose.offsetAndRotation(-0.0F, 8.55422326F, 0.0F, -0.0F, -0.0F, -1.22173048F));
        PartDefinition leg_0_right = body.addOrReplaceChild("leg_0_right", CubeListBuilder.create().texOffs(89, 50).addBox(-0.75F, -0.222888369F, -0.75F, 1.5F, 9.0F, 1.5F), PartPose.offsetAndRotation(8.17946004F, 0.969478636F, -6.87152907F, -0.0F, 0.314159265F, -0.959931089F));
        PartDefinition leg_0_right_tip = leg_0_right.addOrReplaceChild("leg_0_right_tip", CubeListBuilder.create().texOffs(97, 50).addBox(-0.75F, -0.215232089F, -0.75F, 1.5F, 12.0F, 1.5F), PartPose.offsetAndRotation(-0.0F, 8.55422326F, 0.0F, -0.0F, -0.0F, 1.22173048F));
        PartDefinition leg_1_left = body.addOrReplaceChild("leg_1_left", CubeListBuilder.create().texOffs(105, 50).addBox(-0.75F, -0.222888369F, -0.75F, 1.5F, 9.0F, 1.5F), PartPose.offsetAndRotation(-8.17946004F, 0.969478636F, -1.90875808F, -0.0F, 0.104719755F, 0.959931089F));
        PartDefinition leg_1_left_tip = leg_1_left.addOrReplaceChild("leg_1_left_tip", CubeListBuilder.create().texOffs(113, 50).addBox(-0.75F, -0.215232089F, -0.75F, 1.5F, 12.0F, 1.5F), PartPose.offsetAndRotation(-0.0F, 8.55422326F, 0.0F, -0.0F, -0.0F, -1.22173048F));
        PartDefinition leg_1_right = body.addOrReplaceChild("leg_1_right", CubeListBuilder.create().texOffs(121, 50).addBox(-0.75F, -0.222888369F, -0.75F, 1.5F, 9.0F, 1.5F), PartPose.offsetAndRotation(8.17946004F, 0.969478636F, -1.90875808F, -0.0F, 0.104719755F, -0.959931089F));
        PartDefinition leg_1_right_tip = leg_1_right.addOrReplaceChild("leg_1_right_tip", CubeListBuilder.create().texOffs(1, 68).addBox(-0.75F, -0.215232089F, -0.75F, 1.5F, 12.0F, 1.5F), PartPose.offsetAndRotation(-0.0F, 8.55422326F, 0.0F, -0.0F, -0.0F, 1.22173048F));
        PartDefinition leg_2_left = body.addOrReplaceChild("leg_2_left", CubeListBuilder.create().texOffs(9, 68).addBox(-0.75F, -0.222888369F, -0.75F, 1.5F, 9.0F, 1.5F), PartPose.offsetAndRotation(-8.17946004F, 0.969478636F, 3.05401292F, -0.0F, -0.104719755F, 0.959931089F));
        PartDefinition leg_2_left_tip = leg_2_left.addOrReplaceChild("leg_2_left_tip", CubeListBuilder.create().texOffs(17, 68).addBox(-0.75F, -0.215232089F, -0.75F, 1.5F, 12.0F, 1.5F), PartPose.offsetAndRotation(-0.0F, 8.55422326F, 0.0F, -0.0F, -0.0F, -1.22173048F));
        PartDefinition leg_2_right = body.addOrReplaceChild("leg_2_right", CubeListBuilder.create().texOffs(25, 68).addBox(-0.75F, -0.222888369F, -0.75F, 1.5F, 9.0F, 1.5F), PartPose.offsetAndRotation(8.17946004F, 0.969478636F, 3.05401292F, -0.0F, -0.104719755F, -0.959931089F));
        PartDefinition leg_2_right_tip = leg_2_right.addOrReplaceChild("leg_2_right_tip", CubeListBuilder.create().texOffs(33, 68).addBox(-0.75F, -0.215232089F, -0.75F, 1.5F, 12.0F, 1.5F), PartPose.offsetAndRotation(-0.0F, 8.55422326F, 0.0F, -0.0F, -0.0F, 1.22173048F));
        PartDefinition leg_3_left = body.addOrReplaceChild("leg_3_left", CubeListBuilder.create().texOffs(41, 68).addBox(-0.75F, -0.222888369F, -0.75F, 1.5F, 9.0F, 1.5F), PartPose.offsetAndRotation(-8.17946004F, 0.969478636F, 8.01678392F, -0.0F, -0.314159265F, 0.959931089F));
        PartDefinition leg_3_left_tip = leg_3_left.addOrReplaceChild("leg_3_left_tip", CubeListBuilder.create().texOffs(49, 68).addBox(-0.75F, -0.215232089F, -0.75F, 1.5F, 12.0F, 1.5F), PartPose.offsetAndRotation(-0.0F, 8.55422326F, 0.0F, -0.0F, -0.0F, -1.22173048F));
        PartDefinition leg_3_right = body.addOrReplaceChild("leg_3_right", CubeListBuilder.create().texOffs(57, 68).addBox(-0.75F, -0.222888369F, -0.75F, 1.5F, 9.0F, 1.5F), PartPose.offsetAndRotation(8.17946004F, 0.969478636F, 8.01678392F, -0.0F, -0.314159265F, -0.959931089F));
        PartDefinition leg_3_right_tip = leg_3_right.addOrReplaceChild("leg_3_right_tip", CubeListBuilder.create().texOffs(65, 68).addBox(-0.75F, -0.215232089F, -0.75F, 1.5F, 12.0F, 1.5F), PartPose.offsetAndRotation(-0.0F, 8.55422326F, 0.0F, -0.0F, -0.0F, 1.22173048F));
        PartDefinition gore = mouse.addOrReplaceChild("gore", CubeListBuilder.create().texOffs(73, 68).addBox(-5.08770242F, -0.11405631F, -4.09019588F, 10.1754048F, 0.0855422326F, 8.18039175F), PartPose.offsetAndRotation(-0.0F, 0.0F, 0.0F, -0.0F, -0.0F, 0.0F));
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
            throw new IllegalArgumentException("Nonfinite spider_tarantula presentation");
        root.getAllParts().forEach(ModelPart::resetPose);
        gore.visible = false; // Independent source blood state is OFF by default, even Dead.
        if (dead) clip = "down_hold";
        if (clip != null && !clip.equals("rest")) {
            KeyframeAnimations.animate(this, AnimalSpiderTarantulaAnimations.byName(clip),
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
