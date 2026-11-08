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

/** Generated SS14 snake asset rig. CC-BY-SA3.0 art; retain ATTRIBUTION.md.
 * Native1.21.1 API, based on pinned Blockbench4.12.6 geometry conventions.
 * NOT a registered entity/renderer/AI/networking implementation.
 */
public final class AnimalSnakeModel<T extends LivingEntity> extends HierarchicalModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
        ResourceLocation.fromNamespaceAndPath("moonstation14", "animals/snake"), "main");
    private final ModelPart root;
    private final ModelPart mouse;
    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart serpent_0;
    private final ModelPart serpent_1;
    private final ModelPart serpent_2;
    private final ModelPart serpent_3;
    private final ModelPart serpent_4;
    private final ModelPart serpent_5;
    private final ModelPart serpent_6;
    private final ModelPart serpent_7;
    private final ModelPart serpent_8;
    private final ModelPart serpent_9;
    private final ModelPart gore;
    public AnimalSnakeModel(ModelPart root) {
        this.root = root;
        this.mouse = root.getChild("mouse");
        this.body = this.mouse.getChild("body");
        this.head = this.body.getChild("head");
        this.serpent_0 = this.body.getChild("serpent_0");
        this.serpent_1 = this.body.getChild("serpent_1");
        this.serpent_2 = this.body.getChild("serpent_2");
        this.serpent_3 = this.body.getChild("serpent_3");
        this.serpent_4 = this.body.getChild("serpent_4");
        this.serpent_5 = this.body.getChild("serpent_5");
        this.serpent_6 = this.body.getChild("serpent_6");
        this.serpent_7 = this.body.getChild("serpent_7");
        this.serpent_8 = this.body.getChild("serpent_8");
        this.serpent_9 = this.body.getChild("serpent_9");
        this.gore = this.mouse.getChild("gore");
        this.gore.visible = false;
    }
    @Override public ModelPart root() { return root; }
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition base = mesh.getRoot();
        PartDefinition mouse = base.addOrReplaceChild("mouse", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 24.0F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition body = mouse.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(-0.0F, -1.48F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(1, 1).addBox(-1.0F, -7.11F, -1.0F, 2.0F, 7.5F, 2.0F).texOffs(11, 1).addBox(-1.0F, -6.646F, -1.51144704F, 2.0F, 1.0F, 2.5F).texOffs(22, 1).addBox(-1.5F, -7.0428F, -1.76144704F, 3.0F, 1.0F, 3.0F).texOffs(36, 1).addBox(-1.75F, -7.95F, -1.76144704F, 3.5F, 1.5F, 3.0F).texOffs(51, 1).addBox(-1.5F, -8.3572F, -1.76144704F, 3.0F, 1.0F, 3.0F).texOffs(1, 13).addBox(-1.0F, -8.754F, -1.51144704F, 2.0F, 1.0F, 2.5F).texOffs(12, 13).addBox(-1.44297316F, -8.18F, -1.81868223F, 1.0F, 1.0F, 0.5F).texOffs(17, 13).addBox(0.442973162F, -8.18F, -1.81868223F, 1.0F, 1.0F, 0.5F).texOffs(22, 13).addBox(-0.25F, -6.97F, -3.03766158F, 0.5F, 0.5F, 1.5F), PartPose.offsetAndRotation(-0.0F, 0.0F, -3.92170556F, -0.0F, -0.0F, 0.0F));
        PartDefinition serpent_0 = body.addOrReplaceChild("serpent_0", CubeListBuilder.create().texOffs(28, 13).addBox(-0.75F, -1.0F, -1.5F, 1.5F, 2.0F, 3.0F), PartPose.offsetAndRotation(1.01550956F, 0.48F, -2.94127917F, -0.0F, 0.750929062F, 0.0F));
        PartDefinition serpent_1 = body.addOrReplaceChild("serpent_1", CubeListBuilder.create().texOffs(39, 13).addBox(-0.75F, -1.0F, -1.5F, 1.5F, 2.0F, 3.0F), PartPose.offsetAndRotation(2.90145588F, 0.48F, -1.30723519F, -0.0F, 0.805003494F, 0.0F));
        PartDefinition serpent_2 = body.addOrReplaceChild("serpent_2", CubeListBuilder.create().texOffs(50, 13).addBox(-0.75F, -1.0F, -1.5F, 1.5F, 2.0F, 3.0F), PartPose.offsetAndRotation(3.19160147F, 0.48F, 1.17651167F, -0.0F, 0.104876939F, 0.0F));
        PartDefinition serpent_3 = body.addOrReplaceChild("serpent_3", CubeListBuilder.create().texOffs(1, 20).addBox(-0.75F, -1.0F, -1.5F, 1.5F, 2.0F, 3.0F), PartPose.offsetAndRotation(1.01550956F, 0.48F, 2.87591741F, -0.0F, -0.856705628F, 0.0F));
        PartDefinition serpent_4 = body.addOrReplaceChild("serpent_4", CubeListBuilder.create().texOffs(12, 20).addBox(-0.75F, -1.0F, -1.5F, 1.5F, 2.0F, 3.0F), PartPose.offsetAndRotation(-1.74087353F, 0.48F, 2.74519389F, -0.0F, -1.62337939F, 0.0F));
        PartDefinition serpent_5 = body.addOrReplaceChild("serpent_5", CubeListBuilder.create().texOffs(23, 20).addBox(-0.75F, -1.0F, -1.5F, 1.5F, 2.0F, 3.0F), PartPose.offsetAndRotation(-3.33667426F, 0.48F, 0.653617594F, -0.0F, -2.53930531F, 0.0F));
        PartDefinition serpent_6 = body.addOrReplaceChild("serpent_6", CubeListBuilder.create().texOffs(34, 20).addBox(-0.75F, -1.0F, -1.5F, 1.5F, 2.0F, 3.0F), PartPose.offsetAndRotation(-2.90145588F, 0.48F, -1.43795871F, -0.0F, 2.9562447F, 0.0F));
        PartDefinition serpent_7 = body.addOrReplaceChild("serpent_7", CubeListBuilder.create().texOffs(45, 20).addBox(-0.75F, -1.0F, -1.5F, 1.5F, 2.0F, 3.0F), PartPose.offsetAndRotation(-1.16058235F, 0.48F, -2.35302334F, -0.0F, 2.09887078F, 0.0F));
        PartDefinition serpent_8 = body.addOrReplaceChild("serpent_8", CubeListBuilder.create().texOffs(1, 27).addBox(-0.5F, -1.0F, -1.5F, 1.0F, 2.0F, 3.0F), PartPose.offsetAndRotation(-0.0F, 0.48F, -0.653617594F, -0.0F, 0.551654983F, 0.0F));
        PartDefinition serpent_9 = body.addOrReplaceChild("serpent_9", CubeListBuilder.create().texOffs(11, 27).addBox(-0.5F, -1.0F, -1.5F, 1.0F, 2.0F, 3.0F), PartPose.offsetAndRotation(-0.72536397F, 0.48F, 0.653617594F, -0.0F, -0.463647609F, 0.0F));
        PartDefinition gore = mouse.addOrReplaceChild("gore", CubeListBuilder.create().texOffs(21, 27).addBox(-1.88594632F, -0.064F, -1.63404398F, 3.77189265F, 0.048F, 3.26808797F), PartPose.offsetAndRotation(-0.0F, 0.0F, 0.0F, -0.0F, -0.0F, 0.0F));
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
            throw new IllegalArgumentException("Nonfinite snake presentation");
        root.getAllParts().forEach(ModelPart::resetPose);
        gore.visible = false; // Independent source blood state is OFF by default, even Dead.
        if (dead) clip = "down_hold";
        if (clip != null && !clip.equals("rest")) {
            KeyframeAnimations.animate(this, AnimalSnakeAnimations.byName(clip),
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
