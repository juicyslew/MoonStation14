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

/** Generated SS14 reindeer_buck asset rig. CC-BY-SA3.0 art; retain ATTRIBUTION.md.
 * Native1.21.1 API, based on pinned Blockbench4.12.6 geometry conventions.
 * NOT a registered entity/renderer/AI/networking implementation.
 */
public final class AnimalReindeerBuckModel<T extends LivingEntity> extends HierarchicalModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
        ResourceLocation.fromNamespaceAndPath("moonstation14", "animals/reindeer_buck"), "main");
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
    public AnimalReindeerBuckModel(ModelPart root) {
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
        PartDefinition body = mouse.addOrReplaceChild("body", CubeListBuilder.create().texOffs(30, 20).addBox(-2.0F, 3.90416107F, -12.25F, 4.0F, 2.0F, 24.5F).texOffs(89, 20).addBox(-2.75F, 1.80788867F, -13.25F, 5.5F, 2.5F, 26.5F).texOffs(155, 20).addBox(-3.25F, -2.25F, -13.75F, 6.5F, 4.5F, 27.5F).texOffs(1, 54).addBox(-2.75F, -4.30788867F, -13.25F, 5.5F, 2.5F, 26.5F).texOffs(67, 54).addBox(-2.0F, -5.90416107F, -12.25F, 4.0F, 2.0F, 24.5F).texOffs(126, 54).addBox(-2.0F, 3.68355867F, 1.22732064F, 4.0F, 2.0F, 11.0F).texOffs(158, 54).addBox(-2.75F, 1.94589052F, 0.977320645F, 5.5F, 2.0F, 11.5F).texOffs(194, 54).addBox(-3.25F, -2.18212234F, 0.727320645F, 6.5F, 4.5F, 12.0F).texOffs(1, 85).addBox(-2.75F, -3.8101352F, 0.977320645F, 5.5F, 2.0F, 11.5F).texOffs(37, 85).addBox(-2.0F, -5.54780334F, 1.22732064F, 4.0F, 2.0F, 11.0F), PartPose.offsetAndRotation(-0.0F, -19.4378677F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(1, 1).addBox(-1.5F, -0.0697870511F, -1.65908314F, 3.0F, 11.0F, 5.5F).texOffs(20, 1).addBox(-1.75F, -1.5F, -3.75F, 3.5F, 3.0F, 7.5F).texOffs(44, 1).addBox(-1.25F, -2.45780197F, -3.0F, 2.5F, 1.5F, 6.0F).texOffs(63, 1).addBox(-1.25F, 0.957801972F, -3.0F, 2.5F, 1.5F, 6.0F).texOffs(82, 1).addBox(-1.25F, -0.571223381F, -6.61366745F, 2.5F, 2.5F, 4.5F).texOffs(98, 1).addBox(-0.5F, 0.0158702301F, -6.70459143F, 1.0F, 1.0F, 0.5F).texOffs(103, 1).addBox(-1.30749648F, -1.31453194F, -3.97729928F, 0.5F, 1.0F, 0.5F).texOffs(107, 1).addBox(0.807496482F, -1.31453194F, -3.97729928F, 0.5F, 1.0F, 0.5F), PartPose.offsetAndRotation(-0.0F, -11.8785908F, -12.0000855F, -0.0F, -0.0F, 0.0F));
        PartDefinition ear_left = head.addOrReplaceChild("ear_left", CubeListBuilder.create().texOffs(111, 1).addBox(-0.75F, -2.26816493F, -0.5F, 1.5F, 2.5F, 1.0F), PartPose.offsetAndRotation(-1.26899578F, -1.90057453F, 1.27273634F, -0.0F, -0.0F, -0.20943951F));
        PartDefinition ear_right = head.addOrReplaceChild("ear_right", CubeListBuilder.create().texOffs(118, 1).addBox(-0.75F, -2.26816493F, -0.5F, 1.5F, 2.5F, 1.0F), PartPose.offsetAndRotation(1.26899578F, -1.90057453F, 1.27273634F, -0.0F, -0.0F, 0.20943951F));
        PartDefinition front_left = body.addOrReplaceChild("front_left", CubeListBuilder.create().texOffs(125, 1).addBox(-0.5F, -0.310113191F, -0.75F, 1.0F, 7.0F, 1.5F), PartPose.offsetAndRotation(-2.2841924F, 5.49809061F, -9.00006411F, -0.0F, -0.0F, 0.0F));
        PartDefinition front_right = body.addOrReplaceChild("front_right", CubeListBuilder.create().texOffs(140, 1).addBox(-0.5F, -0.310113191F, -0.75F, 1.0F, 7.0F, 1.5F), PartPose.offsetAndRotation(2.2841924F, 5.49809061F, -9.00006411F, -0.0F, -0.0F, 0.0F));
        PartDefinition hind_left = body.addOrReplaceChild("hind_left", CubeListBuilder.create().texOffs(155, 1).addBox(-0.5F, -0.310113191F, -0.75F, 1.0F, 7.0F, 1.5F), PartPose.offsetAndRotation(-2.2841924F, 5.49809061F, 8.45460568F, -0.0F, -0.0F, 0.0F));
        PartDefinition hind_right = body.addOrReplaceChild("hind_right", CubeListBuilder.create().texOffs(170, 1).addBox(-0.5F, -0.310113191F, -0.75F, 1.0F, 7.0F, 1.5F), PartPose.offsetAndRotation(2.2841924F, 5.49809061F, 8.45460568F, -0.0F, -0.0F, 0.0F));
        PartDefinition tail_0 = body.addOrReplaceChild("tail_0", CubeListBuilder.create().texOffs(185, 1).addBox(-0.5F, -0.75F, -0.143935077F, 1.0F, 1.5F, 1.5F), PartPose.offsetAndRotation(-0.0F, 0.678776619F, 13.6364608F, -0.122173048F, -0.20943951F, 0.0F));
        PartDefinition tail_1 = tail_0.addOrReplaceChild("tail_1", CubeListBuilder.create().texOffs(192, 1).addBox(-0.5F, -0.5F, -0.143935077F, 1.0F, 1.0F, 1.5F), PartPose.offsetAndRotation(-0.0F, 0.0F, 1.21212985F, -0.122173048F, 0.20943951F, 0.0F));
        PartDefinition tail_2 = tail_1.addOrReplaceChild("tail_2", CubeListBuilder.create().texOffs(199, 1).addBox(-0.5F, -0.5F, -0.143935077F, 1.0F, 1.0F, 1.5F), PartPose.offsetAndRotation(-0.0F, 0.0F, 1.21212985F, -0.122173048F, -0.20943951F, 0.0F));
        PartDefinition antler_left = head.addOrReplaceChild("antler_left", CubeListBuilder.create().texOffs(206, 1).addBox(-0.25F, -3.37906388F, -0.75F, 0.5F, 3.5F, 1.5F).texOffs(212, 1).addBox(-2.55749648F, -3.48661712F, -0.75F, 3.0F, 1.0F, 1.5F).texOffs(223, 1).addBox(-0.408624472F, -5.18690439F, -0.75F, 0.5F, 2.5F, 1.5F).texOffs(229, 1).addBox(-1.30749648F, -5.18690439F, -0.75F, 0.5F, 2.5F, 1.5F).texOffs(235, 1).addBox(-2.20636849F, -5.18690439F, -0.75F, 0.5F, 2.5F, 1.5F), PartPose.offsetAndRotation(-1.16324613F, -2.17208518F, 0.0F, -0.0F, -0.0F, -0.383972435F));
        PartDefinition antler_right = head.addOrReplaceChild("antler_right", CubeListBuilder.create().texOffs(241, 1).addBox(-0.25F, -3.37906388F, -0.75F, 0.5F, 3.5F, 1.5F).texOffs(1, 20).addBox(-0.442503518F, -3.48661712F, -0.75F, 3.0F, 1.0F, 1.5F).texOffs(12, 20).addBox(-0.0913755277F, -5.18690439F, -0.75F, 0.5F, 2.5F, 1.5F).texOffs(18, 20).addBox(0.807496482F, -5.18690439F, -0.75F, 0.5F, 2.5F, 1.5F).texOffs(24, 20).addBox(1.70636849F, -5.18690439F, -0.75F, 0.5F, 2.5F, 1.5F), PartPose.offsetAndRotation(1.16324613F, -2.17208518F, 0.0F, -0.0F, -0.0F, 0.383972435F));
        PartDefinition gore = mouse.addOrReplaceChild("gore", CubeListBuilder.create().texOffs(69, 85).addBox(-1.37474543F, -0.0543021295F, -2.27274346F, 2.74949085F, 0.0407265971F, 4.54548692F), PartPose.offsetAndRotation(-0.0F, 0.0F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition front_left_knee = front_left.addOrReplaceChild("front_left_knee", CubeListBuilder.create().texOffs(132, 1).addBox(-0.5F, 6.43709704F, -1.36363895F, 1.0F, 1.0F, 2.0F).texOffs(86, 85).addBox(-0.5F, -0.167206803F, -0.75F, 1.0F, 7.0F, 1.5F), PartPose.offsetAndRotation(4.4408921e-16F, 6.50268001F, 1.77635684e-15F, -0.0F, -0.0F, 0.0F));
        PartDefinition front_right_knee = front_right.addOrReplaceChild("front_right_knee", CubeListBuilder.create().texOffs(147, 1).addBox(-0.5F, 6.43709704F, -1.36363895F, 1.0F, 1.0F, 2.0F).texOffs(93, 85).addBox(-0.5F, -0.167206803F, -0.75F, 1.0F, 7.0F, 1.5F), PartPose.offsetAndRotation(-4.4408921e-16F, 6.50268001F, 1.77635684e-15F, -0.0F, -0.0F, 0.0F));
        PartDefinition hind_left_knee = hind_left.addOrReplaceChild("hind_left_knee", CubeListBuilder.create().texOffs(162, 1).addBox(-0.5F, 6.43709704F, -1.36363895F, 1.0F, 1.0F, 2.0F).texOffs(100, 85).addBox(-0.5F, -0.167206803F, -0.75F, 1.0F, 7.0F, 1.5F), PartPose.offsetAndRotation(4.4408921e-16F, 6.50268001F, -1.77635684e-15F, -0.0F, -0.0F, 0.0F));
        PartDefinition hind_right_knee = hind_right.addOrReplaceChild("hind_right_knee", CubeListBuilder.create().texOffs(177, 1).addBox(-0.5F, 6.43709704F, -1.36363895F, 1.0F, 1.0F, 2.0F).texOffs(107, 85).addBox(-0.5F, -0.167206803F, -0.75F, 1.0F, 7.0F, 1.5F), PartPose.offsetAndRotation(-4.4408921e-16F, 6.50268001F, -1.77635684e-15F, -0.0F, -0.0F, 0.0F));
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
            throw new IllegalArgumentException("Nonfinite reindeer_buck presentation");
        root.getAllParts().forEach(ModelPart::resetPose);
        gore.visible = false; // Independent source blood state is OFF by default, even Dead.
        if (dead) clip = "down_hold";
        if (clip != null && !clip.equals("rest")) {
            KeyframeAnimations.animate(this, AnimalReindeerBuckAnimations.byName(clip),
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
