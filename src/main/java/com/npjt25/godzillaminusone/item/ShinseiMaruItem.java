package com.npjt25.godzillaminusone.item;

import com.npjt25.godzillaminusone.entity.ShinseiMaruEntity;
import com.npjt25.godzillaminusone.registry.ModEntities;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Puts the Shinsei Maru in the water (or on land) where you look, pointing the way you face, like a boat. */
public class ShinseiMaruItem extends Item {
    public ShinseiMaruItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.ANY);
        if (hit.getType() != HitResult.Type.BLOCK) {
            return InteractionResultHolder.pass(stack);
        }
        if (level instanceof ServerLevel serverLevel) {
            ShinseiMaruEntity boat = ModEntities.SHINSEI_MARU.get().create(serverLevel);
            if (boat == null) {
                return InteractionResultHolder.fail(stack);
            }
            Vec3 at = hit.getLocation();
            boat.moveTo(at.x, at.y, at.z, player.getYRot(), 0.0F);
            double surface = boat.waterSurface();
            if (!Double.isNaN(surface)) {
                boat.setPos(at.x, surface - ShinseiMaruEntity.DRAFT, at.z);
            }
            if (!level.noCollision(boat, boat.getBoundingBox().deflate(0.1))) {
                return InteractionResultHolder.fail(stack);
            }
            level.addFreshEntity(boat);
            level.gameEvent(player, GameEvent.ENTITY_PLACE, hit.getBlockPos());
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            player.awardStat(Stats.ITEM_USED.get(this));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".desc"));
    }
}
