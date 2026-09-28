package com.npjt25.godzillaminusone.registry;

import com.npjt25.godzillaminusone.GodzillaMinusOne;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * One particle type per add-on particle texture. The effects themselves (sizes, colours, motion) are
 * built client-side in {@code client.particle.Fx}, mirroring the add-on's Bedrock particle files.
 */
public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, GodzillaMinusOne.MODID);

    public static final RegistryObject<SimpleParticleType> GLOW = register("glow");
    public static final RegistryObject<SimpleParticleType> BLUE_FLAME = register("blue_flame");
    public static final RegistryObject<SimpleParticleType> SMOKE = register("smoke");
    public static final RegistryObject<SimpleParticleType> SHOCKWAVE = register("shockwave");
    public static final RegistryObject<SimpleParticleType> BLACK_RAIN = register("black_rain");
    public static final RegistryObject<SimpleParticleType> GUNLIGHT = register("gunlight");
    public static final RegistryObject<SimpleParticleType> MUSHROOM = register("mushroom");

    private static RegistryObject<SimpleParticleType> register(String name) {
        return PARTICLES.register(name, () -> new SimpleParticleType(true));
    }

    private ModParticles() {
    }
}
