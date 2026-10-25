package com.pla.smart_npc.fabric.survival.progression;

import net.minecraft.nbt.CompoundTag;
import java.util.*;

/** Versioned storage; an unsupported future version is deliberately read-only. */
public final class ProgressCodec {
    private ProgressCodec() { }
    public static boolean supported(CompoundTag tag){return tag.getIntOr("version",0)<=PersonalProgress.VERSION;}
    public static PersonalProgress load(CompoundTag tag){
        if(!supported(tag))return new PersonalProgress();
        var totals=new EnumMap<PersonalProgress.Skill,Integer>(PersonalProgress.Skill.class);
        var used=new EnumMap<PersonalProgress.Skill,Integer>(PersonalProgress.Skill.class);
        for(var skill:PersonalProgress.Skill.values()){
            totals.put(skill,tag.getCompoundOrEmpty("totals").getIntOr(skill.name(),0));
            used.put(skill,tag.getCompoundOrEmpty("used").getIntOr(skill.name(),0));
        }
        var proofs=new ArrayList<String>();
        for(int i=0;i<Math.clamp(tag.getIntOr("proofCount",0),0,PersonalProgress.PROOF_LIMIT);i++)proofs.add(tag.getStringOr("proof"+i,""));
        var crafts=new ArrayList<String>();
        for(int i=0;i<Math.clamp(tag.getIntOr("craftCount",0),0,PersonalProgress.CRAFT_LIMIT);i++)crafts.add(tag.getStringOr("craft"+i,""));
        var milestones=EnumSet.noneOf(PersonalProgress.Milestone.class);
        for(var milestone:PersonalProgress.Milestone.values())if(tag.getCompoundOrEmpty("milestones").getBooleanOr(milestone.name(),false))milestones.add(milestone);
        return PersonalProgress.restore(new PersonalProgress.Snapshot(tag.getLongOr("window",-1),tag.getIntOr("workWindows",0),
                tag.getLongOr("lastWorkedWindow",-1),totals,used,proofs,milestones,crafts));
    }
    public static CompoundTag save(PersonalProgress progress){
        var snapshot=progress.snapshot();var tag=new CompoundTag();tag.putInt("version",PersonalProgress.VERSION);
        tag.putLong("window",snapshot.window());tag.putInt("workWindows",snapshot.workWindows());tag.putLong("lastWorkedWindow",snapshot.lastWorkedWindow());
        var totals=new CompoundTag();var used=new CompoundTag();
        for(var skill:PersonalProgress.Skill.values()){
            totals.putInt(skill.name(),snapshot.totals().getOrDefault(skill,0));used.putInt(skill.name(),snapshot.allowanceUsed().getOrDefault(skill,0));
        }
        tag.put("totals",totals);tag.put("used",used);
        tag.putInt("proofCount",snapshot.proofs().size());for(int i=0;i<snapshot.proofs().size();i++)tag.putString("proof"+i,snapshot.proofs().get(i));
        tag.putInt("craftCount",snapshot.knownCrafts().size());for(int i=0;i<snapshot.knownCrafts().size();i++)tag.putString("craft"+i,snapshot.knownCrafts().get(i));
        var milestones=new CompoundTag();for(var milestone:snapshot.milestones())milestones.putBoolean(milestone.name(),true);tag.put("milestones",milestones);
        return tag;
    }
}
