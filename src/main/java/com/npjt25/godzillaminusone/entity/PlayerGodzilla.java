package com.npjt25.godzillaminusone.entity;

import com.npjt25.godzillaminusone.Config;
import com.npjt25.godzillaminusone.GodzillaMinusOne;
import com.npjt25.godzillaminusone.compat.Shapes;
import com.npjt25.godzillaminusone.network.FxPacket;
import com.npjt25.godzillaminusone.network.GodzillaMovePacket;
import com.npjt25.godzillaminusone.network.ModNetwork;
import com.npjt25.godzillaminusone.registry.ModDamageTypes;
import com.npjt25.godzillaminusone.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A player playing as Godzilla (with Walkers, mod id "walkers", or ReMorphed, "remorphed", both by To_Craft; their
 * API is looked up in compat.Shapes), server side. Walkers gives them his size and
 * draws him in their place; this gives them his moves, on their own keys (client.PlayerGodzillaControls), each with
 * his own animation (seen by everyone) and his own blows, as the mob deals them:
 *
 * <ul>
 *   <li>{@link Move#TAIL_WHIP}: the spin, its tail sweeping round him and then snapping.</li>
 *   <li>{@link Move#SCRATCH}: a claw swipe, right then left: it hurts and flings whatever is in front of him, and tears
 *       through what's built there.</li>
 *   <li>{@link Move#BITE}: his jaws, where he's looking (in the water, his swimming bite).</li>
 *   <li>{@link Move#ROAR}: heard thousands of blocks away; whatever is near cowers (Weakness and Slowness).</li>
 *   <li>{@link Move#ATOMIC_BREATH}: the whole charge as the mob does it (the plates lighting up one by one, the
 *       in-breath; he stands his ground meanwhile), then the beam, where he's looking, and the nuclear blast where it
 *       lands, flattening and all, and his roar as the beam dies out: he can't move from the start of the charge to the
 *       end of that roar. Then the breath needs to recharge (atomicBreathCooldown, 3 minutes).</li>
 *   <li>Walking, he breaks through what's built in his way and crushes footprints (playable section of the config).
 *       Whatever interrupts him (changing back, dying, respawning, changing dimension, logging out) frees him.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = GodzillaMinusOne.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PlayerGodzilla {
    public enum Move {
        TAIL_WHIP("spin_whip", 44), SCRATCH("swipe_right", 22), BITE("bite", 20), ROAR("roar", 60),
        ATOMIC_BREATH(null, GodzillaEntity.CHARGE_LENGTH);

        /** The animation it plays (the scratch alternates with "swipe_left"; the bite is "swim_bite" in the water). */
        @Nullable
        public final String animation;
        /** Ticks before he can make another move. */
        public final int length;

        Move(@Nullable String animation, int length) {
            this.animation = animation;
            this.length = length;
        }
    }

    /** Rooted while he charges his breath (it's what the mob does too). */
    private static final UUID ROOTED_ID = UUID.fromString("5d2f6e64-27a8-4f61-9a34-3c1b5b6c9e21");
    private static final AttributeModifier ROOTED = new AttributeModifier(ROOTED_ID, "Godzilla charging his atomic breath", -1.0,
            AttributeModifier.Operation.MULTIPLY_TOTAL);
    private static final String BREATH_READY = GodzillaMinusOne.MODID + ":BreathReady";
    private static final double BEAM_REACH = 220.0;

    private static final Map<UUID, State> STATES = new HashMap<>();

    /** One player's moves in progress. */
    private static final class State {
        int busyUntil;
        boolean leftSwipe;
        int charge = -1;
        @Nullable
        Vec3 blastAt;
        final List<Pending> pending = new ArrayList<>();
    }

    /** A blow landing a few ticks into its animation. */
    private record Pending(int at, Runnable blow) {
    }

    /** Whether {@code player} is playing as Godzilla. */
    public static boolean isGodzilla(Player player) {
        return Shapes.godzillaOf(player) != null;
    }

    /** Ticks until {@code player}'s atomic breath is ready again. */
    public static int breathCooldown(ServerPlayer player) {
        long ready = player.getPersistentData().getLong(BREATH_READY);
        return (int) Math.max(0L, ready - player.serverLevel().getGameTime());
    }

    /** A key pressed (client.PlayerGodzillaControls): the move, if he's Godzilla and free to make it. */
    public static void use(ServerPlayer player, Move move) {
        if (!player.isAlive() || player.isSpectator() || !isGodzilla(player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        State state = STATES.computeIfAbsent(player.getUUID(), id -> new State());
        if (player.tickCount < state.busyUntil || state.charge >= 0) {
            return;
        }
        String animation = move.animation;
        switch (move) {
            case TAIL_WHIP -> {
                later(state, player, 17, () -> tailSweep(player, 30.0, 0.4F, SoundEvents.PLAYER_ATTACK_SWEEP, 0.7F));
                later(state, player, 26, () -> tailSweep(player, 38.0, 0.35F, SoundEvents.PLAYER_ATTACK_CRIT, 1.0F));
            }
            case SCRATCH -> {
                state.leftSwipe = !state.leftSwipe;
                animation = state.leftSwipe ? "swipe_right" : "swipe_left";
                double side = state.leftSwipe ? 1.0 : -1.0;
                later(state, player, 10, () -> scratch(player, side));
            }
            case BITE -> {
                if (player.isInWater()) {
                    animation = "swim_bite";
                }
                later(state, player, 10, () -> bite(player));
            }
            case ROAR -> roar(player);
            case ATOMIC_BREATH -> {
                int cooldown = breathCooldown(player);
                if (cooldown > 0) {
                    player.displayClientMessage(Component.translatable("message." + GodzillaMinusOne.MODID + ".breath_recharging",
                            cooldown / 1200, String.format("%02d", cooldown / 20 % 60)), true);
                    ModNetwork.send(player, new GodzillaMovePacket(player.getId(), -1, cooldown));
                    return;
                }
                state.charge = 0;
                root(player, true);
            }
        }
        state.busyUntil = player.tickCount + move.length;
        int cooldown = move == Move.ATOMIC_BREATH ? Config.ATOMIC_BREATH_COOLDOWN.get() * 20 + GodzillaEntity.CHARGE_LENGTH : breathCooldown(player);
        if (move == Move.ATOMIC_BREATH) {
            player.getPersistentData().putLong(BREATH_READY, level.getGameTime() + cooldown);
        }
        // everyone who can see him sees the move (and he gets his breath's recharge time)
        GodzillaMovePacket packet = new GodzillaMovePacket(player.getId(), move.ordinal(), cooldown, animation);
        ModNetwork.sendNear(level, player.position(), 512.0, packet);
    }

    private static void later(State state, ServerPlayer player, int delay, Runnable blow) {
        state.pending.add(new Pending(player.tickCount + delay, blow));
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        if (player.isAlive() && isGodzilla(player)) {
            stomp(player);
        }
        State state = STATES.get(player.getUUID());
        if (state == null || state.charge < 0) {
            // never left rooted by a breath that isn't going on (changed back, died, logged out mid-charge...)
            root(player, false);
        }
        if (state == null) {
            return;
        }
        if (!player.isAlive() || !isGodzilla(player)) {
            // (changed back, or died: whatever he was doing stops)
            stop(player, state);
            return;
        }
        for (Iterator<Pending> it = state.pending.iterator(); it.hasNext(); ) {
            Pending pending = it.next();
            if (player.tickCount >= pending.at()) {
                it.remove();
                pending.blow().run();
            }
        }
        if (state.charge >= 0) {
            tickBreath(player, state);
        }
    }

    /** Where each player playing as him was a moment ago, and how many strides he's taken (left foot, right foot). */
    private static final Map<UUID, Vec3> LAST_POSITION = new HashMap<>();
    private static final Map<UUID, Integer> STRIDES = new HashMap<>();

    /**
     * Walking as him, he breaks through whatever he walks into (anything but the ground, as the mob does, if
     * godzillaBreaksBlocks is on and the player may build here) and crushes a footprint where each foot comes down.
     */
    private static void stomp(ServerPlayer player) {
        if (player.tickCount % 4 != 0) {
            return;
        }
        Vec3 now = player.position();
        Vec3 before = LAST_POSITION.put(player.getUUID(), now);
        if (before == null || !mayDestroy(player)) {
            return;
        }
        Vec3 moved = new Vec3(now.x - before.x, 0.0, now.z - before.z);
        if (moved.lengthSqr() < 0.15 * 0.15) {
            return;
        }
        ServerLevel level = player.serverLevel();
        Vec3 dir = moved.normalize();
        AABB box = player.getBoundingBox().expandTowards(dir.x * 3.0, 0.0, dir.z * 3.0).inflate(1.0, 0.0, 1.0);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int broken = 0;
        for (int y = Mth.floor(player.getY()) + 1; y <= Mth.floor(box.maxY) && broken < 300; y++) {
            for (int x = Mth.floor(box.minX); x <= Mth.floor(box.maxX) && broken < 300; x++) {
                for (int z = Mth.floor(box.minZ); z <= Mth.floor(box.maxZ) && broken < 300; z++) {
                    BlockState state = level.getBlockState(pos.set(x, y, z));
                    if (state.isAir() || !state.getFluidState().isEmpty() || Destruction.isGround(state)) {
                        continue; // (hills and cliffs he climbs, as the mob does)
                    }
                    if (Destruction.canBreak(level, pos, state, player)) {
                        level.destroyBlock(pos, false, player);
                        broken++;
                    }
                }
            }
        }
        // every couple of seconds' walking, a foot comes down: a footprint crushed where it lands
        int strides = STRIDES.merge(player.getUUID(), 1, Integer::sum);
        if (strides % 4 == 0 && player.onGround() && Config.PLAYER_GODZILLA_FOOTPRINTS.get() && Config.FOOTPRINTS.get()) {
            double side = (strides / 4) % 2 == 0 ? GodzillaEntity.FOOT_SIDE : -GodzillaEntity.FOOT_SIDE;
            float yaw = player.yBodyRot;
            Vec3 fwd = Vec3.directionFromRotation(0.0F, yaw);
            Vec3 foot = now.add(fwd.scale(GodzillaEntity.FOOT_FORWARD)).add(-fwd.z * side, 0.0, fwd.x * side);
            Destruction.footprint(level, player, foot, yaw, GodzillaEntity.FOOT_HALF_WIDTH, GodzillaEntity.FOOT_HALF_LENGTH);
        }
    }

    /**
     * Whether, playing as him, {@code player} may break blocks: playerGodzillaBreaksBlocks and breaksBlocks on, the
     * mobGriefing gamerule on, and they may build here (not in adventure mode, not in spawn protection).
     */
    static boolean mayDestroy(ServerPlayer player) {
        return Config.PLAYER_GODZILLA_BREAKS_BLOCKS.get() && Config.GODZILLA_BREAKS_BLOCKS.get() && !player.isSpectator()
                && player.mayBuild() && player.serverLevel().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            State state = STATES.get(player.getUUID());
            if (state != null) {
                stop(player, state);
            }
            root(player, false);
        }
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            State state = STATES.get(player.getUUID());
            if (state != null) {
                stop(player, state); // (the breath's beam and blast belong to the world he left)
            }
            LAST_POSITION.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_POSITION.remove(event.getEntity().getUUID());
        STRIDES.remove(event.getEntity().getUUID());
        State state = STATES.get(event.getEntity().getUUID());
        if (state != null && event.getEntity() instanceof ServerPlayer player) {
            stop(player, state);
        }
    }

    private static void stop(ServerPlayer player, State state) {
        root(player, false);
        STATES.remove(player.getUUID());
    }

    // ------------------------------------------------------------------ blows

    /** A point on him: {@code side} blocks to his right, {@code up} above his feet, {@code forward} ahead (his heading). */
    private static Vec3 at(Player player, double side, double up, double forward) {
        Vec3 fwd = Vec3.directionFromRotation(0.0F, player.getYRot());
        Vec3 right = new Vec3(-fwd.z, 0.0, fwd.x);
        return player.position().add(right.scale(side)).add(0.0, up, 0.0).add(fwd.scale(forward));
    }

    private static float damage() {
        return Config.GODZILLA_ATTACK_DAMAGE.get().floatValue();
    }

    /** Hurts what's within {@code radius} of {@code center} (not him), flinging it along {@code push} or away from the middle. */
    private static void hurtAround(ServerPlayer player, Vec3 center, double radius, float amount, @Nullable Vec3 push) {
        ServerLevel level = player.serverLevel();
        DamageSource source = level.damageSources().playerAttack(player);
        for (LivingEntity entity : Blasts.livingNear(level, center, radius, player)) {
            if (entity instanceof GodzillaEntity) {
                continue;
            }
            entity.hurt(source, amount);
            Vec3 away = push != null ? push
                    : entity.position().subtract(center).multiply(1.0, 0.0, 1.0).normalize().scale(2.0).add(0.0, 0.8, 0.0);
            entity.setDeltaMovement(entity.getDeltaMovement().add(away));
            entity.hurtMarked = true;
        }
        for (ShinseiMaruEntity boat : level.getEntitiesOfClass(ShinseiMaruEntity.class, new AABB(center, center).inflate(radius))) {
            boat.hurt(source, 1000.0F);
        }
    }

    private static void tailSweep(ServerPlayer player, double radius, float share, SoundEvent sound, float shake) {
        hurtAround(player, player.position(), radius, damage() * share, null);
        player.level().playSound(null, player.getX(), player.getY() + 4.0, player.getZ(), sound, SoundSource.PLAYERS, 10.0F, 0.35F);
        ModNetwork.shake(player.serverLevel(), player.position(), 64.0, shake, 1.2F);
    }

    private static void scratch(ServerPlayer player, double side) {
        Vec3 claws = at(player, 2.2 * side, 10.0, 16.5);
        Vec3 fwd = Vec3.directionFromRotation(0.0F, player.getYRot());
        Vec3 across = new Vec3(fwd.z, 0.0, -fwd.x).scale(side * 2.2).add(0.0, 0.7, 0.0);
        hurtAround(player, claws, 15.0, damage() * 0.6F, across);
        tearThrough(player, claws, 7.0, across.add(fwd.scale(0.4)).scale(0.3));
        player.level().playSound(null, claws.x, claws.y, claws.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 8.0F, 0.45F);
        ModNetwork.shake(player.serverLevel(), claws, 48.0, 0.4F, 0.8F);
    }

    private static void bite(ServerPlayer player) {
        Vec3 jaws = player.isInWater() ? at(player, 0.0, 1.0, 18.0) : player.getEyePosition().add(player.getLookAngle().scale(14.0));
        hurtAround(player, jaws, 9.0, damage(), Vec3.ZERO);
        tearThrough(player, jaws, 5.0, player.getLookAngle().scale(0.4));
        if (player.isInWater()) {
            player.level().playSound(null, jaws.x, jaws.y, jaws.z, SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 6.0F, 0.5F);
        }
        player.level().playSound(null, jaws.x, jaws.y, jaws.z, ModSounds.GODZILLA_BITE.get(), SoundSource.PLAYERS, 6.0F, 1.0F);
        ModNetwork.shake(player.serverLevel(), jaws, 48.0, 0.4F, 1.0F);
    }

    /** What's built within {@code radius} of {@code center} is torn apart: solid blocks topple, the rest breaks. */
    private static void tearThrough(ServerPlayer player, Vec3 center, double radius, Vec3 push) {
        ServerLevel level = player.serverLevel();
        if (!mayDestroy(player)) {
            return;
        }
        int r = Mth.ceil(radius);
        BlockPos middle = BlockPos.containing(center);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int toppled = 0;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dy * dy + dz * dz > radius * radius) {
                        continue;
                    }
                    pos.set(middle.getX() + dx, middle.getY() + dy, middle.getZ() + dz);
                    BlockState state = level.getBlockState(pos);
                    if (!GodzillaEntity.isBuilt(state) || state.getBlock() instanceof LiquidBlock || !Destruction.canBreak(level, pos, state, player)) {
                        continue;
                    }
                    if (Destruction.topple(level, pos, state, push, toppled < 60)) {
                        toppled++;
                    }
                }
            }
        }
    }

    private static void roar(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        GodzillaEntity.broadcastRoar(level, player.getX(), player.getY() + 30.0, player.getZ(), ModSounds.GODZILLA_ROAR.get(),
                player.getRandom().nextLong());
        for (LivingEntity entity : Blasts.livingNear(level, player.position(), 48.0, player)) {
            if (!(entity instanceof GodzillaEntity)) {
                entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 1), player);
                entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 1), player);
            }
        }
        ModNetwork.shake(level, player.position(), 96.0, 0.6F, 2.5F);
    }

    // ------------------------------------------------------------------ the atomic breath

    private static void root(ServerPlayer player, boolean on) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        if (on && !speed.hasModifier(ROOTED)) {
            speed.addTransientModifier(ROOTED);
        } else if (!on) {
            speed.removeModifier(ROOTED_ID);
        }
    }

    /** The mob's charge timeline (entity.GodzillaEntity): the beam, its blast, the cooling down. */
    private static void tickBreath(ServerPlayer player, State state) {
        int t = ++state.charge;
        ServerLevel level = player.serverLevel();
        if (t >= GodzillaEntity.BEAM_START_TICK && t < GodzillaEntity.BEAM_END_TICK && (t - GodzillaEntity.BEAM_START_TICK) % 4 == 0) {
            Vec3[] beam = beam(player);
            beamHits(player, beam[0], beam[1]);
            if (t == GodzillaEntity.BEAM_START_TICK) {
                ModNetwork.shake(level, player.position(), 160.0, 1.0F, 4.0F);
            }
        }
        if (t == GodzillaEntity.CHARGE_BLAST_TICK) {
            blast(player, beam(player)[1]);
        }
        if (t == GodzillaEntity.BEAM_END_TICK) {
            // the roar as the beam dies out, as the mob's: he stays rooted until it's over (CHARGE_LENGTH)
            GodzillaEntity.broadcastRoar(level, player.getX(), player.getY() + 30.0, player.getZ(), ModSounds.GODZILLA_ROAR.get(),
                    player.getRandom().nextLong());
            ModNetwork.sendNear(level, player.position(), 512.0,
                    new GodzillaMovePacket(player.getId(), Move.ROAR.ordinal(), breathCooldown(player), "roar"));
        }
        if (t == GodzillaEntity.BEAM_END_TICK + 80 && state.blastAt != null) {
            ModNetwork.sendNear(level, state.blastAt, 640.0, new FxPacket(FxPacket.Type.BLACK_RAIN, state.blastAt, 150.0F, 20.0F));
        }
        if (t >= GodzillaEntity.CHARGE_LENGTH) {
            state.charge = -1;
            root(player, false); // his roar is over: free to move again
        }
    }

    /** Whether he's rooted to the spot by his atomic breath (its charge, the beam and the roar after it). */
    public static boolean isRooted(Player player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        return speed != null && speed.hasModifier(ROOTED);
    }

    /** The beam: from his mouth where he's looking, to the first thing it hits (or as far as it reaches). */
    private static Vec3[] beam(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 from = player.getEyePosition().add(look.scale(6.0));
        Vec3 to = from.add(look.scale(BEAM_REACH));
        HitResult hit = player.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
        return new Vec3[] {from, hit.getLocation()};
    }

    private static void beamHits(ServerPlayer player, Vec3 start, Vec3 end) {
        ServerLevel level = player.serverLevel();
        DamageSource source = ModDamageTypes.source(level, ModDamageTypes.ATOMIC_BREATH, player);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(start, end).inflate(4.0),
                e -> e != player && e.isAlive())) {
            if (GodzillaEntity.distanceSqrToSegment(entity.getBoundingBox().getCenter(), start, end) > 16.0) {
                continue;
            }
            if (entity instanceof ShindenEntity plane && plane.isVehicle()) {
                plane.explode();
            } else {
                Blasts.nuclearHit(entity, source, false);
            }
        }
    }

    /** Where the beam lands: the nuclear blast, as the mob's (and on the ground, the flattening). */
    private static void blast(ServerPlayer player, Vec3 impact) {
        ServerLevel level = player.serverLevel();
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(impact.x), Mth.floor(impact.z));
        boolean grounded = impact.y - ground < 20.0;
        if (grounded) {
            impact = new Vec3(impact.x, Math.min(impact.y, ground), impact.z);
        }
        DamageSource source = ModDamageTypes.source(level, ModDamageTypes.ATOMIC_BREATH, player);
        boolean grief = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING) && Config.GODZILLA_BREAKS_BLOCKS.get();
        Blasts.nuclear(level, impact, grounded, source, player, player, grief, false, player.getYRot());
        if (grief && grounded) {
            Flattening.start(level, impact, Config.ATOMIC_FLATTEN_RADIUS.get(), Config.ATOMIC_FLATTEN_LEVEL.get());
        }
        State state = STATES.get(player.getUUID());
        if (state != null) {
            state.blastAt = impact;
        }
    }

    private PlayerGodzilla() {
    }
}
