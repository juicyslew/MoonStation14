package com.juicyslew.moonstation14.ms14.atmos;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.net.URLClassLoader;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AtmosAmbientCodecInitializationTest {
    private static final String VENT = "com.juicyslew.moonstation14.ms14.atmos.piping.prototype.VentilationDeviceData";
    private static final String MACHINE = "com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile";
    private static final String PROFILE_CODEC_HELPERS = "com.juicyslew.moonstation14.ms14.atmos.machine.ProfileCodecHelpers";
    private static final String AUDIO = "com.juicyslew.moonstation14.ms14.atmos.audio.AtmosAmbientAudio";

    @Test void codecsInitializeAndDecodeWhenVentilationLoadsFirst() throws Exception {
        assertInitializationOrder(VENT, MACHINE);
    }

    @Test void codecsInitializeAndDecodeWhenAtmosMachineLoadsFirst() throws Exception {
        assertInitializationOrder(MACHINE, VENT);
    }

    private static void assertInitializationOrder(String first, String second) throws Exception {
        URL classes = com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile.class
                .getProtectionDomain().getCodeSource().getLocation();
        try (var loader = new AffectedTypesIsolatedClassLoader(classes,
                AtmosAmbientCodecInitializationTest.class.getClassLoader())) {
            assertNotNull(codec(loader, first));
            assertNotNull(codec(loader, second));
            Codec<Object> audioCodec = codec(loader, AUDIO);
            Object audio = audioCodec.parse(JsonOps.INSTANCE,
                    com.google.gson.JsonParser.parseString("{\"sound\":\"moonstation14:ss14/atmospherics/gas_vent\",\"volumeDb\":-12,\"range\":5,\"gate\":\"enabled_powered\"}"))
                    .getOrThrow();
            assertEquals("moonstation14:ss14/atmospherics/gas_vent", audio.getClass().getMethod("sound").invoke(audio));
            assertEquals(-12f, audio.getClass().getMethod("volumeDb").invoke(audio));
            assertEquals(5f, audio.getClass().getMethod("range").invoke(audio));
            assertEquals("ENABLED_POWERED", audio.getClass().getMethod("gate").invoke(audio).toString());
        }
    }

    @SuppressWarnings("unchecked")
    private static Codec<Object> codec(ClassLoader loader, String className) throws Exception {
        Class<?> type = Class.forName(className, true, loader);
        return (Codec<Object>) type.getField("CODEC").get(null);
    }

    /** Child-load only the three codec owners so each order gets genuinely fresh static fields. */
    private static final class AffectedTypesIsolatedClassLoader extends URLClassLoader {
        AffectedTypesIsolatedClassLoader(URL classes, ClassLoader parent) { super(new URL[]{classes}, parent); }

        @Override protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith(VENT) || name.startsWith(MACHINE) || name.equals(PROFILE_CODEC_HELPERS) || name.startsWith(AUDIO)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    try { loaded = findClass(name); }
                    catch (ClassNotFoundException absent) { loaded = super.loadClass(name, false); }
                }
                if (resolve) resolveClass(loaded);
                return loaded;
            }
            return super.loadClass(name, resolve);
        }
    }
}
