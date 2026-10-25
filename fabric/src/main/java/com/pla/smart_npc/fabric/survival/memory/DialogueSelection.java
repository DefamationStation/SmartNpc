package com.pla.smart_npc.fabric.survival.memory;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Chooses only supplied current evidence. Spatial RESOURCE candidates must be revalidated by caller. */
public final class DialogueSelection {
    public enum Type { BUSY,UNKNOWN,THANKS,HOSTILE_HISTORY,FAILED_ROUTE,HAZARD,RESOURCE,WORK_SITE,HOME,SUCCESSFUL_ROUTE }
    public record Reply(Type type,ObservedMemory.Entry evidence) { }
    private DialogueSelection(){ }
    public static Reply choose(UUID listener,String dimension,long now,NpcPersonality personality,
                               boolean actuallyWorking,List<ObservedMemory.Entry> eligibleEvidence){
        if(actuallyWorking&&personality.diligence()>=65)return new Reply(Type.BUSY,null);
        return eligibleEvidence.stream().filter(e->e.dimension().equals(dimension)&&e.current(now)&&e.confidenceAt(now)>=30&&eligible(e,listener))
                .max(Comparator.comparingInt((ObservedMemory.Entry e)->score(e,personality)).thenComparingLong(ObservedMemory.Entry::seen))
                .map(e->new Reply(type(e.kind()),e)).orElseGet(()->new Reply(Type.UNKNOWN,null));
    }
    private static boolean eligible(ObservedMemory.Entry e,UUID listener){
        return switch(e.kind()){
            case HELP -> e.source()==ObservedMemory.Source.ACTUAL_HANDOFF&&listener.equals(e.subject());
            case HOSTILE -> e.source()==ObservedMemory.Source.CANONICAL_DEFENCE&&listener.equals(e.subject());
            case RESOURCE -> e.source()==ObservedMemory.Source.DIRECT_SIGHT;
            case FAILED_ROUTE -> e.source()==ObservedMemory.Source.TERMINAL_ROUTE_FAILURE;
            case HAZARD -> e.source()==ObservedMemory.Source.EXPERIENCED_HAZARD;
            case HOME -> e.source()==ObservedMemory.Source.OWN_HOME;
            case WORK_SITE -> e.source()==ObservedMemory.Source.SELF_ACTION||e.source()==ObservedMemory.Source.LEGACY_OBSERVATION;
            case SUCCESSFUL_ROUTE -> e.source()==ObservedMemory.Source.SELF_ACTION;
        };
    }
    private static int score(ObservedMemory.Entry e,NpcPersonality p){
        return switch(e.kind()){
            case HOSTILE -> 100;
            case HELP -> 90;
            case HAZARD -> 50+p.caution()/10;
            case FAILED_ROUTE -> 45+p.caution()/20;
            case RESOURCE -> 20+p.curiosity()/5+p.generosity()/10;
            case WORK_SITE -> 25+p.diligence()/10;
            case HOME -> 22+(100-p.curiosity())/10;
            case SUCCESSFUL_ROUTE -> 20+p.curiosity()/10;
        };
    }
    private static Type type(ObservedMemory.Kind kind){return switch(kind){
        case HELP->Type.THANKS;case HOSTILE->Type.HOSTILE_HISTORY;case FAILED_ROUTE->Type.FAILED_ROUTE;
        case HAZARD->Type.HAZARD;case RESOURCE->Type.RESOURCE;case WORK_SITE->Type.WORK_SITE;case HOME->Type.HOME;case SUCCESSFUL_ROUTE->Type.SUCCESSFUL_ROUTE;
    };}
}
