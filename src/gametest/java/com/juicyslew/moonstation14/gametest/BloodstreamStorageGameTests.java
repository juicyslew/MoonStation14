package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.TraitHandler;
import com.juicyslew.moonstation14.ms14.blood.BloodComponent;
import com.juicyslew.moonstation14.ms14.blood.BloodSystem;
import com.juicyslew.moonstation14.ms14.blood.BloodstreamStorage;
import com.juicyslew.moonstation14.ms14.reagent.IReagentTrait;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import com.juicyslew.moonstation14.ms14.reagent.ReagentSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

import java.util.Map;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BloodstreamStorageGameTests {
    private static final ResourceKey<ReagentData> WATER = ResourceKey.create(
            com.juicyslew.moonstation14.ms14.reagent.ModReagents.REAGENT_REGISTRY_KEY,
            ResourceLocation.fromNamespaceAndPath("moonstation14", "water"));

    private BloodstreamStorageGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void legacyLivingReagentsMoveExactlyOnceToBloodstream(GameTestHelper helper) {
        Villager living = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        // Model a pre-bloodstream save rather than colliding with join-time reference blood.
        living.removeData(ModDataAttachments.BLOODSTREAM.get());
        living.removeData(ModDataAttachments.BLOOD.get());
        ReagentAttachment legacy = new ReagentAttachment(Map.of(WATER, 2.35f));
        living.setData(ModDataAttachments.REAGENT.get(), legacy);

        require(BloodstreamStorage.reconcile(living), "single legacy store should migrate");
        require(!living.hasData(ModDataAttachments.REAGENT.get()), "legacy generic attachment must be removed");
        require(living.getData(ModDataAttachments.BLOODSTREAM.get()).equals(legacy),
                "all legacy reagent cents must be preserved in bloodstream");
        require(BloodstreamStorage.reconcile(living), "repeat reconciliation must be idempotent");
        require(living.getData(ModDataAttachments.BLOODSTREAM.get()).equals(legacy),
                "repeat reconciliation must not duplicate or alter contents");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void populatedLegacyOverEmptyUninitializedStreamMigratesExactCents(GameTestHelper helper) {
        for (boolean markerPresent : new boolean[]{false, true}) {
            Villager living = helper.spawn(EntityType.VILLAGER,
                    new BlockPos(markerPresent ? 2 : 1, 1, 1));
            living.setNoAi(true);
            living.setData(ModDataAttachments.BLOODSTREAM.get(), new ReagentAttachment());
            if (markerPresent) living.setData(ModDataAttachments.BLOOD.get(), new BloodComponent(0, false).toAttachment());
            else living.removeData(ModDataAttachments.BLOOD.get());
            ReagentAttachment legacy = new ReagentAttachment(Map.of(WATER, 2.35f));
            living.setData(ModDataAttachments.REAGENT.get(), legacy);

            require(BloodSystem.reconcile(living), "uninitialized empty stream must adopt legacy material");
            require(!living.hasData(ModDataAttachments.REAGENT.get()), "legacy store must be removed after copy");
            require(living.getData(ModDataAttachments.BLOODSTREAM.get()).snapshotUnits().equals(legacy.snapshotUnits()),
                    "migration must retain exact legacy cents without adding reference blood");
            require(BloodSystem.reconcile(living), "migration retry must succeed");
            require(living.getData(ModDataAttachments.BLOODSTREAM.get()).snapshotUnits().equals(legacy.snapshotUnits()),
                    "migration retry must not refill or duplicate the mixture");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void initializedEmptyStreamBesideLegacyFailsClosed(GameTestHelper helper) {
        Villager living = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        living.setNoAi(true);
        ReagentAttachment legacy = new ReagentAttachment(Map.of(WATER, 2.35f));
        living.setData(ModDataAttachments.BLOODSTREAM.get(), new ReagentAttachment());
        living.setData(ModDataAttachments.BLOOD.get(), new BloodComponent(0, true).toAttachment());
        living.setData(ModDataAttachments.REAGENT.get(), legacy);

        for (int retry = 0; retry < 2; retry++) {
            require(!BloodstreamStorage.reconcile(living), "ambiguous depleted stream must fail closed");
            require(!BloodSystem.reconcile(living), "blood initialization must not bypass the conflict");
            require(living.hasData(ModDataAttachments.REAGENT.get())
                            && living.getData(ModDataAttachments.REAGENT.get()).snapshotUnits().equals(legacy.snapshotUnits()),
                    "legacy material must remain untouched");
            require(living.hasData(ModDataAttachments.BLOODSTREAM.get())
                            && living.getData(ModDataAttachments.BLOODSTREAM.get()).isEmpty(),
                    "depleted bloodstream must remain empty and present");
            require(living.getData(ModDataAttachments.BLOOD.get()).initialized(),
                    "initialized marker must remain unchanged");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void conflictingPopulatedStoresArePreservedAndGenericRouteFailsClosed(GameTestHelper helper) {
        Villager living = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        ReagentAttachment legacy = new ReagentAttachment(Map.of(WATER, 2f));
        ReagentAttachment bloodstream = new ReagentAttachment(Map.of(WATER, 3f));
        living.setData(ModDataAttachments.REAGENT.get(), legacy);
        living.setData(ModDataAttachments.BLOODSTREAM.get(), bloodstream);

        require(!BloodstreamStorage.reconcile(living), "two populated stores must fail closed");
        require(living.getData(ModDataAttachments.REAGENT.get()).equals(legacy), "legacy conflict data must remain");
        require(living.getData(ModDataAttachments.BLOODSTREAM.get()).equals(bloodstream),
                "bloodstream conflict data must remain");

        ItemStack bottle = new ItemStack(ModItems.BOTTLE.get());
        bottle.set(ModDataComponents.REAGENT.get(), new ReagentComponent(Map.of(WATER, 4f)));
        TraitHandler<IReagentTrait> livingHandle = new TraitHandler<>(living, () -> 100f);
        ReagentSystem.handleTransfer(ModItems.BOTTLE.get().toHandle(bottle), livingHandle,
                (ServerLevel) helper.getLevel(), 1f);
        require(bottle.get(ModDataComponents.REAGENT.get()).contents().equals(Map.of(WATER, 4f)),
                "conflicted living destination must not consume the ordinary source");
        require(living.getData(ModDataAttachments.REAGENT.get()).equals(legacy)
                        && living.getData(ModDataAttachments.BLOODSTREAM.get()).equals(bloodstream),
                "failed generic routing must preserve both living stores");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void identicalPopulatedStoresAreRecognizedAsInterruptedMigration(GameTestHelper helper) {
        Villager living = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        ReagentAttachment duplicate = new ReagentAttachment(Map.of(WATER, 2.35f));
        living.setData(ModDataAttachments.REAGENT.get(), duplicate);
        living.setData(ModDataAttachments.BLOODSTREAM.get(), new ReagentAttachment(Map.of(WATER, 2.35f)));

        require(BloodstreamStorage.reconcile(living), "exact duplicate must finish interrupted migration");
        require(!living.hasData(ModDataAttachments.REAGENT.get()), "duplicate legacy store must be removed");
        require(living.getData(ModDataAttachments.BLOODSTREAM.get()).snapshotUnits().equals(duplicate.snapshotUnits()),
                "deduplication must preserve exact bloodstream cents");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void livingWithoutPrototypeBloodPolicyIsNotAGenericReagentContainer(GameTestHelper helper) {
        Zombie living = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        ItemStack bottle = new ItemStack(ModItems.BOTTLE.get());
        bottle.set(ModDataComponents.REAGENT.get(), new ReagentComponent(Map.of(WATER, 4f)));
        TraitHandler<IReagentTrait> livingHandle = new TraitHandler<>(living, () -> 100f);

        ReagentSystem.handleTransfer(ModItems.BOTTLE.get().toHandle(bottle), livingHandle,
                (ServerLevel) helper.getLevel(), 1f);
        require(bottle.get(ModDataComponents.REAGENT.get()).contents().equals(Map.of(WATER, 4f)),
                "unconfigured living destination must reject transfer without consuming source");
        require(!living.hasData(ModDataAttachments.REAGENT.get())
                        && !living.hasData(ModDataAttachments.BLOODSTREAM.get()),
                "rejected transfer must not materialize either solution store");
        living.setData(ModDataAttachments.REAGENT.get(), new ReagentAttachment(Map.of(WATER, 2f)));
        require(!BloodstreamStorage.reconcile(living)
                        && living.getData(ModDataAttachments.REAGENT.get()).getMap().equals(Map.of(WATER, 2f))
                        && !living.hasData(ModDataAttachments.BLOODSTREAM.get()),
                "missing prototype policy must preserve legacy data without migrating or creating blood");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void livingBloodstreamCannotBeSpilledAsGenericReagent(GameTestHelper helper) {
        Villager living = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        TraitHandler<IReagentTrait> livingHandle = new TraitHandler<>(living, () -> 100f);
        ReagentAttachment before = living.getData(ModDataAttachments.BLOODSTREAM.get());

        InteractionResult result = ReagentSystem.handleSpill(livingHandle, (ServerLevel) helper.getLevel(),
                new BlockPos(2, 1, 1), 1f);
        require(result == InteractionResult.PASS, "living source must reject the generic spill route");
        require(!living.hasData(ModDataAttachments.REAGENT.get()),
                "rejected living spill must not materialize a generic REAGENT attachment");
        require(living.getData(ModDataAttachments.BLOODSTREAM.get()).equals(before),
                "rejected living spill must preserve the bloodstream");
        helper.succeed();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
