import com.pla.smart_npc.fabric.PersistentData;
import com.pla.smart_npc.fabric.survival.SocialSafety;
import com.pla.smart_npc.fabric.survival.relationships.*;
import com.pla.smart_npc.entity.PlayerNpcEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import java.util.UUID;

/** Actual first-hurt attribution/reload, plus opaque future-defense preservation. */
public final class SmartNpcRelationshipEncounter extends SmartNpcMemoryEncounterScenario {
    final UUID futureVictimId=new UUID(123,4);
    PlayerNpcEntity futureVictim;
    boolean futureChecked;
    CompoundTag futureData(){
        var tag=new CompoundTag();tag.putInt("version",99);tag.putString("opaque","future-defense-preserved");
        tag.putInt("count",1);var entry=new CompoundTag();entry.putString("offender",bystanderId.toString());
        entry.putString("cause","ASSAULT");entry.putLong("expires",Long.MAX_VALUE);tag.put("entry0",entry);return tag;
    }
    @Override void verify()throws Exception{
        super.verify();
        var actual=NpcRelationships.advice(victim,offenderId);
        check(actual.stance()==RelationshipBook.Stance.CAUTIOUS&&actual.trust()<0,
                "canonical actual offender relationship missing");
        var peaceful=NpcRelationships.advice(victim,bystanderId);
        check(peaceful.stance()==RelationshipBook.Stance.UNKNOWN&&peaceful.trust()==0,
                "nearer peaceful bystander relationship changed");
        var level=(ServerLevel)victim.level();
        if(!futureChecked){
            if(replay){
                futureVictim=(PlayerNpcEntity)level.getEntity(futureVictimId);
                check(futureVictim!=null,"saved future-schema defense NPC missing");
                check(SocialSafety.memory(futureVictim).cause(offenderId,level.getGameTime()).isEmpty(),
                        "transient defense became saved authority across unsupported-schema replay");
            }else{
                futureVictim=spawn(level.getServer(),"MemoryVictim",futureVictimId,-4);
                PersistentData.get(futureVictim).put("SurvivalSocial",futureData());
                check(SocialSafety.memory(futureVictim).cause(bystanderId,level.getGameTime()).isEmpty(),
                        "unknown saved defense granted permission against peaceful bystander");
                check(!SocialSafety.permits(futureVictim,bystander),"future defense authorized unprovoked aggression");
                check(futureVictim.hurtServer(level,level.damageSources().mobAttack(offender),2),
                        "first actual hurt with future defense schema rejected");
                check(SocialSafety.hasCause(futureVictim,offender),"new actual offender lacks transient defense");
            }
            futureChecked=true;
        }
        check(PersistentData.get(futureVictim).getCompoundOrEmpty("SurvivalSocial").equals(futureData()),
                "actual hurt or native save/replay overwrote future defense attachment");
        check(SocialSafety.memory(futureVictim).cause(bystanderId,level.getGameTime()).isEmpty(),
                "future-schema entry blamed peaceful bystander");
        check(NpcRelationships.advice(futureVictim,offenderId).stance()==RelationshipBook.Stance.CAUTIOUS,
                "future-schema actual offender relationship not attributed/persisted");
        check(NpcRelationships.advice(futureVictim,bystanderId).stance()==RelationshipBook.Stance.UNKNOWN,
                "future-schema peaceful bystander relationship changed");
        var selector=net.minecraft.world.entity.Mob.class.getDeclaredField("goalSelector");selector.setAccessible(true);
        check(((net.minecraft.world.entity.ai.goal.GoalSelector)selector.get(futureVictim)).getAvailableGoals().size()==80,
                "future-schema victim ordinary goals changed");
    }
}
