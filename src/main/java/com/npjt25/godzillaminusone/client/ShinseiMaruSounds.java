package com.npjt25.godzillaminusone.client;

import com.npjt25.godzillaminusone.GodzillaMinusOne;
import com.npjt25.godzillaminusone.entity.ShinseiMaruEntity;
import com.npjt25.godzillaminusone.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/**
 * The Shinsei Maru's motor, client side: while she's under way her diesel chugs (shinsei_maru.motor, a 2 s loop made by
 * tools/make_sounds.py), quietly, a little louder and higher the faster she goes; it fades in as she gets going and
 * out as she stops, then stops altogether. One loop per boat, started once (not every tick).
 */
@Mod.EventBusSubscriber(modid = GodzillaMinusOne.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ShinseiMaruSounds {
    /** Moving at least this fast (blocks a tick) she's under way. */
    private static final double UNDER_WAY = 0.01;
    /** Boats further off than this aren't heard (the loop fades out by then anyway). */
    private static final double HEARD_WITHIN = 48.0;
    private static final Map<Integer, Motor> MOTORS = new HashMap<>();

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        MOTORS.values().removeIf(Motor::isStopped);
        if (mc.level == null || mc.player == null) {
            MOTORS.clear();
            return;
        }
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof ShinseiMaruEntity boat && !MOTORS.containsKey(boat.getId()) && speed(boat) > UNDER_WAY
                    && boat.distanceToSqr(mc.player) < HEARD_WITHIN * HEARD_WITHIN) {
                Motor motor = new Motor(boat);
                MOTORS.put(boat.getId(), motor);
                mc.getSoundManager().play(motor);
            }
        }
    }

    /** How fast she's going (blocks a tick, over the ground), as this client sees her move. */
    static double speed(ShinseiMaruEntity boat) {
        double dx = boat.getX() - boat.xo;
        double dz = boat.getZ() - boat.zo;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Her motor's loop: follows her, its volume and pitch following her speed; fades out and stops when she does. */
    static final class Motor extends AbstractTickableSoundInstance {
        private static final float MAX_VOLUME = 0.35F;
        private final ShinseiMaruEntity boat;
        private float level;
        private double smoothed;

        Motor(ShinseiMaruEntity boat) {
            super(ModSounds.SHINSEI_MARU_MOTOR.get(), SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
            this.boat = boat;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.0F;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
            follow();
        }

        private void follow() {
            this.x = this.boat.getX();
            this.y = this.boat.getY() + 1.5;
            this.z = this.boat.getZ();
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            if (this.boat.isRemoved()) {
                stop();
                return;
            }
            follow();
            this.smoothed += (speed(this.boat) - this.smoothed) * 0.15;
            double fraction = Mth.clamp(this.smoothed / ShinseiMaruEntity.MAX_SPEED, 0.0, 1.0);
            boolean running = this.smoothed > UNDER_WAY * 0.5;
            // fade in over ~1 s, out over ~1.5 s
            this.level = running ? Math.min(1.0F, this.level + 0.05F) : Math.max(0.0F, this.level - 0.035F);
            if (this.level <= 0.0F && !running) {
                stop();
                return;
            }
            this.volume = this.level * MAX_VOLUME * (float) (0.45 + 0.55 * fraction);
            this.pitch = (float) (0.8 + 0.4 * fraction);
        }
    }

    private ShinseiMaruSounds() {
    }
}
