package com.juicyslew.moonstation14.ms14.atmos.machine.thermal;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;

/** SS14 HeatExchangerSystem thermal step for an already-extracted gas packet; performs no flow. */
public final class PassiveHeatExchangePolicy {
    private static final double COSMIC_BACKGROUND_K = 2.7;
    private static final double REFERENCE_K = 293.15;
    public record Result(GasMixture packetAfter, GasMixture environmentAfter,
                         double radiatedEnergyJoules) { }
    private PassiveHeatExchangePolicy() { }

    public static Result exchange(GasMixture packet, GasMixture environment, double dtSeconds,
                                  double tileLoss, double alpha, double convectionK, boolean blocked) {
        if (packet == null || !Double.isFinite(dtSeconds) || dtSeconds < 0 || !Double.isFinite(tileLoss)
                || tileLoss < 0 || !Double.isFinite(alpha) || alpha < 0
                || !Double.isFinite(convectionK) || convectionK < 0)
            throw new IllegalArgumentException("invalid exchanger input");
        if (blocked || dtSeconds == 0 || packet.heatCapacity() <= 0)
            return new Result(packet, environment, 0);
        double cp = packet.heatCapacity();
        double envCp = environment == null ? 0 : environment.heatCapacity();
        boolean hasEnvironment = environment != null && environment.totalMoles() > 0 && envCp > 0;
        double tEnv = hasEnvironment ? environment.temperatureKelvin() : COSMIC_BACKGROUND_K;
        double inverseCapacity = 1 / cp + (hasEnvironment ? 1 / envCp : 0);
        double deltaT = packet.temperatureKelvin() - tEnv;
        double radiated = 0;
        GasMixture nextPacket = packet;
        GasMixture nextEnvironment = environment;
        if (tileLoss > 0 && alpha > 0 && deltaT != 0) {
            double k = alpha * tileLoss / Math.pow(REFERENCE_K, 4) * inverseCapacity;
            double nextDelta = deltaT * Math.pow(1 + 3 * k * dtSeconds * Math.pow(Math.abs(deltaT), 3), -1.0 / 3.0);
            double requested = (deltaT - nextDelta) / inverseCapacity;
            nextPacket = packet.withThermalEnergy(Math.max(0, packet.thermalEnergy() - requested));
            double actualPacketDelta = nextPacket.thermalEnergy() - packet.thermalEnergy();
            radiated = -actualPacketDelta;
            if (hasEnvironment) nextEnvironment = environment.withThermalEnergy(environment.thermalEnergy() - actualPacketDelta);
        }
        if (hasEnvironment) {
            double dT = nextPacket.temperatureKelvin() - nextEnvironment.temperatureKelvin();
            double k = convectionK * inverseCapacity;
            double requested = (dT - dT * Math.exp(-k * dtSeconds)) / inverseCapacity;
            double beforePacketEnergy = nextPacket.thermalEnergy();
            nextPacket = nextPacket.withThermalEnergy(Math.max(0, beforePacketEnergy - requested));
            double actualPacketDelta = nextPacket.thermalEnergy() - beforePacketEnergy;
            nextEnvironment = nextEnvironment.withThermalEnergy(Math.max(0, nextEnvironment.thermalEnergy() - actualPacketDelta));
        }
        return new Result(nextPacket, nextEnvironment, hasEnvironment ? 0 : radiated);
    }
}
