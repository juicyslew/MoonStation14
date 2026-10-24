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

/** Generated SS14 reindeer_doe asset rig. CC-BY-SA3.0 art; retain ATTRIBUTION.md.
 * Native1.21.1 API, based on pinned Blockbench4.12.6 geometry conventions.
 * NOT a registered entity/renderer/AI/networking implementation.
 */
public final class AnimalReindeerDoeModel<T extends LivingEntity> extends HierarchicalModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
        ResourceLocation.fromNamespaceAndPath("moonstation14", "animals/reindeer_doe"), "main");
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
    private final ModelPart front_left_knee;
    private final ModelPart front_right_knee;
    private final ModelPart hind_left_knee;
    private final ModelPart hind_right_knee;
    public AnimalReindeerDoeModel(ModelPart root) {
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
        PartDefinition body = mouse.addOrReplaceChild("body", CubeListBuilder.create().texOffs(1, 19).addBox(-2.75F, 3.72772693F, -11.75F, 5.5F, 2.0F, 23.5F).texOffs(61, 19).addBox(-3.75F, 1.69787679F, -12.5F, 7.5F, 2.5F, 25.0F).texOffs(128, 19).addBox(-4.25F, -2.25F, -13.0F, 8.5F, 4.5F, 26.0F).texOffs(1, 52).addBox(-3.75F, -4.19787679F, -12.5F, 7.5F, 2.5F, 25.0F).texOffs(68, 52).addBox(-2.75F, -5.72772693F, -11.75F, 5.5F, 2.0F, 23.5F).texOffs(128, 52).addBox(-2.75F, 3.51506101F, 1.34929762F, 5.5F, 2.0F, 10.0F).texOffs(161, 52).addBox(-3.5F, 1.83990794F, 0.84929762F, 7.0F, 2.0F, 11.0F).texOffs(199, 52).addBox(-4.25F, -1.93456433F, 0.59929762F, 8.5F, 4.0F, 11.5F).texOffs(1, 82).addBox(-3.5F, -3.70903661F, 0.84929762F, 7.0F, 2.0F, 11.0F).texOffs(39, 82).addBox(-2.75F, -5.38418968F, 1.34929762F, 5.5F, 2.0F, 10.0F), PartPose.offsetAndRotation(-0.0F, -18.756551F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(1, 1).addBox(-2.0F, -0.0151466551F, -1.72038417F, 4.0F, 10.5F, 5.5F).texOffs(22, 1).addBox(-2.25F, -1.5F, -3.5F, 4.5F, 3.0F, 7.0F).texOffs(47, 1).addBox(-1.75F, -2.39636138F, -3.0F, 3.5F, 1.5F, 6.0F).texOffs(68, 1).addBox(-1.75F, 0.896361377F, -3.0F, 3.5F, 1.5F, 6.0F).texOffs(89, 1).addBox(-1.5F, -0.345643332F, -6.11846332F, 3.0F, 2.0F, 4.0F).texOffs(105, 1).addBox(-0.75F, -0.00268893223F, -6.34189366F, 1.5F, 1.0F, 0.5F).texOffs(111, 1).addBox(-1.91666667F, -1.285228F, -3.76785409F, 1.0F, 1.0F, 0.5F).texOffs(116, 1).addBox(0.916666667F, -1.285228F, -3.76785409F, 1.0F, 1.0F, 0.5F), PartPose.offsetAndRotation(-0.0F, -11.4512417F, -11.3257741F, -0.0F, -0.0F, 0.0F));
        PartDefinition ear_left = head.addOrReplaceChild("ear_left", CubeListBuilder.create().texOffs(121, 1).addBox(-0.75F, -1.981535F, -0.5F, 1.5F, 2.0F, 1.0F), PartPose.offsetAndRotation(-1.7F, -1.83219867F, 1.20121847F, -0.0F, -0.0F, -0.20943951F));
        PartDefinition ear_right = head.addOrReplaceChild("ear_right", CubeListBuilder.create().texOffs(128, 1).addBox(-0.75F, -1.981535F, -0.5F, 1.5F, 2.0F, 1.0F), PartPose.offsetAndRotation(1.7F, -1.83219867F, 1.20121847F, -0.0F, -0.0F, 0.20943951F));
        PartDefinition front_left = body.addOrReplaceChild("front_left", CubeListBuilder.create().texOffs(135, 1).addBox(-0.5F, -0.17415436F, -0.75F, 1.0F, 6.5F, 1.5F), PartPose.offsetAndRotation(-3.06F, 5.30028901F, -8.4943306F, -0.0F, -0.0F, 0.0F));
        PartDefinition front_right = body.addOrReplaceChild("front_right", CubeListBuilder.create().texOffs(151, 1).addBox(-0.5F, -0.17415436F, -0.75F, 1.0F, 6.5F, 1.5F), PartPose.offsetAndRotation(3.06F, 5.30028901F, -8.4943306F, -0.0F, -0.0F, 0.0F));
        PartDefinition hind_left = body.addOrReplaceChild("hind_left", CubeListBuilder.create().texOffs(167, 1).addBox(-0.5F, -0.17415436F, -0.75F, 1.0F, 6.5F, 1.5F), PartPose.offsetAndRotation(-3.06F, 5.30028901F, 7.97952268F, -0.0F, -0.0F, 0.0F));
        PartDefinition hind_right = body.addOrReplaceChild("hind_right", CubeListBuilder.create().texOffs(183, 1).addBox(-0.5F, -0.17415436F, -0.75F, 1.0F, 6.5F, 1.5F), PartPose.offsetAndRotation(3.06F, 5.30028901F, 7.97952268F, -0.0F, -0.0F, 0.0F));
        PartDefinition tail_0 = body.addOrReplaceChild("tail_0", CubeListBuilder.create().texOffs(199, 1).addBox(-0.75F, -0.75F, -0.177991205F, 1.5F, 1.5F, 1.5F), PartPose.offsetAndRotation(-0.0F, 0.654356668F, 12.8701979F, -0.122173048F, -0.20943951F, 0.0F));
        PartDefinition tail_1 = tail_0.addOrReplaceChild("tail_1", CubeListBuilder.create().texOffs(207, 1).addBox(-0.5F, -0.5F, -0.177991205F, 1.0F, 1.0F, 1.5F), PartPose.offsetAndRotation(-0.0F, 0.0F, 1.14401759F, -0.122173048F, 0.20943951F, 0.0F));
        PartDefinition tail_2 = tail_1.addOrReplaceChild("tail_2", CubeListBuilder.create().texOffs(214, 1).addBox(-0.5F, -0.5F, -0.177991205F, 1.0F, 1.0F, 1.5F), PartPose.offsetAndRotation(-0.0F, 0.0F, 1.14401759F, -0.122173048F, -0.20943951F, 0.0F));
        PartDefinition gore = mouse.addOrReplaceChild("gore", CubeListBuilder.create().texOffs(72, 82).addBox(-1.84166667F, -0.0523485334F, -2.14503298F, 3.68333333F, 0.0392614001F, 4.29006596F), PartPose.offsetAndRotation(-0.0F, 0.0F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition front_left_knee = front_left.addOrReplaceChild("front_left_knee", CubeListBuilder.create().texOffs(142, 1).addBox(-0.75F, 6.18752515F, -1.34320528F, 1.5F, 1.0F, 2.0F).texOffs(90, 82).addBox(-0.5F, -0.0371087595F, -0.75F, 1.0F, 6.5F, 1.5F), PartPose.offsetAndRotation(-0.0F, 6.26873688F, -1.77635684e-15F, -0.0F, -0.0F, 0.0F));
        PartDefinition front_right_knee = front_right.addOrReplaceChild("front_right_knee", CubeListBuilder.create().texOffs(158, 1).addBox(-0.75F, 6.18752515F, -1.34320528F, 1.5F, 1.0F, 2.0F).texOffs(97, 82).addBox(-0.5F, -0.0371087595F, -0.75F, 1.0F, 6.5F, 1.5F), PartPose.offsetAndRotation(-0.0F, 6.26873688F, -1.77635684e-15F, -0.0F, -0.0F, 0.0F));
        PartDefinition hind_left_knee = hind_left.addOrReplaceChild("hind_left_knee", CubeListBuilder.create().texOffs(174, 1).addBox(-0.75F, 6.18752515F, -1.34320528F, 1.5F, 1.0F, 2.0F).texOffs(104, 82).addBox(-0.5F, -0.0371087595F, -0.75F, 1.0F, 6.5F, 1.5F), PartPose.offsetAndRotation(-0.0F, 6.26873688F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition hind_right_knee = hind_right.addOrReplaceChild("hind_right_knee", CubeListBuilder.create().texOffs(190, 1).addBox(-0.75F, 6.18752515F, -1.34320528F, 1.5F, 1.0F, 2.0F).texOffs(111, 82).addBox(-0.5F, -0.0371087595F, -0.75F, 1.0F, 6.5F, 1.5F), PartPose.offsetAndRotation(-0.0F, 6.26873688F, 0.0F, -0.0F, -0.0F, 0.0F));
        // Logical UV dimensions from spec; physical PNG has2x texels, NOT doubled geometry.
        return LayerDefinition.create(mesh, 256, 256);
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
            throw new IllegalArgumentException("Nonfinite reindeer_doe presentation");
        root.getAllParts().forEach(ModelPart::resetPose);
        gore.visible = false; // Independent source blood state is OFF by default, even Dead.
        if (dead) clip = "down_hold";
        if (clip != null && !clip.equals("rest")) {
            KeyframeAnimations.animate(this, AnimalReindeerDoeAnimations.byName(clip),
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
