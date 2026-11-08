package com.juicyslew.moonstation14.ms14.combat;

/** Compact per-player trigger admission; revisions are deliberately checked per shot elsewhere. */
public final class GunTriggerPolicy {
    private long bodyEpoch=-1, lastId; private java.util.UUID body; private String hand,token,mode; private boolean open,semiUsed;
    public boolean admit(java.util.UUID body,long epoch,long id,String hand,String token,String mode,boolean start) {
        if(!java.util.Objects.equals(this.body,body)||bodyEpoch!=epoch){reset(body,epoch,hand,token,mode);lastId=0;}
        else if(!java.util.Objects.equals(this.hand,hand)||!java.util.Objects.equals(this.token,token)||!java.util.Objects.equals(this.mode,mode)) resetContext(hand,token,mode);
        if(start){if(id<=lastId)return false;lastId=id;open=true;semiUsed=false;}
        else if(!open||id!=lastId||!"FULL_AUTO".equals(mode))return false;
        if(!open)return false;
        if(!"FULL_AUTO".equals(mode)){if(semiUsed)return false;semiUsed=true;}
        return true;
    }
    public boolean stop(long epoch,long id,String hand,String token){if(open&&bodyEpoch==epoch&&lastId==id&&java.util.Objects.equals(this.hand,hand)&&java.util.Objects.equals(this.token,token)){open=false;return true;}return false;}
    public void clear(){reset(null,-1,null,null,null);lastId=0;}
    private void resetContext(String h,String t,String m){hand=h;token=t;mode=m;open=false;semiUsed=false;}
    private void reset(java.util.UUID b,long e,String h,String t,String m){body=b;bodyEpoch=e;hand=h;token=t;mode=m;open=false;semiUsed=false;}
}
