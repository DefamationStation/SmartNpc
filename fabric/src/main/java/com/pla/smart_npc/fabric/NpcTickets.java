package com.pla.smart_npc.fabric;

import java.util.*;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

/** Ref-counted NPC-owned tickets; never clears another NPC's or mod's ticket. */
public final class NpcTickets {
    public static final TicketType TYPE=Registry.register(BuiltInRegistries.TICKET_TYPE,Identifier.fromNamespaceAndPath("smart_npc","npc_active"),new TicketType(0L,TicketType.FLAG_LOADING|TicketType.FLAG_SIMULATION|TicketType.FLAG_KEEP_DIMENSION_ACTIVE));
    private final Identifier id;
    private final Map<ServerLevel,Map<ChunkPos,Set<UUID>>> owners=new WeakHashMap<>();
    public NpcTickets(Identifier id){this.id=id;}
    public Identifier id(){return id;}
    public void forceChunk(ServerLevel level,UUID owner,int x,int z,boolean add,boolean ticking){
        var chunks=owners.computeIfAbsent(level,l->new HashMap<>());var pos=new ChunkPos(x,z);
        if(add){var set=chunks.computeIfAbsent(pos,p->new HashSet<>());if(set.add(owner)&&set.size()==1)level.getChunkSource().addTicketWithRadius(TYPE,pos,2);}
        else {var set=chunks.get(pos);if(set!=null&&set.remove(owner)&&set.isEmpty()){chunks.remove(pos);level.getChunkSource().removeTicketWithRadius(TYPE,pos,2);}}
    }
    public void clear(){for(var e:owners.entrySet())for(var pos:e.getValue().keySet())e.getKey().getChunkSource().removeTicketWithRadius(TYPE,pos,2);owners.clear();}
}
