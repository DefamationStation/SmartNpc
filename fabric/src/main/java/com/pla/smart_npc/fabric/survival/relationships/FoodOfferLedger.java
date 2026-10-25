package com.pla.smart_npc.fabric.survival.relationships;

import net.minecraft.nbt.CompoundTag;
import java.util.*;

/** Bounded accepted-offer cooldowns only; this ledger grants no trust, rights or relationship rank. */
public final class FoodOfferLedger {
    public static final int VERSION=1,LIMIT=16;
    private final LinkedHashMap<UUID,Long> accepted=new LinkedHashMap<>();
    private long lastNpc=-1;
    private boolean writable=true;
    public boolean writable(){return writable;}
    public int size(){return accepted.size();}
    public boolean ready(UUID actor,long now,int pairCooldown,int npcCooldown){
        if(!writable||actor==null||now<0)return false;
        if(now<lastNpc){accepted.clear();lastNpc=-1;}
        Long previous=accepted.get(actor);
        return (lastNpc<0||now-lastNpc>=Math.clamp(npcCooldown,1,200))
                &&(previous==null||now-previous>=Math.clamp(pairCooldown,20,1200));
    }
    public void accepted(UUID actor,long now){
        if(!writable||actor==null||now<0)throw new IllegalStateException("Invalid or unsupported offer ledger");
        accepted.remove(actor);accepted.put(actor,now);lastNpc=now;
        while(accepted.size()>LIMIT)accepted.remove(accepted.keySet().iterator().next());
    }
    public static FoodOfferLedger load(CompoundTag tag){
        var result=new FoodOfferLedger();int version=tag.getIntOr("version",0);
        if(version<0||version>VERSION){result.writable=false;return result;}
        if(version==0)return result;
        for(int i=0;i<Math.clamp(tag.getIntOr("count",0),0,LIMIT);i++){
            var entry=tag.getCompoundOrEmpty("actor"+i);long when=entry.getLongOr("accepted",-1);
            try{if(when>=0)result.accepted(UUID.fromString(entry.getStringOr("uuid","")),when);}catch(IllegalArgumentException ignored){}
        }
        result.lastNpc=Math.max(result.lastNpc,tag.getLongOr("lastNpc",-1));return result;
    }
    public CompoundTag save(){
        if(!writable)throw new IllegalStateException("Future offer ledger is read-only");
        var tag=new CompoundTag();tag.putInt("version",VERSION);tag.putInt("count",accepted.size());tag.putLong("lastNpc",lastNpc);
        int i=0;for(var pair:accepted.entrySet()){var entry=new CompoundTag();entry.putString("uuid",pair.getKey().toString());entry.putLong("accepted",pair.getValue());tag.put("actor"+i++,entry);}return tag;
    }
}