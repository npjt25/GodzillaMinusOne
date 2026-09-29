package com.npjt25.godzillaminusone.client.particle;

import com.npjt25.godzillaminusone.client.ClientFx;
import com.npjt25.godzillaminusone.entity.GodzillaEntity;
import com.npjt25.godzillaminusone.entity.ShindenEntity;
import com.npjt25.godzillaminusone.registry.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Java versions of the add-on's Bedrock particle effects (RP/particles/*.json). Sizes, lifetimes and colour
 * gradients follow the Bedrock files; emitter counts are trimmed where the originals would spawn tens of
 * thousands of particles.
 */
public final class Fx {
    static final Map<SimpleParticleType, SpriteSet> SPRITES = new HashMap<>();

    public static void registerSprites(SimpleParticleType type, SpriteSet sprites) {
        SPRITES.put(type, sprites);
    }

    public static Builder of(RegistryObject<SimpleParticleType> type) {
        return new Builder(type.get());
    }

    /** Plain particle for /particle. */
    @Nullable
    public static Particle basic(SimpleParticleType type, ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
        SpriteSet sprites = SPRITES.get(type);
        if (sprites == null) {
            return null;
        }
        Builder builder = new Builder(type).at(x, y, z).velocity(dx, dy, dz).life(40).size(1.0F, 1.5F)
                .colors(0.0F, 0xFFFFFFFF, 1.0F, 0x00FFFFFF);
        return new FxParticle(level, builder, sprites);
    }

    private static final RandomSource RANDOM = RandomSource.create();

    private static double rand(double min, double max) {
        return min + RANDOM.nextDouble() * (max - min);
    }

    // ------------------------------------------------------------------ Godzilla / Shinden keyframes

    /** Handles particle keyframes from the GeckoLib animations (effect names set by tools/convert_addon.py). */
    public static void keyframe(Entity entity, String effect, String locator) {
        if (entity instanceof GodzillaEntity godzilla) {
            Vec3 pos = godzillaLocator(godzilla, locator);
            switch (effect) {
                case "step_dust" -> dust(pos, 12, 1.0F);
                case "step_ring" -> groundRing(pos, 26.0F, 10, 0xA3FFFFFF);
                case "stomp_ring" -> groundRing(pos, 40.0F, 14, 0xA3FFFFFF);
                case "stomp_dust" -> dust(pos, 30, 1.8F);
                case "roar_ring" -> roarRings(pos);
                case "beam_smoke" -> beamSmoke(godzilla, pos);
                case "splash" -> {
                    if (locator.equals("y")) {
                        // animation.gojira.swim_bite: the jaws slam into the water
                        splash(godzilla.locator(0.0, GodzillaEntity.SWIM_SINK, GodzillaEntity.SWIM_BITE_REACH), 0.0, 6.0, 40);
                    } else {
                        splash(godzilla.isSwimmingAtSurface() ? godzilla.position().add(0.0, GodzillaEntity.SWIM_SINK, 0.0)
                                : godzilla.position().add(0.0, godzilla.getFluidHeight(FluidTags.WATER), 0.0), 5.0, 10.0, 24);
                    }
                }
                default -> {
                }
            }
        } else if (entity instanceof ShindenEntity shinden && effect.equals("gun_flash")) {
            gunFlash(shinden, locator.equals("locator2") ? 0.69 : -0.69);
        }
    }

    private static Vec3 godzillaLocator(GodzillaEntity g, String locator) {
        return switch (locator) {
            case "st" -> g.locator(GodzillaEntity.FOOT_SIDE, 0.4, GodzillaEntity.FOOT_FORWARD);
            case "st2" -> g.locator(-GodzillaEntity.FOOT_SIDE, 0.4, GodzillaEntity.FOOT_FORWARD);
            case "y" -> g.locator(0.0, 35.0, 14.3);
            case "locator2" -> g.locator(2.5, 30.0, 0.0);
            case "locator3" -> g.locator(-2.5, 30.0, 0.0);
            default -> g.locator(0.0, 36.0, 8.0);
        };
    }

    /** gojira:step_shockwave: brown dust kicked out along the ground. */
    public static void dust(Vec3 pos, int count, float scale) {
        for (int i = 0; i < count; i++) {
            double angle = rand(0, Math.PI * 2);
            double speed = rand(0.3, 1.6) * scale;
            of(ModParticles.SMOKE).at(pos.add(0, 0.5, 0)).velocity(Math.cos(angle) * speed, rand(0.02, 0.15), Math.sin(angle) * speed)
                    .life(100 + RANDOM.nextInt(60)).size(2.5F * scale, 5.0F * scale).friction(0.88F).spin((float) rand(-0.05, 0.05))
                    .colors(0.0F, 0xC27D5333, 0.28F, 0xFF7F4827, 0.42F, 0x4AAB9769, 0.6F, 0x00F2F2F2).spawn();
        }
    }

    /** goji:mine_shockwave / gojira:shockwave_ground: a flat ring racing across the ground. */
    public static void groundRing(Vec3 pos, float size, int life, int color) {
        of(ModParticles.SHOCKWAVE).at(pos.add(0, 0.3, 0)).life(life).size(1.0F, size).flat()
                .colors(0.0F, color, 0.15F, color, 1.0F, color & 0x00FFFFFF).spawn();
    }

    /** goji:shockwave_range2: rings blasting out of his mouth while he roars (4 a second for 3 seconds). */
    public static void roarRings(Vec3 pos) {
        ClientFx.addEmitter(60, tick -> {
            if (tick % 5 == 0) {
                of(ModParticles.SHOCKWAVE).at(pos).life(10).size(1.0F, 26.0F).additive()
                        .colors(0.0F, 0xA3FFFFFF, 0.15F, 0xA3FFFFFF, 1.0F, 0x00FFFFFF).spawn();
            }
        });
    }

    /** gojira:ray_smoke: smoke pouring off his back as the atomic breath fires, plus blue sparks at the mouth. */
    private static void beamSmoke(GodzillaEntity g, Vec3 pos) {
        Vec3 back = Vec3.directionFromRotation(0.0F, g.yBodyRot).scale(-1.0);
        ClientFx.addEmitter(100, tick -> {
            of(ModParticles.SMOKE).at(pos.add(rand(-1, 1), 5 + rand(-1, 1), rand(-1, 1)))
                    .velocity(back.x * 0.8 + rand(-0.2, 0.2), rand(0.0, 0.3), back.z * 0.8 + rand(-0.2, 0.2))
                    .life(100 + RANDOM.nextInt(40)).size(6.0F, 10.0F).friction(0.95F)
                    .colors(0.0F, 0xC0463921, 0.47F, 0xA0291B10, 0.93F, 0x212A2A2A, 1.0F, 0x002A2A2A).spawn();
        });
        Vec3 mouth = g.beamLine(g.getAimPitch(1.0F))[0];
        for (int i = 0; i < 40; i++) {
            of(ModParticles.BLUE_FLAME).at(mouth).velocity(rand(-0.6, 0.6), rand(-0.6, 0.6), rand(-0.6, 0.6))
                    .life(10 + RANDOM.nextInt(15)).size(1.2F, 0.2F).additive()
                    .colors(0.0F, 0xFFFFFFFF, 0.3F, 0xFF66CCFF, 1.0F, 0x003399FF).spawn();
        }
    }

    private static void splash(Vec3 center, double minRadius, double maxRadius, int count) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        for (int i = 0; i < count; i++) {
            double angle = rand(0, Math.PI * 2);
            double r = rand(minRadius, maxRadius);
            double x = center.x + Math.cos(angle) * r;
            double z = center.z + Math.sin(angle) * r;
            level.addAlwaysVisibleParticle(ParticleTypes.SPLASH, true, x, center.y, z, 0.0, 0.3, 0.0);
            level.addAlwaysVisibleParticle(ParticleTypes.CLOUD, true, x, center.y + 0.5, z, rand(-0.05, 0.05), 0.08, rand(-0.05, 0.05));
        }
    }

    /** goji:gun_light + goji:gun_spark at the Shinden's cannon muzzles. */
    private static void gunFlash(ShindenEntity shinden, double side) {
        Vec3 fwd = Vec3.directionFromRotation(shinden.getXRot(), shinden.getYRot());
        Vec3 right = new Vec3(-fwd.z, 0.0, fwd.x).normalize();
        Vec3 muzzle = shinden.position().add(0.0, 2.76, 0.0).add(fwd.scale(2.6)).add(right.scale(side));
        of(ModParticles.GUNLIGHT).at(muzzle).life(3).size(0.7F, 0.4F).additive().spin(0.9F)
                .colors(0.0F, 0xFFFFD448, 1.0F, 0x66FFFFFF).spawn();
        for (int i = 0; i < 3; i++) {
            Vec3 v = fwd.scale(4.0).add(rand(-0.08, 0.08), rand(-0.08, 0.08), rand(-0.08, 0.08));
            of(ModParticles.GLOW).at(muzzle).velocity(v.x, v.y, v.z).life(10).size(0.18F, 0.12F).additive().friction(1.0F)
                    .colors(0.0F, 0xFFFFFFFF, 0.6F, 0xFFF2A625, 1.0F, 0x00F2A625).spawn();
        }
    }

    // ------------------------------------------------------------------ explosions

    /** animation.gojira.mine: goji:mind_explosion fireball + goji:mine_shockwave ring. */
    public static void mineBlast(Vec3 pos) {
        for (int i = 0; i < 100; i++) {
            Vec3 dir = new Vec3(rand(-1, 1), rand(-1, 1), rand(-1, 1)).normalize();
            Vec3 start = pos.add(0, 9, 0).add(dir.scale(rand(0, 19)));
            Vec3 v = dir.scale(rand(0.3, 0.5));
            of(ModParticles.SMOKE).at(start).velocity(v.x, v.y, v.z).life(20 + RANDOM.nextInt(20)).size(10.0F, 9.5F)
                    .friction(0.9F).gravity(0.025F).spin((float) rand(-0.3, 0.3))
                    .colors(0.0F, 0x00F2C963, 0.14F, 0xFFFFBB00, 0.36F, 0xFF9A1818, 0.5F, 0xFF656363, 0.67F, 0xFF383838, 0.99F, 0x29000000)
                    .fullBright().spawn();
        }
        for (int i = 0; i < 6; i++) {
            of(ModParticles.GLOW).at(pos.add(rand(-4, 4), rand(2, 10), rand(-4, 4))).life(14).size(8.0F, 18.0F).additive()
                    .colors(0.0F, 0xFFFFFFFF, 0.3F, 0xFFFFAA33, 1.0F, 0x00FF5500).spawn();
        }
        ClientFx.addEmitter(32, tick -> {
            if (tick % 8 == 0) {
                groundRing(pos, 26.0F, 10, 0xA3FFFFFF);
            }
        });
    }

    /** Stomps, tail slams, collapsing: a dust ring. */
    public static void stomp(Vec3 pos, float size) {
        groundRing(pos, 30.0F * size, 14, 0xA3FFFFFF);
        dust(pos, (int) (20 * size), size);
    }

    /**
     * The atomic breath impact, around the 3D fireball and mushroom cloud (client.render.NuclearCloud): the flash,
     * flame streaks, and on the ground the racing smoke shockwave (gojira:nuclear_shockwave, shockwave_ground).
     */
    public static void atomicBlast(Vec3 pos, boolean grounded) {
        // Flash.
        of(ModParticles.GLOW).at(pos.add(0, 10, 0)).life(8).size(120.0F, 260.0F).additive()
                .colors(0.0F, 0xFFFFFFFF, 0.4F, 0xFFFFF0C0, 1.0F, 0x00FFAA40).spawn();
        // Flame streaks.
        for (int i = 0; i < 80; i++) {
            Vec3 dir = new Vec3(rand(-1, 1), rand(grounded ? -0.2 : -1, 1), rand(-1, 1)).normalize();
            of(ModParticles.GLOW).at(pos.add(0, 1, 0).add(dir.scale(rand(5, 20)))).velocity(dir.x * 0.6, dir.y * 0.6, dir.z * 0.6)
                    .life(20 + RANDOM.nextInt(60)).size((float) rand(3, 12), 1.0F).additive().spin((float) rand(-0.5, 0.5))
                    .colors(0.0F, 0x00FFFFFF, 0.23F, 0xFFFFFFFF, 0.8F, 0xFFFFA030, 1.0F, 0x00FFFFFF).spawn();
        }
        if (!grounded) {
            return;
        }
        // Racing smoke shockwave.
        for (int i = 0; i < 160; i++) {
            double angle = rand(0, Math.PI * 2);
            double speed = rand(3.0, 4.5);
            of(ModParticles.SMOKE).at(pos.add(0, 2, 0)).velocity(Math.cos(angle) * speed, rand(0, 0.1), Math.sin(angle) * speed)
                    .life(160 + RANDOM.nextInt(60)).size(16.0F, 40.0F).friction(0.965F).spin((float) rand(-0.1, 0.1))
                    .colors(0.0F, 0xC29E9E9E, 0.28F, 0xFF4A4038, 0.6F, 0x00F2F2F2).spawn();
        }
        // Ground shockwave rings.
        groundRing(pos, 320.0F, 60, 0xA3FFFFFF);
        groundRing(pos, 160.0F, 40, 0xA3848080);
        // Debris and embers thrown out of the crater.
        for (int i = 0; i < 60; i++) {
            Vec3 dir = new Vec3(rand(-1, 1), rand(0.6, 1.6), rand(-1, 1)).normalize();
            double speed = rand(1.0, 3.0);
            of(ModParticles.SMOKE).at(pos.add(0, 2, 0)).velocity(dir.x * speed, dir.y * speed, dir.z * speed)
                    .life(60 + RANDOM.nextInt(40)).size(2.0F, 4.0F).gravity(0.08F).friction(0.98F).collide()
                    .colors(0.0F, 0xFF3A3028, 0.7F, 0xFF2A2420, 1.0F, 0x002A2420).spawn();
        }
    }

    /** gojira:black_rain around the camera while it's inside the fallout area. */
    public static void blackRainTick(Vec3 camera) {
        for (int i = 0; i < 30; i++) {
            double x = camera.x + rand(-24, 24);
            double z = camera.z + rand(-24, 24);
            double y = camera.y + rand(8, 22);
            of(ModParticles.BLACK_RAIN).at(x, y, z).velocity(0.1 + rand(-0.03, 0.03), -rand(0.8, 1.2), rand(-0.03, 0.03))
                    .life(40).size(0.08F, 0.08F).aspect(5.0F).vertical().gravity(0.2F).friction(1.0F).collide()
                    .colors(0.0F, 0xE0303845, 1.0F, 0xE0303845).spawn();
        }
    }

    // ------------------------------------------------------------------ builder

    public static final class Builder {
        final SimpleParticleType type;
        double x, y, z, vx, vy, vz;
        int lifetime = 20;
        float startSize = 1.0F, endSize = 1.0F, gravity = 0.0F, friction = 0.98F, spin = 0.0F, roll = 0.0F, aspect = 1.0F;
        boolean additive, fullBright, collide;
        FxParticle.Facing facing = FxParticle.Facing.CAMERA;
        float[] stops = {0.0F};
        int[] colors = {0xFFFFFFFF};

        Builder(SimpleParticleType type) {
            this.type = type;
            this.roll = (float) rand(0, Mth.TWO_PI);
        }

        public Builder at(Vec3 pos) {
            return at(pos.x, pos.y, pos.z);
        }

        public Builder at(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
            return this;
        }

        public Builder velocity(double vx, double vy, double vz) {
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            return this;
        }

        public Builder life(int ticks) {
            this.lifetime = ticks;
            return this;
        }

        public Builder size(float start, float end) {
            this.startSize = start;
            this.endSize = end;
            return this;
        }

        public Builder gravity(float gravity) {
            this.gravity = gravity;
            return this;
        }

        public Builder friction(float friction) {
            this.friction = friction;
            return this;
        }

        public Builder spin(float spin) {
            this.spin = spin;
            return this;
        }

        public Builder aspect(float aspect) {
            this.aspect = aspect;
            return this;
        }

        public Builder additive() {
            this.additive = true;
            return this;
        }

        public Builder fullBright() {
            this.fullBright = true;
            return this;
        }

        public Builder collide() {
            this.collide = true;
            return this;
        }

        public Builder flat() {
            this.facing = FxParticle.Facing.FLAT;
            return this;
        }

        public Builder vertical() {
            this.facing = FxParticle.Facing.VERTICAL;
            this.roll = 0.0F;
            return this;
        }

        /** Alternating (stop, ARGB) pairs, like a Bedrock colour gradient. */
        public Builder colors(Object... stopsAndColors) {
            int n = stopsAndColors.length / 2;
            this.stops = new float[n];
            this.colors = new int[n];
            for (int i = 0; i < n; i++) {
                this.stops[i] = ((Number) stopsAndColors[i * 2]).floatValue();
                this.colors[i] = ((Number) stopsAndColors[i * 2 + 1]).intValue();
            }
            return this;
        }

        public void spawn() {
            Minecraft mc = Minecraft.getInstance();
            SpriteSet sprites = SPRITES.get(this.type);
            if (mc.level == null || sprites == null) {
                return;
            }
            mc.particleEngine.add(new FxParticle(mc.level, this, sprites));
        }
    }

    private Fx() {
    }
}
