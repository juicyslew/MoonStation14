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

/** Generated SS14 kangaroo asset rig. CC-BY-SA3.0 art; retain ATTRIBUTION.md.
 * Native1.21.1 API, based on pinned Blockbench4.12.6 geometry conventions.
 * NOT a registered entity/renderer/AI/networking implementation.
 */
public final class AnimalKangarooModel<T extends LivingEntity> extends HierarchicalModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
        ResourceLocation.fromNamespaceAndPath("moonstation14", "animals/kangaroo"), "main");
    private final ModelPart root;
    private final ModelPart mouse;
    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart ear_left;
    private final ModelPart ear_right;
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
    private final ModelPart gore;
    public AnimalKangarooModel(ModelPart root) {
        this.root = root;
        this.mouse = root.getChild("mouse");
        this.body = this.mouse.getChild("body");
        this.head = this.body.getChild("head");
        this.ear_left = this.head.getChild("ear_left");
        this.ear_right = this.head.getChild("ear_right");
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
        this.gore = this.mouse.getChild("gore");
        this.gore.visible = false;
    }
    @Override public ModelPart root() { return root; }
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition base = mesh.getRoot();
        PartDefinition mouse = base.addOrReplaceChild("mouse", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 24.0F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition body = mouse.addOrReplaceChild("body", CubeListBuilder.create().texOffs(1, 1).addBox(-4.0F, -3.90229035F, -3.25F, 8.0F, 6.0F, 6.5F).texOffs(32, 1).addBox(-3.25F, -6.07461205F, -2.75F, 6.5F, 3.0F, 5.5F).texOffs(58, 1).addBox(-3.25F, 1.27003136F, -2.75F, 6.5F, 3.0F, 5.5F).texOffs(84, 1).addBox(-5.25F, 4.31603242F, -2.12717968F, 10.5F, 4.0F, 8.5F).texOffs(1, 16).addBox(-4.0F, 2.97910043F, -1.37717968F, 8.0F, 2.0F, 7.0F).texOffs(33, 16).addBox(-4.0F, 7.65296442F, -1.37717968F, 8.0F, 2.0F, 7.0F).texOffs(65, 16).addBox(-2.25F, -8.81603242F, -4.58510235F, 4.5F, 5.0F, 4.5F).texOffs(85, 16).addBox(-2.75F, 1.35916138F, -3.79037149F, 5.5F, 4.5F, 1.0F), PartPose.offsetAndRotation(-0.0F, -13.6978665F, 1.06141016F, -0.0F, -0.0F, 0.0F));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(100, 16).addBox(-2.5F, -1.25F, -2.5F, 5.0F, 2.5F, 5.0F).texOffs(1, 28).addBox(-2.0F, -2.25231343F, -2.0F, 4.0F, 1.5F, 4.0F).texOffs(19, 28).addBox(-2.0F, 0.752313426F, -2.0F, 4.0F, 1.5F, 4.0F).texOffs(37, 28).addBox(-1.5F, -0.368396758F, -5.29037149F, 3.0F, 2.0F, 4.0F).texOffs(53, 28).addBox(-0.75F, -0.0939693443F, -5.41906748F, 1.5F, 1.0F, 0.5F).texOffs(59, 28).addBox(-2.19811321F, -0.971832277F, -2.69124337F, 1.0F, 0.5F, 0.5F).texOffs(64, 28).addBox(1.19811321F, -0.971832277F, -2.69124337F, 1.0F, 0.5F, 0.5F), PartPose.offsetAndRotation(-0.0F, -9.02290346F, -3.18423048F, -0.0F, -0.0F, 0.0F));
        PartDefinition ear_left = head.addOrReplaceChild("ear_left", CubeListBuilder.create().texOffs(69, 28).addBox(-0.75F, -3.80458069F, -0.5F, 1.5F, 4.0F, 1.0F), PartPose.offsetAndRotation(-1.64150943F, -1.80458069F, 0.955269143F, -0.0F, -0.0F, -0.20943951F));
        PartDefinition ear_right = head.addOrReplaceChild("ear_right", CubeListBuilder.create().texOffs(76, 28).addBox(-0.75F, -3.80458069F, -0.5F, 1.5F, 4.0F, 1.0F), PartPose.offsetAndRotation(1.64150943F, -1.80458069F, 0.955269143F, -0.0F, -0.0F, 0.20943951F));
        PartDefinition arm_left = body.addOrReplaceChild("arm_left", CubeListBuilder.create().texOffs(83, 28).addBox(-0.976415094F, 0.189924244F, -1.28070508F, 1.5F, 3.5F, 1.5F), PartPose.offsetAndRotation(-3.16981132F, -4.51145173F, -1.91053829F, -0.0F, -0.0F, 0.0F));
        PartDefinition forearm_left = arm_left.addOrReplaceChild("forearm_left", CubeListBuilder.create().texOffs(91, 28).addBox(-0.466981132F, 0.238779071F, -1.3868461F, 1.5F, 2.5F, 1.5F).texOffs(99, 28).addBox(-0.183962264F, 1.89106942F, -2.02369219F, 1.5F, 1.0F, 1.5F), PartPose.offsetAndRotation(-0.452830189F, 2.70687104F, -1.06141016F, -0.0F, -0.0F, 0.0F));
        PartDefinition foot_left = body.addOrReplaceChild("foot_left", CubeListBuilder.create().texOffs(1, 36).addBox(-2.72641509F, 0.505725865F, -2.53771797F, 5.0F, 3.5F, 5.5F).texOffs(24, 36).addBox(-2.22641509F, -0.94766716F, -2.03771797F, 4.0F, 2.0F, 4.5F).texOffs(43, 36).addBox(-2.22641509F, 3.45911889F, -2.03771797F, 4.0F, 2.0F, 4.5F).texOffs(62, 36).addBox(-1.22641509F, 3.82351304F, -0.46929492F, 2.0F, 3.0F, 2.0F).texOffs(72, 36).addBox(-1.47641509F, 5.88183404F, -7.64651251F, 2.5F, 1.5F, 8.5F), PartPose.offsetAndRotation(-3.50943396F, 6.31603242F, 2.12282032F, -0.0F, -0.0F, 0.0F));
        PartDefinition arm_right = body.addOrReplaceChild("arm_right", CubeListBuilder.create().texOffs(96, 36).addBox(-0.523584906F, 0.189924244F, -1.28070508F, 1.5F, 3.5F, 1.5F), PartPose.offsetAndRotation(3.16981132F, -4.51145173F, -1.91053829F, -0.0F, -0.0F, 0.0F));
        PartDefinition forearm_right = arm_right.addOrReplaceChild("forearm_right", CubeListBuilder.create().texOffs(104, 36).addBox(-1.03301887F, 0.238779071F, -1.3868461F, 1.5F, 2.5F, 1.5F).texOffs(112, 36).addBox(-1.31603774F, 1.89106942F, -2.02369219F, 1.5F, 1.0F, 1.5F), PartPose.offsetAndRotation(0.452830189F, 2.70687104F, -1.06141016F, -0.0F, -0.0F, 0.0F));
        PartDefinition foot_right = body.addOrReplaceChild("foot_right", CubeListBuilder.create().texOffs(1, 48).addBox(-2.27358491F, 0.505725865F, -2.53771797F, 5.0F, 3.5F, 5.5F).texOffs(24, 48).addBox(-1.77358491F, -0.94766716F, -2.03771797F, 4.0F, 2.0F, 4.5F).texOffs(43, 48).addBox(-1.77358491F, 3.45911889F, -2.03771797F, 4.0F, 2.0F, 4.5F).texOffs(62, 48).addBox(-0.773584906F, 3.82351304F, -0.46929492F, 2.0F, 3.0F, 2.0F).texOffs(72, 48).addBox(-1.02358491F, 5.88183404F, -7.64651251F, 2.5F, 1.5F, 8.5F), PartPose.offsetAndRotation(3.50943396F, 6.31603242F, 2.12282032F, -0.0F, -0.0F, 0.0F));
        PartDefinition tail_0 = body.addOrReplaceChild("tail_0", CubeListBuilder.create().texOffs(96, 48).addBox(-2.0F, -0.396564481F, -0.0657695224F, 4.0F, 3.5F, 6.5F), PartPose.offsetAndRotation(-0.0F, 7.21832277F, 3.18423048F, -0.0F, -0.0F, 0.0F));
        PartDefinition tail_1 = tail_0.addOrReplaceChild("tail_1", CubeListBuilder.create().texOffs(1, 60).addBox(-1.75F, -1.25F, -3.0F, 3.5F, 2.5F, 6.0F), PartPose.offsetAndRotation(-0.0F, 3.42870331F, 8.49128127F, -0.0F, -0.0F, 0.0F));
        PartDefinition tail_2 = tail_1.addOrReplaceChild("tail_2", CubeListBuilder.create().texOffs(22, 60).addBox(-1.0F, -0.75F, -2.5F, 2.0F, 1.5F, 5.0F), PartPose.offsetAndRotation(-0.0F, 1.89480973F, 4.45792267F, -0.0F, -0.0F, 0.0F));
        PartDefinition tail_3 = tail_2.addOrReplaceChild("tail_3", CubeListBuilder.create().texOffs(38, 60).addBox(-0.5F, -0.5F, -1.25F, 1.0F, 1.0F, 2.5F), PartPose.offsetAndRotation(-0.0F, 0.541374208F, 2.33510235F, -0.0F, -0.0F, 0.0F));
        PartDefinition gore = mouse.addOrReplaceChild("gore", CubeListBuilder.create().texOffs(47, 60).addBox(-1.47169811F, -0.0360916138F, -1.3267627F, 2.94339623F, 0.0270687104F, 2.6535254F), PartPose.offsetAndRotation(-0.0F, 0.0F, 0.0F, -0.0F, -0.0F, 0.0F));
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
            throw new IllegalArgumentException("Nonfinite kangaroo presentation");
        root.getAllParts().forEach(ModelPart::resetPose);
        gore.visible = false; // Independent source blood state is OFF by default, even Dead.
        if (dead) clip = "down_hold";
        if (clip != null && !clip.equals("rest")) {
            KeyframeAnimations.animate(this, AnimalKangarooAnimations.byName(clip),
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
