package com.juicyslew.moonstation14.mixinvalidation;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class LivingEntityProneRenderMixinSignatureTest {
    @Test
    void postureAndKnockdownInjectionMatchesMappedSetupRotationsParameters() throws NoSuchMethodException, IOException {
        Method target = Arrays.stream(LivingEntityRenderer.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("setupRotations"))
                .findFirst()
                .orElseThrow();
        Class<?>[] expected = Arrays.copyOf(target.getParameterTypes(), target.getParameterCount() + 1);
        expected[expected.length - 1] = CallbackInfo.class;

        String expectedDescriptor = "(" + Arrays.stream(expected)
                .map(type -> org.objectweb.asm.Type.getDescriptor(type)).reduce("", String::concat) + ")V";
        String resource = "/com/juicyslew/moonstation14/mixin/client/LivingEntityProneRenderMixin.class";
        try (var input = getClass().getResourceAsStream(resource)) {
            if (input == null) throw new AssertionError("Compiled mixin class is missing");
            String[] actualDescriptor = {null};
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    if (name.equals("moonstation14$layDownedBody")) actualDescriptor[0] = descriptor;
                    return null;
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            assertArrayEquals(new String[]{expectedDescriptor}, new String[]{actualDescriptor[0]});
        }
    }
}
