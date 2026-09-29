package com.npjt25.godzillaminusone.client;

import com.npjt25.godzillaminusone.GodzillaMinusOne;
import com.npjt25.godzillaminusone.client.particle.Fx;
import com.npjt25.godzillaminusone.client.render.GodzillaRenderer;
import com.npjt25.godzillaminusone.client.render.NavalMineRenderer;
import com.npjt25.godzillaminusone.client.render.OdoIslandMonsterRenderer;
import com.npjt25.godzillaminusone.client.render.ShindenRenderer;
import com.npjt25.godzillaminusone.entity.EffectHooks;
import com.npjt25.godzillaminusone.registry.ModEntities;
import com.npjt25.godzillaminusone.registry.ModParticles;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

@Mod.EventBusSubscriber(modid = GodzillaMinusOne.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        EffectHooks.particleKeyframe = Fx::keyframe;
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.GODZILLA.get(), GodzillaRenderer::new);
        event.registerEntityRenderer(ModEntities.SHINDEN.get(), ShindenRenderer::new);
        event.registerEntityRenderer(ModEntities.NAVAL_MINE.get(), NavalMineRenderer::new);
        event.registerEntityRenderer(ModEntities.ODO_ISLAND_MONSTER.get(), OdoIslandMonsterRenderer::new);
    }

    @SubscribeEvent
    public static void registerParticles(RegisterParticleProvidersEvent event) {
        for (RegistryObject<SimpleParticleType> type : List.of(ModParticles.GLOW, ModParticles.BLUE_FLAME, ModParticles.SMOKE,
                ModParticles.SHOCKWAVE, ModParticles.BLACK_RAIN, ModParticles.GUNLIGHT, ModParticles.MUSHROOM)) {
            SimpleParticleType particleType = type.get();
            // The big effects are built directly by client.particle.Fx; the provider captures the sprites and
            // gives /particle a plain version of each.
            event.registerSpriteSet(particleType, sprites -> {
                Fx.registerSprites(particleType, sprites);
                return (options, level, x, y, z, dx, dy, dz) -> Fx.basic(particleType, level, x, y, z, dx, dy, dz);
            });
        }
    }

    private ClientSetup() {
    }
}
