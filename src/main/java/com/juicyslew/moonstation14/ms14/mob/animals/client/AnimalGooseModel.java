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

/** Generated SS14 goose asset rig. CC-BY-SA3.0 art; retain ATTRIBUTION.md.
 * Native1.21.1 API, based on pinned Blockbench4.12.6 geometry conventions.
 * NOT a registered entity/renderer/AI/networking implementation.
 */
public final class AnimalGooseModel<T extends LivingEntity> extends HierarchicalModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
        ResourceLocation.fromNamespaceAndPath("moonstation14", "animals/goose"), "main");
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
    public AnimalGooseModel(ModelPart root) {
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
        PartDefinition body = mouse.addOrReplaceChild("body", CubeListBuilder.create().texOffs(1, 1).addBox(-4.05405405F, -1.83798058F, -5.35984075F, 8.10810811F, 3.67596117F, 10.7196815F).texOffs(41, 1).addBox(-3.16216216F, -3.16893204F, -4.39506941F, 6.32432432F, 1.64784466F, 8.79013883F).texOffs(74, 1).addBox(-3.16216216F, 1.52108738F, -4.39506941F, 6.32432432F, 1.64784466F, 8.79013883F).texOffs(1, 18).addBox(-3.24324324F, -1.59904311F, -5.46703756F, 6.48648649F, 3.19808621F, 4.07347897F).texOffs(25, 18).addBox(-2.52972973F, -2.75697087F, -5.10042446F, 5.05945946F, 1.43362485F, 3.34025275F).texOffs(44, 18).addBox(-2.52972973F, 1.32334602F, -5.10042446F, 5.05945946F, 1.43362485F, 3.34025275F), PartPose.offsetAndRotation(-0.0F, -6.46990291F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(63, 18).addBox(-1.54054054F, 0.0F, -1.50075541F, 3.08108108F, 7.42058252F, 3.00151082F).texOffs(78, 18).addBox(-2.83783784F, -1.43592233F, -2.67992037F, 5.67567568F, 2.87184466F, 5.35984075F).texOffs(103, 18).addBox(-2.21351351F, -2.47572816F, -2.19753471F, 4.42702703F, 1.28737864F, 4.39506941F).texOffs(1, 31).addBox(-2.21351351F, 1.18834951F, -2.19753471F, 4.42702703F, 1.28737864F, 4.39506941F).texOffs(21, 31).addBox(-2.02702703F, -0.0990291262F, -4.82385667F, 4.05405405F, 0.726213592F, 2.1439363F).texOffs(36, 31).addBox(-2.27027027F, -0.501747573F, -2.894314F, 0.648648649F, 0.52815534F, 0.285858173F).texOffs(40, 31).addBox(1.62162162F, -0.501747573F, -2.894314F, 0.648648649F, 0.52815534F, 0.285858173F), PartPose.offsetAndRotation(-0.0F, -8.05436893F, -4.07347897F, -0.0F, -0.0F, 0.0F));
        PartDefinition wing_left = body.addOrReplaceChild("wing_left", CubeListBuilder.create().texOffs(44, 31).addBox(-0.932432432F, 0.579914563F, -3.46603035F, 0.891891892F, 2.38937476F, 7.93256431F).texOffs(64, 31).addBox(-0.834324324F, -0.285203883F, -2.75209956F, 0.695675676F, 1.07109903F, 6.50470273F).texOffs(81, 31).addBox(-0.834324324F, 2.76330874F, -2.75209956F, 0.695675676F, 1.07109903F, 6.50470273F), PartPose.offsetAndRotation(-3.56756757F, -1.45770874F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition foot_left = body.addOrReplaceChild("foot_left", CubeListBuilder.create().texOffs(98, 31).addBox(-0.364864865F, -0.165048544F, -0.393054988F, 0.72972973F, 3.30097087F, 0.786109977F).texOffs(104, 31).addBox(-1.33783784F, 2.82563107F, -1.78661358F, 2.67567568F, 0.475339806F, 2.1439363F), PartPose.offsetAndRotation(-1.94594595F, 3.16893204F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition wing_right = body.addOrReplaceChild("wing_right", CubeListBuilder.create().texOffs(1, 44).addBox(0.0405405405F, 0.579914563F, -3.46603035F, 0.891891892F, 2.38937476F, 7.93256431F).texOffs(21, 44).addBox(0.138648649F, -0.285203883F, -2.75209956F, 0.695675676F, 1.07109903F, 6.50470273F).texOffs(38, 44).addBox(0.138648649F, 2.76330874F, -2.75209956F, 0.695675676F, 1.07109903F, 6.50470273F), PartPose.offsetAndRotation(3.56756757F, -1.45770874F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition foot_right = body.addOrReplaceChild("foot_right", CubeListBuilder.create().texOffs(55, 44).addBox(-0.364864865F, -0.165048544F, -0.393054988F, 0.72972973F, 3.30097087F, 0.786109977F).texOffs(61, 44).addBox(-1.33783784F, 2.82563107F, -1.78661358F, 2.67567568F, 0.475339806F, 2.1439363F), PartPose.offsetAndRotation(1.94594595F, 3.16893204F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition tail_0 = body.addOrReplaceChild("tail_0", CubeListBuilder.create().texOffs(73, 44).addBox(-0.72972973F, -0.594174757F, -0.085757452F, 1.45945946F, 1.18834951F, 1.50551971F), PartPose.offsetAndRotation(-0.0F, -0.82392233F, 5.35984075F, 0.261799388F, -0.20943951F, 0.0F));
        PartDefinition tail_1 = tail_0.addOrReplaceChild("tail_1", CubeListBuilder.create().texOffs(81, 44).addBox(-0.583783784F, -0.475339806F, -0.085757452F, 1.16756757F, 0.950679612F, 1.50551971F), PartPose.offsetAndRotation(-0.0F, 0.0F, 1.33400481F, 0.261799388F, 0.20943951F, 0.0F));
        PartDefinition tail_2 = tail_1.addOrReplaceChild("tail_2", CubeListBuilder.create().texOffs(89, 44).addBox(-0.437837838F, -0.356504854F, -0.085757452F, 0.875675676F, 0.713009709F, 1.50551971F), PartPose.offsetAndRotation(-0.0F, 0.0F, 1.33400481F, 0.261799388F, -0.20943951F, 0.0F));
        PartDefinition gore = mouse.addOrReplaceChild("gore", CubeListBuilder.create().texOffs(96, 44).addBox(-2.10810811F, -0.052815534F, -1.78661358F, 4.21621622F, 0.0396116505F, 3.57322717F), PartPose.offsetAndRotation(-0.0F, 0.0F, 0.0F, -0.0F, -0.0F, 0.0F));
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
            throw new IllegalArgumentException("Nonfinite goose presentation");
        root.getAllParts().forEach(ModelPart::resetPose);
        gore.visible = false; // Independent source blood state is OFF by default, even Dead.
        if (dead) clip = "down_hold";
        if (clip != null && !clip.equals("rest")) {
            KeyframeAnimations.animate(this, AnimalGooseAnimations.byName(clip),
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
