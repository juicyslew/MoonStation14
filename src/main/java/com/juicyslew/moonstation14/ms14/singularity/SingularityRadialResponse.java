package com.juicyslew.moonstation14.ms14.singularity;

/** One Java authority for the SS14 coefficient and its C1 soft-threshold seam. */
public final class SingularityRadialResponse {
    public record ValueDerivative(double value, double derivative) { }
    private SingularityRadialResponse() { }

    public static ValueDerivative evaluate(double q, double intensity, double exponent, double cutoff) {
        double u=q/cutoff, u4=u*u*u*u;
        double raw=intensity*Math.pow(q,-exponent)*(1-u4);
        double rawD=-intensity*Math.pow(q,-exponent-1)*(exponent*(1-u4)+4*u4);
        if(raw<=.795) return new ValueDerivative(raw,rawD);
        double pow=Math.pow(raw,.3), powD=.3*Math.pow(raw,-.7);
        if(raw>=.805)return new ValueDerivative(pow,powD*rawD);
        double t=(raw-.795)/.01, blend=t*t*(3-2*t), blendD=6*t*(1-t)/.01;
        double value=raw+(pow-raw)*blend;
        double derivative=rawD+(powD-1)*rawD*blend+(pow-raw)*blendD*rawD;
        return new ValueDerivative(value,derivative);
    }
}
