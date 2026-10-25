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

/** Generated SS14 cockroach asset rig. CC-BY-SA3.0 art; retain ATTRIBUTION.md.
 * Native1.21.1 API, based on pinned Blockbench4.12.6 geometry conventions.
 * NOT a registered entity/renderer/AI/networking implementation.
 */
public final class AnimalCockroachModel<T extends LivingEntity> extends HierarchicalModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
        ResourceLocation.fromNamespaceAndPath("moonstation14", "animals/cockroach"), "main");
    private final ModelPart root;
    private final ModelPart mouse;
    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart antenna_left;
    private final ModelPart antenna_right;
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
    private final ModelPart gore;
    public AnimalCockroachModel(ModelPart root) {
        this.root = root;
        this.mouse = root.getChild("mouse");
        this.body = this.mouse.getChild("body");
        this.head = this.body.getChild("head");
        this.antenna_left = this.head.getChild("antenna_left");
        this.antenna_right = this.head.getChild("antenna_right");
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
        this.gore = this.mouse.getChild("gore");
        this.gore.visible = false;
    }
    @Override public ModelPart root() { return root; }
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition base = mesh.getRoot();
        PartDefinition mouse = base.addOrReplaceChild("mouse", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 24.0F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition body = mouse.addOrReplaceChild("body", CubeListBuilder.create().texOffs(1, 1).addBox(-3.0F, -0.75F, -2.78034682F, 6.0F, 1.5F, 10.0F).texOffs(35, 1).addBox(-2.25F, -0.883572921F, -1.78034682F, 4.5F, 0.5F, 8.0F).texOffs(1, 15).addBox(-2.25F, 0.350226978F, -1.53034682F, 4.5F, 0.5F, 7.5F), PartPose.offsetAndRotation(-0.0F, -3.12771687F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(27, 15).addBox(-2.25F, -0.75F, -2.5F, 4.5F, 1.5F, 5.0F).texOffs(48, 15).addBox(-1.86227221F, -0.666729716F, -2.99710983F, 1.0F, 1.0F, 1.0F).texOffs(54, 15).addBox(0.862272207F, -0.666729716F, -2.99710983F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(-0.0F, 0.0F, -4.99421965F, -0.0F, -0.0F, 0.0F));
        PartDefinition antenna_left = head.addOrReplaceChild("antenna_left", CubeListBuilder.create().texOffs(60, 15).addBox(-0.25F, -1.66691886F, -0.5F, 0.5F, 2.0F, 1.0F), PartPose.offsetAndRotation(-1.41021968F, -0.500189148F, -0.554913295F, -0.436332313F, -0.0F, -0.436332313F));
        PartDefinition antenna_right = head.addOrReplaceChild("antenna_right", CubeListBuilder.create().texOffs(1, 25).addBox(-0.25F, -1.66691886F, -0.5F, 0.5F, 2.0F, 1.0F), PartPose.offsetAndRotation(1.41021968F, -0.500189148F, -0.554913295F, -0.436332313F, -0.0F, 0.436332313F));
        PartDefinition leg_0_left = body.addOrReplaceChild("leg_0_left", CubeListBuilder.create().texOffs(6, 25).addBox(-0.5F, -0.0921547489F, -0.5F, 1.0F, 2.5F, 1.0F), PartPose.offsetAndRotation(-2.2507106F, 0.166729716F, -4.16184971F, -0.0F, 0.20943951F, 0.959931089F));
        PartDefinition leg_0_left_tip = leg_0_left.addOrReplaceChild("leg_0_left_tip", CubeListBuilder.create().texOffs(12, 25).addBox(-0.5F, -0.00897903927F, -0.5F, 1.0F, 1.5F, 1.0F), PartPose.offsetAndRotation(-0.0F, 2.3156905F, 0.0F, -0.0F, -0.0F, -1.22173048F));
        PartDefinition leg_0_right = body.addOrReplaceChild("leg_0_right", CubeListBuilder.create().texOffs(18, 25).addBox(-0.5F, -0.0921547489F, -0.5F, 1.0F, 2.5F, 1.0F), PartPose.offsetAndRotation(2.2507106F, 0.166729716F, -4.16184971F, -0.0F, 0.20943951F, -0.959931089F));
        PartDefinition leg_0_right_tip = leg_0_right.addOrReplaceChild("leg_0_right_tip", CubeListBuilder.create().texOffs(24, 25).addBox(-0.5F, -0.00897903927F, -0.5F, 1.0F, 1.5F, 1.0F), PartPose.offsetAndRotation(-0.0F, 2.3156905F, 0.0F, -0.0F, -0.0F, 1.22173048F));
        PartDefinition leg_1_left = body.addOrReplaceChild("leg_1_left", CubeListBuilder.create().texOffs(30, 25).addBox(-0.5F, -0.0921547489F, -0.5F, 1.0F, 2.5F, 1.0F), PartPose.offsetAndRotation(-2.2507106F, 0.166729716F, 0.346820809F, -0.0F, -0.0F, 0.959931089F));
        PartDefinition leg_1_left_tip = leg_1_left.addOrReplaceChild("leg_1_left_tip", CubeListBuilder.create().texOffs(36, 25).addBox(-0.5F, -0.00897903927F, -0.5F, 1.0F, 1.5F, 1.0F), PartPose.offsetAndRotation(-0.0F, 2.3156905F, 0.0F, -0.0F, -0.0F, -1.22173048F));
        PartDefinition leg_1_right = body.addOrReplaceChild("leg_1_right", CubeListBuilder.create().texOffs(42, 25).addBox(-0.5F, -0.0921547489F, -0.5F, 1.0F, 2.5F, 1.0F), PartPose.offsetAndRotation(2.2507106F, 0.166729716F, 0.346820809F, -0.0F, -0.0F, -0.959931089F));
        PartDefinition leg_1_right_tip = leg_1_right.addOrReplaceChild("leg_1_right_tip", CubeListBuilder.create().texOffs(48, 25).addBox(-0.5F, -0.00897903927F, -0.5F, 1.0F, 1.5F, 1.0F), PartPose.offsetAndRotation(-0.0F, 2.3156905F, 0.0F, -0.0F, -0.0F, 1.22173048F));
        PartDefinition leg_2_left = body.addOrReplaceChild("leg_2_left", CubeListBuilder.create().texOffs(54, 25).addBox(-0.5F, -0.0921547489F, -0.5F, 1.0F, 2.5F, 1.0F), PartPose.offsetAndRotation(-2.2507106F, 0.166729716F, 4.85549133F, -0.0F, -0.20943951F, 0.959931089F));
        PartDefinition leg_2_left_tip = leg_2_left.addOrReplaceChild("leg_2_left_tip", CubeListBuilder.create().texOffs(1, 31).addBox(-0.5F, -0.00897903927F, -0.5F, 1.0F, 1.5F, 1.0F), PartPose.offsetAndRotation(-0.0F, 2.3156905F, 0.0F, -0.0F, -0.0F, -1.22173048F));
        PartDefinition leg_2_right = body.addOrReplaceChild("leg_2_right", CubeListBuilder.create().texOffs(7, 31).addBox(-0.5F, -0.0921547489F, -0.5F, 1.0F, 2.5F, 1.0F), PartPose.offsetAndRotation(2.2507106F, 0.166729716F, 4.85549133F, -0.0F, -0.20943951F, -0.959931089F));
        PartDefinition leg_2_right_tip = leg_2_right.addOrReplaceChild("leg_2_right_tip", CubeListBuilder.create().texOffs(13, 31).addBox(-0.5F, -0.00897903927F, -0.5F, 1.0F, 1.5F, 1.0F), PartPose.offsetAndRotation(-0.0F, 2.3156905F, 0.0F, -0.0F, -0.0F, 1.22173048F));
        PartDefinition gore = mouse.addOrReplaceChild("gore", CubeListBuilder.create().texOffs(19, 31).addBox(-3.66657116F, -0.0741020961F, -5.41907514F, 7.33314231F, 0.0555765721F, 10.8381503F), PartPose.offsetAndRotation(-0.0F, 0.0F, 0.0F, -0.0F, -0.0F, 0.0F));
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
            throw new IllegalArgumentException("Nonfinite cockroach presentation");
        root.getAllParts().forEach(ModelPart::resetPose);
        gore.visible = false; // Independent source blood state is OFF by default, even Dead.
        if (dead) clip = "down_hold";
        if (clip != null && !clip.equals("rest")) {
            KeyframeAnimations.animate(this, AnimalCockroachAnimations.byName(clip),
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
