package com.juicyslew.moonstation14.ms14.mobs;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.components.DamageableComponent;
import com.juicyslew.moonstation14.ms14.character.components.MobStateComponent;
import com.juicyslew.moonstation14.ms14.damage.DamageKeys;
import com.juicyslew.moonstation14.ms14.damage.DamageSystem;
import com.juicyslew.moonstation14.ms14.player_body_control.action.BodyActionAuthority;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Map;

/** Ordinary-player testing command applying lethal typed Bloodloss to the exact controlled body. */
@EventBusSubscriber(modid = MoonStation14.MOD_ID)
public final class SuicideCommand {
    private SuicideCommand() { }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("suicide")
                .executes(context -> execute(context.getSource())));
    }

    private static int execute(net.minecraft.commands.CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer
                || player.getServer() == null || !player.getServer().isSameThread()
                || player.getServer().getPlayerList().getPlayer(player.getUUID()) != player) return 0;
        var snapshot = BodyActionAuthority.resolve(player).orElse(null);
        if (snapshot == null || !BodyActionAuthority.revalidate(player, snapshot)) {
            source.sendFailure(Component.literal("You must be controlling a live character body to do that."));
            return 0;
        }
        var body = snapshot.body();
        var character = CharacterIdentitySystem.resolveForActor(body).orElse(null);
        var damageable = character == null ? null : character.component(DamageableComponent.class).orElse(null);
        var policy = MobStateSystem.policy(body);
        if (!medicalEligibility(damageable, policy)) {
            source.sendFailure(Component.literal("This body cannot be killed through the medical damage system."));
            return 0;
        }
        double dead = policy.thresholds().thresholds().entrySet().stream()
                .filter(entry -> entry.getValue() == MobStateComponent.State.DEAD)
                .mapToDouble(Map.Entry::getKey).min().orElse(Double.NaN);
        if (!Double.isFinite(dead) || dead <= 0) return 0;
        var ledger = body.getExistingDataOrNull(ModDataAttachments.DAMAGE.get());
        double total = ledger == null ? 0 : ledger.getMap().values().stream().mapToDouble(Float::doubleValue).sum();
        double needed = dead - total;
        if (!Double.isFinite(needed) || needed <= 0 || needed > Float.MAX_VALUE) return 0;
        float delta = (float) needed;
        if (!Float.isFinite(delta) || delta <= 0) return 0;
        if (delta < needed) delta = Math.nextUp(delta);
        DamageSystem.applyHealthChange(body, Map.of(DamageKeys.BLOODLOSS, delta), 1f, true,
                DamageSystem.Cause.BLOOD_LOSS);
        if (MobStateSystem.isDead(body)) {
            source.sendSuccess(() -> Component.literal("You are dead. Move to enter the normal ghost state."), false);
            return 1;
        }
        source.sendFailure(Component.literal("The damage did not kill your character; no death was recorded."));
        return 0;
    }

    static boolean medicalEligibility(DamageableComponent damageable, com.juicyslew.moonstation14.ms14.mobs.MobStatePolicy policy) {
        return damageable != null && com.juicyslew.moonstation14.ms14.medical.MedicalTreatmentService
                .acceptsDamageContainer(java.util.List.of("biological"), damageable.damageContainer())
                && policy != null && policy.states().allowedStates().contains(MobStateComponent.State.DEAD);
    }
}
