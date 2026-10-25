import com.pla.smart_npc.entity.ai.PlacingBlockAi;
import com.pla.smart_npc.fabric.survival.progression.*;
import com.pla.smart_npc.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Controlled last-block transaction immediately before a real process save.
 * Prepared geometry and one declared material are not an autonomous build. */
public final class SmartNpcProgressCheckpoint extends SmartNpcMemoryScenario {
    @Override void tick(MinecraftServer server)throws Exception{
        if(replay&&stage==2&&!NpcProgression.snapshot(a).milestones().contains(PersonalProgress.Milestone.HOME_COMPLETE)){
            // Controlled scheduler admission, like the supply-consumer fixture.
            // This tests persisted deferred work, not autonomous worker selection.
            if(a.tickCount%20==Math.floorMod(a.getUUID().hashCode(),20)){
                PlayerNpcAiWorkBudget.onWorkStarted(a);
                NpcProgression.tick(new com.pla.smart_npc.fabric.Events.EntityTickEvent.Post(a));
            }
            if(ticks>=199&&ticks<500){ticks++;return;}
        }
        if(replay&&stage==2&&ticks>=199){
            check(NpcProgression.snapshot(a).milestones().contains(PersonalProgress.Milestone.HOME_COMPLETE),
                    "last-block pending completion lost across real restart");
            check(NpcProgression.snapshot(a).totals().getOrDefault(PersonalProgress.Skill.BUILDING,0)>=1,
                    "actual final placement evidence lost across restart");
        }
        super.tick(server);
    }
    @Override void finish(MinecraftServer server,String marker,String message){
        if(!replay&&marker.equals("checkpoint.txt")){try{
            var level=server.overworld();
            var layout=PlayerNpcBuildLayoutLoader.getLayouts().stream().filter(l->l.id().contains("starter_survival_cabin")).findFirst().orElseThrow();
            var base=new BlockPos(-2,259,-2);
            PlayerNpcHomeUtil.setHome(a,new PlayerNpcHomeUtil.HomeArea(base,layout.width(),layout.depth()));
            PlayerNpcHomeUtil.setHomeLayoutId(a,layout.id());
            var last=layout.blocks().stream().filter(b->b.state().is(net.minecraft.tags.BlockTags.PLANKS))
                    .min(java.util.Comparator.comparingDouble(b->b.toWorld(base).distSqr(a.blockPosition()))).orElseThrow();
            for(var block:layout.blocks())level.setBlockAndUpdate(block.toWorld(base),block==last?net.minecraft.world.level.block.Blocks.AIR.defaultBlockState():block.state());
            // One real declared block is consumed by the normal held-item placement.
            var held=a.getMainHandItem();a.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(last.state().getBlock().asItem()));
            check(new PlacingBlockAi(a).placeHeldBlock(level,last.toWorld(base),last.state()),"last block native placement failed");
            check(a.getMainHandItem().isEmpty(),"last block material was not consumed");a.setItemSlot(EquipmentSlot.MAINHAND,held);
            check(PlayerNpcBuildLayoutLoader.countMissingRequired(level,layout,base)==0,"declared cabin incomplete");
            check(!NpcProgression.snapshot(a).milestones().contains(PersonalProgress.Milestone.HOME_COMPLETE),"completion credited before delayed physical check");
        }catch(Exception error){throw new RuntimeException(error);}}
        super.finish(server,marker,message+"; pending final owned-cabin placement checkpoint verified");
    }
}
