package com.pla.smart_npc.fabric.survival.preparation;

import java.util.*;
import com.pla.smart_npc.fabric.survival.readiness.ExpeditionReadiness;

/** Advice for an optional explicit commitment. Native supply goals are never gated. */
public final class PreparedExpeditionPolicy {
    private PreparedExpeditionPolicy() { }
    public enum Reason { UNSAFE_OR_UNAVAILABLE, EXISTING_COMMITMENT, EARN_COOKED_FOOD, EARN_SAFE_REST }
    public record Evidence(boolean safeAwake,boolean idle,boolean preparedFood,boolean safeRest,ExpeditionReadiness.Assessment materials){
        public Evidence {Objects.requireNonNull(materials);}
    }
    public record Decision(List<Reason> reasons,List<ExpeditionReadiness.Missing> materials){
        public Decision {reasons=List.copyOf(reasons);materials=List.copyOf(materials);}
        public boolean accepted(){return reasons.isEmpty()&&materials.isEmpty();}
    }
    public static Decision assess(Evidence facts){
        var reasons=new ArrayList<Reason>();
        if(!facts.safeAwake())reasons.add(Reason.UNSAFE_OR_UNAVAILABLE);
        if(!facts.idle())reasons.add(Reason.EXISTING_COMMITMENT);
        if(!facts.preparedFood())reasons.add(Reason.EARN_COOKED_FOOD);
        if(!facts.safeRest())reasons.add(Reason.EARN_SAFE_REST);
        return new Decision(reasons,facts.materials().missing());
    }
}
