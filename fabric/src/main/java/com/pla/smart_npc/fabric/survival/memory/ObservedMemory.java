package com.pla.smart_npc.fabric.survival.memory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Bounded evidence, not a world index or permission to access another inventory. */
public final class ObservedMemory {
    public static final int LIMIT=64;
    public enum Kind {
        RESOURCE(32,24_000,0),WORK_SITE(12,72_000,0),HOME(4,72_000,3),HAZARD(12,12_000,1),
        FAILED_ROUTE(12,1_200,1),SUCCESSFUL_ROUTE(12,24_000,0),HELP(16,72_000,2),HOSTILE(16,24_000,2);
        final int limit,priority;final long lifetime;
        Kind(int limit,long lifetime,int priority){this.limit=limit;this.lifetime=lifetime;this.priority=priority;}
        public long lifetime(){return lifetime;}
    }
    public enum Source { DIRECT_SIGHT,SELF_ACTION,OWN_HOME,EXPERIENCED_HAZARD,TERMINAL_ROUTE_FAILURE,ACTUAL_HANDOFF,CANONICAL_DEFENCE,LEGACY_OBSERVATION }
    public record Point(int x,int y,int z) {
        public Point {if(Math.abs((long)x)>30_000_000||Math.abs((long)z)>30_000_000||y < -2048||y>2047)throw new IllegalArgumentException("Invalid remembered point");}
        public double distanceSquared(Point p){double dx=(double)x-p.x,dy=(double)y-p.y,dz=(double)z-p.z;return dx*dx+dy*dy+dz*dz;}
    }
    public record Entry(Kind kind,String dimension,Point position,Point destination,UUID subject,
                        String detail,Source source,long seen,int confidence) {
        public Entry {
            Objects.requireNonNull(kind);Objects.requireNonNull(position);Objects.requireNonNull(source);
            if(dimension==null||dimension.isBlank()||dimension.length()>128||seen<0||confidence<1||confidence>100)
                throw new IllegalArgumentException("Invalid observed evidence");
            if(detail==null||detail.length()>128||detail.chars().anyMatch(c->c<32))throw new IllegalArgumentException("Invalid memory detail");
            if((kind==Kind.FAILED_ROUTE||kind==Kind.SUCCESSFUL_ROUTE)&&destination==null)throw new IllegalArgumentException("Route lacks destination");
            if((kind==Kind.HELP||kind==Kind.HOSTILE)&&subject==null)throw new IllegalArgumentException("Encounter lacks observed actor");
        }
        public boolean current(long now){return now>=seen&&now-seen<kind.lifetime;}
        public int confidenceAt(long now){return current(now)?Math.max(1,(int)(confidence*(kind.lifetime-(now-seen))/kind.lifetime)):0;}
        boolean sameFact(Entry other){return kind==other.kind&&dimension.equals(other.dimension)&&position.equals(other.position)
                &&Objects.equals(destination,other.destination)&&Objects.equals(subject,other.subject)&&detail.equals(other.detail);}
    }
    private final ArrayList<Entry> entries=new ArrayList<>();
    public List<Entry> entries(){return List.copyOf(entries);}
    public int size(){return entries.size();}
    public boolean observe(Entry entry){
        var previous=entries.stream().filter(e->e.sameFact(entry)).findFirst();
        if(previous.isPresent()&&previous.get().seen>entry.seen)return false;
        if(previous.isPresent()&&previous.get().equals(entry))return false;
        entries.removeIf(e->e.sameFact(entry));
        if(entry.kind==Kind.HOME)entries.removeIf(e->e.kind==Kind.HOME&&e.dimension.equals(entry.dimension));
        entries.add(entry);
        while(entries.stream().filter(e->e.kind==entry.kind).count()>entry.kind.limit)removeOldest(entry.kind);
        while(entries.size()>LIMIT){
            var victim=entries.stream().min(Comparator.comparingInt((Entry e)->e.kind.priority).thenComparingLong(Entry::seen)).orElseThrow();
            entries.remove(victim);
        }
        return true;
    }
    private void removeOldest(Kind kind){entries.stream().filter(e->e.kind==kind).min(Comparator.comparingLong(Entry::seen)).ifPresent(entries::remove);}
    public boolean expire(long now){return entries.removeIf(e->!e.current(now));}
    public boolean forget(Kind kind,String dimension,Point position){return entries.removeIf(e->e.kind==kind&&e.dimension.equals(dimension)&&e.position.equals(position));}
    public List<Entry> current(Kind kind,String dimension,long now,int minimumConfidence){
        return entries.stream().filter(e->e.kind==kind&&e.dimension.equals(dimension)&&e.current(now)&&e.confidenceAt(now)>=Math.max(1,minimumConfidence))
                .sorted(Comparator.comparingLong(Entry::seen).reversed()).toList();
    }
    public boolean failedRecently(String dimension,Point origin,Point destination,long now){
        return current(Kind.FAILED_ROUTE,dimension,now,1).stream().anyMatch(e->e.destination.equals(destination)&&e.position.distanceSquared(origin)<=16);
    }
    public boolean clearRouteFailure(String dimension,Point destination){return entries.removeIf(e->e.kind==Kind.FAILED_ROUTE&&e.dimension.equals(dimension)&&destination.equals(e.destination));}
}
