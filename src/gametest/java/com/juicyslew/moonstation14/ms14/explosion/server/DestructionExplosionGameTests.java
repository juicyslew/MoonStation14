package com.juicyslew.moonstation14.ms14.explosion.server;

import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.damage.ItemDamageState;
import com.juicyslew.moonstation14.ms14.explosion.prototype.ExplosiveSourceData;
import com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile;
import com.juicyslew.moonstation14.ms14.loose_objects.ModLooseObjectProfiles;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@GameTestTags({"item-damage", "explosion-queue", "loose-blast"})
@PrefixGameTestTemplate(false)
public final class DestructionExplosionGameTests {
    private DestructionExplosionGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 100, batch = "catalog-destruction-explosion-tomato")
    public static void impactDestructionQueuesBlastAgainstDifferentProfileTomato(GameTestHelper helper) {
        var manager = PrototypeRuntime.serverManager();
        var type = ModLooseObjectProfiles.PROFILE_TYPE;
        var original = new HashMap<>(manager.snapshot(type).asMap());
        var tomatoId = ResourceLocation.fromNamespaceAndPath("moonstation14", "tomato");
        var sourceId = ResourceLocation.fromNamespaceAndPath("moonstation14", "fixture_destruction_explosive_source");
        var receiverId = ResourceLocation.fromNamespaceAndPath("moonstation14", "fixture_destruction_explosive_receiver");
        var base = original.get(tomatoId);
        var oldDamage = base.itemDamage();
        var explosion = new ExplosiveSourceData(.1, 1, .001, ResourceLocation.fromNamespaceAndPath("moonstation14", "default"));
        var sourceDamage = new LooseObjectProfile.ItemDamageComposition(oldDamage.allowedGroups(), Map.of("blunt", 1f),
                0f, 1f, 40, Map.of(), 1f, null, false, java.util.List.of(), explosion);
        var receiverDamage = new LooseObjectProfile.ItemDamageComposition(oldDamage.allowedGroups(), Map.of(),
                0f, 0f, 0, Map.of(), 100f, null, false, java.util.List.of(), null);
        var profiles = new HashMap<>(original);
        profiles.put(sourceId, withDamage(base, sourceDamage));
        profiles.put(receiverId, withDamage(base, receiverDamage));
        manager.publishDecoded(type, profiles);
        var restored = new AtomicBoolean();
        helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            private void restore() { if (restored.compareAndSet(false, true)) manager.publishDecoded(type, original); }
            @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo info) { }
            @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo info, net.minecraft.gametest.framework.GameTestRunner runner) { restore(); }
            @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo info, net.minecraft.gametest.framework.GameTestRunner runner) { restore(); }
            @Override public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo oldTest, net.minecraft.gametest.framework.GameTestInfo newTest, net.minecraft.gametest.framework.GameTestRunner runner) { restore(); }
        });
        LooseItemEntity source = null, receiver = null;
        try {
            var wall = helper.absolutePos(new BlockPos(8, 2, 5));
            var level = helper.getLevel();
            for (int y = 1; y < 6; y++) level.setBlock(wall.offset(0, y - 2, 0), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
            var sourceStack = new ItemStack(ModItems.TOMATO.get());
            sourceStack.set(ModDataComponents.LOOSE_OBJECT_PROFILE.get(), sourceId);
            source = LooseItemEntity.create(level, new Vec3(wall.getX() - 2, wall.getY() + .5, wall.getZ() + .5), sourceStack);
            source.setNoGravity(true); source.setDeltaMovement(2, 0, 0);
            var receiverStack = new ItemStack(ModItems.TOMATO.get());
            receiverStack.set(ModDataComponents.LOOSE_OBJECT_PROFILE.get(), receiverId);
            receiver = LooseItemEntity.create(level, new Vec3(wall.getX() - 4, wall.getY() + .5, wall.getZ() + .5), receiverStack);
            receiver.setNoGravity(true); receiver.setDeltaMovement(Vec3.ZERO);
            if (!level.addFreshEntity(source) || !level.addFreshEntity(receiver)) throw new GameTestAssertException("fixture tomatoes enter world");
            var s = source; var r = receiver;
            helper.runAfterDelay(8, () -> {
                try {
                    var ledger = r.getItem().get(ModDataComponents.ITEM_DAMAGE_STATE.get());
                    require(s.isRemoved(), "impact destroyed explosive source");
                    require(!r.isRemoved() && r.getItem().get(ModDataComponents.LOOSE_OBJECT_PROFILE.get()).equals(receiverId), "different-profile receiver survives");
                    require(ledger != null && ledger.damage().equals(Map.of("heat", .5f, "blunt", .5f, "piercing", .5f)), "queued destruction blast applies expected receiver ledger");
                    require(ledger.nextImpactDeadline() == 0, "receiver damage ledger has no unrelated impact cooldown");
                    helper.succeed();
                } catch (Throwable failure) { helper.fail("impact destruction explosion route: " + failure); }
                finally { if (!s.isRemoved()) s.discard(); if (!r.isRemoved()) r.discard(); }
            });
        } catch (Throwable failure) {
            if (source != null && !source.isRemoved()) source.discard();
            if (receiver != null && !receiver.isRemoved()) receiver.discard();
            restored.compareAndSet(false, true); manager.publishDecoded(type, original);
            helper.fail("impact destruction explosion fixture: " + failure);
        }
    }

    private static LooseObjectProfile withDamage(LooseObjectProfile p, LooseObjectProfile.ItemDamageComposition damage) {
        return p.withItemDamage(damage);
    }
    private static void require(boolean value, String message) { if (!value) throw new GameTestAssertException(message); }
}
