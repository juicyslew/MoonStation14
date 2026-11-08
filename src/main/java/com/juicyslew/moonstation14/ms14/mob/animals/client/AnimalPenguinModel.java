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

/** Generated SS14 penguin asset rig. CC-BY-SA3.0 art; retain ATTRIBUTION.md.
 * Native1.21.1 API, based on pinned Blockbench4.12.6 geometry conventions.
 * NOT a registered entity/renderer/AI/networking implementation.
 */
public final class AnimalPenguinModel<T extends LivingEntity> extends HierarchicalModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
        ResourceLocation.fromNamespaceAndPath("moonstation14", "animals/penguin"), "main");
    private final ModelPart root;
    private final ModelPart mouse;
    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart wing_left;
    private final ModelPart foot_left;
    private final ModelPart wing_right;
    private final ModelPart foot_right;
    private final ModelPart gore;
    public AnimalPenguinModel(ModelPart root) {
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
        PartDefinition body = mouse.addOrReplaceChild("body", CubeListBuilder.create().texOffs(1, 1).addBox(-4.48598131F, -4.46153846F, -3.19650067F, 8.97196262F, 8.92307692F, 6.39300135F).texOffs(34, 1).addBox(-3.49906542F, -7.69230769F, -2.62113055F, 6.99813084F, 4.0F, 5.2422611F).texOffs(61, 1).addBox(-3.49906542F, 3.69230769F, -2.62113055F, 6.99813084F, 4.0F, 5.2422611F).texOffs(88, 1).addBox(-3.58878505F, -3.88153846F, -3.26043069F, 7.17757009F, 7.76307692F, 2.42934051F).texOffs(110, 1).addBox(-2.79925234F, -6.69230769F, -3.04179004F, 5.59850467F, 3.48F, 1.99205922F).texOffs(1, 19).addBox(-2.79925234F, 3.21230769F, -3.04179004F, 5.59850467F, 3.48F, 1.99205922F), PartPose.offsetAndRotation(-0.0F, -9.23076923F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(19, 19).addBox(-1.7046729F, 0.0F, -1.69582773F, 3.40934579F, 5.76923077F, 3.39165545F).texOffs(35, 19).addBox(-3.14018692F, -2.00769231F, -3.0282638F, 6.28037383F, 4.01538462F, 6.05652759F).texOffs(62, 19).addBox(-2.44934579F, -3.46153846F, -2.48317631F, 4.89869159F, 1.8F, 4.96635262F).texOffs(84, 19).addBox(-2.44934579F, 1.66153846F, -2.48317631F, 4.89869159F, 1.8F, 4.96635262F).texOffs(106, 19).addBox(-1.34579439F, -0.144230769F, -4.37415882F, 2.69158879F, 1.05769231F, 1.34589502F).texOffs(117, 19).addBox(-2.5271028F, -0.730769231F, -3.28061911F, 0.747663551F, 0.769230769F, 0.336473755F).texOffs(122, 19).addBox(1.77943925F, -0.730769231F, -3.28061911F, 0.747663551F, 0.769230769F, 0.336473755F), PartPose.offsetAndRotation(-0.0F, -7.30769231F, -2.42934051F, -0.0F, -0.0F, 0.0F));
        PartDefinition wing_left = body.addOrReplaceChild("wing_left", CubeListBuilder.create().texOffs(1, 32).addBox(-1.05233645F, 1.40769231F, -1.77658143F, 1.02803738F, 5.8F, 4.730821F).texOffs(15, 32).addBox(-0.939252336F, -0.692307692F, -1.35080754F, 0.801869159F, 2.6F, 3.87927322F).texOffs(27, 32).addBox(-0.939252336F, 6.70769231F, -1.35080754F, 0.801869159F, 2.6F, 3.87927322F), PartPose.offsetAndRotation(-3.94766355F, -3.53846154F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition foot_left = body.addOrReplaceChild("foot_left", CubeListBuilder.create().texOffs(39, 32).addBox(-0.420560748F, -0.0769230769F, -0.462651413F, 0.841121495F, 1.53846154F, 0.925302826F).texOffs(45, 32).addBox(-1.48037383F, 0.846153846F, -2.10296097F, 2.96074766F, 0.692307692F, 2.52355316F), PartPose.offsetAndRotation(-2.15327103F, 7.69230769F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition wing_right = body.addOrReplaceChild("wing_right", CubeListBuilder.create().texOffs(58, 32).addBox(0.0242990654F, 1.40769231F, -1.77658143F, 1.02803738F, 5.8F, 4.730821F).texOffs(72, 32).addBox(0.137383178F, -0.692307692F, -1.35080754F, 0.801869159F, 2.6F, 3.87927322F).texOffs(84, 32).addBox(0.137383178F, 6.70769231F, -1.35080754F, 0.801869159F, 2.6F, 3.87927322F), PartPose.offsetAndRotation(3.94766355F, -3.53846154F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition foot_right = body.addOrReplaceChild("foot_right", CubeListBuilder.create().texOffs(96, 32).addBox(-0.420560748F, -0.0769230769F, -0.462651413F, 0.841121495F, 1.53846154F, 0.925302826F).texOffs(102, 32).addBox(-1.48037383F, 0.846153846F, -2.10296097F, 2.96074766F, 0.692307692F, 2.52355316F), PartPose.offsetAndRotation(2.15327103F, 7.69230769F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition gore = mouse.addOrReplaceChild("gore", CubeListBuilder.create().texOffs(1, 45).addBox(-2.42990654F, -0.0769230769F, -2.10296097F, 4.85981308F, 0.0576923077F, 4.20592194F), PartPose.offsetAndRotation(-0.0F, 0.0F, 0.0F, -0.0F, -0.0F, 0.0F));
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
            throw new IllegalArgumentException("Nonfinite penguin presentation");
        root.getAllParts().forEach(ModelPart::resetPose);
        gore.visible = false; // Independent source blood state is OFF by default, even Dead.
        if (dead) clip = "down_hold";
        if (clip != null && !clip.equals("rest")) {
            KeyframeAnimations.animate(this, AnimalPenguinAnimations.byName(clip),
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
        gore.visible = enabled && true;
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
