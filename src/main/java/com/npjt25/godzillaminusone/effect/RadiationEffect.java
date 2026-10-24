package com.npjt25.godzillaminusone.effect;

import com.npjt25.godzillaminusone.registry.ModDamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * Radiation (its icon the radiation trefoil): what standing near Godzilla, or in an atomic blast's fallout, does to
 * you. It eats away at your health as Wither does, a point every 2 s at level I and twice as often each level up, as
 * radiation damage ("died of radiation poisoning"), which armour doesn't stop; a whole hazmat suit keeps it off
 * (entity.Radiation).
 */
public class RadiationEffect extends MobEffect {
    public RadiationEffect() {
        super(MobEffectCategory.HARMFUL, 0x9CD12B);
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if (!entity.level().isClientSide) {
            entity.hurt(ModDamageTypes.source(entity.level(), ModDamageTypes.RADIATION, null), 1.0F);
        }
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        int every = 40 >> amplifier;
        return every <= 0 || duration % every == 0;
    }
}
