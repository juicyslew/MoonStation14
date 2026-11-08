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

/** Generated SS14 possum asset rig. CC-BY-SA3.0 art; retain ATTRIBUTION.md.
 * Native1.21.1 API, based on pinned Blockbench4.12.6 geometry conventions.
 * NOT a registered entity/renderer/AI/networking implementation.
 */
public final class AnimalPossumModel<T extends LivingEntity> extends HierarchicalModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
        ResourceLocation.fromNamespaceAndPath("moonstation14", "animals/possum"), "main");
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
    public AnimalPossumModel(ModelPart root) {
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
        PartDefinition body = mouse.addOrReplaceChild("body", CubeListBuilder.create().texOffs(1, 1).addBox(-3.5F, -1.52031233F, -5.13617056F, 7.0F, 3.04062466F, 10.2723411F).texOffs(1, 17).addBox(-2.8F, -2.1537758F, -4.21165986F, 5.6F, 0.929079757F, 8.42331971F).texOffs(32, 17).addBox(-2.625F, 1.05577245F, -4.43911884F, 5.25F, 1.09800335F, 7.70425583F), PartPose.offsetAndRotation(-0.0F, -4.08232014F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(1, 29).addBox(-2.275F, -1.83000558F, -2.78820687F, 4.55F, 3.66001116F, 4.10893645F).texOffs(21, 29).addBox(-1.47875F, -0.21115449F, -3.96218872F, 2.9575F, 1.83000558F, 2.05446822F).texOffs(34, 29).addBox(-0.56875F, 0.28153932F, -4.12361122F, 1.1375F, 0.844617961F, 0.440243191F).texOffs(40, 29).addBox(-1.988F, -0.915002791F, -3.03767801F, 0.7F, 0.56307864F, 0.29349546F).texOffs(44, 29).addBox(1.288F, -0.915002791F, -3.03767801F, 0.7F, 0.56307864F, 0.29349546F), PartPose.offsetAndRotation(-0.0F, -1.05577245F, -4.40243191F, -0.0F, -0.0F, 0.0F));
        PartDefinition ear_left = head.addOrReplaceChild("ear_left", CubeListBuilder.create().texOffs(48, 29).addBox(-0.637F, -1.64700502F, -0.366869325F, 1.274F, 1.64700502F, 0.733738651F).texOffs(55, 29).addBox(-0.3822F, -1.35877914F, -0.469592737F, 0.7644F, 1.07055327F, 0.117398184F), PartPose.offsetAndRotation(-1.638F, -1.61885109F, 0.0F, -0.0F, -0.0F, -0.20943951F));
        PartDefinition ear_right = head.addOrReplaceChild("ear_right", CubeListBuilder.create().texOffs(1, 39).addBox(-0.637F, -1.64700502F, -0.366869325F, 1.274F, 1.64700502F, 0.733738651F).texOffs(8, 39).addBox(-0.3822F, -1.35877914F, -0.469592737F, 0.7644F, 1.07055327F, 0.117398184F), PartPose.offsetAndRotation(1.638F, -1.61885109F, 0.0F, -0.0F, -0.0F, 0.20943951F));
        PartDefinition front_left = body.addOrReplaceChild("front_left", CubeListBuilder.create().texOffs(12, 39).addBox(-0.56F, -0.168923592F, -0.469592737F, 1.12F, 1.99892917F, 0.939185473F).texOffs(19, 39).addBox(-0.672F, 1.68923592F, -0.997884565F, 1.344F, 0.844617961F, 1.40877821F), PartPose.offsetAndRotation(-2.45F, 1.54846626F, -3.28714916F, -0.0F, -0.0F, 0.0F));
        PartDefinition front_right = body.addOrReplaceChild("front_right", CubeListBuilder.create().texOffs(27, 39).addBox(-0.56F, -0.168923592F, -0.469592737F, 1.12F, 1.99892917F, 0.939185473F).texOffs(34, 39).addBox(-0.672F, 1.68923592F, -0.997884565F, 1.344F, 0.844617961F, 1.40877821F), PartPose.offsetAndRotation(2.45F, 1.54846626F, -3.28714916F, -0.0F, -0.0F, 0.0F));
        PartDefinition hind_left = body.addOrReplaceChild("hind_left", CubeListBuilder.create().texOffs(42, 39).addBox(-0.56F, -0.168923592F, -0.469592737F, 1.12F, 1.99892917F, 0.939185473F).texOffs(49, 39).addBox(-0.672F, 1.68923592F, -0.997884565F, 1.344F, 0.844617961F, 1.40877821F), PartPose.offsetAndRotation(-2.45F, 1.54846626F, 3.28714916F, -0.0F, -0.0F, 0.0F));
        PartDefinition hind_right = body.addOrReplaceChild("hind_right", CubeListBuilder.create().texOffs(57, 39).addBox(-0.56F, -0.168923592F, -0.469592737F, 1.12F, 1.99892917F, 0.939185473F).texOffs(1, 44).addBox(-0.672F, 1.68923592F, -0.997884565F, 1.344F, 0.844617961F, 1.40877821F), PartPose.offsetAndRotation(2.45F, 1.54846626F, 3.28714916F, -0.0F, -0.0F, 0.0F));
        PartDefinition tail_0 = body.addOrReplaceChild("tail_0", CubeListBuilder.create().texOffs(9, 44).addBox(-0.3325F, -0.267462354F, -0.0880486381F, 0.665F, 0.534924708F, 2.62189278F), PartPose.offsetAndRotation(-0.0F, 0.844617961F, 5.13617056F, 0.261799388F, -0.20943951F, 0.0F));
        PartDefinition tail_1 = tail_0.addOrReplaceChild("tail_1", CubeListBuilder.create().texOffs(18, 44).addBox(-0.266F, -0.213969883F, -0.0880486381F, 0.532F, 0.427939767F, 2.62189278F), PartPose.offsetAndRotation(-0.0F, 0.0F, 2.4457955F, 0.261799388F, 0.20943951F, 0.0F));
        PartDefinition tail_2 = tail_1.addOrReplaceChild("tail_2", CubeListBuilder.create().texOffs(27, 44).addBox(-0.1995F, -0.160477413F, -0.0880486381F, 0.399F, 0.320954825F, 2.62189278F), PartPose.offsetAndRotation(-0.0F, 0.0F, 2.4457955F, 0.261799388F, -0.20943951F, 0.0F));
        PartDefinition gore = mouse.addOrReplaceChild("gore", CubeListBuilder.create().texOffs(36, 44).addBox(-2.275F, -0.056307864F, -1.83434663F, 4.55F, 0.042230898F, 3.66869325F), PartPose.offsetAndRotation(-0.0F, 0.0F, 0.0F, -0.0F, -0.0F, 0.0F));
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
            throw new IllegalArgumentException("Nonfinite possum presentation");
        root.getAllParts().forEach(ModelPart::resetPose);
        gore.visible = false; // Independent source blood state is OFF by default, even Dead.
        if (dead) clip = "down_hold";
        if (clip != null && !clip.equals("rest")) {
            KeyframeAnimations.animate(this, AnimalPossumAnimations.byName(clip),
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
