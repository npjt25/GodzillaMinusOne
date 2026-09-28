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

    private EffectHooks() {
    }
}
