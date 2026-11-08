package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateQuery;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver;
import com.juicyslew.moonstation14.ms14.damage.ItemDamageState;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

@GameTestTags({"botany", "botany-item-damage", "loose-objects", "interaction"})
@PrefixGameTestTemplate(false)
public final class TomatoItemDamageGameTests {
    private static final ResourceLocation TOMATO = ResourceLocation.fromNamespaceAndPath("moonstation14", "tomato");

    private TomatoItemDamageGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 120)
    public static void ordinaryHarvestDropSurvivesGravityWithoutDamageOrReagentChange(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos floor = helper.absolutePos(new BlockPos(5, 1, 5));
        level.setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
        var stack = ProduceFactory.createHarvest(TOMATO, 10);
        var chemistry = stack.get(ModDataComponents.REAGENT.get());
        var genetics = stack.get(ModDataComponents.GENETIC_VALUE.get());
        var item = LooseItemEntity.create(level, new Vec3(floor.getX() + .5, floor.getY() + 4, floor.getZ() + .5), stack);
        require(level.addFreshEntity(item), "ordinary harvested tomato enters loose world");
        helper.runAfterDelay(90, () -> {
            try {
                require(!item.isRemoved() && item.getItem().is(ModItems.TOMATO.get()), "ordinary falling tomato remains an item");
                require(item.getItem().get(ModDataComponents.ITEM_DAMAGE_STATE.get()) == null,
                        "ordinary harvest/drop was not marked as an explicit throw and gains no item damage");
                require(item.getItem().get(ModDataComponents.REAGENT.get()).equals(chemistry), "gravity-only landing preserves exact food chemistry");
                require(java.util.Objects.equals(item.getItem().get(ModDataComponents.GENETIC_VALUE.get()), genetics),
                        "gravity-only landing preserves harvest genetics");
                require(level.getBlockState(floor.above()).isAir(), "ordinary drop creates no destruction puddle");
                helper.succeed();
            } catch (Throwable failure) { helper.fail("ordinary tomato drop: " + failure); }
        });
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 120)
    public static void ordinaryWatermelonDropSurvivesGravityWithoutDamageOrReagentChange(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos floor = helper.absolutePos(new BlockPos(5, 1, 5));
        level.setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
        var stack = ProduceFactory.createHarvest(ResourceLocation.fromNamespaceAndPath("moonstation14", "watermelon"), 10);
        var chemistry = stack.get(ModDataComponents.REAGENT.get());
        var item = LooseItemEntity.create(level, new Vec3(floor.getX() + .5, floor.getY() + 4, floor.getZ() + .5), stack);
        require(level.addFreshEntity(item), "ordinary harvested watermelon enters loose world");
        helper.runAfterDelay(90, () -> {
            try {
                require(!item.isRemoved() && item.getItem().is(ModItems.WATERMELON.get()), "ordinary falling watermelon remains an item");
                require(item.getItem().get(ModDataComponents.ITEM_DAMAGE_STATE.get()) == null,
                        "ordinary vertical watermelon drop is not an explicit throw and gains no impact damage");
                require(item.getItem().get(ModDataComponents.REAGENT.get()).equals(chemistry),
                        "gravity-only watermelon landing preserves exact food chemistry");
                require(level.getBlockState(floor.above()).isAir(), "ordinary watermelon drop creates no destruction puddle");
                helper.succeed();
            } catch (Throwable failure) { helper.fail("ordinary watermelon drop: " + failure); }
        });
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 240)
    @com.juicyslew.moonstation14.gametest.support.GameTestTags({"merge-main-remaining", "merge-main-stage-two"})
    public static void authenticatedThrowLandsDestroysTomatoAndSpillsOnlyCurrentChemistry(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(5, 1, 5));
        for (int x = 2; x <= 13; x++) for (int z = 2; z <= 8; z++)
            level.setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 3);
        var fixture = RegisteredServerPlayerFixture.register(level, "botany-tomato-damage-throw-v1");
        helper.runAtTickTime(2, () -> {
            try {
                var owner = RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                var body = owner.body();
                body.setPos(origin.getX() + .5, origin.getY() + 1, origin.getZ() + .5);
                var tomato = ProduceFactory.createHarvest(TOMATO, 10);
                var genetics = tomato.get(ModDataComponents.GENETIC_VALUE.get());
                var currentChemistry = ReagentComponent.fromCents(Map.of(
                        ModReagents.createKey("nutriment"), 150L,
                        ModReagents.createKey("vitamin"), 120L));
                tomato.set(ModDataComponents.REAGENT.get(), currentChemistry);
                var presetDamage = new ItemDamageState(Map.of("blunt", .5f), level.getGameTime() + 40);
                tomato.set(ModDataComponents.ITEM_DAMAGE_STATE.get(), presetDamage);
                var loose = LooseItemEntity.create(level, body.position().add(0, .4, 1.1), tomato);
                loose.setNoGravity(true);
                require(level.addFreshEntity(loose), "registered harvest enters ordinary loose-item world");
                String hand = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get()).activeHand();
                body.lookAt(EntityAnchorArgument.Anchor.EYES, loose.position());
                var hands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                require(BodyHandItemTransfer.pickupSelected(fixture.player(), loose.getUUID(), hand, hands.revision(), ignored -> { })
                                == BodyHandItemTransfer.Result.SUCCESS,
                        "real registered player picks up current tomato through existing transfer owner");
                BlockPos aimBlock = new BlockPos(origin.getX() + 7, origin.getY(), origin.getZ());
                // Throw aiming is body look direction, not the short interaction ray used by aimAt.
                body.lookAt(EntityAnchorArgument.Anchor.EYES, aimBlock.getCenter());
                var query = BodyHandStateService.query(fixture.player(), new BodyHandStateQuery(owner.epoch(), 2));
                var handSnapshot = query.hands().stream().filter(candidate -> candidate.id().equals(hand)).findFirst().orElseThrow();
                require(handSnapshot.token() != null && handSnapshot.itemId().equals("moonstation14:tomato"), "fresh authoritative query identifies held tomato");
                var request = new BodyHandActionRequest(BodyHandActionRequest.Action.THROW_ITEM, hand, owner.epoch(), 1,
                        query.revision(), null, handSnapshot.token());
                var result = BodyHandRequestService.apply(fixture.player(), request);
                if (!result.accepted() || result.hostTransition() == null
                        || result.hostTransition().direction() != com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.HostTransition.Direction.DROP) {
                    var live = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                    String context = "throw reason=" + result.reason() + " transition=" + result.hostTransition()
                            + " rev=" + query.revision() + "/" + live.revision()
                            + " ; throw diagnostics, epoch=" + owner.epoch()
                            + ", sequence=1, requestRevision=" + query.revision()
                            + ", liveRevision=" + live.revision() + ", requestedHand=" + hand + ", selectedHand=" + live.activeHand()
                            + ", bodyLook=" + body.getLookAngle() + ", bodyPosition=" + body.position()
                            + ", bodyChunkLoaded=" + level.hasChunkAt(body.blockPosition()) + ", throwGate="
                            + com.juicyslew.moonstation14.ms14.hands.live.BodyLooseThrow.lastPortableThrowGateForDiagnostics();
                    throw new AssertionError(context);
                }
                require(body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get())
                                .stackCopy(hand).orElse(ItemStack.EMPTY).isEmpty(), "successful throw empties only its source hand");
                UUID thrownId = result.hostTransition().worldEntity();
                var thrown = level.getEntity(thrownId);
                require(thrown instanceof LooseItemEntity, "published world owner is the actual loose tomato entity");
                var actualThrown = (LooseItemEntity) thrown;
                var savedFlight = actualThrown.saveWithoutId(new CompoundTag());
                require(savedFlight.getBoolean("LoosePendingThrownLandingDamage"),
                        "successfully published authenticated throw persists its one-time pending landing marker");
                var unattachedRoundTrip = LooseItemEntity.create(level, actualThrown.position(), actualThrown.getItem());
                unattachedRoundTrip.load(savedFlight);
                var roundTripTag = unattachedRoundTrip.saveWithoutId(new CompoundTag());
                require(roundTripTag.getBoolean("LoosePendingThrownLandingDamage"),
                        "normal entity NBT load/save preserves pending thrown landing state without world insertion");
                require(presetDamage.equals(unattachedRoundTrip.getItem().get(ModDataComponents.ITEM_DAMAGE_STATE.get()))
                                && currentChemistry.equals(unattachedRoundTrip.getItem().get(ModDataComponents.REAGENT.get()))
                                && java.util.Objects.equals(genetics, unattachedRoundTrip.getItem().get(ModDataComponents.GENETIC_VALUE.get())),
                        "unattached NBT round-trip preserves preset damage/deadline, current chemistry, and harvest genetics");
                unattachedRoundTrip.discard();
                helper.runAfterDelay(70, () -> {
                    try {
                        require(level.getEntity(thrownId) == null || thrown.isRemoved(), "normal first thrown floor contact destroys threshold-damaged tomato");
                        var puddles = new java.util.ArrayList<com.juicyslew.moonstation14.block.block_entity.PuddleBlockEntity>();
                        var total = new java.util.HashMap<net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData>, Long>();
                        for (int x = origin.getX() - 3; x <= origin.getX() + 10; x++)
                            for (int y = origin.getY(); y <= origin.getY() + 4; y++)
                                for (int z = origin.getZ() - 3; z <= origin.getZ() + 3; z++) {
                                    var blockEntity = level.getBlockEntity(new BlockPos(x, y, z));
                                    if (!(blockEntity instanceof com.juicyslew.moonstation14.block.block_entity.PuddleBlockEntity puddle)) continue;
                                    puddles.add(puddle);
                            var contents = com.juicyslew.moonstation14.ms14.MS14Provider.getDetached(puddle,
                                    com.juicyslew.moonstation14.ms14.MS14Bridges.REAGENT).snapshotUnits();
                            contents.forEach((key, amount) -> total.merge(key, amount, Math::addExact));
                        }
                        require(total.equals(currentChemistry.centContents()), "normal destruction spill contains exactly the current remaining 150c nutriment + 120c vitamin");
                        require(genetics != null, "authentic harvest source carries its registered genetic value");
                        require(puddles.stream().allMatch(puddle -> level.hasChunkAt(puddle.getBlockPos())), "spill uses only existing loaded puddle surfaces");
                        helper.succeed();
                    } catch (Throwable failure) {
                        failure.addSuppressed(new AssertionError("authenticated tomato throw and destruction spill"));
                        helper.testInfo.fail(failure);
                    }
                    finally { fixture.close(); }
                });
            } catch (Throwable failure) {
                fixture.close();
                failure.addSuppressed(new AssertionError("authenticated tomato throw setup"));
                helper.testInfo.fail(failure);
            }
        });
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
