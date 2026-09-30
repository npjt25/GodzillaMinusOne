package com.npjt25.godzillaminusone.registry;

import com.npjt25.godzillaminusone.GodzillaMinusOne;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.HashMap;
import java.util.Map;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, GodzillaMinusOne.MODID);

    /** Lookup by event name, used by animation sound keyframes (e.g. "godzilla.step"). */
    public static final Map<String, RegistryObject<SoundEvent>> BY_NAME = new HashMap<>();

    public static final RegistryObject<SoundEvent> GODZILLA_AMBIENT = register("godzilla.ambient");
    public static final RegistryObject<SoundEvent> GODZILLA_ROAR = register("godzilla.roar");
    public static final RegistryObject<SoundEvent> GODZILLA_ROAR2 = register("godzilla.roar2");
    public static final RegistryObject<SoundEvent> GODZILLA_EMERGE = register("godzilla.emerge");
    public static final RegistryObject<SoundEvent> GODZILLA_DEATH = register("godzilla.death");
    public static final RegistryObject<SoundEvent> GODZILLA_STEP = register("godzilla.step");
    public static final RegistryObject<SoundEvent> GODZILLA_STOMP = register("godzilla.stomp");
    public static final RegistryObject<SoundEvent> GODZILLA_ATTACK = register("godzilla.attack");
    public static final RegistryObject<SoundEvent> GODZILLA_HURT = register("godzilla.hurt");
    public static final RegistryObject<SoundEvent> GODZILLA_RADIATION = register("godzilla.radiation");
    public static final RegistryObject<SoundEvent> GODZILLA_CHARGE = register("godzilla.charge");
    public static final RegistryObject<SoundEvent> GODZILLA_FULL_CHARGE = register("godzilla.full_charge");
    public static final RegistryObject<SoundEvent> GODZILLA_INHALE = register("godzilla.inhale");
    public static final RegistryObject<SoundEvent> GODZILLA_ATOMIC_BLAST = register("godzilla.atomic_blast");
    public static final RegistryObject<SoundEvent> SHINDEN_ENGINE = register("shinden.engine");
    public static final RegistryObject<SoundEvent> SHINDEN_GUN = register("shinden.gun");
    public static final RegistryObject<SoundEvent> NAVAL_MINE_EXPLODE = register("naval_mine.explode");
    public static final RegistryObject<SoundEvent> NUKE_ALARM = register("nuke.alarm");
    public static final RegistryObject<SoundEvent> ODO_AMBIENT = register("odo.ambient");
    public static final RegistryObject<SoundEvent> ODO_ROAR = register("odo.roar");
    public static final RegistryObject<SoundEvent> ODO_BITE = register("odo.bite");
    public static final RegistryObject<SoundEvent> ODO_STOMP = register("odo.stomp");
    public static final RegistryObject<SoundEvent> ODO_STEP = register("odo.step");
    public static final RegistryObject<SoundEvent> ODO_HURT = register("odo.hurt");
    public static final RegistryObject<SoundEvent> ODO_DEATH = register("odo.death");
    public static final RegistryObject<SoundEvent> SHINSEI_MARU_GUN = register("shinsei_maru.gun");

    private static RegistryObject<SoundEvent> register(String name) {
        RegistryObject<SoundEvent> sound = SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(GodzillaMinusOne.id(name)));
        BY_NAME.put(name, sound);
        return sound;
    }

    private ModSounds() {
    }
}
