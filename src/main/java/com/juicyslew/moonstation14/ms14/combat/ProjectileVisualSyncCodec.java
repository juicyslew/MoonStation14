package com.juicyslew.moonstation14.ms14.combat;

import net.minecraft.resources.ResourceLocation;

/** Codec for the existing projectile VISUAL synchronized string. */
public final class ProjectileVisualSyncCodec {
    private ProjectileVisualSyncCodec() { }
    public static String encode(ProjectileProfile.SpriteData s) {
        if(s==null)return "";
        return s.texture()+"|"+s.state()+"|"+s.frameWidth()+"|"+s.frameHeight()+"|"+s.frameCount()+"|"+s.frameSeconds()+"|"+s.unshaded()+"|"+s.noRot()+"|"+Integer.toUnsignedString(s.color())+"|"+s.frameColumns()+"|"+s.rotationOffset();
    }
    public static ProjectileProfile.SpriteData decode(String value) {
        try {
            String[] p=value.split("\\|",-1);
            if(p.length<8||p.length>11)return null;
            int count=Integer.parseInt(p[4]); int columns=p.length>=10?Integer.parseInt(p[9]):count;
            int color=p.length>=9?(int)Long.parseLong(p[8]):0xffffffff;
            float offset=p.length==11?Float.parseFloat(p[10]):0f;
            return new ProjectileProfile.SpriteData(ResourceLocation.parse(p[0]),p[1],Integer.parseInt(p[2]),Integer.parseInt(p[3]),count,columns,Float.parseFloat(p[5]),Boolean.parseBoolean(p[6]),Boolean.parseBoolean(p[7]),color,offset);
        } catch(RuntimeException e){return null;}
    }
}
