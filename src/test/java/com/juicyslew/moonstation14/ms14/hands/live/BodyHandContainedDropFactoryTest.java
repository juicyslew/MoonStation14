package com.juicyslew.moonstation14.ms14.hands.live;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Guards contained-body world drops against bypassing the canonical loose-item factory. */
class BodyHandContainedDropFactoryTest {
    @Test void containedOwnerDropUsesCanonicalProfileAwareEntityFactory() throws Exception {
        String resource = "/" + BodyHandItemTransfer.class.getName().replace('.', '/') + ".class";
        List<String> calls = new ArrayList<>();
        try (var input = BodyHandItemTransfer.class.getResourceAsStream(resource)) {
            assertNotNull(input);
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                           String signature, String[] exceptions) {
                    if (!name.equals("dropAtContainedOwner")) return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitMethodInsn(int opcode, String owner, String name,
                                                              String descriptor, boolean isInterface) {
                            calls.add(owner + "." + name);
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        assertTrue(calls.contains("com/juicyslew/moonstation14/ms14/hands/live/BodyHandItemTransfer.createTrustedDropEntity"),
                "contained world publication must select the canonical LooseItemEntity/profile factory: " + calls);
        assertFalse(calls.contains("net/minecraft/world/entity/item/ItemEntity.<init>"),
                "contained world publication must not construct a generic ItemEntity");
    }
}
