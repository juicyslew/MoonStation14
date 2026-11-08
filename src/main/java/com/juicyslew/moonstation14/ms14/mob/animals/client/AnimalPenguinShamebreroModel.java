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

/** Generated SS14 penguin_shamebrero asset rig. CC-BY-SA3.0 art; retain ATTRIBUTION.md.
 * Native1.21.1 API, based on pinned Blockbench4.12.6 geometry conventions.
 * NOT a registered entity/renderer/AI/networking implementation.
 */
public final class AnimalPenguinShamebreroModel<T extends LivingEntity> extends HierarchicalModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
        ResourceLocation.fromNamespaceAndPath("moonstation14", "animals/penguin_shamebrero"), "main");
    private final ModelPart root;
    private final ModelPart mouse;
    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart wing_left;
    private final ModelPart foot_left;
    private final ModelPart wing_right;
    private final ModelPart foot_right;
    private final ModelPart gore;
    public AnimalPenguinShamebreroModel(ModelPart root) {
        this.root = root;
        this.mouse = root.getChild("mouse");
        this.body = this.mouse.getChild("body");
        this.head = this.body.getChild("head");
        this.wing_left = this.body.getChild("wing_left");
        this.foot_left = this.body.getChild("foot_left");
        this.wing_right = this.body.getChild("wing_right");
        this.foot_right = this.body.getChild("foot_right");
        this.gore = this.mouse.getChild("gore");
        this.gore.visible = false;
    }
    @Override public ModelPart root() { return root; }
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition base = mesh.getRoot();
        PartDefinition mouse = base.addOrReplaceChild("mouse", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 24.0F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition body = mouse.addOrReplaceChild("body", CubeListBuilder.create().texOffs(1, 1).addBox(-5.5F, -4.75F, -4.25F, 11.0F, 9.5F, 8.5F).texOffs(42, 1).addBox(-4.25F, -8.17F, -3.5F, 8.5F, 4.5F, 7.0F).texOffs(75, 1).addBox(-4.25F, 3.67F, -3.5F, 8.5F, 4.5F, 7.0F).texOffs(1, 21).addBox(-4.25F, -4.25F, -4.35571429F, 8.5F, 8.5F, 3.5F).texOffs(27, 21).addBox(-3.5F, -7.1504F, -4.10571429F, 7.0F, 4.0F, 3.0F).texOffs(49, 21).addBox(-3.5F, 3.1504F, -4.10571429F, 7.0F, 4.0F, 3.0F), PartPose.offsetAndRotation(-0.0F, -9.74F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(71, 21).addBox(-2.25F, 0.0F, -2.25F, 4.5F, 6.0F, 4.5F).texOffs(91, 21).addBox(-3.75F, -2.25F, -4.0F, 7.5F, 4.5F, 8.0F).texOffs(1, 36).addBox(-3.0F, -3.664F, -3.25F, 6.0F, 2.0F, 6.5F).texOffs(28, 36).addBox(-3.0F, 1.664F, -3.25F, 6.0F, 2.0F, 6.5F).texOffs(55, 36).addBox(-1.75F, -0.35F, -5.71428571F, 3.5F, 1.5F, 2.0F).texOffs(68, 36).addBox(-3.0344F, -0.86F, -4.21428571F, 1.0F, 1.0F, 0.5F).texOffs(73, 36).addBox(2.0344F, -0.86F, -4.21428571F, 1.0F, 1.0F, 0.5F).texOffs(1, 66).addBox(-11.0F, -4.7F, -7.5F, 22.0F, 1.0F, 15.0F).texOffs(77, 66).addBox(-4.5F, -8.0F, -3.75F, 9.0F, 4.0F, 7.5F).texOffs(1, 84).addBox(-4.75F, -5.6F, -3.75F, 9.5F, 1.0F, 7.5F).texOffs(62, 84).addBox(9.75F, -3.74F, -7.5F, 0.5F, 2.0F, 0.5F).texOffs(66, 84).addBox(9.5F, -2.11F, -7.75F, 1.0F, 0.5F, 1.0F).texOffs(72, 84).addBox(7.75F, -3.74F, -7.5F, 0.5F, 2.5F, 0.5F).texOffs(76, 84).addBox(7.5F, -1.61F, -7.75F, 1.0F, 0.5F, 1.0F).texOffs(82, 84).addBox(5.75F, -3.74F, -7.5F, 0.5F, 4.0F, 0.5F).texOffs(86, 84).addBox(5.5F, -0.11F, -7.75F, 1.0F, 0.5F, 1.0F).texOffs(92, 84).addBox(3.75F, -3.74F, -7.5F, 0.5F, 4.0F, 0.5F).texOffs(96, 84).addBox(3.5F, -0.11F, -7.75F, 1.0F, 0.5F, 1.0F).texOffs(102, 84).addBox(1.75F, -3.74F, -7.5F, 0.5F, 3.0F, 0.5F).texOffs(106, 84).addBox(1.5F, -1.11F, -7.75F, 1.0F, 0.5F, 1.0F).texOffs(112, 84).addBox(-0.25F, -3.74F, -7.5F, 0.5F, 3.0F, 0.5F).texOffs(116, 84).addBox(-0.5F, -1.11F, -7.75F, 1.0F, 0.5F, 1.0F).texOffs(122, 84).addBox(-2.25F, -3.74F, -7.5F, 0.5F, 3.0F, 0.5F).texOffs(1, 95).addBox(-2.5F, -1.11F, -7.75F, 1.0F, 0.5F, 1.0F).texOffs(7, 95).addBox(-4.25F, -3.74F, -7.5F, 0.5F, 4.0F, 0.5F).texOffs(11, 95).addBox(-4.5F, -0.11F, -7.75F, 1.0F, 0.5F, 1.0F).texOffs(17, 95).addBox(-6.25F, -3.74F, -7.5F, 0.5F, 4.0F, 0.5F).texOffs(21, 95).addBox(-6.5F, -0.11F, -7.75F, 1.0F, 0.5F, 1.0F).texOffs(27, 95).addBox(-8.25F, -3.74F, -7.5F, 0.5F, 2.5F, 0.5F).texOffs(31, 95).addBox(-8.5F, -1.61F, -7.75F, 1.0F, 0.5F, 1.0F).texOffs(37, 95).addBox(-10.25F, -3.74F, -7.5F, 0.5F, 2.0F, 0.5F).texOffs(41, 95).addBox(-10.5F, -2.11F, -7.75F, 1.0F, 0.5F, 1.0F).texOffs(47, 95).addBox(9.75F, -3.74F, 7.0F, 0.5F, 2.0F, 0.5F).texOffs(51, 95).addBox(9.5F, -2.11F, 6.75F, 1.0F, 0.5F, 1.0F).texOffs(57, 95).addBox(7.75F, -3.74F, 7.0F, 0.5F, 2.5F, 0.5F).texOffs(61, 95).addBox(7.5F, -1.61F, 6.75F, 1.0F, 0.5F, 1.0F).texOffs(67, 95).addBox(5.75F, -3.74F, 7.0F, 0.5F, 4.0F, 0.5F).texOffs(71, 95).addBox(5.5F, -0.11F, 6.75F, 1.0F, 0.5F, 1.0F).texOffs(77, 95).addBox(3.75F, -3.74F, 7.0F, 0.5F, 4.0F, 0.5F).texOffs(81, 95).addBox(3.5F, -0.11F, 6.75F, 1.0F, 0.5F, 1.0F).texOffs(87, 95).addBox(1.75F, -3.74F, 7.0F, 0.5F, 3.0F, 0.5F).texOffs(91, 95).addBox(1.5F, -1.11F, 6.75F, 1.0F, 0.5F, 1.0F).texOffs(97, 95).addBox(-0.25F, -3.74F, 7.0F, 0.5F, 3.0F, 0.5F).texOffs(101, 95).addBox(-0.5F, -1.11F, 6.75F, 1.0F, 0.5F, 1.0F).texOffs(107, 95).addBox(-2.25F, -3.74F, 7.0F, 0.5F, 3.0F, 0.5F).texOffs(111, 95).addBox(-2.5F, -1.11F, 6.75F, 1.0F, 0.5F, 1.0F).texOffs(117, 95).addBox(-4.25F, -3.74F, 7.0F, 0.5F, 4.0F, 0.5F).texOffs(121, 95).addBox(-4.5F, -0.11F, 6.75F, 1.0F, 0.5F, 1.0F).texOffs(1, 102).addBox(-6.25F, -3.74F, 7.0F, 0.5F, 4.0F, 0.5F).texOffs(5, 102).addBox(-6.5F, -0.11F, 6.75F, 1.0F, 0.5F, 1.0F).texOffs(11, 102).addBox(-8.25F, -3.74F, 7.0F, 0.5F, 2.5F, 0.5F).texOffs(15, 102).addBox(-8.5F, -1.61F, 6.75F, 1.0F, 0.5F, 1.0F).texOffs(21, 102).addBox(-10.25F, -3.74F, 7.0F, 0.5F, 2.0F, 0.5F).texOffs(25, 102).addBox(-10.5F, -2.11F, 6.75F, 1.0F, 0.5F, 1.0F).texOffs(31, 102).addBox(10.5F, -3.74F, -5.75F, 0.5F, 2.5F, 0.5F).texOffs(35, 102).addBox(10.25F, -1.61F, -6.0F, 1.0F, 0.5F, 1.0F).texOffs(41, 102).addBox(10.5F, -3.74F, -3.55F, 0.5F, 3.5F, 0.5F).texOffs(45, 102).addBox(10.25F, -0.61F, -3.8F, 1.0F, 0.5F, 1.0F).texOffs(51, 102).addBox(10.5F, -3.74F, -1.35F, 0.5F, 3.5F, 0.5F).texOffs(55, 102).addBox(10.25F, -0.61F, -1.6F, 1.0F, 0.5F, 1.0F).texOffs(61, 102).addBox(10.5F, -3.74F, 0.85F, 0.5F, 3.5F, 0.5F).texOffs(65, 102).addBox(10.25F, -0.61F, 0.6F, 1.0F, 0.5F, 1.0F).texOffs(71, 102).addBox(10.5F, -3.74F, 3.05F, 0.5F, 3.5F, 0.5F).texOffs(75, 102).addBox(10.25F, -0.61F, 2.8F, 1.0F, 0.5F, 1.0F).texOffs(81, 102).addBox(10.5F, -3.74F, 5.25F, 0.5F, 2.5F, 0.5F).texOffs(85, 102).addBox(10.25F, -1.61F, 5.0F, 1.0F, 0.5F, 1.0F).texOffs(91, 102).addBox(-11.0F, -3.74F, -5.75F, 0.5F, 2.5F, 0.5F).texOffs(95, 102).addBox(-11.25F, -1.61F, -6.0F, 1.0F, 0.5F, 1.0F).texOffs(101, 102).addBox(-11.0F, -3.74F, -3.55F, 0.5F, 3.5F, 0.5F).texOffs(105, 102).addBox(-11.25F, -0.61F, -3.8F, 1.0F, 0.5F, 1.0F).texOffs(111, 102).addBox(-11.0F, -3.74F, -1.35F, 0.5F, 3.5F, 0.5F).texOffs(115, 102).addBox(-11.25F, -0.61F, -1.6F, 1.0F, 0.5F, 1.0F).texOffs(121, 102).addBox(-11.0F, -3.74F, 0.85F, 0.5F, 3.5F, 0.5F).texOffs(1, 109).addBox(-11.25F, -0.61F, 0.6F, 1.0F, 0.5F, 1.0F).texOffs(7, 109).addBox(-11.0F, -3.74F, 3.05F, 0.5F, 3.5F, 0.5F).texOffs(11, 109).addBox(-11.25F, -0.61F, 2.8F, 1.0F, 0.5F, 1.0F).texOffs(17, 109).addBox(-11.0F, -3.74F, 5.25F, 0.5F, 2.5F, 0.5F).texOffs(21, 109).addBox(-11.25F, -1.61F, 5.0F, 1.0F, 0.5F, 1.0F), PartPose.offsetAndRotation(-0.0F, -7.6F, -3.09428571F, -0.0F, -0.0F, 0.0F));
        PartDefinition wing_left = body.addOrReplaceChild("wing_left", CubeListBuilder.create().texOffs(78, 36).addBox(-1.3836F, 1.23F, -2.5F, 1.5F, 6.5F, 6.5F).texOffs(96, 36).addBox(-1.1336F, -0.868F, -1.75F, 1.0F, 3.0F, 5.0F).texOffs(110, 36).addBox(-1.1336F, 6.828F, -1.75F, 1.0F, 3.0F, 5.0F), PartPose.offsetAndRotation(-4.6464F, -3.68F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition foot_left = body.addOrReplaceChild("foot_left", CubeListBuilder.create().texOffs(1, 51).addBox(-0.5F, -0.28F, -0.75F, 1.0F, 2.0F, 1.5F).texOffs(8, 51).addBox(-1.75F, 0.74F, -2.82142857F, 3.5F, 1.0F, 3.5F), PartPose.offsetAndRotation(-2.5344F, 8.0F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition wing_right = body.addOrReplaceChild("wing_right", CubeListBuilder.create().texOffs(24, 51).addBox(-0.1164F, 1.23F, -2.5F, 1.5F, 6.5F, 6.5F).texOffs(42, 51).addBox(0.1336F, -0.868F, -1.75F, 1.0F, 3.0F, 5.0F).texOffs(56, 51).addBox(0.1336F, 6.828F, -1.75F, 1.0F, 3.0F, 5.0F), PartPose.offsetAndRotation(4.6464F, -3.68F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition foot_right = body.addOrReplaceChild("foot_right", CubeListBuilder.create().texOffs(70, 51).addBox(-0.5F, -0.28F, -0.75F, 1.0F, 2.0F, 1.5F).texOffs(77, 51).addBox(-1.75F, 0.74F, -2.82142857F, 3.5F, 1.0F, 3.5F), PartPose.offsetAndRotation(2.5344F, 8.0F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition gore = mouse.addOrReplaceChild("gore", CubeListBuilder.create().texOffs(37, 84).addBox(-2.86F, -0.08F, -2.67857143F, 5.72F, 0.06F, 5.35714286F), PartPose.offsetAndRotation(-0.0F, 0.0F, 0.0F, -0.0F, -0.0F, 0.0F));
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
            throw new IllegalArgumentException("Nonfinite penguin_shamebrero presentation");
        root.getAllParts().forEach(ModelPart::resetPose);
        gore.visible = false; // Independent source blood state is OFF by default, even Dead.
        if (dead) clip = "down_hold";
        if (clip != null && !clip.equals("rest")) {
            KeyframeAnimations.animate(this, AnimalPenguinShamebreroAnimations.byName(clip),
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
