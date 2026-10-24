package com.npjt25.godzillaminusone.entity;

import com.npjt25.godzillaminusone.GodzillaMinusOne;
import com.npjt25.godzillaminusone.item.HazmatSuitItem;
import com.npjt25.godzillaminusone.registry.ModDamageTypes;
import com.npjt25.godzillaminusone.registry.ModEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * Radiation: the Radiation effect (effect.RadiationEffect) that Godzilla's aura and an atomic blast's fallout give,
 * and the hazmat suit that keeps it off. Wearing the whole suit (hood, suit, trousers and boots), nothing gives you
 * Radiation, any you had wears off at once, and radiation damage (the heat of Godzilla's charge too) doesn't touch you.
 */
@Mod.EventBusSubscriber(modid = GodzillaMinusOne.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Radiation {
    private static final EquipmentSlot[] SUIT = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    /** Whether {@code entity} is wearing the whole hazmat suit. */
    public static boolean shielded(LivingEntity entity) {
        for (EquipmentSlot slot : SUIT) {
            if (!(entity.getItemBySlot(slot).getItem() instanceof HazmatSuitItem)) {
                return false;
            }
        }
        return true;
    }

    /** {@code entity} gets Radiation (level {@code amplifier} + 1) for {@code ticks}, unless it's shielded. */
    public static void irradiate(LivingEntity entity, int ticks, int amplifier, @Nullable Entity from) {
        if (!shielded(entity)) {
            entity.addEffect(new MobEffectInstance(ModEffects.RADIATION.get(), ticks, amplifier), from);
        }
    }

    @SubscribeEvent
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        if (event.getEffectInstance().getEffect() == ModEffects.RADIATION.get() && shielded(event.getEntity())) {
            event.setResult(Event.Result.DENY);
        }
    }

    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        if (event.getSource().is(ModDamageTypes.RADIATION) && shielded(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (!entity.level().isClientSide && entity.tickCount % 10 == 0 && entity.hasEffect(ModEffects.RADIATION.get()) && shielded(entity)) {
            entity.removeEffect(ModEffects.RADIATION.get()); // (put the suit on, and what you'd caught washes off)
        }
    }

    private Radiation() {
    }
}
