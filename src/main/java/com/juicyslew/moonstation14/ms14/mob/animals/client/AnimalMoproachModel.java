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

/** Generated SS14 moproach asset rig. CC-BY-SA3.0 art; retain ATTRIBUTION.md.
 * Native1.21.1 API, based on pinned Blockbench4.12.6 geometry conventions.
 * NOT a registered entity/renderer/AI/networking implementation.
 */
public final class AnimalMoproachModel<T extends LivingEntity> extends HierarchicalModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
        ResourceLocation.fromNamespaceAndPath("moonstation14", "animals/moproach"), "main");
    private final ModelPart root;
    private final ModelPart mouse;
    private final ModelPart body;
    private final ModelPart abdomen;
    private final ModelPart head;
    private final ModelPart antenna_left;
    private final ModelPart antenna_right;
    private final ModelPart ruff_tuft_left0;
    private final ModelPart ruff_tuft_left1;
    private final ModelPart ruff_tuft_left2;
    private final ModelPart ruff_tuft_right0;
    private final ModelPart ruff_tuft_right1;
    private final ModelPart ruff_tuft_right2;
    private final ModelPart leg_0_left;
    private final ModelPart leg_0_right;
    private final ModelPart leg_1_left;
    private final ModelPart leg_1_right;
    private final ModelPart leg_2_left;
    private final ModelPart leg_2_right;
    private final ModelPart gore;
    public AnimalMoproachModel(ModelPart root) {
        this.root = root;
        this.mouse = root.getChild("mouse");
        this.body = this.mouse.getChild("body");
        this.abdomen = this.body.getChild("abdomen");
        this.head = this.body.getChild("head");
        this.antenna_left = this.head.getChild("antenna_left");
        this.antenna_right = this.head.getChild("antenna_right");
        this.ruff_tuft_left0 = this.body.getChild("ruff_tuft_left0");
        this.ruff_tuft_left1 = this.body.getChild("ruff_tuft_left1");
        this.ruff_tuft_left2 = this.body.getChild("ruff_tuft_left2");
        this.ruff_tuft_right0 = this.body.getChild("ruff_tuft_right0");
        this.ruff_tuft_right1 = this.body.getChild("ruff_tuft_right1");
        this.ruff_tuft_right2 = this.body.getChild("ruff_tuft_right2");
        this.leg_0_left = this.body.getChild("leg_0_left");
        this.leg_0_right = this.body.getChild("leg_0_right");
        this.leg_1_left = this.body.getChild("leg_1_left");
        this.leg_1_right = this.body.getChild("leg_1_right");
        this.leg_2_left = this.body.getChild("leg_2_left");
        this.leg_2_right = this.body.getChild("leg_2_right");
        this.gore = this.mouse.getChild("gore");
        this.gore.visible = false;
    }
    @Override public ModelPart root() { return root; }
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition base = mesh.getRoot();
        PartDefinition mouse = base.addOrReplaceChild("mouse", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 24.0F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition body = mouse.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(-0.0F, -3.52116681F, 0.778095627F, -0.0F, -0.0F, 0.0F));
        // PORT rear-abdomen articulation: identity wrapper preserves authored geometry/vertices; not an upstream tail-bone claim.
        body.addOrReplaceChild("abdomen", CubeListBuilder.create().texOffs(1, 1).addBox(-1.75F, 1.58095886F, -4.25F, 3.5F, 1.0F, 8.5F).texOffs(27, 1).addBox(-2.5F, 0.797539056F, -4.5F, 5.0F, 1.0F, 9.0F).texOffs(57, 1).addBox(-2.5F, -1.0F, -4.5F, 5.0F, 2.0F, 9.0F).texOffs(87, 1).addBox(-2.5F, -1.79753906F, -4.5F, 5.0F, 1.0F, 9.0F).texOffs(1, 14).addBox(-1.75F, -2.58095886F, -4.25F, 3.5F, 1.0F, 8.5F).texOffs(27, 14).addBox(-2.0F, 0.586057379F, -4.61238251F, 4.0F, 1.0F, 3.0F).texOffs(43, 14).addBox(-2.5F, -0.0973513888F, -4.86238251F, 5.0F, 1.0F, 3.5F).texOffs(62, 14).addBox(-3.0F, -1.72924716F, -4.86238251F, 6.0F, 2.0F, 3.5F).texOffs(83, 14).addBox(-2.5F, -2.36114293F, -4.86238251F, 5.0F, 1.0F, 3.5F).texOffs(102, 14).addBox(-2.0F, -3.0445517F, -4.61238251F, 4.0F, 1.0F, 3.0F).texOffs(105, 26).addBox(-1.5F, 0.932449781F, 2.33469555F, 3.0F, 1.0F, 2.0F).texOffs(1, 33).addBox(-2.25F, 0.432394585F, 2.33469555F, 4.5F, 1.0F, 2.0F).texOffs(16, 33).addBox(-2.5F, -0.645821834F, 2.33469555F, 5.0F, 1.5F, 2.0F).texOffs(32, 33).addBox(-2.25F, -1.22403825F, 2.33469555F, 4.5F, 1.0F, 2.0F).texOffs(47, 33).addBox(-1.5F, -1.72409345F, 2.33469555F, 3.0F, 1.0F, 2.0F).texOffs(59, 33).addBox(-1.25F, 0.733209039F, 3.22394769F, 2.5F, 1.0F, 2.0F).texOffs(70, 33).addBox(-1.75F, 0.308162122F, 3.22394769F, 3.5F, 1.0F, 2.0F).texOffs(83, 33).addBox(-2.0F, -0.395821834F, 3.22394769F, 4.0F, 1.0F, 2.0F).texOffs(97, 33).addBox(-1.75F, -1.09980579F, 3.22394769F, 3.5F, 1.0F, 2.0F).texOffs(110, 33).addBox(-1.25F, -1.52485271F, 3.22394769F, 2.5F, 1.0F, 2.0F).texOffs(1, 39).addBox(-1.0F, 0.533968297F, 4.11319984F, 2.0F, 1.0F, 2.0F).texOffs(11, 39).addBox(-1.5F, 0.183929659F, 4.11319984F, 3.0F, 1.0F, 2.0F).texOffs(23, 39).addBox(-1.5F, -0.395821834F, 4.11319984F, 3.0F, 1.0F, 2.0F).texOffs(35, 39).addBox(-1.5F, -0.975573327F, 4.11319984F, 3.0F, 1.0F, 2.0F).texOffs(47, 39).addBox(-1.0F, -1.32561196F, 4.11319984F, 2.0F, 1.0F, 2.0F).texOffs(57, 39).addBox(-0.75F, 0.584727554F, 5.00245198F, 1.5F, 0.5F, 2.0F).texOffs(66, 39).addBox(-1.0F, 0.309697196F, 5.00245198F, 2.0F, 0.5F, 2.0F).texOffs(76, 39).addBox(-1.25F, -0.395821834F, 5.00245198F, 2.5F, 1.0F, 2.0F).texOffs(87, 39).addBox(-1.0F, -0.601340864F, 5.00245198F, 2.0F, 0.5F, 2.0F).texOffs(97, 39).addBox(-0.75F, -0.876371222F, 5.00245198F, 1.5F, 0.5F, 2.0F), PartPose.ZERO);
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(1, 26).addBox(-1.5F, 1.71378603F, -1.58346955F, 3.0F, 1.0F, 2.5F).texOffs(14, 26).addBox(-2.25F, 0.880360698F, -1.83346955F, 4.5F, 1.0F, 3.0F).texOffs(31, 26).addBox(-2.5F, -1.0F, -1.83346955F, 5.0F, 2.0F, 3.0F).texOffs(49, 26).addBox(-2.25F, -1.8803607F, -1.83346955F, 4.5F, 1.0F, 3.0F).texOffs(66, 26).addBox(-1.5F, -2.71378603F, -1.58346955F, 3.0F, 1.0F, 2.5F).texOffs(79, 26).addBox(-2.87548032F, -0.541643668F, -1.55598692F, 1.5F, 1.5F, 1.0F).texOffs(92, 26).addBox(1.37548032F, -0.541643668F, -1.55598692F, 1.5F, 1.5F, 1.0F).texOffs(34, 57).addBox(-1.39924227F, -0.979191965F, -2.05073559F, 0.5F, 1.5F, 0.5F).texOffs(38, 57).addBox(-1.89924227F, -0.479191965F, -2.05873559F, 1.5F, 0.5F, 0.5F).texOffs(44, 57).addBox(0.899242268F, -0.979191965F, -2.05073559F, 0.5F, 1.5F, 0.5F).texOffs(48, 57).addBox(0.399242268F, -0.479191965F, -2.05873559F, 1.5F, 0.5F, 0.5F), PartPose.offsetAndRotation(-0.0F, -0.625068995F, -4.77973028F, -0.0F, -0.0F, 0.0F));
        PartDefinition antenna_left = head.addOrReplaceChild("antenna_left", CubeListBuilder.create().texOffs(86, 26).addBox(-0.5F, -1.53133624F, -0.5F, 1.0F, 1.5F, 1.0F), PartPose.offsetAndRotation(-1.35931881F, -1.77102882F, -0.333469555F, -0.0F, -0.0F, -0.837758041F));
        PartDefinition antenna_right = head.addOrReplaceChild("antenna_right", CubeListBuilder.create().texOffs(99, 26).addBox(-0.5F, -1.53133624F, -0.5F, 1.0F, 1.5F, 1.0F), PartPose.offsetAndRotation(1.35931881F, -1.77102882F, -0.333469555F, -0.0F, -0.0F, 0.837758041F));
        PartDefinition ruff_tuft_left0 = body.addOrReplaceChild("ruff_tuft_left0", CubeListBuilder.create().texOffs(106, 39).addBox(-0.5F, -1.0F, -0.5F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(-2.53327597F, -0.104178166F, -2.22313036F, -0.0F, -0.0F, -0.383972435F));
        PartDefinition ruff_tuft_left1 = body.addOrReplaceChild("ruff_tuft_left1", CubeListBuilder.create().texOffs(112, 39).addBox(-0.5F, -1.0F, -0.5F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(-2.53327597F, 0.0520890829F, -0.222313036F, -0.0F, -0.0F, -0.383972435F));
        PartDefinition ruff_tuft_left2 = body.addOrReplaceChild("ruff_tuft_left2", CubeListBuilder.create().texOffs(118, 39).addBox(-0.5F, -1.0F, -0.5F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(-2.53327597F, 0.208356332F, 1.77850429F, -0.0F, -0.0F, -0.383972435F));
        PartDefinition ruff_tuft_right0 = body.addOrReplaceChild("ruff_tuft_right0", CubeListBuilder.create().texOffs(1, 45).addBox(-0.5F, -1.0F, -0.5F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(2.53327597F, -0.104178166F, -2.22313036F, -0.0F, -0.0F, 0.383972435F));
        PartDefinition ruff_tuft_right1 = body.addOrReplaceChild("ruff_tuft_right1", CubeListBuilder.create().texOffs(7, 45).addBox(-0.5F, -1.0F, -0.5F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(2.53327597F, 0.0520890829F, -0.222313036F, -0.0F, -0.0F, 0.383972435F));
        PartDefinition ruff_tuft_right2 = body.addOrReplaceChild("ruff_tuft_right2", CubeListBuilder.create().texOffs(13, 45).addBox(-0.5F, -1.0F, -0.5F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(2.53327597F, 0.208356332F, 1.77850429F, -0.0F, -0.0F, 0.383972435F));
        PartDefinition leg_0_left = body.addOrReplaceChild("leg_0_left", CubeListBuilder.create().texOffs(19, 45).addBox(-0.932510531F, -0.0728419217F, -0.5F, 1.0F, 1.5F, 1.0F).texOffs(25, 45).addBox(-1.05893609F, 0.645959825F, -1.25F, 1.5F, 1.0F, 2.5F).texOffs(35, 45).addBox(-0.101710675F, 1.11473397F, -1.25F, 0.5F, 0.5F, 2.5F).texOffs(43, 45).addBox(-0.558936093F, 1.11473397F, -1.5F, 0.5F, 0.5F, 3.0F).texOffs(52, 45).addBox(-1.01616151F, 1.11473397F, -1.25F, 0.5F, 0.5F, 2.5F), PartPose.offsetAndRotation(-1.79182934F, 1.87520699F, -3.00122599F, -0.0F, -0.0F, 0.0F));
        PartDefinition leg_0_right = body.addOrReplaceChild("leg_0_right", CubeListBuilder.create().texOffs(60, 45).addBox(-0.0674894691F, -0.0728419217F, -0.5F, 1.0F, 1.5F, 1.0F).texOffs(66, 45).addBox(-0.441063907F, 0.645959825F, -1.25F, 1.5F, 1.0F, 2.5F).texOffs(76, 45).addBox(0.516161512F, 1.11473397F, -1.25F, 0.5F, 0.5F, 2.5F).texOffs(84, 45).addBox(0.0589360935F, 1.11473397F, -1.5F, 0.5F, 0.5F, 3.0F).texOffs(93, 45).addBox(-0.398289325F, 1.11473397F, -1.25F, 0.5F, 0.5F, 2.5F), PartPose.offsetAndRotation(1.79182934F, 1.87520699F, -3.00122599F, -0.0F, -0.0F, 0.0F));
        PartDefinition leg_1_left = body.addOrReplaceChild("leg_1_left", CubeListBuilder.create().texOffs(101, 45).addBox(-0.932510531F, -0.0728419217F, -0.5F, 1.0F, 1.5F, 1.0F).texOffs(107, 45).addBox(-1.05893609F, 0.645959825F, -1.25F, 1.5F, 1.0F, 2.5F).texOffs(117, 45).addBox(-0.101710675F, 1.11473397F, -1.25F, 0.5F, 0.5F, 2.5F).texOffs(1, 51).addBox(-0.558936093F, 1.11473397F, -1.5F, 0.5F, 0.5F, 3.0F).texOffs(10, 51).addBox(-1.01616151F, 1.11473397F, -1.25F, 0.5F, 0.5F, 2.5F), PartPose.offsetAndRotation(-1.79182934F, 1.87520699F, -0.333469555F, -0.0F, -0.0F, 0.0F));
        PartDefinition leg_1_right = body.addOrReplaceChild("leg_1_right", CubeListBuilder.create().texOffs(18, 51).addBox(-0.0674894691F, -0.0728419217F, -0.5F, 1.0F, 1.5F, 1.0F).texOffs(24, 51).addBox(-0.441063907F, 0.645959825F, -1.25F, 1.5F, 1.0F, 2.5F).texOffs(34, 51).addBox(0.516161512F, 1.11473397F, -1.25F, 0.5F, 0.5F, 2.5F).texOffs(42, 51).addBox(0.0589360935F, 1.11473397F, -1.5F, 0.5F, 0.5F, 3.0F).texOffs(51, 51).addBox(-0.398289325F, 1.11473397F, -1.25F, 0.5F, 0.5F, 2.5F), PartPose.offsetAndRotation(1.79182934F, 1.87520699F, -0.333469555F, -0.0F, -0.0F, 0.0F));
        PartDefinition leg_2_left = body.addOrReplaceChild("leg_2_left", CubeListBuilder.create().texOffs(59, 51).addBox(-0.932510531F, -0.0728419217F, -0.5F, 1.0F, 1.5F, 1.0F).texOffs(65, 51).addBox(-1.05893609F, 0.645959825F, -1.25F, 1.5F, 1.0F, 2.5F).texOffs(75, 51).addBox(-0.101710675F, 1.11473397F, -1.25F, 0.5F, 0.5F, 2.5F).texOffs(84, 51).addBox(-0.558936093F, 1.11473397F, -1.5F, 0.5F, 0.5F, 3.0F).texOffs(94, 51).addBox(-1.01616151F, 1.11473397F, -1.25F, 0.5F, 0.5F, 2.5F), PartPose.offsetAndRotation(-1.79182934F, 1.87520699F, 2.33428688F, -0.0F, -0.0F, 0.0F));
        PartDefinition leg_2_right = body.addOrReplaceChild("leg_2_right", CubeListBuilder.create().texOffs(103, 51).addBox(-0.0674894691F, -0.0728419217F, -0.5F, 1.0F, 1.5F, 1.0F).texOffs(109, 51).addBox(-0.441063907F, 0.645959825F, -1.25F, 1.5F, 1.0F, 2.5F).texOffs(119, 51).addBox(0.516161512F, 1.11473397F, -1.25F, 0.5F, 0.5F, 2.5F).texOffs(1, 57).addBox(0.0589360935F, 1.11473397F, -1.5F, 0.5F, 0.5F, 3.0F).texOffs(11, 57).addBox(-0.398289325F, 1.11473397F, -1.25F, 0.5F, 0.5F, 2.5F), PartPose.offsetAndRotation(1.79182934F, 1.87520699F, 2.33428688F, -0.0F, -0.0F, 0.0F));
        PartDefinition gore = mouse.addOrReplaceChild("gore", CubeListBuilder.create().texOffs(20, 57).addBox(-1.5F, -0.276044541F, -1.5F, 3.0F, 0.5F, 3.0F), PartPose.offsetAndRotation(-0.0F, 0.0F, 0.0F, -0.0F, -0.0F, 0.0F));
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
            throw new IllegalArgumentException("Nonfinite moproach presentation");
        root.getAllParts().forEach(ModelPart::resetPose);
        gore.visible = false; // Independent source blood state is OFF by default, even Dead.
        if (dead) clip = "down_hold";
        var animation = clip != null && !clip.equals("rest") ? AnimalMoproachAnimations.byName(clip) : null;
        if (animation != null) {
            KeyframeAnimations.animate(this, animation,
                Math.round(Math.max(0, elapsedSeconds) * 1000.0F), 1.0F, new Vector3f());
        }
        boolean aliveLook = !dead && (clip == null || clip.equals("rest") ||
            clip.equals("walk") || clip.equals("run") || clip.equals("idle_breathe"));
        if (aliveLook) {
            head.yRot -= Math.max(-25, Math.min(25, yawDegrees)) * (float)(Math.PI / 180);
            head.xRot -= Math.max(-12, Math.min(12, pitchDegrees)) * (float)(Math.PI / 180);
        }
        // Additional motion is strictly gait-only; reset above leaves rest/down/corpse at identity.
        abdomen.yRot = PORTAbdomenMotion.yaw(clip, dead, elapsedSeconds,
                animation == null ? 0.0F : animation.lengthInSeconds());
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


