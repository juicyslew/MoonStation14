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

/** Generated SS14 parrot asset rig. CC-BY-SA3.0 art; retain ATTRIBUTION.md.
 * Native1.21.1 API, based on pinned Blockbench4.12.6 geometry conventions.
 * NOT a registered entity/renderer/AI/networking implementation.
 */
public final class AnimalParrotModel<T extends LivingEntity> extends HierarchicalModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
        ResourceLocation.fromNamespaceAndPath("moonstation14", "animals/parrot"), "main");
    private final ModelPart root;
    private final ModelPart mouse;
    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart tail_0;
    private final ModelPart tail_1;
    private final ModelPart tail_2;
    private final ModelPart wing_left;
    private final ModelPart wingtip_left;
    private final ModelPart foot_left;
    private final ModelPart wing_right;
    private final ModelPart wingtip_right;
    private final ModelPart foot_right;
    private final ModelPart gore;
    public AnimalParrotModel(ModelPart root) {
        this.root = root;
        this.mouse = root.getChild("mouse");
        this.body = this.mouse.getChild("body");
        this.head = this.body.getChild("head");
        this.tail_0 = this.body.getChild("tail_0");
        this.tail_1 = this.tail_0.getChild("tail_1");
        this.tail_2 = this.tail_1.getChild("tail_2");
        this.wing_left = this.body.getChild("wing_left");
        this.wingtip_left = this.wing_left.getChild("wingtip_left");
        this.foot_left = this.body.getChild("foot_left");
        this.wing_right = this.body.getChild("wing_right");
        this.wingtip_right = this.wing_right.getChild("wingtip_right");
        this.foot_right = this.body.getChild("foot_right");
        this.gore = this.mouse.getChild("gore");
        this.gore.visible = false;
    }
    @Override public ModelPart root() { return root; }
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition base = mesh.getRoot();
        PartDefinition mouse = base.addOrReplaceChild("mouse", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 24.0F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition body = mouse.addOrReplaceChild("body", CubeListBuilder.create().texOffs(1, 1).addBox(-2.08785249F, -3.02811245F, -2.42003631F, 4.17570499F, 6.0562249F, 4.84007263F).texOffs(22, 1).addBox(-1.62852495F, -5.22088353F, -1.98442978F, 3.25704989F, 2.71485944F, 3.96885955F).texOffs(39, 1).addBox(-1.62852495F, 2.5060241F, -1.98442978F, 3.25704989F, 2.71485944F, 3.96885955F), PartPose.offsetAndRotation(-0.0F, -6.52610442F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(1, 14).addBox(-1.75F, -2.40753012F, -2.30395279F, 3.5F, 3.0F, 4.0F).texOffs(18, 14).addBox(-1.25F, -2.83253012F, -1.80395279F, 2.5F, 1.0F, 3.0F).texOffs(31, 14).addBox(-1.5F, -0.0825301205F, -1.80395279F, 3.0F, 1.0F, 3.0F).texOffs(45, 14).addBox(-1.35F, -1.38253012F, -2.57895279F, 0.5F, 0.5F, 0.5F).texOffs(49, 14).addBox(0.85F, -1.38253012F, -2.57895279F, 0.5F, 0.5F, 0.5F).texOffs(53, 14).addBox(-0.75F, -1.13253012F, -3.30395279F, 1.5F, 1.5F, 1.5F).texOffs(1, 23).addBox(-0.5F, -0.0325301205F, -3.55395279F, 1.0F, 1.0F, 1.0F), PartPose.offsetAndRotation(-0.0F, -3.34136546F, -3.14604721F, -0.0F, -0.0F, 0.0F));
        PartDefinition tail_0 = body.addOrReplaceChild("tail_0", CubeListBuilder.create().texOffs(7, 23).addBox(-0.596529284F, -0.870147256F, -0.0726010894F, 1.19305857F, 1.74029451F, 2.56523849F), PartPose.offsetAndRotation(-0.0F, 1.74029451F, 2.42003631F, -0.122173048F, -0.20943951F, 0.0F));
        PartDefinition tail_1 = tail_0.addOrReplaceChild("tail_1", CubeListBuilder.create().texOffs(17, 23).addBox(-0.477223427F, -0.696117805F, -0.0726010894F, 0.954446855F, 1.39223561F, 2.56523849F), PartPose.offsetAndRotation(-0.0F, 0.0F, 2.42003631F, -0.122173048F, 0.20943951F, 0.0F));
        PartDefinition tail_2 = tail_1.addOrReplaceChild("tail_2", CubeListBuilder.create().texOffs(27, 23).addBox(-0.35791757F, -0.522088353F, -0.0726010894F, 0.715835141F, 1.04417671F, 2.56523849F), PartPose.offsetAndRotation(-0.0F, 0.0F, 2.42003631F, -0.122173048F, -0.20943951F, 0.0F));
        PartDefinition wing_left = body.addOrReplaceChild("wing_left", CubeListBuilder.create().texOffs(36, 23).addBox(-4.5813449F, -0.304551539F, -1.1797677F, 4.5813449F, 0.435073628F, 0.423506355F).texOffs(1, 30).addBox(-4.5813449F, -0.113119143F, -1.87552814F, 4.5813449F, 0.226238286F, 4.59806899F), PartPose.offsetAndRotation(-1.8373102F, -0.870147256F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition wingtip_left = wing_left.addOrReplaceChild("wingtip_left", CubeListBuilder.create().texOffs(22, 30).addBox(-4.5813449F, -0.104417671F, -1.23421852F, 4.5813449F, 0.208835341F, 1.13741707F).texOffs(36, 30).addBox(-3.34056399F, -0.104417671F, -0.145202179F, 3.34056399F, 0.208835341F, 1.13741707F).texOffs(47, 30).addBox(-2.09978308F, -0.104417671F, 0.943814162F, 2.09978308F, 0.208835341F, 1.13741707F), PartPose.offsetAndRotation(-4.5813449F, 0.0F, -0.121001816F, -0.0F, -0.0F, 0.0F));
        PartDefinition foot_left = body.addOrReplaceChild("foot_left", CubeListBuilder.create().texOffs(56, 30).addBox(-0.238611714F, 0.156626506F, -1.02851543F, 0.477223427F, 1.56626506F, 1.33101997F), PartPose.offsetAndRotation(-0.960412148F, 4.80321285F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition wing_right = body.addOrReplaceChild("wing_right", CubeListBuilder.create().texOffs(1, 37).addBox(0.0F, -0.304551539F, -1.1797677F, 4.5813449F, 0.435073628F, 0.423506355F).texOffs(14, 37).addBox(0.0F, -0.113119143F, -1.87552814F, 4.5813449F, 0.226238286F, 4.59806899F), PartPose.offsetAndRotation(1.8373102F, -0.870147256F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition wingtip_right = wing_right.addOrReplaceChild("wingtip_right", CubeListBuilder.create().texOffs(35, 37).addBox(0.0F, -0.104417671F, -1.23421852F, 4.5813449F, 0.208835341F, 1.13741707F).texOffs(49, 37).addBox(-8.8817842e-16F, -0.104417671F, -0.145202179F, 3.34056399F, 0.208835341F, 1.13741707F).texOffs(1, 44).addBox(0.0F, -0.104417671F, 0.943814162F, 2.09978308F, 0.208835341F, 1.13741707F), PartPose.offsetAndRotation(4.5813449F, 0.0F, -0.121001816F, -0.0F, -0.0F, 0.0F));
        PartDefinition foot_right = body.addOrReplaceChild("foot_right", CubeListBuilder.create().texOffs(10, 44).addBox(-0.238611714F, 0.156626506F, -1.02851543F, 0.477223427F, 1.56626506F, 1.33101997F), PartPose.offsetAndRotation(0.960412148F, 4.80321285F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition gore = mouse.addOrReplaceChild("gore", CubeListBuilder.create().texOffs(16, 44).addBox(-1.55097614F, -0.0696117805F, -1.5125227F, 3.10195228F, 0.0522088353F, 3.02504539F), PartPose.offsetAndRotation(-0.0F, 0.0F, 0.0F, -0.0F, -0.0F, 0.0F));
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
            throw new IllegalArgumentException("Nonfinite parrot presentation");
        root.getAllParts().forEach(ModelPart::resetPose);
        gore.visible = false; // Independent source blood state is OFF by default, even Dead.
        if (dead) clip = "down_hold";
        if (clip != null && !clip.equals("rest")) {
            KeyframeAnimations.animate(this, AnimalParrotAnimations.byName(clip),
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
