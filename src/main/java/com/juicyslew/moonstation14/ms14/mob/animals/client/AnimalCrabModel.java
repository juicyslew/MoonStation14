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

/** Generated SS14 crab asset rig. CC-BY-SA3.0 art; retain ATTRIBUTION.md.
 * Native1.21.1 API, based on pinned Blockbench4.12.6 geometry conventions.
 * NOT a registered entity/renderer/AI/networking implementation.
 */
public final class AnimalCrabModel<T extends LivingEntity> extends HierarchicalModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
        ResourceLocation.fromNamespaceAndPath("moonstation14", "animals/crab"), "main");
    private final ModelPart root;
    private final ModelPart mouse;
    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart claw_left;
    private final ModelPart claw_right;
    private final ModelPart gore;
    private final ModelPart leg_0_left;
    private final ModelPart leg_0_left_upper_joint;
    private final ModelPart leg_0_left_lower_joint;
    private final ModelPart leg_0_right;
    private final ModelPart leg_0_right_upper_joint;
    private final ModelPart leg_0_right_lower_joint;
    private final ModelPart leg_1_left;
    private final ModelPart leg_1_left_upper_joint;
    private final ModelPart leg_1_left_lower_joint;
    private final ModelPart leg_1_right;
    private final ModelPart leg_1_right_upper_joint;
    private final ModelPart leg_1_right_lower_joint;
    private final ModelPart leg_2_left;
    private final ModelPart leg_2_left_upper_joint;
    private final ModelPart leg_2_left_lower_joint;
    private final ModelPart leg_2_right;
    private final ModelPart leg_2_right_upper_joint;
    private final ModelPart leg_2_right_lower_joint;
    private final ModelPart leg_3_left;
    private final ModelPart leg_3_left_upper_joint;
    private final ModelPart leg_3_left_lower_joint;
    private final ModelPart leg_3_right;
    private final ModelPart leg_3_right_upper_joint;
    private final ModelPart leg_3_right_lower_joint;
    public AnimalCrabModel(ModelPart root) {
        this.root = root;
        this.mouse = root.getChild("mouse");
        this.body = this.mouse.getChild("body");
        this.head = this.body.getChild("head");
        this.claw_left = this.body.getChild("claw_left");
        this.claw_right = this.body.getChild("claw_right");
        this.gore = this.mouse.getChild("gore");
        this.leg_0_left = this.body.getChild("leg_0_left");
        this.leg_0_left_upper_joint = this.leg_0_left.getChild("leg_0_left_upper_joint");
        this.leg_0_left_lower_joint = this.leg_0_left.getChild("leg_0_left_lower_joint");
        this.leg_0_right = this.body.getChild("leg_0_right");
        this.leg_0_right_upper_joint = this.leg_0_right.getChild("leg_0_right_upper_joint");
        this.leg_0_right_lower_joint = this.leg_0_right.getChild("leg_0_right_lower_joint");
        this.leg_1_left = this.body.getChild("leg_1_left");
        this.leg_1_left_upper_joint = this.leg_1_left.getChild("leg_1_left_upper_joint");
        this.leg_1_left_lower_joint = this.leg_1_left.getChild("leg_1_left_lower_joint");
        this.leg_1_right = this.body.getChild("leg_1_right");
        this.leg_1_right_upper_joint = this.leg_1_right.getChild("leg_1_right_upper_joint");
        this.leg_1_right_lower_joint = this.leg_1_right.getChild("leg_1_right_lower_joint");
        this.leg_2_left = this.body.getChild("leg_2_left");
        this.leg_2_left_upper_joint = this.leg_2_left.getChild("leg_2_left_upper_joint");
        this.leg_2_left_lower_joint = this.leg_2_left.getChild("leg_2_left_lower_joint");
        this.leg_2_right = this.body.getChild("leg_2_right");
        this.leg_2_right_upper_joint = this.leg_2_right.getChild("leg_2_right_upper_joint");
        this.leg_2_right_lower_joint = this.leg_2_right.getChild("leg_2_right_lower_joint");
        this.leg_3_left = this.body.getChild("leg_3_left");
        this.leg_3_left_upper_joint = this.leg_3_left.getChild("leg_3_left_upper_joint");
        this.leg_3_left_lower_joint = this.leg_3_left.getChild("leg_3_left_lower_joint");
        this.leg_3_right = this.body.getChild("leg_3_right");
        this.leg_3_right_upper_joint = this.leg_3_right.getChild("leg_3_right_upper_joint");
        this.leg_3_right_lower_joint = this.leg_3_right.getChild("leg_3_right_lower_joint");
        this.gore.visible = false;
    }
    @Override public ModelPart root() { return root; }
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition base = mesh.getRoot();
        PartDefinition mouse = base.addOrReplaceChild("mouse", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 24.0F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition body = mouse.addOrReplaceChild("body", CubeListBuilder.create().texOffs(1, 1).addBox(-6.5F, -1.5F, -2.22597677F, 13.0F, 3.0F, 8.0F).texOffs(45, 1).addBox(-5.0F, -2.51838095F, -1.47597677F, 10.0F, 1.5F, 6.5F).texOffs(80, 1).addBox(-5.0F, 0.925308273F, -1.22597677F, 10.0F, 1.5F, 6.0F), PartPose.offsetAndRotation(-0.0F, -3.34514189F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(1, 14).addBox(-5.0F, -1.75F, -2.0F, 10.0F, 3.5F, 4.0F).texOffs(31, 14).addBox(-3.30851064F, -4.50754386F, -0.876715945F, 1.0F, 2.5F, 1.5F).texOffs(38, 14).addBox(-3.55851064F, -4.68827068F, -1.12671595F, 1.5F, 1.0F, 2.0F).texOffs(88, 14).addBox(2.30851064F, -4.50754386F, -0.876715945F, 1.0F, 2.5F, 1.5F).texOffs(95, 14).addBox(2.05851064F, -4.68827068F, -1.12671595F, 1.5F, 1.0F, 2.0F), PartPose.offsetAndRotation(-0.0F, 0.0F, -3.99155227F, -0.0F, -0.0F, 0.0F));
        PartDefinition claw_left = body.addOrReplaceChild("claw_left", CubeListBuilder.create().texOffs(47, 14).addBox(-5.81382979F, -1.25F, -4.65073918F, 5.5F, 2.5F, 5.5F).texOffs(71, 14).addBox(-5.21276596F, -0.75F, -7.50184794F, 2.0F, 1.5F, 5.5F), PartPose.offsetAndRotation(-6.38297872F, 0.0F, -1.90073918F, -0.0F, -0.0F, 0.0F));
        PartDefinition claw_right = body.addOrReplaceChild("claw_right", CubeListBuilder.create().texOffs(104, 14).addBox(0.313829787F, -1.25F, -4.65073918F, 5.5F, 2.5F, 5.5F).texOffs(1, 24).addBox(3.21276596F, -0.75F, -7.50184794F, 2.0F, 1.5F, 5.5F), PartPose.offsetAndRotation(6.38297872F, 0.0F, -1.90073918F, -0.0F, -0.0F, 0.0F));
        PartDefinition gore = mouse.addOrReplaceChild("gore", CubeListBuilder.create().texOffs(18, 24).addBox(-3.31914894F, -0.0744581455F, -3.95987328F, 6.63829787F, 0.0558436091F, 7.91974657F), PartPose.offsetAndRotation(-0.0F, 0.0F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition leg_0_left = body.addOrReplaceChild("leg_0_left", CubeListBuilder.create(), PartPose.offsetAndRotation(-5.6F, 0.247099917F, -2.8F, -0.0F, -0.0F, 0.0F));
        PartDefinition leg_0_left_upper_joint = leg_0_left.addOrReplaceChild("leg_0_left_upper_joint", CubeListBuilder.create().texOffs(50, 24).addBox(-0.5F, -0.5F, -2.25F, 1.0F, 1.0F, 4.5F), PartPose.offsetAndRotation(-1.85F, 0.6F, -0.3375F, -0.30884857F, -1.75124436F, 0.0F));
        PartDefinition leg_0_left_lower_joint = leg_0_left.addOrReplaceChild("leg_0_left_lower_joint", CubeListBuilder.create().texOffs(63, 24).addBox(-0.5F, -0.5F, -1.25F, 1.0F, 1.0F, 2.5F), PartPose.offsetAndRotation(-4.6F, 1.925F, -0.825F, -0.671436575F, -1.735945F, 0.0F));
        PartDefinition leg_0_right = body.addOrReplaceChild("leg_0_right", CubeListBuilder.create(), PartPose.offsetAndRotation(5.6F, 0.247099917F, -2.8F, -0.0F, -0.0F, 0.0F));
        PartDefinition leg_0_right_upper_joint = leg_0_right.addOrReplaceChild("leg_0_right_upper_joint", CubeListBuilder.create().texOffs(72, 24).addBox(-0.5F, -0.5F, -2.25F, 1.0F, 1.0F, 4.5F), PartPose.offsetAndRotation(1.85F, 0.6F, -0.3375F, -0.30884857F, 1.75124436F, 0.0F));
        PartDefinition leg_0_right_lower_joint = leg_0_right.addOrReplaceChild("leg_0_right_lower_joint", CubeListBuilder.create().texOffs(85, 24).addBox(-0.5F, -0.5F, -1.25F, 1.0F, 1.0F, 2.5F), PartPose.offsetAndRotation(4.6F, 1.925F, -0.825F, -0.671436575F, 1.735945F, 0.0F));
        PartDefinition leg_1_left = body.addOrReplaceChild("leg_1_left", CubeListBuilder.create(), PartPose.offsetAndRotation(-5.6F, 0.247099917F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition leg_1_left_upper_joint = leg_1_left.addOrReplaceChild("leg_1_left_upper_joint", CubeListBuilder.create().texOffs(94, 24).addBox(-0.5F, -0.5F, -2.25F, 1.0F, 1.0F, 4.5F), PartPose.offsetAndRotation(-1.85F, 0.6F, -0.1125F, -0.313079463F, -1.63153234F, 0.0F));
        PartDefinition leg_1_left_lower_joint = leg_1_left.addOrReplaceChild("leg_1_left_lower_joint", CubeListBuilder.create().texOffs(107, 24).addBox(-0.5F, -0.5F, -1.25F, 1.0F, 1.0F, 2.5F), PartPose.offsetAndRotation(-4.6F, 1.925F, -0.275F, -0.67736668F, -1.62629483F, 0.0F));
        PartDefinition leg_1_right = body.addOrReplaceChild("leg_1_right", CubeListBuilder.create(), PartPose.offsetAndRotation(5.6F, 0.247099917F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition leg_1_right_upper_joint = leg_1_right.addOrReplaceChild("leg_1_right_upper_joint", CubeListBuilder.create().texOffs(116, 24).addBox(-0.5F, -0.5F, -2.25F, 1.0F, 1.0F, 4.5F), PartPose.offsetAndRotation(1.85F, 0.6F, -0.1125F, -0.313079463F, 1.63153234F, 0.0F));
        PartDefinition leg_1_right_lower_joint = leg_1_right.addOrReplaceChild("leg_1_right_lower_joint", CubeListBuilder.create().texOffs(1, 34).addBox(-0.5F, -0.5F, -1.25F, 1.0F, 1.0F, 2.5F), PartPose.offsetAndRotation(4.6F, 1.925F, -0.275F, -0.67736668F, 1.62629483F, 0.0F));
        PartDefinition leg_2_left = body.addOrReplaceChild("leg_2_left", CubeListBuilder.create(), PartPose.offsetAndRotation(-5.6F, 0.247099917F, 2.8F, -0.0F, -0.0F, 0.0F));
        PartDefinition leg_2_left_upper_joint = leg_2_left.addOrReplaceChild("leg_2_left_upper_joint", CubeListBuilder.create().texOffs(10, 34).addBox(-0.5F, -0.5F, -2.25F, 1.0F, 1.0F, 4.5F), PartPose.offsetAndRotation(-1.85F, 0.6F, 0.1125F, -0.313079463F, -1.51006031F, 0.0F));
        PartDefinition leg_2_left_lower_joint = leg_2_left.addOrReplaceChild("leg_2_left_lower_joint", CubeListBuilder.create().texOffs(23, 34).addBox(-0.5F, -0.5F, -1.25F, 1.0F, 1.0F, 2.5F), PartPose.offsetAndRotation(-4.6F, 1.925F, 0.275F, -0.67736668F, -1.51529782F, 0.0F));
        PartDefinition leg_2_right = body.addOrReplaceChild("leg_2_right", CubeListBuilder.create(), PartPose.offsetAndRotation(5.6F, 0.247099917F, 2.8F, -0.0F, -0.0F, 0.0F));
        PartDefinition leg_2_right_upper_joint = leg_2_right.addOrReplaceChild("leg_2_right_upper_joint", CubeListBuilder.create().texOffs(32, 34).addBox(-0.5F, -0.5F, -2.25F, 1.0F, 1.0F, 4.5F), PartPose.offsetAndRotation(1.85F, 0.6F, 0.1125F, -0.313079463F, 1.51006031F, 0.0F));
        PartDefinition leg_2_right_lower_joint = leg_2_right.addOrReplaceChild("leg_2_right_lower_joint", CubeListBuilder.create().texOffs(45, 34).addBox(-0.5F, -0.5F, -1.25F, 1.0F, 1.0F, 2.5F), PartPose.offsetAndRotation(4.6F, 1.925F, 0.275F, -0.67736668F, 1.51529782F, 0.0F));
        PartDefinition leg_3_left = body.addOrReplaceChild("leg_3_left", CubeListBuilder.create(), PartPose.offsetAndRotation(-5.6F, 0.247099917F, 5.6F, -0.0F, -0.0F, 0.0F));
        PartDefinition leg_3_left_upper_joint = leg_3_left.addOrReplaceChild("leg_3_left_upper_joint", CubeListBuilder.create().texOffs(54, 34).addBox(-0.5F, -0.5F, -2.25F, 1.0F, 1.0F, 4.5F), PartPose.offsetAndRotation(-1.85F, 0.6F, 0.3375F, -0.30884857F, -1.3903483F, 0.0F));
        PartDefinition leg_3_left_lower_joint = leg_3_left.addOrReplaceChild("leg_3_left_lower_joint", CubeListBuilder.create().texOffs(67, 34).addBox(-0.5F, -0.5F, -1.25F, 1.0F, 1.0F, 2.5F), PartPose.offsetAndRotation(-4.6F, 1.925F, 0.825F, -0.671436575F, -1.40564765F, 0.0F));
        PartDefinition leg_3_right = body.addOrReplaceChild("leg_3_right", CubeListBuilder.create(), PartPose.offsetAndRotation(5.6F, 0.247099917F, 5.6F, -0.0F, -0.0F, 0.0F));
        PartDefinition leg_3_right_upper_joint = leg_3_right.addOrReplaceChild("leg_3_right_upper_joint", CubeListBuilder.create().texOffs(76, 34).addBox(-0.5F, -0.5F, -2.25F, 1.0F, 1.0F, 4.5F), PartPose.offsetAndRotation(1.85F, 0.6F, 0.3375F, -0.30884857F, 1.3903483F, 0.0F));
        PartDefinition leg_3_right_lower_joint = leg_3_right.addOrReplaceChild("leg_3_right_lower_joint", CubeListBuilder.create().texOffs(89, 34).addBox(-0.5F, -0.5F, -1.25F, 1.0F, 1.0F, 2.5F), PartPose.offsetAndRotation(4.6F, 1.925F, 0.825F, -0.671436575F, 1.40564765F, 0.0F));
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
            throw new IllegalArgumentException("Nonfinite crab presentation");
        root.getAllParts().forEach(ModelPart::resetPose);
        gore.visible = false; // Independent source blood state is OFF by default, even Dead.
        if (dead) clip = "down_hold";
        if (clip != null && !clip.equals("rest")) {
            KeyframeAnimations.animate(this, AnimalCrabAnimations.byName(clip),
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
