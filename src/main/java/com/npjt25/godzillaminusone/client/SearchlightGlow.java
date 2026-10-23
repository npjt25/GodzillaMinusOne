package com.npjt25.godzillaminusone.client;

import com.npjt25.godzillaminusone.GodzillaMinusOne;
import com.npjt25.godzillaminusone.entity.SearchlightEntity;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Searchlight beams light up the mobs they shine on, wherever along the beam they are: a mob (or player) inside the
 * cone of a lit searchlight, short of where the beam lands and with nothing solid between it and the lens, is drawn
 * lit as by a block light of up to 15 (brightest on the beam's axis and near the lamp, a little less at the edge of
 * the cone and far out), never darker than the world has it. Everything else about how it's drawn is left alone (no
 * glowing outline): it just stands in the light. When the beam moves off it, or the lamp goes off, it goes back to the
 * world's light within a few ticks.
 *
 * <p>Client side only, and only how things are drawn (through mixin.EntityRenderDispatcherMixin, so every renderer's
 * mobs are lit, GeckoLib's included): every client works it out for itself from the searchlights it sees, so everyone
 * sees the same. The pool of light where the beam lands is lit by real light sources (SearchlightEntity), which light
 * whatever is near it as any light does.
 */
@Mod.EventBusSubscriber(modid = GodzillaMinusOne.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class SearchlightGlow {
    /** Searchlights this far from the camera light things up (as far as their beams are drawn). */
    private static final double MAX_DISTANCE = 384.0;
    /** How fast a mob's light comes and goes: light levels a tick. */
    private static final int STEP = 5;
    /** The least a mob in the beam is lit (at the edge of the cone, at the end of its reach). */
    private static final int MIN_LEVEL = 8;
    /** Each lit mob's light from the beams: {last tick, this tick} (light levels), by entity id. */
    private static final Int2ObjectMap<int[]> LIT = new Int2ObjectOpenHashMap<>();
    private static ClientLevel litLevel;

    /**
     * Where a searchlight's lamp points (yaw, pitch) as this client draws it, {@code partialTick} into the tick: for its
     * own operator, where they're looking right now (what the server will make it on its next tick), so the beam keeps
     * up with their view; for everyone else, smoothly between the aims the server last sent.
     */
    public static float[] aim(SearchlightEntity light, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getVehicle() == light) {
            return new float[] {Mth.wrapDegrees(mc.player.getViewYRot(partialTick)),
                    SearchlightEntity.clampPitch(mc.player.getViewXRot(partialTick))};
        }
        return new float[] {light.getAimYaw(partialTick), light.getAimPitch(partialTick)};
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != litLevel) {
            LIT.clear();
            litLevel = mc.level;
        }
        if (mc.level == null || mc.isPaused()) {
            return;
        }
        Int2IntOpenHashMap wanted = new Int2IntOpenHashMap();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        List<LivingEntity> mobs = null;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof SearchlightEntity light && light.isLit() && light.distanceToSqr(cam) < MAX_DISTANCE * MAX_DISTANCE) {
                if (mobs == null) {
                    mobs = mobs(mc.level);
                }
                shine(mc.level, light, mobs, wanted);
            }
        }
        // each lit mob's light eases towards what the beams give it now (none: back to the world's)
        for (Iterator<Int2ObjectMap.Entry<int[]>> it = LIT.int2ObjectEntrySet().iterator(); it.hasNext(); ) {
            Int2ObjectMap.Entry<int[]> entry = it.next();
            int[] lit = entry.getValue();
            lit[0] = lit[1];
            lit[1] = approach(lit[1], wanted.remove(entry.getIntKey()));
            if (lit[0] == 0 && lit[1] == 0) {
                it.remove();
            }
        }
        wanted.int2IntEntrySet().forEach(entry -> LIT.put(entry.getIntKey(), new int[] {0, approach(0, entry.getIntValue())}));
    }

    private static int approach(int from, int to) {
        return from < to ? Math.min(to, from + STEP) : Math.max(to, from - STEP);
    }

    private static List<LivingEntity> mobs(ClientLevel level) {
        List<LivingEntity> mobs = new ArrayList<>();
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof LivingEntity living && living.isAlive() && !living.isSpectator()) {
                mobs.add(living);
            }
        }
        return mobs;
    }

    /** The mobs one searchlight's beam reaches, and how brightly. */
    private static void shine(ClientLevel level, SearchlightEntity light, List<LivingEntity> mobs, Int2IntOpenHashMap wanted) {
        float[] aim = aim(light, 1.0F);
        Vec3 dir = SearchlightEntity.beamDirection(aim[0], aim[1]);
        Vec3 from = light.lens(dir);
        Vec3 end = light.visibleBeamHit(from, dir).getLocation();
        double length = end.distanceTo(from);
        AABB area = new AABB(from, end).inflate(SearchlightEntity.beamRadius(length));
        for (LivingEntity mob : mobs) {
            if (mob.getVehicle() == light || !mob.getBoundingBox().intersects(area)) {
                continue;
            }
            SearchlightEntity.BeamTouch touch = SearchlightEntity.beamTouch(mob.getBoundingBox(), from, dir, length);
            if (touch != null && inSight(level, light, from, mob, touch)) {
                double far = Math.min(1.0, touch.along() / SearchlightEntity.RANGE);
                int lit = (int) Math.round(15.0 - 4.0 * (1.0 - touch.strength()) - 3.0 * far);
                lit = Math.max(MIN_LEVEL, lit);
                if (lit > wanted.get(mob.getId())) {
                    wanted.put(mob.getId(), lit);
                }
            }
        }
    }

    /**
     * Whether the lens can see some of {@code mob}: the part of it nearest the beam's axis, its middle, or its eyes
     * (blocks in the way as the beam itself is stopped by them: what you can see through, glass say, lets it through).
     */
    private static boolean inSight(ClientLevel level, SearchlightEntity light, Vec3 from, LivingEntity mob, SearchlightEntity.BeamTouch touch) {
        Vec3 middle = mob.getBoundingBox().getCenter();
        for (Vec3 p : new Vec3[] {touch.point().add(middle.subtract(touch.point()).scale(0.2)), middle, mob.getEyePosition()}) {
            if (level.clip(new ClipContext(from, p, ClipContext.Block.VISUAL, ClipContext.Fluid.ANY, light)).getType() == HitResult.Type.MISS) {
                return true;
            }
        }
        return false;
    }

    /** The light {@code entity} is drawn in ({@code packedLight}, the world's), with what the beams on it add. */
    public static int brighten(Entity entity, int packedLight, float partialTick) {
        if (LIT.isEmpty()) {
            return packedLight;
        }
        int[] lit = LIT.get(entity.getId());
        if (lit == null) {
            return packedLight;
        }
        int level = Math.round(Mth.lerp(partialTick, lit[0], lit[1]));
        return level > LightTexture.block(packedLight) ? LightTexture.pack(level, LightTexture.sky(packedLight)) : packedLight;
    }

    /** For the in-game checks: the light the beams give {@code entity} now (0: none). */
    public static int glow(Entity entity) {
        int[] lit = LIT.get(entity.getId());
        return lit == null ? 0 : lit[1];
    }

    private SearchlightGlow() {
    }
}
