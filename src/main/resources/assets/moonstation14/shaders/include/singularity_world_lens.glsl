uniform int MS14LensCount;
uniform mat4 MS14LensProjection;
uniform mat4 MS14LensInverseProjection;
uniform vec4 MS14LensSource0; // eye xyz, raw profile intensity
uniform vec4 MS14LensSource1;
uniform vec4 MS14LensSource2;
uniform vec4 MS14LensSource3;
uniform vec4 MS14LensSource4;
uniform vec4 MS14LensMeta0; // falloff, cutoff SS pixels, strength*visibility, foreground ramp world
uniform vec4 MS14LensMeta1;
uniform vec4 MS14LensMeta2;
uniform vec4 MS14LensMeta3;
uniform vec4 MS14LensMeta4;

float MS14LensSmooth(float x) { x=clamp(x,0.0,1.0); return x*x*(3.0-2.0*x); }
vec2 MS14LensResponse(float q, float intensity, float exponent, float cutoff) {
    float u=q/cutoff, u4=u*u*u*u;
    float raw=intensity*pow(q,-exponent)*(1.0-u4);
    float rawD=-intensity*pow(q,-exponent-1.0)*(exponent*(1.0-u4)+4.0*u4);
    if(raw<=0.795) return vec2(raw,rawD);
    float powered=pow(raw,0.3), poweredD=0.3*pow(raw,-0.7);
    if(raw>=0.805) return vec2(powered,poweredD*rawD);
    float t=(raw-0.795)/0.01, blend=t*t*(3.0-2.0*t), blendD=6.0*t*(1.0-t)/0.01;
    return vec2(raw+(powered-raw)*blend,
        rawD+(poweredD-1.0)*rawD*blend+(powered-raw)*blendD*rawD);
}
float MS14LensForward(float q,float intensity,float exponent,float cutoff,float amount) {
    return q*(1.0-amount*MS14LensResponse(q,intensity,exponent,cutoff).x);
}
float MS14LensDerivative(float q,float intensity,float exponent,float cutoff,float amount) {
    vec2 rd=MS14LensResponse(q,intensity,exponent,cutoff);
    return 1.0-amount*(rd.x+q*rd.y);
}
float MS14LensRadius(float r,float intensity,float exponent,float cutoff,float amount) {
    if(amount<=0.0 || r>=cutoff) return r;
    if(r<0.01) {
        float lo=0.01, hi=cutoff;
        for(int j=0;j<24;j++){float mid=(lo+hi)*0.5;if(MS14LensForward(mid,intensity,exponent,cutoff,amount)<0.01)lo=mid;else hi=mid;}
        float b=(lo+hi)*0.5;
        float m=clamp(0.01/(b*MS14LensDerivative(b,intensity,exponent,cutoff,amount)),0.0,1.0);
        float t=r/0.01;
        return b*((2.0-m)*t-(1.0-m)*t*t);
    }
    float lo=r, hi=cutoff;
    for(int j=0;j<24;j++){float mid=(lo+hi)*0.5;if(MS14LensForward(mid,intensity,exponent,cutoff,amount)<r)lo=mid;else hi=mid;}
    return (lo+hi)*0.5;
}
vec4 MS14LensGetSource(int i) {
    if(i==0)return MS14LensSource0; if(i==1)return MS14LensSource1;
    if(i==2)return MS14LensSource2; if(i==3)return MS14LensSource3; return MS14LensSource4;
}
vec4 MS14LensGetMeta(int i) {
    if(i==0)return MS14LensMeta0; if(i==1)return MS14LensMeta1;
    if(i==2)return MS14LensMeta2; if(i==3)return MS14LensMeta3; return MS14LensMeta4;
}
vec4 MS14LensClip(vec4 originalClip) {
    if(MS14LensCount<=0) return originalClip;
    vec4 eyeH=MS14LensInverseProjection*originalClip;
    if(abs(eyeH.w)<1e-20) return originalClip;
    vec3 p=eyeH.xyz/eyeH.w;
    for(int i=4;i>=0;i--) {
        if(i>=MS14LensCount) continue;
        vec4 src=MS14LensGetSource(i), meta=MS14LensGetMeta(i);
        vec3 c=src.xyz;
        float D=length(c); if(D<=0.0) continue;
        vec3 n=c/D;
        float s=dot(p,n); if(s<=0.0) continue;
        vec3 v=p-n*s;
        float r=32.0*length(v)*D/s;
        float cutoff=meta.y, ramp=meta.w;
        float W=ramp>0.0?MS14LensSmooth(1.0-(D-s)/ramp):(s>=D?1.0:0.0);
        float amount=W*meta.z;
        if(amount<=0.0 || r>=cutoff) continue;
        float q=MS14LensRadius(r,src.w,meta.x,cutoff,amount);
        p=n*s+v*(r>0.0?q/r:1.0);
    }
    return MS14LensProjection*vec4(p,1.0);
}
