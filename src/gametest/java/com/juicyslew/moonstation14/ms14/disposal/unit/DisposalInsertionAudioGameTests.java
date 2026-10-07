package com.juicyslew.moonstation14.ms14.disposal.unit;

import com.juicyslew.moonstation14.MoonStation14;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@com.juicyslew.moonstation14.gametest.support.GameTestTags({"disposal", "audio"})
@PrefixGameTestTemplate(false)
public final class DisposalInsertionAudioGameTests {
    private DisposalInsertionAudioGameTests() { }

    @GameTest(templateNamespace = "moonstation14", batch = "disposals", template = "disposal_clear", timeoutTicks = 20)
    public static void successfulBodyAdmissionPlaysResolvedInsertionSoundOnlyOnce(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(3, 2, 3));
        level.setBlock(pos, com.juicyslew.moonstation14.block.ModBlocks.DISPOSAL_UNIT.get().defaultBlockState(), 3);
        var unit = (DisposalUnitBlockEntity) level.getBlockEntity(pos);
        require(unit != null && unit.prototype() != null, "installed unit has resolved prototype");
        unit.setGameTestPowerOverride(false);

        var sound = new ArrayList<ResourceLocation>();
        Consumer<PlayLevelSoundEvent.AtPosition> listener = event -> {
            if (event.getLevel() == level && event.getPosition().distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < 1.0e-6) {
                event.getSound().unwrapKey().map(key -> key.location()).ifPresent(sound::add);
            }
        };
        NeoForge.EVENT_BUS.addListener(listener);
        try {
            var body = helper.spawn(net.minecraft.world.entity.EntityType.VILLAGER, new BlockPos(3, 2, 3));
            require(unit.insertBody(body), "valid body admission succeeds");
            require(!unit.insertBody(body), "duplicate body admission is rejected");
            ResourceLocation configured = ResourceLocation.parse(unit.prototype().insertionSound());
            require(sound.equals(List.of(configured)), "only successful admission emits exactly the prototype insertion sound; observed " + sound);
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }
        helper.succeed();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
