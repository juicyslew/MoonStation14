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

/** Generated SS14 possum_old asset rig. CC-BY-SA3.0 art; retain ATTRIBUTION.md.
 * Native1.21.1 API, based on pinned Blockbench4.12.6 geometry conventions.
 * NOT a registered entity/renderer/AI/networking implementation.
 */
public final class AnimalPossumOldModel<T extends LivingEntity> extends HierarchicalModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
        ResourceLocation.fromNamespaceAndPath("moonstation14", "animals/possum_old"), "main");
    private final ModelPart root;
    private final ModelPart mouse;
    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart ear_left;
    private final ModelPart ear_right;
    private final ModelPart front_left;
    private final ModelPart front_right;
    private final ModelPart hind_left;
    private final ModelPart hind_right;
    private final ModelPart tail_0;
    private final ModelPart tail_1;
    private final ModelPart tail_2;
    private final ModelPart gore;
    public AnimalPossumOldModel(ModelPart root) {
        this.root = root;
        this.mouse = root.getChild("mouse");
        this.body = this.mouse.getChild("body");
        this.head = this.body.getChild("head");
        this.ear_left = this.head.getChild("ear_left");
        this.ear_right = this.head.getChild("ear_right");
        this.front_left = this.body.getChild("front_left");
        this.front_right = this.body.getChild("front_right");
        this.hind_left = this.body.getChild("hind_left");
        this.hind_right = this.body.getChild("hind_right");
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
        PartDefinition body = mouse.addOrReplaceChild("body", CubeListBuilder.create().texOffs(1, 1).addBox(-4.0F, -1.78939695F, -5.12971822F, 8.0F, 3.57879389F, 10.2594364F).texOffs(1, 17).addBox(-3.2F, -2.53497901F, -4.20636894F, 6.4F, 1.09352036F, 8.41273788F).texOffs(33, 17).addBox(-3.0F, 1.24263677F, -4.43354217F, 6.0F, 1.29234224F, 7.69457733F), PartPose.offsetAndRotation(-0.0F, -4.80486217F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(1, 29).addBox(-2.6F, -2.15390373F, -2.78470418F, 5.2F, 4.30780746F, 4.10377457F).texOffs(22, 29).addBox(-1.69F, -0.248527354F, -3.9572112F, 3.38F, 2.15390373F, 2.05188729F).texOffs(35, 29).addBox(-0.65F, 0.331369805F, -4.11843091F, 1.3F, 0.994109414F, 0.439690133F).texOffs(41, 29).addBox(-2.272F, -1.07695187F, -3.03386192F, 0.8F, 0.662739609F, 0.293126755F).texOffs(46, 29).addBox(1.472F, -1.07695187F, -3.03386192F, 0.8F, 0.662739609F, 0.293126755F), PartPose.offsetAndRotation(-0.0F, -1.24263677F, -4.39690133F, -0.0F, -0.0F, 0.0F));
        PartDefinition ear_left = head.addOrReplaceChild("ear_left", CubeListBuilder.create().texOffs(51, 29).addBox(-0.728F, -1.93851336F, -0.366408444F, 1.456F, 1.93851336F, 0.732816888F).texOffs(58, 29).addBox(-0.4368F, -1.59927352F, -0.469002808F, 0.8736F, 1.26003368F, 0.117250702F), PartPose.offsetAndRotation(-1.872F, -1.90537638F, 0.0F, -0.0F, -0.0F, -0.20943951F));
        PartDefinition ear_right = head.addOrReplaceChild("ear_right", CubeListBuilder.create().texOffs(1, 40).addBox(-0.728F, -1.93851336F, -0.366408444F, 1.456F, 1.93851336F, 0.732816888F).texOffs(8, 40).addBox(-0.4368F, -1.59927352F, -0.469002808F, 0.8736F, 1.26003368F, 0.117250702F), PartPose.offsetAndRotation(1.872F, -1.90537638F, 0.0F, -0.0F, -0.0F, 0.20943951F));
        PartDefinition front_left = body.addOrReplaceChild("front_left", CubeListBuilder.create().texOffs(12, 40).addBox(-0.64F, -0.198821883F, -0.469002808F, 1.28F, 2.35272561F, 0.938005617F).texOffs(19, 40).addBox(-0.768F, 1.98821883F, -0.996630968F, 1.536F, 0.994109414F, 1.40700843F), PartPose.offsetAndRotation(-2.8F, 1.82253393F, -3.28301966F, -0.0F, -0.0F, 0.0F));
        PartDefinition front_right = body.addOrReplaceChild("front_right", CubeListBuilder.create().texOffs(27, 40).addBox(-0.64F, -0.198821883F, -0.469002808F, 1.28F, 2.35272561F, 0.938005617F).texOffs(34, 40).addBox(-0.768F, 1.98821883F, -0.996630968F, 1.536F, 0.994109414F, 1.40700843F), PartPose.offsetAndRotation(2.8F, 1.82253393F, -3.28301966F, -0.0F, -0.0F, 0.0F));
        PartDefinition hind_left = body.addOrReplaceChild("hind_left", CubeListBuilder.create().texOffs(42, 40).addBox(-0.64F, -0.198821883F, -0.469002808F, 1.28F, 2.35272561F, 0.938005617F).texOffs(49, 40).addBox(-0.768F, 1.98821883F, -0.996630968F, 1.536F, 0.994109414F, 1.40700843F), PartPose.offsetAndRotation(-2.8F, 1.82253393F, 3.28301966F, -0.0F, -0.0F, 0.0F));
        PartDefinition hind_right = body.addOrReplaceChild("hind_right", CubeListBuilder.create().texOffs(57, 40).addBox(-0.64F, -0.198821883F, -0.469002808F, 1.28F, 2.35272561F, 0.938005617F).texOffs(1, 46).addBox(-0.768F, 1.98821883F, -0.996630968F, 1.536F, 0.994109414F, 1.40700843F), PartPose.offsetAndRotation(2.8F, 1.82253393F, 3.28301966F, -0.0F, -0.0F, 0.0F));
        PartDefinition tail_0 = body.addOrReplaceChild("tail_0", CubeListBuilder.create().texOffs(9, 46).addBox(-0.38F, -0.314801315F, -0.0879380266F, 0.76F, 0.629602629F, 2.61859901F), PartPose.offsetAndRotation(-0.0F, 0.994109414F, 5.12971822F, 0.261799388F, -0.20943951F, 0.0F));
        PartDefinition tail_1 = tail_0.addOrReplaceChild("tail_1", CubeListBuilder.create().texOffs(18, 46).addBox(-0.304F, -0.251841052F, -0.0879380266F, 0.608F, 0.503682103F, 2.61859901F), PartPose.offsetAndRotation(-0.0F, 0.0F, 2.44272296F, 0.261799388F, 0.20943951F, 0.0F));
        PartDefinition tail_2 = tail_1.addOrReplaceChild("tail_2", CubeListBuilder.create().texOffs(27, 46).addBox(-0.228F, -0.188880789F, -0.0879380266F, 0.456F, 0.377761577F, 2.61859901F), PartPose.offsetAndRotation(-0.0F, 0.0F, 2.44272296F, 0.261799388F, -0.20943951F, 0.0F));
        PartDefinition gore = mouse.addOrReplaceChild("gore", CubeListBuilder.create().texOffs(36, 46).addBox(-2.6F, -0.0662739609F, -1.83204222F, 5.2F, 0.0497054707F, 3.66408444F), PartPose.offsetAndRotation(-0.0F, 0.0F, 0.0F, -0.0F, -0.0F, 0.0F));
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
            throw new IllegalArgumentException("Nonfinite possum_old presentation");
        root.getAllParts().forEach(ModelPart::resetPose);
        gore.visible = false; // Independent source blood state is OFF by default, even Dead.
        if (dead) clip = "down_hold";
        if (clip != null && !clip.equals("rest")) {
            KeyframeAnimations.animate(this, AnimalPossumOldAnimations.byName(clip),
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
