package com.pla.smart_npc.fabric.survival.memory;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import java.util.UUID;

/** One durable identity per save, independent of seed, level name and dimensions. */
public final class MemoryWorldIdentity extends SavedData {
    private static final SavedDataType<MemoryWorldIdentity> TYPE=new SavedDataType<>(
            Identifier.fromNamespaceAndPath("smart_npc","personal_memory_world"),MemoryWorldIdentity::fresh,
            CompoundTag.CODEC.xmap(MemoryWorldIdentity::load,MemoryWorldIdentity::save),null);
    private final UUID identity;
    private final CompoundTag futureData;
    private MemoryWorldIdentity(UUID identity,CompoundTag futureData){this.identity=identity;this.futureData=futureData;}
    static MemoryWorldIdentity fresh(){var result=new MemoryWorldIdentity(UUID.randomUUID(),null);result.setDirty();return result;}
    static MemoryWorldIdentity load(CompoundTag tag){
        int version=tag.getIntOr("version",0);UUID identity=null;
        try{identity=UUID.fromString(tag.getStringOr("identity",""));}catch(IllegalArgumentException ignored){ }
        if(version>1||version<0)return new MemoryWorldIdentity(identity==null?UUID.randomUUID():identity,tag.copy());
        return identity==null?fresh():new MemoryWorldIdentity(identity,null);
    }
    CompoundTag save(){
        if(futureData!=null)return futureData.copy();
        var result=new CompoundTag();result.putInt("version",1);result.putString("identity",identity.toString());return result;
    }
    public UUID identity(){return identity;}
    public static UUID identity(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(TYPE).identity;}
}
