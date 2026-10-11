package com.juicyslew.moonstation14.ms14.ui.preview;

import com.juicyslew.moonstation14.ms14.ui.client.paint.OrderedPaint;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/** Bounded deterministic raster backend for the production OrderedPaint contract. */
final class CpuOrderedPaint implements OrderedPaint {
    private final BufferedImage image;
    private final MinecraftBitmapFont font;
    private final Deque<State> stack=new ArrayDeque<>();
    private final Deque<Rect> clips=new ArrayDeque<>();
    private final Map<ResourceLocation,BufferedImage> sprites=new HashMap<>();
    private final int guiScale;
    private float tx,ty,sx=1,sy=1;
    CpuOrderedPaint(int width,int height,MinecraftBitmapFont font){this(width,height,font,1);}
    CpuOrderedPaint(int width,int height,MinecraftBitmapFont font,int guiScale){if(width<1||height<1||(long)width*height>16_000_000)throw new IllegalArgumentException("preview dimensions");if(guiScale<1||guiScale>8)throw new IllegalArgumentException("GUI scale outside supported bound");image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);this.font=font;this.guiScale=guiScale;clips.push(new Rect(0,0,width,height));}
    BufferedImage image(){return image;}
    public void fill(int l,int t,int r,int b,int color){int x0=px(l),y0=py(t),x1=px(r),y1=py(b);for(int y=Math.max(y0,clips.peek().t);y<Math.min(y1,clips.peek().b);y++)for(int x=Math.max(x0,clips.peek().l);x<Math.min(x1,clips.peek().r);x++)blend(x,y,color);}
    /**
     * Mirrors Font.StringRenderOutput (1.21.1): supplied y is the font render origin;
     * SheetGlyphInfo.getTop() is 7 - bearingTop, and BitmapProvider uses ascent as
     * bearingTop. Shadow RGB is dimmed by .25; bitmap shadow offset is 1.
     * Font width/lineHeight remain logical, unaffected by pose scaling.
     */
    public void text(Component c,int x,int y,int color,boolean shadow){requirePlain(c);draw(c.getString(),x,y,color,shadow);}
    private void draw(String s,int x,int y,int color,boolean shadow){for(int cp:s.codePoints().toArray()){int advance=Math.round(font.advance(cp));int left=px(x),top=py(y)+Math.round((7-font.ascent(cp))*sy);if(shadow){int dim=(color&0xff000000)|((int)(((color>>16)&255)*.25f)<<16)|((int)(((color>>8)&255)*.25f)<<8)|(int)((color&255)*.25f);raster(cp,left+Math.round(sx),top+Math.round(sy),dim);}raster(cp,left,top,color);x+=advance;}}
    private void raster(int cp,int x,int top,int color){var cell=new BufferedImage(Math.max(1,font.cellWidth(cp)),Math.max(1,font.cellHeight(cp)),BufferedImage.TYPE_INT_ARGB);font.drawCell(cell,0,0,cp,color,1,1);for(int yy=0;yy<cell.getHeight();yy++)for(int xx=0;xx<cell.getWidth();xx++){int sample=cell.getRGB(xx,yy),a=(sample>>>24)*(color>>>24)/255;if(a==0)continue;int ox=x+Math.round(xx*sx),oy=top+Math.round(yy*sy);int dw=Math.max(1,Math.round((xx+1)*sx))-Math.round(xx*sx),dh=Math.max(1,Math.round((yy+1)*sy))-Math.round(yy*sy);for(int dy=0;dy<dh;dy++)for(int dx=0;dx<dw;dx++)blend(ox+dx,oy+dy,(a<<24)|(sample&0xffffff));}}
    public int width(Component c){requirePlain(c);return width(c.getString());} public int width(String s){return (int)s.codePoints().mapToDouble(font::advance).sum();} public int lineHeight(){return 9;}
    public String plainSubstrByWidth(String s,int w){StringBuilder out=new StringBuilder();int n=0;for(int cp:s.codePoints().toArray()){int a=(int)font.advance(cp);if(n+a>w)break;out.appendCodePoint(cp);n+=a;}return out.toString();}
    public void push(){stack.push(new State(tx,ty,sx,sy));} public void pop(){if(stack.isEmpty())throw new IllegalStateException("paint pop underflow");var s=stack.pop();tx=s.tx;ty=s.ty;sx=s.sx;sy=s.sy;}
    public void translate(float x,float y,float z){tx+=x*sx;ty+=y*sy;} public void scale(float x,float y,float z){sx*=x;sy*=y;}
    /** Mirrors GuiGraphics.applyScissor: absolute GUI bounds are converted by Window.guiScale, never the pose. */
    public void clip(int l,int t,int r,int b){clips.push(intersect(clips.peek(),new Rect(l*guiScale,t*guiScale,r*guiScale,b*guiScale)));} public void unclip(){if(clips.size()<=1)throw new IllegalStateException("clip underflow");clips.pop();}
    public void sprite(ResourceLocation id,int x,int y,int w,int h,int u,int v,int sw,int sh,int tw,int th,int tint){
        try {var source=sprites.computeIfAbsent(id,k->{String path="assets/"+k.getNamespace()+"/"+k.getPath();try(var in=CpuOrderedPaint.class.getClassLoader().getResourceAsStream(path)){if(in!=null)return ImageIO.read(in);}catch(Exception e){throw new IllegalStateException("cannot load classpath sprite "+path,e);}Path root=Path.of(System.getProperty("ms14.preview.projectDir",System.getProperty("user.dir")));Path file=root.resolve("src/main/resources").resolve(path);try{return ImageIO.read(file.toFile());}catch(Exception e){throw new IllegalStateException("missing sprite "+file,e);}});if(source==null)throw new IllegalStateException("invalid sprite "+id);if(tw<=0||th<=0||sw<0||sh<0)throw new IllegalArgumentException("invalid sprite UV geometry");
            int dw=Math.abs(Math.round(w*sx)),dh=Math.abs(Math.round(h*sy));for(int dy=0;dy<dh;dy++)for(int dx=0;dx<dw;dx++){int ox=Math.round(tx+x*sx)+(sx<0?-dx:dx),oy=Math.round(ty+y*sy)+(sy<0?-dy:dy);if(!inside(ox,oy))continue;double uu=u+(dx+.5)*sw/Math.max(1,dw),vv=v+(dy+.5)*sh/Math.max(1,dh);if(uu<0||vv<0||uu>=tw||vv>=th)continue;int ix=Math.min(source.getWidth()-1,(int)(uu*source.getWidth()/tw)),iy=Math.min(source.getHeight()-1,(int)(vv*source.getHeight()/th));int p=source.getRGB(ix,iy),a=(p>>>24)*(tint>>>24)/255;int red=((p>>16)&255)*((tint>>16)&255)/255,green=((p>>8)&255)*((tint>>8)&255)/255,blue=(p&255)*(tint&255)/255;if(a!=0)blend(ox,oy,(a<<24)|(red<<16)|(green<<8)|blue);}
        }catch(RuntimeException e){throw e;}
    }
    private int px(float x){return Math.round(tx+x*sx);}private int py(float y){return Math.round(ty+y*sy);}private boolean inside(int x,int y){return x>=clips.peek().l&&x<clips.peek().r&&y>=clips.peek().t&&y<clips.peek().b&&x>=0&&y>=0&&x<image.getWidth()&&y<image.getHeight();}
    private static void requirePlain(Component c){if(!c.getStyle().isEmpty())throw new UnsupportedOperationException("styled Component text/metrics unsupported by CPU preview: "+c);for(Component sibling:c.getSiblings())requirePlain(sibling);}
    private void blend(int x,int y,int src){if(!inside(x,y))return;int a=src>>>24;if(a==255){image.setRGB(x,y,src);return;}int dst=image.getRGB(x,y),da=dst>>>24,oa=a+da*(255-a)/255;if(oa==0)return;int rr=(((src>>16)&255)*a+((dst>>16)&255)*da*(255-a)/255)/oa,gg=(((src>>8)&255)*a+((dst>>8)&255)*da*(255-a)/255)/oa,bb=((src&255)*a+(dst&255)*da*(255-a)/255)/oa;image.setRGB(x,y,(oa<<24)|(rr<<16)|(gg<<8)|bb);}
    private static Rect intersect(Rect a,Rect b){return new Rect(Math.max(a.l,b.l),Math.max(a.t,b.t),Math.max(Math.max(a.l,b.l),Math.min(a.r,b.r)),Math.max(Math.max(a.t,b.t),Math.min(a.b,b.b)));}
    private record Rect(int l,int t,int r,int b){} private record State(float tx,float ty,float sx,float sy){}
}
