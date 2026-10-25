package com.pla.smart_npc.fabric.survival.memory;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.UUID;

/** Bounded UI throttling only; talking never creates social credit or progression. */
public final class DialogueLedger {
    public static final int LIMIT=16;
    public static final long PAIR_COOLDOWN=1200;
    private final LinkedHashMap<UUID,Long> listeners=new LinkedHashMap<>();
    private long lastSpoken=-1;
    public int size(){return listeners.size();}
    public boolean allow(UUID listener,long now,int npcCooldown){
        if(listener==null||now<0)return false;
        listeners.entrySet().removeIf(e->now<e.getValue()||now-e.getValue()>=PAIR_COOLDOWN);
        if(lastSpoken>now)lastSpoken=-1;
        if(lastSpoken>=0&&now-lastSpoken<Math.clamp(npcCooldown,200,1200)||listeners.containsKey(listener))return false;
        listeners.put(listener,now);lastSpoken=now;
        while(listeners.size()>LIMIT){var key=listeners.entrySet().stream().min(Comparator.comparingLong(java.util.Map.Entry::getValue)).orElseThrow().getKey();listeners.remove(key);}
        return true;
    }
}
