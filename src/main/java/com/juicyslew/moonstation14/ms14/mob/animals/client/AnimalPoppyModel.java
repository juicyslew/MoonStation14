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

/** Generated SS14 poppy asset rig. CC-BY-SA3.0 art; retain ATTRIBUTION.md.
 * Native1.21.1 API, based on pinned Blockbench4.12.6 geometry conventions.
 * NOT a registered entity/renderer/AI/networking implementation.
 */
public final class AnimalPoppyModel<T extends LivingEntity> extends HierarchicalModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
        ResourceLocation.fromNamespaceAndPath("moonstation14", "animals/poppy"), "main");
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
    public AnimalPoppyModel(ModelPart root) {
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
        PartDefinition body = mouse.addOrReplaceChild("body", CubeListBuilder.create().texOffs(1, 1).addBox(-3.29411765F, -1.52170284F, -5.13673545F, 6.58823529F, 3.04340568F, 10.2734709F).texOffs(1, 17).addBox(-2.63529412F, -2.15574569F, -4.21212307F, 5.27058824F, 0.929929515F, 8.42424614F).texOffs(31, 17).addBox(-2.47058824F, 1.05673808F, -4.43960707F, 4.94117647F, 1.09900761F, 7.70510318F).texOffs(27, 44).addBox(-3.45882353F, -2.21914998F, -3.22880514F, 6.91764706F, 4.43829996F, 3.52233288F).texOffs(1, 54).addBox(-3.5F, -2.25437458F, -1.83454838F, 7.0F, 4.50874916F, 0.73381935F), PartPose.offsetAndRotation(-0.0F, -4.08605393F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(1, 29).addBox(-2.14117647F, -1.83167935F, -2.78851353F, 4.28235294F, 3.66335869F, 4.10938836F).texOffs(20, 29).addBox(-1.39176471F, -0.211347617F, -3.96262449F, 2.78352941F, 1.83167935F, 2.05469418F).texOffs(32, 29).addBox(-0.535294118F, 0.281796823F, -4.12406475F, 1.07058824F, 0.845390468F, 0.44029161F).texOffs(38, 29).addBox(-1.87105882F, -0.915839674F, -3.03801211F, 0.658823529F, 0.563593645F, 0.29352774F).texOffs(42, 29).addBox(1.21223529F, -0.915839674F, -3.03801211F, 0.658823529F, 0.563593645F, 0.29352774F).texOffs(19, 54).addBox(-3.12941176F, -2.81796823F, -2.7151316F, 6.25882353F, 0.845390468F, 5.43026319F), PartPose.offsetAndRotation(-0.0F, -1.05673808F, -4.4029161F, -0.0F, -0.0F, 0.0F));
        PartDefinition ear_left = head.addOrReplaceChild("ear_left", CubeListBuilder.create().texOffs(46, 29).addBox(-0.599529412F, -1.64851141F, -0.366909675F, 1.19905882F, 1.64851141F, 0.73381935F).texOffs(52, 29).addBox(-0.359717647F, -1.36002192F, -0.469644384F, 0.719435294F, 1.07153242F, 0.117411096F), PartPose.offsetAndRotation(-1.54164706F, -1.62033173F, 0.0F, -0.0F, -0.0F, -0.20943951F));
        PartDefinition ear_right = head.addOrReplaceChild("ear_right", CubeListBuilder.create().texOffs(56, 29).addBox(-0.599529412F, -1.64851141F, -0.366909675F, 1.19905882F, 1.64851141F, 0.73381935F).texOffs(1, 39).addBox(-0.359717647F, -1.36002192F, -0.469644384F, 0.719435294F, 1.07153242F, 0.117411096F), PartPose.offsetAndRotation(1.54164706F, -1.62033173F, 0.0F, -0.0F, -0.0F, 0.20943951F));
        PartDefinition front_left = body.addOrReplaceChild("front_left", CubeListBuilder.create().texOffs(5, 39).addBox(-0.527058824F, -0.169078094F, -0.469644384F, 1.05411765F, 2.00075744F, 0.939288768F).texOffs(11, 39).addBox(-0.632470588F, 1.69078094F, -0.997994316F, 1.26494118F, 0.845390468F, 1.40893315F), PartPose.offsetAndRotation(-2.30588235F, 1.54988252F, -3.28751069F, -0.0F, -0.0F, 0.0F));
        PartDefinition front_right = body.addOrReplaceChild("front_right", CubeListBuilder.create().texOffs(19, 39).addBox(-0.527058824F, -0.169078094F, -0.469644384F, 1.05411765F, 2.00075744F, 0.939288768F).texOffs(25, 39).addBox(-0.632470588F, 1.69078094F, -0.997994316F, 1.26494118F, 0.845390468F, 1.40893315F), PartPose.offsetAndRotation(2.30588235F, 1.54988252F, -3.28751069F, -0.0F, -0.0F, 0.0F));
        PartDefinition hind_left = body.addOrReplaceChild("hind_left", CubeListBuilder.create().texOffs(33, 39).addBox(-0.527058824F, -0.169078094F, -0.469644384F, 1.05411765F, 2.00075744F, 0.939288768F).texOffs(39, 39).addBox(-0.632470588F, 1.69078094F, -0.997994316F, 1.26494118F, 0.845390468F, 1.40893315F), PartPose.offsetAndRotation(-2.30588235F, 1.54988252F, 3.28751069F, -0.0F, -0.0F, 0.0F));
        PartDefinition hind_right = body.addOrReplaceChild("hind_right", CubeListBuilder.create().texOffs(47, 39).addBox(-0.527058824F, -0.169078094F, -0.469644384F, 1.05411765F, 2.00075744F, 0.939288768F).texOffs(53, 39).addBox(-0.632470588F, 1.69078094F, -0.997994316F, 1.26494118F, 0.845390468F, 1.40893315F), PartPose.offsetAndRotation(2.30588235F, 1.54988252F, 3.28751069F, -0.0F, -0.0F, 0.0F));
        PartDefinition tail_0 = body.addOrReplaceChild("tail_0", CubeListBuilder.create().texOffs(1, 44).addBox(-0.312941176F, -0.267706982F, -0.088058322F, 0.625882353F, 0.535413963F, 2.62218114F), PartPose.offsetAndRotation(-0.0F, 0.845390468F, 5.13673545F, 0.261799388F, -0.20943951F, 0.0F));
        PartDefinition tail_1 = tail_0.addOrReplaceChild("tail_1", CubeListBuilder.create().texOffs(10, 44).addBox(-0.250352941F, -0.214165585F, -0.088058322F, 0.500705882F, 0.42833117F, 2.62218114F), PartPose.offsetAndRotation(-0.0F, 0.0F, 2.4460645F, 0.261799388F, 0.20943951F, 0.0F));
        PartDefinition tail_2 = tail_1.addOrReplaceChild("tail_2", CubeListBuilder.create().texOffs(19, 44).addBox(-0.187764706F, -0.160624189F, -0.088058322F, 0.375529412F, 0.321248378F, 2.62218114F), PartPose.offsetAndRotation(-0.0F, 0.0F, 2.4460645F, 0.261799388F, -0.20943951F, 0.0F));
        PartDefinition gore = mouse.addOrReplaceChild("gore", CubeListBuilder.create().texOffs(45, 54).addBox(-2.14117647F, -0.0563593645F, -1.83454838F, 4.28235294F, 0.0422695234F, 3.66909675F), PartPose.offsetAndRotation(-0.0F, 0.0F, 0.0F, -0.0F, -0.0F, 0.0F));
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
            throw new IllegalArgumentException("Nonfinite poppy presentation");
        root.getAllParts().forEach(ModelPart::resetPose);
        gore.visible = false; // Independent source blood state is OFF by default, even Dead.
        if (dead) clip = "down_hold";
        if (clip != null && !clip.equals("rest")) {
            KeyframeAnimations.animate(this, AnimalPoppyAnimations.byName(clip),
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
