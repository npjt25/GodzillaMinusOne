package com.npjt25.godzillaminusone.entity;

import net.minecraft.world.entity.Entity;

/**
 * Bridge from common entity code to client-only effect code. The client installs the real
 * implementations during client setup; on a dedicated server these stay no-ops.
 */
public final class EffectHooks {
    @FunctionalInterface
    public interface ParticleKeyframe {
        void spawn(Entity entity, String effect, String locator);
    }

    public static ParticleKeyframe particleKeyframe = (entity, effect, locator) -> {
    };

    /** Where Godzilla's beam is stopped by a block: its impact (client.particle.Fx.beamImpact). */
    @FunctionalInterface
    public interface BeamImpact {
        void spawn(GodzillaEntity godzilla, net.minecraft.world.phys.Vec3 at, net.minecraft.world.phys.Vec3 normal,
                   net.minecraft.world.level.block.state.BlockState block);
    }

    public static BeamImpact beamImpact = (godzilla, at, normal, block) -> {
    };

    private EffectHooks() {
    }
}
