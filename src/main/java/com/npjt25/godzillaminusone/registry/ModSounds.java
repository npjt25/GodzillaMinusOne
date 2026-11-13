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
    public static final RegistryObject<SoundEvent> GODZILLA_BITE = register("godzilla.bite");
    /**
     * Godzilla Minus Zero's own (the mod author's), all played by his animations' sound keyframes
     * (tools/make_minus_zero_animations.py): charged again as his plates light up the second time, his second beam
     * firing, the plate at the tip of his tail punching up for his tail strike, and the tail swinging round
     * (godzilla_minus_zero_tail_swing.ogg: tools/make_sounds.py).
     */
    public static final RegistryObject<SoundEvent> MINUS_ZERO_CHARGED_2ND_TIME = register("godzilla_minus_zero.charged_2nd_time");
    public static final RegistryObject<SoundEvent> MINUS_ZERO_2ND_FIRE_BEAM = register("godzilla_minus_zero.2nd_fire_beam");
    public static final RegistryObject<SoundEvent> MINUS_ZERO_DORSAL_FIN_PUNCH_UP = register("godzilla_minus_zero.dorsal_fin_punch_up");
    public static final RegistryObject<SoundEvent> MINUS_ZERO_TAIL_SWING = register("godzilla_minus_zero.tail_swing");
    /** Roars for listeners 200+ blocks away (GodzillaEntity.broadcastRoar picks one per listener, alternating). */
    public static final RegistryObject<SoundEvent> GODZILLA_ROAR_FAR = register("godzilla.roar_far");
    public static final RegistryObject<SoundEvent> GODZILLA_ROAR_FAR2 = register("godzilla.roar_far2");
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
    public static final RegistryObject<SoundEvent> ODO_ROAR_FAR = register("odo.roar_far");
    public static final RegistryObject<SoundEvent> ODO_SPOTTED = register("odo.spotted");
    public static final RegistryObject<SoundEvent> ODO_SPAWN = register("odo.spawn");
    public static final RegistryObject<SoundEvent> ODO_NUKED = register("odo.nuked");
    // King Ghidorah (his sound pack; named for when each plays)
    public static final RegistryObject<SoundEvent> KING_GHIDORAH_WING_SPAN_ROAR = register("king_ghidorah.wing_span_roar");
    public static final RegistryObject<SoundEvent> KING_GHIDORAH_ENTRANCE_ROAR = register("king_ghidorah.entrance_roar");
    public static final RegistryObject<SoundEvent> KING_GHIDORAH_IDLE = register("king_ghidorah.idle");
    public static final RegistryObject<SoundEvent> KING_GHIDORAH_HURT = register("king_ghidorah.hurt");
    public static final RegistryObject<SoundEvent> KING_GHIDORAH_DEATH = register("king_ghidorah.death");
    public static final RegistryObject<SoundEvent> KING_GHIDORAH_BITE = register("king_ghidorah.bite");
    public static final RegistryObject<SoundEvent> KING_GHIDORAH_GRAVITY_BEAM = register("king_ghidorah.gravity_beam");
    public static final RegistryObject<SoundEvent> KING_GHIDORAH_FLYING = register("king_ghidorah.flying");
    public static final RegistryObject<SoundEvent> KING_GHIDORAH_FOOTSTEP_1 = register("king_ghidorah.footstep_1");
    public static final RegistryObject<SoundEvent> KING_GHIDORAH_FOOTSTEP_2 = register("king_ghidorah.footstep_2");
    public static final RegistryObject<SoundEvent> KING_GHIDORAH_FOOTSTEP_3 = register("king_ghidorah.footstep_3");
    // (and the mod's own, for his entrance)
    public static final RegistryObject<SoundEvent> KING_GHIDORAH_RUMBLE = register("king_ghidorah.rumble");
    public static final RegistryObject<SoundEvent> KING_GHIDORAH_EARTH_CRACK = register("king_ghidorah.earth_crack");
    public static final RegistryObject<SoundEvent> KING_GHIDORAH_WING_SPREAD = register("king_ghidorah.wing_spread");
    public static final RegistryObject<SoundEvent> SHINSEI_MARU_GUN = register("shinsei_maru.gun");
    public static final RegistryObject<SoundEvent> SHINSEI_MARU_MOTOR = register("shinsei_maru.motor");

    private static RegistryObject<SoundEvent> register(String name) {
        RegistryObject<SoundEvent> sound = SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(GodzillaMinusOne.id(name)));
        BY_NAME.put(name, sound);
        return sound;
    }

    private ModSounds() {
    }
}
