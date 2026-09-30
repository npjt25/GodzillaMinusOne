package com.npjt25.godzillaminusone.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;

/**
 * The fiery puffs around the mushroom cloud: Alex's Caves' nuclear smoke and explosion particles (SmallExplosionParticle,
 * its NukeFactory: vanilla's smoke or explosion sprites played through, tinted amber and darkening as they go, slowing
 * as they spread), made {@code size} times as big as the source's for a cloud that many times its size.
 */
public class NukePuffParticle extends TextureSheetParticle {
    /** The source's tint, 0xFFB300. */
    private static final float RED = 1.0F;
    private static final float GREEN = 0xB3 / 255.0F;
    private static final float BLUE = 0.0F;

    private final SpriteSet sprites;

    public NukePuffParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites, float size) {
        super(level, x, y, z, dx, dy, dz);
        this.xd = dx;
        this.yd = dy;
        this.zd = dz;
        this.setSize(0.5F, 0.5F);
        this.quadSize = (0.8F + level.random.nextFloat() * 0.3F) * size;
        this.lifetime = 15 + level.random.nextInt(10);
        this.friction = 0.96F;
        float tint = level.random.nextFloat() * 0.05F;
        setColor(Math.min(RED + tint, 1.0F), Math.min(GREEN + tint, 1.0F), Math.min(BLUE + tint, 1.0F));
        this.sprites = sprites;
        setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        setSpriteFromAge(this.sprites);
        if (this.age++ >= this.lifetime) {
            remove();
            return;
        }
        this.rCol *= 0.95F;
        this.gCol *= 0.95F;
        this.bCol *= 0.95F;
        move(this.xd, this.yd, this.zd);
        this.xd *= this.friction;
        this.yd *= this.friction;
        this.zd *= this.friction;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
    }

    @Override
    public int getLightColor(float partialTick) {
        return 240;
    }

    @Override
    public boolean shouldCull() {
        return false;
    }
}
