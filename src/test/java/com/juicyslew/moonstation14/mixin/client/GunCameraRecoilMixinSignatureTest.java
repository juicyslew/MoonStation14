package com.juicyslew.moonstation14.mixinvalidation;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GunCameraRecoilMixinSignatureTest {
    @Test
    void recoilInjectionMatchesMappedCameraSetupDescriptor() throws IOException {
        String jarPath = System.getProperty("moonstation14.mappedMinecraftJar");
        if (jarPath == null) throw new AssertionError("Mapped Minecraft jar system property is missing");
        String targetDescriptor = mappedCameraSetupDescriptor(jarPath);
        String handlerDescriptor = methodDescriptor("/com/juicyslew/moonstation14/mixin/client/GunCameraRecoilMixin.class", "moonstation14$cameraRecoil");
        String expected = "(" + java.util.Arrays.stream(org.objectweb.asm.Type.getArgumentTypes(targetDescriptor))
                .map(org.objectweb.asm.Type::getDescriptor).reduce("", String::concat)
                + "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V";
        assertEquals(expected, handlerDescriptor);
    }

    private String mappedCameraSetupDescriptor(String jarPath) throws IOException {
        try (ZipFile jar = new ZipFile(jarPath)) {
            String resource = "net/minecraft/client/Camera.class";
            ZipEntry entry = jar.getEntry(resource);
            if (entry == null) throw new AssertionError("Mapped Minecraft jar does not contain " + resource + ": " + jarPath);
            try (InputStream input = jar.getInputStream(entry)) {
                return methodDescriptor(input, resource, "setup");
            }
        }
    }

    private String methodDescriptor(String resource, String method) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(resource)) {
            if (input == null) throw new AssertionError("Class bytes missing from test classpath: " + resource);
            return methodDescriptor(input, resource, method);
        }
    }

    private String methodDescriptor(InputStream input, String resource, String method) throws IOException {
            String[] descriptor = {null};
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                    if (name.equals(method)) descriptor[0] = desc;
                    return null;
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            if (descriptor[0] == null) throw new AssertionError("Method missing: " + method + " in " + resource);
            return descriptor[0];
    }
}
