package com.juicyslew.moonstation14.mixin.client;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;

/** Narrow post-merge guard for the pinned Sable remote plot writer in Entity.tick's injected handler. */
public final class SableRemotePlotProjectionPlugin implements IMixinConfigPlugin {
    public static final String OWNED_ENTITY_MIXIN =
            "com.juicyslew.moonstation14.mixin.client.SableOwnedRemoteFrameEntityMixin";
    public static final String SDK_ENTITY_MIXIN =
            "dev.ryanhcode.sable.mixin.entity.entities_stick_sublevels.EntityMixin";
    public static final String LOOSE_CARGO_PACKET_MIXIN =
            "com.juicyslew.moonstation14.mixin.client.LooseCargoPacketFrameMixin";
    public static final String SDK_PACKET_LISTENER_MIXIN =
            "dev.ryanhcode.sable.mixin.entity.entities_stick_sublevels.ClientPacketListenerMixin";
    public static final String LIGHTING_MIXIN =
            "com.juicyslew.moonstation14.mixin.client.SableEntityRendererLightingMixin";
    public static final String SDK_RENDERER_MIXIN =
            "dev.ryanhcode.sable.mixin.entity.entity_rendering.EntityRendererMixin";
    private static final String SDK_HANDLER_SUFFIX = "$sable$updateSubLevelPosition";
    private static final String SDK_PACKET_HANDLER_DESCRIPTOR =
            "(Lnet/minecraft/world/entity/Entity;DDDFFIZZ)V";
    private static final String CALLBACK_DESCRIPTOR =
            "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V";
    private static final String MERGED_ANNOTATION =
            "Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;";
    private static final String OWNERSHIP_METHOD =
            "com/juicyslew/moonstation14/ms14/player_body_control/client/GhostControlClient";
    private static final String STICK_EXTENSION =
            "dev/ryanhcode/sable/mixinterface/entity/entities_stick_sublevels/EntityStickExtension";
    private static final String ENTITY_DESCRIPTOR = "Lnet/minecraft/world/entity/Entity;";
    private static final String VEC3_DESCRIPTOR = "Lnet/minecraft/world/phys/Vec3;";
    private static final String CARGO_PACKET_CALLBACK =
            "com/juicyslew/moonstation14/entities/LooseItemEntity";
    private static final String LIGHT_BRIDGE_DESCRIPTOR =
            "(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/LightLayer;Lnet/minecraft/core/BlockPos;)I";
    private static final String LEVEL_GET_BRIGHTNESS = "getBrightness";
    private static final String LIGHTING_SCOPE =
            "com/juicyslew/moonstation14/ms14/shuttle/sable/PackedLightAccountingScope";

    @Override public void onLoad(String mixinPackage) { }
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) { return true; }
    @Override public void acceptTargets(java.util.Set<String> myTargets, java.util.Set<String> otherTargets) { }
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName,
                                   IMixinInfo mixinInfo) { }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName,
                          IMixinInfo mixinInfo) {
        if (LOOSE_CARGO_PACKET_MIXIN.equals(dotted(mixinClassName))) {
            injectLooseCargoPacketMetadata(targetClassName, targetClass);
            return;
        }
        if (LIGHTING_MIXIN.equals(dotted(mixinClassName))) {
            injectLightingRawFirstPass(targetClassName, targetClass);
            return;
        }
        if (!OWNED_ENTITY_MIXIN.equals(dotted(mixinClassName))) return;
        if (!"net.minecraft.world.entity.Entity".equals(dotted(targetClassName))
                || !"net/minecraft/world/entity/Entity".equals(targetClass.name))
            throw new IllegalStateException("Sable remote-plot guard applied to unexpected target " + targetClassName);

        MethodNode handler = null;
        int matches = 0;
        for (MethodNode method : targetClass.methods) {
            if (!method.name.endsWith(SDK_HANDLER_SUFFIX) || !method.desc.equals(CALLBACK_DESCRIPTOR)
                    || !mergedFromPinnedSableEntityMixin(method)) continue;
            handler = method;
            matches++;
        }
        if (matches != 1)
            throw new IllegalStateException("Expected exactly one merged pinned Sable remote plot handler; found " + matches);

        // The callback returns before either cached-position projection or the null-cache
        // tracking clear. Discard the packet cache while still inside this handler so the
        // SDK resumes normally when this exact body is no longer locally prediction-owned.
        LabelNode continueSdkHandler = new LabelNode();
        InsnList guard = new InsnList();
        guard.add(new VarInsnNode(Opcodes.ALOAD, 0));
        guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC, OWNERSHIP_METHOD,
                "ownsSableRemoteFrameInterpolation", "(" + ENTITY_DESCRIPTOR + ")Z", false));
        guard.add(new JumpInsnNode(Opcodes.IFEQ, continueSdkHandler));
        guard.add(new VarInsnNode(Opcodes.ALOAD, 0));
        guard.add(new TypeInsnNode(Opcodes.CHECKCAST, STICK_EXTENSION));
        guard.add(new InsnNode(Opcodes.ACONST_NULL));
        guard.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, STICK_EXTENSION,
                "sable$setPlotPosition", "(" + VEC3_DESCRIPTOR + ")V", true));
        guard.add(new InsnNode(Opcodes.RETURN));
        guard.add(continueSdkHandler);
        handler.instructions.insert(guard);
    }

    private static void injectLightingRawFirstPass(String targetClassName, ClassNode targetClass) {
        if (!"net.minecraft.client.renderer.entity.EntityRenderer".equals(dotted(targetClassName))
                || !"net/minecraft/client/renderer/entity/EntityRenderer".equals(targetClass.name))
            throw new IllegalStateException("Sable lighting scope applied to unexpected target " + targetClassName);
        int skyMatched = 0;
        int blockMatched = 0;
        for (MethodNode method : targetClass.methods) {
            boolean sky = method.name.contains("sable$getSkyLightLevel") || method.name.equals("getSkyLightLevel");
            boolean block = method.name.contains("sable$getBlockLightLevel") || method.name.equals("getBlockLightLevel");
            if (!sky && !block || !mergedFrom(method, SDK_RENDERER_MIXIN)) continue;
            if (!method.desc.equals(LIGHT_BRIDGE_DESCRIPTOR))
                throw new IllegalStateException("Pinned Sable lighting bridge descriptor changed: " + method.name + method.desc);
            if ((method.access & Opcodes.ACC_STATIC) != 0 || (method.access & Opcodes.ACC_PRIVATE) == 0)
                throw new IllegalStateException("Pinned Sable lighting bridge has unexpected access: " + method.name);
            boolean hasAccountedCall = false, hasCellCenter = false;
            for (AbstractInsnNode insn : method.instructions)
                if (insn instanceof MethodInsnNode call) {
                    if (call.name.equals(sky ? "sable$getSubLevelAccountedSkyLight" : "sable$getSubLevelAccountedBlockLight"))
                        hasAccountedCall = true;
                    if (call.owner.equals("dev/ryanhcode/sable/companion/math/JOMLConversion")
                            && call.name.equals("atCenterOf")) hasCellCenter = true;
                }
            if (!hasAccountedCall || !hasCellCenter)
                throw new IllegalStateException("Pinned Sable lighting bridge body no longer matches expected accounted-helper shape: " + method.name);
            if (sky) skyMatched++;
            else blockMatched++;
            LabelNode continueSdk = new LabelNode();
            InsnList guard = new InsnList();
            guard.add(new VarInsnNode(Opcodes.ALOAD, 0));
            guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC, LIGHTING_SCOPE, "activeForRenderer",
                    "(Ljava/lang/Object;)Z", false));
            guard.add(new JumpInsnNode(Opcodes.IFEQ, continueSdk));
            guard.add(new VarInsnNode(Opcodes.ALOAD, 1));
            guard.add(new VarInsnNode(Opcodes.ALOAD, 2));
            guard.add(new VarInsnNode(Opcodes.ALOAD, 3));
            guard.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/level/Level",
                    LEVEL_GET_BRIGHTNESS, "(Lnet/minecraft/world/level/LightLayer;Lnet/minecraft/core/BlockPos;)I", false));
            guard.add(new InsnNode(Opcodes.IRETURN));
            guard.add(continueSdk);
            method.instructions.insert(guard);
        }
        if (skyMatched != 1 || blockMatched != 1)
            throw new IllegalStateException("Expected exactly one merged pinned Sable sky and block light bridge; found sky="
                    + skyMatched + ", block=" + blockMatched);
    }

    private static void injectLooseCargoPacketMetadata(String targetClassName, ClassNode targetClass) {
        if (!"net.minecraft.client.multiplayer.ClientPacketListener".equals(dotted(targetClassName))
                || !"net/minecraft/client/multiplayer/ClientPacketListener".equals(targetClass.name))
            throw new IllegalStateException("Loose cargo packet metadata applied to unexpected target " + targetClassName);
        MethodNode helper = null;
        int matches = 0;
        for (MethodNode method : targetClass.methods) {
            boolean sableName = method.name.equals("sable$lerp") || method.name.endsWith("$sable$lerp")
                    || method.name.startsWith("sable$lerp$");
            if (!sableName || !method.desc.equals(SDK_PACKET_HANDLER_DESCRIPTOR)
                    || !mergedFrom(method, SDK_PACKET_LISTENER_MIXIN)) continue;
            helper = method;
            matches++;
        }
        if (matches != 1)
            throw new IllegalStateException("Expected exactly one merged pinned Sable packet lerp helper; found " + matches);

        InsnList callback = new InsnList();
        callback.add(new VarInsnNode(Opcodes.ALOAD, 1));
        callback.add(new VarInsnNode(Opcodes.DLOAD, 2));
        callback.add(new VarInsnNode(Opcodes.DLOAD, 4));
        callback.add(new VarInsnNode(Opcodes.DLOAD, 6));
        callback.add(new VarInsnNode(Opcodes.ILOAD, 12));
        callback.add(new MethodInsnNode(Opcodes.INVOKESTATIC, CARGO_PACKET_CALLBACK,
                "recordClientPacketFrame", "(Lnet/minecraft/world/entity/Entity;DDDZ)V", false));
        helper.instructions.insert(callback);
    }

    private static boolean mergedFromPinnedSableEntityMixin(MethodNode method) {
        return mergedFrom(method, SDK_ENTITY_MIXIN);
    }

    private static boolean mergedFrom(MethodNode method, String sourceMixin) {
        if (method.visibleAnnotations != null)
            for (var annotation : method.visibleAnnotations)
                if (isMergedFrom(annotation.desc, annotation.values, sourceMixin)) return true;
        if (method.invisibleAnnotations != null)
            for (var annotation : method.invisibleAnnotations)
                if (isMergedFrom(annotation.desc, annotation.values, sourceMixin)) return true;
        return false;
    }

    private static boolean isMergedFrom(String descriptor, List<Object> values, String sourceMixin) {
        if (!MERGED_ANNOTATION.equals(descriptor) || values == null) return false;
        for (int i = 0; i + 1 < values.size(); i += 2)
            if (values.get(i).equals("mixin") && sourceMixin.equals(dotted((String) values.get(i + 1))))
                return true;
        return false;
    }

    private static String dotted(String name) { return name == null ? "" : name.replace('/', '.'); }
}
