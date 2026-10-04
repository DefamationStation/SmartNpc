import com.pla.smart_npc.entity.PlayerNpcEntity;
import com.pla.smart_npc.entity.ai.ToolAi;
import com.pla.smart_npc.init.SmartNpcModEntities;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

/** Synchronous real entity save/load and physical stack conservation checks. */
public final class SmartNpcToolSwapChecks {
    private SmartNpcToolSwapChecks() { }
    private static void check(boolean condition, String message) {
        SmartNpcFunctional.check(condition, message);
    }
    private static PlayerNpcEntity fixture(ServerLevel level, ServerPlayer player) {
        var npc = SmartNpcModEntities.PLAYER_NPC.get().create(level, EntitySpawnReason.COMMAND);
        npc.setNoAi(true);
        npc.setNoGravity(true);
        npc.setPos(player.getX() + 100, 300, player.getZ());
        level.getChunk(npc.blockPosition());
        npc.getInventory().clearContent();
        for (EquipmentSlot slot : EquipmentSlot.values()) npc.setItemSlot(slot, ItemStack.EMPTY);
        npc.cacheMainWeaponItemForAi(ItemStack.EMPTY);
        npc.setOffWeaponItem(ItemStack.EMPTY);
        return npc;
    }
    private static PlayerNpcEntity roundTrip(ServerLevel level, PlayerNpcEntity npc) {
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        npc.saveWithoutId(output);
        var clone = SmartNpcModEntities.PLAYER_NPC.get().create(level, EntitySpawnReason.LOAD);
        clone.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), output.buildResult()));
        return clone;
    }
    private static int carried(PlayerNpcEntity npc, Item item) {
        int count = npc.getInventory().countItem(item);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            var stack = npc.getItemBySlot(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }
    public static void run(ServerLevel level, ServerPlayer player) {
        var npc = fixture(level, player);
        var bread = new ItemStack(Items.BREAD, 5);
        bread.set(DataComponents.CUSTOM_NAME, Component.literal("Original provisions"));
        npc.setMainHandItemForAi(bread);
        var pick = new ItemStack(Items.IRON_PICKAXE);
        pick.setDamageValue(7);
        pick.set(DataComponents.CUSTOM_NAME, Component.literal("Working pick"));
        npc.getInventory().setItem(0, pick);
        var first = new ToolAi(npc);
        check(first.equipTool(ItemTags.PICKAXES) && npc.getInventory().getItem(0).isEmpty(),
                "temporary pick comes from an actual removed inventory stack");
        npc.getMainHandItem().setDamageValue(19);
        var loaded = roundTrip(level, npc);
        check(ItemStack.isSameItemSameComponents(loaded.getMainHandItem(), bread)
                        && loaded.getMainHandItem().getCount() == 5,
                "entity reload restores original noncombat held stack and components");
        check(carried(loaded, Items.IRON_PICKAXE) == 1 && carried(loaded, Items.BREAD) == 5,
                "reload conserves actual temporary tool and original held count");
        var recoveredPick = loaded.getInventory().getItem(0);
        check(recoveredPick.is(Items.IRON_PICKAXE) && recoveredPick.getDamageValue() == 19
                        && ItemStack.isSameItemSameComponents(recoveredPick, npc.getMainHandItem()),
                "reload preserves live tool wear and custom components");
        check(loaded.getMainWeaponItem().isEmpty() && loaded.getOffWeaponItem().isEmpty(),
                "noncombat temporary swap does not manufacture reserved weapon caches");
        var loadedAgain = roundTrip(level, loaded);
        check(carried(loadedAgain, Items.IRON_PICKAXE) == 1 && carried(loadedAgain, Items.BREAD) == 5,
                "completed reload recovery does not duplicate on another save/load");
        new ToolAi(loadedAgain).restoreMainHand();
        check(loadedAgain.getMainHandItem().is(Items.BREAD), "absent pending state is backward compatible");
        loadedAgain.getInventory().setItem(1, new ItemStack(Items.FISHING_ROD));
        var oldGoal = new ToolAi(loadedAgain);
        var newGoal = new ToolAi(loadedAgain);
        check(oldGoal.equipTool(ItemTags.PICKAXES) && newGoal.equipItem(Items.FISHING_ROD),
                "new goal adopts shared pending transaction across repeated swaps");
        loadedAgain.getMainHandItem().setDamageValue(11);
        oldGoal.restoreMainHand();
        check(loadedAgain.getMainHandItem().is(Items.FISHING_ROD),
                "stale goal stop cannot restore another goal's active equipment");
        var interrupted = roundTrip(level, loadedAgain);
        check(interrupted.getMainHandItem().is(Items.BREAD)
                        && carried(interrupted, Items.IRON_PICKAXE) == 1
                        && carried(interrupted, Items.FISHING_ROD) == 1
                        && carried(interrupted, Items.BREAD) == 5,
                "reload after repeated cross-goal swaps conserves all stacks");
        check(interrupted.getInventory().getItem(1).getDamageValue() == 11,
                "latest temporary rod wear survives interrupted cross-goal swap");
        newGoal.restoreMainHand();
        newGoal.restoreMainHand();
        check(carried(loadedAgain, Items.FISHING_ROD) == 1 && carried(loadedAgain, Items.BREAD) == 5,
                "repeated restore completes exactly once");
        var emptyGoal = new ToolAi(interrupted);
        emptyGoal.equipEmptyMainHand();
        var emptyReload = roundTrip(level, interrupted);
        check(emptyReload.getMainHandItem().is(Items.BREAD) && carried(emptyReload, Items.BREAD) == 5,
                "empty temporary hand also preserves original stack on reload");

        for (boolean offhand : new boolean[] {false, true}) {
            var fisher = fixture(level, player);
            fisher.setMainHandItemForAi(bread);
            var rod = new ItemStack(Items.FISHING_ROD);
            rod.setDamageValue(5);
            rod.set(DataComponents.CUSTOM_NAME, Component.literal("Actual fishing rod"));
            if (offhand) fisher.setItemSlot(EquipmentSlot.OFFHAND, rod);
            else fisher.getInventory().setItem(0, rod);
            var fishingGoal = new com.pla.smart_npc.entity.goal.PlayerNpcFishingGoal(fisher);
            try {
                var equip = fishingGoal.getClass().getDeclaredMethod("equipRodIfNeeded");
                equip.setAccessible(true);
                check((boolean) equip.invoke(fishingGoal), "native fishing equips actual " + (offhand ? "offhand" : "inventory") + " rod");
            } catch (ReflectiveOperationException exception) { throw new RuntimeException(exception); }
            fisher.getMainHandItem().setDamageValue(17);
            var fishingReload = roundTrip(level, fisher);
            check(fishingReload.getMainHandItem().is(Items.BREAD)
                            && carried(fishingReload, Items.BREAD) == 5
                            && carried(fishingReload, Items.FISHING_ROD) == 1,
                    "native fishing reload preserves original hand and one real rod");
            var restoredRod = offhand ? fishingReload.getOffhandItem() : fishingReload.getInventory().getItem(0);
            check(restoredRod.getDamageValue() == 17 && ItemStack.isSameItemSameComponents(restoredRod, fisher.getMainHandItem()),
                    "native fishing reload preserves rod wear, components and source");
            fishingGoal.stop(); fishingGoal.stop();
            check(carried(fisher, Items.FISHING_ROD) == 1 && carried(fisher, Items.BREAD) == 5,
                    "native fishing repeated stop restores exactly once");
        }

        var combat = fixture(level, player);
        var axe = new ItemStack(Items.IRON_AXE);
        axe.setDamageValue(23);
        combat.setMainHandItemForAi(axe);
        combat.cacheMainWeaponItemForAi(axe); // Existing combat cache mirrors the original hand.
        combat.getInventory().setItem(0, pick.copy());
        var axeGoal = new ToolAi(combat);
        check(axeGoal.equipTool(ItemTags.PICKAXES) && axeGoal.equipTool(ItemTags.AXES),
                "requesting saved original tool restores it instead of taking its cache mirror");
        axeGoal.restoreMainHand();
        check(carried(combat, Items.IRON_AXE) == 1 && carried(combat, Items.IRON_PICKAXE) == 1
                        && combat.getMainHandItem().getDamageValue() == 23,
                "original combat tool cache does not create a second physical axe: axes="
                        + carried(combat, Items.IRON_AXE) + " picks=" + carried(combat, Items.IRON_PICKAXE)
                        + " main=" + combat.getMainHandItem() + " damage=" + combat.getMainHandItem().getDamageValue()
                        + " reserve=" + combat.getMainWeaponItem());

        var digger = fixture(level, player);
        digger.setNoAi(false);
        digger.setHealth(digger.getMaxHealth());
        digger.getInventory().addItem(new ItemStack(Items.BEEF, 2));
        digger.getInventory().addItem(new ItemStack(Items.OAK_LOG, 4));
        var originalPick = new ItemStack(Items.WOODEN_PICKAXE);
        originalPick.setDamageValue(4);
        originalPick.set(DataComponents.CUSTOM_NAME, Component.literal("Retained dirt-clearing pick"));
        digger.setMainHandItemForAi(originalPick);
        var cooking = com.pla.smart_npc.fabric.survival.SurvivalTasks.memory(digger);
        cooking.cookingActive = true;
        cooking.cookingDimension = com.pla.smart_npc.fabric.survival.SurvivalTasks.dimension(digger);
        cooking.cookingStep = "NEED_STONE";
        var digGoal = new com.pla.smart_npc.entity.goal.DigDownForStoneGoal(digger, 1.0D);
        try {
            var toolField = digGoal.getClass().getDeclaredField("toolAi");
            toolField.setAccessible(true);
            var digTool = (ToolAi) toolField.get(digGoal);
            digTool.equipBestToolFor(net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState());
            check(digger.getMainHandItem().isEmpty() && carried(digger, Items.WOODEN_PICKAXE) == 0
                            && ToolAi.hasRetainedTool(digger, ItemTags.PICKAXES),
                    "native dirt clearing retains its original real pick solely in the active hand transaction");
            check(digger.hasCarriedTool(ItemTags.PICKAXES)
                            && com.pla.smart_npc.fabric.survival.CookingCraftGoal.snapshot(digger, level).pickaxeReady()
                            && com.pla.smart_npc.entity.goal.GatherStoneGoal.isStoneSupplyPhaseActive(digger, level),
                    "cooking and native stone admission still see the retained pick during barehand dirt clearing");
            new ToolAi(digger).restoreMainHand();
            check(digger.getMainHandItem().isEmpty() && ToolAi.hasRetainedTool(digger, ItemTags.PICKAXES),
                    "a stale tool owner cannot restore the active native dig transaction");
            digGoal.stop(); digGoal.stop();
            check(carried(digger, Items.WOODEN_PICKAXE) == 1
                            && ItemStack.isSameItemSameComponents(digger.getMainHandItem(), originalPick)
                            && !ToolAi.hasRetainedTool(digger, ItemTags.PICKAXES),
                    "native dig stop restores the original pick with wear and components exactly once");
        } catch (ReflectiveOperationException exception) { throw new RuntimeException(exception); }

        var full = fixture(level, player);
        full.setMainHandItemForAi(bread);
        full.getInventory().setItem(0, new ItemStack(Items.COAL, 10));
        var overflowGoal = new ToolAi(full);
        check(overflowGoal.equipItem(Items.COAL), "overflow fixture equips a real stack");
        for (int i = 0; i < full.getInventory().getContainerSize(); i++)
            full.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        full.getInventory().setItem(0, new ItemStack(Items.COAL, 63));
        var bounds = full.getBoundingBox().inflate(3);
        var before = level.getEntitiesOfClass(ItemEntity.class, bounds);
        var overflowReload = roundTrip(level, full);
        int dropped = 0;
        for (var item : level.getEntitiesOfClass(ItemEntity.class, bounds)) {
            if (!before.contains(item) && item.getItem().is(Items.COAL)) {
                dropped += item.getItem().getCount(); item.discard();
            }
        }
        check(overflowReload.getInventory().countItem(Items.COAL) == 64 && dropped == 9,
                "full inventory reload inserts one coal and drops only nine-item remainder");
        check(overflowReload.getMainHandItem().is(Items.BREAD)
                        && overflowReload.getMainHandItem().getCount() == 5,
                "overflow recovery retains the original held stack");
        System.out.println("SMARTNPC_TOOL_SWAP_COMPLETE reload, durability, ownership and overflow conservation");
    }
}
