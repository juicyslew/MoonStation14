package com.juicyslew.moonstation14.ms14.mobs;

import com.juicyslew.moonstation14.entities.AnimalMob;
import com.juicyslew.moonstation14.entities.AnimalMobRegistration;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.components.NpcBehaviorComponent;
import com.juicyslew.moonstation14.ms14.npc.NpcRetaliationSystem;
import com.juicyslew.moonstation14.ms14.mobs.NpcBehaviorSystem;
import com.juicyslew.moonstation14.ms14.player_body_control.character.MindControlledMob;
import com.juicyslew.moonstation14.ms14.player_body_control.character.NpcMovementDriven;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Exercises a real registered AnimalMob through its resolved prototype and shared NPC tick owner. */
@PrefixGameTestTemplate(false)
public final class AnimalNpcRetaliationGameTests {
    private AnimalNpcRetaliationGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 80)
    @GameTestTags({"animal-npc", "npc-retaliation"})
    public static void registeredAnimalUsesSharedBehaviorAndControlledBodyCannotProvoke(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        for (int x = 0; x < 7; x++) for (int z = 0; z < 7; z++)
            helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        var goatType = AnimalMobRegistration.types().get("goat");
        require(goatType != null, "registered goat host exists");
        AnimalMob goat = goatType.get().create(level);
        AnimalMob attacker = goatType.get().create(level);
        require(goat != null && attacker != null, "registered animal hosts construct");
        BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
        goat.setPos(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
        attacker.setPos(origin.getX() + 3.5, origin.getY(), origin.getZ() + .5);
        require(level.addFreshEntity(goat) && level.addFreshEntity(attacker), "registered animals join live world");
        require(goat instanceof NpcMovementDriven, "registered AnimalMob satisfies the shared movement-driven interface");
        var resolved = CharacterIdentitySystem.resolveForActor(goat).orElseThrow();
        require(resolved.component(NpcBehaviorComponent.class).isPresent(), "host inheritance resolves NPC behavior policy");
        helper.runAfterDelay(15, () -> {
            try {
                require(((NpcMovementDriven) goat).moonstation14$isNpcMovementDriven(),
                        "live NpcBehaviorSystem tick enrolled AnimalMob in shared grounded motor");
                require(NpcBehaviorSystem.debugSnapshot(goat) != null, "prototype-backed brain created for actual animal host");
                var policy = CharacterIdentitySystem.resolveForActor(goat).orElseThrow()
                        .component(NpcBehaviorComponent.class).orElseThrow();
                require(policy.tasks().contains(NpcBehaviorComponent.Task.FOOD)
                                && policy.tasks().contains(NpcBehaviorComponent.Task.DRINK),
                        "resolved inherited food/drink tasks reach live animal brain");
                require(resolved.component(com.juicyslew.moonstation14.ms14.character.components.NpcRetaliationComponent.class).isPresent(),
                        "goat resolves defensive retaliation policy");
                require(NpcRetaliationSystem.provoke(goat, attacker, level.getGameTime()),
                        "autonomous animal records a real provocation");
                helper.runAfterDelay(2, () -> {
                    try {
                        require(NpcBehaviorSystem.debugSnapshot(goat).task()
                                        == NpcBehaviorComponent.Task.RETALIATE,
                                "resolved provocation reaches live shared NPC behavior task");
                        ((MindControlledMob) goat).moonstation14$setMovementOwned(true);
                        require(!NpcBehaviorSystem.autonomousNpcEligible(goat, level, resolved),
                                "movement-owned body fails canonical autonomy admission");
                        require(NpcRetaliationSystem.target(goat, level.getGameTime()) == null,
                                "control takeover clears defensive target acquisition");
                        helper.succeed();
                    } catch (RuntimeException | Error failure) {
                        helper.fail("controlled registered goat retaliation fixture: " + failure);
                    }
                });
            } catch (RuntimeException | Error failure) {
                helper.fail("registered animal NPC behavior/retaliation fixture: " + failure);
            }
        });
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
