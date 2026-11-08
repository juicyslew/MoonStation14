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

/** Generated SS14 gorilla asset rig. CC-BY-SA3.0 art; retain ATTRIBUTION.md.
 * Native1.21.1 API, based on pinned Blockbench4.12.6 geometry conventions.
 * NOT a registered entity/renderer/AI/networking implementation.
 */
public final class AnimalGorillaModel<T extends LivingEntity> extends HierarchicalModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
        ResourceLocation.fromNamespaceAndPath("moonstation14", "animals/gorilla"), "main");
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
    private final ModelPart gore;
    public AnimalGorillaModel(ModelPart root) {
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
        this.gore = this.mouse.getChild("gore");
        this.gore.visible = false;
    }
    @Override public ModelPart root() { return root; }
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition base = mesh.getRoot();
        PartDefinition mouse = base.addOrReplaceChild("mouse", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 24.0F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition body = mouse.addOrReplaceChild("body", CubeListBuilder.create().texOffs(1, 1).addBox(-10.0F, -9.23668281F, -9.62935323F, 20.0F, 11.5F, 21.0F).texOffs(85, 1).addBox(-7.75F, -13.0820823F, -7.87935323F, 15.5F, 5.0F, 17.5F).texOffs(153, 1).addBox(-7.75F, 1.10871671F, -7.87935323F, 15.5F, 5.0F, 17.5F).texOffs(1, 36).addBox(-12.25F, -10.7233656F, -14.8358209F, 24.5F, 7.5F, 14.0F).texOffs(80, 36).addBox(-9.75F, -13.2386199F, -13.5858209F, 19.5F, 3.5F, 11.5F).texOffs(144, 36).addBox(-9.75F, -4.20811138F, -13.5858209F, 19.5F, 3.5F, 11.5F).texOffs(1, 60).addBox(-7.75F, 4.96670702F, 0.715174129F, 15.5F, 7.5F, 12.5F).texOffs(59, 60).addBox(-6.0F, 2.45145278F, 1.96517413F, 12.0F, 3.5F, 10.0F).texOffs(105, 60).addBox(-6.0F, 11.4819613F, 1.96517413F, 12.0F, 3.5F, 10.0F).texOffs(151, 60).addBox(-6.0F, -5.25F, -15.039801F, 12.0F, 10.5F, 1.0F), PartPose.offsetAndRotation(-0.0F, -21.1125908F, 3.48258706F, -0.0F, -0.0F, 0.0F));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(179, 60).addBox(-5.5F, -3.75F, -5.5F, 11.0F, 7.5F, 11.0F).texOffs(1, 82).addBox(-4.5F, -6.45876513F, -4.5F, 9.0F, 3.5F, 9.0F).texOffs(39, 82).addBox(-4.5F, 2.95876513F, -4.5F, 9.0F, 3.5F, 9.0F).texOffs(77, 82).addBox(-3.75F, 0.865012107F, -7.67039801F, 7.5F, 3.5F, 3.5F).texOffs(101, 82).addBox(-3.0F, -0.199104116F, -7.42039801F, 6.0F, 1.5F, 3.0F).texOffs(121, 82).addBox(-3.0F, 3.92912833F, -7.42039801F, 6.0F, 1.5F, 3.0F).texOffs(141, 82).addBox(-3.75F, -2.27300242F, -5.98507463F, 7.5F, 3.5F, 1.0F).texOffs(160, 82).addBox(-4.5F, -3.62651332F, -6.67039801F, 9.0F, 1.5F, 1.5F).texOffs(183, 82).addBox(-3.09047619F, -2.23184019F, -6.62313433F, 1.0F, 1.5F, 0.5F).texOffs(188, 82).addBox(2.09047619F, -2.23184019F, -6.62313433F, 1.0F, 1.5F, 0.5F).texOffs(193, 82).addBox(-1.75F, 0.208837772F, -8.24875622F, 3.5F, 1.5F, 1.0F).texOffs(204, 82).addBox(-2.25F, 3.58535109F, -8.01616915F, 4.5F, 0.5F, 0.5F).texOffs(216, 82).addBox(-6.21428571F, -1.25F, -0.651741294F, 1.0F, 2.5F, 2.0F).texOffs(216, 107).addBox(5.21428571F, -1.25F, -0.651741294F, 1.0F, 2.5F, 2.0F), PartPose.offsetAndRotation(-0.0F, -8.71670702F, -13.9303483F, -0.0F, -0.0F, 0.0F));
        PartDefinition arm_left = body.addOrReplaceChild("arm_left", CubeListBuilder.create().texOffs(224, 82).addBox(-4.16428571F, -1.14830508F, -5.56716418F, 6.5F, 14.5F, 8.0F), PartPose.offsetAndRotation(-10.8190476F, -6.97336562F, -6.96517413F, -0.0F, -0.0F, 0.0F));
        PartDefinition forearm_left = arm_left.addOrReplaceChild("forearm_left", CubeListBuilder.create().texOffs(1, 107).addBox(-2.82619048F, -0.712469734F, -5.57835821F, 5.5F, 14.5F, 7.5F).texOffs(29, 107).addBox(-3.40238095F, 11.0750605F, -8.15671642F, 6.5F, 4.0F, 9.0F).texOffs(62, 107).addBox(-3.40238095F, 12.8825666F, -8.70149254F, 6.5F, 3.0F, 8.0F), PartPose.offsetAndRotation(-1.82857143F, 12.2033898F, -3.13432836F, -0.0F, -0.0F, 0.0F));
        PartDefinition foot_left = body.addOrReplaceChild("foot_left", CubeListBuilder.create().texOffs(93, 107).addBox(-3.0547619F, 1.63135593F, -1.75870647F, 5.5F, 6.5F, 7.0F).texOffs(120, 107).addBox(-2.5547619F, -0.488861985F, -1.25870647F, 4.5F, 3.0F, 6.0F).texOffs(143, 107).addBox(-2.5547619F, 7.25157385F, -1.25870647F, 4.5F, 3.0F, 6.0F).texOffs(166, 107).addBox(-2.5547619F, 7.36138015F, -2.32587065F, 4.5F, 5.5F, 5.0F).texOffs(187, 107).addBox(-3.0547619F, 11.6392252F, -6.08955224F, 5.5F, 2.0F, 8.0F), PartPose.offsetAndRotation(-5.48571429F, 7.3220339F, 6.61691542F, -0.0F, -0.0F, 0.0F));
        PartDefinition arm_right = body.addOrReplaceChild("arm_right", CubeListBuilder.create().texOffs(224, 107).addBox(-2.33571429F, -1.14830508F, -5.56716418F, 6.5F, 14.5F, 8.0F), PartPose.offsetAndRotation(10.8190476F, -6.97336562F, -6.96517413F, -0.0F, -0.0F, 0.0F));
        PartDefinition forearm_right = arm_right.addOrReplaceChild("forearm_right", CubeListBuilder.create().texOffs(1, 132).addBox(-2.67380952F, -0.712469734F, -5.57835821F, 5.5F, 14.5F, 7.5F).texOffs(29, 132).addBox(-3.09761905F, 11.0750605F, -8.15671642F, 6.5F, 4.0F, 9.0F).texOffs(62, 132).addBox(-3.09761905F, 12.8825666F, -8.70149254F, 6.5F, 3.0F, 8.0F), PartPose.offsetAndRotation(1.82857143F, 12.2033898F, -3.13432836F, -0.0F, -0.0F, 0.0F));
        PartDefinition foot_right = body.addOrReplaceChild("foot_right", CubeListBuilder.create().texOffs(93, 132).addBox(-2.4452381F, 1.63135593F, -1.75870647F, 5.5F, 6.5F, 7.0F).texOffs(120, 132).addBox(-1.9452381F, -0.488861985F, -1.25870647F, 4.5F, 3.0F, 6.0F).texOffs(143, 132).addBox(-1.9452381F, 7.25157385F, -1.25870647F, 4.5F, 3.0F, 6.0F).texOffs(166, 132).addBox(-1.9452381F, 7.36138015F, -2.32587065F, 4.5F, 5.5F, 5.0F).texOffs(187, 132).addBox(-2.4452381F, 11.6392252F, -6.08955224F, 5.5F, 2.0F, 8.0F), PartPose.offsetAndRotation(5.48571429F, 7.3220339F, 6.61691542F, -0.0F, -0.0F, 0.0F));
        PartDefinition gore = mouse.addOrReplaceChild("gore", CubeListBuilder.create().texOffs(216, 132).addBox(-1.98095238F, -0.0697336562F, -2.17661692F, 3.96190476F, 0.0523002421F, 4.35323383F), PartPose.offsetAndRotation(-0.0F, 0.0F, 0.0F, -0.0F, -0.0F, 0.0F));
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
            throw new IllegalArgumentException("Nonfinite gorilla presentation");
        root.getAllParts().forEach(ModelPart::resetPose);
        gore.visible = false; // Independent source blood state is OFF by default, even Dead.
        if (dead) clip = "down_hold";
        if (clip != null && !clip.equals("rest")) {
            KeyframeAnimations.animate(this, AnimalGorillaAnimations.byName(clip),
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
