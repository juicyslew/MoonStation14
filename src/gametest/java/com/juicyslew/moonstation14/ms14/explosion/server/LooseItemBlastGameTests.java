package com.juicyslew.moonstation14.ms14.explosion.server;

import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.ScopedPrototypeCatalogFixture;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.explosion.core.BlastPlanner;
import com.juicyslew.moonstation14.ms14.explosion.core.BlastKnockbackPolicy;
import com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile;
import com.juicyslew.moonstation14.ms14.loose_objects.ModLooseObjectProfiles;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.damage.ItemDamageState;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;
import java.util.Map;

@GameTestTags({"loose-blast", "botany"})
@PrefixGameTestTemplate(false)
public final class LooseItemBlastGameTests {
    private LooseItemBlastGameTests() { }

    @GameTest(template = "atmos_large_empty", templateNamespace = "moonstation14", timeoutTicks = 100, batch = "catalog-loose-blast-disallowed-tomato")
    public static void blastDamagesAndRemovesTomato(GameTestHelper helper) {
        var level = helper.getLevel();
        // atmos_large_empty is 36x5x8; keep the blast origin inside its z footprint.
        var origin = helper.absolutePos(new BlockPos(8, 2, 4));
        if (!level.getBlockState(origin).isAir() || !level.hasChunkAt(origin))
            throw new GameTestAssertException("blast origin is loaded air");
        var tomato = LooseItemEntity.create(level,
                new net.minecraft.world.phys.Vec3(origin.getX() + .5, origin.getY() + .5, origin.getZ() + .5),
                new ItemStack(ModItems.TOMATO.get()));
        if (!level.addFreshEntity(tomato)) throw new GameTestAssertException("tomato enters fixture");
        try {
            var result = ServerExplosionService.execute(level, origin,
                    ResourceLocation.fromNamespaceAndPath("moonstation14", "default"),
                    new BlastPlanner.Proposal(.1, 1, .001));
            if (result.status() != ServerExplosionService.Status.APPLIED)
                throw new GameTestAssertException("blast status is APPLIED, got " + result.status());
            if (result.damaged() != 1) throw new GameTestAssertException("exactly one loose item damaged");
            if (!tomato.isRemoved()) throw new GameTestAssertException("blast removes tomato above item damage threshold");
            helper.succeed();
        } catch (Throwable failure) {
            helper.fail("loose tomato blast: " + failure);
        } finally {
            if (!tomato.isRemoved()) tomato.discard();
        }
    }

    @GameTest(template = "atmos_large_empty", templateNamespace = "moonstation14", timeoutTicks = 100)
    public static void blastIgnoresFoodWithoutItemDamageProfile(GameTestHelper helper) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(new BlockPos(8, 2, 4));
        if (!level.getBlockState(origin).isAir() || !level.hasChunkAt(origin))
            throw new GameTestAssertException("blast origin is loaded air");
        var loose = LooseItemEntity.create(level,
                new net.minecraft.world.phys.Vec3(origin.getX() + .5, origin.getY() + .5, origin.getZ() + .5),
                new ItemStack(ModItems.FOOD_COCOA_BEANS.get()));
        if (!level.addFreshEntity(loose)) throw new GameTestAssertException("food enters fixture");
        var stack = loose.getItem();
        var components = stack.getComponents();
        try {
            var result = blast(level, origin);
            require(result.status() == ServerExplosionService.Status.APPLIED, "blast applied");
            require(result.damaged() == 0 && !loose.isRemoved(), "profileless food is unaffected");
            require(loose.getItem() == stack && stack.getComponents().equals(components), "owned stack and components preserved");
            require(stack.get(com.juicyslew.moonstation14.component.ModDataComponents.ITEM_DAMAGE_STATE.get()) == null,
                    "no item damage state created");
            // SS14 independently throws eligible loose entities even when they have no
            // damageable component. Preserve that distinction; this fixture makes no mass claim.
            var expectedImpulse = BlastKnockbackPolicy.calculate(.1, origin.getX() + .5, origin.getY() + .5,
                    origin.getZ() + .5, loose.getX(), loose.getY() + loose.getBbHeight() * .5, loose.getZ());
            var actualVelocity = loose.getDeltaMovement();
            require(expectedImpulse.applicable()
                            && Math.abs(actualVelocity.x - expectedImpulse.velocity().x()) < 1e-9
                            && Math.abs(actualVelocity.y - expectedImpulse.velocity().y()) < 1e-9
                            && Math.abs(actualVelocity.z - expectedImpulse.velocity().z()) < 1e-9,
                    "profileless food remains physically eligible for blast impulse");
            helper.succeed();
        } catch (Throwable failure) { helper.fail("profileless food blast: " + failure); }
        finally { if (!loose.isRemoved()) loose.discard(); }
    }

    @GameTest(template = "atmos_large_empty", templateNamespace = "moonstation14", timeoutTicks = 100)
    public static void blastIgnoresDisallowedTomatoDamageGroups(GameTestHelper helper) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(new BlockPos(8, 2, 4));
        if (!level.getBlockState(origin).isAir() || !level.hasChunkAt(origin))
            throw new GameTestAssertException("blast origin is loaded air");
        var manager = PrototypeRuntime.serverManager();
        var type = ModLooseObjectProfiles.PROFILE_TYPE;
        var original = new HashMap<>(manager.snapshot(type).asMap());
        var id = ResourceLocation.fromNamespaceAndPath("moonstation14", "tomato");
        var previous = original.get(id);
        var composition = previous.itemDamage();
        var restricted = new LooseObjectProfile.ItemDamageComposition(java.util.Set.of("airloss", "genetic"),
                composition.highSpeedImpact(), composition.minimumImpactSpeed(), composition.impactFactor(),
                composition.impactCooldownTicks(), composition.throwLandDamage(), 1f, composition.destructionSound(),
                composition.spillCurrentReagents(), composition.destructionOutputs());
        var candidate = new HashMap<>(original);
        candidate.put(id, previous.withItemDamage(restricted));
        manager.publishDecoded(type, candidate);
        var loose = LooseItemEntity.create(level,
                new net.minecraft.world.phys.Vec3(origin.getX() + .5, origin.getY() + .5, origin.getZ() + .5),
                new ItemStack(ModItems.TOMATO.get()));
        var ledger = new ItemDamageState(Map.of("blunt", .25f), 0);
        loose.getItem().set(com.juicyslew.moonstation14.component.ModDataComponents.ITEM_DAMAGE_STATE.get(), ledger);
        if (!level.addFreshEntity(loose)) {
            manager.publishDecoded(type, original);
            throw new GameTestAssertException("tomato enters fixture");
        }
        var stack = loose.getItem();
        var components = stack.getComponents();
        try {
            var result = blast(level, origin);
            require(result.status() == ServerExplosionService.Status.APPLIED, "blast applied");
            require(result.damaged() == 0 && !loose.isRemoved(), "all blast damage groups disallowed");
            require(loose.getItem() == stack && stack.getComponents().equals(components), "owned stack and components preserved");
            require(ledger.equals(stack.get(com.juicyslew.moonstation14.component.ModDataComponents.ITEM_DAMAGE_STATE.get())),
                    "existing damage ledger preserved");
            // Damage filtering is not physics immunity: blast knockback is applied before
            // damage-group filtering, so this profile-backed item still receives its impulse.
            var expectedImpulse = BlastKnockbackPolicy.calculate(.1, origin.getX() + .5, origin.getY() + .5,
                    origin.getZ() + .5, loose.getX(), loose.getY() + loose.getBbHeight() * .5, loose.getZ());
            var actualVelocity = loose.getDeltaMovement();
            require(expectedImpulse.applicable()
                            && Math.abs(actualVelocity.x - expectedImpulse.velocity().x()) < 1e-9
                            && Math.abs(actualVelocity.y - expectedImpulse.velocity().y()) < 1e-9
                            && Math.abs(actualVelocity.z - expectedImpulse.velocity().z()) < 1e-9,
                    "unsupported damage group leaves ledger untouched but retains blast impulse");
            helper.succeed();
        } catch (Throwable failure) { helper.fail("disallowed tomato blast: " + failure); }
        finally {
            if (!loose.isRemoved()) loose.discard();
            manager.publishDecoded(type, original);
        }
    }

    @GameTest(template = "atmos_large_empty", templateNamespace = "moonstation14", timeoutTicks = 100,
            batch = "item-damage-modifier-validation-r3")
    public static void blastAppliesProfileModifiersExactlyOnce(GameTestHelper helper) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(new BlockPos(8, 2, 4));
        if (!level.getBlockState(origin).isAir() || !level.hasChunkAt(origin))
            throw new GameTestAssertException("blast origin is loaded air");
        var type = ModLooseObjectProfiles.PROFILE_TYPE;
        var tomatoId = ResourceLocation.fromNamespaceAndPath("moonstation14", "tomato");
        var original = new HashMap<>(PrototypeRuntime.serverManager().snapshot(type).asMap());
        var previous = original.get(tomatoId);
        var old = previous.itemDamage();
        var modifiers = new com.juicyslew.moonstation14.ms14.damage.DamageModifierData(
                Map.of("blunt", 1.2f, "slash", .5f, "piercing", 1f, "heat", .8f, "shock", 0f),
                Map.of("slash", 5f, "piercing", 5f, "heat", 5f));
        var adjusted = new LooseObjectProfile.ItemDamageComposition(java.util.Set.of("brute"),
                java.util.Set.of("heat", "shock"), old.highSpeedImpact(), old.minimumImpactSpeed(),
                old.impactFactor(), old.impactCooldownTicks(), old.throwLandDamage(), 100f,
                old.destructionSound(), old.spillCurrentReagents(), old.destructionOutputs(),
                old.destructionExplosion(), old.destructionSoundVolumeDb(), modifiers);
        var catalog = ScopedPrototypeCatalogFixture.replace(helper, type, candidate -> {
            candidate.put(tomatoId, previous.withItemDamage(adjusted));
            return candidate;
        });
        var loose = LooseItemEntity.create(level,
                new net.minecraft.world.phys.Vec3(origin.getX() + .5, origin.getY() + .5, origin.getZ() + .5),
                new ItemStack(ModItems.TOMATO.get()));
        if (!level.addFreshEntity(loose)) {
            catalog.restore();
            throw new GameTestAssertException("tomato enters fixture");
        }
        try {
            var result = blast(level, origin);
            require(result.status() == ServerExplosionService.Status.APPLIED, "blast applied");
            require(result.damaged() == 1 && !loose.isRemoved(), "one surviving source accepted damage");
            var ledger = loose.getItem().get(com.juicyslew.moonstation14.component.ModDataComponents.ITEM_DAMAGE_STATE.get());
            require(ledger != null && ledger.damage().size() == 1
                            && Math.abs(ledger.damage().getOrDefault("blunt", 0f) - .6f) < 1e-6,
                    "service applies blunt modifier once (.6), with no other ledger entries: " + ledger);
            helper.succeed();
        } catch (Throwable failure) { helper.fail("modified tomato blast: " + failure); }
        finally {
            if (!loose.isRemoved()) loose.discard();
            catalog.restore();
        }
    }

    private static ServerExplosionService.Result blast(net.minecraft.server.level.ServerLevel level, BlockPos origin) {
        return ServerExplosionService.execute(level, origin,
                ResourceLocation.fromNamespaceAndPath("moonstation14", "default"),
                new BlastPlanner.Proposal(.1, 1, .001));
    }

    private static void require(boolean value, String message) {
        if (!value) throw new GameTestAssertException(message);
    }
}
