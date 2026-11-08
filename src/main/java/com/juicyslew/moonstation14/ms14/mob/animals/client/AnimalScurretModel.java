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

/** Generated SS14 scurret asset rig. CC-BY-SA3.0 art; retain ATTRIBUTION.md.
 * Native1.21.1 API, based on pinned Blockbench4.12.6 geometry conventions.
 * NOT a registered entity/renderer/AI/networking implementation.
 */
public final class AnimalScurretModel<T extends LivingEntity> extends HierarchicalModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
        ResourceLocation.fromNamespaceAndPath("moonstation14", "animals/scurret"), "main");
    private final ModelPart root;
    private final ModelPart mouse;
    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart ear_left;
    private final ModelPart arm_left;
    private final ModelPart foot_left;
    private final ModelPart ear_right;
    private final ModelPart arm_right;
    private final ModelPart foot_right;
    private final ModelPart tail_0;
    private final ModelPart tail_1;
    private final ModelPart tail_2;
    private final ModelPart gore;
    public AnimalScurretModel(ModelPart root) {
        this.root = root;
        this.mouse = root.getChild("mouse");
        this.body = this.mouse.getChild("body");
        this.head = this.body.getChild("head");
        this.ear_left = this.head.getChild("ear_left");
        this.arm_left = this.body.getChild("arm_left");
        this.foot_left = this.body.getChild("foot_left");
        this.ear_right = this.head.getChild("ear_right");
        this.arm_right = this.body.getChild("arm_right");
        this.foot_right = this.body.getChild("foot_right");
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
        PartDefinition body = mouse.addOrReplaceChild("body", CubeListBuilder.create().texOffs(1, 1).addBox(-1.25F, 2.52644686F, -2.65160643F, 2.5F, 2.0F, 4.5F).texOffs(17, 1).addBox(-1.75F, 0.549836811F, -2.90160643F, 3.5F, 2.5F, 5.0F).texOffs(36, 1).addBox(-2.0F, -3.30986108F, -2.90160643F, 4.0F, 4.5F, 5.0F).texOffs(56, 1).addBox(-1.75F, -5.16955897F, -2.90160643F, 3.5F, 2.5F, 5.0F).texOffs(75, 1).addBox(-1.25F, -6.64616901F, -2.65160643F, 2.5F, 2.0F, 4.5F).texOffs(91, 1).addBox(-1.75F, 6.96961182F, -2.21452477F, 3.5F, 1.0F, 5.5F).texOffs(1, 13).addBox(-2.5F, 5.90255528F, -2.21452477F, 5.0F, 1.5F, 5.5F).texOffs(24, 13).addBox(-3.0F, 4.04930539F, -2.46452477F, 6.0F, 2.5F, 6.0F).texOffs(50, 13).addBox(-2.5F, 3.19605549F, -2.21452477F, 5.0F, 1.5F, 5.5F).texOffs(73, 13).addBox(-1.75F, 2.62899895F, -2.21452477F, 3.5F, 1.0F, 5.5F), PartPose.offsetAndRotation(-0.0F, -8.83436211F, 1.33868809F, -0.0F, -0.0F, 0.0F));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(93, 13).addBox(-1.5F, 0.892272051F, -2.25F, 3.0F, 1.0F, 4.5F).texOffs(110, 13).addBox(-2.0F, 0.368122573F, -2.25F, 4.0F, 1.0F, 4.5F).texOffs(1, 24).addBox(-2.25F, -0.75F, -2.5F, 4.5F, 1.5F, 5.0F).texOffs(22, 24).addBox(-2.0F, -1.36812257F, -2.25F, 4.0F, 1.0F, 4.5F).texOffs(41, 24).addBox(-1.5F, -1.89227205F, -2.25F, 3.0F, 1.0F, 4.5F).texOffs(58, 24).addBox(-1.0F, 0.756868023F, -3.40963855F, 2.0F, 0.5F, 2.0F).texOffs(68, 24).addBox(-1.25F, 0.541041768F, -3.65963855F, 2.5F, 0.5F, 2.5F).texOffs(80, 24).addBox(-1.5F, -0.0664204685F, -3.65963855F, 3.0F, 1.0F, 2.5F).texOffs(93, 24).addBox(-1.25F, -0.173882704F, -3.65963855F, 2.5F, 0.5F, 2.5F).texOffs(105, 24).addBox(-1.0F, -0.38970896F, -3.40963855F, 2.0F, 0.5F, 2.0F).texOffs(115, 24).addBox(-0.5F, -0.210946979F, -3.89123159F, 1.0F, 1.0F, 0.5F).texOffs(120, 24).addBox(-1.78484848F, -0.837228525F, -2.61947791F, 1.0F, 1.0F, 0.5F).texOffs(1, 33).addBox(0.784848485F, -0.837228525F, -2.61947791F, 1.0F, 1.0F, 0.5F), PartPose.offsetAndRotation(-0.0F, -6.74457049F, -1.74029451F, -0.0F, -0.0F, 0.0F));
        PartDefinition ear_left = head.addOrReplaceChild("ear_left", CubeListBuilder.create().texOffs(6, 33).addBox(-0.5F, -1.62628155F, -0.5F, 1.0F, 2.0F, 1.0F).texOffs(12, 33).addBox(-0.25F, -0.981755035F, -0.785475234F, 0.5F, 1.0F, 0.5F), PartPose.offsetAndRotation(-1.57575758F, -1.15621208F, 0.267737617F, -0.0F, -0.0F, -0.488692191F));
        PartDefinition arm_left = body.addOrReplaceChild("arm_left", CubeListBuilder.create().texOffs(16, 33).addBox(-0.621212121F, 0.0806691332F, -1.41934404F, 1.0F, 3.5F, 1.5F).texOffs(23, 33).addBox(-0.621212121F, 2.77593424F, -1.68708166F, 1.0F, 1.0F, 1.5F), PartPose.offsetAndRotation(-1.57575758F, -2.02337115F, -2.27576975F, -0.0F, -0.0F, 0.0F));
        PartDefinition foot_left = body.addOrReplaceChild("foot_left", CubeListBuilder.create().texOffs(30, 33).addBox(-0.871212121F, 2.37848633F, -2.0F, 1.5F, 1.0F, 4.0F).texOffs(43, 33).addBox(-1.37121212F, 1.8389207F, -2.0F, 2.5F, 1.0F, 4.0F).texOffs(58, 33).addBox(-1.62121212F, 0.695265105F, -2.0F, 3.0F, 1.5F, 4.0F).texOffs(74, 33).addBox(-1.37121212F, 0.0516095151F, -2.0F, 2.5F, 1.0F, 4.0F).texOffs(89, 33).addBox(-0.871212121F, -0.487956124F, -2.0F, 1.5F, 1.0F, 4.0F).texOffs(102, 33).addBox(-1.12121212F, 2.53505672F, -4.27576975F, 2.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(-2.3030303F, 5.29930539F, 0.401606426F, -0.0F, -0.0F, 0.0F));
        PartDefinition ear_right = head.addOrReplaceChild("ear_right", CubeListBuilder.create().texOffs(116, 33).addBox(-0.5F, -1.62628155F, -0.5F, 1.0F, 2.0F, 1.0F).texOffs(122, 33).addBox(-0.25F, -0.981755035F, -0.785475234F, 0.5F, 1.0F, 0.5F), PartPose.offsetAndRotation(1.57575758F, -1.15621208F, 0.267737617F, -0.0F, -0.0F, 0.488692191F));
        PartDefinition arm_right = body.addOrReplaceChild("arm_right", CubeListBuilder.create().texOffs(1, 41).addBox(-0.378787879F, 0.0806691332F, -1.41934404F, 1.0F, 3.5F, 1.5F).texOffs(8, 41).addBox(-0.378787879F, 2.77593424F, -1.68708166F, 1.0F, 1.0F, 1.5F), PartPose.offsetAndRotation(1.57575758F, -2.02337115F, -2.27576975F, -0.0F, -0.0F, 0.0F));
        PartDefinition foot_right = body.addOrReplaceChild("foot_right", CubeListBuilder.create().texOffs(15, 41).addBox(-0.628787879F, 2.37848633F, -2.0F, 1.5F, 1.0F, 4.0F).texOffs(28, 41).addBox(-1.12878788F, 1.8389207F, -2.0F, 2.5F, 1.0F, 4.0F).texOffs(43, 41).addBox(-1.37878788F, 0.695265105F, -2.0F, 3.0F, 1.5F, 4.0F).texOffs(59, 41).addBox(-1.12878788F, 0.0516095151F, -2.0F, 2.5F, 1.0F, 4.0F).texOffs(74, 41).addBox(-0.628787879F, -0.487956124F, -2.0F, 1.5F, 1.0F, 4.0F).texOffs(87, 41).addBox(-0.878787879F, 2.53505672F, -4.27576975F, 2.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(2.3030303F, 5.29930539F, 0.401606426F, -0.0F, -0.0F, 0.0F));
        PartDefinition tail_0 = body.addOrReplaceChild("tail_0", CubeListBuilder.create().texOffs(101, 41).addBox(-1.0F, 0.650881916F, 0.0612449799F, 2.0F, 0.5F, 5.5F).texOffs(1, 49).addBox(-1.5F, 0.0617263709F, 0.0612449799F, 3.0F, 1.0F, 5.5F).texOffs(20, 49).addBox(-1.5F, -0.5F, -0.18875502F, 3.0F, 1.0F, 6.0F).texOffs(40, 49).addBox(-1.5F, -1.06172637F, 0.0612449799F, 3.0F, 1.0F, 5.5F).texOffs(59, 49).addBox(-1.0F, -1.15088192F, 0.0612449799F, 2.0F, 0.5F, 5.5F), PartPose.offsetAndRotation(-0.0F, 6.9372725F, 2.14190094F, -0.0F, -0.0F, 0.0F));
        PartDefinition tail_1 = tail_0.addOrReplaceChild("tail_1", CubeListBuilder.create().texOffs(76, 49).addBox(-1.0F, 0.446136026F, 0.226572959F, 2.0F, 0.5F, 4.5F).texOffs(91, 49).addBox(-1.25F, 0.184061287F, -0.0234270415F, 2.5F, 0.5F, 5.0F).texOffs(108, 49).addBox(-1.5F, -0.5F, -0.0234270415F, 3.0F, 1.0F, 5.0F).texOffs(1, 58).addBox(-1.25F, -0.684061287F, -0.0234270415F, 2.5F, 0.5F, 5.0F).texOffs(18, 58).addBox(-1.0F, -0.946136026F, 0.226572959F, 2.0F, 0.5F, 4.5F), PartPose.offsetAndRotation(-0.0F, 0.722632553F, 4.35073628F, -0.0F, -0.0F, 0.0F));
        PartDefinition tail_2 = tail_1.addOrReplaceChild("tail_2", CubeListBuilder.create().texOffs(33, 58).addBox(-0.75F, 0.282339314F, 0.0572289157F, 1.5F, 0.5F, 3.5F).texOffs(45, 58).addBox(-1.0F, 0.0819292191F, 0.0572289157F, 2.0F, 0.5F, 3.5F).texOffs(58, 58).addBox(-1.25F, -0.5F, -0.192771084F, 2.5F, 1.0F, 4.0F).texOffs(73, 58).addBox(-1.0F, -0.581929219F, 0.0572289157F, 2.0F, 0.5F, 3.5F).texOffs(86, 58).addBox(-0.75F, -0.782339314F, 0.0572289157F, 1.5F, 0.5F, 3.5F), PartPose.offsetAndRotation(-0.0F, 0.385404028F, 4.41767068F, -0.0F, -0.0F, 0.0F));
        PartDefinition gore = mouse.addOrReplaceChild("gore", CubeListBuilder.create().texOffs(98, 58).addBox(-1.57575758F, -0.0385404028F, -1.67336011F, 3.15151515F, 0.0289053021F, 3.34672021F), PartPose.offsetAndRotation(-0.0F, 0.0F, 0.0F, -0.0F, -0.0F, 0.0F));
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
            throw new IllegalArgumentException("Nonfinite scurret presentation");
        root.getAllParts().forEach(ModelPart::resetPose);
        gore.visible = false; // Independent source blood state is OFF by default, even Dead.
        if (dead) clip = "down_hold";
        if (clip != null && !clip.equals("rest")) {
            KeyframeAnimations.animate(this, AnimalScurretAnimations.byName(clip),
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
