import com.pla.smart_npc.entity.PlayerNpcEntity;
import com.pla.smart_npc.entity.ai.PlacingBlockAi;
import com.pla.smart_npc.fabric.Events;
import com.pla.smart_npc.fabric.survival.*;
import com.pla.smart_npc.fabric.survival.progression.*;
import com.pla.smart_npc.util.PlayerNpcBlockBreakUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import java.util.UUID;

/** Controlled real transactions with native producers and save hooks; not an autonomous survival trial. */
public final class SmartNpcIntegratedMemoryProgress extends SmartNpcMemoryScenario {
    final BlockPos finalOre=new BlockPos(4,260,0);
    boolean nativeHarvestObserved;
    @Override public void onInitializeServer(){super.onInitializeServer();Events.register(this);}
    @Events.SubscribeEvent public void actualProducer(VerifiedWorkOutcome outcome){
        if(outcome.kind()==VerifiedWorkOutcome.Kind.HARVEST&&outcome.position().equals(visible)){
            nativeHarvestObserved|=StackWalker.getInstance().walk(frames->frames.anyMatch(f->f.getClassName().equals(PlayerNpcBlockBreakUtil.class.getName())));
        }
    }
    @Override PlayerNpcEntity spawn(MinecraftServer server,String name,UUID id,int x)throws Exception{
        var n=super.spawn(server,name,id,x);var level=server.overworld();
        if(name.equals("MemoryA")){
            // One declared real log is consumed by the native table recipe.
            n.getInventory().addItem(new ItemStack(Items.OAK_LOG));
            check(CookingCraftingExecutor.tryCraft(n,CookingCraftingExecutor.Action.CRAFTING_TABLE,null),"real crafting transaction failed");
            check(n.getInventory().countItem(Items.OAK_LOG)==0,"recipe did not consume log");
            check(NpcProgression.snapshot(n).knownCrafts().contains("minecraft:crafting_table"),"native craft producer missing");
            ItemStack table=take(n,Items.CRAFTING_TABLE,1),pick=n.getMainHandItem();
            check(table.getCount()==1,"crafted table missing");n.setItemSlot(EquipmentSlot.MAINHAND,table);
            var pos=new BlockPos(1,260,1);
            check(new PlacingBlockAi(n).placeHeldBlock(level,pos,Blocks.CRAFTING_TABLE.defaultBlockState()),"native placement failed");
            check(n.getMainHandItem().isEmpty()&&level.getBlockState(pos).is(Blocks.CRAFTING_TABLE),"placement did not consume the real table");
            n.setItemSlot(EquipmentSlot.MAINHAND,pick);
            check(NpcProgression.snapshot(n).totals().getOrDefault(PersonalProgress.Skill.BUILDING,0)==0,"arbitrary placement granted blueprint credit");
            level.setBlockAndUpdate(finalOre,Blocks.COAL_ORE.defaultBlockState());
        }else{
            // An actual command admission test: equal food, distinct saved profiles.
            var escrowA=take(a,Items.COOKED_BEEF,11);var escrowB=take(n,Items.COOKED_BEEF,11);
            check(escrowA.getCount()==11&&escrowB.getCount()==11,"food escrow failed");
            String cautious=Journeys.start(a,a.blockPosition().offset(128,0,0));
            check(!Journeys.active(a)&&cautious.contains("6"),"cautious personality did not require its real reserve");
            String prepared=Journeys.start(n,n.blockPosition().offset(128,0,0));
            check(Journeys.active(n),"prepared profile cannot depart with five food: "+prepared);
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"smart_npc journey "+n.getUUID()+" cancel");
            check(!Journeys.active(n),"real cancellation failed");
            check(n.getInventory().addItem(escrowB).isEmpty()&&a.getInventory().addItem(escrowA).isEmpty(),"food escrow restore failed");
            check(NpcProgression.snapshot(n).totals().getOrDefault(PersonalProgress.Skill.EXPLORATION,0)==0,"cancelled intention granted expedition progress");
        }
        return n;
    }
    ItemStack take(PlayerNpcEntity n,Item item,int count){
        for(int i=0;i<n.getInventory().getContainerSize();i++)if(n.getInventory().getItem(i).is(item))return n.getInventory().removeItem(i,count);
        return ItemStack.EMPTY;
    }
    @Override void tick(MinecraftServer server)throws Exception{
        // This new successful native harvest occurs after the last entity tick,
        // immediately before shutdown. Only the native entity save hook can flush it.
        if(!replay&&stage==2&&ticks==199){
            check(nativeHarvestObserved,"first harvest was supplied only by the fixture bridge, not the native producer");
            var level=server.overworld();
            check(PlayerNpcBlockBreakUtil.destroyBlock(level,finalOre,level.getBlockState(finalOre),a),"final native harvest failed");
            check(NpcProgression.snapshot(a).totals().getOrDefault(PersonalProgress.Skill.MINING,0)==2,"native harvest credit/dedup failed");
        }
        if(replay&&stage==2){
            var progress=NpcProgression.snapshot(a);
            check(progress.totals().getOrDefault(PersonalProgress.Skill.MINING,0)==2,"last native outcome was lost during real save/reload");
            check(progress.knownCrafts().contains("minecraft:crafting_table"),"known craft lost on reload");
            check(progress.milestones().isEmpty(),"declared starting supplies or cancelled trip fabricated milestones");
        }
        super.tick(server);
    }
}
