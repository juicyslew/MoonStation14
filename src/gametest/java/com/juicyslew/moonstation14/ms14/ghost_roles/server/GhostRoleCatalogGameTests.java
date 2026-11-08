package com.juicyslew.moonstation14.ms14.ghost_roles.server;

import com.juicyslew.moonstation14.entities.ModEntities;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessRegistration;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;


@PrefixGameTestTemplate(false)
@GameTestTags({"ghost-roles", "character"})
public final class GhostRoleCatalogGameTests {
    private GhostRoleCatalogGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 40)
    public static void catalogUsesMappedOrdinaryHostsAndNeverOffersDeadOrHarnessBodies(GameTestHelper helper) {
        var level = helper.getLevel();
        Mob villager = spawn(helper, EntityType.VILLAGER);
        Mob pig = spawn(helper, EntityType.PIG);
        Mob mouse = spawn(helper, ModEntities.MOUSE.get());
        Mob bat = spawn(helper, com.juicyslew.moonstation14.entities.AnimalMobRegistration.types().get("bat").get());
        Mob customHarness = spawn(helper, PlayerCharacterHarnessRegistration.getEntityType());
        CharacterIdentitySystem.enrollSupportedActor(villager, level);
        CharacterIdentitySystem.enrollSupportedActor(pig, level);
        CharacterIdentitySystem.enrollSupportedActor(mouse, level);

        var first = GhostRoleCatalog.snapshot(level.getServer());
        require(has(first, villager, "Human") && has(first, pig, "Pig") && has(first, mouse, "Mouse"),
                "mapped unowned villager, pig and mouse are catalogued without a nearby player");
        require(!has(first, customHarness, "Human"), "custom player-character harness is not an ordinary human ghost-role host");

        var nonOperator = GhostRoleCatalog.entryForTarget(level.getServer(), bat, false);
        var operator = GhostRoleCatalog.entryForTarget(level.getServer(), bat, true);
        require(nonOperator == null, "nonoperator cannot resolve a forged development-only bat request");
        require(operator != null && operator.development() && operator.takeover(),
                "operator resolves bat as a development-only takeover without an ordinary GhostRole component");
        require(operator.teleport(), "operator development bat entry authorizes catalogued teleport");
        require(GhostRoleCatalog.entryForTarget(level.getServer(), mouse, false) != null
                        && !GhostRoleCatalog.entryForTarget(level.getServer(), mouse, false).development(),
                "ordinary GhostRole eligibility remains available to nonoperators");
        require(GhostRoleCatalog.snapshot(level.getServer(), false).stream().noneMatch(entry -> entry.target().equals(bat.getUUID()))
                        && GhostRoleCatalog.snapshot(level.getServer(), true).stream().anyMatch(entry -> entry.target().equals(bat.getUUID()) && entry.development()),
                "operator listing includes bat while nonoperator listing excludes it");

        mouse.hurt(level.damageSources().generic(), 10000);
        var afterDeath = GhostRoleCatalog.snapshot(level.getServer());
        require(!has(afterDeath, mouse, "Mouse"), "dead mouse is unavailable despite remaining loaded as a corpse");
        require(!has(GhostRoleCatalog.snapshot(level.getServer()), mouse, "Mouse"),
                "repeated snapshots do not re-advertise the corpse");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 40)
    public static void teleportEligibilityResolvesTargetOutsideBoundedSnapshot(GameTestHelper helper) {
        var level = helper.getLevel();
        Mob target = spawn(helper, EntityType.VILLAGER);
        CharacterIdentitySystem.enrollSupportedActor(target, level);
        var targetEntry = GhostRoleCatalog.entryForTarget(level.getServer(), target, false);
        require(targetEntry != null && targetEntry.teleport(), "test target is an eligible teleport host");
        var entries = new java.util.ArrayList<com.juicyslew.moonstation14.ms14.ghost_roles.network.GhostRolePayloads.Entry>();
        for (int i = 0; i < com.juicyslew.moonstation14.ms14.ghost_roles.network.GhostRolePayloads.MAX_ENTRIES; i++)
            entries.add(new com.juicyslew.moonstation14.ms14.ghost_roles.network.GhostRolePayloads.Entry(
                    new java.util.UUID(0, i + 1), "a-filler-" + i, "Filler", true, false, false));
        entries.add(targetEntry);
        var snapshot = GhostRoleCatalog.boundSnapshot(entries);
        require(snapshot.size() == com.juicyslew.moonstation14.ms14.ghost_roles.network.GhostRolePayloads.MAX_ENTRIES,
                "UI catalog remains bounded");
        require(snapshot.stream().noneMatch(entry -> entry.target().equals(target.getUUID())),
                "requested target is outside bounded UI catalog");
        var exactEntry = GhostRoleCatalog.entryForTarget(level.getServer(), target, false);
        require(exactEntry != null && exactEntry.teleport(),
                "teleport authorization resolves the exact eligible target independently of snapshot bound");
        helper.succeed();
    }

    private static Mob spawn(GameTestHelper helper, EntityType<? extends Mob> type) {
        Mob mob = helper.spawn(type, new BlockPos(2, 1, 2));
        mob.setNoAi(true);
        mob.setNoGravity(true);
        return mob;
    }

    private static boolean has(java.util.List<com.juicyslew.moonstation14.ms14.ghost_roles.network.GhostRolePayloads.Entry> entries,
                               Entity entity, String role) {
        return entries.stream().anyMatch(entry -> entry.target().equals(entity.getUUID()) && entry.displayName().equals(role));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
