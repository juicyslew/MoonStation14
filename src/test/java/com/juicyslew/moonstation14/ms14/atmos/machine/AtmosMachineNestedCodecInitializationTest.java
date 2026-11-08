package com.juicyslew.moonstation14.ms14.atmos.machine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.net.URLClassLoader;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class AtmosMachineNestedCodecInitializationTest {
    private static final String MACHINE = "com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile";

    @Test void portCodecCanInitializeFirst() throws Exception { assertNestedCodec("$Port", "{\"name\":\"inlet\",\"direction\":\"north\"}"); }
    @Test void filterCodecCanInitializeFirst() throws Exception { assertNestedCodec("$Filter", "{\"gas\":\"oxygen\",\"transferRate\":1,\"maxTransferRate\":2}"); }

    @SuppressWarnings("unchecked")
    private static void assertNestedCodec(String nested, String json) throws Exception {
        URL classes = AtmosMachineProfile.class.getProtectionDomain().getCodeSource().getLocation();
        try (var loader = new URLClassLoader(new URL[]{classes}, AtmosMachineNestedCodecInitializationTest.class.getClassLoader()) {
            @Override protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith(MACHINE) || name.equals("com.juicyslew.moonstation14.ms14.atmos.machine.ProfileCodecHelpers")) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null) try { loaded = findClass(name); } catch (ClassNotFoundException absent) { loaded = super.loadClass(name, false); }
                    if (resolve) resolveClass(loaded);
                    return loaded;
                }
                return super.loadClass(name, resolve);
            }
        }) {
            Class<?> type = Class.forName(MACHINE + nested, true, loader);
            Codec<Object> codec = (Codec<Object>) type.getField("CODEC").get(null);
            assertNotNull(codec.parse(JsonOps.INSTANCE, com.google.gson.JsonParser.parseString(json)).getOrThrow());
        }
    }
}
