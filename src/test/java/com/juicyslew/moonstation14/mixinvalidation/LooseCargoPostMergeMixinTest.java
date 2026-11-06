package com.juicyslew.moonstation14.mixinvalidation;

import com.juicyslew.moonstation14.mixin.client.SableRemotePlotProjectionPlugin;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LooseCargoPostMergeMixinTest {
    private static final String DESC = "(Lnet/minecraft/world/entity/Entity;DDDFFIZZ)V";
    private static final String LISTENER = "net/minecraft/client/multiplayer/ClientPacketListener";
    private static final String SDK =
            "dev.ryanhcode.sable.mixin.entity.entities_stick_sublevels.ClientPacketListenerMixin";
    private static final String MARKER = SableRemotePlotProjectionPlugin.LOOSE_CARGO_PACKET_MIXIN;

    @Test void insertsOnlyMetadataCallbackAtHeadWithRawPacketSlots() {
        ClassNode target = target();
        MethodNode helper = helper("$unique$sable$lerp", SDK);
        AbstractInsnNode sdkInstruction = new InsnNode(Opcodes.RETURN);
        helper.instructions.add(sdkInstruction);
        target.methods.add(helper);

        new SableRemotePlotProjectionPlugin().postApply("net.minecraft.client.multiplayer.ClientPacketListener",
                target, MARKER, null);

        assertSame(sdkInstruction, helper.instructions.getLast(), "SDK body remains intact after injected prefix");
        assertEquals(List.of(Opcodes.ALOAD, Opcodes.DLOAD, Opcodes.DLOAD, Opcodes.DLOAD, Opcodes.ILOAD),
                opcodesBeforeCallback(helper));
        MethodInsnNode callback = callback(helper);
        assertEquals(Opcodes.INVOKESTATIC, callback.getOpcode());
        assertEquals("com/juicyslew/moonstation14/entities/LooseItemEntity", callback.owner);
        assertEquals("recordClientPacketFrame", callback.name);
        assertEquals("(Lnet/minecraft/world/entity/Entity;DDDZ)V", callback.desc);
        assertEquals(12, ((VarInsnNode) callback.getPrevious()).var);
    }

    @Test void rejectsWrongMissingDuplicateOrNonSdkMergedHelpers() {
        var plugin = new SableRemotePlotProjectionPlugin();
        ClassNode wrongSource = target();
        wrongSource.methods.add(helper("sable$lerp", "other.Mixin"));
        assertThrows(IllegalStateException.class, () -> plugin.postApply(
                "net.minecraft.client.multiplayer.ClientPacketListener", wrongSource, MARKER, null));

        ClassNode missing = target();
        assertThrows(IllegalStateException.class, () -> plugin.postApply(
                "net.minecraft.client.multiplayer.ClientPacketListener", missing, MARKER, null));

        ClassNode wrongDescriptor = target();
        MethodNode wrong = helper("sable$lerp", SDK);
        wrong.desc = "(Lnet/minecraft/world/entity/Entity;DDDFFIZZ)Z";
        wrongDescriptor.methods.add(wrong);
        assertThrows(IllegalStateException.class, () -> plugin.postApply(
                "net.minecraft.client.multiplayer.ClientPacketListener", wrongDescriptor, MARKER, null));

        ClassNode duplicate = target();
        duplicate.methods.add(helper("sable$lerp", SDK));
        duplicate.methods.add(helper("$unique$sable$lerp", SDK));
        assertThrows(IllegalStateException.class, () -> plugin.postApply(
                "net.minecraft.client.multiplayer.ClientPacketListener", duplicate, MARKER, null));

        ClassNode wrongTarget = target();
        wrongTarget.name = "net/minecraft/client/multiplayer/OtherListener";
        assertThrows(IllegalStateException.class, () -> plugin.postApply(
                "net.minecraft.client.multiplayer.OtherListener", wrongTarget, MARKER, null));
    }

    private static ClassNode target() {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        node.name = LISTENER;
        return node;
    }

    private static MethodNode helper(String name, String source) {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PRIVATE, name, DESC, null, null);
        method.visibleAnnotations = new ArrayList<>();
        AnnotationNode merged = new AnnotationNode(
                "Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;");
        merged.values = new ArrayList<>(List.of("mixin", source, "priority", 1000));
        method.visibleAnnotations.add(merged);
        return method;
    }

    private static MethodInsnNode callback(MethodNode method) {
        return java.util.Arrays.stream(method.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .filter(call -> call.name.equals("recordClientPacketFrame"))
                .findFirst().orElseThrow();
    }

    private static List<Integer> opcodesBeforeCallback(MethodNode method) {
        List<Integer> result = new java.util.ArrayList<>();
        for (AbstractInsnNode insn : method.instructions) {
            if (insn instanceof MethodInsnNode call && call.name.equals("recordClientPacketFrame")) break;
            if (insn instanceof VarInsnNode variable) result.add(variable.getOpcode());
        }
        return result;
    }
}
