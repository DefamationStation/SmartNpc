package com.pla.smart_npc.entity.ai;

import com.pla.smart_npc.util.SmartNpcItemUtil;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BowItem;

import com.pla.smart_npc.entity.PlayerNpcEntity;
import com.pla.smart_npc.util.InventoryUtils;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EquipmentSlot;

import net.minecraft.world.item.ItemStack;

import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public final class ToolAi {
    private final PlayerNpcEntity playerNpc;
    /** One pending transaction per entity, independent of goal instance lifetimes. */
    public static final class SwapState {
        private ItemStack previousMainHand = ItemStack.EMPTY;
        private MainHandSource currentMainHandSource = MainHandSource.NONE;
        private boolean swappedMainHand;
        private ToolAi owner;
        private boolean bowUsed;
    }

    private SwapState state() {
        return this.playerNpc.getToolSwapState();
    }

    public static void save(PlayerNpcEntity npc, ValueOutput output) {
        SwapState state = npc.getToolSwapState();
        output.putInt("MainHandSwapFormatVersion", 1);
        output.discard("TemporaryToolSwap");
        if (!state.swappedMainHand) return;
        ValueOutput swap = output.child("TemporaryToolSwap");
        swap.putBoolean("Active", true);
        swap.putBoolean("BowUsed", state.bowUsed);
        swap.putString("Source", state.currentMainHandSource.name());
        if (!state.previousMainHand.isEmpty()) {
            swap.store("PreviousMainHand", ItemStack.CODEC, state.previousMainHand);
        }
    }

    /** Goals do not resume after load; complete the saved transaction before cache repair. */
    public static boolean restoreAfterLoad(PlayerNpcEntity npc, ValueInput input) {
        SwapState state = npc.getToolSwapState();
        state.previousMainHand = ItemStack.EMPTY;
        state.currentMainHandSource = MainHandSource.NONE;
        state.swappedMainHand = false;
        state.owner = null;
        state.bowUsed = false;
        boolean legacyBow = input.getBooleanOr("TemporaryBowEquipped", false);
        ItemStack legacyPrevious = legacyBow
                ? input.read("TemporaryBowPreviousMainHand", ItemStack.CODEC).orElse(ItemStack.EMPTY)
                : ItemStack.EMPTY;
        boolean legacyTool = input.child("TemporaryToolSwap")
                .map(swap -> swap.getBooleanOr("Active", false)).orElse(false);
        ItemStack toolPrevious = input.child("TemporaryToolSwap")
                .flatMap(swap -> swap.read("PreviousMainHand", ItemStack.CODEC)).orElse(ItemStack.EMPTY);
        boolean bowOnTop = false;
        if (legacyBow) {
            ItemStack current = npc.getMainHandItem();
            if (!legacyTool || current.getItem() instanceof BowItem) {
                bowOnTop = true;
            } else if (current.isEmpty()) {
                // A non-bow tool predecessor proves tool -> bow. An original bow instead
                // requires the weapon cache to identify it when the final stack broke.
                bowOnTop = !(toolPrevious.getItem() instanceof BowItem)
                        || (!toolPrevious.isEmpty()
                        && ItemStack.isSameItemSameComponents(npc.getMainWeaponItem(), toolPrevious)
                        && !ItemStack.isSameItemSameComponents(npc.getMainWeaponItem(), legacyPrevious));
                // Without cache evidence, original-bow -> tool -> broken-bow and
                // original-tool -> bow -> broken-tool are indistinguishable in old saves.
                // Default to bow -> tool; conserve both saved stacks without inventing gear.
            }
        }
        if (bowOnTop) restoreLegacyBow(npc, legacyPrevious);
        input.child("TemporaryToolSwap").ifPresent(swap -> {
            if (!swap.getBooleanOr("Active", false)) return;
            state.previousMainHand = swap.read("PreviousMainHand", ItemStack.CODEC).orElse(ItemStack.EMPTY);
            try {
                state.currentMainHandSource = MainHandSource.valueOf(swap.getStringOr("Source", "NONE"));
            } catch (IllegalArgumentException ignored) {
                state.currentMainHandSource = MainHandSource.NONE;
            }
            state.swappedMainHand = true;
            state.bowUsed = swap.getBooleanOr("BowUsed", false);
            ToolAi recovery = new ToolAi(npc);
            state.owner = recovery;
            recovery.restoreMainHand();

        });
        if (legacyBow && !bowOnTop) restoreLegacyBow(npc, legacyPrevious);
        return legacyBow || legacyTool;
    }

    private static void restoreLegacyBow(PlayerNpcEntity npc, ItemStack previous) {
        ItemStack current = npc.getMainHandItem().copy();
        npc.setMainHandItemForAi(previous);
        if (!current.isEmpty()) {
            ItemStack remainder = InventoryUtils.addItemAndReturnRemainder(npc.getInventory(), current);
            if (!remainder.isEmpty()) npc.spawnAtLocation(remainder);
        }
        npc.setSwapToBowCooldown();
    }

    public boolean ownsSwap() {
        return this.state().swappedMainHand && this.state().owner == this;
    }

    /** Bow uses the same original hand and owner guard as tools, and only consumes inventory gear. */
    public boolean equipInventoryBow() {
        if (this.playerNpc.getMainHandItem().getItem() instanceof BowItem) {
            if (this.state().swappedMainHand) {
                this.state().owner = this;
                this.state().bowUsed = true;
            }
            return true;
        }
        if (this.state().swappedMainHand && this.state().previousMainHand.getItem() instanceof BowItem) {
            this.state().owner = this;
            this.restoreMainHand();
            return true;
        }
        for (int i = 0; i < this.playerNpc.getInventory().getContainerSize(); i++) {
            ItemStack stack = this.playerNpc.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof BowItem) {
                this.swapMainHandWithSlot(i, stack);
                this.state().bowUsed = true;
                return true;
            }
        }
        return false;
    }

    public ToolAi(PlayerNpcEntity playerNpc) {
        this.playerNpc = playerNpc;
    }

    public boolean equipBestToolFor(BlockState state) {
        Object toolClass = preferredToolFor(state);
        if (toolClass == null) {
            return true;
        }
        if (this.equipTool(toolClass)) {
            return true;
        }
        if (toolClass == net.minecraft.tags.ItemTags.SHOVELS) {
            this.equipEmptyMainHand();
        }
        return false;
    }

    public boolean hasPreferredToolFor(BlockState state) {
        Object toolClass = preferredToolFor(state);
        return toolClass == null || this.hasTool(toolClass);
    }

    public boolean hasTool(Object toolClass) {
        return this.playerNpc.hasCarriedTool(toolClass)
                || (this.state().swappedMainHand && SmartNpcItemUtil.matches(toolClass, this.state().previousMainHand.getItem()));
    }

    public static Object preferredToolFor(BlockState state) {
        if (state.is(BlockTags.LOGS)
                || state.is(BlockTags.LEAVES)
                || state.is(BlockTags.MINEABLE_WITH_AXE)) {
            return net.minecraft.tags.ItemTags.AXES;
        }
        if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)
                || state.is(Blocks.DIRT)
                || state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.SAND)
                || state.is(Blocks.RED_SAND)) {
            return net.minecraft.tags.ItemTags.SHOVELS;
        }
        if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
            return ItemTags.PICKAXES;
        }
        return null;
    }

    public boolean equipTool(Object toolClass) {
        if (SmartNpcItemUtil.matches(toolClass, this.playerNpc.getMainHandItem().getItem())) {
            if (this.state().swappedMainHand) this.state().owner = this;
            return true;
        }
        if (this.state().swappedMainHand
                && SmartNpcItemUtil.matches(toolClass, this.state().previousMainHand.getItem())) {
            this.state().owner = this;
            this.restoreMainHand();
            return true;
        }
        for (int i = 0; i < this.playerNpc.getInventory().getContainerSize(); i++) {
            ItemStack stack = this.playerNpc.getInventory().getItem(i);
            if (stack.isEmpty() || !SmartNpcItemUtil.matches(toolClass, stack.getItem())) {
                continue;
            }

            this.swapMainHandWithSlot(i, stack);
            return true;
        }

        ItemStack offhandTool = this.playerNpc.getOffhandItem();
        if (!offhandTool.isEmpty() && SmartNpcItemUtil.matches(toolClass, offhandTool.getItem())) {
            this.swapMainHandWithOffhand(offhandTool);
            return true;
        }

        ItemStack mainWeaponTool = this.playerNpc.takeMainWeaponItem(stack -> SmartNpcItemUtil.matches(toolClass, stack.getItem()));
        if (!mainWeaponTool.isEmpty()) {
            this.swapMainHandWithReserved(mainWeaponTool, MainHandSource.MAIN_WEAPON);
            return true;
        }

        ItemStack offWeaponTool = this.playerNpc.takeOffWeaponItem(stack -> SmartNpcItemUtil.matches(toolClass, stack.getItem()));
        if (!offWeaponTool.isEmpty()) {
            this.swapMainHandWithReserved(offWeaponTool, MainHandSource.OFF_WEAPON);
            return true;
        }
        return false;
    }

    public boolean equipItem(ItemLike itemLike) {
        if (this.playerNpc.getMainHandItem().is(itemLike.asItem())) {
            if (this.state().swappedMainHand) this.state().owner = this;
            return true;
        }
        if (this.state().swappedMainHand && this.state().previousMainHand.is(itemLike.asItem())) {
            this.state().owner = this;
            this.restoreMainHand();
            return true;
        }
        for (int i = 0; i < this.playerNpc.getInventory().getContainerSize(); i++) {
            ItemStack stack = this.playerNpc.getInventory().getItem(i);
            if (stack.isEmpty() || !stack.is(itemLike.asItem())) {
                continue;
            }

            this.swapMainHandWithSlot(i, stack);
            return true;
        }
        ItemStack offhandItem = this.playerNpc.getOffhandItem();
        if (!offhandItem.isEmpty() && offhandItem.is(itemLike.asItem())) {
            this.swapMainHandWithOffhand(offhandItem);
            return true;
        }
        return false;
    }

    public void restoreMainHand() {
        if (!this.state().swappedMainHand || this.state().owner != this) {
            return;
        }
        ItemStack current = this.playerNpc.getMainHandItem().copy();
        this.stashCurrentMainHand(current);
        this.playerNpc.setMainHandItemForAi(this.state().previousMainHand);
        this.state().swappedMainHand = false;
        this.state().owner = null;
        if (this.state().bowUsed) this.playerNpc.setSwapToBowCooldown();
        this.state().bowUsed = false;
        this.state().previousMainHand = ItemStack.EMPTY;
        this.state().currentMainHandSource = MainHandSource.NONE;
    }

    public void equipEmptyMainHand() {
        if (this.state().swappedMainHand) this.state().owner = this;
        ItemStack current = this.playerNpc.getMainHandItem().copy();
        if (current.isEmpty()) {
            return;
        }
        if (!this.state().swappedMainHand) {
            this.state().previousMainHand = current.copy();
            this.state().swappedMainHand = true;
        } else {
            this.stashCurrentMainHand(current);
        }
        this.state().owner = this;
        this.playerNpc.setMainHandItemForAi(ItemStack.EMPTY);
        this.state().currentMainHandSource = MainHandSource.NONE;
    }

    private void swapMainHandWithSlot(int slot, ItemStack stack) {
        ItemStack selected = stack.copy();
        this.playerNpc.getInventory().setItem(slot, ItemStack.EMPTY);
        ItemStack current = this.playerNpc.getMainHandItem().copy();
        if (!this.state().swappedMainHand) {
            this.state().previousMainHand = current.copy();
            this.state().swappedMainHand = true;
        } else {
            this.stashCurrentMainHand(current);
        }
        this.state().owner = this;
        this.playerNpc.setMainHandItemForAi(selected);
        this.state().currentMainHandSource = MainHandSource.INVENTORY;
    }

    private void swapMainHandWithOffhand(ItemStack stack) {
        ItemStack selected = stack.copy();
        this.playerNpc.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        ItemStack current = this.playerNpc.getMainHandItem().copy();
        if (!this.state().swappedMainHand) {
            this.state().previousMainHand = current.copy();
            this.state().swappedMainHand = true;
        } else {
            this.stashCurrentMainHand(current);
        }
        this.state().owner = this;
        this.playerNpc.setMainHandItemForAi(selected);
        this.state().currentMainHandSource = MainHandSource.OFFHAND;
    }

    private void swapMainHandWithReserved(ItemStack stack, MainHandSource source) {
        ItemStack current = this.playerNpc.getMainHandItem().copy();
        if (!this.state().swappedMainHand) {
            this.state().previousMainHand = current.copy();
            this.state().swappedMainHand = true;
        } else {
            this.stashCurrentMainHand(current);
        }
        this.state().owner = this;
        this.playerNpc.setMainHandItemForAi(stack.copy());
        this.state().currentMainHandSource = source;
    }

    private void stashCurrentMainHand(ItemStack stack) {
        if (stack.isEmpty()) {
            this.state().currentMainHandSource = MainHandSource.NONE;
            return;
        }

        if (this.state().currentMainHandSource == MainHandSource.OFF_WEAPON
                && this.playerNpc.getOffWeaponItem().isEmpty()) {
            this.playerNpc.setOffWeaponItem(stack);
        } else if (this.state().currentMainHandSource == MainHandSource.OFFHAND
                && this.playerNpc.getOffhandItem().isEmpty()) {
            this.playerNpc.setItemSlot(EquipmentSlot.OFFHAND, stack);
        } else {
            ItemStack remainder = InventoryUtils.addItemAndReturnRemainder(this.playerNpc.getInventory(), stack);
            if (!remainder.isEmpty()) this.playerNpc.spawnAtLocation(remainder);
        }
        this.state().currentMainHandSource = MainHandSource.NONE;
    }

    private enum MainHandSource {
        NONE,
        INVENTORY,
        OFFHAND,
        MAIN_WEAPON,
        OFF_WEAPON
    }
}
