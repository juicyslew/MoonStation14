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

/** Generated SS14 snoth asset rig. CC-BY-SA3.0 art; retain ATTRIBUTION.md.
 * Native1.21.1 API, based on pinned Blockbench4.12.6 geometry conventions.
 * NOT a registered entity/renderer/AI/networking implementation.
 */
public final class AnimalSnothModel<T extends LivingEntity> extends HierarchicalModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
        ResourceLocation.fromNamespaceAndPath("moonstation14", "animals/snoth"), "main");
    private final ModelPart root;
    private final ModelPart mouse;
    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart antenna_left;
    private final ModelPart antenna_right;
    private final ModelPart moth_fan_left;
    private final ModelPart moth_fan_right;
    private final ModelPart gore;
    public AnimalSnothModel(ModelPart root) {
        this.root = root;
        this.mouse = root.getChild("mouse");
        this.body = this.mouse.getChild("body");
        this.head = this.body.getChild("head");
        this.antenna_left = this.head.getChild("antenna_left");
        this.antenna_right = this.head.getChild("antenna_right");
        this.moth_fan_left = this.head.getChild("moth_fan_left");
        this.moth_fan_right = this.head.getChild("moth_fan_right");
        this.gore = this.mouse.getChild("gore");
        this.gore.visible = false;
    }
    @Override public ModelPart root() { return root; }
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition base = mesh.getRoot();
        PartDefinition mouse = base.addOrReplaceChild("mouse", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 24.0F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition body = mouse.addOrReplaceChild("body", CubeListBuilder.create().texOffs(1, 1).addBox(-2.14283412F, 1.04938272F, -4.784F, 4.28566825F, 2.09876543F, 10.4F).texOffs(17, 16).addBox(-2.98133443F, -4.09259259F, -1.352F, 5.96266887F, 5.24691358F, 7.696F).texOffs(1, 31).addBox(-2.60866763F, -5.35185185F, -0.312F, 5.21733526F, 1.4691358F, 5.616F).texOffs(25, 31).addBox(-2.60866763F, 0.62962963F, -0.312F, 5.21733526F, 1.4691358F, 5.616F).texOffs(49, 31).addBox(-2.60866763F, -3.25308642F, -2.08F, 5.21733526F, 3.56790123F, 1.04F), PartPose.offsetAndRotation(-0.0F, -3.14814815F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(33, 1).addBox(-1.77016732F, -1.25925926F, -1.352F, 3.54033464F, 4.19753086F, 3.952F).texOffs(50, 1).addBox(-1.58383392F, -1.67901235F, -1.56F, 3.16766783F, 2.51851852F, 1.872F).texOffs(1, 16).addBox(-1.11800041F, -0.944444444F, -2.496F, 0.559000206F, 0.62962963F, 0.416F).texOffs(5, 16).addBox(0.559000206F, -0.944444444F, -2.496F, 0.559000206F, 0.62962963F, 0.416F).texOffs(1, 41).addBox(-2.32916753F, -2.41358025F, -1.9968F, 4.65833505F, 3.98765432F, 0.832F), PartPose.offsetAndRotation(-0.0F, -1.04938272F, -4.16F, -0.0F, -0.0F, 0.0F));
        PartDefinition antenna_left = head.addOrReplaceChild("antenna_left", CubeListBuilder.create().texOffs(9, 16).addBox(-0.204966742F, -2.9382716F, -0.26F, 0.409933485F, 2.9382716F, 0.52F), PartPose.offsetAndRotation(-1.02483371F, -1.04938272F, 0.0F, -0.0F, -0.0F, -0.20943951F));
        PartDefinition antenna_right = head.addOrReplaceChild("antenna_right", CubeListBuilder.create().texOffs(13, 16).addBox(-0.204966742F, -2.9382716F, -0.26F, 0.409933485F, 2.9382716F, 0.52F), PartPose.offsetAndRotation(1.02483371F, -1.04938272F, 0.0F, -0.0F, -0.0F, 0.20943951F));
        PartDefinition moth_fan_left = head.addOrReplaceChild("moth_fan_left", CubeListBuilder.create().texOffs(14, 41).addBox(-0.512416856F, -2.72839506F, -0.312F, 1.02483371F, 2.72839506F, 0.624F), PartPose.offsetAndRotation(-1.21116711F, -1.04938272F, -0.416F, -0.0F, -0.0F, -0.785398163F));
        PartDefinition moth_fan_right = head.addOrReplaceChild("moth_fan_right", CubeListBuilder.create().texOffs(20, 41).addBox(-0.512416856F, -2.72839506F, -0.312F, 1.02483371F, 2.72839506F, 0.624F), PartPose.offsetAndRotation(1.21116711F, -1.04938272F, -0.416F, -0.0F, -0.0F, 0.785398163F));
        PartDefinition gore = mouse.addOrReplaceChild("gore", CubeListBuilder.create().texOffs(26, 41).addBox(-2.42233423F, -0.0839506173F, -2.6F, 4.84466845F, 0.062962963F, 5.2F), PartPose.offsetAndRotation(-0.0F, 0.0F, 0.0F, -0.0F, -0.0F, 0.0F));
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
            throw new IllegalArgumentException("Nonfinite snoth presentation");
        root.getAllParts().forEach(ModelPart::resetPose);
        gore.visible = false; // Independent source blood state is OFF by default, even Dead.
        if (dead) clip = "down_hold";
        if (clip != null && !clip.equals("rest")) {
            KeyframeAnimations.animate(this, AnimalSnothAnimations.byName(clip),
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
