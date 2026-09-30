package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.activity.EntityActivity;
import com.juicyslew.moonstation14.ms14.activity.EntityActivitySystem;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.monster.Zombie;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.util.UUID;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StomachJoinGameTests {
    private static final ResourceKey<ReagentData> WATER = ResourceKey.create(
            ModReagents.REAGENT_REGISTRY_KEY, ResourceLocation.fromNamespaceAndPath("moonstation14", "water"));

    private StomachJoinGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void unknownHostRetainsStomachButDoesNotScheduleDigestion(GameTestHelper helper) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        ReagentAttachment saved = populatedStomach();
        zombie.setData(ModDataAttachments.STOMACH.get(), saved);
        EntityActivitySystem.update(zombie, EntityActivity.REAGENT_METABOLISM, true);
        EntityActivitySystem.reconcile(zombie);
        require(zombie.getExistingDataOrNull(ModDataAttachments.STOMACH.get()) == saved,
                "unknown host must retain saved stomach contents");
        var activities = zombie.getExistingDataOrNull(ModDataAttachments.ACTIVE_SYSTEMS.get());
        require(activities == null || !activities.isActive(EntityActivity.REAGENT_METABOLISM),
                "unknown host stomach must not schedule digestion");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void stomachReconcilesAfterJoinAndCodecReload(GameTestHelper helper) {
        require(helper.getLevel() instanceof ServerLevel && !helper.getLevel().isClientSide,
                "test actors must be server-side");
        Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        ReagentAttachment stomach = populatedStomach();
        villager.setData(ModDataAttachments.STOMACH.get(), stomach);
        villager.removeData(ModDataAttachments.ACTIVE_SYSTEMS.get());

        FakePlayer player = new FakePlayer(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "stomach-rejoin-test"));
        player.setPos(3.5, 1, 1.5);
        player.setData(ModDataAttachments.STOMACH.get(), populatedStomach());
        require(helper.getLevel().addFreshEntity(player), "server fake player must join");
        requireActive(player, "real entity-join processing must schedule populated stomach metabolism");

        // Exercise production reconciliation directly after losing all derived flags.
        player.removeData(ModDataAttachments.ACTIVE_SYSTEMS.get());
        EntityActivitySystem.reconcile(player);
        requireActive(player, "reconcile must restore metabolism for persisted stomach contents");

        var encoded = ReagentAttachment.CODEC.encodeStart(JsonOps.INSTANCE, stomach).getOrThrow();
        ReagentAttachment reloaded = ReagentAttachment.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        require(reloaded.equals(stomach), "save/load codec roundtrip must preserve stomach contents");
        villager.setData(ModDataAttachments.STOMACH.get(), reloaded);
        villager.removeData(ModDataAttachments.ACTIVE_SYSTEMS.get());
        EntityActivitySystem.reconcile(villager);
        requireActive(villager, "reloaded stomach must restore metabolism without a missing binding");

        ReagentAttachment empty = new ReagentAttachment();
        villager.setData(ModDataAttachments.STOMACH.get(), empty);
        villager.removeData(ModDataAttachments.ACTIVE_SYSTEMS.get());
        EntityActivitySystem.reconcile(villager);
        // A mapped human also owns an independently scheduled BLOODSTREAM, so the
        // shared metabolism activity can remain enabled with an empty stomach.
        require(!MS14Bridges.STOMACH.activityBinding().orElseThrow().needsTicking().test(empty),
                "present but empty stomach must not contribute a metabolism schedule");
        helper.succeed();
    }

    private static ReagentAttachment populatedStomach() {
        return new ReagentAttachment(Map.of(WATER, 1f));
    }

    private static void requireActive(net.minecraft.world.entity.LivingEntity entity, String message) {
        require(entity.hasData(ModDataAttachments.ACTIVE_SYSTEMS.get())
                        && entity.getData(ModDataAttachments.ACTIVE_SYSTEMS.get())
                        .isActive(EntityActivity.REAGENT_METABOLISM), message);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
