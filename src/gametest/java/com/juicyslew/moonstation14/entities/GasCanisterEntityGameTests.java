package com.juicyslew.moonstation14.entities;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.item.custom.GasTankItem;
import com.juicyslew.moonstation14.ms14.atmos.tank.ModGasTanks;
import com.juicyslew.moonstation14.ms14.atmos.tank.ModGasCanisters;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocation;
import com.juicyslew.moonstation14.ms14.hands.transfer.LevelItemLocationLedgers;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransferGameTestAccess;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.UUID;

@GameTestTags({"gas-canister-entity"})
@PrefixGameTestTemplate(false)
public final class GasCanisterEntityGameTests {
    private GasCanisterEntityGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 20, templateNamespace = "moonstation14")
    public static void resolvedProfileMovementAndPickupAdmission(GameTestHelper helper) {
        var level = helper.getLevel();
        var profile = LooseObjectProfile.resolve(LooseObjectProfile.GAS_CANISTER_ID);
        if (profile.physicalWidth() != .5f || profile.physicalHeight() != 1f || profile.mass() != 190f)
            throw new AssertionError("resolved canister physical profile mismatch: " + profile);
        GasCanisterEntity entity = GasCanisterEntity.create(level,
                helper.absolutePos(new BlockPos(2, 2, 2)).getCenter(),
                ResourceLocation.fromNamespaceAndPath("moonstation14", "storage_canister"));
        if (!level.addFreshEntity(entity)) throw new AssertionError("canister registration failed");
        if (Math.abs(entity.getBbWidth() - .5f) > 1e-5 || Math.abs(entity.getBbHeight() - 1f) > 1e-5)
            throw new AssertionError("canister dimensions are not prototype-resolved");
        if (entity.admitsBodyHandPickup()) throw new AssertionError("canister admitted to body hands");
        entity.launch(new Vec3(.2, 0, 0), 0);
        entity.applyPullVelocityChange(new Vec3(.1, 0, 0));
        if (Math.abs(entity.getDeltaMovement().x - .3) > 1e-5) throw new AssertionError("shared pulling velocity was not applied");
        Vec3 before = entity.position();
        entity.tick();
        if (entity.position().distanceToSqr(before) <= 0) throw new AssertionError("loose movement did not advance");
        entity.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20, templateNamespace = "moonstation14")
    public static void permanentRemovalReleasesExactlyOneTank(GameTestHelper helper) {
        Fixture f = fixture(helper);
        f.entity.discard(); // exercises registered Entity.setRemoved mixin bridge
        var dropped = helper.getLevel().getEntitiesOfClass(LooseItemEntity.class,
                f.entity.getBoundingBox().inflate(8), e -> e.getItem().is(ModItems.PLASMA_TANK.get()));
        require(dropped.size() == 1 && sameTank(f.payload, dropped.getFirst().getItem()), "exact tank payload released once");
        require(!f.ledger.snapshot().locations().containsKey(f.token), "slot ownership claim retired");
        f.entity.discard();
        require(helper.getLevel().getEntitiesOfClass(LooseItemEntity.class,
                f.entity.getBoundingBox().inflate(8), e -> e.getItem().is(ModItems.PLASMA_TANK.get())).size() == 1,
                "repeated removal does not duplicate output");
        dropped.forEach(LooseItemEntity::discard);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20, templateNamespace = "moonstation14")
    public static void failedReleaseDefersRemovalAndRetrySucceeds(GameTestHelper helper) {
        Fixture f = fixture(helper);
        var slot = f.entity.canisterState().slot();
        var slotLocation = new ItemLocation.MachineSlot(f.entity.getUUID(), "tank_slot");
        // Contrived stale-claim boundary: leave the real occupied slot intact but invalidate its
        // ledger owner, making the canonical slot.eject compare-and-move fail without test mocks.
        require(f.ledger.consume(f.ledger.snapshot().revision(), f.token, slotLocation).accepted(),
                "stale slot ledger claim deliberately removed");
        f.entity.discard();
        require(!f.entity.isRemoved(), "failed canonical release cancels permanent removal");
        require(slot.snapshot().token().equals(f.token) && sameTank(f.payload, slot.snapshot().stack()),
                "failed release retains exact occupied slot payload and token");
        require(helper.getLevel().getEntitiesOfClass(LooseItemEntity.class,
                f.entity.getBoundingBox().inflate(8), e -> e.getItem().is(ModItems.PLASMA_TANK.get())).isEmpty(),
                "failed release publishes no drop");
        require(f.ledger.register(f.token, slotLocation).accepted(), "legitimate ledger owner restored for retry");
        f.entity.discard();
        require(f.entity.isRemoved() && slot.snapshot().stack().isEmpty(), "retry safely removes entity and empties slot");
        var drops = helper.getLevel().getEntitiesOfClass(LooseItemEntity.class,
                f.entity.getBoundingBox().inflate(8), e -> e.getItem().is(ModItems.PLASMA_TANK.get()));
        require(drops.size() == 1 && sameTank(f.payload, drops.getFirst().getItem()),
                "retry emits exactly one exact tank payload");
        require(!f.ledger.snapshot().locations().containsKey(f.token), "successful retry retires token");
        drops.forEach(LooseItemEntity::discard);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20, templateNamespace = "moonstation14")
    public static void unloadSaveLoadRestoresSingleSlotClaim(GameTestHelper helper) {
        Fixture f = fixture(helper);
        UUID owner = f.entity.getUUID();
        f.entity.setRemoved(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
        require(f.ledger.snapshot().locations().get(f.token) == null, "unload suspends original slot claim");
        require(f.entity.canisterState() == null, "removed unloaded entity cannot lazily resume its pending owner");
        for (int i = 0; i < 20; i++) f.entity.tick();
        require(!f.ledger.snapshot().locations().containsKey(f.token), "removed entity tick cannot reclaim the suspended token");
        var saved = f.entity.saveWithoutId(new net.minecraft.nbt.CompoundTag());
        require(!saved.getCompound("CanisterState").isEmpty(), "removed entity save retains pending gas and exact slot payload");
        GasCanisterEntity restored = GasCanisterEntity.create(helper.getLevel(),
                helper.absolutePos(new BlockPos(5, 2, 5)).getCenter(), ModGasCanisters.STORAGE_ID);
        restored.setUUID(owner);
        restored.load(saved);
        require(helper.getLevel().addFreshEntity(restored), "restored canister registered");
        var state = restored.canisterState();
        require(state != null && !state.slot().isRetired(), "restored slot claim active");
        require(sameTank(f.payload, state.slot().snapshot().stack()), "exact saved stack, metadata and finite gas restored");
        require(new ItemLocation.MachineSlot(owner, "tank_slot").equals(f.ledger.snapshot().locations().get(f.token)),
                "one restored ledger owner, no duplicate claim");
        restored.discard();
        helper.getLevel().getEntitiesOfClass(LooseItemEntity.class, restored.getBoundingBox().inflate(8),
                e -> e.getItem().is(ModItems.PLASMA_TANK.get())).forEach(LooseItemEntity::discard);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40, templateNamespace = "moonstation14")
    public static void serverTickFiniteTankRefillConservesAndPersists(GameTestHelper helper) {
        Fixture f = fixture(helper);
        var state = f.entity.canisterState();
        require(state.setValveClosed(state.revision(), false), "revision-checked valve opened");
        var beforeTank = GasTankItem.contents(f.payload, helper.getLevel()).orElseThrow().mixture();
        var beforeCanister = state.gas();
        for (int i = 0; i < 20; i++) f.entity.tick();
        var afterState = f.entity.canisterState();
        var slot = afterState.slot().snapshot();
        var afterTank = GasTankItem.contents(slot.stack(), helper.getLevel()).orElseThrow().mixture();
        require(slot.token().equals(f.token), "refill retains exact tank token");
        require(slot.revision() > 1, "successful in-place payload mutation advances slot revision");
        require(Component.literal("finite test tank").equals(slot.stack().get(net.minecraft.core.component.DataComponents.CUSTOM_NAME)),
                "refill preserves custom tank metadata");
        require(afterState.gas().pressureKpa() <= afterState.releasePressure() + .01,
                "canister pressure is bounded by resolved configured target");
        for (var type : com.juicyslew.moonstation14.ms14.atmos.core.GasType.values()) {
            double totalBefore = beforeCanister.moles(type) + beforeTank.moles(type);
            double totalAfter = afterState.gas().moles(type) + afterTank.moles(type);
            require(Math.abs(totalBefore - totalAfter) < 2e-4, "species inventory conserved: " + type);
        }
        require(afterState.gas().totalMoles() > beforeCanister.totalMoles(), "live entity tick transferred finite gas");
        require(f.ledger.snapshot().locations().get(f.token).equals(new ItemLocation.MachineSlot(f.entity.getUUID(), "tank_slot")),
                "refill retains existing active ledger claim");
        long fullStateRevision = afterState.revision();
        long fullSlotRevision = slot.revision();
        for (int i = 0; i < 20; i++) f.entity.tick();
        require(afterState.revision() == fullStateRevision && afterState.slot().snapshot().revision() == fullSlotRevision,
                "at-target full receiver causes no transfer or revision churn");
        var saved = f.entity.saveWithoutId(new net.minecraft.nbt.CompoundTag());
        UUID owner = f.entity.getUUID();
        f.entity.setRemoved(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
        var restored = GasCanisterEntity.create(helper.getLevel(), helper.absolutePos(new BlockPos(5, 2, 5)).getCenter(), ModGasCanisters.STORAGE_ID);
        restored.setUUID(owner); restored.load(saved);
        require(helper.getLevel().addFreshEntity(restored), "refilled canister restored from save");
        require(restored.canisterState().gas().gasMoles().equals(afterState.gas().gasMoles()), "canister gas survives save/load");
        require(sameTank(slot.stack(), restored.canisterState().slot().snapshot().stack()), "refilled tank payload and metadata survive save/load");
        restored.discard();
        helper.getLevel().getEntitiesOfClass(LooseItemEntity.class, restored.getBoundingBox().inflate(8),
                e -> e.getItem().is(ModItems.PLASMA_TANK.get())).forEach(LooseItemEntity::discard);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20, templateNamespace = "moonstation14")
    public static void staleLedgerAndClosedValveDoNotMutateGas(GameTestHelper helper) {
        Fixture f = fixture(helper);
        var state = f.entity.canisterState();
        var slotBefore = state.slot().snapshot();
        var gasBefore = state.gas();
        require(!state.refillFromInsertedTank(state.revision(), slotBefore.revision(), f.ledger.snapshot().revision() - 1),
                "stale ledger revision rejected");
        require(state.gas().gasMoles().equals(gasBefore.gasMoles()) && sameTank(slotBefore.stack(), state.slot().snapshot().stack()),
                "stale ledger leaves both gas inventories unchanged");
        require(!state.refillFromInsertedTank(state.revision(), slotBefore.revision(), f.ledger.snapshot().revision()),
                "closed valve blocks transfer");
        require(state.gas().gasMoles().equals(gasBefore.gasMoles()), "closed valve consumes no gas");
        require(state.setValveClosed(state.revision(), false), "open valve for inactive-claim check");
        require(f.ledger.consume(f.ledger.snapshot().revision(), f.token,
                new ItemLocation.MachineSlot(f.entity.getUUID(), "tank_slot")).accepted(), "fixture claim deliberately retired");
        long ledgerRev = f.ledger.snapshot().revision();
        require(!state.refillFromInsertedTank(state.revision(), slotBefore.revision(), ledgerRev),
                "inactive exact token claim rejected");
        require(state.gas().gasMoles().equals(gasBefore.gasMoles())
                        && sameTank(slotBefore.stack(), state.slot().snapshot().stack()),
                "inactive claim rejection leaves both finite inventories intact");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40, templateNamespace = "moonstation14")
    public static void openValveWithoutInsertedTankDoesNotVentOrRevTick(GameTestHelper helper) {
        var entity = GasCanisterEntity.create(helper.getLevel(),
                helper.absolutePos(new BlockPos(3, 2, 3)).getCenter(), ModGasCanisters.STORAGE_ID);
        require(helper.getLevel().addFreshEntity(entity), "empty canister added");
        var state = entity.canisterState();
        require(state.setValveClosed(state.revision(), false), "valve opened by revision-checked control");
        long revision = state.revision();
        var moles = state.gas().gasMoles();
        for (int i = 0; i < 20; i++) entity.tick();
        require(state.gas().gasMoles().equals(moles), "no inserted tank means no gas is sent into void");
        require(state.revision() == revision && state.slot().snapshot().revision() == 0,
                "no-op tick does not churn revisions");
        entity.discard(); helper.succeed();
    }

    @GameTestTags({"canister-appearance", "gas-canister-domain", "machine-slots"})
    @GameTest(template = "empty", timeoutTicks = 80, templateNamespace = "moonstation14")
    public static void registeredAppearanceTracksResolvedProfileSlotAndPersistence(GameTestHelper helper) {
        var level = helper.getLevel();
        var entity = GasCanisterEntity.create(level, helper.absolutePos(new BlockPos(3, 2, 3)).getCenter(), ModGasCanisters.PLASMA_ID);
        require(level.addFreshEntity(entity), "configured plasma canister registered");
        for (int i = 0; i < 2; i++) entity.tick();
        var appearance = entity.appearance().orElseThrow(() -> new AssertionError("resolved appearance ready after normal tick"));
        require(appearance.profileId().equals(ModGasCanisters.PLASMA_ID.toString()), "appearance carries configured profile id");
        require(appearance.profile().modelSkinPrefix().equals("orange") && appearance.profile().defaultLockedVisual(),
                "visual defaults come from resolved plasma profile");
        require(appearance.pressureBand() == GasCanisterEntity.pressureBand(entity.canisterState().gas().pressureKpa()),
                "pressure band is canonical quantization of live gas pressure");
        var state = entity.canisterState();
        var ledger = LevelItemLocationLedgers.forLevel((net.minecraft.server.level.ServerLevel) level);
        ItemStack payload = GasTankItem.createStack(level, ModGasTanks.PLASMA_FILLED_ID);
        ItemToken token = new ItemToken("appearance-tank-" + UUID.randomUUID());
        ItemLocation source = new ItemLocation.WorldEntity(UUID.randomUUID(), UUID.randomUUID());
        require(ledger.register(token, source).accepted(), "trusted fixture source claim registered");
        require(state.slot().insert(state.slot().snapshot().revision(), ledger.snapshot().revision(), token, source, payload), "real canister slot accepts owned tank");
        for (int i = 0; i < 2; i++) entity.tick();
        require(entity.appearance().orElseThrow().tankInserted(), "appearance reflects inserted owned tank");
        var inserted = state.slot().snapshot();
        var ejected = state.slot().eject(inserted.revision(), ledger.snapshot().revision(), source).orElseThrow();
        require(ejected.token().equals(token), "normal slot ejection preserves token ownership");
        for (int i = 0; i < 2; i++) entity.tick();
        require(!entity.appearance().orElseThrow().tankInserted(), "appearance clears after normal ejection");
        var saved = entity.saveWithoutId(new net.minecraft.nbt.CompoundTag());
        UUID owner = entity.getUUID();
        entity.setRemoved(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
        var restored = GasCanisterEntity.create(level, helper.absolutePos(new BlockPos(5, 2, 5)).getCenter(), ModGasCanisters.STORAGE_ID);
        restored.setUUID(owner); restored.load(saved);
        require(level.addFreshEntity(restored), "configured canister restored from entity NBT");
        for (int i = 0; i < 2; i++) restored.tick();
        var restoredAppearance = restored.appearance().orElseThrow(() -> new AssertionError("restored appearance becomes ready"));
        require(restoredAppearance.profileId().equals(ModGasCanisters.PLASMA_ID.toString())
                        && restoredAppearance.profile().modelSkinPrefix().equals("orange")
                        && restoredAppearance.profile().defaultLockedVisual(), "NBT restores configured profile appearance");
        require(restored.canisterState().gas().gasMoles().equals(state.gas().gasMoles()), "normal NBT reload preserves canonical gas inventory");
        restored.discard(); entity.discard(); helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20, templateNamespace = "moonstation14")
    public static void bodyHandPickupAdmissionDeniesCanister(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 1, 2));
        var body = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
        require(body != null, "pickup body created");
        body.setNoAi(true);
        body.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        body.setYRot(-90);
        body.setYHeadRot(-90);
        body.setXRot(0);
        require(level.addFreshEntity(body) && LiveHands.initialize(body).isPresent(), "pickup body initialized");
        var hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        GasCanisterEntity canister = GasCanisterEntity.create(level,
                new Vec3(pos.getX() + 3.5, pos.getY() + 1.5, pos.getZ() + .5), ModGasCanisters.STORAGE_ID);
        require(level.addFreshEntity(canister), "canister pickup target registered");
        require(BodyHandItemTransferGameTestAccess.pickupOwned(body, canister.getUUID(), hands.activeHand(), hands.revision())
                == BodyHandItemTransfer.Result.DENIED, "shared body-hand transaction denies canister");
        require(body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == hands
                && level.getEntity(canister.getUUID()) == canister, "denial leaves hand source and canister unchanged");
        body.discard(); canister.discard(); helper.succeed();
    }

    private static Fixture fixture(GameTestHelper helper) {
        var level = helper.getLevel();
        var ledger = LevelItemLocationLedgers.forLevel((net.minecraft.server.level.ServerLevel) level);
        GasCanisterEntity entity = GasCanisterEntity.create(level,
                helper.absolutePos(new BlockPos(3, 2, 3)).getCenter(), ModGasCanisters.STORAGE_ID);
        require(level.addFreshEntity(entity), "actual registered canister added");
        var slot = entity.canisterState().slot();
        ItemStack payload = GasTankItem.createStack(level, ModGasTanks.PLASMA_FILLED_ID);
        payload.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("finite test tank"));
        payload.set(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA,
                new net.minecraft.world.item.component.CustomModelData(9));
        payload.setCount(1);
        ItemToken token = new ItemToken("canister-tank-" + UUID.randomUUID());
        ItemLocation source = new ItemLocation.WorldEntity(UUID.randomUUID(), UUID.randomUUID());
        require(ledger.register(token, source).accepted(), "slot source ledger claim seeded");
        require(slot.insert(slot.snapshot().revision(), ledger.snapshot().revision(), token, source, payload), "tank inserted into real canister slot");
        return new Fixture(entity, ledger, token, payload);
    }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static boolean sameTank(ItemStack expected, ItemStack actual) {
        if (!actual.is(expected.getItem()) || actual.getCount() != expected.getCount()
                || !java.util.Objects.equals(expected.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME),
                        actual.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME))
                || !java.util.Objects.equals(expected.get(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA),
                        actual.get(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA))) return false;
        var expectedGas = GasTankItem.contents(expected).orElseThrow();
        var actualGas = GasTankItem.contents(actual).orElseThrow();
        var a = expectedGas.mixture(); var b = actualGas.mixture();
        return expectedGas.prototypeId().equals(actualGas.prototypeId())
                && a.gasMoles().equals(b.gasMoles())
                && a.temperatureKelvin() == b.temperatureKelvin()
                && a.volumeCubicMeters() == b.volumeCubicMeters();
    }
    private record Fixture(GasCanisterEntity entity,
                           com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocationLedger ledger,
                           ItemToken token, ItemStack payload) { }
}
