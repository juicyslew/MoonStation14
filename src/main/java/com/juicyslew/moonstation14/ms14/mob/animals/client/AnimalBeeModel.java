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

/** Generated SS14 bee asset rig. CC-BY-SA3.0 art; retain ATTRIBUTION.md.
 * Native1.21.1 API, based on pinned Blockbench4.12.6 geometry conventions.
 * NOT a registered entity/renderer/AI/networking implementation.
 */
public final class AnimalBeeModel<T extends LivingEntity> extends HierarchicalModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
        ResourceLocation.fromNamespaceAndPath("moonstation14", "animals/bee"), "main");
    private final ModelPart root;
    private final ModelPart mouse;
    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart wing_left;
    private final ModelPart wing_right;
    private final ModelPart gore;
    public AnimalBeeModel(ModelPart root) {
        this.root = root;
        this.mouse = root.getChild("mouse");
        this.body = this.mouse.getChild("body");
        this.head = this.body.getChild("head");
        this.wing_left = this.body.getChild("wing_left");
        this.wing_right = this.body.getChild("wing_right");
        this.gore = this.mouse.getChild("gore");
        this.gore.visible = false;
    }
    @Override public ModelPart root() { return root; }
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition base = mesh.getRoot();
        PartDefinition mouse = base.addOrReplaceChild("mouse", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 24.0F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition body = mouse.addOrReplaceChild("body", CubeListBuilder.create().texOffs(1, 1).addBox(-0.364841427F, -0.258928571F, -0.671213209F, 0.729682854F, 0.517857143F, 1.34242642F).texOffs(8, 1).addBox(-0.284576313F, -0.446428571F, -0.550394831F, 0.569152626F, 0.232142857F, 1.10078966F).texOffs(14, 1).addBox(-0.284576313F, 0.214285714F, -0.550394831F, 0.569152626F, 0.232142857F, 1.10078966F), PartPose.offsetAndRotation(-0.0F, -5.95238095F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(20, 1).addBox(-0.346599356F, -0.155357143F, -0.536970567F, 0.693198712F, 0.310714286F, 1.07394113F).texOffs(26, 1).addBox(-0.270347498F, -0.267857143F, -0.440315865F, 0.540694995F, 0.139285714F, 0.88063173F).texOffs(31, 1).addBox(-0.270347498F, 0.128571429F, -0.440315865F, 0.540694995F, 0.139285714F, 0.88063173F).texOffs(36, 1).addBox(-0.281119921F, -0.206349206F, -0.65620962F, 0.153617443F, 0.198412698F, 0.15793252F).texOffs(39, 1).addBox(0.127502478F, -0.206349206F, -0.65620962F, 0.153617443F, 0.198412698F, 0.15793252F), PartPose.offsetAndRotation(-0.0F, -0.285714286F, -0.872577172F, -0.0F, -0.0F, 0.0F));
        PartDefinition wing_left = body.addOrReplaceChild("wing_left", CubeListBuilder.create().texOffs(42, 1).addBox(-1.22893954F, -0.0496031746F, -0.394831299F, 1.22893954F, 0.0992063492F, 0.789662599F), PartPose.offsetAndRotation(-0.321060456F, -0.496031746F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition wing_right = body.addOrReplaceChild("wing_right", CubeListBuilder.create().texOffs(49, 1).addBox(0.0F, -0.0496031746F, -0.394831299F, 1.22893954F, 0.0992063492F, 0.789662599F), PartPose.offsetAndRotation(0.321060456F, -0.496031746F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition gore = mouse.addOrReplaceChild("gore", CubeListBuilder.create().texOffs(1, 5).addBox(-0.99851338F, -0.0396825397F, -0.987078248F, 1.99702676F, 0.0297619048F, 1.9741565F), PartPose.offsetAndRotation(-0.0F, 0.0F, 0.0F, -0.0F, -0.0F, 0.0F));
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
            throw new IllegalArgumentException("Nonfinite bee presentation");
        root.getAllParts().forEach(ModelPart::resetPose);
        gore.visible = false; // Independent source blood state is OFF by default, even Dead.
        if (dead) clip = "down_hold";
        if (clip != null && !clip.equals("rest")) {
            KeyframeAnimations.animate(this, AnimalBeeAnimations.byName(clip),
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
        applyPresentation(dead ? "down_hold" : limbSwingAmount > .025F ? "fly" : "idle_breathe",
            dead, ageInTicks / 20.0F, -netHeadYaw, -headPitch, -1);
    }
}
