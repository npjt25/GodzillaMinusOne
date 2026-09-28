package com.npjt25.godzillaminusone.item;

import com.npjt25.godzillaminusone.entity.ShindenEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * goji:torrid, the Shinden's cannon. Handed to the pilot when they climb in and taken away when they leave;
 * it can't be dropped (the add-on locks it in the inventory).
 */
public class TorridItem extends Item {
    private static final int COOLDOWN_TICKS = 10;

    public TorridItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player.getVehicle() instanceof ShindenEntity shinden)) {
            return InteractionResultHolder.fail(stack);
        }
        if (!level.isClientSide) {
            shinden.fireGuns(player);
        }
        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide && !(entity.getVehicle() instanceof ShindenEntity)) {
            stack.setCount(0);
        }
    }

    @Override
    public boolean onDroppedByPlayer(ItemStack item, Player player) {
        return false;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.godzillaminusone.torrid.desc"));
    }
}
