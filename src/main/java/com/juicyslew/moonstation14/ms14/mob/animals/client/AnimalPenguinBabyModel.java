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

/** Generated SS14 penguin_baby asset rig. CC-BY-SA3.0 art; retain ATTRIBUTION.md.
 * Native1.21.1 API, based on pinned Blockbench4.12.6 geometry conventions.
 * NOT a registered entity/renderer/AI/networking implementation.
 */
public final class AnimalPenguinBabyModel<T extends LivingEntity> extends HierarchicalModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
        ResourceLocation.fromNamespaceAndPath("moonstation14", "animals/penguin_baby"), "main");
    private final ModelPart root;
    private final ModelPart mouse;
    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart wing_left;
    private final ModelPart foot_left;
    private final ModelPart wing_right;
    private final ModelPart foot_right;
    private final ModelPart gore;
    public AnimalPenguinBabyModel(ModelPart root) {
        this.root = root;
        this.mouse = root.getChild("mouse");
        this.body = this.mouse.getChild("body");
        this.head = this.body.getChild("head");
        this.wing_left = this.body.getChild("wing_left");
        this.foot_left = this.body.getChild("foot_left");
        this.wing_right = this.body.getChild("wing_right");
        this.foot_right = this.body.getChild("foot_right");
        this.gore = this.mouse.getChild("gore");
        this.gore.visible = false;
    }
    @Override public ModelPart root() { return root; }
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition base = mesh.getRoot();
        PartDefinition mouse = base.addOrReplaceChild("mouse", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 24.0F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition body = mouse.addOrReplaceChild("body", CubeListBuilder.create().texOffs(1, 1).addBox(-3.0F, -2.75F, -2.25F, 6.0F, 5.5F, 4.5F).texOffs(24, 1).addBox(-2.5F, -4.66538462F, -1.75F, 5.0F, 2.5F, 3.5F).texOffs(43, 1).addBox(-2.5F, 2.16538462F, -1.75F, 5.0F, 2.5F, 3.5F).texOffs(1, 13).addBox(-2.5F, -2.5F, -2.30856434F, 5.0F, 5.0F, 2.0F).texOffs(17, 13).addBox(-2.0F, -4.22138462F, -2.05856434F, 4.0F, 2.5F, 1.5F).texOffs(30, 13).addBox(-2.0F, 1.72138462F, -2.05856434F, 4.0F, 2.5F, 1.5F), PartPose.offsetAndRotation(-0.0F, -5.70347395F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(43, 13).addBox(-1.25F, -0.0192307692F, -1.25F, 2.5F, 3.5F, 2.5F).texOffs(1, 22).addBox(-2.25F, -1.25F, -2.0F, 4.5F, 2.5F, 4.0F).texOffs(20, 22).addBox(-1.75F, -2.28692308F, -1.75F, 3.5F, 1.5F, 3.5F).texOffs(36, 22).addBox(-1.75F, 0.786923077F, -1.75F, 3.5F, 1.5F, 3.5F).texOffs(52, 22).addBox(-1.0F, -0.377791563F, -3.38129184F, 2.0F, 1.5F, 1.5F).texOffs(61, 22).addBox(-1.66794668F, -0.584987593F, -2.65607984F, 0.5F, 0.5F, 0.5F).texOffs(1, 31).addBox(1.16794668F, -0.584987593F, -2.65607984F, 0.5F, 0.5F, 0.5F).texOffs(59, 40).addBox(-1.85F, -0.976923077F, -2.27607984F, 1.0F, 2.0F, 0.5F).texOffs(1, 47).addBox(0.85F, -0.976923077F, -2.27607984F, 1.0F, 2.0F, 0.5F), PartPose.offsetAndRotation(-0.0F, -4.38461538F, -1.55392016F, -0.0F, -0.0F, 0.0F));
        PartDefinition wing_left = body.addOrReplaceChild("wing_left", CubeListBuilder.create().texOffs(5, 31).addBox(-1.10448667F, 0.834615385F, -1.14251178F, 1.5F, 3.5F, 3.5F).texOffs(17, 31).addBox(-0.85448667F, -0.635384615F, -0.642511778F, 1.0F, 2.0F, 2.5F).texOffs(26, 31).addBox(-0.85448667F, 3.80461538F, -0.642511778F, 1.0F, 2.0F, 2.5F), PartPose.offsetAndRotation(-2.59956892F, -2.12307692F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition foot_left = body.addOrReplaceChild("foot_left", CubeListBuilder.create().texOffs(35, 31).addBox(-0.5F, -0.0846153846F, -0.5F, 1.0F, 1.0F, 1.0F).texOffs(41, 31).addBox(-1.0F, 0.08808933F, -2.36784032F, 2.0F, 1.0F, 3.0F), PartPose.offsetAndRotation(-1.41794668F, 4.61538462F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition wing_right = body.addOrReplaceChild("wing_right", CubeListBuilder.create().texOffs(53, 31).addBox(-0.39551333F, 0.834615385F, -1.14251178F, 1.5F, 3.5F, 3.5F).texOffs(1, 40).addBox(-0.14551333F, -0.635384615F, -0.642511778F, 1.0F, 2.0F, 2.5F).texOffs(10, 40).addBox(-0.14551333F, 3.80461538F, -0.642511778F, 1.0F, 2.0F, 2.5F), PartPose.offsetAndRotation(2.59956892F, -2.12307692F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition foot_right = body.addOrReplaceChild("foot_right", CubeListBuilder.create().texOffs(19, 40).addBox(-0.5F, -0.0846153846F, -0.5F, 1.0F, 1.0F, 1.0F).texOffs(25, 40).addBox(-1.0F, 0.08808933F, -2.36784032F, 2.0F, 1.0F, 3.0F), PartPose.offsetAndRotation(1.41794668F, 4.61538462F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition gore = mouse.addOrReplaceChild("gore", CubeListBuilder.create().texOffs(37, 40).addBox(-2.58082813F, -0.0744416873F, -2.16960079F, 5.16165627F, 0.0558312655F, 4.33920159F), PartPose.offsetAndRotation(-0.0F, 0.0F, 0.0F, -0.0F, -0.0F, 0.0F));
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
            throw new IllegalArgumentException("Nonfinite penguin_baby presentation");
        root.getAllParts().forEach(ModelPart::resetPose);
        gore.visible = false; // Independent source blood state is OFF by default, even Dead.
        if (dead) clip = "down_hold";
        if (clip != null && !clip.equals("rest")) {
            KeyframeAnimations.animate(this, AnimalPenguinBabyAnimations.byName(clip),
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
        gore.visible = enabled && true;
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
