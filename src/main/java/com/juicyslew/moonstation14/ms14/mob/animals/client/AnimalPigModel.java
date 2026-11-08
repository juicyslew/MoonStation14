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

/** Generated SS14 pig asset rig. CC-BY-SA3.0 art; retain ATTRIBUTION.md.
 * Native1.21.1 API, based on pinned Blockbench4.12.6 geometry conventions.
 * NOT a registered entity/renderer/AI/networking implementation.
 */
public final class AnimalPigModel<T extends LivingEntity> extends HierarchicalModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
        ResourceLocation.fromNamespaceAndPath("moonstation14", "animals/pig"), "main");
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
    public AnimalPigModel(ModelPart root) {
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
        PartDefinition body = mouse.addOrReplaceChild("body", CubeListBuilder.create().texOffs(1, 1).addBox(-6.0F, -2.75F, 0.404034752F, 12.0F, 5.5F, 8.0F).texOffs(43, 1).addBox(-4.75F, -4.64169053F, 0.904034752F, 9.5F, 2.5F, 7.0F).texOffs(78, 1).addBox(-4.75F, 2.14169053F, 0.904034752F, 9.5F, 2.5F, 7.0F).texOffs(1, 17).addBox(-5.0F, 2.3148399F, -7.25F, 10.0F, 2.5F, 14.5F).texOffs(66, 45).addBox(-3.75F, 3.32873416F, -8.0F, 7.5F, 2.0F, 16.0F).texOffs(1, 65).addBox(-5.25F, 1.69909307F, -8.5F, 10.5F, 2.0F, 17.0F).texOffs(58, 65).addBox(-6.0F, -2.0F, -9.0F, 12.0F, 4.0F, 18.0F).texOffs(1, 89).addBox(-5.25F, -3.69909307F, -8.5F, 10.5F, 2.0F, 17.0F).texOffs(58, 89).addBox(-3.75F, -5.32873416F, -8.0F, 7.5F, 2.0F, 16.0F), PartPose.offsetAndRotation(-0.0F, -10.425994F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(52, 17).addBox(-2.0F, -1.16975428F, -1.43694743F, 4.0F, 2.0F, 4.5F).texOffs(71, 17).addBox(-3.25F, -2.0F, -2.75F, 6.5F, 4.0F, 5.5F).texOffs(97, 17).addBox(-2.75F, -3.51236336F, -2.25F, 5.5F, 2.0F, 4.5F).texOffs(1, 36).addBox(-2.75F, 1.51236336F, -2.25F, 5.5F, 2.0F, 4.5F).texOffs(23, 36).addBox(-2.5F, -0.651228596F, -4.75221028F, 5.0F, 3.0F, 3.0F).texOffs(41, 36).addBox(-1.75F, -0.104933733F, -5.06056104F, 3.5F, 1.5F, 0.5F).texOffs(51, 36).addBox(-2.6F, -1.51852569F, -3.02792961F, 1.0F, 1.0F, 0.5F).texOffs(56, 36).addBox(1.6F, -1.51852569F, -3.02792961F, 1.0F, 1.0F, 0.5F), PartPose.offsetAndRotation(-0.0F, -1.69754281F, -7.75110116F, -0.0F, -0.0F, 0.0F));
        PartDefinition ear_left = head.addOrReplaceChild("ear_left", CubeListBuilder.create().texOffs(61, 36).addBox(-1.25F, -2.77315711F, -0.5F, 2.5F, 3.0F, 1.0F), PartPose.offsetAndRotation(-2.475F, -2.37655993F, 0.948561331F, -0.0F, -0.0F, -0.20943951F));
        PartDefinition ear_right = head.addOrReplaceChild("ear_right", CubeListBuilder.create().texOffs(70, 36).addBox(-1.25F, -2.77315711F, -0.5F, 2.5F, 3.0F, 1.0F), PartPose.offsetAndRotation(2.475F, -2.37655993F, 0.948561331F, -0.0F, -0.0F, 0.20943951F));
        PartDefinition front_left = body.addOrReplaceChild("front_left", CubeListBuilder.create().texOffs(79, 36).addBox(-1.0F, -0.236937457F, -1.0F, 2.0F, 2.5F, 2.0F), PartPose.offsetAndRotation(-4.32F, 4.75311986F, -5.81332587F, -0.0F, -0.0F, 0.0F));
        PartDefinition front_right = body.addOrReplaceChild("front_right", CubeListBuilder.create().texOffs(102, 36).addBox(-1.0F, -0.236937457F, -1.0F, 2.0F, 2.5F, 2.0F), PartPose.offsetAndRotation(4.32F, 4.75311986F, -5.81332587F, -0.0F, -0.0F, 0.0F));
        PartDefinition hind_left = body.addOrReplaceChild("hind_left", CubeListBuilder.create().texOffs(1, 45).addBox(-1.0F, -0.236937457F, -1.0F, 2.0F, 2.5F, 2.0F), PartPose.offsetAndRotation(-4.32F, 4.75311986F, 5.46100309F, -0.0F, -0.0F, 0.0F));
        PartDefinition hind_right = body.addOrReplaceChild("hind_right", CubeListBuilder.create().texOffs(24, 45).addBox(-1.0F, -0.236937457F, -1.0F, 2.0F, 2.5F, 2.0F), PartPose.offsetAndRotation(4.32F, 4.75311986F, 5.46100309F, -0.0F, -0.0F, 0.0F));
        PartDefinition tail_0 = body.addOrReplaceChild("tail_0", CubeListBuilder.create().texOffs(47, 45).addBox(-0.5F, -0.5F, -0.298304128F, 1.0F, 1.0F, 1.5F), PartPose.offsetAndRotation(-0.0F, -2.54631421F, 8.8080695F, 0.261799388F, -0.20943951F, 0.0F));
        PartDefinition tail_1 = tail_0.addOrReplaceChild("tail_1", CubeListBuilder.create().texOffs(54, 45).addBox(-0.25F, -0.5F, -0.298304128F, 0.5F, 1.0F, 1.5F), PartPose.offsetAndRotation(-0.0F, 0.0F, 0.903391744F, 0.261799388F, 0.20943951F, 0.0F));
        PartDefinition tail_2 = tail_1.addOrReplaceChild("tail_2", CubeListBuilder.create().texOffs(60, 45).addBox(-0.25F, -0.25F, -0.298304128F, 0.5F, 0.5F, 1.5F), PartPose.offsetAndRotation(-0.0F, 0.0F, 0.903391744F, 0.261799388F, -0.20943951F, 0.0F));
        PartDefinition gore = mouse.addOrReplaceChild("gore", CubeListBuilder.create().texOffs(107, 89).addBox(-1.95F, -0.0679017123F, -1.69385952F, 3.9F, 0.0509262843F, 3.38771904F), PartPose.offsetAndRotation(-0.0F, 0.0F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition front_left_knee = front_left.addOrReplaceChild("front_left_knee", CubeListBuilder.create().texOffs(89, 36).addBox(-1.25F, 1.98304392F, -1.77101752F, 2.5F, 1.5F, 3.0F).texOffs(1, 110).addBox(-1.0F, -0.05323232F, -1.0F, 2.0F, 2.5F, 2.0F), PartPose.offsetAndRotation(-0.0F, 2.18983022F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition front_right_knee = front_right.addOrReplaceChild("front_right_knee", CubeListBuilder.create().texOffs(112, 36).addBox(-1.25F, 1.98304392F, -1.77101752F, 2.5F, 1.5F, 3.0F).texOffs(11, 110).addBox(-1.0F, -0.05323232F, -1.0F, 2.0F, 2.5F, 2.0F), PartPose.offsetAndRotation(-0.0F, 2.18983022F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition hind_left_knee = hind_left.addOrReplaceChild("hind_left_knee", CubeListBuilder.create().texOffs(11, 45).addBox(-1.25F, 1.98304392F, -1.77101752F, 2.5F, 1.5F, 3.0F).texOffs(21, 110).addBox(-1.0F, -0.05323232F, -1.0F, 2.0F, 2.5F, 2.0F), PartPose.offsetAndRotation(-0.0F, 2.18983022F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition hind_right_knee = hind_right.addOrReplaceChild("hind_right_knee", CubeListBuilder.create().texOffs(34, 45).addBox(-1.25F, 1.98304392F, -1.77101752F, 2.5F, 1.5F, 3.0F).texOffs(31, 110).addBox(-1.0F, -0.05323232F, -1.0F, 2.0F, 2.5F, 2.0F), PartPose.offsetAndRotation(-0.0F, 2.18983022F, 0.0F, -0.0F, -0.0F, 0.0F));
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
            throw new IllegalArgumentException("Nonfinite pig presentation");
        root.getAllParts().forEach(ModelPart::resetPose);
        gore.visible = false; // Independent source blood state is OFF by default, even Dead.
        if (dead) clip = "down_hold";
        if (clip != null && !clip.equals("rest")) {
            KeyframeAnimations.animate(this, AnimalPigAnimations.byName(clip),
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
