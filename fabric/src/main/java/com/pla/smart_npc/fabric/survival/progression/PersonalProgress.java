package com.pla.smart_npc.fabric.survival.progression;

import java.util.*;

/** Bounded evidence of this individual's work, separate from world difficulty and inventory. */
public final class PersonalProgress {
    public static final int VERSION=1, PROOF_LIMIT=128, CRAFT_LIMIT=64, WINDOW_ALLOWANCE=64, TOTAL_LIMIT=1_000_000;
    public static final long WINDOW_TICKS=24_000;
    public enum Skill { FORAGING, MINING, BUILDING, CRAFTING, COOKING, FISHING, EXPLORATION }
    public enum Milestone { SAFE_REST, HOME_COMPLETE, FOOD_PREPARED, FIRST_JOURNEY_RETURN }
    public enum Stage { STARTER, SURVIVOR, RESIDENT, SPECIALIST, EXPEDITION_READY, VETERAN }
    public record Snapshot(long window, int workWindows, long lastWorkedWindow, Map<Skill,Integer> totals,
                           Map<Skill,Integer> allowanceUsed, List<String> proofs, Set<Milestone> milestones,
                           List<String> knownCrafts) { }
    private final EnumMap<Skill,Integer> totals=new EnumMap<>(Skill.class), used=new EnumMap<>(Skill.class);
    private final LinkedHashSet<String> proofs=new LinkedHashSet<>(), crafts=new LinkedHashSet<>();
    private final EnumSet<Milestone> milestones=EnumSet.noneOf(Milestone.class);
    private long window=-1,lastWorkedWindow=-1;
    private int workWindows;

    public boolean record(Skill skill,String proof,int units,long tick) {
        if(skill==null||proof==null||proof.isBlank()||proof.length()>320||units<1||tick<0)return false;
        long current=tick/WINDOW_TICKS;
        if(current>window){window=current;used.clear();proofs.clear();}
        String key=skill.name()+":"+proof;
        int remaining=WINDOW_ALLOWANCE-used.getOrDefault(skill,0);
        if(remaining<=0||proofs.size()>=PROOF_LIMIT||proofs.contains(key))return false;
        int credit=Math.min(remaining,Math.min(WINDOW_ALLOWANCE,units));
        proofs.add(key);used.merge(skill,credit,Integer::sum);
        totals.put(skill,(int)Math.min(TOTAL_LIMIT,(long)total(skill)+credit));
        if(lastWorkedWindow!=window){workWindows=Math.min(TOTAL_LIMIT,workWindows+1);lastWorkedWindow=window;}
        return true;
    }
    public boolean mark(Milestone milestone){return milestones.add(Objects.requireNonNull(milestone));}
    public boolean knowsCraft(String id){return crafts.contains(id);}
    public void rememberCraft(String id){if(id!=null&&!id.isBlank()&&id.length()<=160&&(crafts.contains(id)||crafts.size()<CRAFT_LIMIT))crafts.add(id);}
    public int total(Skill skill){return totals.getOrDefault(skill,0);}
    public int workWindows(){return workWindows;}
    public Stage stage(){
        if(!milestones.contains(Milestone.SAFE_REST)||!milestones.contains(Milestone.FOOD_PREPARED)
                ||total(Skill.FORAGING)+total(Skill.MINING)<8)return Stage.STARTER;
        if(!milestones.contains(Milestone.HOME_COMPLETE))return Stage.SURVIVOR;
        if(total(Skill.EXPLORATION)>=3&&workWindows>=3)return Stage.VETERAN;
        if(total(Skill.EXPLORATION)>=1&&workWindows>=2)return Stage.EXPEDITION_READY;
        if(workWindows>=2&&totals.values().stream().anyMatch(v->v>=64))return Stage.SPECIALIST;
        return Stage.RESIDENT;
    }
    public Snapshot snapshot(){return new Snapshot(window,workWindows,lastWorkedWindow,Map.copyOf(totals),Map.copyOf(used),
            List.copyOf(proofs),Set.copyOf(milestones),List.copyOf(crafts));}
    public static PersonalProgress restore(Snapshot snapshot){
        var result=new PersonalProgress();if(snapshot==null)return result;
        result.window=Math.max(-1,snapshot.window());result.lastWorkedWindow=Math.min(result.window,Math.max(-1,snapshot.lastWorkedWindow()));
        result.workWindows=Math.clamp(snapshot.workWindows(),0,TOTAL_LIMIT);
        for(var skill:Skill.values()){
            int total=Math.clamp(snapshot.totals().getOrDefault(skill,0),0,TOTAL_LIMIT);
            int allowance=Math.clamp(snapshot.allowanceUsed().getOrDefault(skill,0),0,WINDOW_ALLOWANCE);
            if(total>0)result.totals.put(skill,total);
            if(allowance>0)result.used.put(skill,allowance);
        }
        for(String proof:snapshot.proofs())if(proof!=null&&!proof.isBlank()&&proof.length()<=340&&result.proofs.size()<PROOF_LIMIT)result.proofs.add(proof);
        result.milestones.addAll(snapshot.milestones());
        for(String craft:snapshot.knownCrafts())result.rememberCraft(craft);
        return result;
    }
}
