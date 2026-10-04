import com.pla.smart_npc.entity.PlayerNpcEntity;
import com.pla.smart_npc.entity.ai.ToolAi;
import com.pla.smart_npc.entity.goal.PlayerNpcRangedBowAttackGoal;
import com.pla.smart_npc.init.SmartNpcModEntities;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

/** Native synchronous bow/tool ownership and physical stack recovery regression checks. */
public final class SmartNpcBowSwapChecks {
    private SmartNpcBowSwapChecks() { }
    private static void check(boolean condition, String message) {
        SmartNpcFunctional.check(condition, message);
    }
    private static ItemStack named(Item item, int damage, String name) {
        var stack = new ItemStack(item);
        stack.setDamageValue(damage);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
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
        var sword = named(Items.IRON_SWORD, 7, "Original combat sword");
        npc.setMainHandItemForAi(sword);
        npc.cacheMainWeaponItemForAi(sword); // A cache mirror must never become a second physical weapon.
        return npc;
    }
    private static TagValueOutput save(ServerLevel level, PlayerNpcEntity npc) {
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        npc.saveWithoutId(output);
        return output;
    }
    private static PlayerNpcEntity load(ServerLevel level, TagValueOutput output) {
        var clone = SmartNpcModEntities.PLAYER_NPC.get().create(level, EntitySpawnReason.LOAD);
        clone.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), output.buildResult()));
        return clone;
    }
    private static int physical(PlayerNpcEntity npc, Item item) {
        int count = npc.getInventory().countItem(item);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            var stack = npc.getItemBySlot(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        // Off-weapon is a real reserve, whereas main-weapon mirrors our fixture's held sword.
        if (npc.getOffWeaponItem().is(item)) count += npc.getOffWeaponItem().getCount();
        return count;
    }
    private static ItemStack find(PlayerNpcEntity npc, Item item) {
        for (EquipmentSlot slot : EquipmentSlot.values()) if (npc.getItemBySlot(slot).is(item)) return npc.getItemBySlot(slot);
        for (int i = 0; i < npc.getInventory().getContainerSize(); i++)
            if (npc.getInventory().getItem(i).is(item)) return npc.getInventory().getItem(i);
        if (npc.getOffWeaponItem().is(item)) return npc.getOffWeaponItem();
        return ItemStack.EMPTY;
    }
    private static void conserved(PlayerNpcEntity npc, ItemStack sword, ItemStack bow, ItemStack pick, String context) {
        check(physical(npc, Items.IRON_SWORD) == 1 && physical(npc, Items.BOW) == 1
                        && physical(npc, Items.IRON_PICKAXE) == 1,
                context + " conserves one physical original sword, bow and tool");
        check(ItemStack.isSameItemSameComponents(npc.getMainHandItem(), sword),
                context + " restores original hand and components");
        check(ItemStack.isSameItemSameComponents(npc.getMainWeaponItem(), sword)
                        && npc.getMainWeaponItem().getCount() == 1,
                context + " preserves exactly one original weapon cache mirror");
        check(ItemStack.isSameItemSameComponents(find(npc, Items.BOW), bow)
                        && ItemStack.isSameItemSameComponents(find(npc, Items.IRON_PICKAXE), pick),
                context + " preserves actual live bow/tool wear and custom components");
        check(npc.getSwapToBowCooldown() >= 100 && npc.getSwapToBowCooldown() < 300,
                context + " applies native bow cooldown after recovery");
    }
    public static void run(ServerLevel level, ServerPlayer player) {
        for (boolean bowFirst : new boolean[] {false, true}) {
            for (String source : new String[] {"INVENTORY", "OFFHAND", "OFF_WEAPON"}) {
                var npc = fixture(level, player);
                var sword = npc.getMainHandItem().copy();
                var bow = named(Items.BOW, 3, "Actual temporary bow");
                var pick = named(Items.IRON_PICKAXE, 5, "Actual obstruction pick");
                npc.getInventory().setItem(0, bow);
                if (source.equals("OFFHAND")) npc.setItemSlot(EquipmentSlot.OFFHAND, pick);
                else if (source.equals("OFF_WEAPON")) npc.setOffWeaponItem(pick);
                else npc.getInventory().setItem(1, pick);
                var toolGoal = new ToolAi(npc);
                var bowGoal = new PlayerNpcRangedBowAttackGoal(npc, 1.0D, 20, 15.0F);
                String context = (bowFirst ? "bow -> tool " : "tool -> bow ") + source;
                if (bowFirst) {
                    bowGoal.start();
                    check(npc.getMainHandItem().is(Items.BOW), context + " native goal equips real bow");
                    npc.getMainHandItem().setDamageValue(17); bow.setDamageValue(17);
                    check(toolGoal.equipTool(ItemTags.PICKAXES), context + " equips obstruction tool");
                    npc.getMainHandItem().setDamageValue(29); pick.setDamageValue(29);
                    bowGoal.stop();
                    check(npc.getMainHandItem().is(Items.IRON_PICKAXE)
                                    && npc.getMainHandItem().getDamageValue() == 29,
                            context + " stale native bow stop cannot restore active tool");
                } else {
                    check(toolGoal.equipTool(ItemTags.PICKAXES), context + " equips first tool");
                    npc.getMainHandItem().setDamageValue(29); pick.setDamageValue(29);
                    bowGoal.start();
                    check(npc.getMainHandItem().is(Items.BOW), context + " native goal equips real bow");
                    npc.getMainHandItem().setDamageValue(17); bow.setDamageValue(17);
                    toolGoal.restoreMainHand();
                    check(npc.getMainHandItem().is(Items.BOW) && npc.getMainHandItem().getDamageValue() == 17,
                            context + " stale tool stop cannot restore active bow");
                }
                var interrupted = load(level, save(level, npc));
                conserved(interrupted, sword, bow, pick, context + " interrupted reload");
                var again = load(level, save(level, interrupted));
                again.restoreMainHandAfterTemporaryBow();
                new ToolAi(again).restoreMainHand();
                conserved(again, sword, bow, pick, context + " repeated recovery");
                if (bowFirst) toolGoal.restoreMainHand(); else bowGoal.stop();
                bowGoal.stop(); toolGoal.restoreMainHand();
                conserved(npc, sword, bow, pick, context + " repeated native stop");
                if (source.equals("OFFHAND")) check(find(npc, Items.IRON_PICKAXE) == npc.getOffhandItem(),
                        context + " returns actual tool to offhand");
                if (source.equals("OFF_WEAPON")) check(find(npc, Items.IRON_PICKAXE) == npc.getOffWeaponItem(),
                        context + " returns actual tool to off-weapon reserve");
            }
        }
        // Reconstruct both old independently saved transaction orders to exercise migration.
        for (boolean bowFirst : new boolean[] {false, true}) {
            var legacy = fixture(level, player);
            var sword = legacy.getMainHandItem().copy();
            var bow = named(Items.BOW, 17, "Legacy actual bow");
            var pick = named(Items.IRON_PICKAXE, 29, "Legacy actual pick");
            legacy.setMainHandItemForAi(bowFirst ? pick : bow);
            var output = save(level, legacy);
            output.discard("MainHandSwapFormatVersion");
            output.putBoolean("TemporaryBowEquipped", true);
            output.store("TemporaryBowPreviousMainHand", ItemStack.CODEC, bowFirst ? sword : pick);
            var tool = output.child("TemporaryToolSwap");
            tool.putBoolean("Active", true);
            tool.putString("Source", "OFFHAND");
            tool.store("PreviousMainHand", ItemStack.CODEC, bowFirst ? bow : sword);
            var migrated = load(level, output);
            conserved(migrated, sword, bow, pick, "legacy " + (bowFirst ? "bow -> tool" : "tool -> bow"));
            check(ItemStack.isSameItemSameComponents(migrated.getOffhandItem(), pick),
                    "legacy migration restores tool to its actual offhand source");
            var again = load(level, save(level, migrated));
            conserved(again, sword, bow, pick, "legacy repeated recovery");
        }
        // A broken last equipped stack must not change the legacy unwind order.
        for (boolean bowFirst : new boolean[] {false, true}) {
            var legacy = fixture(level, player);
            var sword = legacy.getMainHandItem().copy();
            var bow = named(Items.BOW, 17, "Legacy surviving bow");
            var pick = named(Items.IRON_PICKAXE, 29, "Legacy surviving pick");
            legacy.setMainHandItemForAi(ItemStack.EMPTY);
            var broken = save(level, legacy);
            broken.discard("MainHandSwapFormatVersion");
            broken.putBoolean("TemporaryBowEquipped", true);
            broken.store("TemporaryBowPreviousMainHand", ItemStack.CODEC, bowFirst ? sword : pick);
            var tool = broken.child("TemporaryToolSwap");
            tool.putBoolean("Active", true);
            tool.putString("Source", "OFFHAND");
            tool.store("PreviousMainHand", ItemStack.CODEC, bowFirst ? bow : sword);
            var recovered = load(level, broken);
            check(ItemStack.isSameItemSameComponents(recovered.getMainHandItem(), sword)
                            && physical(recovered, Items.IRON_SWORD) == 1
                            && physical(recovered, Items.BOW) == (bowFirst ? 1 : 0)
                            && physical(recovered, Items.IRON_PICKAXE) == (bowFirst ? 0 : 1),
                    "broken legacy " + (bowFirst ? "tool" : "bow") + " preserves original and surviving stack only");
        }
        // Old original bow A -> tool P -> borrowed bow B needs the live bow or cache
        // to distinguish it from original tool P -> bow A -> tool, especially after breakage.
        for (boolean brokenBow : new boolean[] {false, true}) {
            var legacy = fixture(level, player);
            var originalBow = named(Items.BOW, 11, "Legacy original bow A");
            var temporaryBow = named(Items.BOW, 23, "Legacy borrowed bow B");
            var pick = named(Items.IRON_PICKAXE, 31, "Legacy intervening pick P");
            legacy.setMainHandItemForAi(brokenBow ? ItemStack.EMPTY : temporaryBow);
            legacy.cacheMainWeaponItemForAi(originalBow);
            var legacyOutput = save(level, legacy);
            legacyOutput.discard("MainHandSwapFormatVersion");
            legacyOutput.putBoolean("TemporaryBowEquipped", true);
            legacyOutput.store("TemporaryBowPreviousMainHand", ItemStack.CODEC, pick);
            var tool = legacyOutput.child("TemporaryToolSwap");
            tool.putBoolean("Active", true);
            tool.putString("Source", "OFFHAND");
            tool.store("PreviousMainHand", ItemStack.CODEC, originalBow);
            var recovered = load(level, legacyOutput);
            check(ItemStack.isSameItemSameComponents(recovered.getMainHandItem(), originalBow)
                            && ItemStack.isSameItemSameComponents(recovered.getOffhandItem(), pick)
                            && physical(recovered, Items.BOW) == (brokenBow ? 1 : 2)
                            && physical(recovered, Items.IRON_PICKAXE) == 1
                            && ItemStack.isSameItemSameComponents(recovered.getMainWeaponItem(), originalBow),
                    "legacy original bow -> tool -> " + (brokenBow ? "broken" : "intact")
                            + " borrowed bow restores original bow, cache and tool source");
            var repeated = load(level, save(level, recovered));
            check(ItemStack.isSameItemSameComponents(repeated.getMainHandItem(), originalBow)
                            && ItemStack.isSameItemSameComponents(repeated.getOffhandItem(), pick)
                            && physical(repeated, Items.BOW) == (brokenBow ? 1 : 2),
                    "repeated save preserves a migrated original bow instead of legacy ranged repair");
        }
        // Identical old fields can represent two broken-final histories when no cache
        // identifies the original. Guarantee conservation, without asserting unknown order.
        var ambiguous = fixture(level, player);
        ambiguous.setMainHandItemForAi(ItemStack.EMPTY);
        ambiguous.cacheMainWeaponItemForAi(ItemStack.EMPTY);
        var ambiguousOutput = save(level, ambiguous);
        ambiguousOutput.discard("MainHandSwapFormatVersion");
        ambiguousOutput.putBoolean("TemporaryBowEquipped", true);
        ambiguousOutput.store("TemporaryBowPreviousMainHand", ItemStack.CODEC,
                named(Items.IRON_PICKAXE, 31, "Ambiguous surviving pick"));
        var ambiguousTool = ambiguousOutput.child("TemporaryToolSwap");
        ambiguousTool.putBoolean("Active", true);
        ambiguousTool.putString("Source", "OFFHAND");
        ambiguousTool.store("PreviousMainHand", ItemStack.CODEC,
                named(Items.BOW, 11, "Ambiguous surviving bow"));
        var ambiguousRecovered = load(level, ambiguousOutput);
        check(physical(ambiguousRecovered, Items.BOW) == 1
                        && physical(ambiguousRecovered, Items.IRON_PICKAXE) == 1
                        && ambiguousRecovered.getMainWeaponItem().isEmpty(),
                "ambiguous broken legacy order conserves both survivors without creating a cache weapon");
        var modern = fixture(level, player);
        var modernOriginalBow = named(Items.BOW, 13, "Modern original bow");
        var modernInventoryBow = named(Items.BOW, 19, "Modern spare bow");
        var modernPick = named(Items.IRON_PICKAXE, 37, "Modern intervening pick");
        modern.setMainHandItemForAi(modernOriginalBow);
        modern.cacheMainWeaponItemForAi(modernOriginalBow);
        modern.getInventory().setItem(0, modernInventoryBow);
        modern.getInventory().setItem(1, modernPick);
        var modernTool = new ToolAi(modern);
        check(modernTool.equipTool(ItemTags.PICKAXES), "modern original bow temporarily equips pick");
        modern.getMainHandItem().setDamageValue(41); modernPick.setDamageValue(41);
        var modernInterrupted = load(level, save(level, modern));
        check(ItemStack.isSameItemSameComponents(modernInterrupted.getMainHandItem(), modernOriginalBow)
                        && ItemStack.isSameItemSameComponents(find(modernInterrupted, Items.IRON_PICKAXE), modernPick),
                "modern interrupted original bow transaction restores original and live pick wear");
        var modernRepeated = load(level, save(level, modernInterrupted));
        check(ItemStack.isSameItemSameComponents(modernRepeated.getMainHandItem(), modernOriginalBow)
                        && physical(modernRepeated, Items.BOW) == 2
                        && physical(modernRepeated, Items.IRON_PICKAXE) == 1
                        && ItemStack.isSameItemSameComponents(modernRepeated.getMainWeaponItem(), modernOriginalBow),
                "modern repeated save retains original bow, spare bow and weapon cache without legacy repair");
        check(modern.equipTemporaryBowFromInventory()
                        && ItemStack.isSameItemSameComponents(modern.getMainHandItem(), modernOriginalBow)
                        && physical(modern, Items.BOW) == 2,
                "modern tool -> bow reuses saved original bow instead of materializing cache mirror");
        modernTool.restoreMainHand(); modern.restoreMainHandAfterTemporaryBow();
        check(physical(modern, Items.BOW) == 2 && physical(modern, Items.IRON_PICKAXE) == 1,
                "modern original bow repeated stops conserve both distinct bows and one pick");
        // A legacy bow-only save has no tool child and still requires recovery.
        var legacyBow = fixture(level, player);
        var original = legacyBow.getMainHandItem().copy();
        var bow = named(Items.BOW, 21, "Legacy bow-only actual bow");
        legacyBow.setMainHandItemForAi(bow);
        var output = save(level, legacyBow);
        output.discard("MainHandSwapFormatVersion");
            output.putBoolean("TemporaryBowEquipped", true);
        output.store("TemporaryBowPreviousMainHand", ItemStack.CODEC, original);
        var migrated = load(level, output);
        check(ItemStack.isSameItemSameComponents(migrated.getMainHandItem(), original)
                        && physical(migrated, Items.BOW) == 1 && physical(migrated, Items.IRON_SWORD) == 1
                        && ItemStack.isSameItemSameComponents(find(migrated, Items.BOW), bow),
                "legacy bow-only save restores original hand and exactly one worn bow");
        System.out.println("SMARTNPC_BOW_SWAP_COMPLETE nesting, native stops, reload, legacy migration, wear and cache conservation");
    }
}
