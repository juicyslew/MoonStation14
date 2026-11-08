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

/** Generated SS14 chicken_2 asset rig. CC-BY-SA3.0 art; retain ATTRIBUTION.md.
 * Native1.21.1 API, based on pinned Blockbench4.12.6 geometry conventions.
 * NOT a registered entity/renderer/AI/networking implementation.
 */
public final class AnimalChicken2Model<T extends LivingEntity> extends HierarchicalModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
        ResourceLocation.fromNamespaceAndPath("moonstation14", "animals/chicken_2"), "main");
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
    public AnimalChicken2Model(ModelPart root) {
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
        PartDefinition body = mouse.addOrReplaceChild("body", CubeListBuilder.create().texOffs(1, 1).addBox(-3.55555556F, -1.52103746F, -3.59954461F, 7.11111111F, 3.04207493F, 7.19908922F).texOffs(32, 1).addBox(-2.77333333F, -2.62247839F, -2.95162658F, 5.54666667F, 1.36368876F, 5.90325316F).texOffs(57, 1).addBox(-2.77333333F, 1.25878963F, -2.95162658F, 5.54666667F, 1.36368876F, 5.90325316F).texOffs(82, 1).addBox(-2.84444444F, -1.32330259F, -3.6715355F, 5.68888889F, 2.64660519F, 2.7356539F).texOffs(101, 1).addBox(-2.21866667F, -2.2815562F, -3.42532665F, 4.43733333F, 1.18640922F, 2.2432362F).texOffs(1, 14).addBox(-2.21866667F, 1.09514697F, -3.42532665F, 4.43733333F, 1.18640922F, 2.2432362F), PartPose.offsetAndRotation(-0.0F, -4.870317F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(17, 14).addBox(-1.35111111F, 0.0F, -1.02337822F, 2.70222222F, 3.09702209F, 2.04675644F).texOffs(29, 14).addBox(-2.48888889F, -1.19510086F, -1.82746111F, 4.97777778F, 2.39020173F, 3.65492222F).texOffs(49, 14).addBox(-1.94133333F, -2.06051873F, -1.49851811F, 3.88266667F, 1.07146974F, 2.99703622F).texOffs(65, 14).addBox(-1.94133333F, 0.989048991F, -1.49851811F, 3.88266667F, 1.07146974F, 2.99703622F).texOffs(81, 14).addBox(-1.06666667F, -0.0936599424F, -2.71350286F, 2.13333333F, 0.686839577F, 0.88604175F).texOffs(90, 14).addBox(-2.02989899F, -0.474543708F, -1.99359394F, 0.646464646F, 0.499519693F, 0.221510438F).texOffs(94, 14).addBox(1.38343434F, -0.474543708F, -1.99359394F, 0.646464646F, 0.499519693F, 0.221510438F).texOffs(109, 24).addBox(-0.444444444F, -4.50816523F, -0.88604175F, 0.888888889F, 1.87319885F, 1.7720835F).texOffs(117, 24).addBox(-0.565656566F, 1.69836695F, -2.2261799F, 1.13131313F, 0.999039385F, 0.553776094F), PartPose.offsetAndRotation(-0.0F, -3.62151777F, -2.7356539F, -0.0F, -0.0F, 0.0F));
        PartDefinition wing_left = body.addOrReplaceChild("wing_left", CubeListBuilder.create().texOffs(98, 14).addBox(-0.871111111F, 0.479913545F, -2.27601975F, 0.888888889F, 1.9773487F, 5.32732602F).texOffs(113, 14).addBox(-0.773333333F, -0.236023055F, -1.7965604F, 0.693333333F, 0.886397695F, 4.36840734F).texOffs(1, 24).addBox(-0.773333333F, 2.28680115F, -1.7965604F, 0.693333333F, 0.886397695F, 4.36840734F), PartPose.offsetAndRotation(-3.12888889F, -1.20634006F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition foot_left = body.addOrReplaceChild("foot_left", CubeListBuilder.create().texOffs(14, 24).addBox(-0.363636364F, -0.112391931F, -0.304576852F, 0.727272727F, 2.24783862F, 0.609153703F).texOffs(19, 24).addBox(-1.17333333F, 1.79827089F, -1.38444023F, 2.34666667F, 0.449567723F, 1.66132828F), PartPose.offsetAndRotation(-1.70666667F, 2.62247839F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition wing_right = body.addOrReplaceChild("wing_right", CubeListBuilder.create().texOffs(30, 24).addBox(-0.0177777778F, 0.479913545F, -2.27601975F, 0.888888889F, 1.9773487F, 5.32732602F).texOffs(45, 24).addBox(0.08F, -0.236023055F, -1.7965604F, 0.693333333F, 0.886397695F, 4.36840734F).texOffs(58, 24).addBox(0.08F, 2.28680115F, -1.7965604F, 0.693333333F, 0.886397695F, 4.36840734F), PartPose.offsetAndRotation(3.12888889F, -1.20634006F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition foot_right = body.addOrReplaceChild("foot_right", CubeListBuilder.create().texOffs(71, 24).addBox(-0.363636364F, -0.112391931F, -0.304576852F, 0.727272727F, 2.24783862F, 0.609153703F).texOffs(76, 24).addBox(-1.17333333F, 1.79827089F, -1.38444023F, 2.34666667F, 0.449567723F, 1.66132828F), PartPose.offsetAndRotation(1.70666667F, 2.62247839F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition tail_0 = body.addOrReplaceChild("tail_0", CubeListBuilder.create().texOffs(87, 24).addBox(-0.727272727F, -0.561959654F, -0.0664531313F, 1.45454545F, 1.12391931F, 1.16662164F), PartPose.offsetAndRotation(-0.0F, -0.68184438F, 3.59954461F, 0.261799388F, -0.20943951F, 0.0F));
        PartDefinition tail_1 = tail_0.addOrReplaceChild("tail_1", CubeListBuilder.create().texOffs(95, 24).addBox(-0.581818182F, -0.449567723F, -0.0664531313F, 1.16363636F, 0.899135447F, 1.16662164F), PartPose.offsetAndRotation(-0.0F, 0.0F, 1.03371538F, 0.261799388F, 0.20943951F, 0.0F));
        PartDefinition tail_2 = tail_1.addOrReplaceChild("tail_2", CubeListBuilder.create().texOffs(102, 24).addBox(-0.436363636F, -0.337175793F, -0.0664531313F, 0.872727273F, 0.674351585F, 1.16662164F), PartPose.offsetAndRotation(-0.0F, 0.0F, 1.03371538F, 0.261799388F, -0.20943951F, 0.0F));
        PartDefinition gore = mouse.addOrReplaceChild("gore", CubeListBuilder.create().texOffs(1, 34).addBox(-2.1010101F, -0.0499519693F, -1.38444023F, 4.2020202F, 0.0374639769F, 2.76888047F), PartPose.offsetAndRotation(-0.0F, 0.0F, 0.0F, -0.0F, -0.0F, 0.0F));
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
            throw new IllegalArgumentException("Nonfinite chicken_2 presentation");
        root.getAllParts().forEach(ModelPart::resetPose);
        gore.visible = false; // Independent source blood state is OFF by default, even Dead.
        if (dead) clip = "down_hold";
        if (clip != null && !clip.equals("rest")) {
            KeyframeAnimations.animate(this, AnimalChicken2Animations.byName(clip),
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
