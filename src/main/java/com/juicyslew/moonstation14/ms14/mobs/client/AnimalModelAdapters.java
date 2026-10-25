package com.juicyslew.moonstation14.ms14.mobs.client;

import com.juicyslew.moonstation14.entities.AnimalMob;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.character.components.MobStateComponent;
import com.juicyslew.moonstation14.ms14.mobs.MobStateAttachment;
import com.juicyslew.moonstation14.ms14.animals.flight.FlightSystem;
import net.minecraft.client.model.geom.ModelPart;
import java.util.function.Function;
import java.util.function.Supplier;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Explicit typed bridge between a resolved animal appearance and its native exported rig.
 * Each exported model has its own adapter: the generated rigs expose different public
 * animation/state contracts, so callers must not infer behavior from arbitrary reflection.
 */
public final class AnimalModelAdapters {
    @FunctionalInterface private interface Animator<M> {
        void apply(M model, MobStateComponent.State state, String clip, float elapsedSeconds, float yaw, float pitch);
    }
    public interface Adapter {
        ModelLayerLocation layer();
        LayerDefinition definition();
        HierarchicalModel<? extends LivingEntity> bake(EntityRendererProvider.Context context);
        HierarchicalModel<? extends LivingEntity> construct(ModelPart root);
        ResourceLocation baseTexture();
        ResourceLocation texture(AnimalMob mob);
        Set<String> supportedClips();
        void animate(HierarchicalModel<? extends LivingEntity> model, AnimalMob mob,
                     float limbSwing, float limbSwingAmount, float ageInTicks,
                     float netHeadYaw, float headPitch);
         void applyClip(HierarchicalModel<? extends LivingEntity> model, AnimalMob mob, String clip,
                         float phase, float netHeadYaw, float headPitch);
         void applyPose(HierarchicalModel<? extends LivingEntity> model, MobStateComponent.State state,
                        String clip, float phase, float netHeadYaw, float headPitch);
        void applyPortable(HierarchicalModel<? extends LivingEntity> model,
                           com.juicyslew.moonstation14.ms14.hands.PortableBodyAppearance view, float ageInTicks);
        ResourceLocation portableTexture(com.juicyslew.moonstation14.ms14.hands.PortableBodyAppearance view);
        float presentationScale();
        float groundedContactOffsetPixels();
        boolean supportsPortablePresentation();
        boolean hasHandAnchors();
        java.util.List<String> handAnchorPaths();
        boolean translateToHand(HierarchicalModel<? extends LivingEntity> model, boolean left, com.mojang.blaze3d.vertex.PoseStack pose);
    }
    @FunctionalInterface private interface PortableAnimator<M> { void apply(M model,
            com.juicyslew.moonstation14.ms14.hands.PortableBodyAppearance view, float ageInTicks); }
    private record HandAnchorSpec(String leftPath, String rightPath, float x, float y, float z) { }

    private static final Map<String, Adapter> ADAPTERS = new LinkedHashMap<>();
    private static final Set<String> GROUNDED_CLIPS = Set.of("walk", "run", "idle_breathe", "down_hold");
    private static final Set<String> FLIGHT_CLIPS = Set.of("fly", "walk", "run", "idle_breathe", "down_hold");
    private static final Set<String> FLYING_MODELS = Set.of("bat", "bee", "butterfly", "parrot");
    static {
        bindNative("bat", com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalBatModel.LAYER_LOCATION, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalBatModel::createBodyLayer, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalBatModel::new, (m,e,c,t,y,p)->m.applyPresentation(c,dead(e),t,-y,-p,-1));
        bindPortable("bee", com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalBeeModel.LAYER_LOCATION, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalBeeModel::createBodyLayer, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalBeeModel::new, (m,e,c,t,y,p)->m.applyPresentation(c,dead(e),t,-y,-p,-1), (m,v,t)->m.applyPresentation(v.state() == com.juicyslew.moonstation14.ms14.hands.PortableBodyAppearance.State.ALIVE ? "idle_breathe" : "down_hold",v.state() == com.juicyslew.moonstation14.ms14.hands.PortableBodyAppearance.State.DEAD,t/20f,0,0,-1));
        bindPortable("butterfly", com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalButterflyModel.LAYER_LOCATION, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalButterflyModel::createBodyLayer, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalButterflyModel::new, (m,e,c,t,y,p)->m.applyPresentation(c,dead(e),t,-y,-p,-1), (m,v,t)->m.applyPresentation(v.state() == com.juicyslew.moonstation14.ms14.hands.PortableBodyAppearance.State.ALIVE ? "idle_breathe" : "down_hold",v.state() == com.juicyslew.moonstation14.ms14.hands.PortableBodyAppearance.State.DEAD,t/20f,0,0,-1));
        bindNative("chicken_0", com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalChicken0Model.LAYER_LOCATION, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalChicken0Model::createBodyLayer, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalChicken0Model::new, (m,e,c,t,y,p)->m.applyPresentation(c,dead(e),t,-y,-p,-1));
        bindNative("chicken_1", com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalChicken1Model.LAYER_LOCATION, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalChicken1Model::createBodyLayer, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalChicken1Model::new, (m,e,c,t,y,p)->m.applyPresentation(c,dead(e),t,-y,-p,-1));
        bindNative("chicken_2", com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalChicken2Model.LAYER_LOCATION, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalChicken2Model::createBodyLayer, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalChicken2Model::new, (m,e,c,t,y,p)->m.applyPresentation(c,dead(e),t,-y,-p,-1));
        bindPortable("cockroach", com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalCockroachModel.LAYER_LOCATION, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalCockroachModel::createBodyLayer, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalCockroachModel::new, (m,e,c,t,y,p)->m.applyPresentation(c,dead(e),t,-y,-p,-1), (m,v,t)->m.applyPresentation(v.state() == com.juicyslew.moonstation14.ms14.hands.PortableBodyAppearance.State.ALIVE ? "idle_breathe" : "down_hold",v.state() == com.juicyslew.moonstation14.ms14.hands.PortableBodyAppearance.State.DEAD,t/20f,0,0,-1));
        bindNative("cow", com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalCowModel.LAYER_LOCATION, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalCowModel::createBodyLayer, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalCowModel::new, (m,e,c,t,y,p)->m.applyPresentation(c,dead(e),t,-y,-p,-1));
        bindNative("crab", com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalCrabModel.LAYER_LOCATION, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalCrabModel::createBodyLayer, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalCrabModel::new, (m,e,c,t,y,p)->m.applyPresentation(c,dead(e),t,-y,-p,-1));
        bindNative("duck_0", com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalDuck0Model.LAYER_LOCATION, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalDuck0Model::createBodyLayer, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalDuck0Model::new, (m,e,c,t,y,p)->m.applyPresentation(c,dead(e),t,-y,-p,-1));
        bindNative("duck_1", com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalDuck1Model.LAYER_LOCATION, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalDuck1Model::createBodyLayer, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalDuck1Model::new, (m,e,c,t,y,p)->m.applyPresentation(c,dead(e),t,-y,-p,-1));
        bindNative("duck_2", com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalDuck2Model.LAYER_LOCATION, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalDuck2Model::createBodyLayer, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalDuck2Model::new, (m,e,c,t,y,p)->m.applyPresentation(c,dead(e),t,-y,-p,-1));
        bindNative("fox", com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalFoxModel.LAYER_LOCATION, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalFoxModel::createBodyLayer, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalFoxModel::new, (m,e,c,t,y,p)->m.applyPresentation(c,dead(e),t,-y,-p,-1));
        bindNative("frog", com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalFrogModel.LAYER_LOCATION, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalFrogModel::createBodyLayer, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalFrogModel::new, (m,e,c,t,y,p)->m.applyPresentation(c,dead(e),t,-y,-p,-1));
        bindNative("goat", com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalGoatModel.LAYER_LOCATION, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalGoatModel::createBodyLayer, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalGoatModel::new, (m,e,c,t,y,p)->m.applyPresentation(c,dead(e),t,-y,-p,-1));
        bindNative("goose", com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalGooseModel.LAYER_LOCATION, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalGooseModel::createBodyLayer, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalGooseModel::new, (m,e,c,t,y,p)->m.applyPresentation(c,dead(e),t,-y,-p,-1));
        bindNative("gorilla", com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalGorillaModel.LAYER_LOCATION, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalGorillaModel::createBodyLayer, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalGorillaModel::new, (m,e,c,t,y,p)->m.applyPresentation(c,dead(e),t,-y,-p,-1));
        bindPortable("hamster", com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalHamsterModel.LAYER_LOCATION, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalHamsterModel::createBodyLayer, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalHamsterModel::new, (m,e,c,t,y,p)->m.applyPresentation(c,dead(e),t,-y,-p,-1), (m,v,t)->m.applyPresentation(v.state() == com.juicyslew.moonstation14.ms14.hands.PortableBodyAppearance.State.ALIVE ? "idle_breathe" : "down_hold",v.state() == com.juicyslew.moonstation14.ms14.hands.PortableBodyAppearance.State.DEAD,t/20f,0,0,-1));
        bindNative("kangaroo", com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalKangarooModel.LAYER_LOCATION, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalKangarooModel::createBodyLayer, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalKangarooModel::new, (m,e,c,t,y,p)->m.applyPresentation(c,dead(e),t,-y,-p,-1));
        bindHanded("kobold", com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalKoboldModel.LAYER_LOCATION, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalKoboldModel::createBodyLayer, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalKoboldModel::new, (m,e,c,t,y,p)->m.applyPresentation(c,dead(e),t,-y,-p,-1), new HandAnchorSpec("mouse/body/arm_left/forearm_left", "mouse/body/arm_right/forearm_right", .1f, 3.65f, -.1f));
        bindNative("lizard", com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalLizardModel.LAYER_LOCATION, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalLizardModel::createBodyLayer, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalLizardModel::new, (m,e,c,t,y,p)->m.applyPresentation(c,dead(e),t,-y,-p,-1));
        bindHanded("monkey", com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalMonkeyModel.LAYER_LOCATION, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalMonkeyModel::createBodyLayer, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalMonkeyModel::new, (m,e,c,t,y,p)->m.applyPresentation(c,dead(e),t,-y,-p,-1), new HandAnchorSpec("mouse/body/arm_left/forearm_left", "mouse/body/arm_right/forearm_right", -.13f, 2.75f, -.1f));
        bindPortable("mothroach", com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalMothroachModel.LAYER_LOCATION, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalMothroachModel::createBodyLayer, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalMothroachModel::new, (m,e,c,t,y,p)->m.applyPresentation(c,dead(e),t,-y,-p,-1), (m,v,t)->m.applyPresentation(v.state() == com.juicyslew.moonstation14.ms14.hands.PortableBodyAppearance.State.ALIVE ? "idle_breathe" : "down_hold",v.state() == com.juicyslew.moonstation14.ms14.hands.PortableBodyAppearance.State.DEAD,t/20f,0,0,-1));
        bindPortable("moproach", com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalMoproachModel.LAYER_LOCATION, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalMoproachModel::createBodyLayer, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalMoproachModel::new, (m,e,c,t,y,p)->m.applyPresentation(c,dead(e),t,-y,-p,-1), (m,v,t)->m.applyPresentation(v.state() == com.juicyslew.moonstation14.ms14.hands.PortableBodyAppearance.State.ALIVE ? "idle_breathe" : "down_hold",v.state() == com.juicyslew.moonstation14.ms14.hands.PortableBodyAppearance.State.DEAD,t/20f,0,0,-1));
        bindNative("nymph", com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalNymphModel.LAYER_LOCATION, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalNymphModel::createBodyLayer, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalNymphModel::new, (m,e,c,t,y,p)->m.applyPresentation(c,dead(e),t,-y,-p,-1));
        bindNative("parrot", com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalParrotModel.LAYER_LOCATION, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalParrotModel::createBodyLayer, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalParrotModel::new, (m,e,c,t,y,p)->m.applyPresentation(c,dead(e),t,-y,-p,-1));
        bindNative("penguin", com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalPenguinModel.LAYER_LOCATION, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalPenguinModel::createBodyLayer, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalPenguinModel::new, (m,e,c,t,y,p)->m.applyPresentation(c,dead(e),t,-y,-p,-1));
        bindNative("penguin_baby", com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalPenguinBabyModel.LAYER_LOCATION, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalPenguinBabyModel::createBodyLayer, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalPenguinBabyModel::new, (m,e,c,t,y,p)->m.applyPresentation(c,dead(e),t,-y,-p,-1));
        bindNative("penguin_shamebrero", com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalPenguinShamebreroModel.LAYER_LOCATION, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalPenguinShamebreroModel::createBodyLayer, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalPenguinShamebreroModel::new, (m,e,c,t,y,p)->m.applyPresentation(c,dead(e),t,-y,-p,-1));
        bindNative("pig", com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalPigModel.LAYER_LOCATION, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalPigModel::createBodyLayer, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalPigModel::new, (m,e,c,t,y,p)->m.applyPresentation(c,dead(e),t,-y,-p,-1));
        bindNative("possum", com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalPossumModel.LAYER_LOCATION, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalPossumModel::createBodyLayer, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalPossumModel::new, (m,e,c,t,y,p)->m.applyPresentation(c,dead(e),t,-y,-p,-1));
        bindNative("possum_old", com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalPossumOldModel.LAYER_LOCATION, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalPossumOldModel::createBodyLayer, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalPossumOldModel::new, (m,e,c,t,y,p)->m.applyPresentation(c,dead(e),t,-y,-p,-1));
        bindNative("poppy", com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalPoppyModel.LAYER_LOCATION, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalPoppyModel::createBodyLayer, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalPoppyModel::new, (m,e,c,t,y,p)->m.applyPresentation(c,dead(e),t,-y,-p,-1));
        bindNative("raccoon", com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalRaccoonModel.LAYER_LOCATION, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalRaccoonModel::createBodyLayer, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalRaccoonModel::new, (m,e,c,t,y,p)->m.applyPresentation(c,dead(e),t,-y,-p,-1));
        bindNative("reindeer_buck", com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalReindeerBuckModel.LAYER_LOCATION, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalReindeerBuckModel::createBodyLayer, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalReindeerBuckModel::new, (m,e,c,t,y,p)->m.applyPresentation(c,dead(e),t,-y,-p,-1));
        bindNative("reindeer_doe", com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalReindeerDoeModel.LAYER_LOCATION, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalReindeerDoeModel::createBodyLayer, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalReindeerDoeModel::new, (m,e,c,t,y,p)->m.applyPresentation(c,dead(e),t,-y,-p,-1));
        bindHanded("scurret", com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalScurretModel.LAYER_LOCATION, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalScurretModel::createBodyLayer, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalScurretModel::new, (m,e,c,t,y,p)->m.applyPresentation(c,dead(e),t,-y,-p,-1), new HandAnchorSpec("mouse/body/arm_left", "mouse/body/arm_right", -.12f, 3.75f, -1.0f));
        bindNative("slug", com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalSlugModel.LAYER_LOCATION, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalSlugModel::createBodyLayer, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalSlugModel::new, (m,e,c,t,y,p)->m.applyPresentation(c,dead(e),t,-y,-p,-1));
        bindNative("snail", com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalSnailModel.LAYER_LOCATION, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalSnailModel::createBodyLayer, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalSnailModel::new, (m,e,c,t,y,p)->m.applyPresentation(c,dead(e),t,-y,-p,-1));
        bindNative("snoth", com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalSnothModel.LAYER_LOCATION, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalSnothModel::createBodyLayer, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalSnothModel::new, (m,e,c,t,y,p)->m.applyPresentation(c,dead(e),t,-y,-p,-1));
        bindNative("snake", com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalSnakeModel.LAYER_LOCATION, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalSnakeModel::createBodyLayer, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalSnakeModel::new, (m,e,c,t,y,p)->m.applyPresentation(c,dead(e),t,-y,-p,-1));
        bindNative("spider_tarantula", com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalSpiderTarantulaModel.LAYER_LOCATION, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalSpiderTarantulaModel::createBodyLayer, com.juicyslew.moonstation14.ms14.mob.animals.client.AnimalSpiderTarantulaModel::new, (m,e,c,t,y,p)->m.applyPresentation(c,dead(e),t,-y,-p,-1));
    }
    private AnimalModelAdapters() { }

    private static <M extends HierarchicalModel<? extends LivingEntity>> void bindNative(String key, ModelLayerLocation layer,
            Supplier<LayerDefinition> definition, Function<ModelPart, M> constructor, Animator<M> animator) {
        bindNative(key, layer, definition, constructor, animator, null, null);
    }
    private static <M extends HierarchicalModel<? extends LivingEntity>> void bindPortable(String key, ModelLayerLocation layer,
            Supplier<LayerDefinition> definition, Function<ModelPart, M> constructor, Animator<M> animator,
            PortableAnimator<M> portableAnimator) {
        bindNative(key, layer, definition, constructor, animator, portableAnimator, null);
    }
    private static <M extends HierarchicalModel<? extends LivingEntity>> void bindHanded(String key, ModelLayerLocation layer,
            Supplier<LayerDefinition> definition, Function<ModelPart, M> constructor, Animator<M> animator,
            HandAnchorSpec anchors) { bindNative(key, layer, definition, constructor, animator, null, anchors); }
    private static <M extends HierarchicalModel<? extends LivingEntity>> void bindNative(String key, ModelLayerLocation layer,
            Supplier<LayerDefinition> definition, Function<ModelPart, M> constructor, Animator<M> animator,
            PortableAnimator<M> portableAnimator, HandAnchorSpec anchors) {
        bind(key, new Adapter() {
            public float presentationScale() { return switch (key) {
                case "butterfly", "cockroach", "crab", "nymph", "lizard", "mothroach", "reindeer_buck", "reindeer_doe" -> .82F;
                case "spider_tarantula" -> .9F;
                default -> 1F;
            }; }
            public float groundedContactOffsetPixels() { return switch (key) {
                case "bat" -> 3.51152F;
                case "bee" -> 5.50592F;
                case "butterfly" -> 5.74938F;
                default -> 0F;
            }; }
            public ModelLayerLocation layer() { return layer; }
            public LayerDefinition definition() { return definition.get(); }
            public HierarchicalModel<? extends LivingEntity> bake(EntityRendererProvider.Context context) { return construct(context.bakeLayer(layer)); }
            @SuppressWarnings("unchecked") public HierarchicalModel<? extends LivingEntity> construct(ModelPart root) { return constructor.apply(root); }
            public ResourceLocation baseTexture() { return ResourceLocation.fromNamespaceAndPath("moonstation14", "textures/entity/animals/" + key + ".png"); }
            public Set<String> supportedClips() { return clipsFor(key); }
            public ResourceLocation texture(AnimalMob mob) {
                String suffix = switch (mobState(mob)) {
                    case DEAD -> "_dead";
                    case CRITICAL -> "_critical";
                    default -> "";
                };
                ResourceLocation candidate = ResourceLocation.fromNamespaceAndPath("moonstation14", "textures/entity/animals/" + key + suffix + ".png");
                return suffix.isEmpty() || net.minecraft.client.Minecraft.getInstance().getResourceManager().getResource(candidate).isPresent()
                        ? candidate : baseTexture();
            }
            @SuppressWarnings("unchecked") public void applyPortable(HierarchicalModel<? extends LivingEntity> model,
                    com.juicyslew.moonstation14.ms14.hands.PortableBodyAppearance view, float ageInTicks) {
                if (portableAnimator == null) throw new IllegalStateException("No portable presentation adapter for " + key);
                portableAnimator.apply((M) model, view, ageInTicks);
            }
            public ResourceLocation portableTexture(com.juicyslew.moonstation14.ms14.hands.PortableBodyAppearance view) {
                String suffix = switch (view.state()) {
                    case ALIVE -> "";
                    case CRITICAL -> "_critical";
                    case DEAD -> "_dead";
                };
                ResourceLocation candidate = ResourceLocation.fromNamespaceAndPath("moonstation14", "textures/entity/animals/" + key + suffix + ".png");
                return suffix.isEmpty() || net.minecraft.client.Minecraft.getInstance().getResourceManager().getResource(candidate).isPresent()
                        ? candidate : baseTexture();
            }
            public boolean supportsPortablePresentation() { return portableAnimator != null; }
            public boolean hasHandAnchors() { return anchors != null; }
            public java.util.List<String> handAnchorPaths() {
                return anchors == null ? java.util.List.of() : java.util.List.of(anchors.leftPath(), anchors.rightPath());
            }
            public boolean translateToHand(HierarchicalModel<? extends LivingEntity> model, boolean left,
                                           com.mojang.blaze3d.vertex.PoseStack pose) {
                if (anchors == null) return false;
                String[] path = (left ? anchors.leftPath() : anchors.rightPath()).split("/");
                ModelPart part = model.root();
                for (String child : path) {
                    part = part.getChild(child);
                    part.translateAndRotate(pose);
                }
                pose.translate(anchors.x() / 16.0, anchors.y() / 16.0, anchors.z() / 16.0);
                return true;
            }
            @SuppressWarnings("unchecked") public void animate(HierarchicalModel<? extends LivingEntity> model, AnimalMob mob, float swing, float amount, float age, float yaw, float pitch) { animator.apply((M) model, mobState(mob), clip(mob, amount, FLYING_MODELS.contains(key)), age / 20f, yaw, pitch); }
            @SuppressWarnings("unchecked") public void applyClip(HierarchicalModel<? extends LivingEntity> model, AnimalMob mob,
                    String selectedClip, float phase, float yaw, float pitch) {
                applyPose(model, mobState(mob), selectedClip, phase, yaw, pitch);
            }
            @SuppressWarnings("unchecked") public void applyPose(HierarchicalModel<? extends LivingEntity> model,
                    MobStateComponent.State state, String selectedClip, float phase, float yaw, float pitch) {
                animator.apply((M) model, state, selectedClip, phase, yaw, pitch);
            }
        });
    }

    private static boolean dead(MobStateComponent.State state) {
        return state == MobStateComponent.State.DEAD;
    }
    static MobStateComponent.State mobState(AnimalMob mob) {
        MobStateAttachment state = mob.getExistingDataOrNull(ModDataAttachments.MOB_STATE.get());
        return state == null ? MobStateComponent.State.ALIVE : state.data().state();
    }
    private static Set<String> clipsFor(String modelId) {
        if (modelId.equals("bat")) return Set.of("fly", "walk", "run", "idle_breathe", "down_hold");
        if (FLYING_MODELS.contains(modelId)) {
            if (modelId.equals("parrot")) return Set.of("fly", "walk", "run", "idle_breathe", "down_hold", "sit_hold");
            return FLIGHT_CLIPS;
        }
        if (modelId.equals("mothroach") || modelId.equals("nymph"))
            return Set.of("walk", "run", "idle_breathe", "down_hold", "sleep_hold");
        if (modelId.equals("gorilla")) return Set.of("walk", "run", "idle_breathe", "down_hold", "stand_hold");
        return GROUNDED_CLIPS;
    }

    static String selectClip(Set<String> clips, MobStateComponent.State state, boolean airborne,
                             boolean moving, boolean sprinting, boolean sleeping) {
        if (state != MobStateComponent.State.ALIVE) return "down_hold";
        if (sleeping && clips.contains("sleep_hold")) return "sleep_hold";
        if (airborne && clips.contains("fly")) return "fly";
        if (moving) {
            String locomotion = sprinting ? "run" : "walk";
            if (clips.contains(locomotion)) return locomotion;
        }
        return clips.contains("idle_breathe") ? "idle_breathe" : "rest";
    }
    public static String selectPortableClip(Set<String> clips,
            com.juicyslew.moonstation14.ms14.hands.PortableBodyAppearance.State state) {
        return state == com.juicyslew.moonstation14.ms14.hands.PortableBodyAppearance.State.ALIVE
                ? clips.contains("idle_breathe") ? "idle_breathe" : "rest"
                : clips.contains("down_hold") ? "down_hold" : "rest";
    }
    public static Set<String> handAnchorModels() {
        return ADAPTERS.entrySet().stream().filter(entry -> entry.getValue().hasHandAnchors())
                .map(Map.Entry::getKey).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
    public static Map<String, java.util.List<String>> handAnchorPaths() {
        var result = new LinkedHashMap<String, java.util.List<String>>();
        ADAPTERS.forEach((key, adapter) -> { if (adapter.hasHandAnchors()) result.put(key, adapter.handAnchorPaths()); });
        return Map.copyOf(result);
    }

    private static String clip(AnimalMob mob, float amount, boolean flies) {
        String appearance = mob.appearanceKey();
        Set<String> clips = appearance == null || appearance.isBlank()
                ? (flies ? FLIGHT_CLIPS : GROUNDED_CLIPS) : resolve(appearance).supportedClips();
        boolean airborne = FlightSystem.isFlying(mob); // Includes LANDING until grounded.
        return selectClip(clips, mobState(mob), airborne, amount > .025F,
                mob.isSprinting(), mob.hasPose(net.minecraft.world.entity.Pose.SLEEPING));
    }

    static String selectClip(AnimalMob mob, String appearance, boolean moving) {
        Set<String> clips = resolve(appearance).supportedClips();
        return selectClip(clips, mobState(mob), FlightSystem.isFlying(mob), moving,
                mob.isSprinting(), mob.hasPose(net.minecraft.world.entity.Pose.SLEEPING));
    }

    public static void bind(String resolvedAppearanceKey, Adapter adapter) {
        if (resolvedAppearanceKey == null || resolvedAppearanceKey.isBlank() || adapter == null)
            throw new IllegalArgumentException("Invalid animal presentation binding");
        if (ADAPTERS.putIfAbsent(resolvedAppearanceKey, adapter) != null)
            throw new IllegalStateException("Duplicate animal presentation binding: " + resolvedAppearanceKey);
    }

    public static Adapter resolve(String key) {
        Adapter adapter = ADAPTERS.get(key);
        if (adapter == null) throw new IllegalStateException("No animal model adapter for resolved appearance " + key);
        return adapter;
    }

    public static Map<String, Adapter> bindings() { return Map.copyOf(ADAPTERS); }

    /** Applies the rig-local ground contact correction after animation has selected its pose. */
    public static void applyGroundedContact(Adapter adapter, ModelPart root, boolean alive,
            boolean grounded, String clip, float weight) {
        if (!alive || !grounded || !(clip.equals("idle_breathe") || clip.equals("walk")
                || clip.equals("run") || clip.equals("rest"))) return;
        root.y += adapter.groundedContactOffsetPixels() * Math.max(0F, Math.min(1F, weight));
    }

    static final class DownedContact {
        private boolean measured;
        private float offset;

        void apply(ModelPart root, MobStateComponent.State state, String clip) {
            if (state == MobStateComponent.State.ALIVE || !"down_hold".equals(clip)) return;
            if (!measured) {
                var bottom = AnimalPoseBounds.maxVisibleY(root);
                offset = bottom.isPresent() ? (float) (24D - bottom.getAsDouble()) : 0F;
                measured = true;
            }
            root.y += offset;
        }
    }

    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        ADAPTERS.values().forEach(adapter -> event.registerLayerDefinition(adapter.layer(), adapter::definition));
    }
}
