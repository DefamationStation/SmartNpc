package com.pla.smart_npc.fabric.survival.relationships;
import net.minecraft.nbt.CompoundTag;
import java.util.UUID;
/** Versioned bounded personal relationship evidence, with a non-destructive future-schema boundary. */
public final class RelationshipCodec {
    public static final int VERSION=1;
    public record Loaded(RelationshipBook book,boolean writable){}
    private RelationshipCodec(){}
    public static Loaded load(CompoundTag tag){
        int version=tag.getIntOr("version",0);var book=new RelationshipBook();if(version<0||version>VERSION)return new Loaded(book,false);
        if(version==0)return new Loaded(book,true);
        for(int i=0;i<Math.clamp(tag.getIntOr("contacts",0),0,RelationshipBook.CONTACT_LIMIT);i++){
            var contact=tag.getCompoundOrEmpty("contact"+i);UUID actor;
            try{actor=UUID.fromString(contact.getStringOr("actor",""));}catch(IllegalArgumentException ignored){continue;}
            for(int j=0;j<Math.clamp(contact.getIntOr("events",0),0,RelationshipBook.EVENTS_PER_CONTACT);j++){
                var event=contact.getCompoundOrEmpty("event"+j);
                try{book.restore(actor,new RelationshipBook.Evidence(RelationshipBook.Kind.valueOf(event.getStringOr("kind","")),RelationshipBook.Source.valueOf(event.getStringOr("source","")),event.getStringOr("resource",""),event.getLongOr("seen",-1),event.getIntOr("units",0)));}catch(IllegalArgumentException|NullPointerException ignored){}
            }
        }
        return new Loaded(book,true);
    }
    public static CompoundTag save(Loaded state){
        if(!state.writable)throw new IllegalStateException("Future relationship data is read-only");
        var tag=new CompoundTag();tag.putInt("version",VERSION);var contacts=state.book.contacts();tag.putInt("contacts",contacts.size());int i=0;
        for(var contact:contacts){var c=new CompoundTag();c.putString("actor",contact.actor().toString());c.putInt("events",contact.evidence().size());int j=0;
            for(var e:contact.evidence()){var event=new CompoundTag();event.putString("kind",e.kind().name());event.putString("source",e.source().name());event.putString("resource",e.resource());event.putLong("seen",e.seen());event.putInt("units",e.units());c.put("event"+j++,event);}tag.put("contact"+i++,c);
        }
        return tag;
    }
}