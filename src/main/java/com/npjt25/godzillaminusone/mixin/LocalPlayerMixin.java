package com.npjt25.godzillaminusone.mixin;

import com.npjt25.godzillaminusone.entity.GodzillaStride;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Walking through things as Godzilla (entity.GodzillaStride), you aren't pushed back out of the wall you're in. */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
    @Inject(method = "moveTowardsClosestSpace", at = @At("HEAD"), cancellable = true)
    private void godzillaminusone$stayInside(double x, double z, CallbackInfo ci) {
        if (GodzillaStride.walksThrough((LocalPlayer) (Object) this)) {
            ci.cancel();
        }
    }
}
