import com.pla.smart_npc.entity.PlayerNpcEntity;
import com.pla.smart_npc.init.SmartNpcModEntities;
import com.pla.smart_npc.fabric.survival.*;
import com.pla.smart_npc.util.*;
import com.pla.smart_npc.event.PlayerNpcChestProtectEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.*;
import java.util.*;

/** Real server entities and a client-to-server container click in an isolated test world. */
public final class SmartNpcSurvivalChecks {
    static PlayerNpcEntity owner;
    static BlockPos chestPos;
    static int phase;
    static volatile boolean networkTheftPassed;
    static PlayerNpcEntity sleeper;
    static com.pla.smart_npc.entity.goal.SleepAtHomeGoal sleepGoal;
    static volatile boolean sleepPassed;
    static void check(boolean value, String message) { SmartNpcFunctional.check(value, message); }
    static PlayerNpcEntity npc(ServerLevel level, ServerPlayer player) {
        var npc = SmartNpcModEntities.PLAYER_NPC.get().create(level, EntitySpawnReason.COMMAND);
        npc.setUsername("Dream"); // Includes both player-hunting and prank interests in the default config.
        npc.setPos(player.getX()+2,player.getY(),player.getZ()); npc.setNoAi(true); npc.setNoGravity(true);
        level.addFreshEntity(npc); return npc;
    }
    public static void setup(ServerLevel level, ServerPlayer player) {
        // World spawn can choose a different nearby spot on the same seed. Keep witnessed
        // offence fixtures in a clear arena: an owner inside terrain legitimately has no
        // line of sight, which must not be confused with broken offence handling.
        var arena=player.blockPosition();
        for(int x=-3;x<=4;x++)for(int z=-2;z<=2;z++){
            level.setBlockAndUpdate(arena.offset(x,-1,z),Blocks.STONE.defaultBlockState());
            for(int y=0;y<=3;y++)level.setBlockAndUpdate(arena.offset(x,y,z),Blocks.AIR.defaultBlockState());
        }
        // Exercise the entity's scheduler at a night-time clock: this used to miss the
        // morning selection window and leave a newly loaded/spawned NPC without a job.
        var worker=npc(level,player);
        var jobName=com.pla.smart_npc.config.SmartNpcNamesConfig.getPlayerNpcNameEntries().stream()
            .map(com.pla.smart_npc.config.SmartNpcNamesConfig::parseNameEntry).flatMap(Optional::stream)
            .filter(n->n.interests().stream().anyMatch(com.pla.smart_npc.clazz.PlayerNpcInterest::isJob)
                && !n.interests().contains(com.pla.smart_npc.clazz.PlayerNpcInterest.BUILDING))
            .findFirst().orElseThrow();
        worker.setUsername(jobName.skinName());
        try {
            var select=PlayerNpcEntity.class.getDeclaredMethod("tickDailyJobSelection",ServerLevel.class);select.setAccessible(true);
            var dayField=PlayerNpcEntity.class.getDeclaredField("selectedDailyJobDay");dayField.setAccessible(true);
            var interestField=PlayerNpcEntity.class.getDeclaredField("selectedDailyJobInterest");interestField.setAccessible(true);
            var clocks=level.getServer().getCommands();
            long previousTime=level.getOverworldClockTime();
            try {
                clocks.performPrefixedCommand(player.createCommandSourceStack(),"time set 18000");
                dayField.setLong(worker,-1);interestField.set(worker,null);select.invoke(worker,level);
                check(worker.getSelectedDailyJobInterest().isPresent(),"night-time spawn receives a daily job immediately");
                var selected=worker.getSelectedDailyJobInterest();select.invoke(worker,level);
                check(worker.getSelectedDailyJobInterest().equals(selected),"daily job remains committed during the same day");
                dayField.setLong(worker,-1);select.invoke(worker,level);
                check(worker.getSelectedDailyJobDay()==level.getOverworldClockTime()/24000,"missed morning selection repairs a stale assignment");
                interestField.set(worker,com.pla.smart_npc.clazz.PlayerNpcInterest.BUILDING);select.invoke(worker,level);
                check(worker.getSelectedDailyJobInterest().orElseThrow()!=com.pla.smart_npc.clazz.PlayerNpcInterest.BUILDING,
                    "saved job that no longer matches personality is repaired immediately");
            } finally { clocks.performPrefixedCommand(player.createCommandSourceStack(),"time set "+previousTime); }
            var field=net.minecraft.world.entity.Mob.class.getDeclaredField("goalSelector");field.setAccessible(true);
            var selector=(net.minecraft.world.entity.ai.goal.GoalSelector)field.get(worker);
            check(selector.getAvailableGoals().stream().anyMatch(g ->
                g.getGoal() instanceof com.pla.smart_npc.entity.goal.StartupWorkGatedGoal gate
                && gate.getDelegateGoal() instanceof com.pla.smart_npc.entity.goal.SleepAtHomeGoal),
                "home sleep is registered for every personality without a building-interest gate");
        } catch(ReflectiveOperationException e){throw new RuntimeException(e);} finally {worker.discard();}
        var legacy = PlayerNpcBuildLayoutLoader.getLayout("smart_npc:easy_survival_house").orElseThrow();
        check(!legacy.footprint().isEmpty() && legacy.requiredBlocks()>0,"legacy blueprint loads real blocks instead of air");
        check(legacy.blocks().stream().anyMatch(b -> b.state().getBlock() instanceof StairBlock
            && b.state().getValue(StairBlock.FACING)!=net.minecraft.core.Direction.NORTH),"legacy blueprint orientations survive palette conversion");
        var starter = PlayerNpcBuildLayoutLoader.getLayout("smart_npc:starter_survival_cabin").orElseThrow();
        check(starter.blocks().stream().anyMatch(b->b.state().is(Blocks.CHEST)) && starter.blocks().stream().anyMatch(b->b.state().is(Blocks.FURNACE))
            && starter.blocks().stream().filter(b->b.state().getBlock() instanceof BedBlock).count()==2,"starter cabin contains bed, storage and furnace");
        var victim=npc(level,player); var neighbour=npc(level,player);
        try {
            var field=net.minecraft.world.entity.Mob.class.getDeclaredField("goalSelector");field.setAccessible(true);
            var selector=(net.minecraft.world.entity.ai.goal.GoalSelector)field.get(victim);
            check(selector.getAvailableGoals().stream().noneMatch(g -> g.getGoal() instanceof com.pla.smart_npc.entity.goal.TrollHitGoal
                || g.getGoal() instanceof com.pla.smart_npc.entity.goal.IronGolemTrollGoal
                || g.getGoal() instanceof com.pla.smart_npc.entity.goal.LootNearbyChestGoal
                || g.getGoal() instanceof com.pla.smart_npc.entity.goal.AiBudgetWaitingStrollGoal),"random prank, raid and decorative waiting-stroll goals are not registered");
        } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
        victim.setTarget(player); check(victim.getTarget()==null,"hunter personality cannot acquire an unprovoked player target");
        victim.setTarget(neighbour); check(victim.getTarget()==null && !victim.canAttack(neighbour),"NPC neighbours are peaceful without an offence");
        float health=neighbour.getHealth(); check(!victim.doHurtTarget(level,neighbour) && neighbour.getHealth()==health,"direct neutral melee is blocked");
        var golem=net.minecraft.world.entity.EntityTypes.IRON_GOLEM.create(level,EntitySpawnReason.COMMAND);
        check(!victim.canAttack(golem),"neutral golems are not prank targets");
        check(!com.pla.smart_npc.entity.ai.CautiousThreatAi.isValidThreat(victim,player),"visitors are not automatic cautious threats");
        check(victim.hurtServer(level,level.damageSources().mobAttack(neighbour),1),"assault fixture applies actual damage");
        check(victim.getTarget()==neighbour && SocialSafety.hasCause(victim,neighbour),"actual assault permits defensive targeting");
        check(com.pla.smart_npc.entity.ai.CautiousThreatAi.isValidThreat(victim,neighbour),"a proven attacker is a cautious threat");
        check(SocialSafety.memory(victim).cause(neighbour.getUUID(),level.getGameTime()+1200).isEmpty(),"defence expires instead of creating a permanent feud");
        victim.discard(); neighbour.discard();

        var vandalOwner=npc(level,player);
        check(SocialSafety.witnesses(vandalOwner,player),"property offence fixture has clear witnessed sight");
        var origin=player.blockPosition().offset(8,0,0);
        PlayerNpcHomeUtil.setHome(vandalOwner,new PlayerNpcHomeUtil.HomeArea(origin,starter.width(),starter.depth()),starter.id());
        level.setBlockAndUpdate(origin,Blocks.DIRT.defaultBlockState());
        check(player.gameMode.destroyBlock(origin),"terrain fixture broken through player game mode");
        check(!SocialSafety.hasCause(vandalOwner,player),"ordinary terrain in a home plot is not treated as vandalism");
        level.setBlockAndUpdate(origin,Blocks.OAK_PLANKS.defaultBlockState());
        check(player.gameMode.destroyBlock(origin),"constructed fixture broken through player game mode");
        check(SocialSafety.hasCause(vandalOwner,player),"damage to a tracked matching base block permits defence");
        vandalOwner.discard();

        // Keep this copied fixture at night and test the registered sleep delegate after
        // the sky clock has updated, without random profession or movement competition.
        level.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(),"time set 18000");
        sleeper=npc(level,player);
        var nonBuilder=com.pla.smart_npc.config.SmartNpcNamesConfig.getPlayerNpcNameEntries().stream()
            .map(com.pla.smart_npc.config.SmartNpcNamesConfig::parseNameEntry).flatMap(Optional::stream)
            .filter(n->!n.interests().contains(com.pla.smart_npc.clazz.PlayerNpcInterest.BUILDING)).findFirst().orElseThrow();
        sleeper.setUsername(nonBuilder.skinName());
        var bed=new BlockPos(player.getBlockX()+6,280,player.getBlockZ());
        level.setBlockAndUpdate(bed.below(),Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(bed.east().below(),Blocks.STONE.defaultBlockState());
        var foot=starter.blocks().stream().map(b->b.state()).filter(s->s.getBlock() instanceof BedBlock).findFirst().orElseThrow()
            .setValue(BedBlock.FACING,net.minecraft.core.Direction.EAST).setValue(BedBlock.PART,BedPart.FOOT);
        level.setBlockAndUpdate(bed,foot);level.setBlockAndUpdate(bed.east(),foot.setValue(BedBlock.PART,BedPart.HEAD));
        sleeper.setPos(bed.getX()+0.5,280,bed.getZ()+0.5);
        PlayerNpcHomeUtil.setHome(sleeper,new PlayerNpcHomeUtil.HomeArea(bed,2,1));
        try {
            var field=net.minecraft.world.entity.Mob.class.getDeclaredField("goalSelector");field.setAccessible(true);
            var selector=(net.minecraft.world.entity.ai.goal.GoalSelector)field.get(sleeper);
            sleepGoal=(com.pla.smart_npc.entity.goal.SleepAtHomeGoal)selector.getAvailableGoals().stream()
                .map(g->g.getGoal()).filter(g->g instanceof com.pla.smart_npc.entity.goal.StartupWorkGatedGoal)
                .map(g->((com.pla.smart_npc.entity.goal.StartupWorkGatedGoal)g).getDelegateGoal())
                .filter(g->g instanceof com.pla.smart_npc.entity.goal.SleepAtHomeGoal).findFirst().orElseThrow();
            selector.removeAllGoals(g->true);
        } catch(ReflectiveOperationException e){throw new RuntimeException(e);}

        owner=npc(level,player); chestPos=player.blockPosition().offset(-2,0,0);
        level.setBlockAndUpdate(chestPos,Blocks.CHEST.defaultBlockState()); owner.setOwnedChestPos(chestPos);
        var chest=(ChestBlockEntity)level.getBlockEntity(chestPos); chest.setItem(0,new ItemStack(Items.COAL,3)); chest.setChanged();
        var recorded=chest.saveWithFullMetadata(level.registryAccess());chest.clearContent();
        try {
            var method=com.pla.smart_npc.entity.goal.BuildHouseGoal.class.getDeclaredMethod("applyBlockEntityData",ServerLevel.class,BlockPos.class,net.minecraft.nbt.CompoundTag.class);
            method.setAccessible(true);method.invoke(new com.pla.smart_npc.entity.goal.BuildHouseGoal(owner),level,chestPos,recorded);
            check(chest.isEmpty(),"constructed storage cannot copy free inventory from a blueprint");
        } catch(ReflectiveOperationException e){throw new RuntimeException(e);}
        chest.setItem(0,new ItemStack(Items.COAL,3));chest.setChanged();
        PlayerNpcChestProtectEvent.reportOffense(level,chestPos,player,"opened");
        check(owner.getTarget()==null && !SocialSafety.hasCause(owner,player),"opening owned storage warns without attacking");
        check(!SocialSafety.removedAny(List.of(new ItemStack(Items.COAL,3)),List.of(new ItemStack(Items.COAL,1),new ItemStack(Items.COAL,2))),"chest rearrangement is not theft");
        check(!SocialSafety.removedAny(List.of(new ItemStack(Items.COAL,3)),List.of(new ItemStack(Items.COAL,4))),"depositing supplies is not theft");
        SocialSafety.opened(player,chestPos); player.openMenu(chest);
    }
    public static void clientTick(Minecraft mc,double elapsed) {
        if(phase==0 && elapsed>1 && mc.player.containerMenu instanceof ChestMenu menu){
            phase=1;
            mc.gameMode.handleContainerInput(menu.containerId,0,0,ContainerInput.QUICK_MOVE,mc.player);
        }
        if(phase==1 && elapsed>3){
            phase=2;mc.player.closeContainer();
            mc.getSingleplayerServer().execute(()->{try {
                var player=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                check(SocialSafety.memory(owner).cause(player.getUUID(),owner.level().getGameTime()).orElseThrow()==GrievanceMemory.Cause.THEFT,
                    "real network chest click records actual theft");
                check(((ChestBlockEntity)owner.level().getBlockEntity(chestPos)).getItem(0).isEmpty(),"theft corresponds to actual removed contents");
                sleeper.setNoAi(false);sleeper.getRandom().setSeed(12345);
                boolean ready=false;
                for(int attempt=0;attempt<100 && !ready;attempt++){sleeper.tickCount+=40;ready=sleepGoal.canUse();}
                check(ready,"non-builder can select its existing home bed at night");
                sleepGoal.start();sleepGoal.tick();
                check(sleeper.isSleeping(),"non-builder actually enters sleeping pose in its home bed");
                sleepGoal.stop();sleeper.discard();sleepPassed=true;
                networkTheftPassed=true;owner.discard();
            }catch(Throwable t){t.printStackTrace();}});
        }
    }
}
