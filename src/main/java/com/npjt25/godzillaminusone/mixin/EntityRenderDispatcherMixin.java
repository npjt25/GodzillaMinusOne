package com.npjt25.godzillaminusone.mixin;

import com.npjt25.godzillaminusone.client.SearchlightGlow;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The light an entity is drawn in (what the world gives it, from where it stands): a searchlight's beam shining on it
 * adds its own ({@link SearchlightGlow}). Every entity is drawn in this light, whichever renderer draws it (GeckoLib's
 * and other mods' too), and so is the player's hand in first person.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @Inject(method = "getPackedLightCoords", at = @At("RETURN"), cancellable = true)
    private void godzillaminusone$searchlightGlow(Entity entity, float partialTick, CallbackInfoReturnable<Integer> cir) {
        int light = cir.getReturnValueI();
        int lit = SearchlightGlow.brighten(entity, light, partialTick);
        if (lit != light) {
            cir.setReturnValue(lit);
        }
    }
}
