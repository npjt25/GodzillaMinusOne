package com.npjt25.godzillaminusone.client;

import com.npjt25.godzillaminusone.ClientConfig;
import com.npjt25.godzillaminusone.entity.KingGhidorahEntity;
import com.npjt25.godzillaminusone.network.CameraPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * King Ghidorah's effects, on each client, by the server's clock ({@link KingGhidorahEntity#getActionTicks}): a player
 * who comes in halfway through his entrance sees it from there. Nothing here touches the world: the ground he rises
 * through only seems to break (its debris is the ground's own blocks, taken from where each piece flies up).
 *
 * <ol>
 *   <li>0-3 s: cracks spread out across the ground from where he'll rise, dust spurting from them, the ground shaking;
 *       bursts of dirt, dust and rock;</li>
 *   <li>3-8 s: earth fountains up where his heads break through;</li>
 *   <li>8-10.6 s: as his body hauls itself out, lurch by lurch, heavier bursts of debris, dense clouds of dust, a jolt
 *       at each lurch;</li>
 *   <li>10.8-12.4 s: his wings spread: a rush of dust outwards along the ground, loose earth falling from him;</li>
 *   <li>12.6 s: the roar.</li>
 * </ol>
 * The camera shakes as far as ghidorahEntranceShake says (0: not at all).
 */
public final class GhidorahEffects {
    private static final int CRACKS = 9;
    /** Ticks into the entrance the ground jolts, and how hard (before ghidorahEntranceShake), and for how long (s). */
    private static final int[] SHAKE_AT = {0, 52, 62, 112, 124, 160, 174, 179, 193, 197, 212, 216, 250};
    private static final float[] SHAKE_HOW = {0.5F, 0.8F, 0.9F, 0.7F, 0.7F, 1.1F, 1.5F, 1.1F, 1.5F, 1.1F, 1.7F, 1.0F, 1.2F};
    private static final float[] SHAKE_FOR = {4.0F, 1.2F, 1.5F, 1.2F, 1.2F, 1.0F, 1.2F, 1.0F, 1.2F, 1.0F, 1.4F, 2.0F, 2.5F};
    /** Bursts of earth and rock: tick, and how big. */
    private static final int[] BURSTS = {18, 36, 52, 62, 112, 124, 160, 174, 179, 193, 197, 212};
    private static final float[] BURST_SIZE = {0.3F, 0.4F, 0.7F, 0.8F, 0.7F, 0.7F, 1.0F, 1.3F, 1.0F, 1.3F, 1.0F, 1.5F};

    public static void tick(KingGhidorahEntity g) {
        if (!(g.level() instanceof ClientLevel level)) {
            return;
        }
        int action = g.getAction();
        if (action == KingGhidorahEntity.ACTION_ENTRANCE) {
            entrance(level, g, g.getActionTicks());
        } else if (action == KingGhidorahEntity.ACTION_BEAM || action == KingGhidorahEntity.ACTION_LIFT) {
            beams(level, g, action == KingGhidorahEntity.ACTION_BEAM);
        }
    }

    // ------------------------------------------------------------------ his entrance

    private static void entrance(ClientLevel level, KingGhidorahEntity g, int t) {
        if (t < 0 || t > KingGhidorahEntity.ENTRANCE_TICKS) {
            return;
        }
        RandomSource r = g.getRandom();
        Vec3 c = g.position();
        Vec3 fwd = Vec3.directionFromRotation(0.0F, g.getYRot());
        Vec3 right = new Vec3(-fwd.z, 0.0, fwd.x);
        double u = KingGhidorahEntity.UNIT;
        // the cracks: spreading out from where he'll rise, dust and grit spurting from them
        if (t >= 6 && t < 240) {
            double reach = Math.min(36.0, (t - 6) * 0.6);
            for (int i = 0; i < CRACKS; i++) {
                double a = i * Mth.TWO_PI / CRACKS + Math.sin(g.getId() * 7.31 + i * 2.17) * 0.35;
                for (int k = 0; k < 2; k++) {
                    double s = r.nextDouble() * reach;
                    double zig = Math.sin(s * 0.7 + i * 1.9) * 1.4;
                    double x = c.x + Math.cos(a) * s - Math.sin(a) * zig;
                    double z = c.z + Math.sin(a) * s + Math.cos(a) * zig;
                    double y = surface(level, x, z);
                    add(level, block(level, x, z), x, y + 0.1, z, 0.0, 0.05 + r.nextDouble() * 0.25, 0.0);
                }
                if (r.nextInt(3) == 0) {
                    double x = c.x + Math.cos(a) * reach;
                    double z = c.z + Math.sin(a) * reach;
                    add(level, ParticleTypes.POOF, x, surface(level, x, z) + 0.3, z, 0.0, 0.08, 0.0);
                }
                // dust plumes rising from along the crack (what shows from far off: the lines of it spreading out)
                if ((t + i) % 2 == 0) {
                    double s = r.nextDouble() * reach;
                    double zig = Math.sin(s * 0.7 + i * 1.9) * 1.4;
                    double x = c.x + Math.cos(a) * s - Math.sin(a) * zig;
                    double z = c.z + Math.sin(a) * s + Math.cos(a) * zig;
                    add(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, x, surface(level, x, z) + 0.2, z, 0.0, 0.03 + r.nextDouble() * 0.04, 0.0);
                }
            }
        }
        // bursts of dirt, dust and rock
        for (int i = 0; i < BURSTS.length; i++) {
            if (t == BURSTS[i]) {
                burst(level, r, c.add(fwd.scale(t < 150 ? 25.0 : 0.0)), 14.0 + 10.0 * BURST_SIZE[i], (int) (90 * BURST_SIZE[i]), BURST_SIZE[i]);
            }
        }
        // earth fountaining up where his heads break through (the first, then the other two)
        if (t >= 58 && t < 170) {
            Vec3 heads = c.add(fwd.scale(36.0 * u));
            double spread = t < 108 ? 8.0 : 18.0;
            Vec3 where = t < 108 ? heads.add(right.scale(-19.0 * u)) : heads;
            for (int k = 0; k < 6; k++) {
                double x = where.x + r.nextGaussian() * spread;
                double z = where.z + r.nextGaussian() * spread;
                add(level, block(level, x, z), x, surface(level, x, z) + 0.5, z, r.nextGaussian() * 0.2, 0.6 + r.nextDouble() * 0.9, r.nextGaussian() * 0.2);
            }
            if (t % 2 == 0) {
                double x = where.x + r.nextGaussian() * spread;
                double z = where.z + r.nextGaussian() * spread;
                add(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, x, surface(level, x, z) + 1.0, z, 0.0, 0.06, 0.0);
            }
        }
        // his body hauling itself out: heavy debris all round, dense dust
        if (t >= 160 && t < 225) {
            for (int k = 0; k < 10; k++) {
                double a = r.nextDouble() * Mth.TWO_PI;
                double d = 6.0 + r.nextDouble() * 24.0;
                double x = c.x + Math.cos(a) * d;
                double z = c.z + Math.sin(a) * d;
                add(level, block(level, x, z), x, surface(level, x, z) + 0.5, z, Math.cos(a) * 0.4, 0.5 + r.nextDouble() * 1.2, Math.sin(a) * 0.4);
            }
            for (int k = 0; k < 4; k++) {
                double a = r.nextDouble() * Mth.TWO_PI;
                double d = 8.0 + r.nextDouble() * 28.0;
                double x = c.x + Math.cos(a) * d;
                double z = c.z + Math.sin(a) * d;
                add(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, x, surface(level, x, z) + 1.0, z, Math.cos(a) * 0.05, 0.05, Math.sin(a) * 0.05);
            }
        }
        // his wings spreading: dust rushing outwards along the ground, earth falling from him
        if (t >= 216 && t < 228) {
            for (int k = 0; k < 24; k++) {
                double a = r.nextDouble() * Mth.TWO_PI;
                double d = 12.0 + r.nextDouble() * 6.0;
                double x = c.x + Math.cos(a) * d;
                double z = c.z + Math.sin(a) * d;
                double speed = 1.2 + r.nextDouble() * 1.0;
                add(level, ParticleTypes.CLOUD, x, surface(level, x, z) + 0.8, z, Math.cos(a) * speed, 0.02, Math.sin(a) * speed);
            }
        }
        if (t >= 200 && t < 275) {
            for (int k = 0; k < 8; k++) {
                double x = c.x + r.nextGaussian() * 30.0;
                double z = c.z + r.nextGaussian() * 30.0;
                double y = c.y + 20.0 + r.nextDouble() * 80.0;
                BlockState ground = groundState(level, x, z);
                add(level, new BlockParticleOption(ParticleTypes.FALLING_DUST, ground), x, y, z, 0.0, 0.0, 0.0);
                if (k < 3) {
                    add(level, new BlockParticleOption(ParticleTypes.BLOCK, ground), x, y, z, 0.0, -0.4, 0.0);
                }
            }
        }
        // the ground shaking (ghidorahEntranceShake), for whoever's near enough to feel it
        for (int i = 0; i < SHAKE_AT.length; i++) {
            if (t == SHAKE_AT[i]) {
                shake(g, SHAKE_HOW[i], SHAKE_FOR[i]);
            }
        }
    }

    private static void burst(ClientLevel level, RandomSource r, Vec3 at, double radius, int n, float size) {
        for (int k = 0; k < n; k++) {
            double a = r.nextDouble() * Mth.TWO_PI;
            double d = r.nextDouble() * radius;
            double x = at.x + Math.cos(a) * d;
            double z = at.z + Math.sin(a) * d;
            double y = surface(level, x, z) + 0.5;
            add(level, block(level, x, z), x, y, z, Math.cos(a) * 0.5 * size, (0.6 + r.nextDouble() * 1.4) * size, Math.sin(a) * 0.5 * size);
            if (k % 6 == 0) {
                add(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y + 0.5, z, 0.0, 0.07, 0.0);
            }
            if (k % 15 == 0) {
                add(level, ParticleTypes.EXPLOSION, x, y + 1.0, z, 0.0, 0.0, 0.0);
            }
        }
    }

    private static void shake(KingGhidorahEntity g, float intensity, float seconds) {
        float scale = ClientConfig.GHIDORAH_SHAKE.get() / 100.0F;
        var player = Minecraft.getInstance().player;
        if (scale <= 0.0F || player == null) {
            return;
        }
        double dist = player.position().distanceTo(g.position());
        if (dist > 220.0) {
            return;
        }
        float falloff = (float) (1.0 - 0.7 * dist / 220.0);
        ClientFx.handle(CameraPacket.shake(intensity * scale * falloff, seconds));
    }

    // ------------------------------------------------------------------ his beams

    /** Sparks and smoke where his beams strike (a flash and blasts too where they vaporize), and crackling along them. */
    private static void beams(ClientLevel level, KingGhidorahEntity g, boolean vaporizing) {
        RandomSource r = g.getRandom();
        Vec3 aim = g.getAim();
        for (KingGhidorahEntity.Head head : KingGhidorahEntity.Head.values()) {
            if (!g.isFiring(head)) {
                continue;
            }
            Vec3 drawn = g.drawnMouths[head.ordinal()];
            Vec3 from = drawn != null ? g.position().add(drawn) : g.beamMouth(head);
            Vec3 end = g.beamEnd(head, from, aim);
            for (int i = 0; i < (vaporizing ? 10 : 4); i++) {
                add(level, ParticleTypes.ELECTRIC_SPARK, end.x + r.nextGaussian() * 2.0, end.y + r.nextDouble() * 3.0, end.z + r.nextGaussian() * 2.0,
                        r.nextGaussian() * 0.8, 0.4 + r.nextDouble() * 1.0, r.nextGaussian() * 0.8);
            }
            if (vaporizing) {
                if (g.tickCount % 3 == 0) {
                    add(level, ParticleTypes.EXPLOSION_EMITTER, end.x, end.y, end.z, 0.0, 0.0, 0.0);
                }
                for (int i = 0; i < 3; i++) {
                    double a = r.nextDouble() * Mth.TWO_PI;
                    double d = r.nextDouble() * KingGhidorahEntity.VAPORIZE_RADIUS;
                    add(level, ParticleTypes.LARGE_SMOKE, end.x + Math.cos(a) * d, end.y + r.nextGaussian() * 4.0, end.z + Math.sin(a) * d, 0.0, 0.2, 0.0);
                }
            }
            for (int i = 0; i < 2; i++) {
                Vec3 along = from.lerp(end, r.nextDouble());
                add(level, ParticleTypes.ELECTRIC_SPARK, along.x, along.y, along.z, r.nextGaussian() * 0.3, r.nextGaussian() * 0.3, r.nextGaussian() * 0.3);
            }
        }
    }

    // ------------------------------------------------------------------ the ground

    private static double surface(ClientLevel level, double x, double z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
    }

    /** The block the ground is made of there (what its debris looks like); dirt where there's none to be had. */
    private static BlockState groundState(ClientLevel level, double x, double z) {
        int y = (int) surface(level, x, z) - 1;
        BlockState state = level.getBlockState(BlockPos.containing(x, y, z));
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return Blocks.DIRT.defaultBlockState();
        }
        return state;
    }

    private static ParticleOptions block(ClientLevel level, double x, double z) {
        return new BlockParticleOption(ParticleTypes.BLOCK, groundState(level, x, z));
    }

    /** (seen from any distance: he's 90 blocks tall, and so is what he throws up) */
    private static void add(ClientLevel level, ParticleOptions particle, double x, double y, double z, double dx, double dy, double dz) {
        level.addAlwaysVisibleParticle(particle, true, x, y, z, dx, dy, dz);
    }

    private GhidorahEffects() {
    }
}
