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

/** Generated SS14 monkey asset rig. CC-BY-SA3.0 art; retain ATTRIBUTION.md.
 * Native1.21.1 API, based on pinned Blockbench4.12.6 geometry conventions.
 * NOT a registered entity/renderer/AI/networking implementation.
 */
public final class AnimalMonkeyModel<T extends LivingEntity> extends HierarchicalModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
        ResourceLocation.fromNamespaceAndPath("moonstation14", "animals/monkey"), "main");
    private final ModelPart root;
    private final ModelPart mouse;
    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart arm_left;
    private final ModelPart forearm_left;
    private final ModelPart foot_left;
    private final ModelPart arm_right;
    private final ModelPart forearm_right;
    private final ModelPart foot_right;
    private final ModelPart tail_0;
    private final ModelPart tail_1;
    private final ModelPart tail_2;
    private final ModelPart tail_3;
    private final ModelPart tail_4;
    private final ModelPart gore;
    public AnimalMonkeyModel(ModelPart root) {
        this.root = root;
        this.mouse = root.getChild("mouse");
        this.body = this.mouse.getChild("body");
        this.head = this.body.getChild("head");
        this.arm_left = this.body.getChild("arm_left");
        this.forearm_left = this.arm_left.getChild("forearm_left");
        this.foot_left = this.body.getChild("foot_left");
        this.arm_right = this.body.getChild("arm_right");
        this.forearm_right = this.arm_right.getChild("forearm_right");
        this.foot_right = this.body.getChild("foot_right");
        this.tail_0 = this.body.getChild("tail_0");
        this.tail_1 = this.tail_0.getChild("tail_1");
        this.tail_2 = this.tail_1.getChild("tail_2");
        this.tail_3 = this.tail_2.getChild("tail_3");
        this.tail_4 = this.tail_3.getChild("tail_4");
        this.gore = this.mouse.getChild("gore");
        this.gore.visible = false;
    }
    @Override public ModelPart root() { return root; }
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition base = mesh.getRoot();
        PartDefinition mouse = base.addOrReplaceChild("mouse", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 24.0F, 0.0F, -0.0F, -0.0F, 0.0F));
        // The upper diaper shell retains its own UVs but is nested by 0.01 on
        // each X/Z edge, avoiding coplanar side faces with the lower layer.
        PartDefinition body = mouse.addOrReplaceChild("body", CubeListBuilder.create().texOffs(1, 1).addBox(-3.0F, -3.07057057F, -2.5F, 6.0F, 5.0F, 5.0F).texOffs(25, 1).addBox(-2.25F, -4.86057057F, -2.25F, 4.5F, 2.5F, 4.5F).texOffs(45, 1).addBox(-2.25F, 1.21942943F, -2.25F, 4.5F, 2.5F, 4.5F).texOffs(65, 1).addBox(-3.25F, 2.99399399F, -1.85515873F, 6.5F, 2.0F, 5.0F).texOffs(90, 1).addBox(-2.5F, 2.52288288F, -1.35515873F, 5.0F, 1.0F, 4.0F).texOffs(1, 13).addBox(-2.5F, 4.46510511F, -1.35515873F, 5.0F, 1.0F, 4.0F).texOffs(21, 13).addBox(-3.24F, 2.93693694F, -1.84515873F, 6.48F, 2.0F, 4.98F).texOffs(46, 13).addBox(-2.0F, 4.09984985F, -2.04861111F, 4.0F, 1.5F, 5.0F), PartPose.offsetAndRotation(-0.0F, -10.5067568F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(66, 13).addBox(-3.75F, -1.86411411F, -2.87103175F, 7.5F, 3.5F, 6.0F).texOffs(95, 13).addBox(-3.0F, -3.26744745F, -2.37103175F, 6.0F, 2.0F, 5.0F).texOffs(1, 25).addBox(-3.0F, 1.03921922F, -2.37103175F, 6.0F, 2.0F, 5.0F).texOffs(25, 25).addBox(-2.75F, -1.47822823F, -3.10019841F, 5.5F, 2.5F, 0.5F).texOffs(39, 25).addBox(-2.25F, -2.10045045F, -3.10019841F, 4.5F, 1.0F, 0.5F).texOffs(51, 25).addBox(-2.25F, 0.643993994F, -3.10019841F, 4.5F, 1.0F, 0.5F).texOffs(63, 25).addBox(-1.5F, 0.162912913F, -4.07738095F, 3.0F, 1.5F, 1.5F).texOffs(74, 25).addBox(-1.0F, 0.971021021F, -4.11904762F, 2.0F, 0.5F, 0.5F).texOffs(81, 25).addBox(-1.83921303F, -1.18468468F, -3.40972222F, 1.0F, 1.0F, 0.5F).texOffs(86, 25).addBox(0.839213026F, -1.18468468F, -3.40972222F, 1.0F, 1.0F, 0.5F).texOffs(91, 25).addBox(-4.90284939F, -0.75F, -0.0486111111F, 2.0F, 1.5F, 1.0F).texOffs(99, 25).addBox(-4.65284939F, -0.925555556F, -0.0486111111F, 1.5F, 0.5F, 1.0F).texOffs(106, 25).addBox(-4.65284939F, 0.425555556F, -0.0486111111F, 1.5F, 0.5F, 1.0F).texOffs(113, 25).addBox(-4.42835821F, -0.75F, -0.288690476F, 1.0F, 1.5F, 0.5F).texOffs(43, 34).addBox(2.90284939F, -0.75F, -0.0486111111F, 2.0F, 1.5F, 1.0F).texOffs(51, 34).addBox(3.15284939F, -0.925555556F, -0.0486111111F, 1.5F, 0.5F, 1.0F).texOffs(58, 34).addBox(3.15284939F, 0.425555556F, -0.0486111111F, 1.5F, 0.5F, 1.0F).texOffs(65, 34).addBox(3.42835821F, -0.75F, -0.288690476F, 1.0F, 1.5F, 0.5F).texOffs(51, 44).addBox(-3.97428765F, -1.47822823F, -2.66071429F, 0.5F, 2.5F, 3.0F).texOffs(60, 44).addBox(3.47428765F, -1.47822823F, -2.66071429F, 0.5F, 2.5F, 3.0F), PartPose.offsetAndRotation(-0.0F, -5.70570571F, -1.03174603F, -0.0F, -0.0F, 0.0F));
        PartDefinition arm_left = body.addOrReplaceChild("arm_left", CubeListBuilder.create().texOffs(118, 25).addBox(-1.0050882F, -0.217717718F, -1.09672619F, 1.5F, 5.0F, 2.0F), PartPose.offsetAndRotation(-2.55088195F, -3.42342342F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition forearm_left = arm_left.addOrReplaceChild("forearm_left", CubeListBuilder.create().texOffs(1, 34).addBox(-0.877544098F, -0.358858859F, -0.911210317F, 1.5F, 3.0F, 1.5F).texOffs(9, 34).addBox(-1.0050882F, 1.78228228F, -1.32242063F, 1.5F, 1.0F, 2.0F), PartPose.offsetAndRotation(-0.510176391F, 4.56456456F, -0.193452381F, -0.0F, -0.0F, 0.0F));
        PartDefinition foot_left = body.addOrReplaceChild("foot_left", CubeListBuilder.create().texOffs(18, 34).addBox(-1.0F, -0.239489489F, -0.806547619F, 2.0F, 5.5F, 2.0F).texOffs(28, 34).addBox(-1.25F, 4.44219219F, -2.9672619F, 2.5F, 1.5F, 4.0F), PartPose.offsetAndRotation(-1.59430122F, 4.56456456F, 0.709325397F, -0.0F, -0.0F, 0.0F));
        PartDefinition arm_right = body.addOrReplaceChild("arm_right", CubeListBuilder.create().texOffs(70, 34).addBox(-0.494911805F, -0.217717718F, -1.09672619F, 1.5F, 5.0F, 2.0F), PartPose.offsetAndRotation(2.55088195F, -3.42342342F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition forearm_right = arm_right.addOrReplaceChild("forearm_right", CubeListBuilder.create().texOffs(79, 34).addBox(-0.622455902F, -0.358858859F, -0.911210317F, 1.5F, 3.0F, 1.5F).texOffs(87, 34).addBox(-0.494911805F, 1.78228228F, -1.32242063F, 1.5F, 1.0F, 2.0F), PartPose.offsetAndRotation(0.510176391F, 4.56456456F, -0.193452381F, -0.0F, -0.0F, 0.0F));
        PartDefinition foot_right = body.addOrReplaceChild("foot_right", CubeListBuilder.create().texOffs(96, 34).addBox(-1.0F, -0.239489489F, -0.806547619F, 2.0F, 5.5F, 2.0F).texOffs(106, 34).addBox(-1.25F, 4.44219219F, -2.9672619F, 2.5F, 1.5F, 4.0F), PartPose.offsetAndRotation(1.59430122F, 4.56456456F, 0.709325397F, -0.0F, -0.0F, 0.0F));
        PartDefinition tail_0 = body.addOrReplaceChild("tail_0", CubeListBuilder.create().texOffs(1, 44).addBox(-0.5F, -0.5F, -0.170634921F, 1.0F, 1.0F, 5.5F), PartPose.offsetAndRotation(-0.0F, 4.56456456F, 2.19246032F, -0.0F, -0.0F, 0.0F));
        PartDefinition tail_1 = tail_0.addOrReplaceChild("tail_1", CubeListBuilder.create().texOffs(16, 44).addBox(-2.25F, -0.5F, -0.5F, 4.5F, 1.0F, 1.0F), PartPose.offsetAndRotation(-2.04070556F, 0.228228228F, 5.41666667F, -0.0F, -0.0F, 0.0F));
        PartDefinition tail_2 = tail_1.addOrReplaceChild("tail_2", CubeListBuilder.create().texOffs(29, 44).addBox(-0.5F, -1.5F, -0.5F, 1.0F, 3.0F, 1.0F), PartPose.offsetAndRotation(-1.91316147F, -1.02702703F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition tail_3 = tail_2.addOrReplaceChild("tail_3", CubeListBuilder.create().texOffs(35, 44).addBox(-1.5F, -0.5F, -0.5F, 3.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(1.14789688F, -1.14114114F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition tail_4 = tail_3.addOrReplaceChild("tail_4", CubeListBuilder.create().texOffs(45, 44).addBox(-0.5F, -0.75F, -0.5F, 1.0F, 1.5F, 1.0F), PartPose.offsetAndRotation(0.892808684F, 0.570570571F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition gore = mouse.addOrReplaceChild("gore", CubeListBuilder.create().texOffs(69, 44).addBox(-1.65807327F, -0.0456456456F, -1.61210317F, 3.31614654F, 0.0342342342F, 3.22420635F), PartPose.offsetAndRotation(-0.0F, 0.0F, 0.0F, -0.0F, -0.0F, 0.0F));
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
            throw new IllegalArgumentException("Nonfinite monkey presentation");
        root.getAllParts().forEach(ModelPart::resetPose);
        gore.visible = false; // Independent source blood state is OFF by default, even Dead.
        if (dead) clip = "down_hold";
        if (clip != null && !clip.equals("rest")) {
            KeyframeAnimations.animate(this, AnimalMonkeyAnimations.byName(clip),
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
