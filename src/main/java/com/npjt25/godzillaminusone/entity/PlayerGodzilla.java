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
 *   <li>{@link Move#TAIL_WHIP}: the spin, its tail sweeping round him and then snapping, knocking away what's built
 *       round him (low down).</li>
 *   <li>{@link Move#SCRATCH}: a claw swipe, right then left: it hurts and flings whatever is in front of him, and tears
 *       through what's built there.</li>
 *   <li>{@link Move#BITE}: his jaws, where he's looking (in the water, his swimming bite).</li>
 *   <li>{@link Move#ROAR}: heard thousands of blocks away; whatever is near cowers (Weakness and Slowness).</li>
 *   <li>{@link Move#ATOMIC_BREATH}: the whole charge as the mob does it (the plates lighting up one by one, the
 *       in-breath; he stands his ground meanwhile), then the beam, where he's looking, and the nuclear blast where it
 *       lands, flattening and all, and his roar as the beam dies out: he can't move from the start of the charge to the
 *       end of that roar. Then the breath needs to recharge (atomicBreathCooldown, 3 minutes).</li>
 *   <li>{@link Move#TAIL_STRIKE} (playing as Godzilla Minus Zero): the plate at the tip of his tail punches up and glows,
 *       then the spin and whip, twice as hard as the tail whip: whatever it hits is destroyed, every block in its sweep
 *       too. As Minus Zero, his breath is Minus Zero's (its second blast, both blasts twice as big), and every reach of
 *       his is twice Minus One's.</li>
 *   <li>Each move is also a command: /godzilla tailwhip, scratch, bite, roar, breath, tailstrike (GodzillaCommands).</li>
 *   <li>Walking, he destroys what's in his way (at walkBreaksFromY, 60, or above) and crushes footprints (playable
 *       section of the config).
 *       Whatever interrupts him (changing back, dying, respawning, changing dimension, logging out) frees him.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = GodzillaMinusOne.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PlayerGodzilla {
    public enum Move {
        TAIL_WHIP("spin_whip", 44), SCRATCH("swipe_right", 22), BITE("bite", 20), ROAR("roar", 60),
        ATOMIC_BREATH(null, GodzillaEntity.CHARGE_LENGTH),
        /** Minus Zero's alone: the tip of his tail punched up and glowing for 1 s, then the whip, twice as hard. */
        TAIL_STRIKE("tail_strike", 64);

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
    /** How high above his feet the tail sweeping round takes buildings. */
    private static final int TAIL_HEIGHT = 10;

    private static final Map<UUID, State> STATES = new HashMap<>();

    /** One player's moves in progress. */
    private static final class State {
        int busyUntil;
        /** The last move made, and when (the player's ticks): for the in-game checks. */
        @Nullable
        Move last;
        int lastAt = -1;
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

    /** Whether {@code player} is playing as Godzilla Minus Zero (who has the tail strike). */
    public static boolean canTailStrike(Player player) {
        GodzillaEntity shape = Shapes.godzillaOf(player);
        return shape != null && shape.tailStrikes();
    }

    /** How much bigger than Minus One the Godzilla {@code player} plays as is (Minus Zero: twice). */
    static double scale(Player player) {
        GodzillaEntity shape = Shapes.godzillaOf(player);
        return shape != null ? shape.bodyScale() : 1.0;
    }

    /** For the in-game checks: the last move {@code player} made (null: none yet), and when (their ticks; -1: never). */
    @Nullable
    public static Move lastMove(Player player) {
        State state = STATES.get(player.getUUID());
        return state == null ? null : state.last;
    }

    public static int lastMoveAt(Player player) {
        State state = STATES.get(player.getUUID());
        return state == null ? -1 : state.lastAt;
    }

    /** Ticks until {@code player}'s atomic breath is ready again. */
    public static int breathCooldown(ServerPlayer player) {
        long ready = player.getPersistentData().getLong(BREATH_READY);
        return (int) Math.max(0L, ready - player.serverLevel().getGameTime());
    }

    /**
     * A key pressed (client.PlayerGodzillaControls) or a /godzilla command: the move, if he's Godzilla and free to make
     * it (the tail strike: if he's Minus Zero). Returns whether he made it.
     */
    public static boolean use(ServerPlayer player, Move move) {
        GodzillaEntity shape = Shapes.godzillaOf(player);
        if (!player.isAlive() || player.isSpectator() || shape == null || move == Move.TAIL_STRIKE && !shape.tailStrikes()) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        State state = STATES.computeIfAbsent(player.getUUID(), id -> new State());
        if (player.tickCount < state.busyUntil || state.charge >= 0) {
            return false;
        }
        double s = shape.bodyScale();
        String animation = move.animation;
        switch (move) {
            case TAIL_WHIP -> {
                later(state, player, 17, () -> tailSweep(player, 0.0, 30.0 * s, 0.4F, SoundEvents.PLAYER_ATTACK_SWEEP, 0.7F));
                later(state, player, 26, () -> tailSweep(player, 30.0 * s, 38.0 * s, 0.35F, SoundEvents.PLAYER_ATTACK_CRIT, 1.0F));
            }
            case TAIL_STRIKE -> {
                float power = GodzillaEntity.TAIL_STRIKE_POWER;
                later(state, player, 37, () -> tailStrike(player, 0.0, 30.0 * s, 0.4F * power, SoundEvents.PLAYER_ATTACK_SWEEP, 1.2F));
                later(state, player, 46, () -> tailStrike(player, 30.0 * s, 38.0 * s, 0.35F * power, SoundEvents.PLAYER_ATTACK_CRIT, 1.5F));
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
                    return false;
                }
                state.charge = 0;
                root(player, true);
            }
        }
        int length = move == Move.ATOMIC_BREATH ? shape.chargeLength() : move.length;
        state.busyUntil = player.tickCount + length;
        state.last = move;
        state.lastAt = player.tickCount;
        int cooldown = move == Move.ATOMIC_BREATH ? Config.ATOMIC_BREATH_COOLDOWN.get() * 20 + length : breathCooldown(player);
        if (move == Move.ATOMIC_BREATH) {
            player.getPersistentData().putLong(BREATH_READY, level.getGameTime() + cooldown);
        }
        // everyone who can see him sees the move (and he gets his breath's recharge time)
        GodzillaMovePacket packet = new GodzillaMovePacket(player.getId(), move.ordinal(), cooldown, animation);
        ModNetwork.sendNear(level, player.position(), 512.0, packet);
        return true;
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
     * Walking as him, he destroys whatever he walks into at walkBreaksFromY (60) or above, ground and all, as the mob does
     * (nothing below it; if breaksBlocks is on and the player may build here), and crushes a footprint where each foot
     * comes down.
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
        int cap = 300 * (int) Math.round(scale(player) * scale(player));
        for (int y = Math.max(Mth.floor(player.getY()) + 1, Destruction.walkFloorY(player)); y <= Mth.floor(box.maxY) && broken < cap; y++) {
            for (int x = Mth.floor(box.minX); x <= Mth.floor(box.maxX) && broken < cap; x++) {
                for (int z = Mth.floor(box.minZ); z <= Mth.floor(box.maxZ) && broken < cap; z++) {
                    BlockState state = level.getBlockState(pos.set(x, y, z));
                    if (state.isAir() || !state.getFluidState().isEmpty()) {
                        continue;
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
            double s = scale(player);
            double side = ((strides / 4) % 2 == 0 ? GodzillaEntity.FOOT_SIDE : -GodzillaEntity.FOOT_SIDE) * s;
            float yaw = player.yBodyRot;
            Vec3 fwd = Vec3.directionFromRotation(0.0F, yaw);
            Vec3 foot = now.add(fwd.scale(GodzillaEntity.FOOT_FORWARD * s)).add(-fwd.z * side, 0.0, fwd.x * side);
            Destruction.footprint(level, player, foot, yaw, GodzillaEntity.FOOT_HALF_WIDTH * s, GodzillaEntity.FOOT_HALF_LENGTH * s);
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

    /** The tail comes round (out to {@code radius}), and what's built in its way, from {@code from} out, goes flying. */
    private static void tailSweep(ServerPlayer player, double from, double radius, float share, SoundEvent sound, float shake) {
        hurtAround(player, player.position(), radius, damage() * share, null);
        if (mayDestroy(player)) {
            Destruction.tailSweep(player.serverLevel(), player, player.position(), Math.max(from, player.getBbWidth() / 2.0), radius,
                    (int) Math.round(TAIL_HEIGHT * scale(player)));
        }
        player.level().playSound(null, player.getX(), player.getY() + 4.0, player.getZ(), sound, SoundSource.PLAYERS, 10.0F, 0.35F);
        ModNetwork.shake(player.serverLevel(), player.position(), 64.0, shake, 1.2F);
    }

    /**
     * Minus Zero's tail strike, from {@code from} out to {@code radius}: what's alive takes {@code share} of his blow and is
     * flung, anything else is destroyed (GodzillaEntity.strikeAround), and so is every block in its sweep.
     */
    private static void tailStrike(ServerPlayer player, double from, double radius, float share, SoundEvent sound, float shake) {
        ServerLevel level = player.serverLevel();
        double inner = Math.max(from, player.getBbWidth() / 2.0);
        double height = TAIL_HEIGHT * scale(player);
        GodzillaEntity.strikeAround(level, player, player.position(), inner, radius, height, damage() * share,
                level.damageSources().playerAttack(player));
        if (mayDestroy(player)) {
            Destruction.tailSweep(level, player, player.position(), inner, radius, (int) Math.round(height), true);
        }
        level.playSound(null, player.getX(), player.getY() + 4.0, player.getZ(), sound, SoundSource.PLAYERS, 14.0F, 0.28F);
        ModNetwork.shake(level, player.position(), 128.0, shake, 1.3F);
    }

    private static void scratch(ServerPlayer player, double side) {
        double s = scale(player);
        Vec3 claws = at(player, 2.2 * side * s, 10.0 * s, 16.5 * s);
        Vec3 fwd = Vec3.directionFromRotation(0.0F, player.getYRot());
        Vec3 across = new Vec3(fwd.z, 0.0, -fwd.x).scale(side * 2.2).add(0.0, 0.7, 0.0);
        hurtAround(player, claws, 15.0 * s, damage() * 0.6F, across);
        tearThrough(player, claws, 7.0 * s, across.add(fwd.scale(0.4)).scale(0.3));
        player.level().playSound(null, claws.x, claws.y, claws.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 8.0F, 0.45F);
        ModNetwork.shake(player.serverLevel(), claws, 48.0, 0.4F, 0.8F);
    }

    private static void bite(ServerPlayer player) {
        double s = scale(player);
        Vec3 jaws = player.isInWater() ? at(player, 0.0, 1.0 * s, 18.0 * s) : player.getEyePosition().add(player.getLookAngle().scale(14.0 * s));
        hurtAround(player, jaws, 9.0 * s, damage(), Vec3.ZERO);
        tearThrough(player, jaws, 5.0 * s, player.getLookAngle().scale(0.4));
        if (player.isInWater()) {
            player.level().playSound(null, jaws.x, jaws.y, jaws.z, SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 6.0F, 0.5F);
        }
        player.level().playSound(null, jaws.x, jaws.y, jaws.z, ModSounds.GODZILLA_BITE.get(), SoundSource.PLAYERS, 6.0F, 1.0F);
        ModNetwork.shake(player.serverLevel(), jaws, 48.0, 0.4F, 1.0F);
    }

    /** What's built within {@code radius} of {@code center} is torn apart: solid blocks topple, the rest breaks. */
    private static void tearThrough(ServerPlayer player, Vec3 center, double radius, Vec3 push) {
        if (mayDestroy(player)) {
            Destruction.tearThrough(player.serverLevel(), player, center, radius, push);
        }
    }

    private static void roar(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        GodzillaEntity.broadcastRoar(level, player.getX(), player.getY() + 30.0, player.getZ(), ModSounds.GODZILLA_ROAR.get(),
                player.getRandom().nextLong());
        for (LivingEntity entity : Blasts.livingNear(level, player.position(), 48.0 * scale(player), player)) {
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

    /**
     * The mob's charge timeline (entity.GodzillaEntity), as the Godzilla he plays as has it: the beam, its blast, the
     * cooling down; as Minus Zero, his first beam held on, his second beam (wider, hitting twice as often) and second
     * blast, both blasts twice as big.
     */
    private static void tickBreath(ServerPlayer player, State state) {
        int t = ++state.charge;
        ServerLevel level = player.serverLevel();
        GodzillaEntity shape = Shapes.godzillaOf(player);
        if (shape == null) {
            return;
        }
        int beamEnd = shape.beamEndTick();
        boolean surging = shape.surges() && t >= GodzillaEntity.SURGE_TICK;
        if (t >= GodzillaEntity.BEAM_START_TICK && t < beamEnd && (t - GodzillaEntity.BEAM_START_TICK) % (surging ? 2 : 4) == 0) {
            Vec3[] beam = beam(player);
            beamHits(player, beam[0], beam[1], shape.beamRadius(t));
            if (t == GodzillaEntity.BEAM_START_TICK) {
                ModNetwork.shake(level, player.position(), 160.0, 1.0F, 4.0F);
            }
        }
        if (shape.surges() && t == GodzillaEntity.SURGE_TICK) {
            // his second beam: his plates punched down, and the ground shakes again as it fires
            ModNetwork.shake(level, player.position(), 220.0, 1.2F, 3.0F);
            level.playSound(null, player.getX(), player.getY() + 30.0 * shape.bodyScale(), player.getZ(), ModSounds.GODZILLA_ROAR2.get(),
                    SoundSource.PLAYERS, 12.0F, 1.2F);
        }
        if (shape.blastsAt(t)) {
            blast(player, beam(player)[1], shape.blastScale());
        }
        if (t == beamEnd) {
            // the roar as the beam dies out, as the mob's: he stays rooted until it's over (CHARGE_LENGTH)
            GodzillaEntity.broadcastRoar(level, player.getX(), player.getY() + 30.0, player.getZ(), ModSounds.GODZILLA_ROAR.get(),
                    player.getRandom().nextLong());
            ModNetwork.sendNear(level, player.position(), 512.0,
                    new GodzillaMovePacket(player.getId(), Move.ROAR.ordinal(), breathCooldown(player), "roar"));
        }
        if (t == beamEnd + 80 && state.blastAt != null) {
            ModNetwork.sendNear(level, state.blastAt, 640.0, new FxPacket(FxPacket.Type.BLACK_RAIN, state.blastAt, 150.0F, 20.0F));
        }
        if (t >= shape.chargeLength()) {
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
        double s = scale(player);
        Vec3 from = player.getEyePosition().add(look.scale(6.0 * s));
        Vec3 to = from.add(look.scale(BEAM_REACH * s));
        HitResult hit = player.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
        return new Vec3[] {from, hit.getLocation()};
    }

    private static void beamHits(ServerPlayer player, Vec3 start, Vec3 end, double radius) {
        ServerLevel level = player.serverLevel();
        DamageSource source = ModDamageTypes.source(level, ModDamageTypes.ATOMIC_BREATH, player);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(start, end).inflate(radius),
                e -> e != player && e.isAlive())) {
            if (GodzillaEntity.distanceSqrToSegment(entity.getBoundingBox().getCenter(), start, end) > radius * radius) {
                continue;
            }
            if (entity instanceof ShindenEntity plane && plane.isVehicle()) {
                plane.explode();
            } else {
                Blasts.nuclearHit(entity, source, false);
            }
        }
    }

    /** Where the beam lands: the nuclear blast, as the mob's (and on the ground, the flattening), {@code scale} times as big. */
    private static void blast(ServerPlayer player, Vec3 impact, double scale) {
        ServerLevel level = player.serverLevel();
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(impact.x), Mth.floor(impact.z));
        boolean grounded = impact.y - ground < 20.0;
        if (grounded) {
            impact = new Vec3(impact.x, Math.min(impact.y, ground), impact.z);
        }
        DamageSource source = ModDamageTypes.source(level, ModDamageTypes.ATOMIC_BREATH, player);
        boolean grief = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING) && Config.GODZILLA_BREAKS_BLOCKS.get();
        Blasts.nuclear(level, impact, grounded, source, player, player, grief, false, player.getYRot(), scale);
        if (grief && grounded) {
            Flattening.start(level, impact, (int) Math.round(Config.ATOMIC_FLATTEN_RADIUS.get() * scale), Config.ATOMIC_FLATTEN_LEVEL.get());
        }
        State state = STATES.get(player.getUUID());
        if (state != null) {
            state.blastAt = impact;
        }
    }

    private PlayerGodzilla() {
    }
}
