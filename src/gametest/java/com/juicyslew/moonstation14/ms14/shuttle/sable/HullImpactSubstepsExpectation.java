package com.juicyslew.moonstation14.ms14.shuttle.sable;

/** Read-only configured-versus-expected substep guard used by the diagnostic GameTests. */
public final class HullImpactSubstepsExpectation {
    private HullImpactSubstepsExpectation() { }

    static Integer parseExpected(String configured) {
        if(configured==null)return null;
        final int expected;
        try { expected=Integer.parseInt(configured); }
        catch(NumberFormatException invalid) {
            throw new IllegalArgumentException("expected substeps must be an integer from 1 through 10",invalid);
        }
        if(expected<1||expected>10)
            throw new IllegalArgumentException("expected substeps must be in range 1 through 10");
        return expected;
    }

    static void requireExpected(int actual,String configuredExpected) {
        if(actual<1||actual>10)
            throw new IllegalStateException("SDK-reported substeps outside supported range: "+actual);
        Integer expected=parseExpected(configuredExpected);
        if(expected!=null&&actual!=expected)
            throw new IllegalStateException("effective SDK substeps "+actual+" did not match expected "+expected);
    }

    public static void requireNormalSix(net.minecraft.server.level.ServerLevel level, String caseName) {
        int actual = dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem.require(level)
                .getConfig().substepsPerTick;
        com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                "[CABIN_NATIVE_CONFIG] case={} effective={} expected=6", caseName, actual);
        requireExpected(actual, "6");
    }
}
