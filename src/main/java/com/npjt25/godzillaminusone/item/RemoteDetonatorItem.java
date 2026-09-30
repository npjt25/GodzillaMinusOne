package com.npjt25.godzillaminusone.item;

import com.npjt25.godzillaminusone.entity.NavalMineEntity;
import com.npjt25.godzillaminusone.entity.SearchlightEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Point it at a naval mine (up to {@link #RANGE} blocks away) and right-click: the mine goes off. */
public class RemoteDetonatorItem extends Item {
    public static final double RANGE = 160.0;

    public RemoteDetonatorItem(Properties properties) {
        super(properties);
    }

    /** The naval mine under the player's crosshair, if there is one in reach and in sight. */
    @Nullable
    public static NavalMineEntity aimedMine(Level level, Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        double reach = SearchlightEntity.loadedReach(level, eye, look, RANGE);
        Vec3 end = eye.add(look.scale(reach));
        // walls and hills are in the way; water isn't (the mines float in it)
        HitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (block.getType() != HitResult.Type.MISS) {
            end = block.getLocation();
        }
        AABB area = new AABB(eye, end).inflate(2.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, area,
                e -> e instanceof NavalMineEntity && e.isAlive(), eye.distanceToSqr(end));
        return hit != null && hit.getEntity() instanceof NavalMineEntity mine ? mine : null;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.PLAYERS, 0.6F, 1.6F);
        NavalMineEntity mine = aimedMine(level, player);
        if (mine == null) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable(getDescriptionId() + ".no_target"), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        if (!level.isClientSide) {
            mine.detonate(player);
        }
        player.getCooldowns().addCooldown(this, 10);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".desc"));
    }
}
