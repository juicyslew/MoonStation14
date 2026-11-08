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

/** Generated SS14 nymph asset rig. CC-BY-SA3.0 art; retain ATTRIBUTION.md.
 * Native1.21.1 API, based on pinned Blockbench4.12.6 geometry conventions.
 * NOT a registered entity/renderer/AI/networking implementation.
 */
public final class AnimalNymphModel<T extends LivingEntity> extends HierarchicalModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
        ResourceLocation.fromNamespaceAndPath("moonstation14", "animals/nymph"), "main");
    private final ModelPart root;
    private final ModelPart mouse;
    private final ModelPart body;
    private final ModelPart trunk_0;
    private final ModelPart trunk_1;
    private final ModelPart trunk_2;
    private final ModelPart trunk_3;
    private final ModelPart trunk_4;
    private final ModelPart trunk_5;
    private final ModelPart head;
    private final ModelPart sprout_left;
    private final ModelPart sprout_right;
    private final ModelPart leg_0_left;
    private final ModelPart leg_0_right;
    private final ModelPart leg_1_left;
    private final ModelPart leg_1_right;
    private final ModelPart leg_2_left;
    private final ModelPart leg_2_right;
    private final ModelPart leg_3_left;
    private final ModelPart leg_3_right;
    private final ModelPart leg_4_left;
    private final ModelPart leg_4_right;
    private final ModelPart leg_5_left;
    private final ModelPart leg_5_right;
    private final ModelPart leg_6_left;
    private final ModelPart leg_6_right;
    private final ModelPart leg_7_left;
    private final ModelPart leg_7_right;
    private final ModelPart spiral_tail_0_joint;
    private final ModelPart spiral_tail_1_joint;
    private final ModelPart spiral_tail_2_joint;
    private final ModelPart spiral_tail_3_joint;
    private final ModelPart spiral_tail_4_joint;
    private final ModelPart spiral_tail_5_joint;
    private final ModelPart spiral_tail_6_joint;
    private final ModelPart spiral_tail_7_joint;
    private final ModelPart gore;
    private final ModelPart flowing_antenna_left0_joint;
    private final ModelPart flowing_antenna_left1_joint;
    private final ModelPart flowing_antenna_left2_joint;
    private final ModelPart flowing_antenna_left3_joint;
    private final ModelPart flowing_antenna_right0_joint;
    private final ModelPart flowing_antenna_right1_joint;
    private final ModelPart flowing_antenna_right2_joint;
    private final ModelPart flowing_antenna_right3_joint;
    public AnimalNymphModel(ModelPart root) {
        this.root = root;
        this.mouse = root.getChild("mouse");
        this.body = this.mouse.getChild("body");
        this.trunk_0 = this.body.getChild("trunk_0");
        this.trunk_1 = this.body.getChild("trunk_1");
        this.trunk_2 = this.body.getChild("trunk_2");
        this.trunk_3 = this.body.getChild("trunk_3");
        this.trunk_4 = this.body.getChild("trunk_4");
        this.trunk_5 = this.body.getChild("trunk_5");
        this.head = this.body.getChild("head");
        this.sprout_left = this.head.getChild("sprout_left");
        this.sprout_right = this.head.getChild("sprout_right");
        this.leg_0_left = this.body.getChild("leg_0_left");
        this.leg_0_right = this.body.getChild("leg_0_right");
        this.leg_1_left = this.body.getChild("leg_1_left");
        this.leg_1_right = this.body.getChild("leg_1_right");
        this.leg_2_left = this.body.getChild("leg_2_left");
        this.leg_2_right = this.body.getChild("leg_2_right");
        this.leg_3_left = this.body.getChild("leg_3_left");
        this.leg_3_right = this.body.getChild("leg_3_right");
        this.leg_4_left = this.body.getChild("leg_4_left");
        this.leg_4_right = this.body.getChild("leg_4_right");
        this.leg_5_left = this.body.getChild("leg_5_left");
        this.leg_5_right = this.body.getChild("leg_5_right");
        this.leg_6_left = this.body.getChild("leg_6_left");
        this.leg_6_right = this.body.getChild("leg_6_right");
        this.leg_7_left = this.body.getChild("leg_7_left");
        this.leg_7_right = this.body.getChild("leg_7_right");
        this.spiral_tail_0_joint = this.body.getChild("spiral_tail_0_joint");
        this.spiral_tail_1_joint = this.body.getChild("spiral_tail_1_joint");
        this.spiral_tail_2_joint = this.body.getChild("spiral_tail_2_joint");
        this.spiral_tail_3_joint = this.body.getChild("spiral_tail_3_joint");
        this.spiral_tail_4_joint = this.body.getChild("spiral_tail_4_joint");
        this.spiral_tail_5_joint = this.body.getChild("spiral_tail_5_joint");
        this.spiral_tail_6_joint = this.body.getChild("spiral_tail_6_joint");
        this.spiral_tail_7_joint = this.body.getChild("spiral_tail_7_joint");
        this.gore = this.mouse.getChild("gore");
        this.flowing_antenna_left0_joint = this.head.getChild("flowing_antenna_left0_joint");
        this.flowing_antenna_left1_joint = this.head.getChild("flowing_antenna_left1_joint");
        this.flowing_antenna_left2_joint = this.head.getChild("flowing_antenna_left2_joint");
        this.flowing_antenna_left3_joint = this.head.getChild("flowing_antenna_left3_joint");
        this.flowing_antenna_right0_joint = this.head.getChild("flowing_antenna_right0_joint");
        this.flowing_antenna_right1_joint = this.head.getChild("flowing_antenna_right1_joint");
        this.flowing_antenna_right2_joint = this.head.getChild("flowing_antenna_right2_joint");
        this.flowing_antenna_right3_joint = this.head.getChild("flowing_antenna_right3_joint");
        this.gore.visible = false;
    }
    @Override public ModelPart root() { return root; }
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition base = mesh.getRoot();
        PartDefinition mouse = base.addOrReplaceChild("mouse", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 24.0F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition body = mouse.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(-0.0F, -4.33408718F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition trunk_0 = body.addOrReplaceChild("trunk_0", CubeListBuilder.create().texOffs(1, 1).addBox(-1.75F, 0.985000929F, -1.5F, 3.5F, 1.0F, 3.0F).texOffs(16, 1).addBox(-2.5F, 0.425941756F, -1.5F, 5.0F, 1.0F, 3.0F).texOffs(34, 1).addBox(-3.0F, -0.75F, -1.5F, 6.0F, 1.5F, 3.0F).texOffs(54, 1).addBox(-2.5F, -1.42594176F, -1.5F, 5.0F, 1.0F, 3.0F).texOffs(72, 1).addBox(-1.75F, -1.98500093F, -1.5F, 3.5F, 1.0F, 3.0F), PartPose.offsetAndRotation(-0.0F, 1.03529477F, 4.99594155F, -0.0F, -0.0F, 0.0F));
        PartDefinition trunk_1 = body.addOrReplaceChild("trunk_1", CubeListBuilder.create().texOffs(87, 1).addBox(-1.75F, 0.985000929F, -1.5F, 3.5F, 1.0F, 3.0F).texOffs(102, 1).addBox(-2.5F, 0.425941756F, -1.5F, 5.0F, 1.0F, 3.0F).texOffs(1, 8).addBox(-2.75F, -0.75F, -1.5F, 5.5F, 1.5F, 3.0F).texOffs(20, 8).addBox(-2.5F, -1.42594176F, -1.5F, 5.0F, 1.0F, 3.0F).texOffs(38, 8).addBox(-1.75F, -1.98500093F, -1.5F, 3.5F, 1.0F, 3.0F), PartPose.offsetAndRotation(-0.0F, 0.543529752F, 3.08959543F, -0.0F, -0.0F, 0.0F));
        PartDefinition trunk_2 = body.addOrReplaceChild("trunk_2", CubeListBuilder.create().texOffs(53, 8).addBox(-1.75F, 0.985000929F, -1.5F, 3.5F, 1.0F, 3.0F).texOffs(68, 8).addBox(-2.25F, 0.425941756F, -1.5F, 4.5F, 1.0F, 3.0F).texOffs(85, 8).addBox(-2.75F, -0.75F, -1.5F, 5.5F, 1.5F, 3.0F).texOffs(104, 8).addBox(-2.25F, -1.42594176F, -1.5F, 4.5F, 1.0F, 3.0F).texOffs(1, 15).addBox(-1.75F, -1.98500093F, -1.5F, 3.5F, 1.0F, 3.0F), PartPose.offsetAndRotation(-0.0F, 0.0517647383F, 1.18324931F, -0.0F, -0.0F, 0.0F));
        PartDefinition trunk_3 = body.addOrReplaceChild("trunk_3", CubeListBuilder.create().texOffs(16, 15).addBox(-1.5F, 0.985000929F, -1.5F, 3.0F, 1.0F, 3.0F).texOffs(30, 15).addBox(-2.25F, 0.425941756F, -1.5F, 4.5F, 1.0F, 3.0F).texOffs(47, 15).addBox(-2.5F, -0.75F, -1.5F, 5.0F, 1.5F, 3.0F).texOffs(65, 15).addBox(-2.25F, -1.42594176F, -1.5F, 4.5F, 1.0F, 3.0F).texOffs(82, 15).addBox(-1.5F, -1.98500093F, -1.5F, 3.0F, 1.0F, 3.0F), PartPose.offsetAndRotation(-0.0F, -0.440000275F, -0.723096803F, -0.0F, -0.0F, 0.0F));
        PartDefinition trunk_4 = body.addOrReplaceChild("trunk_4", CubeListBuilder.create().texOffs(96, 15).addBox(-1.5F, 0.985000929F, -1.5F, 3.0F, 1.0F, 3.0F).texOffs(110, 15).addBox(-2.0F, 0.425941756F, -1.5F, 4.0F, 1.0F, 3.0F).texOffs(1, 22).addBox(-2.25F, -0.75F, -1.5F, 4.5F, 1.5F, 3.0F).texOffs(18, 22).addBox(-2.0F, -1.42594176F, -1.5F, 4.0F, 1.0F, 3.0F).texOffs(34, 22).addBox(-1.5F, -1.98500093F, -1.5F, 3.0F, 1.0F, 3.0F), PartPose.offsetAndRotation(-0.0F, -0.931765289F, -2.62944292F, -0.0F, -0.0F, 0.0F));
        PartDefinition trunk_5 = body.addOrReplaceChild("trunk_5", CubeListBuilder.create().texOffs(48, 22).addBox(-1.5F, 0.985000929F, -1.5F, 3.0F, 1.0F, 3.0F).texOffs(62, 22).addBox(-2.0F, 0.425941756F, -1.5F, 4.0F, 1.0F, 3.0F).texOffs(78, 22).addBox(-2.25F, -0.75F, -1.5F, 4.5F, 1.5F, 3.0F).texOffs(95, 22).addBox(-2.0F, -1.42594176F, -1.5F, 4.0F, 1.0F, 3.0F).texOffs(111, 22).addBox(-1.5F, -1.98500093F, -1.5F, 3.0F, 1.0F, 3.0F), PartPose.offsetAndRotation(-0.0F, -1.4235303F, -4.53578904F, -0.0F, -0.0F, 0.0F));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(1, 29).addBox(-1.75F, 0.930000895F, -1.5F, 3.5F, 1.0F, 3.0F).texOffs(16, 29).addBox(-2.5F, 0.391647617F, -1.75F, 5.0F, 1.0F, 3.5F).texOffs(35, 29).addBox(-2.75F, -0.75F, -1.75F, 5.5F, 1.5F, 3.5F).texOffs(55, 29).addBox(-2.5F, -1.39164762F, -1.75F, 5.0F, 1.0F, 3.5F).texOffs(74, 29).addBox(-1.75F, -1.93000089F, -1.5F, 3.5F, 1.0F, 3.0F).texOffs(89, 29).addBox(-1.75F, -1.45294146F, -2.21431561F, 3.5F, 2.0F, 1.5F), PartPose.offsetAndRotation(-0.0F, -2.32941322F, -6.04771872F, -0.0F, -0.0F, 0.0F));
        PartDefinition sprout_left = head.addOrReplaceChild("sprout_left", CubeListBuilder.create(), PartPose.offsetAndRotation(-1.54950672F, -1.16470661F, 0.65736073F, -0.0F, -0.0F, -0.314159265F));
        PartDefinition sprout_right = head.addOrReplaceChild("sprout_right", CubeListBuilder.create(), PartPose.offsetAndRotation(1.54950672F, -1.16470661F, 0.65736073F, -0.0F, -0.0F, 0.314159265F));
        PartDefinition leg_0_left = body.addOrReplaceChild("leg_0_left", CubeListBuilder.create().texOffs(101, 29).addBox(-0.95573727F, -0.14117562F, -0.5F, 1.0F, 3.0F, 1.0F), PartPose.offsetAndRotation(-1.70901476F, 1.16470661F, -5.52183013F, -0.0F, -0.0F, -0.314159265F));
        PartDefinition leg_0_right = body.addOrReplaceChild("leg_0_right", CubeListBuilder.create().texOffs(107, 29).addBox(-0.0442627304F, -0.14117562F, -0.5F, 1.0F, 3.0F, 1.0F), PartPose.offsetAndRotation(1.70901476F, 1.16470661F, -5.52183013F, -0.0F, -0.0F, 0.314159265F));
        PartDefinition leg_1_left = body.addOrReplaceChild("leg_1_left", CubeListBuilder.create().texOffs(113, 29).addBox(-0.95573727F, -0.215125246F, -0.5F, 1.0F, 3.0F, 1.0F), PartPose.offsetAndRotation(-1.70901476F, 1.31260586F, -3.94416438F, -0.0F, -0.0F, -0.314159265F));
        PartDefinition leg_1_right = body.addOrReplaceChild("leg_1_right", CubeListBuilder.create().texOffs(119, 29).addBox(-0.0442627304F, -0.215125246F, -0.5F, 1.0F, 3.0F, 1.0F), PartPose.offsetAndRotation(1.70901476F, 1.31260586F, -3.94416438F, -0.0F, -0.0F, 0.314159265F));
        PartDefinition leg_2_left = body.addOrReplaceChild("leg_2_left", CubeListBuilder.create().texOffs(1, 36).addBox(-0.95573727F, -0.289074872F, -0.5F, 1.0F, 3.0F, 1.0F), PartPose.offsetAndRotation(-1.70901476F, 1.46050512F, -2.36649863F, -0.0F, -0.0F, -0.314159265F));
        PartDefinition leg_2_right = body.addOrReplaceChild("leg_2_right", CubeListBuilder.create().texOffs(7, 36).addBox(-0.0442627304F, -0.289074872F, -0.5F, 1.0F, 3.0F, 1.0F), PartPose.offsetAndRotation(1.70901476F, 1.46050512F, -2.36649863F, -0.0F, -0.0F, 0.314159265F));
        PartDefinition leg_3_left = body.addOrReplaceChild("leg_3_left", CubeListBuilder.create().texOffs(13, 36).addBox(-0.95573727F, -0.113024499F, -0.5F, 1.0F, 2.5F, 1.0F), PartPose.offsetAndRotation(-1.70901476F, 1.60840437F, -0.788832876F, -0.0F, -0.0F, -0.314159265F));
        PartDefinition leg_3_right = body.addOrReplaceChild("leg_3_right", CubeListBuilder.create().texOffs(19, 36).addBox(-0.0442627304F, -0.113024499F, -0.5F, 1.0F, 2.5F, 1.0F), PartPose.offsetAndRotation(1.70901476F, 1.60840437F, -0.788832876F, -0.0F, -0.0F, 0.314159265F));
        PartDefinition leg_4_left = body.addOrReplaceChild("leg_4_left", CubeListBuilder.create().texOffs(25, 36).addBox(-0.95573727F, -0.186974125F, -0.5F, 1.0F, 2.5F, 1.0F), PartPose.offsetAndRotation(-1.70901476F, 1.75630362F, 0.788832876F, -0.0F, -0.0F, -0.314159265F));
        PartDefinition leg_4_right = body.addOrReplaceChild("leg_4_right", CubeListBuilder.create().texOffs(31, 36).addBox(-0.0442627304F, -0.186974125F, -0.5F, 1.0F, 2.5F, 1.0F), PartPose.offsetAndRotation(1.70901476F, 1.75630362F, 0.788832876F, -0.0F, -0.0F, 0.314159265F));
        PartDefinition leg_5_left = body.addOrReplaceChild("leg_5_left", CubeListBuilder.create().texOffs(37, 36).addBox(-0.95573727F, -0.260923751F, -0.5F, 1.0F, 2.5F, 1.0F), PartPose.offsetAndRotation(-1.70901476F, 1.90420287F, 2.36649863F, -0.0F, -0.0F, -0.314159265F));
        PartDefinition leg_5_right = body.addOrReplaceChild("leg_5_right", CubeListBuilder.create().texOffs(43, 36).addBox(-0.0442627304F, -0.260923751F, -0.5F, 1.0F, 2.5F, 1.0F), PartPose.offsetAndRotation(1.70901476F, 1.90420287F, 2.36649863F, -0.0F, -0.0F, 0.314159265F));
        PartDefinition leg_6_left = body.addOrReplaceChild("leg_6_left", CubeListBuilder.create().texOffs(49, 36).addBox(-0.95573727F, -0.0848733769F, -0.5F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(-1.70901476F, 2.05210212F, 3.94416438F, -0.0F, -0.0F, -0.314159265F));
        PartDefinition leg_6_right = body.addOrReplaceChild("leg_6_right", CubeListBuilder.create().texOffs(55, 36).addBox(-0.0442627304F, -0.0848733769F, -0.5F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(1.70901476F, 2.05210212F, 3.94416438F, -0.0F, -0.0F, 0.314159265F));
        PartDefinition leg_7_left = body.addOrReplaceChild("leg_7_left", CubeListBuilder.create().texOffs(61, 36).addBox(-0.95573727F, -0.158823003F, -0.5F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(-1.70901476F, 2.20000138F, 5.52183013F, -0.0F, -0.0F, -0.314159265F));
        PartDefinition leg_7_right = body.addOrReplaceChild("leg_7_right", CubeListBuilder.create().texOffs(67, 36).addBox(-0.0442627304F, -0.158823003F, -0.5F, 1.0F, 2.0F, 1.0F), PartPose.offsetAndRotation(1.70901476F, 2.20000138F, 5.52183013F, -0.0F, -0.0F, 0.314159265F));
        PartDefinition spiral_tail_0_joint = body.addOrReplaceChild("spiral_tail_0_joint", CubeListBuilder.create().texOffs(73, 36).addBox(-1.5F, -0.75F, -1.25F, 3.0F, 1.5F, 2.5F), PartPose.offsetAndRotation(-0.0F, 1.0029418F, 7.2967041F, 0.0333209959F, 0.0F, 0.0F));
        PartDefinition spiral_tail_1_joint = body.addOrReplaceChild("spiral_tail_1_joint", CubeListBuilder.create().texOffs(86, 36).addBox(-1.25F, -0.75F, -1.25F, 2.5F, 1.5F, 2.5F), PartPose.offsetAndRotation(-0.0F, 0.485294421F, 9.33452237F, 0.43833656F, 0.0F, 0.0F));
        PartDefinition spiral_tail_2_joint = body.addOrReplaceChild("spiral_tail_2_joint", CubeListBuilder.create().texOffs(98, 36).addBox(-1.25F, -0.75F, -1.25F, 2.5F, 1.5F, 2.5F), PartPose.offsetAndRotation(-0.0F, -0.970588843F, 10.9121881F, 1.080839F, 0.0F, 0.0F));
        PartDefinition spiral_tail_3_joint = body.addOrReplaceChild("spiral_tail_3_joint", CubeListBuilder.create().texOffs(110, 36).addBox(-1.0F, -0.75F, -1.25F, 2.0F, 1.5F, 2.5F), PartPose.offsetAndRotation(-0.0F, -2.78235468F, 11.0436603F, 1.13838855F, 3.14159265F, 0.0F));
        PartDefinition spiral_tail_4_joint = body.addOrReplaceChild("spiral_tail_4_joint", CubeListBuilder.create().texOffs(1, 42).addBox(-1.0F, -0.5F, -1.25F, 2.0F, 1.0F, 2.5F), PartPose.offsetAndRotation(-0.0F, -3.97941426F, 9.7289388F, 0.374333616F, 3.14159265F, 0.0F));
        PartDefinition spiral_tail_5_joint = body.addOrReplaceChild("spiral_tail_5_joint", CubeListBuilder.create().texOffs(12, 42).addBox(-0.75F, -0.5F, -1.0F, 1.5F, 1.0F, 2.0F), PartPose.offsetAndRotation(-0.0F, -3.97941426F, 8.01980091F, -0.429762279F, 3.14159265F, 0.0F));
        PartDefinition spiral_tail_6_joint = body.addOrReplaceChild("spiral_tail_6_joint", CubeListBuilder.create().texOffs(21, 42).addBox(-0.75F, -0.5F, -0.75F, 1.5F, 1.0F, 1.5F), PartPose.offsetAndRotation(-0.0F, -2.97647245F, 7.36244018F, -1.37340077F, 0.0F, 0.0F));
        PartDefinition spiral_tail_7_joint = body.addOrReplaceChild("spiral_tail_7_joint", CubeListBuilder.create().texOffs(29, 42).addBox(-0.5F, -0.25F, -0.75F, 1.0F, 0.5F, 1.5F), PartPose.offsetAndRotation(-0.0F, -2.23235434F, 8.01980091F, -0.18534795F, 0.0F, 0.0F));
        PartDefinition gore = mouse.addOrReplaceChild("gore", CubeListBuilder.create().texOffs(36, 42).addBox(-2.96229225F, -0.0517647383F, -1.64340183F, 5.9245845F, 0.0388235537F, 3.28680365F), PartPose.offsetAndRotation(-0.0F, 0.0F, 0.0F, -0.0F, -0.0F, 0.0F));
        PartDefinition flowing_antenna_left0_joint = head.addOrReplaceChild("flowing_antenna_left0_joint", CubeListBuilder.create().texOffs(57, 42).addBox(-0.25F, -0.25F, -1.0F, 0.5F, 0.5F, 2.0F), PartPose.offsetAndRotation(-1.25F, -2.0864996F, 0.897718717F, 1.10099323F, -1.16590454F, 0.0F));
        PartDefinition flowing_antenna_left1_joint = head.addOrReplaceChild("flowing_antenna_left1_joint", CubeListBuilder.create().texOffs(64, 42).addBox(-0.25F, -0.25F, -1.0F, 0.5F, 0.5F, 2.0F), PartPose.offsetAndRotation(-1.75F, -3.4864996F, 0.847718717F, 1.20362249F, -2.49809154F, 0.0F));
        PartDefinition flowing_antenna_left2_joint = head.addOrReplaceChild("flowing_antenna_left2_joint", CubeListBuilder.create().texOffs(71, 42).addBox(-0.25F, -0.25F, -1.0F, 0.5F, 0.5F, 2.0F), PartPose.offsetAndRotation(-1.7F, -4.7364996F, 0.247718717F, 0.930274014F, 2.67794504F, 0.0F));
        PartDefinition flowing_antenna_left3_joint = head.addOrReplaceChild("flowing_antenna_left3_joint", CubeListBuilder.create().texOffs(78, 42).addBox(-0.25F, -0.25F, -0.75F, 0.5F, 0.5F, 1.5F), PartPose.offsetAndRotation(-1.15F, -5.5864996F, -0.652281283F, 0.388768414F, 2.53086669F, 0.0F));
        PartDefinition flowing_antenna_right0_joint = head.addOrReplaceChild("flowing_antenna_right0_joint", CubeListBuilder.create().texOffs(84, 42).addBox(-0.25F, -0.25F, -1.0F, 0.5F, 0.5F, 2.0F), PartPose.offsetAndRotation(1.25F, -2.0864996F, 0.897718717F, 1.10099323F, 1.16590454F, 0.0F));
        PartDefinition flowing_antenna_right1_joint = head.addOrReplaceChild("flowing_antenna_right1_joint", CubeListBuilder.create().texOffs(91, 42).addBox(-0.25F, -0.25F, -1.0F, 0.5F, 0.5F, 2.0F), PartPose.offsetAndRotation(1.75F, -3.4864996F, 0.847718717F, 1.20362249F, 2.49809154F, 0.0F));
        PartDefinition flowing_antenna_right2_joint = head.addOrReplaceChild("flowing_antenna_right2_joint", CubeListBuilder.create().texOffs(98, 42).addBox(-0.25F, -0.25F, -1.0F, 0.5F, 0.5F, 2.0F), PartPose.offsetAndRotation(1.7F, -4.7364996F, 0.247718717F, 0.930274014F, -2.67794504F, 0.0F));
        PartDefinition flowing_antenna_right3_joint = head.addOrReplaceChild("flowing_antenna_right3_joint", CubeListBuilder.create().texOffs(105, 42).addBox(-0.25F, -0.25F, -0.75F, 0.5F, 0.5F, 1.5F), PartPose.offsetAndRotation(1.15F, -5.5864996F, -0.652281283F, 0.388768414F, -2.53086669F, 0.0F));
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
            throw new IllegalArgumentException("Nonfinite nymph presentation");
        root.getAllParts().forEach(ModelPart::resetPose);
        gore.visible = false; // Independent source blood state is OFF by default, even Dead.
        if (dead) clip = "down_hold";
        if (clip != null && !clip.equals("rest")) {
            KeyframeAnimations.animate(this, AnimalNymphAnimations.byName(clip),
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
