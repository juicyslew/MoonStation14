package com.juicyslew.moonstation14.mixinvalidation;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SableOwnedStepProbeMixinStructureTest {
    @Test
    void onlyFirstTryStepUpSetIsWrappedAndOnlyInsideOwnedCharacterScope() throws Exception {
        Path root = Path.of(System.getProperty("user.dir"));
        while (root != null && !Files.exists(root.resolve("src/main/java/com/juicyslew/moonstation14/mixin/compat/"
                + "SableOwnedNativeCollisionTraceMixin.java"))) root = root.getParent();
        assertNotNull(root, "project source tree is available to source contract test");
        Path mixin = root.resolve("src/main/java/com/juicyslew/moonstation14/mixin/compat/"
                + "SableOwnedNativeCollisionTraceMixin.java");
        Path sdk = root.resolve("build/shuttle-sdk-source-06/SubLevelEntityCollision.java");
        assertTrue(Files.isRegularFile(mixin), "mixin source exists");
        assertTrue(Files.isRegularFile(sdk), "pinned SDK source snapshot exists");
        String source = Files.readString(mixin);
        String sdkSource = Files.readString(sdk);

        assertTrue(source.contains("@WrapOperation(method = \"tryStepUp\""));
        assertTrue(source.contains("Vector3d;set(Lorg/joml/Vector3dc;)Lorg/joml/Vector3d;\", ordinal = 0)"),
                "the wrapper selects only boundsCenter.set(entityBoundsCenter), not lastStepTestMTV.set(sink.mtv)");
        assertTrue(source.contains("if (CurrentFrameCollisionScope.adaptsOwnedCharacterStepProbe(entity))"),
                "non-owned entities preserve stock SDK probe behavior");
        int original = source.indexOf("Operation<Vector3d> original, @Local(argsOnly = true) Entity entity");
        int originalCall = source.indexOf("Vector3d result = original.call(receiver, entityBoundsCenter)", original);
        int gate = source.indexOf("if (CurrentFrameCollisionScope.adaptsOwnedCharacterStepProbe(entity))", originalCall);
        int adaptation = source.indexOf("SableOwnedStepProbe", gate);
        assertTrue(original >= 0 && originalCall > original && gate > originalCall && adaptation > gate,
                "stock candidate is returned unchanged outside the exact owned-character scope");

        int first = sdkSource.indexOf("boundsCenter.set(entityBoundsCenter)");
        int second = sdkSource.indexOf("lastStepTestMTV.set(sink.mtv)");
        assertTrue(first >= 0 && second > first, "pinned SDK method order matches wrapped ordinal");
        String stepMethod = sdkSource.substring(sdkSource.indexOf("private static boolean tryStepUp"),
                sdkSource.indexOf("private static boolean hasCollision"));
        assertEquals(1, count(stepMethod, "boundsCenter.set(entityBoundsCenter)"));
        assertEquals(1, count(stepMethod, "lastStepTestMTV.set(sink.mtv)"),
                "the other Vector3d.set is the later upward-MTV sample and must remain stock");
        assertTrue(stepMethod.contains("boundsCenter.set(entityBoundsCenter).fma(currentStepUp")
                        && stepMethod.contains("collisionMotion.fma(currentStepUp"),
                "candidate and solver response share the same SDK step increments");
    }

    private static int count(String value, String needle) {
        int result = 0, index = 0;
        while ((index = value.indexOf(needle, index)) >= 0) { result++; index += needle.length(); }
        return result;
    }
}
