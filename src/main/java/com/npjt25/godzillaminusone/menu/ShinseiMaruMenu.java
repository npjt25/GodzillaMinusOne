package com.npjt25.godzillaminusone.menu;

import com.npjt25.godzillaminusone.entity.ShinseiMaruEntity;
import com.npjt25.godzillaminusone.registry.ModMenus;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** The Shinsei Maru's mine hold: {@link ShinseiMaruEntity#MINE_SLOTS} slots for naval mines, and nothing else. */
public class ShinseiMaruMenu extends AbstractContainerMenu {
    private final Container mines;
    @Nullable
    private final ShinseiMaruEntity boat;

    /** On the client (the slots are filled in by the server). */
    public ShinseiMaruMenu(int id, Inventory inventory) {
        this(id, inventory, new SimpleContainer(ShinseiMaruEntity.MINE_SLOTS), null);
    }

    public ShinseiMaruMenu(int id, Inventory inventory, Container mines, @Nullable ShinseiMaruEntity boat) {
        super(ModMenus.SHINSEI_MARU.get(), id);
        checkContainerSize(mines, ShinseiMaruEntity.MINE_SLOTS);
        this.mines = mines;
        this.boat = boat;
        mines.startOpen(inventory.player);
        for (int i = 0; i < ShinseiMaruEntity.MINE_SLOTS; i++) {
            addSlot(new Slot(mines, i, 8 + i * 18, 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return ShinseiMaruEntity.isMine(stack);
                }
            });
        }
        // the player's inventory, laid out as under a one-row chest
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 49 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, 107));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        int hold = ShinseiMaruEntity.MINE_SLOTS;
        if (index < hold) {
            if (!moveItemStackTo(stack, hold, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!ShinseiMaruEntity.isMine(stack) || !moveItemStackTo(stack, 0, hold, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return before;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.boat == null || this.boat.canReachHold(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.mines.stopOpen(player);
    }

    /** For the game tests: the hold's slots. */
    public Slot holdSlot(int i) {
        return this.slots.get(i);
    }
}
