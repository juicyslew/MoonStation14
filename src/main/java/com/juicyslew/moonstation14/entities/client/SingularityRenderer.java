package com.juicyslew.moonstation14.entities.client;

import com.juicyslew.moonstation14.entities.SingularityEntity;
import com.juicyslew.moonstation14.ms14.singularity.ModSingularityVisuals;
import com.juicyslew.moonstation14.ms14.singularity.client.SingularityBillboardBasis;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.AABB;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

/** Live, full-bright sprite rendering on a dedicated world-lens-exempt cutout program. */
public final class SingularityRenderer extends EntityRenderer<SingularityEntity> {
    private static volatile ShaderInstance coreShader;
    private static final net.minecraft.client.renderer.RenderStateShard.ShaderStateShard CORE_SHADER_STATE =
            new net.minecraft.client.renderer.RenderStateShard.ShaderStateShard(() -> coreShader);

    public static void registerShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                            ResourceLocation.fromNamespaceAndPath("moonstation14", "singularity_core"),
                            com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY), loaded -> {
                        coreShader = loaded;
                        com.juicyslew.moonstation14.MoonStation14.LOGGER.info("MS14 singularity world shader initialized");
                    });
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Unable to load dedicated singularity core shader", exception);
        }
    }

    public static boolean coreShaderReady() { return coreShader != null; }

    private static RenderType depthTestedCore(ResourceLocation texture) {
        return RenderType.create("moonstation14_singularity_core", com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY,
                com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, 1536,
                RenderType.CompositeState.builder()
                        .setShaderState(CORE_SHADER_STATE)
                        .setTextureState(new net.minecraft.client.renderer.RenderStateShard.TextureStateShard(texture,false,false))
                        .setTransparencyState(net.minecraft.client.renderer.RenderStateShard.NO_TRANSPARENCY)
                        .setDepthTestState(net.minecraft.client.renderer.RenderStateShard.LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(net.minecraft.client.renderer.RenderStateShard.COLOR_DEPTH_WRITE)
                        .setCullState(net.minecraft.client.renderer.RenderStateShard.NO_CULL)
                        .setLightmapState(net.minecraft.client.renderer.RenderStateShard.LIGHTMAP)
                        .setOverlayState(net.minecraft.client.renderer.RenderStateShard.OVERLAY).createCompositeState(false));
    }
    private static final int MAX_DIAGNOSTICS = 32;
    private static final java.util.LinkedHashMap<java.util.UUID, Diagnostic> DIAGNOSTICS = new java.util.LinkedHashMap<>();
    private static Diagnostic diagnostic(SingularityEntity entity) {
        return DIAGNOSTICS.computeIfAbsent(entity.getUUID(), id -> {
            if (DIAGNOSTICS.size() >= MAX_DIAGNOSTICS) DIAGNOSTICS.remove(DIAGNOSTICS.keySet().iterator().next());
            return new Diagnostic();
        });
    }
    public static String diagnosticSnapshot(java.util.UUID id) {
        Diagnostic d = DIAGNOSTICS.get(id);
        return d == null ? "shouldRender=not-checked renders=0" : "shouldRender=" + d.admission
                + " checks=" + d.checks + " renders=" + d.renders + " lastTick=" + d.tick;
    }
    public static void clearDiagnostics() { DIAGNOSTICS.clear(); }
    private static final class Diagnostic { boolean admission; long checks, renders, tick; }
    public SingularityRenderer(EntityRendererProvider.Context context) { super(context); }

    private static ResourceLocation texture(SingularityEntity entity, float partialTick) {
        int level = Math.max(1, Math.min(6, entity.singularityLevel()));
        var profile=ModSingularityVisuals.get(level);
        return profile==null?ResourceLocation.fromNamespaceAndPath("moonstation14","textures/singularity/singularity_"+level+".png")
                :profile.frameTexture((entity.level().getGameTime()+partialTick)/20.0);
    }

    @Override public void render(SingularityEntity entity, float yaw, float partialTick, PoseStack pose,
                                 MultiBufferSource buffers, int light) {
        Diagnostic diagnostic = diagnostic(entity); diagnostic.renders++; diagnostic.tick = entity.level().getGameTime();
        var profile = ModSingularityVisuals.get(entity.singularityLevel());
        if (profile != null) {
            pose.pushPose();
            try {
                var source = entity.getPosition(partialTick);
                var camera = entityRenderDispatcher.camera.getPosition();
                pose.mulPose(SingularityBillboardBasis.between(source, camera).rotation());
                float scale = profile.spriteHalfExtent();
                pose.scale(scale, scale, scale);
                ProjectileBillboard.renderCrossed(pose, buffers,
                        depthTestedCore(texture(entity,partialTick)), 15728880);
            } finally { pose.popPose(); }
        }
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    @Override public ResourceLocation getTextureLocation(SingularityEntity entity) { return texture(entity,0); }

    @Override public boolean shouldRender(SingularityEntity entity,
                                          net.minecraft.client.renderer.culling.Frustum frustum,
                                          double cameraX, double cameraY, double cameraZ) {
        Diagnostic diagnostic = diagnostic(entity); diagnostic.checks++; diagnostic.tick = entity.level().getGameTime();
        if (!entity.shouldRender(cameraX, cameraY, cameraZ)) { diagnostic.admission = false; return false; }
        if (entity.noCulling) { diagnostic.admission = true; return true; }
        AABB body = entity.getBoundingBox();
        var profile = ModSingularityVisuals.get(entity.singularityLevel());
        if (profile == null) { diagnostic.admission = frustum.isVisible(body.inflate(0.5)); return diagnostic.admission; }
        // Billboard planes rotate with the camera; sqrt(2) bounds their farthest quad corner.
        double radius = profile.spriteHalfExtent() * Math.sqrt(2);
        double x = entity.getX(), y = entity.getY(), z = entity.getZ();
        AABB rendered = body.minmax(new AABB(x - radius, y - radius, z - radius,
                x + radius, y + radius, z + radius));
        diagnostic.admission = frustum.isVisible(rendered.inflate(0.5));
        return diagnostic.admission;
    }
}
