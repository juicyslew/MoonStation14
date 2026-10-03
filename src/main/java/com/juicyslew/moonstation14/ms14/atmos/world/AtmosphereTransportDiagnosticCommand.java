package com.juicyslew.moonstation14.ms14.atmos.world;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Locale;

/** Operator-only, bounded loaded-cell transport evidence; does not trigger ownership discovery. */
@EventBusSubscriber(modid = MoonStation14.MOD_ID)
public final class AtmosphereTransportDiagnosticCommand {
    private AtmosphereTransportDiagnosticCommand() { }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ms14atmoscheck").requires(s -> s.hasPermission(2))
                .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(c -> check(c.getSource(), BlockPosArgument.getBlockPos(c, "pos")))));
    }

    private static int check(CommandSourceStack source, BlockPos pos) {
        if (!source.hasPermission(2) || !(source.getLevel() instanceof ServerLevel level)
                || level.getServer() == null || !level.getServer().isSameThread()) return 0;
        AtmosphereService service = AtmosphereService.INSTANCE;
        AtmosphereService.CellDiagnostic center = service.diagnosticCell(level, pos);
        send(source, "Atmosphere enabled=" + service.isEnabled() + " cell=" + pos.toShortString());
        send(source, cell("center", center));
        for (Direction d : Direction.values()) {
            BlockPos neighbor = pos.relative(d);
            var face = service.diagnosticCell(level, neighbor);
            boolean open = center.gas() != null && face.gas() != null && !face.status().startsWith("unknown")
                    && AtmosphereTopology.canExchange(level, pos, neighbor);
            boolean boundary = center.status().equals("finite") && face.status().equals("exterior") && open;
            send(source, cell(d.getName(), face) + " open-face=" + open + " boundary-eligible=" + boundary);
        }
        send(source, "Exterior policy=" + (center.exteriorConfiguredVacuum() ? "vacuum" : "ambient")
                + "; ambient=" + gas(center.ambient()));
        var timing = service.diagnosticTiming(level);
        send(source, timing == null ? "Due-step timing: no completed due step recorded"
                : String.format(Locale.ROOT, "Due step %d ms ownership=%.3f discovery/solve+commit=%.3f LINDA=%.3f reactions=%.3f total=%.3f",
                        timing.gameTime(), timing.ownershipMs(), timing.discoverySolveCommitMs(), timing.lindaMs(), timing.reactionsMs(), timing.totalMs()));
        if (timing != null) send(source, "Work snapshot: queue=" + timing.queued() + " dirty=" + timing.dirty() + " ownership=" + timing.ownershipPending()
                + "; boundary ledger cumulative signed gas=" + AtmosphereService.INSTANCE.boundaryGasLedger(level)
                + " energyJ=" + AtmosphereService.INSTANCE.boundaryEnergyLedger(level));
        var patch = service.diagnosticPatch(level);
        send(source, patch == null ? "Kernel: no recorded attempt" : "Kernel last attempt=" + patch.attempted() + " committed=" + patch.committed()
                + " status=" + patch.status() + " patch=" + patch.size() + " work=" + patch.expansions() + "/" + patch.calls()
                + " signed-export-moles=" + String.format(Locale.ROOT, "%.6g", patch.exportMoles()) + " energyJ=" + String.format(Locale.ROOT, "%.6g", patch.exportEnergy()));
        return 1;
    }

    private static String cell(String label, AtmosphereService.CellDiagnostic c) {
        return label + ": " + c.status() + (c.gas() == null ? "" : " " + gas(c.gas()));
    }
    private static String gas(GasMixture gas) {
        if (gas == null) return "unavailable";
        return String.format(Locale.ROOT, "P=%.3f kPa T=%.2f K total=%.6g mol O2=%.6g mol Trit=%.6g mol",
                gas.pressureKpa(1.0), gas.temperatureKelvin(), gas.totalMoles(), gas.moles(GasType.OXYGEN), gas.moles(GasType.TRITIUM));
    }
    private static void send(CommandSourceStack source, String text) { source.sendSuccess(() -> Component.literal(text), false); }
}
