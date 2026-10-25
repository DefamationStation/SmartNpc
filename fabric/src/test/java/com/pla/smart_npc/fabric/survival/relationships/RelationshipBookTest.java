package com.pla.smart_npc.fabric.survival.relationships;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class RelationshipBookTest {
    final UUID actor=new UUID(1,2),bystander=new UUID(3,4);
    RelationshipBook.Evidence help(long now,int units){return new RelationshipBook.Evidence(RelationshipBook.Kind.ASSISTANCE,RelationshipBook.Source.CONSERVED_HANDOFF,"minecraft:cooked_beef",now,units);}
    RelationshipBook.Evidence offense(RelationshipBook.Kind kind,long now){return new RelationshipBook.Evidence(kind,RelationshipBook.Source.CANONICAL_DEFENCE,"smart_npc:observed",now,1);}
    @Test void actualHelpIsModestAndAttributedOnlyToExactUuid(){
        var book=new RelationshipBook();book.record(actor,help(100,1));assertEquals(2,book.advice(actor,101).trust());assertEquals(10,book.advice(actor,101).familiarity());
        assertEquals(RelationshipBook.Stance.ACQUAINTED,book.advice(actor,101).stance());assertEquals(RelationshipBook.Stance.UNKNOWN,book.advice(bystander,101).stance());
    }
    @Test void repeatedOffersAndLargeStacksCannotFarmImmediateLoyalty(){
        var book=new RelationshipBook();assertTrue(book.record(actor,help(100,64)));
        for(int i=101;i<1300;i++)assertFalse(book.record(actor,help(i,64)));
        assertEquals(2,book.advice(actor,1299).trust());assertTrue(book.record(actor,help(1300,1)));
        for(int i=2;i<100;i++)book.record(actor,help(100+i*1200L,64));
        assertTrue(book.advice(actor,118901).trust()<=16);assertTrue(book.eventCount()<=RelationshipBook.EVENTS_PER_CONTACT);
    }
    @Test void positiveHistoryDoesNotForgiveActiveOffenseOrReplaceItsCause(){
        var book=new RelationshipBook();book.record(actor,offense(RelationshipBook.Kind.THEFT,100));
        for(int i=0;i<20;i++)book.record(actor,help(100+i*1200L,1));
        var advice=book.advice(actor,22901);assertEquals(RelationshipBook.Stance.CAUTIOUS,advice.stance());assertTrue(advice.trust()<0);
        assertTrue(book.contacts().getFirst().evidence().stream().anyMatch(e->e.kind()==RelationshipBook.Kind.THEFT));
    }
    @Test void relationshipSourcesCannotChangeTheMeaningOfActualEvents(){
        assertThrows(IllegalArgumentException.class,()->new RelationshipBook.Evidence(RelationshipBook.Kind.ASSAULT,RelationshipBook.Source.CONSERVED_HANDOFF,"smart_npc:observed",1,1));
        assertThrows(IllegalArgumentException.class,()->new RelationshipBook.Evidence(RelationshipBook.Kind.ASSISTANCE,RelationshipBook.Source.CANONICAL_DEFENCE,"smart_npc:observed",1,1));
        assertThrows(IllegalArgumentException.class,()->help(-1,1));assertThrows(IllegalArgumentException.class,()->help(1,0));
    }
    @Test void expiryAndClockRollbackDoNotInventCurrentRelationshipFacts(){
        var book=new RelationshipBook();book.record(actor,help(100,1));assertEquals(RelationshipBook.Stance.UNKNOWN,book.advice(actor,99).stance());
        assertFalse(book.prune(99));assertEquals(1,book.eventCount());assertEquals(RelationshipBook.Stance.UNKNOWN,book.advice(actor,72100).stance());assertTrue(book.prune(72100));assertEquals(0,book.contactCount());
    }
    @Test void contactAndGlobalCapsHoldAndCurrentOffenseSurvivesHelpChurn(){
        var book=new RelationshipBook();book.record(actor,offense(RelationshipBook.Kind.ASSAULT,100));
        for(int i=0;i<1000;i++)book.record(new UUID(9,i),help(1000+i,1));
        assertTrue(book.contactCount()<=RelationshipBook.CONTACT_LIMIT);assertTrue(book.eventCount()<=RelationshipBook.EVENT_LIMIT);assertEquals(RelationshipBook.Stance.CAUTIOUS,book.advice(actor,2000).stance());
        var mixed=new RelationshipBook();for(int i=0;i<16;i++)for(int j=0;j<8;j++)mixed.record(new UUID(8,i),help(j*1200L,1));
        assertTrue(mixed.eventCount()<=RelationshipBook.EVENT_LIMIT);for(var c:mixed.contacts())assertTrue(c.evidence().size()<=RelationshipBook.EVENTS_PER_CONTACT);
    }
    @Test void codecRetainsExactIdentityCauseResourceUnitsAndOriginalDates(){
        var book=new RelationshipBook();book.record(actor,help(100,3));book.record(bystander,offense(RelationshipBook.Kind.VANDALISM,200));
        var state=new RelationshipCodec.Loaded(book,true);var tag=RelationshipCodec.save(state);var loaded=RelationshipCodec.load(tag);
        assertEquals(book.contacts(),loaded.book().contacts());assertEquals(tag,RelationshipCodec.save(loaded));
    }
    @Test void unsupportedFutureDataIsReadOnlyAndNeverOverwritten(){
        var tag=new CompoundTag();tag.putInt("version",99);tag.putString("future","untouched");var before=tag.copy();var loaded=RelationshipCodec.load(tag);
        assertFalse(loaded.writable());assertEquals(0,loaded.book().contactCount());assertThrows(IllegalStateException.class,()->RelationshipCodec.save(loaded));assertEquals(before,tag);
    }
    @Test void corruptAndDuplicateCodecEntriesCannotAmplifyTrust(){
        var book=new RelationshipBook();book.record(actor,help(100,1));var tag=RelationshipCodec.save(new RelationshipCodec.Loaded(book,true));var contact=tag.getCompoundOrEmpty("contact0");
        contact.putInt("events",Integer.MAX_VALUE);for(int i=1;i<8;i++)contact.put("event"+i,contact.getCompoundOrEmpty("event0").copy());tag.put("contact0",contact);
        var loaded=RelationshipCodec.load(tag);assertEquals(1,loaded.book().eventCount());assertEquals(2,loaded.book().advice(actor,101).trust());
        contact.putString("actor","not-a-uuid");tag.put("contact0",contact);assertEquals(0,RelationshipCodec.load(tag).book().eventCount());assertEquals(0,RelationshipCodec.load(new CompoundTag()).book().eventCount());
    }
}