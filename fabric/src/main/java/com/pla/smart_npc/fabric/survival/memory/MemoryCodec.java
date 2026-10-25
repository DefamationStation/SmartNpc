package com.pla.smart_npc.fabric.survival.memory;

import net.minecraft.nbt.CompoundTag;
import java.util.UUID;

/** Missing legacy data migrates lazily. Unknown future versions remain untouched. */
public final class MemoryCodec {
    public static final int VERSION=1;
    public record Loaded(NpcPersonality personality,ObservedMemory observations,String worldScope,boolean writable) { }
    private MemoryCodec(){ }
    public static Loaded load(CompoundTag tag,UUID id,String currentWorldScope){
        int version=tag.getIntOr("version",0);var fallback=NpcPersonality.seeded(id);
        if(version>VERSION||version<0)return new Loaded(fallback,new ObservedMemory(),currentWorldScope,false);
        if(version==0)return new Loaded(fallback,new ObservedMemory(),currentWorldScope,true);
        var p=tag.getCompoundOrEmpty("personality");
        var personality=new NpcPersonality(p.getIntOr("sociability",fallback.sociability()),p.getIntOr("caution",fallback.caution()),
                p.getIntOr("generosity",fallback.generosity()),p.getIntOr("curiosity",fallback.curiosity()),p.getIntOr("diligence",fallback.diligence()));
        String scope=tag.getStringOr("world","");var observations=new ObservedMemory();
        if(!scope.equals(currentWorldScope))return new Loaded(personality,observations,currentWorldScope,true);
        for(int index=0;index<Math.clamp(tag.getIntOr("count",0),0,ObservedMemory.LIMIT);index++){
            var e=tag.getCompoundOrEmpty("entry"+index);
            try{
                var kind=ObservedMemory.Kind.valueOf(e.getStringOr("kind",""));
                var source=ObservedMemory.Source.valueOf(e.getStringOr("source",""));
                var destination=e.contains("destination")?point(e.getCompoundOrEmpty("destination")):null;
                UUID subject=e.contains("subject")?UUID.fromString(e.getStringOr("subject","")):null;
                observations.observe(new ObservedMemory.Entry(kind,e.getStringOr("dimension",""),point(e.getCompoundOrEmpty("position")),
                        destination,subject,e.getStringOr("detail",""),source,e.getLongOr("seen",-1),e.getIntOr("confidence",0)));
            }catch(IllegalArgumentException|NullPointerException ignored){ /* Invalid evidence cannot create a destination. */ }
        }
        return new Loaded(personality,observations,currentWorldScope,true);
    }
    public static CompoundTag save(Loaded state){
        if(!state.writable)throw new IllegalStateException("Unknown future memory version is read-only");
        var tag=new CompoundTag();tag.putInt("version",VERSION);tag.putString("world",state.worldScope);
        var p=new CompoundTag();var personality=state.personality;
        p.putInt("sociability",personality.sociability());p.putInt("caution",personality.caution());p.putInt("generosity",personality.generosity());
        p.putInt("curiosity",personality.curiosity());p.putInt("diligence",personality.diligence());tag.put("personality",p);
        var entries=state.observations.entries();tag.putInt("count",entries.size());
        for(int index=0;index<entries.size();index++){
            var observation=entries.get(index);var e=new CompoundTag();e.putString("kind",observation.kind().name());
            e.putString("dimension",observation.dimension());e.put("position",point(observation.position()));
            if(observation.destination()!=null)e.put("destination",point(observation.destination()));
            if(observation.subject()!=null)e.putString("subject",observation.subject().toString());
            e.putString("detail",observation.detail());e.putString("source",observation.source().name());
            e.putLong("seen",observation.seen());e.putInt("confidence",observation.confidence());tag.put("entry"+index,e);
        }
        return tag;
    }
    private static ObservedMemory.Point point(CompoundTag tag){
        if(!tag.contains("x")||!tag.contains("y")||!tag.contains("z"))throw new IllegalArgumentException("Missing point");
        int x=tag.getIntOr("x",Integer.MAX_VALUE),y=tag.getIntOr("y",Integer.MAX_VALUE),z=tag.getIntOr("z",Integer.MAX_VALUE);
        if(Math.abs((long)x)>30_000_000||Math.abs((long)z)>30_000_000||y < -2048||y>2047)throw new IllegalArgumentException("Invalid point");
        return new ObservedMemory.Point(x,y,z);
    }
    private static CompoundTag point(ObservedMemory.Point p){var tag=new CompoundTag();tag.putInt("x",p.x());tag.putInt("y",p.y());tag.putInt("z",p.z());return tag;}
}
