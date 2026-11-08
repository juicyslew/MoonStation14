package com.juicyslew.moonstation14.ms14.mobs;

import com.juicyslew.moonstation14.entities.AnimalMob;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native entity eye anchors must not alter collision dimensions. */
@PrefixGameTestTemplate(false)
@GameTestTags({"native-animal-presentation", "native-creature-combat"})
public final class AnimalPresentationAnchorGameTests {
    private AnimalPresentationAnchorGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 10)
    public static void koboldEyeAnchor(GameTestHelper helper) {
        verify(helper, "moonstation14:kobold", 1.19, 1.0, 0.7);
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 10)
    public static void monkeyEyeAnchor(GameTestHelper helper) {
        verify(helper, "moonstation14:monkey", 1.06, 0.9, 0.7);
    }

    private static void verify(GameTestHelper helper, String id, double eye, double height, double width) {
        var type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(id));
        var entity = type.create(helper.getLevel());
        if (!(entity instanceof AnimalMob animal)) throw new GameTestAssertException(id + " is not a native AnimalMob");
        var pos = helper.absolutePos(new net.minecraft.core.BlockPos(2, 1, 2));
        animal.setPos(pos.getX(), pos.getY(), pos.getZ());
        if (!helper.getLevel().addFreshEntity(animal)) {
            animal.discard();
            throw new GameTestAssertException("could not join " + id);
        }
        Runnable verify = () -> {
            try {
                check(Math.abs(animal.getEyeHeight() - eye), id + " cached eye height");
                check(Math.abs(animal.getEyeY() - animal.getY() - eye), id + " cached eye Y");
                check(Math.abs(animal.getEyeHeight(animal.getPose()) - eye), id + " pose eye height");
                check(Math.abs(animal.getDimensions(animal.getPose()).eyeHeight() - eye), id + " dimensions eye height");
                check(Math.abs(animal.getBbHeight() - height), id + " collider height");
                check(Math.abs(animal.getBbWidth() - width), id + " collider width");
            } catch (GameTestAssertException failure) {
                animal.discard();
                throw new GameTestAssertException(failure.getMessage() + "; default=" + animal.getDefaultDimensions(animal.getPose()).eyeHeight() + ", dimensions=" + animal.getDimensions(animal.getPose()).eyeHeight() + ", cached=" + animal.getEyeHeight() + ", ticks=" + animal.tickCount + ", appearance=" + animal.appearanceKey());
            }
        };
        verify.run();
        helper.runAfterDelay(2, () -> { verify.run(); animal.discard(); helper.succeed(); });
    }

    private static void check(double error, String label) {
        if (error > 1.0e-6) throw new GameTestAssertException(label + " mismatch: error=" + error);
    }
}
