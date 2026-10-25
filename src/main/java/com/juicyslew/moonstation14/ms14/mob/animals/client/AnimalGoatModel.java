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

/** Generated SS14 goat asset rig. CC-BY-SA3.0 art; retain ATTRIBUTION.md.
 * Native1.21.1 API, based on pinned Blockbench4.12.6 geometry conventions.
 * NOT a registered entity/renderer/AI/networking implementation.
 */
public final class AnimalGoatModel<T extends LivingEntity> extends HierarchicalModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
        ResourceLocation.fromNamespaceAndPath("moonstation14", "animals/goat"), "main");
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
    private final ModelPart antler_left;
    private final ModelPart antler_right;
    private final ModelPart gore;
    private final ModelPart front_left_knee;
    private final ModelPart front_right_knee;
    private final ModelPart hind_left_knee;
    private final ModelPart hind_right_knee;
    public AnimalGoatModel(ModelPart root) {
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
        this.antler_left = this.head.getChild("antler_left");
        this.antler_right = this.head.getChild("antler_right");
        this.gore = this.mouse.getChild("gore");
        this.front_left_knee = this.front_left.getChild("front_left_knee");
        this.front_right_knee = this.front_right.getChild("front_right_knee");
        this.hind_left_knee = this.hind_left.getChild("hind_left_knee");
        this.hind_right_knee = this.hind_right.getChild("hind_right_knee");
        this.gore.visible = false;
    }
    @Override public ModelPart root() { return root; }
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition base = mesh.getRoot();
        PartDefinition mouse = base.addOrReplaceChild("mouse", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 24.0F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition body = mouse.addOrReplaceChild("body", CubeListBuilder.create().texOffs(1, 1).addBox(-4.25F, -2.5F, 0.278499879F, 8.5F, 5.0F, 8.5F).texOffs(37, 1).addBox(-3.25F, -4.18272479F, 1.02849988F, 6.5F, 2.5F, 7.0F).texOffs(66, 1).addBox(-3.25F, 1.68272479F, 1.02849988F, 6.5F, 2.5F, 7.0F).texOffs(1, 17).addBox(-3.5F, 1.83244348F, -7.25F, 7.0F, 2.5F, 14.5F).texOffs(40, 44).addBox(-2.75F, 2.99296708F, -8.25F, 5.5F, 1.5F, 16.5F).texOffs(1, 64).addBox(-3.75F, 1.33385006F, -8.75F, 7.5F, 2.0F, 17.5F).texOffs(53, 64).addBox(-4.25F, -1.75F, -9.25F, 8.5F, 3.5F, 18.5F).texOffs(1, 88).addBox(-3.75F, -3.33385006F, -8.75F, 7.5F, 2.0F, 17.5F).texOffs(53, 88).addBox(-2.75F, -4.49296708F, -8.25F, 5.5F, 1.5F, 16.5F), PartPose.offsetAndRotation(-0.0F, -12.0748898F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(46, 17).addBox(-2.25F, -0.0580228931F, -1.41396925F, 4.5F, 1.5F, 4.5F).texOffs(66, 17).addBox(-1.75F, -1.25F, -3.0F, 3.5F, 2.5F, 6.0F).texOffs(87, 17).addBox(-1.25F, -2.33274036F, -2.5F, 2.5F, 1.5F, 5.0F).texOffs(104, 17).addBox(-1.25F, 0.832740365F, -2.5F, 2.5F, 1.5F, 5.0F).texOffs(1, 36).addBox(-1.25F, -0.370929903F, -5.09412299F, 2.5F, 2.0F, 3.5F).texOffs(15, 36).addBox(-0.5F, -0.0219067261F, -5.19651525F, 1.0F, 1.0F, 0.5F).texOffs(20, 36).addBox(-1.3125F, -1.25488412F, -3.10643839F, 0.5F, 1.0F, 0.5F).texOffs(24, 36).addBox(0.8125F, -1.25488412F, -3.10643839F, 0.5F, 1.0F, 0.5F).texOffs(22, 44).addBox(-0.75F, 1.76953647F, -3.53676916F, 1.5F, 2.5F, 1.5F), PartPose.offsetAndRotation(-0.0F, -3.14535049F, -7.97015979F, -0.0F, -0.0F, 0.0F));
        PartDefinition ear_left = head.addOrReplaceChild("ear_left", CubeListBuilder.create().texOffs(28, 36).addBox(-0.75F, -1.94360515F, -0.5F, 1.5F, 2.0F, 1.0F), PartPose.offsetAndRotation(-1.275F, -1.76139627F, 0.975369205F, -0.0F, -0.0F, -0.20943951F));
        PartDefinition ear_right = head.addOrReplaceChild("ear_right", CubeListBuilder.create().texOffs(35, 36).addBox(-0.75F, -1.94360515F, -0.5F, 1.5F, 2.0F, 1.0F), PartPose.offsetAndRotation(1.275F, -1.76139627F, 0.975369205F, -0.0F, -0.0F, 0.20943951F));
        PartDefinition front_left = body.addOrReplaceChild("front_left", CubeListBuilder.create().texOffs(42, 36).addBox(-0.75F, -0.300382841F, -1.0F, 1.5F, 4.0F, 2.0F), PartPose.offsetAndRotation(-3.06F, 4.15186264F, -5.97761984F, -0.0F, -0.0F, 0.0F));
        PartDefinition front_right = body.addOrReplaceChild("front_right", CubeListBuilder.create().texOffs(63, 36).addBox(-0.75F, -0.300382841F, -1.0F, 1.5F, 4.0F, 2.0F), PartPose.offsetAndRotation(3.06F, 4.15186264F, -5.97761984F, -0.0F, -0.0F, 0.0F));
        PartDefinition hind_left = body.addOrReplaceChild("hind_left", CubeListBuilder.create().texOffs(84, 36).addBox(-0.75F, -0.300382841F, -1.0F, 1.5F, 4.0F, 2.0F), PartPose.offsetAndRotation(-3.06F, 4.15186264F, 5.61533985F, -0.0F, -0.0F, 0.0F));
        PartDefinition hind_right = body.addOrReplaceChild("hind_right", CubeListBuilder.create().texOffs(105, 36).addBox(-0.75F, -0.300382841F, -1.0F, 1.5F, 4.0F, 2.0F), PartPose.offsetAndRotation(3.06F, 4.15186264F, 5.61533985F, -0.0F, -0.0F, 0.0F));
        PartDefinition tail_0 = body.addOrReplaceChild("tail_0", CubeListBuilder.create().texOffs(1, 44).addBox(-0.5F, -0.75F, -0.285538474F, 1.0F, 1.5F, 1.5F), PartPose.offsetAndRotation(-0.0F, -2.20174534F, 9.05699976F, -0.122173048F, -0.20943951F, 0.0F));
        PartDefinition tail_1 = tail_0.addOrReplaceChild("tail_1", CubeListBuilder.create().texOffs(8, 44).addBox(-0.5F, -0.5F, -0.285538474F, 1.0F, 1.0F, 1.5F), PartPose.offsetAndRotation(-0.0F, 0.0F, 0.928923052F, -0.122173048F, 0.20943951F, 0.0F));
        PartDefinition tail_2 = tail_1.addOrReplaceChild("tail_2", CubeListBuilder.create().texOffs(15, 44).addBox(-0.5F, -0.5F, -0.285538474F, 1.0F, 1.0F, 1.5F), PartPose.offsetAndRotation(-0.0F, 0.0F, 0.928923052F, -0.122173048F, -0.20943951F, 0.0F));
        PartDefinition antler_left = head.addOrReplaceChild("antler_left", CubeListBuilder.create().texOffs(30, 44).addBox(-0.25F, -3.25976823F, -0.5F, 0.5F, 3.5F, 1.0F), PartPose.offsetAndRotation(-1.16875F, -2.01302431F, 0.0F, -0.0F, -0.0F, -0.383972435F));
        PartDefinition antler_right = head.addOrReplaceChild("antler_right", CubeListBuilder.create().texOffs(35, 44).addBox(-0.25F, -3.25976823F, -0.5F, 0.5F, 3.5F, 1.0F), PartPose.offsetAndRotation(1.16875F, -2.01302431F, 0.0F, -0.0F, -0.0F, 0.383972435F));
        PartDefinition gore = mouse.addOrReplaceChild("gore", CubeListBuilder.create().texOffs(99, 88).addBox(-1.38125F, -0.0503256078F, -1.74173072F, 2.7625F, 0.0377442058F, 3.48346145F), PartPose.offsetAndRotation(-0.0F, 0.0F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition front_left_knee = front_left.addOrReplaceChild("front_left_knee", CubeListBuilder.create().texOffs(51, 36).addBox(-1.0F, 3.412816F, -1.77867692F, 2.0F, 1.0F, 3.0F).texOffs(114, 88).addBox(-0.75F, -0.169406017F, -1.0F, 1.5F, 4.0F, 2.0F), PartPose.offsetAndRotation(-0.0F, 3.51021114F, -8.8817842e-16F, -0.0F, -0.0F, 0.0F));
        PartDefinition front_right_knee = front_right.addOrReplaceChild("front_right_knee", CubeListBuilder.create().texOffs(72, 36).addBox(-1.0F, 3.412816F, -1.77867692F, 2.0F, 1.0F, 3.0F).texOffs(1, 110).addBox(-0.75F, -0.169406017F, -1.0F, 1.5F, 4.0F, 2.0F), PartPose.offsetAndRotation(-0.0F, 3.51021114F, -8.8817842e-16F, -0.0F, -0.0F, 0.0F));
        PartDefinition hind_left_knee = hind_left.addOrReplaceChild("hind_left_knee", CubeListBuilder.create().texOffs(93, 36).addBox(-1.0F, 3.412816F, -1.77867692F, 2.0F, 1.0F, 3.0F).texOffs(10, 110).addBox(-0.75F, -0.169406017F, -1.0F, 1.5F, 4.0F, 2.0F), PartPose.offsetAndRotation(-0.0F, 3.51021114F, -8.8817842e-16F, -0.0F, -0.0F, 0.0F));
        PartDefinition hind_right_knee = hind_right.addOrReplaceChild("hind_right_knee", CubeListBuilder.create().texOffs(114, 36).addBox(-1.0F, 3.412816F, -1.77867692F, 2.0F, 1.0F, 3.0F).texOffs(19, 110).addBox(-0.75F, -0.169406017F, -1.0F, 1.5F, 4.0F, 2.0F), PartPose.offsetAndRotation(-0.0F, 3.51021114F, -8.8817842e-16F, -0.0F, -0.0F, 0.0F));
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
            throw new IllegalArgumentException("Nonfinite goat presentation");
        root.getAllParts().forEach(ModelPart::resetPose);
        gore.visible = false; // Independent source blood state is OFF by default, even Dead.
        if (dead) clip = "down_hold";
        if (clip != null && !clip.equals("rest")) {
            KeyframeAnimations.animate(this, AnimalGoatAnimations.byName(clip),
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
