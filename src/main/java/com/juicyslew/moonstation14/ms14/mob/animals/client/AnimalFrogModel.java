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

/** Generated SS14 frog asset rig. CC-BY-SA3.0 art; retain ATTRIBUTION.md.
 * Native1.21.1 API, based on pinned Blockbench4.12.6 geometry conventions.
 * NOT a registered entity/renderer/AI/networking implementation.
 */
public final class AnimalFrogModel<T extends LivingEntity> extends HierarchicalModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
        ResourceLocation.fromNamespaceAndPath("moonstation14", "animals/frog"), "main");
    private final ModelPart root;
    private final ModelPart mouse;
    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart front_left;
    private final ModelPart front_right;
    private final ModelPart hind_left;
    private final ModelPart hind_right;
    private final ModelPart gore;
    public AnimalFrogModel(ModelPart root) {
        this.root = root;
        this.mouse = root.getChild("mouse");
        this.body = this.mouse.getChild("body");
        this.head = this.body.getChild("head");
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
        PartDefinition body = mouse.addOrReplaceChild("body", CubeListBuilder.create().texOffs(1, 1).addBox(-2.75F, -2.0625F, -3.75F, 5.5F, 3.5F, 7.5F).texOffs(1, 14).addBox(-2.25F, 0.34375F, -3.875F, 4.5F, 1.5F, 6.5F).texOffs(40, 24).addBox(1.13297872F, -0.625F, 0.0416666667F, 2.5F, 2.5F, 4.5F).texOffs(1, 33).addBox(-3.63297872F, -0.625F, 0.0416666667F, 2.5F, 2.5F, 4.5F), PartPose.offsetAndRotation(-0.0F, -2.375F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(29, 1).addBox(-2.75F, -1.25F, -1.0F, 5.5F, 2.5F, 4.5F).texOffs(25, 14).addBox(-2.2393617F, -2.078125F, -1.20833333F, 1.5F, 1.5F, 2.0F).texOffs(34, 14).addBox(0.739361702F, -2.078125F, -1.20833333F, 1.5F, 1.5F, 2.0F).texOffs(43, 14).addBox(-1.7393617F, -1.65625F, -1.5F, 0.5F, 0.5F, 0.5F).texOffs(47, 14).addBox(1.2393617F, -1.65625F, -1.5F, 0.5F, 0.5F, 0.5F), PartPose.offsetAndRotation(-0.0F, -0.78125F, -4.16666667F, -0.0F, -0.0F, 0.0F));
        PartDefinition front_left = body.addOrReplaceChild("front_left", CubeListBuilder.create().texOffs(51, 14).addBox(-0.5F, -0.390625F, -0.5F, 1.0F, 2.0F, 1.0F).texOffs(57, 14).addBox(-0.5F, 1.21875F, -1.16666667F, 1.0F, 1.0F, 1.5F), PartPose.offsetAndRotation(-1.78723404F, 0.15625F, -2.5F, -0.0F, -0.0F, 0.0F));
        PartDefinition front_right = body.addOrReplaceChild("front_right", CubeListBuilder.create().texOffs(1, 24).addBox(-0.5F, -0.390625F, -0.5F, 1.0F, 2.0F, 1.0F).texOffs(7, 24).addBox(-0.5F, 1.21875F, -1.16666667F, 1.0F, 1.0F, 1.5F), PartPose.offsetAndRotation(1.78723404F, 0.15625F, -2.5F, -0.0F, -0.0F, 0.0F));
        PartDefinition hind_left = body.addOrReplaceChild("hind_left", CubeListBuilder.create().texOffs(14, 24).addBox(-0.5F, -0.390625F, -0.5F, 1.0F, 2.0F, 1.0F).texOffs(20, 24).addBox(-0.5F, 1.21875F, -1.16666667F, 1.0F, 1.0F, 1.5F), PartPose.offsetAndRotation(-1.78723404F, 0.15625F, 2.29166667F, -0.0F, -0.0F, 0.0F));
        PartDefinition hind_right = body.addOrReplaceChild("hind_right", CubeListBuilder.create().texOffs(27, 24).addBox(-0.5F, -0.390625F, -0.5F, 1.0F, 2.0F, 1.0F).texOffs(33, 24).addBox(-0.5F, 1.21875F, -1.16666667F, 1.0F, 1.0F, 1.5F), PartPose.offsetAndRotation(1.78723404F, 0.15625F, 2.29166667F, -0.0F, -0.0F, 0.0F));
        PartDefinition gore = mouse.addOrReplaceChild("gore", CubeListBuilder.create().texOffs(17, 33).addBox(-1.93617021F, -0.0625F, -2.60416667F, 3.87234043F, 0.046875F, 5.20833333F), PartPose.offsetAndRotation(-0.0F, 0.0F, 0.0F, -0.0F, -0.0F, 0.0F));
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
            throw new IllegalArgumentException("Nonfinite frog presentation");
        root.getAllParts().forEach(ModelPart::resetPose);
        gore.visible = false; // Independent source blood state is OFF by default, even Dead.
        if (dead) clip = "down_hold";
        if (clip != null && !clip.equals("rest")) {
            KeyframeAnimations.animate(this, AnimalFrogAnimations.byName(clip),
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
