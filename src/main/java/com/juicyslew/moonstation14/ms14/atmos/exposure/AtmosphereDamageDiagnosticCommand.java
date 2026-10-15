package com.juicyslew.moonstation14.ms14.atmos.exposure;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.ms14.player_body_control.action.BodyActionAuthority;
import com.juicyslew.moonstation14.ms14.organ.BodySystem;
import com.juicyslew.moonstation14.ms14.organ.RespirationState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Operator-only read-only point-in-time inspection; typed damage is not damage provenance. */
@EventBusSubscriber(modid = MoonStation14.MOD_ID)
public final class AtmosphereDamageDiagnosticCommand {
    static final String NO_EXACT_BODY_MESSAGE = "No exact controlled CHARACTER body; enter body and retry";

    private AtmosphereDamageDiagnosticCommand() { }

    static List<String> format(BlockPos eye, GasMixture gas, Double bodyKelvin,
                               RespirationState respiration, Map<String, Float> damage,
                               com.juicyslew.moonstation14.ms14.blood.BloodComponent blood, float health, float maxHealth) {
        List<String> lines = new ArrayList<>();
        lines.add("Eye block: " + eye.getX() + ", " + eye.getY() + ", " + eye.getZ());
        lines.add("Atmosphere: active");
        if (gas == null) {
            lines.add("Eye-cell gas: unavailable");
        } else {
            lines.add(String.format(Locale.ROOT, "Eye-cell gas: %.2f kPa, %.2f K; O2 %.4f mol, N2 %.4f mol, CO2 %.4f mol",
                    gas.pressureKpa(1.0), gas.temperatureKelvin(), gas.moles(GasType.OXYGEN),
                    gas.moles(GasType.NITROGEN), gas.moles(GasType.CARBON_DIOXIDE)));
        }
        lines.add(bodyKelvin == null ? "Body temperature: unavailable" : String.format(Locale.ROOT, "Body temperature: %.2f K", bodyKelvin));
        lines.add(respiration == null ? "Respiration: unavailable" : String.format(Locale.ROOT,
                "Respiration: initialized=%s, saturation=%.4f, phase=%s", respiration.initialized(),
                respiration.saturation(), respiration.phase()));
        lines.add("Typed damage: " + (damage == null ? "unavailable" : damage.toString()));
        lines.add(blood == null ? "Bleeding: blood state unavailable" : String.format(Locale.ROOT,
                "Bleeding: initialized=%s, bleed rate=%.2f", blood.initialized(), blood.bleedRate()));
        lines.add(String.format(Locale.ROOT, "Blood-loss damage ledger: %.2f",
                damage == null ? 0 : damage.getOrDefault(com.juicyslew.moonstation14.ms14.damage.DamageKeys.BLOODLOSS, 0f)));
        lines.add(String.format(Locale.ROOT, "Health: %.2f / %.2f", health, maxHealth));
        lines.add("Snapshot only; repeated snapshots distinguish rising type; typed key is not provenance.");
        return List.copyOf(lines);
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ms14damagecheck")
                .requires(source -> source.hasPermission(2))
                .executes(context -> check(context.getSource())));
    }

    private static int check(CommandSourceStack source) {
        if (!source.hasPermission(2) || !(source.getEntity() instanceof ServerPlayer player)
                || player instanceof FakePlayer || player.getServer() == null
                || !player.getServer().isSameThread()
                || player.getServer().getPlayerList().getPlayer(player.getUUID()) != player) return 0;

        var snapshot = BodyActionAuthority.resolve(player);
        if (snapshot.isEmpty() || !BodyActionAuthority.revalidate(player, snapshot.orElseThrow())) {
            source.sendFailure(Component.literal(NO_EXACT_BODY_MESSAGE));
            return 0;
        }
        var body = snapshot.orElseThrow().body();
        if (!(body.level() instanceof ServerLevel level)) return 0;
        BlockPos eye = BlockPos.containing(body.getEyePosition());
        GasMixture gas = AtmosphereService.INSTANCE.sample(level, eye).orElse(null);
        var temperature = body.getExistingDataOrNull(ModDataAttachments.BODY_TEMPERATURE.get());
        var damage = body.getExistingDataOrNull(ModDataAttachments.DAMAGE.get());
        RespirationState respiration = BodySystem.current(body).map(state -> state.respiration()).orElse(null);
        var blood = com.juicyslew.moonstation14.ms14.blood.BloodSystem.state(body).orElse(null);
        for (String line : format(eye, gas,
                temperature == null ? null : temperature.kelvin(), respiration, damage == null ? null : damage.getMap(),
                blood, body.getHealth(), body.getMaxHealth())) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }
}

