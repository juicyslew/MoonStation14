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
        Mob customHarness = spawn(helper, PlayerCharacterHarnessRegistration.getEntityType());
        CharacterIdentitySystem.enrollSupportedActor(villager, level);
        CharacterIdentitySystem.enrollSupportedActor(pig, level);
        CharacterIdentitySystem.enrollSupportedActor(mouse, level);

        var first = GhostRoleCatalog.snapshot(level.getServer());
        require(has(first, villager, "Human") && has(first, pig, "Pig") && has(first, mouse, "Mouse"),
                "mapped unowned villager, pig and mouse are catalogued without a nearby player");
        require(!has(first, customHarness, "Human"), "custom player-character harness is not an ordinary human ghost-role host");

        mouse.hurt(level.damageSources().generic(), 10000);
        var afterDeath = GhostRoleCatalog.snapshot(level.getServer());
        require(!has(afterDeath, mouse, "Mouse"), "dead mouse is unavailable despite remaining loaded as a corpse");
        require(!has(GhostRoleCatalog.snapshot(level.getServer()), mouse, "Mouse"),
                "repeated snapshots do not re-advertise the corpse");
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
