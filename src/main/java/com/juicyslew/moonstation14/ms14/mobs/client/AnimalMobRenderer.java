package com.juicyslew.moonstation14.ms14.mobs.client;

import com.juicyslew.moonstation14.entities.AnimalMob;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import com.mojang.blaze3d.vertex.PoseStack;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.model.geom.ModelPart;
import com.mojang.blaze3d.vertex.PoseStack;

/** Renderer for prototype-resolved fauna appearances. Model geometry and animation
 * are supplied by per-export adapters; there is deliberately no generic fallback rig.
 */
public final class AnimalMobRenderer extends MobRenderer<AnimalMob, HierarchicalModel<AnimalMob>> {
    private final String baseAppearanceKey;
    private final EntityRendererProvider.Context context;
    private final Map<String, AdapterModel> models = new HashMap<>();
    private AnimalMob renderingMob;
    private String renderingAppearanceKey;

    public AnimalMobRenderer(EntityRendererProvider.Context context, String appearanceKey) {
        this(context, appearanceKey, AnimalModelAdapters.resolve(appearanceKey));
    }

    private AnimalMobRenderer(EntityRendererProvider.Context context, String appearanceKey,
                              AnimalModelAdapters.Adapter adapter) {
        super(context, new AdapterModel(appearanceKey, adapter, cast(adapter.bake(context))), 0.0F);
        this.baseAppearanceKey = appearanceKey;
        this.context = context;
        this.models.put(appearanceKey, (AdapterModel) model);
        addLayer(new com.juicyslew.moonstation14.ms14.equipment.client.AnimalBodyAppearanceItemLayer(this, context));
    }

    @SuppressWarnings("unchecked")
    private static HierarchicalModel<AnimalMob> cast(HierarchicalModel<? extends LivingEntity> model) {
        return (HierarchicalModel<AnimalMob>) (HierarchicalModel<?>) model;
    }

    @Override public ResourceLocation getTextureLocation(AnimalMob mob) {
        String key = renderingMob == mob ? renderingAppearanceKey : resolvedKey(mob);
        return AnimalModelAdapters.resolve(key).texture(mob);
    }

    private String resolvedKey(AnimalMob mob) {
        return selectAppearanceKey(mob.appearanceKey(), baseAppearanceKey);
    }

    public boolean translateHeldHand(boolean left, PoseStack pose) {
        return model instanceof AdapterModel selected && selected.translateToHand(left, pose);
    }

    static String selectAppearanceKey(String synchronizedKey, String baseKey) {
        String key = synchronizedKey;
        // The synchronized appearance may be empty during initial spawn. The entity's
        // registered catalog model is the only safe interim choice (never a generic rig).
        key = key == null || key.isBlank() ? baseKey : key;
        AnimalModelAdapters.resolve(key);
        return key;
    }

    @Override public void render(AnimalMob mob, float yaw, float partialTick, PoseStack poseStack,
            MultiBufferSource buffers, int packedLight) {
        com.juicyslew.moonstation14.ms14.animals.diagnostics.AnimalPopulationDiagnostic.animalRenderCall();
        String key = resolvedKey(mob);
        AdapterModel selected = models.computeIfAbsent(key, appearance -> {
            AnimalModelAdapters.Adapter selectedAdapter = AnimalModelAdapters.resolve(appearance);
            return new AdapterModel(appearance, selectedAdapter, cast(selectedAdapter.bake(context)));
        });
        model = selected;
        renderingMob = mob;
        renderingAppearanceKey = key;
        try {
            super.render(mob, yaw, partialTick, poseStack, buffers, packedLight);
        } finally {
            renderingMob = null;
            renderingAppearanceKey = null;
        }
    }
    @Override protected void scale(AnimalMob mob, com.mojang.blaze3d.vertex.PoseStack stack, float partialTick) {
        float factor = ((AdapterModel) model).adapter.presentationScale();
        stack.scale(factor, factor, factor);
    }
    @Override protected float getFlipDegrees(AnimalMob mob) { return 0.0F; }

    @Override protected void setupRotations(AnimalMob mob, com.mojang.blaze3d.vertex.PoseStack stack,
            float ageInTicks, float rotationYaw, float partialTicks, float scale) {
        super.setupRotations(mob, stack, ageInTicks, rotationYaw, partialTicks, scale);
    }

    private static final class AdapterModel extends HierarchicalModel<AnimalMob> {
        private final AnimalModelAdapters.Adapter adapter;
        private final String appearanceKey;
        private final HierarchicalModel<AnimalMob> delegate;
        private final java.util.List<ModelPart> parts;
        private final WeakHashMap<AnimalMob, AnimalAnimationTransition.State> animation = new WeakHashMap<>();
        private final float[] sourcePose;
        private final AnimalModelAdapters.DownedContact downedContact = new AnimalModelAdapters.DownedContact();
        private AdapterModel(String appearanceKey, AnimalModelAdapters.Adapter adapter, HierarchicalModel<AnimalMob> delegate) {
            this.adapter = adapter;
            this.appearanceKey = appearanceKey;
            this.delegate = delegate;
            this.parts = delegate.root().getAllParts().toList();
            this.sourcePose = new float[parts.size() * 9];
        }
        @Override public net.minecraft.client.model.geom.ModelPart root() { return delegate.root(); }
        @Override public void setupAnim(AnimalMob mob, float limbSwing, float limbSwingAmount,
                float ageInTicks, float netHeadYaw, float headPitch) {
            double now = (mob.tickCount + (double) (ageInTicks - mob.tickCount)) / 20.0;
            double walkSpeed=0;
            try {
                var data=com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.projectForActor(mob).orElse(null);
                if(data!=null) walkSpeed=com.juicyslew.moonstation14.ms14.movement.CharacterMovementPolicy
                        .fromCharacterData(data).walkSpeedPerSecond();
                var flight = com.juicyslew.moonstation14.ms14.animals.flight.FlightSystem.capability(mob).orElse(null);
                var flightState = com.juicyslew.moonstation14.ms14.animals.flight.FlightSystem.state(mob);
                boolean externalFlight = com.juicyslew.moonstation14.ms14.player_body_control.movement.ExternalBodyFlight
                        .active(mob);
                walkSpeed = com.juicyslew.moonstation14.ms14.animals.flight.FlightGroundSpeedPolicy.cadenceWalkSpeed(
                        walkSpeed, flight, flightState, mob.onGround(), externalFlight);
            } catch(RuntimeException ignored) { }
            var renderedPosition=mob.getPosition(ageInTicks-mob.tickCount);
            boolean moving=animation.computeIfAbsent(mob, ignored -> new AnimalAnimationTransition.State())
                    .observe(appearanceKey,now,renderedPosition.x,renderedPosition.z,walkSpeed);
            String selectedAppearance = appearanceKey;
            String requested = AnimalModelAdapters.selectClip(mob, appearanceKey, moving);
            boolean immediate = !"alive".equalsIgnoreCase(mobStateName(mob))
                    || mob.hasPose(net.minecraft.world.entity.Pose.SLEEPING);
            AnimalAnimationTransition.State state = animation.computeIfAbsent(mob, ignored -> new AnimalAnimationTransition.State());
            var selection = state.select(selectedAppearance, requested, now, immediate);
            adapter.applyClip(delegate, mob, selection.source(), selection.sourcePhase(), netHeadYaw, headPitch);
            if (!selection.source().equals(selection.destination()) && selection.destinationWeight() < 1) {
                for (int i = 0; i < parts.size(); i++) writePose(parts.get(i), sourcePose, i * 9);
                adapter.applyClip(delegate, mob, selection.destination(), selection.destinationPhase(), netHeadYaw, headPitch);
                for (int i = 0; i < parts.size(); i++) interpolate(parts.get(i), sourcePose, i * 9, selection.destinationWeight());
            }
            String groundedClip = selection.destination();
            AnimalModelAdapters.applyGroundedContact(adapter, root(),
                    AnimalModelAdapters.mobState(mob) == com.juicyslew.moonstation14.ms14.character.components.MobStateComponent.State.ALIVE,
                    !com.juicyslew.moonstation14.ms14.animals.flight.FlightSystem.isFlying(mob), groundedClip,
                    selection.source().equals(groundedClip) ? 1F : selection.destinationWeight());
            downedContact.apply(root(), AnimalModelAdapters.mobState(mob), groundedClip);
            recordFlyerDiagnostic(mob, renderedPosition.y, selection);
        }
        private void recordFlyerDiagnostic(AnimalMob mob, double renderY, AnimalAnimationTransition.Selection selection) {
            if (!com.juicyslew.moonstation14.ms14.animals.diagnostics.AnimalPopulationDiagnostic.flyerPosesEnabled()
                    || !adapter.supportedClips().contains("fly")) return;
            long tick = mob.level().getGameTime();
            if (!com.juicyslew.moonstation14.ms14.animals.diagnostics.AnimalPopulationClientSampler
                    .tryFlyerPoseSample(mob.getUUID(), mob.level(), tick)) return;
            var bottom = AnimalPoseBounds.maxVisibleY(root());
            var capability = com.juicyslew.moonstation14.ms14.animals.flight.FlightSystem.capability(mob);
            var event = new com.juicyslew.moonstation14.ms14.animals.diagnostics.AnimalPopulationDiagnostic.ClientFlyerPose();
            event.entityId = mob.getUUID().toString(); event.dimension = mob.level().dimension().location().toString();
            event.hostEntityType = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString();
            event.gameTick = tick; event.entityY = mob.getY(); event.renderY = renderY;
            event.flightState = com.juicyslew.moonstation14.ms14.animals.flight.FlightSystem.state(mob).name();
            event.poweredFlight = com.juicyslew.moonstation14.ms14.animals.flight.FlightSystem.isActivePoweredFlight(mob);
            event.resolvedFlightCapability = capability.isPresent(); event.onGround = mob.onGround();
            event.sourceClip = selection.source(); event.destinationClip = selection.destination(); event.blendWeight = selection.destinationWeight();
            event.hasVisibleGeometry = bottom.isPresent(); event.modelBottomPixels = bottom.orElse(Double.NaN);
            event.presentationScale = adapter.presentationScale();
            event.estimatedMeshBottomY = bottom.isPresent()
                    ? com.juicyslew.moonstation14.ms14.animals.diagnostics.AnimalPopulationDiagnostic.estimateMeshBottomY(renderY, bottom.getAsDouble(), event.presentationScale)
                    : Double.NaN;
            com.juicyslew.moonstation14.ms14.animals.diagnostics.AnimalPopulationDiagnostic.commitClientFlyerPose(event);
        }
        private static String mobStateName(AnimalMob mob) { return AnimalModelAdapters.mobState(mob).name(); }
        private static void writePose(ModelPart p, float[] a, int i) {
            a[i]=p.x; a[i+1]=p.y; a[i+2]=p.z; a[i+3]=p.xRot; a[i+4]=p.yRot; a[i+5]=p.zRot;
            a[i+6]=p.xScale; a[i+7]=p.yScale; a[i+8]=p.zScale;
        }
        private static void interpolate(ModelPart p, float[] a, int i, float w) {
            p.x=lerp(a[i],p.x,w); p.y=lerp(a[i+1],p.y,w); p.z=lerp(a[i+2],p.z,w);
            p.xRot=angle(a[i+3],p.xRot,w); p.yRot=angle(a[i+4],p.yRot,w); p.zRot=angle(a[i+5],p.zRot,w);
            p.xScale=lerp(a[i+6],p.xScale,w); p.yScale=lerp(a[i+7],p.yScale,w); p.zScale=lerp(a[i+8],p.zScale,w);
        }
        private static float lerp(float a,float b,float w) { return a+(b-a)*w; }
        private static float angle(float a,float b,float w) {
            float d=(float)Math.atan2(Math.sin(b-a),Math.cos(b-a)); return a+d*w;
        }
        private boolean translateToHand(boolean left, PoseStack pose) {
            return adapter.translateToHand(delegate, left, pose);
        }
    }
}
