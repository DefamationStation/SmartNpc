package com.pla.smart_npc.fabric.survival.relationships;

import java.util.*;

/** Recent attributed evidence, never authority for aggression, ownership, team membership or access. */
public final class RelationshipBook {
    public static final int CONTACT_LIMIT=16,EVENTS_PER_CONTACT=8,EVENT_LIMIT=64;
    public static final long POSITIVE_WINDOW=1200,POSITIVE_TTL=72000,OFFENSE_TTL=24000;
    public enum Kind { ASSISTANCE,TRADE,ASSAULT,THEFT,VANDALISM,ALLY_DEFENCE }
    public enum Source { CONSERVED_HANDOFF,CONSERVED_EXCHANGE,CANONICAL_DEFENCE }
    public enum Stance { UNKNOWN,CAUTIOUS,ACQUAINTED,HELPFUL_HISTORY }
    public record Evidence(Kind kind,Source source,String resource,long seen,int units){
        public Evidence {
            Objects.requireNonNull(kind);Objects.requireNonNull(source);
            if(resource==null||resource.length()>160||!resource.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")||seen<0||units<1||units>64)throw new IllegalArgumentException("Invalid actual relationship evidence");
            if(kind==Kind.ASSISTANCE&&source!=Source.CONSERVED_HANDOFF||kind==Kind.TRADE&&source!=Source.CONSERVED_EXCHANGE
                    ||negative(kind)&&source!=Source.CANONICAL_DEFENCE)throw new IllegalArgumentException("Untrusted relationship source");
        }
        public boolean current(long now){return now>=seen&&now-seen<(negative(kind)?OFFENSE_TTL:POSITIVE_TTL);}
    }
    public record Contact(UUID actor,List<Evidence> evidence){public Contact{Objects.requireNonNull(actor);evidence=List.copyOf(evidence);}}
    public record Advice(Stance stance,int familiarity,int trust,int currentEvidence){ }
    private final LinkedHashMap<UUID,ArrayList<Evidence>> contacts=new LinkedHashMap<>();
    public static boolean negative(Kind kind){return kind==Kind.ASSAULT||kind==Kind.THEFT||kind==Kind.VANDALISM||kind==Kind.ALLY_DEFENCE;}
    public List<Contact> contacts(){return contacts.entrySet().stream().map(e->new Contact(e.getKey(),e.getValue())).toList();}
    public int contactCount(){return contacts.size();}
    public int eventCount(){return contacts.values().stream().mapToInt(List::size).sum();}
    public Advice advice(UUID actor,long now){
        var facts=contacts.getOrDefault(actor,new ArrayList<>()).stream().filter(e->e.current(now)).toList();
        if(facts.isEmpty())return new Advice(Stance.UNKNOWN,0,0,0);
        int score=0;for(var e:facts)score+=switch(e.kind){case ASSISTANCE->2;case TRADE->1;case ASSAULT->-20;case THEFT,VANDALISM->-12;case ALLY_DEFENCE->-10;};
        if(facts.stream().anyMatch(e->negative(e.kind)))score=Math.min(score,-1);
        score=Math.clamp(score,-100,20);int familiarity=Math.min(100,facts.size()*10);
        return new Advice(score<0?Stance.CAUTIOUS:score>=6?Stance.HELPFUL_HISTORY:Stance.ACQUAINTED,familiarity,score,facts.size());
    }
    /** Trusted successful-outcome producers only. Repeated positive events share one actor window. */
    public boolean record(UUID actor,Evidence actual){
        Objects.requireNonNull(actor);Objects.requireNonNull(actual);prune(actual.seen);
        var facts=contacts.get(actor);
        if(facts!=null&&!negative(actual.kind)&&facts.stream().anyMatch(e->!negative(e.kind)&&e.seen<=actual.seen&&actual.seen-e.seen<POSITIVE_WINDOW))return false;
        if(facts==null){facts=new ArrayList<>();contacts.put(actor,facts);}
        if(negative(actual.kind)){
            var previous=facts.stream().filter(e->e.kind==actual.kind).findFirst();
            if(previous.isPresent()&&previous.get().seen>=actual.seen)return false;
            facts.removeIf(e->e.kind==actual.kind);
        }
        facts.add(actual);
        while(facts.size()>EVENTS_PER_CONTACT)facts.remove(oldest(facts));
        while(contacts.size()>CONTACT_LIMIT){
            var victim=contacts.entrySet().stream().min(Comparator.<Map.Entry<UUID,ArrayList<Evidence>>>comparingInt(e->e.getValue().stream().anyMatch(f->negative(f.kind)&&f.current(actual.seen))?1:0)
                    .thenComparingLong(e->e.getValue().stream().mapToLong(Evidence::seen).max().orElse(-1))).orElseThrow();contacts.remove(victim.getKey());
        }
        while(eventCount()>EVENT_LIMIT){
            UUID owner=null;Evidence oldest=null;
            for(var entry:contacts.entrySet())for(var fact:entry.getValue())if(oldest==null||compareAge(fact,oldest)<0){owner=entry.getKey();oldest=fact;}
            contacts.get(owner).remove(oldest);if(contacts.get(owner).isEmpty())contacts.remove(owner);
        }
        return true;
    }
    private static int compareAge(Evidence a,Evidence b){int priority=Boolean.compare(negative(a.kind),negative(b.kind));return priority!=0?priority:Long.compare(a.seen,b.seen);}
    private static Evidence oldest(List<Evidence> facts){return facts.stream().min(RelationshipBook::compareAge).orElseThrow();}
    public boolean prune(long now){
        if(now<0)return false;int before=eventCount();
        contacts.values().forEach(facts->facts.removeIf(e->now>=e.seen&&!e.current(now)));contacts.values().removeIf(List::isEmpty);return before!=eventCount();
    }
    /** Bounded migration ingestion; loading older facts cannot manufacture fresh timestamps. */
    void restore(UUID actor,Evidence evidence){
        var facts=contacts.computeIfAbsent(actor,id->new ArrayList<>());if(contacts.size()>CONTACT_LIMIT){contacts.remove(actor);return;}
        if(negative(evidence.kind)){
            var prior=facts.stream().filter(e->e.kind==evidence.kind).findFirst();
            if(prior.isPresent()&&prior.get().seen>=evidence.seen)return;
            facts.removeIf(e->e.kind==evidence.kind);
        }else if(facts.stream().anyMatch(e->!negative(e.kind)&&Math.abs(e.seen-evidence.seen)<POSITIVE_WINDOW))return;
        if(facts.size()<EVENTS_PER_CONTACT&&eventCount()<EVENT_LIMIT)facts.add(evidence);
    }
}