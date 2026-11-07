package com.npjt25.godzillaminusone.mixin;

import com.npjt25.godzillaminusone.entity.GodzillaStride;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A player playing as Godzilla who walks through things (entity.GodzillaStride): walls and hills don't stop him going
 * forwards, backwards or sideways; the ground still holds him up (and ceilings still stop a jump).
 */
@Mixin(Entity.class)
public abstract class EntityCollideMixin {
    @Inject(method = "collide(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;", at = @At("RETURN"), cancellable = true)
    private void godzillaminusone$walkThrough(Vec3 movement, CallbackInfoReturnable<Vec3> cir) {
        if ((Object) this instanceof Player player && GodzillaStride.walksThrough(player)) {
            cir.setReturnValue(new Vec3(movement.x, cir.getReturnValue().y, movement.z));
        }
    }
}
