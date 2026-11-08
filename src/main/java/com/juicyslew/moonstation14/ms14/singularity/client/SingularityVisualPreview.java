package com.juicyslew.moonstation14.ms14.singularity.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import com.juicyslew.moonstation14.ms14.singularity.ModSingularityVisuals;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;

import java.util.ArrayList;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/** Local-only visual test anchors, source visibility, and diagnostics for the world-vertex lens. */
public final class SingularityVisualPreview {
    private static final List<Anchor> ANCHORS = new ArrayList<>();
    private static boolean reducedMotion;
    private static final SingularitySourceVisibility.Cache VISIBILITY = new SingularitySourceVisibility.Cache();
    private static Object visibilityWorld;
    private SingularityVisualPreview() { }

    public static void preview(int level) {
        if (level < 1 || level > 6) throw new IllegalArgumentException("level must be 1..6");
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (ANCHORS.size() == 5) ANCHORS.remove(0);
        ANCHORS.add(new Anchor(UUID.randomUUID(), mc.player.blockPosition().getX()+.5, mc.player.blockPosition().getY()+.5, mc.player.blockPosition().getZ()+.5, level));
    }
    public static void clear() {
        ANCHORS.clear();
        VISIBILITY.clear();
    }
    public static void toggleReducedMotion() { reducedMotion = !reducedMotion; }
    public static boolean reducedMotion() { return reducedMotion; }
    static void resetVisibility() { VISIBILITY.clear(); visibilityWorld = null; }
    public static String diagnosticStatus() {
        Minecraft mc=Minecraft.getInstance(); int level=ANCHORS.isEmpty()?0:ANCHORS.get(0).level;
        if(level==0&&mc.level!=null&&mc.player!=null) {
            var live=mc.level.getEntitiesOfClass(com.juicyslew.moonstation14.entities.SingularityEntity.class,
                    mc.player.getBoundingBox().inflate(4096));
            if(!live.isEmpty()) level=live.get(0).singularityLevel();
        }
        var profile=level>0?ModSingularityVisuals.get(level):null;
        int frame=profile==null||mc.level==null? -1:profile.frameIndex(mc.level.getGameTime()/20.0);
        var activeSources = SingularityWorldLensClient.worldSources();
        return "worldFrameActive="+SingularityWorldLensClient.isActive()+" declaredSources="+activeSources.size()
                +" appliedCount="+SingularityWorldLensUniforms.activeSourceCount()+" pass=vertex-world-map"
                +" sourcePass="+activeSources.stream().map(s -> s.id()+"/L"+s.level()+"/visibility="+s.visibility()).toList()
                +" reducedMotion="+reducedMotion+" previews="+ANCHORS.size()+" frameCount="
                +(profile==null?0:profile.frameDelaysSeconds().size())+" currentFrame="+frame;
    }

    /** Read-only native PA model and below-neighbor occlusion report for the existing status command. */
    public static String nativePaDiagnostic() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.hitResult == null || !(mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit))
            return "nativePA=not-aiming-at-block";
        var pos = hit.getBlockPos();
        var state = mc.level.getBlockState(pos);
        if (!(state.getBlock() instanceof com.juicyslew.moonstation14.block.custom.ParticleAcceleratorPartBlock pa))
            return "nativePA=aimed-block-not-pa id=" + net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
        var belowPos = pos.below();
        var below = mc.level.getBlockState(belowPos);
        boolean groundFace = net.minecraft.world.level.block.Block.shouldRenderFace(
                below, mc.level, belowPos, net.minecraft.core.Direction.UP, pos);
        var model = mc.getBlockRenderer().getBlockModel(state);
        var random = net.minecraft.util.RandomSource.create(0L);
        var sprites = new java.util.LinkedHashSet<net.minecraft.resources.ResourceLocation>();
        var faceGeometry = new java.util.EnumMap<net.minecraft.core.Direction, String>(net.minecraft.core.Direction.class);
        int general = model.getQuads(state, null, random).size();
        for (var quad : model.getQuads(state, null, net.minecraft.util.RandomSource.create(0L))) {
            sprites.add(quad.getSprite().contents().name());
            recordRepresentativeFace(faceGeometry, quad);
        }
        var directional = new java.util.EnumMap<net.minecraft.core.Direction, Integer>(net.minecraft.core.Direction.class);
        for (var direction : net.minecraft.core.Direction.values()) {
            var quads = model.getQuads(state, direction, net.minecraft.util.RandomSource.create(0L));
            directional.put(direction, quads.size());
            for (var quad : quads) {
                sprites.add(quad.getSprite().contents().name());
                recordRepresentativeFace(faceGeometry, quad);
            }
        }
        var renders = model.getRenderTypes(state, net.minecraft.util.RandomSource.create(0L),
                net.neoforged.neoforge.client.model.data.ModelData.EMPTY);
        var renderLayerNames = new ArrayList<String>();
        var probeShaders = new java.util.LinkedHashSet<String>();
        for (var renderType : renders) {
            String shaderName = nativeChunkShaderName(renderType);
            renderLayerNames.add(nativeChunkLayerName(renderType));
            if (shaderName != null) probeShaders.add(shaderName);
        }
        SingularityNativeDrawProbe.arm(probeShaders);
        var occlusionShape = state.getOcclusionShape(mc.level, pos);
        var occlusion = occlusionShape.isEmpty() ? "empty" : occlusionShape.bounds().toString();
        var textureEvidence = new ArrayList<String>();
        int textureCount = 0;
        for (var spriteId : sprites) {
            if (textureCount++ == 8) break;
            var sprite = mc.getTextureAtlas(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS)
                    .apply(spriteId).contents();
            textureEvidence.add(spriteId + "[frame=" + sprite.width() + "x" + sprite.height()
                    + ",png=" + resourceEvidence(mc, textureResource(spriteId))
                    + ",mcmeta=" + resourceEvidence(mc, textureMetadataResource(spriteId)) + "]");
        }
        return "nativePA id=" + net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock())
                + " role=" + pa.role() + " state=" + state + " model=" + model.getClass().getName()
                + " renderTypes=" + renderLayerNames + " generalQuads=" + general + " directionalQuads=" + directional
                + " representativeFaceCentersAndWinding=" + faceGeometry
                + " sprites=" + sprites + " spriteTextureEvidence=" + textureEvidence + " paCanOcclude=" + state.canOcclude() + " paOcclusion=" + occlusion
                + " below=" + net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(below.getBlock())
                + " belowState=" + below + " belowRenderShape=" + below.getRenderShape()
                + " belowCanOcclude=" + below.canOcclude() + " groundTopShouldRender=" + groundFace
                + " drawCapture=armed-matching-native-chunk-layer-shaders=" + probeShaders + "-generic-context";
    }

    private static String nativeChunkShaderName(net.minecraft.client.renderer.RenderType renderType) {
        if (renderType == net.minecraft.client.renderer.RenderType.solid()) return "rendertype_solid";
        if (renderType == net.minecraft.client.renderer.RenderType.cutout()) return "rendertype_cutout";
        if (renderType == net.minecraft.client.renderer.RenderType.cutoutMipped()) return "rendertype_cutout_mipped";
        if (renderType == net.minecraft.client.renderer.RenderType.translucent()) return "rendertype_translucent";
        return null;
    }

    private static String nativeChunkLayerName(net.minecraft.client.renderer.RenderType renderType) {
        if (renderType == net.minecraft.client.renderer.RenderType.solid()) return "solid";
        if (renderType == net.minecraft.client.renderer.RenderType.cutout()) return "cutout";
        if (renderType == net.minecraft.client.renderer.RenderType.cutoutMipped()) return "cutout_mipped";
        if (renderType == net.minecraft.client.renderer.RenderType.translucent()) return "translucent";
        return "unsupported-native-chunk-layer";
    }

    static ResourceLocation textureResource(ResourceLocation sprite) {
        return ResourceLocation.fromNamespaceAndPath(sprite.getNamespace(), "textures/" + sprite.getPath() + ".png");
    }

    static ResourceLocation textureMetadataResource(ResourceLocation sprite) {
        return textureResource(sprite).withSuffix(".mcmeta");
    }

    private static String resourceEvidence(Minecraft mc, ResourceLocation id) {
        try {
            var resource = mc.getResourceManager().getResource(id);
            if (resource.isEmpty()) return "absent";
            var active = resource.get();
            return active.sourcePackId() + "/sha256=" + hash(active.open());
        } catch (Exception exception) {
            return "unavailable:" + exception.getClass().getSimpleName();
        }
    }

    private static String hash(InputStream input) throws java.io.IOException {
        try (var stream = input) {
            var digest = MessageDigest.getInstance("SHA-256");
            try (var hashing = new DigestInputStream(stream, digest)) {
                hashing.transferTo(java.io.OutputStream.nullOutputStream());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static void recordRepresentativeFace(java.util.Map<net.minecraft.core.Direction, String> geometry,
                                                net.minecraft.client.renderer.block.model.BakedQuad quad) {
        var direction = quad.getDirection();
        geometry.putIfAbsent(direction, quadFaceGeometry(quad));
    }

    private static String quadFaceGeometry(net.minecraft.client.renderer.block.model.BakedQuad quad) {
        int[] vertices = quad.getVertices();
        double x0 = Float.intBitsToFloat(vertices[0]), y0 = Float.intBitsToFloat(vertices[1]), z0 = Float.intBitsToFloat(vertices[2]);
        double ax = Float.intBitsToFloat(vertices[8]) - x0, ay = Float.intBitsToFloat(vertices[9]) - y0,
                az = Float.intBitsToFloat(vertices[10]) - z0;
        double bx = Float.intBitsToFloat(vertices[16]) - Float.intBitsToFloat(vertices[8]);
        double by = Float.intBitsToFloat(vertices[17]) - Float.intBitsToFloat(vertices[9]);
        double bz = Float.intBitsToFloat(vertices[18]) - Float.intBitsToFloat(vertices[10]);
        double nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
        double cx = 0, cy = 0, cz = 0;
        for (int vertex = 0; vertex < 4; vertex++) {
            cx += Float.intBitsToFloat(vertices[vertex * 8]);
            cy += Float.intBitsToFloat(vertices[vertex * 8 + 1]);
            cz += Float.intBitsToFloat(vertices[vertex * 8 + 2]);
        }
        return "center=" + (cx / 4) + "," + (cy / 4) + "," + (cz / 4) + " cross=" + nx + "," + ny + "," + nz;
    }

    public static void render(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) { VISIBILITY.clear(); visibilityWorld=null; return; }
        if (visibilityWorld != mc.level) { VISIBILITY.clear(); visibilityWorld=mc.level; }
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) drawSprites(event, mc);
        if (reducedMotion) VISIBILITY.clear();
    }

    /** Frame collector owns the one shared live/preview source ordering and visibility cache. */
    static List<WorldSource> collectResolvedWorldSources(Minecraft mc, Camera camera, float partialTick, org.joml.Matrix4fc view) {
        if (mc.level == null || mc.player == null || camera == null || reducedMotion) { VISIBILITY.clear(); return List.of(); }
        if (visibilityWorld != mc.level) { VISIBILITY.clear(); visibilityWorld = mc.level; }
        var cameraPos = camera.getPosition();
        List<Anchor> selected = selectWarpSources(liveSources(mc, partialTick), List.copyOf(ANCHORS));
        VISIBILITY.prune(selected.stream().map(Anchor::id).collect(java.util.stream.Collectors.toSet()));
        var result = new ArrayList<WorldSource>(selected.size());
        var cameraRelativeEye = new org.joml.Vector4f();
        for (Anchor anchor : selected) {
            var profile = ModSingularityVisuals.get(anchor.level);
            if (profile == null || !Float.isFinite(profile.lensStrength()) || profile.lensStrength() <= 0) continue;
            double dx = anchor.x - cameraPos.x, dy = anchor.y - cameraPos.y, dz = anchor.z - cameraPos.z;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (!Double.isFinite(distance) || distance < 1e-5) continue;
            var world = new net.minecraft.world.phys.Vec3(anchor.x, anchor.y, anchor.z);
            float visibility = VISIBILITY.update(anchor.id, mc.level,
                    SingularitySourceVisibility.fraction(mc.level, pos -> mc.level.hasChunkAt(pos), cameraPos,
                            world, profile.spriteHalfExtent(), SingularityBillboardBasis.between(world, cameraPos)), System.nanoTime());
            float effectiveStrength = profile.lensStrength() * visibility;
            if (!Float.isFinite(effectiveStrength) || effectiveStrength <= 0) continue;
            cameraRelativeEye.set((float) dx, (float) dy, (float) dz, 1f);
            view.transform(cameraRelativeEye);
            if (!Float.isFinite(cameraRelativeEye.x) || !Float.isFinite(cameraRelativeEye.y) || !Float.isFinite(cameraRelativeEye.z)) continue;
            var uniform = new SingularityWorldLensUniforms.Source(cameraRelativeEye.x, cameraRelativeEye.y, cameraRelativeEye.z,
                    profile.intensity(), profile.falloff(), profile.cutoffWorldUnits() * com.juicyslew.moonstation14.ms14.singularity.SingularityVisualProfile.PIXELS_PER_METER,
                    effectiveStrength, profile.foregroundRampWorldUnits());
            result.add(new WorldSource(anchor.id, world, anchor.level, profile, visibility, uniform));
        }
        return List.copyOf(result);
    }

    public record WorldSource(UUID id, net.minecraft.world.phys.Vec3 worldPosition, int level,
                              com.juicyslew.moonstation14.ms14.singularity.SingularityVisualProfile profile,
                              float visibility, SingularityWorldLensUniforms.Source uniform) { }

    private static boolean hasSources(Minecraft mc) {
        return !ANCHORS.isEmpty() || !mc.level.getEntitiesOfClass(com.juicyslew.moonstation14.entities.SingularityEntity.class,
                mc.player.getBoundingBox().inflate(4096), e -> !e.isRemoved()).isEmpty();
    }

    private static void drawSprites(RenderLevelStageEvent event, Minecraft mc) {
        Camera camera = event.getCamera();
        var cameraPos = camera.getPosition();
        var pose = event.getPoseStack();
        Matrix4f matrix = pose != null ? pose.last().pose() : event.getModelViewMatrix();
        boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),cull=GL11.glIsEnabled(GL11.GL_CULL_FACE),blend=GL11.glIsEnabled(GL11.GL_BLEND);
        boolean depthWrite=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int srcRgb=GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),dstRgb=GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcAlpha=GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),dstAlpha=GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        int activeTexture=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0); int texture0=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GlStateManager._activeTexture(activeTexture);
        ShaderInstance previousShader=RenderSystem.getShader();
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest(); RenderSystem.depthMask(false); RenderSystem.disableCull();
        try (var lensSuspension = SingularityWorldLensUniforms.suspend()) {
            for (Anchor anchor : renderSources(mc)) {
                var profile = ModSingularityVisuals.get(anchor.level);
                if (profile == null || !mc.level.hasChunkAt(BlockPos.containing(anchor.x, anchor.y, anchor.z))) continue;
                var center = new org.joml.Vector3d(anchor.x, anchor.y, anchor.z);
                double dx = center.x - cameraPos.x, dy = center.y - cameraPos.y, dz = center.z - cameraPos.z;
                double distance = Math.sqrt(dx*dx + dy*dy + dz*dz);
                if (distance < .05 || distance > profile.cutoffWorldUnits() * 4) continue;
                float scale = profile.spriteHalfExtent();
                var basis = SingularityBillboardBasis.between(new net.minecraft.world.phys.Vec3(center.x, center.y, center.z), cameraPos);
                float cx = (float)(center.x-cameraPos.x), cy=(float)(center.y-cameraPos.y), cz=(float)(center.z-cameraPos.z);
                double elapsed=(mc.level.getGameTime()+event.getPartialTick().getGameTimeDeltaPartialTick(false))/20.0;
                var id = profile.frameTexture(elapsed);
                RenderSystem.setShaderTexture(0, id); RenderSystem.setShader(GameRendererAccess::positionTexColorShader);
                BufferBuilder b=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);
                 var right=basis.rightVector(); var up=basis.upVector();
                 float rx=right.x*scale, ry=right.y*scale, rz=right.z*scale;
                 float ux=up.x*scale, uy=up.y*scale, uz=up.z*scale;
                vertex(b,matrix,cx-rx-ux,cy-ry-uy,cz-rz-uz,0,1); vertex(b,matrix,cx+rx-ux,cy+ry-uy,cz+rz-uz,1,1);
                vertex(b,matrix,cx+rx+ux,cy+ry+uy,cz+rz+uz,1,0); vertex(b,matrix,cx-rx+ux,cy-ry+uy,cz-rz+uz,0,0);
                BufferUploader.drawWithShader(b.buildOrThrow());
            }
        } finally {
            RenderSystem.depthMask(depthWrite);
            setCapability(GL11.GL_DEPTH_TEST,depth); setCapability(GL11.GL_CULL_FACE,cull); setCapability(GL11.GL_BLEND,blend);
            GlStateManager._blendFuncSeparate(srcRgb,dstRgb,srcAlpha,dstAlpha);
            RenderSystem.setShader(() -> previousShader);
            GlStateManager._activeTexture(GL13.GL_TEXTURE0); GlStateManager._bindTexture(texture0);
            GlStateManager._activeTexture(activeTexture);
        }
    }
    private static void vertex(BufferBuilder b, Matrix4f m,float x,float y,float z,float u,float v) {
        b.addVertex(m,x,y,z).setUv(u,v).setColor(255,255,255,255);
    }

    private static void setCapability(int capability, boolean enabled) {
        if (enabled) {
            if (capability==GL11.GL_DEPTH_TEST) RenderSystem.enableDepthTest();
            else if (capability==GL11.GL_CULL_FACE) RenderSystem.enableCull();
            else RenderSystem.enableBlend();
        } else {
            if (capability==GL11.GL_DEPTH_TEST) RenderSystem.disableDepthTest();
            else if (capability==GL11.GL_CULL_FACE) RenderSystem.disableCull();
            else RenderSystem.disableBlend();
        }
    }

    private static List<Anchor> renderSources(Minecraft mc) {
        // Live entities use SingularityRenderer; this listener draws only local preview anchors.
        return List.copyOf(ANCHORS);
    }

    private static List<Anchor> liveSources(Minecraft mc) {
        return liveSources(mc, mc.getTimer().getGameTimeDeltaPartialTick(false));
    }

    private static List<Anchor> liveSources(Minecraft mc, float partialTick) {
        List<Anchor> result = new ArrayList<>();
        for (var entity : mc.level.getEntitiesOfClass(com.juicyslew.moonstation14.entities.SingularityEntity.class,
                mc.player.getBoundingBox().inflate(4096), e -> !e.isRemoved())) {
            var position=entity.getPosition(partialTick);
            result.add(new Anchor(entity.getUUID(), position.x, position.y, position.z, entity.singularityLevel()));
        }
        return result;
    }

    static <T> List<T> selectWarpSources(List<T> live, List<T> previews) {
        List<T> selected = new ArrayList<>(5);
        selected.addAll(live.stream().limit(5).toList());
        if (selected.size() < 5) selected.addAll(previews.stream().limit(5 - selected.size()).toList());
        return selected;
    }

    private record Anchor(UUID id,double x,double y,double z,int level) { }
    private static final class GameRendererAccess {
        static net.minecraft.client.renderer.ShaderInstance positionTexColorShader() { return net.minecraft.client.renderer.GameRenderer.getPositionTexColorShader(); }
    }
}

