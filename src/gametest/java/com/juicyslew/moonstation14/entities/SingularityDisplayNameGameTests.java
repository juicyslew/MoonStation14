package com.juicyslew.moonstation14.entities;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Verifies the registered entity type name consumed by Shift-look previews. */
@GameTestTags("singularity-display-name")
@PrefixGameTestTemplate(false)
public final class SingularityDisplayNameGameTests {
    private SingularityDisplayNameGameTests() { }

    @GameTest(template="empty", timeoutTicks=10, templateNamespace="moonstation14")
    public static void registeredEntityUsesLocalizedNameKey(GameTestHelper helper) {
        var entity = ModEntities.SINGULARITY.get().create(helper.getLevel());
        if (entity == null) throw new GameTestAssertException("registered singularity entity creates");

        String key = ModEntities.SINGULARITY.get().getDescriptionId();
        if (!"entity.moonstation14.singularity".equals(key))
            throw new GameTestAssertException("registered singularity description key is localized: " + key);
        if (!(entity.getName().getContents() instanceof TranslatableContents name)
                || !key.equals(name.getKey()))
            throw new GameTestAssertException("Entity#getName uses registered type description key");
        entity.discard();
        helper.succeed();
    }
}
