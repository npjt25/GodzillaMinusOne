package com.npjt25.godzillaminusone.mixin;

import com.npjt25.godzillaminusone.client.GhidorahSky;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** King Ghidorah's weather (client.GhidorahSky): the sky a dark grey, the clouds heavy, the daylight dimmed. */
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
    private void godzillaminusone$overcastSky(Vec3 pos, float partialTick, CallbackInfoReturnable<Vec3> cir) {
        float f = GhidorahSky.factor(partialTick);
        if (f > 0.0F) {
            cir.setReturnValue(GhidorahSky.overcast(cir.getReturnValue(), f, 0.35));
        }
    }

    @Inject(method = "getCloudColor", at = @At("RETURN"), cancellable = true)
    private void godzillaminusone$heavyClouds(float partialTick, CallbackInfoReturnable<Vec3> cir) {
        float f = GhidorahSky.factor(partialTick);
        if (f > 0.0F) {
            cir.setReturnValue(GhidorahSky.overcast(cir.getReturnValue(), f, 0.45));
        }
    }

    @Inject(method = "getSkyDarken", at = @At("RETURN"), cancellable = true)
    private void godzillaminusone$dimDaylight(float partialTick, CallbackInfoReturnable<Float> cir) {
        float f = GhidorahSky.factor(partialTick);
        if (f > 0.0F) {
            cir.setReturnValue(GhidorahSky.daylight(cir.getReturnValue(), f));
        }
    }
}
