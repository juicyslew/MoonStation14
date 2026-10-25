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

/** Generated SS14 lizard asset rig. CC-BY-SA3.0 art; retain ATTRIBUTION.md.
 * Native1.21.1 API, based on pinned Blockbench4.12.6 geometry conventions.
 * NOT a registered entity/renderer/AI/networking implementation.
 */
public final class AnimalLizardModel<T extends LivingEntity> extends HierarchicalModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
        ResourceLocation.fromNamespaceAndPath("moonstation14", "animals/lizard"), "main");
    private final ModelPart root;
    private final ModelPart mouse;
    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart front_left;
    private final ModelPart front_right;
    private final ModelPart hind_left;
    private final ModelPart hind_right;
    private final ModelPart tail_0;
    private final ModelPart tail_1;
    private final ModelPart tail_2;
    private final ModelPart gore;
    public AnimalLizardModel(ModelPart root) {
        this.root = root;
        this.mouse = root.getChild("mouse");
        this.body = this.mouse.getChild("body");
        this.head = this.body.getChild("head");
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
        PartDefinition body = mouse.addOrReplaceChild("body", CubeListBuilder.create().texOffs(1, 1).addBox(-3.0F, -1.03399558F, -4.33387839F, 6.0F, 2.06799116F, 8.66775679F).texOffs(33, 1).addBox(-2.4F, -1.46482707F, -3.55378028F, 4.8F, 0.631886189F, 7.10756057F).texOffs(1, 14).addBox(-2.25F, 0.718052488F, -3.82825925F, 4.5F, 0.746774587F, 6.50081759F), PartPose.offsetAndRotation(-0.0F, -3.21384088F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(26, 14).addBox(-2.6F, -1.53184531F, -2.60032704F, 5.2F, 3.06369061F, 3.75602794F).texOffs(46, 14).addBox(-1.69F, 0.191480663F, -3.46710272F, 3.38F, 1.53184531F, 1.44462613F).texOffs(58, 14).addBox(-0.65F, 0.382961327F, -3.39487141F, 1.3F, 1.14888398F, 0.433387839F).texOffs(1, 24).addBox(-2.272F, -1.24462431F, -2.84591348F, 0.8F, 0.765922654F, 0.288925226F).texOffs(6, 24).addBox(1.472F, -1.24462431F, -2.84591348F, 0.8F, 0.765922654F, 0.288925226F).texOffs(43, 29).addBox(-0.55F, -3.06369061F, -1.0834696F, 1.1F, 2.29776796F, 2.1669392F), PartPose.offsetAndRotation(-0.0F, -0.718052488F, -3.61156533F, -0.0F, -0.0F, 0.0F));
        PartDefinition front_left = body.addOrReplaceChild("front_left", CubeListBuilder.create().texOffs(11, 24).addBox(-0.55F, -0.229776796F, -0.397272186F, 1.1F, 1.18718011F, 0.794544372F).texOffs(17, 24).addBox(-0.66F, 0.765922654F, -0.884833505F, 1.32F, 1.14888398F, 1.19181656F), PartPose.offsetAndRotation(-2.1F, 0.670182322F, -2.77368217F, -0.0F, -0.0F, 0.0F));
        PartDefinition front_right = body.addOrReplaceChild("front_right", CubeListBuilder.create().texOffs(25, 24).addBox(-0.55F, -0.229776796F, -0.397272186F, 1.1F, 1.18718011F, 0.794544372F).texOffs(31, 24).addBox(-0.66F, 0.765922654F, -0.884833505F, 1.32F, 1.14888398F, 1.19181656F), PartPose.offsetAndRotation(2.1F, 0.670182322F, -2.77368217F, -0.0F, -0.0F, 0.0F));
        PartDefinition hind_left = body.addOrReplaceChild("hind_left", CubeListBuilder.create().texOffs(39, 24).addBox(-0.55F, -0.229776796F, -0.397272186F, 1.1F, 1.18718011F, 0.794544372F).texOffs(45, 24).addBox(-0.66F, 0.765922654F, -0.884833505F, 1.32F, 1.14888398F, 1.19181656F), PartPose.offsetAndRotation(-2.1F, 0.670182322F, 2.77368217F, -0.0F, -0.0F, 0.0F));
        PartDefinition hind_right = body.addOrReplaceChild("hind_right", CubeListBuilder.create().texOffs(53, 24).addBox(-0.55F, -0.229776796F, -0.397272186F, 1.1F, 1.18718011F, 0.794544372F).texOffs(1, 29).addBox(-0.66F, 0.765922654F, -0.884833505F, 1.32F, 1.14888398F, 1.19181656F), PartPose.offsetAndRotation(2.1F, 0.670182322F, 2.77368217F, -0.0F, -0.0F, 0.0F));
        PartDefinition tail_0 = body.addOrReplaceChild("tail_0", CubeListBuilder.create().texOffs(9, 29).addBox(-0.8F, -0.765922654F, -0.0866775679F, 1.6F, 1.53184531F, 3.0626074F), PartPose.offsetAndRotation(-0.0F, 0.670182322F, 4.33387839F, -0.122173048F, -0.20943951F, 0.0F));
        PartDefinition tail_1 = tail_0.addOrReplaceChild("tail_1", CubeListBuilder.create().texOffs(21, 29).addBox(-0.64F, -0.612738123F, -0.0866775679F, 1.28F, 1.22547625F, 3.0626074F), PartPose.offsetAndRotation(-0.0F, 0.0F, 2.88925226F, -0.122173048F, 0.20943951F, 0.0F));
        PartDefinition tail_2 = tail_1.addOrReplaceChild("tail_2", CubeListBuilder.create().texOffs(32, 29).addBox(-0.48F, -0.459553592F, -0.0866775679F, 0.96F, 0.919107184F, 3.0626074F), PartPose.offsetAndRotation(-0.0F, 0.0F, 2.88925226F, -0.122173048F, -0.20943951F, 0.0F));
        PartDefinition gore = mouse.addOrReplaceChild("gore", CubeListBuilder.create().texOffs(1, 36).addBox(-2.6F, -0.0765922654F, -1.80578266F, 5.2F, 0.057444199F, 3.61156533F), PartPose.offsetAndRotation(-0.0F, 0.0F, 0.0F, -0.0F, -0.0F, 0.0F));
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
            throw new IllegalArgumentException("Nonfinite lizard presentation");
        root.getAllParts().forEach(ModelPart::resetPose);
        gore.visible = false; // Independent source blood state is OFF by default, even Dead.
        if (dead) clip = "down_hold";
        if (clip != null && !clip.equals("rest")) {
            KeyframeAnimations.animate(this, AnimalLizardAnimations.byName(clip),
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
