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

/** Generated SS14 raccoon asset rig. CC-BY-SA3.0 art; retain ATTRIBUTION.md.
 * Native1.21.1 API, based on pinned Blockbench4.12.6 geometry conventions.
 * NOT a registered entity/renderer/AI/networking implementation.
 */
public final class AnimalRaccoonModel<T extends LivingEntity> extends HierarchicalModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
        ResourceLocation.fromNamespaceAndPath("moonstation14", "animals/raccoon"), "main");
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
    public AnimalRaccoonModel(ModelPart root) {
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
        PartDefinition body = mouse.addOrReplaceChild("body", CubeListBuilder.create().texOffs(1, 1).addBox(-5.0F, -2.13848824F, -5.29067776F, 10.0F, 4.27697647F, 10.5813555F).texOffs(45, 1).addBox(-4.0F, -3.029525F, -4.33835576F, 8.0F, 1.30685392F, 8.67671152F).texOffs(81, 1).addBox(-3.75F, 1.48506128F, -4.49707609F, 7.5F, 1.54446373F, 7.93601664F), PartPose.offsetAndRotation(-0.0F, -5.9402451F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(1, 18).addBox(-3.27272727F, -2.4503511F, -2.97600624F, 6.54545455F, 4.90070221F, 4.62934304F).texOffs(26, 18).addBox(-2.12727273F, -0.482644914F, -3.90187485F, 4.25454545F, 2.4503511F, 1.58720333F).texOffs(40, 18).addBox(-0.818181818F, 0.297012255F, -3.94155493F, 1.63636364F, 0.891036765F, 0.396800832F).texOffs(52, 30).addBox(-3.18181818F, -1.33655515F, -3.13472657F, 6.36363636F, 1.48506128F, 0.105813555F).texOffs(67, 30).addBox(-2.5F, -0.853910233F, -3.3066736F, 0.636363636F, 0.519771446F, 0.264533888F).texOffs(71, 30).addBox(1.86363636F, -0.853910233F, -3.3066736F, 0.636363636F, 0.519771446F, 0.264533888F), PartPose.offsetAndRotation(-0.0F, -1.48506128F, -4.62934304F, -0.0F, -0.0F, 0.0F));
        PartDefinition ear_left = head.addOrReplaceChild("ear_left", CubeListBuilder.create().texOffs(47, 18).addBox(-0.916363636F, -2.20531599F, -0.33066736F, 1.83272727F, 2.20531599F, 0.66133472F).texOffs(54, 18).addBox(-0.549818182F, -1.81938569F, -0.423254221F, 1.09963636F, 1.4334554F, 0.105813555F), PartPose.offsetAndRotation(-2.35636364F, -2.22759191F, 0.0F, -0.0F, -0.0F, -0.20943951F));
        PartDefinition ear_right = head.addOrReplaceChild("ear_right", CubeListBuilder.create().texOffs(59, 18).addBox(-0.916363636F, -2.20531599F, -0.33066736F, 1.83272727F, 2.20531599F, 0.66133472F).texOffs(66, 18).addBox(-0.549818182F, -1.81938569F, -0.423254221F, 1.09963636F, 1.4334554F, 0.105813555F), PartPose.offsetAndRotation(2.35636364F, -2.22759191F, 0.0F, -0.0F, -0.0F, 0.20943951F));
        PartDefinition front_left = body.addOrReplaceChild("front_left", CubeListBuilder.create().texOffs(71, 18).addBox(-0.8F, -0.178207353F, -0.581974553F, 1.6F, 2.99982378F, 1.16394911F).texOffs(79, 18).addBox(-0.96F, 2.6731103F, -1.13749572F, 1.92F, 0.891036765F, 1.74592366F), PartPose.offsetAndRotation(-3.5F, 2.37609804F, -3.38603377F, -0.0F, -0.0F, 0.0F));
        PartDefinition front_right = body.addOrReplaceChild("front_right", CubeListBuilder.create().texOffs(89, 18).addBox(-0.8F, -0.178207353F, -0.581974553F, 1.6F, 2.99982378F, 1.16394911F).texOffs(97, 18).addBox(-0.96F, 2.6731103F, -1.13749572F, 1.92F, 0.891036765F, 1.74592366F), PartPose.offsetAndRotation(3.5F, 2.37609804F, -3.38603377F, -0.0F, -0.0F, 0.0F));
        PartDefinition hind_left = body.addOrReplaceChild("hind_left", CubeListBuilder.create().texOffs(107, 18).addBox(-0.8F, -0.178207353F, -0.581974553F, 1.6F, 2.99982378F, 1.16394911F).texOffs(115, 18).addBox(-0.96F, 2.6731103F, -1.13749572F, 1.92F, 0.891036765F, 1.74592366F), PartPose.offsetAndRotation(-3.5F, 2.37609804F, 3.38603377F, -0.0F, -0.0F, 0.0F));
        PartDefinition hind_right = body.addOrReplaceChild("hind_right", CubeListBuilder.create().texOffs(1, 30).addBox(-0.8F, -0.178207353F, -0.581974553F, 1.6F, 2.99982378F, 1.16394911F).texOffs(9, 30).addBox(-0.96F, 2.6731103F, -1.13749572F, 1.92F, 0.891036765F, 1.74592366F), PartPose.offsetAndRotation(3.5F, 2.37609804F, 3.38603377F, -0.0F, -0.0F, 0.0F));
        PartDefinition tail_0 = body.addOrReplaceChild("tail_0", CubeListBuilder.create().texOffs(19, 30).addBox(-1.36363636F, -1.11379596F, -0.0793601664F, 2.72727273F, 2.22759191F, 2.14272449F), PartPose.offsetAndRotation(-0.0F, 0.0F, 5.29067776F, -0.122173048F, -0.20943951F, 0.0F));
        PartDefinition tail_1 = tail_0.addOrReplaceChild("tail_1", CubeListBuilder.create().texOffs(31, 30).addBox(-1.09090909F, -0.891036765F, -0.0793601664F, 2.18181818F, 1.78207353F, 2.14272449F), PartPose.offsetAndRotation(-0.0F, 0.0F, 1.98400416F, -0.122173048F, 0.20943951F, 0.0F));
        PartDefinition tail_2 = tail_1.addOrReplaceChild("tail_2", CubeListBuilder.create().texOffs(42, 30).addBox(-0.818181818F, -0.668277574F, -0.0793601664F, 1.63636364F, 1.33655515F, 2.14272449F), PartPose.offsetAndRotation(-0.0F, 0.0F, 1.98400416F, -0.122173048F, -0.20943951F, 0.0F));
        PartDefinition gore = mouse.addOrReplaceChild("gore", CubeListBuilder.create().texOffs(75, 30).addBox(-2.36363636F, -0.059402451F, -1.6533368F, 4.72727273F, 0.0445518383F, 3.3066736F), PartPose.offsetAndRotation(-0.0F, 0.0F, 0.0F, -0.0F, -0.0F, 0.0F));
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
            throw new IllegalArgumentException("Nonfinite raccoon presentation");
        root.getAllParts().forEach(ModelPart::resetPose);
        gore.visible = false; // Independent source blood state is OFF by default, even Dead.
        if (dead) clip = "down_hold";
        if (clip != null && !clip.equals("rest")) {
            KeyframeAnimations.animate(this, AnimalRaccoonAnimations.byName(clip),
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
