package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.ms14.combat.MeleeWeaponResolver;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestTags({"melee-presentation-setup", "registered-melee"})
@PrefixGameTestTemplate(false)
public final class MeleePresentationSetupGameTests {
    private MeleePresentationSetupGameTests() { }

    @GameTest(template="atmos_large_empty", templateNamespace="moonstation14", batch="melee_presentation_setup", timeoutTicks=100)
    public static void weaponSetupUsesCanonicalLiveHandBinding(GameTestHelper helper) {
        var fixture = RegisteredServerPlayerFixture.register(helper.getLevel(), "melee-presentation-setup");
        helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            private void clean(net.minecraft.gametest.framework.GameTestInfo info) {
                try { fixture.close(); }
                catch (Throwable failure) { if (info.getError() != null) info.getError().addSuppressed(failure); else info.fail(failure); }
            }
            public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i) { }
            public void testPassed(net.minecraft.gametest.framework.GameTestInfo i, net.minecraft.gametest.framework.GameTestRunner r) { clean(i); }
            public void testFailed(net.minecraft.gametest.framework.GameTestInfo i, net.minecraft.gametest.framework.GameTestRunner r) { clean(i); }
            public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo o, net.minecraft.gametest.framework.GameTestInfo n, net.minecraft.gametest.framework.GameTestRunner r) { clean(o); }
        });
        helper.runAtTickTime(2, () -> {
            try {
                var body = RegisteredPlayerReadyDriver.readyAndQuery(fixture).body();
                var knife = com.juicyslew.moonstation14.item.ModItems.KITCHEN_KNIFE.get();
                var crowbar = com.juicyslew.moonstation14.item.ModItems.CROWBAR.get();
                requireEmpty(body);
                MeleePresentationServerDiagnostic.equipWeapon(fixture.player(), body, knife);
                assertHeld(body, knife);
                var beforeRepeat = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                if (beforeRepeat == null) throw new GameTestAssertException("registered body lost LiveHands before repeated setup");
                var revision = beforeRepeat.revision();
                var token = beforeRepeat.token(beforeRepeat.activeHand());
                MeleePresentationServerDiagnostic.equipWeapon(fixture.player(), body, knife);
                var afterRepeat = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                if (afterRepeat == null || afterRepeat.revision() != revision
                        || !token.equals(afterRepeat.token(afterRepeat.activeHand())))
                    throw new GameTestAssertException("repeating weapon setup replaced held knife or changed ownership binding");
                assertHeld(body, knife);
                MeleePresentationServerDiagnostic.equipWeapon(fixture.player(), body, crowbar);
                assertHeld(body, crowbar);
                helper.succeed();
            } catch (RuntimeException | Error failure) { fixture.close(); throw failure; }
        });
    }

    private static void requireEmpty(net.minecraft.world.entity.LivingEntity body) {
        var hands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
        if (hands == null || !hands.stackCopy(hands.activeHand()).orElse(net.minecraft.world.item.ItemStack.EMPTY).isEmpty())
            throw new GameTestAssertException("registered body did not start with an empty selected hand");
    }

    private static void assertHeld(net.minecraft.world.entity.LivingEntity body, net.minecraft.world.item.Item item) {
        var hands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
        var selection = MeleeWeaponResolver.resolve(body).orElseThrow(() -> new GameTestAssertException("melee binding unresolved"));
        var stack = hands == null ? net.minecraft.world.item.ItemStack.EMPTY : hands.stackCopy(hands.activeHand()).orElse(net.minecraft.world.item.ItemStack.EMPTY);
        var expected = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
        var actual = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (hands == null || stack.isEmpty() || !expected.equals(actual) || selection.kind() != MeleeWeaponResolver.Kind.HELD
                || !selection.activeHand().equals(hands.activeHand()) || selection.handRevision() != hands.revision()
                || selection.itemToken().isEmpty() || !selection.itemToken().equals(hands.token(hands.activeHand())))
            throw new GameTestAssertException("canonical weapon binding mismatch expected=" + expected + " actual=" + actual);
    }
}
