package com.juicyslew.moonstation14.ms14.mob.mouse.client;

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

/** Generated SS14 mouse asset rig. CC-BY-SA3.0 art; retain ATTRIBUTION.md.
 * Native1.21.1 API, based on pinned Blockbench4.12.6 geometry conventions.
 * NOT a registered entity/renderer/AI/networking implementation.
 */
public final class MouseModel<T extends LivingEntity> extends HierarchicalModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
        ResourceLocation.fromNamespaceAndPath("moonstation14", "mouse"), "main");
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
    private final ModelPart tail_base;
    private final ModelPart tail_middle;
    private final ModelPart tail_tip;
    private final ModelPart gore;
    public MouseModel(ModelPart root) {
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
        this.tail_base = this.body.getChild("tail_base");
        this.tail_middle = this.tail_base.getChild("tail_middle");
        this.tail_tip = this.tail_middle.getChild("tail_tip");
        this.gore = this.mouse.getChild("gore");
        this.gore.visible = false;
    }
    @Override public ModelPart root() { return root; }
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition base = mesh.getRoot();
        PartDefinition mouse = base.addOrReplaceChild("mouse", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 24.0F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition body = mouse.addOrReplaceChild("body", CubeListBuilder.create().texOffs(1, 1).addBox(-1.3875F, -0.6375F, -0.8505F, 2.775F, 1.84875F, 2.1735F).texOffs(13, 1).addBox(-1.11F, -1.1475F, -0.61425F, 2.22F, 0.70125F, 1.701F).texOffs(23, 1).addBox(-1.1655F, -0.51F, 1.134F, 2.331F, 1.46625F, 0.590625F).texOffs(31, 1).addBox(-1.02675F, 0.765F, -0.6615F, 2.0535F, 0.51F, 1.89F), PartPose.offsetAndRotation(-0.0F, -1.625F, -0.4995F, -0.0F, -0.0F, 0.0F));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(41, 1).addBox(-1.485F, -1.35F, -1.485F, 2.97F, 2.43F, 2.52F).texOffs(54, 1).addBox(-1.305F, -1.8F, -1.035F, 2.61F, 0.765F, 1.755F).texOffs(1, 8).addBox(-1.08F, -0.135F, -2.205F, 2.16F, 1.35F, 0.945F).texOffs(10, 8).addBox(-0.63F, 0.315F, -2.565F, 1.26F, 0.72F, 0.495F).texOffs(16, 8).addBox(-0.315F, 0.405F, -2.727F, 0.63F, 0.45F, 0.207F).texOffs(20, 8).addBox(-0.72F, 0.882F, -2.088F, 1.44F, 0.351F, 1.008F).texOffs(27, 8).addBox(-1.611F, -0.954F, -1.521F, 0.396F, 0.549F, 0.351F).texOffs(1, 13).addBox(1.215F, -0.954F, -1.521F, 0.396F, 0.549F, 0.351F), PartPose.offsetAndRotation(-0.0F, -0.475F, -1.1505F, -0.0F, -0.0F, 0.0F));
        PartDefinition ear_left = head.addOrReplaceChild("ear_left", CubeListBuilder.create().texOffs(31, 8).addBox(-1.02375F, -0.66F, -0.1875F, 2.0475F, 0.66F, 0.45F).texOffs(38, 8).addBox(-1.4625F, -1.8975F, -0.1875F, 2.925F, 1.2375F, 0.45F).texOffs(47, 8).addBox(-0.975F, -2.475F, -0.1875F, 1.95F, 0.5775F, 0.45F).texOffs(54, 8).addBox(-0.61425F, -1.36125F, -0.2175F, 1.2285F, 0.93225F, 0.0375F).texOffs(59, 8).addBox(-0.73125F, -1.8975F, -0.2175F, 1.4625F, 0.7425F, 0.0375F), PartPose.offsetAndRotation(-1.935F, -1.35F, 0.09F, 0.13962634F, 0.436332313F, -0.20943951F));
        PartDefinition ear_right = head.addOrReplaceChild("ear_right", CubeListBuilder.create().texOffs(5, 13).addBox(-1.02375F, -0.66F, -0.1875F, 2.0475F, 0.66F, 0.45F).texOffs(12, 13).addBox(-1.4625F, -1.8975F, -0.1875F, 2.925F, 1.2375F, 0.45F).texOffs(21, 13).addBox(-0.975F, -2.475F, -0.1875F, 1.95F, 0.5775F, 0.45F).texOffs(28, 13).addBox(-0.61425F, -1.36125F, -0.2175F, 1.2285F, 0.93225F, 0.0375F).texOffs(33, 13).addBox(-0.73125F, -1.8975F, -0.2175F, 1.4625F, 0.7425F, 0.0375F), PartPose.offsetAndRotation(1.935F, -1.35F, 0.09F, 0.13962634F, -0.436332313F, 0.20943951F));
        PartDefinition front_left = body.addOrReplaceChild("front_left", CubeListBuilder.create().texOffs(39, 13).addBox(-0.189F, -0.0714F, -0.18375F, 0.378F, 0.6384F, 0.40425F).texOffs(43, 13).addBox(-0.273F, 0.525F, -0.378F, 0.546F, 0.21F, 0.651F), PartPose.offsetAndRotation(-0.888F, 0.89F, -0.89775F, -0.0F, -0.0F, 0.0F));
        PartDefinition front_right = body.addOrReplaceChild("front_right", CubeListBuilder.create().texOffs(48, 13).addBox(-0.189F, -0.0714F, -0.18375F, 0.378F, 0.6384F, 0.40425F).texOffs(52, 13).addBox(-0.273F, 0.525F, -0.378F, 0.546F, 0.21F, 0.651F), PartPose.offsetAndRotation(0.888F, 0.89F, -0.89775F, -0.0F, -0.0F, 0.0F));
        PartDefinition hind_left = body.addOrReplaceChild("hind_left", CubeListBuilder.create().texOffs(57, 13).addBox(-0.189F, -0.0714F, -0.18375F, 0.378F, 0.6384F, 0.40425F).texOffs(1, 17).addBox(-0.273F, 0.525F, -0.378F, 0.546F, 0.21F, 0.651F), PartPose.offsetAndRotation(-1.0545F, 0.89F, 0.968625F, -0.0F, -0.0F, 0.0F));
        PartDefinition hind_right = body.addOrReplaceChild("hind_right", CubeListBuilder.create().texOffs(6, 17).addBox(-0.189F, -0.0714F, -0.18375F, 0.378F, 0.6384F, 0.40425F).texOffs(10, 17).addBox(-0.273F, 0.525F, -0.378F, 0.546F, 0.21F, 0.651F), PartPose.offsetAndRotation(1.0545F, 0.89F, 0.968625F, -0.0F, -0.0F, 0.0F));
        PartDefinition tail_base = body.addOrReplaceChild("tail_base", CubeListBuilder.create().texOffs(15, 17).addBox(-0.225F, -0.225F, 0.0F, 0.45F, 0.45F, 1.725F), PartPose.offsetAndRotation(-0.0F, 0.57375F, 1.6065F, 0.174532925F, -0.314159265F, 0.0F));
        PartDefinition tail_middle = tail_base.addOrReplaceChild("tail_middle", CubeListBuilder.create().texOffs(22, 17).addBox(-0.18F, -0.18F, -0.0375F, 0.36F, 0.36F, 1.4625F), PartPose.offsetAndRotation(-0.0F, 0.0F, 1.6875F, 0.436332313F, 0.558505361F, 0.0F));
        PartDefinition tail_tip = tail_middle.addOrReplaceChild("tail_tip", CubeListBuilder.create().texOffs(28, 17).addBox(-0.135F, -0.135F, -0.0375F, 0.27F, 0.27F, 1.2375F), PartPose.offsetAndRotation(-0.0F, 0.0F, 1.3875F, 0.610865238F, 0.41887902F, 0.0F));
        PartDefinition gore = mouse.addOrReplaceChild("gore", CubeListBuilder.create().texOffs(34, 17).addBox(-0.444F, -0.04125F, -1.6335F, 1.6095F, 0.0225F, 1.46475F).texOffs(43, 17).addBox(-1.24875F, -0.0435F, -2.295F, 1.02675F, 0.02325F, 1.323F).texOffs(50, 17).addBox(-1.0545F, -0.045F, -0.334125F, 1.4985F, 0.024F, 0.826875F).texOffs(57, 17).addBox(0.9435F, -0.165F, -1.042875F, 0.30525F, 0.135F, 0.259875F), PartPose.offsetAndRotation(-0.0F, 0.0F, 0.0F, -0.0F, -0.0F, 0.0F));
        // 128x128 PNG, logical64x64:32 physical texels/block, NOT doubled geometry.
        return LayerDefinition.create(mesh, 64, 64);
    }
    /** Reset every bone FIRST. Host may call after setupAnim with authoritative
     * state. Negative flinch time disables optional PORT hurt overlay.
     * Critical: down_hold,false. Dead: down_hold,true. Never vanilla corpse flip.
     * Look inputs are canonical +Y yaw/+X pitch. Mojmaps netHeadYaw/headPitch
     * are negated by setupAnim below; host adapters must do that ONCE, not twice.
     */
    public void applyPresentation(String clip, boolean dead, float elapsedSeconds,
            float yawDegrees, float pitchDegrees, float flinchSeconds, float cadenceScale) {
        if (!Float.isFinite(elapsedSeconds) || !Float.isFinite(yawDegrees) ||
            !Float.isFinite(pitchDegrees) || !Float.isFinite(flinchSeconds))
            throw new IllegalArgumentException("Nonfinite mouse presentation");
        root.getAllParts().forEach(ModelPart::resetPose);
        gore.visible = dead;
        if (dead) clip = "down_hold";
        if (clip != null && !clip.equals("rest")) {
            KeyframeAnimations.animate(this, MouseAnimations.byName(clip),
                Math.round(Math.max(0, elapsedSeconds) * 1000.0F),
                "run".equals(clip) && Float.isFinite(cadenceScale) ? Math.max(1f, cadenceScale) : 1f,
                new Vector3f());
        }
        boolean aliveLook = !dead && (clip == null || clip.equals("rest") ||
            clip.equals("walk") || clip.equals("run") || clip.equals("idle_breathe"));
        if (aliveLook) {
            head.yRot -= Math.max(-25, Math.min(25, yawDegrees)) * (float)(Math.PI / 180);
            head.xRot -= Math.max(-12, Math.min(12, pitchDegrees)) * (float)(Math.PI / 180);
            if (flinchSeconds >= 0 && flinchSeconds <= .2F)
                KeyframeAnimations.animate(this, MouseAnimations.HURT,
                    Math.round(flinchSeconds * 1000.0F), 1.0F, new Vector3f());
        }
    }
    @Override public void setupAnim(T entity, float limbSwing, float limbSwingAmount,
            float ageInTicks, float netHeadYaw, float headPitch) {
        // Safe vanilla fallback ONLY. Host authoritative Critical/sprint/feeding/
        // swim/airborne state must override through applyPresentation; see contract.
        boolean dead = entity.isDeadOrDying();
        if (entity instanceof com.juicyslew.moonstation14.entities.MouseMob mouse) {
            var state = mouse.getExistingDataOrNull(
                com.juicyslew.moonstation14.component.ModDataAttachments.MOB_STATE.get());
            if (state != null) {
                var mobState = state.data().state();
                if (mobState == com.juicyslew.moonstation14.ms14.character.components.MobStateComponent.State.DEAD) {
                    applyPresentation("down_hold", true, ageInTicks / 20.0F, 0, 0, -1, 1);
                    return;
                }
                if (mobState == com.juicyslew.moonstation14.ms14.character.components.MobStateComponent.State.CRITICAL) {
                    applyPresentation("down_hold", false, ageInTicks / 20.0F, 0, 0, -1, 1);
                    return;
                }
            }
        }
        // Do not use limbSwingAmount here: vanilla's smoothed walk animation
        // intentionally eases out over several ticks, which leaves a mouse
        // visibly walking after its body has stopped. Position delta is the
        // rendered body's immediate movement signal (and works for controlled
        // mice whose sprint intent is not mirrored to isSprinting()).
        double dx = entity.getX() - entity.xo;
        double dz = entity.getZ() - entity.zo;
        double walkSpeed = 0d;
        if (entity instanceof com.juicyslew.moonstation14.entities.MouseMob mouse) {
            try {
                var data = com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem
                    .projectForActor(mouse).orElse(null);
                if (data != null) walkSpeed = com.juicyslew.moonstation14.ms14.movement.CharacterMovementPolicy
                    .fromCharacterData(data).walkSpeedPerSecond();
            } catch (RuntimeException ignored) {
                // Prototype data may not yet be available on a client; retain the authored cadence.
            }
        }
        String clip = MouseLocomotionPresentation.clip(dx, dz, dead, false);
        applyPresentation(clip, dead, ageInTicks / 20.0F, -netHeadYaw, -headPitch, -1,
            com.juicyslew.moonstation14.ms14.player_body_control.movement.OwnedHarnessWalkAnimation
                .cadenceScale(dx, dz, walkSpeed));
    }
}
