package com.juicyslew.moonstation14.ms14.combat;

/** Pure charge decision; caller supplies validated powered seconds and admission. */
public final class ChargerKernel {
    private ChargerKernel() { }
    public record Result(boolean accepted, float charge) { }
    public static Result charge(BatteryState state, BatteryProfile battery, ChargerProfile charger,
                                double poweredSeconds, boolean fullyAuthorized) {
        if (!fullyAuthorized || state == null || battery == null || charger == null ||
                !Double.isFinite(poweredSeconds) || poweredSeconds <= 0 ||
                state.charge() > battery.capacity()) return new Result(false, state == null ? 0 : state.charge());
        double amount = poweredSeconds * charger.chargeRate();
        float updated = (float) Math.min(battery.capacity(), state.charge() + amount);
        return new Result(updated > state.charge(), updated);
    }
}
