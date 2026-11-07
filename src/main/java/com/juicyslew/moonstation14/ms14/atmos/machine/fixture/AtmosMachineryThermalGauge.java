package com.juicyslew.moonstation14.ms14.atmos.machine.fixture;

/** Fixture-only visual evidence policy; inputs are measured finite-room/pipe changes and committed work. */
public final class AtmosMachineryThermalGauge {
    private AtmosMachineryThermalGauge() { }

    public static boolean warming(double beforeK,double nowK,double paidJ,double committedThermalJ) {
        return measuredChange(beforeK,nowK)&&nowK>beforeK+floatDeadband(beforeK,nowK)
                &&positiveWork(paidJ)&&positiveWork(committedThermalJ);
    }
    public static boolean cooling(double beforeK,double nowK,double paidJ,double committedThermalJ) {
        return measuredChange(beforeK,nowK)&&nowK<beforeK-floatDeadband(beforeK,nowK)
                &&positiveWork(paidJ)&&negativeWork(committedThermalJ);
    }
    /** True only when a committed passive-exchanger receipt agrees with measured opposite pipe/room changes. */
    public static boolean oppositeExchange(double roomBeforeJ,double roomNowJ,double pipeBeforeJ,double pipeNowJ,
                                            double committedPipeJ) {
        if(!measuredChange(roomBeforeJ,roomNowJ)||!measuredChange(pipeBeforeJ,pipeNowJ)
                ||!Double.isFinite(committedPipeJ))return false;
        double roomDelta=roomNowJ-roomBeforeJ,pipeDelta=pipeNowJ-pipeBeforeJ;
        return Math.abs(roomDelta)>floatDeadband(roomBeforeJ,roomNowJ)
                &&Math.abs(pipeDelta)>floatDeadband(pipeBeforeJ,pipeNowJ)
                &&Math.signum(roomDelta)==-Math.signum(pipeDelta)
                &&Math.signum(committedPipeJ)==Math.signum(pipeDelta)
                &&Math.abs(committedPipeJ)>energyDeadband(committedPipeJ);
    }
    public static double floatDeadband(double before,double now) {
        if(!Double.isFinite(before)||!Double.isFinite(now))return Double.POSITIVE_INFINITY;
        return Math.max(Math.ulp((float)Math.max(1,Math.abs(before)))*4d,
                Math.ulp((float)Math.max(1,Math.abs(now)))*4d);
    }
    private static double energyDeadband(double value){return Math.ulp((float)Math.max(1,Math.abs(value)))*4d;}
    private static boolean measuredChange(double before,double now){return Double.isFinite(before)&&Double.isFinite(now);}
    private static boolean positiveWork(double value){return Double.isFinite(value)&&value>energyDeadband(value);}
    private static boolean negativeWork(double value){return Double.isFinite(value)&&value< -energyDeadband(value);}
}
