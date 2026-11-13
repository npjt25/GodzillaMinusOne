package com.npjt25.godzillaminusone.entity;

import com.npjt25.godzillaminusone.Config;
import com.npjt25.godzillaminusone.GodzillaMinusOne;
import com.npjt25.godzillaminusone.network.ModNetwork;
import com.npjt25.godzillaminusone.registry.ModDamageTypes;
import com.npjt25.godzillaminusone.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/**
 * King Ghidorah: an enormous, ancient three-headed golden dragon, as tall as Godzilla Minus Zero (his heads 90.74
 * blocks up as he stands: his model, tools/make_ghidorah.py, drawn at {@link #RENDER_SCALE}), with Minus Zero's
 * hitbox (20 x 80), his health (maxHealth) and his durability: only naval mines hurt him (each a tenth of his health:
 * onlyHurtByNavalMines, mineDamagePercent), nothing of a nuclear blast does, and while he has nothing to fight he heals
 * (calmRegenPercent). He hunts as Minus Zero does (every creature, or with targetsAllMobs off the dangerous ones), and
 * Godzilla too, roars as often (roarIntervalSeconds), and tears through what's built as he goes (walkBreaksFromY up).
 *
 * <p>He stands crouched with his wings raised and half spread, never folded (but as he digs himself out of the
 * ground), and he fights with:
 * <ul>
 *   <li>a bite from whichever of his three heads is nearest;</li>
 *   <li>a stomp of his right foot on whatever is under him;</li>
 *   <li>a whip of both his tails round behind him;</li>
 *   <li>his gravity beams, from 40 to 160 blocks: three concentrated golden bolts that bore through whatever is in
 *       their way and vaporize every block 12 blocks round where they strike, and what's caught there;</li>
 *   <li>a lift: the beams seize their target, haul it high into the air and let it fall;</li>
 *   <li>a grab: he takes off, seizes his target (a player, Godzilla, anything) in his talons, carries it high and dives,
 *       slamming it into the ground.</li>
 * </ul>
 * With his health low he flies off and away (retreats, retreatHealthPercent).
 *
 * <p>Spawned (his egg, /summon), he rises out of the ground ({@link #ENTRANCE_TICKS}, client.GhidorahEffects), once:
 * not when he's loaded again. A thunderstorm follows {@code entranceStormDelayTicks} after it ends.
 *
 * <p>Everything he does runs on the server's clock ({@link #getActionTicks}): his animations and their effects are
 * where the server is in them, for a player who starts watching halfway through as for one who saw it begin.
 */
public class KingGhidorahEntity extends Monster implements GeoEntity {
    /** His model's height standing (tools/make_ghidorah.py: the tops of his heads' horns, idling in his crouch). */
    public static final double MODEL_HEIGHT = 105.5;
    /** As tall as Godzilla Minus Zero (twice Minus One's standing height). */
    public static final double REAL_HEIGHT = GodzillaEntity.REAL_HEIGHT * GodzillaMinusZeroEntity.SCALE;
    /** How big his renderer draws him (sixteenths of a block per model unit). */
    public static final float RENDER_SCALE = (float) (REAL_HEIGHT * 16.0 / MODEL_HEIGHT);
    /** Blocks per model unit. */
    public static final double UNIT = RENDER_SCALE / 16.0;

    public static final int ACTION_NONE = 0;
    public static final int ACTION_ROAR = 1;
    public static final int ACTION_BITE = 2;   // + the head: 2 middle, 3 right, 4 left
    public static final int ACTION_BEAM = 5;
    public static final int ACTION_LIFT = 6;
    public static final int ACTION_STOMP = 7;
    public static final int ACTION_WHIP = 8;
    public static final int ACTION_ENTRANCE = 9;
    public static final int ACTION_GRAB = 10;
    public static final int ACTION_RETREAT = 11;

    /** Ticks each animation lasts (animation.king_ghidorah.*). */
    public static final int ROAR_TICKS = 64;
    public static final int BITE_TICKS = 26;
    public static final int BEAM_TICKS = 156;
    public static final int STOMP_TICKS = 32;
    public static final int WHIP_TICKS = 36;
    public static final int ENTRANCE_TICKS = 292;
    private static final int ROAR_SOUND_AT = 6;
    public static final int BITE_SNAP_AT = 12;
    /** His walk animation's stride (animation.king_ghidorah.walk, 2.4 s at its own pace), in ticks. */
    public static final int WALK_TICKS = 48;
    /**
     * When in his stride each hind foot comes down (measured on his walk animation: his left foot touches the ground 3
     * ticks in, his right 27), a couple of ticks early: the pack's footsteps thud about 0.15 s in.
     */
    public static final int[] WALK_FOOTFALLS = {1, 25};
    /** How fast his walk animation carries him over the ground at its own pace (his planted feet, measured): blocks a tick. */
    public static final double WALK_BLOCKS_PER_TICK = 0.35;
    /** How long his animations take to blend from one into the next (his main controller's transition). */
    private static final int MAIN_TRANSITION = 6;
    /** How long his flying sound (his wingbeats) lasts: it plays again as it ends while he's in the air. */
    private static final int FLYING_SOUND_TICKS = 164;
    public static final int STOMP_HIT_AT = 17;
    public static final int WHIP_HIT_AT = 15;
    /** The tick his beams stop (the animation's BEAM_END: as long as his gravity beam sound roars, before it fades). */
    public static final int BEAM_END_AT = 136;

    public static final double BEAM_MIN_RANGE = 40.0;
    public static final double BEAM_RANGE = 160.0;
    /** Every block this far round where a beam strikes is vaporized. */
    public static final double VAPORIZE_RADIUS = 12.0;
    /** And this far round its path, all the way there. */
    private static final double BORE_RADIUS = 2.5;
    /** How high his lift hauls what it holds before it lets go. */
    public static final double LIFT_HEIGHT = 34.0;
    private static final double BITE_RADIUS = 18.0;
    private static final double STOMP_RADIUS = 16.0;
    private static final double WHIP_INNER = 8.0;
    private static final double WHIP_OUTER = 66.0;
    /** How high over the ground he carries what he's grabbed before he slams it down. */
    public static final double GRAB_ALTITUDE = 48.0;
    private static final double FLY_SPEED = 1.6;
    private static final int BEAM_COOLDOWN = 15 * 20;
    private static final int LIFT_COOLDOWN = 20 * 20;
    private static final int GRAB_COOLDOWN = 45 * 20;
    private static final int RETREAT_COOLDOWN = 2 * 60 * 20;

    /**
     * His three heads, and where their mouths are (model units: his right, up, forward) as they fire their beams
     * (animation.king_ghidorah.gravity_beam, 2 s in), and as they snap shut on a bite (bite_*, 0.62 s), and when their
     * beams fire (animation seconds).
     */
    public enum Head {
        MIDDLE("c", 0.0, 70.1, 55.4, 0.0, 49.1, 65.2, 1.2),
        RIGHT("r", 19.4, 66.6, 51.2, 25.7, 47.5, 57.7, 1.35),
        LEFT("l", -19.4, 66.9, 51.4, -25.7, 47.5, 57.7, 1.5);

        public final String bone;
        final Vec3 beamMouth;
        public final Vec3 biteMouth;
        /** The tick its beam fires. */
        public final int fireAt;

        Head(String bone, double bx, double by, double bz, double kx, double ky, double kz, double fireSeconds) {
            this.bone = bone;
            this.beamMouth = new Vec3(bx, by, bz).scale(UNIT);
            this.biteMouth = new Vec3(kx, ky, kz).scale(UNIT);
            this.fireAt = (int) Math.round(fireSeconds * 20);
        }

        /** Each head's beam strikes a little apart from the others': {right, up} blocks off the aim. */
        public Vec3 spread(int tick) {
            double wobble = Math.sin(tick * 0.31 + ordinal() * 2.1) * 1.2;
            return switch (this) {
                case MIDDLE -> new Vec3(wobble, 0.0, 0.0);
                case RIGHT -> new Vec3(4.0 + wobble, -0.5, 0.0);
                case LEFT -> new Vec3(-4.0 + wobble, -0.5, 0.0);
            };
        }
    }

    /** Where a flight is: up off the ground, after its prey, carrying it up, diving, landing; or fleeing. */
    private enum Flight {
        TAKEOFF, APPROACH, ASCEND, DIVE, FLEE, LAND
    }

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.king_ghidorah.idle");
    private static final String WALK_NAME = "animation.king_ghidorah.walk";
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop(WALK_NAME);
    private static final RawAnimation FLY = RawAnimation.begin().thenLoop("animation.king_ghidorah.fly");
    private static final RawAnimation FLY_CARRY = RawAnimation.begin().thenLoop("animation.king_ghidorah.fly_carry");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("animation.king_ghidorah.death");

    private static final EntityDataAccessor<Integer> DATA_ACTION = SynchedEntityData.defineId(KingGhidorahEntity.class, EntityDataSerializers.INT);
    /** The game time (its low 32 bits) the action began: both sides count from it. */
    private static final EntityDataAccessor<Integer> DATA_ACTION_START = SynchedEntityData.defineId(KingGhidorahEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> DATA_AIM = SynchedEntityData.defineId(KingGhidorahEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Boolean> DATA_FLYING = SynchedEntityData.defineId(KingGhidorahEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_CARRYING = SynchedEntityData.defineId(KingGhidorahEntity.class, EntityDataSerializers.BOOLEAN);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final ServerBossEvent bossEvent = new ServerBossEvent(
            Component.translatable("boss." + GodzillaMinusOne.MODID + ".king_ghidorah"),
            BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.PROGRESS);

    private int beamCooldown = 60;
    private int liftCooldown = 200;
    private int grabCooldown = 200;
    private int biteCooldown;
    private int stompCooldown;
    private int whipCooldown;
    private int retreatCooldown;
    private int roarTimer;
    private int decisionDelay;
    /** Footsteps the server has sounded (his stomps, landings, his heaves at his entrance), for their turns. */
    private int serverSteps;
    /** Footsteps his walk has sounded on this client, for their turns. */
    private int walkSteps;
    /** On each client: how far into his walk he is (animation ticks: it runs at strideSpeed), how fast he's going, and for how long he's walked (-1: he isn't). */
    private double walkClock;
    private double walkClockSpeed;
    private double groundSpeed;
    private int walkingFor = -1;
    /** Ticks till his flying sound plays again. */
    private int flyingSoundIn;
    /** How far into his action his last tick got (-1: not yet), and this tick's stretch of it (reached). */
    private int actionTickedTo = -1;
    private int actionFrom = -1;
    private int actionTo = -1;
    @Nullable
    private LivingEntity lastTarget;
    @Nullable
    private Integer walkFloorOverride;
    @Nullable
    private Vec3 walkTarget;
    // his entrance
    private boolean wantsEntrance;
    private boolean entranceDone;
    private long stormAt = -1L;
    private float entranceYaw;
    // a flight
    private Flight flight = Flight.TAKEOFF;
    private int flightTicks;
    @Nullable
    private LivingEntity prey;
    @Nullable
    private Vec3 fleeFrom;
    private boolean slammed;
    // what his lift holds, and where it was taken from
    @Nullable
    private LivingEntity lifted;
    private double liftedFromY;
    /** Beams vaporizing what they strike, a bit at a time. */
    private final List<Vaporize> vaporizing = new ArrayList<>();
    /** (client) Where each head's mouth was last drawn, relative to him (client.render.KingGhidorahRenderer). */
    public final Vec3[] drawnMouths = new Vec3[3];

    public KingGhidorahEntity(EntityType<? extends KingGhidorahEntity> type, Level level) {
        super(type, level);
        this.xpReward = 500;
        this.noCulling = true;          // (his wings and tails reach far outside his hitbox)
        this.setMaxUpStep(6.0F);
        this.setPersistenceRequired();
        this.moveControl = new GodzillaEntity.GodzillaMoveControl(this, 4.0F);
        if (!level.isClientSide) {
            applyConfig();
            this.roarTimer = Config.ROAR_INTERVAL.get() * 20;
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 1000.0D)
                .add(Attributes.ATTACK_DAMAGE, 2000.0D)
                .add(Attributes.ATTACK_KNOCKBACK, 3.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.FOLLOW_RANGE, BEAM_RANGE)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.ARMOR, 20.0D);
    }

    /** Minus Zero's health and the damage of his blows (maxHealth, attackDamage). */
    private void applyConfig() {
        AttributeInstance health = getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(Config.GODZILLA_MAX_HEALTH.get());
        }
        AttributeInstance damage = getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null) {
            damage.setBaseValue(Config.GODZILLA_ATTACK_DAMAGE.get());
        }
        setHealth(getMaxHealth());
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_ACTION, ACTION_NONE);
        this.entityData.define(DATA_ACTION_START, 0);
        this.entityData.define(DATA_AIM, new Vector3f());
        this.entityData.define(DATA_FLYING, false);
        this.entityData.define(DATA_CARRYING, false);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(2, new HuntGoal(this));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 64.0F) {
            @Override
            public boolean canUse() {
                return !((KingGhidorahEntity) this.mob).isBusy() && super.canUse();
            }
        });
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this) {
            @Override
            public boolean canUse() {
                return !isBusy() && super.canUse();
            }
        });
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new Prey(this));
    }

    // ------------------------------------------------------------------ state

    /** What he's doing: {@link #ACTION_NONE}, a roar, a bite ({@link #ACTION_BITE} + a head), his beam... */
    public int getAction() {
        return this.entityData.get(DATA_ACTION);
    }

    /** Ticks into what he's doing, by the server's clock (on either side). */
    /** When (game time) what he's doing began: the server's, synced to every client watching. */
    public int getActionStart() {
        return this.entityData.get(DATA_ACTION_START);
    }

    public int getActionTicks() {
        return (int) level().getGameTime() - this.entityData.get(DATA_ACTION_START);
    }

    /** The same, to the fraction of a tick (for his animations). */
    public double getActionTicks(double partialTick) {
        return getActionTicks() + partialTick;
    }

    /** Where his beams are aimed (world). */
    public Vec3 getAim() {
        Vector3f v = this.entityData.get(DATA_AIM);
        return new Vec3(v.x(), v.y(), v.z());
    }

    public boolean isFlying() {
        return this.entityData.get(DATA_FLYING);
    }

    public boolean isCarrying() {
        return this.entityData.get(DATA_CARRYING);
    }

    /** Whether a beam fires from this head now (his gravity beams or his lift). */
    public boolean isFiring(Head head) {
        int action = getAction();
        int t = getActionTicks();
        return (action == ACTION_BEAM || action == ACTION_LIFT) && t >= head.fireAt && t < BEAM_END_AT + head.ordinal() * 2;
    }

    public boolean isBusy() {
        return getAction() != ACTION_NONE;
    }

    public boolean inEntrance() {
        return getAction() == ACTION_ENTRANCE;
    }

    /** The lowest Y his stride destroys anything at (walkBreaksFromY; a game test sets its own). */
    public int walkFloorY() {
        return this.walkFloorOverride != null ? this.walkFloorOverride : Config.WALK_BREAKS_FROM_Y.get();
    }

    public void setWalkFloorY(@Nullable Integer y) {
        this.walkFloorOverride = y;
    }

    /** World position of a point on him: {@code x} blocks to his right, {@code y} up, {@code z} forward. */
    public Vec3 locator(Vec3 local) {
        Vec3 fwd = Vec3.directionFromRotation(0.0F, this.yBodyRot);
        Vec3 right = new Vec3(-fwd.z, 0.0, fwd.x);
        return position().add(fwd.scale(local.z)).add(right.scale(local.x)).add(0.0, local.y, 0.0);
    }

    public Vec3 beamMouth(Head head) {
        return locator(head.beamMouth);
    }

    /**
     * Where a head's beam strikes: its aim, spread a little from the others' (his gravity beams bore through what's
     * in their way); his lift's beams, at what they hold.
     */
    public Vec3 beamEnd(Head head, Vec3 from, Vec3 aim) {
        Vec3 fwd = aim.subtract(from);
        Vec3 right = new Vec3(-fwd.z, 0.0, fwd.x).normalize();
        Vec3 off = getAction() == ACTION_LIFT ? head.spread(this.tickCount).scale(0.15) : head.spread(this.tickCount);
        return aim.add(right.scale(off.x)).add(0.0, off.y, 0.0);
    }

    // ------------------------------------------------------------------ ticking

    @Override
    public void tick() {
        if (!level().isClientSide && this.wantsEntrance && !this.entranceDone && getAction() == ACTION_NONE) {
            this.wantsEntrance = false;
            startEntrance();
        }
        if (inEntrance()) {
            // he rises where he is: no turning, no walking, nothing moves him
            setYRot(this.entranceYaw);
            this.yBodyRot = this.entranceYaw;
            this.yHeadRot = this.entranceYaw;
            setDeltaMovement(0.0, Math.min(0.0, getDeltaMovement().y), 0.0);
        }
        super.tick();
        if (level().isClientSide) {
            walkClockTick();
            EffectHooks.ghidorah.tick(this);
            return;
        }
        ServerLevel level = (ServerLevel) level();
        this.bossEvent.setProgress(Mth.clamp(getHealth() / getMaxHealth(), 0.0F, 1.0F));
        if (this.stormAt >= 0 && level.getGameTime() >= this.stormAt) {
            this.stormAt = -1L;
            storm(level);
        }
        tickVaporizing(level);
        if (isDeadOrDying()) {
            return;
        }
        if (this.beamCooldown > 0) {
            this.beamCooldown--;
        }
        if (this.liftCooldown > 0) {
            this.liftCooldown--;
        }
        if (this.grabCooldown > 0) {
            this.grabCooldown--;
        }
        if (this.biteCooldown > 0) {
            this.biteCooldown--;
        }
        if (this.stompCooldown > 0) {
            this.stompCooldown--;
        }
        if (this.whipCooldown > 0) {
            this.whipCooldown--;
        }
        if (this.retreatCooldown > 0) {
            this.retreatCooldown--;
        }
        LivingEntity target = getTarget();
        if (target != null && (!target.isAlive() || !isValidTarget(target))) {
            setTarget(null);
            target = null;
        }
        if (target == null && !isBusy() && this.tickCount % 100 == 0 && getHealth() < getMaxHealth()) {
            heal((float) (getMaxHealth() * Config.CALM_REGEN_PERCENT.get() / 100.0));     // (as Godzilla does)
        }
        if (target != null && target != this.lastTarget && !isBusy()) {
            startAction(ACTION_ROAR);       // (he lets what he's found to hunt know it)
        }
        this.lastTarget = target;
        int action = getAction();
        if (action != ACTION_NONE) {
            tickAction(level, action, getActionTicks(), target);
        } else {
            int interval = Config.ROAR_INTERVAL.get() * 20;
            if (interval > 0 && --this.roarTimer <= 0) {
                this.roarTimer = interval;
                startAction(ACTION_ROAR);
            }
            if (this.walkTarget != null && target == null) {
                if (position().distanceToSqr(this.walkTarget.x, getY(), this.walkTarget.z) < 16.0) {
                    this.walkTarget = null;
                } else {
                    getMoveControl().setWantedPosition(this.walkTarget.x, this.walkTarget.y, this.walkTarget.z, 1.0);
                }
            }
        }
        if (this.tickCount % 4 == 0 && !isFlying() && getDeltaMovement().horizontalDistanceSqr() > 1.0E-4) {
            smashBlocks(level);
        }
    }

    private void tickAction(ServerLevel level, int action, int t, @Nullable LivingEntity target) {
        this.actionFrom = this.actionTickedTo;
        this.actionTo = t;
        this.actionTickedTo = t;
        switch (action) {
            case ACTION_ROAR -> {
                if (reached(ROAR_SOUND_AT)) {
                    roar(level);
                }
                if (t >= ROAR_TICKS) {
                    endAction();
                }
            }
            case ACTION_BITE, ACTION_BITE + 1, ACTION_BITE + 2 -> {
                if (reached(BITE_SNAP_AT - 5)) {
                    Vec3 jaws = locator(Head.values()[action - ACTION_BITE].biteMouth);
                    sound(level, jaws, ModSounds.KING_GHIDORAH_BITE.get(), 10.0F, 1.0F);
                }
                if (reached(BITE_SNAP_AT)) {
                    bite(level, Head.values()[action - ACTION_BITE]);
                }
                if (t >= BITE_TICKS) {
                    endAction();
                }
            }
            case ACTION_STOMP -> {
                if (reached(STOMP_HIT_AT - 3)) {
                    footstep(level, stompFoot(), 16.0F);      // (its thud lands as his foot does)
                }
                if (reached(STOMP_HIT_AT)) {
                    stomp(level);
                }
                if (t >= STOMP_TICKS) {
                    endAction();
                }
            }
            case ACTION_WHIP -> {
                if (reached(WHIP_HIT_AT - 6)) {
                    level.playSound(null, getX(), getY() + 10.0, getZ(), ModSounds.MINUS_ZERO_TAIL_SWING.get(), SoundSource.HOSTILE, 12.0F, 0.8F);
                }
                if (reached(WHIP_HIT_AT)) {
                    whip(level);
                }
                if (t >= WHIP_TICKS) {
                    endAction();
                }
            }
            case ACTION_BEAM, ACTION_LIFT -> {
                beamTick(level, action, t, target);
                if (t >= BEAM_TICKS) {
                    this.lifted = null;
                    endAction();
                }
            }
            case ACTION_ENTRANCE -> entranceTick(level, t);
            case ACTION_GRAB, ACTION_RETREAT -> flightTick(level, action, target);
            default -> endAction();
        }
    }

    /**
     * Whether his action has got to tick {@code at} this tick: each of its moments (a bite's snap, a footfall, a head
     * firing) happens once, when it's reached, even if he missed that very tick (his chunk loading, the server behind).
     */
    private boolean reached(int at) {
        return this.actionFrom < at && at <= this.actionTo;
    }

    private boolean reachedAny(int[] ticks) {
        for (int at : ticks) {
            if (reached(at)) {
                return true;
            }
        }
        return false;
    }

    private void startAction(int action) {
        this.actionTickedTo = -1;
        this.entityData.set(DATA_ACTION_START, (int) level().getGameTime());
        this.entityData.set(DATA_ACTION, action);
        if (action == ACTION_ROAR) {
            this.roarTimer = Math.max(Config.ROAR_INTERVAL.get() * 20, 1);
        }
    }

    private void endAction() {
        this.entityData.set(DATA_ACTION, ACTION_NONE);
    }

    // ------------------------------------------------------------------ his entrance

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        @Nullable SpawnGroupData data, @Nullable CompoundTag tag) {
        // spawned (his egg, /summon, a spawner): he rises out of the ground, once (not when he's loaded again)
        this.wantsEntrance = true;
        return super.finalizeSpawn(level, difficulty, reason, data, tag);
    }

    /** Rise out of the ground now (his spawn does this; the harness and game tests call it). */
    public void startEntrance() {
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        settleOnGround(level);
        this.entranceYaw = getYRot();
        this.entranceDone = true;
        getNavigation().stop();
        startAction(ACTION_ENTRANCE);
    }

    /** On solid ground: down onto it if he was put in the air, up out of it if he was put inside it. */
    private void settleOnGround(ServerLevel level) {
        BlockPos.MutableBlockPos p = BlockPos.containing(getX(), getY(), getZ()).mutable();
        int moved = 0;
        while (moved < 16 && !level.getBlockState(p).getCollisionShape(level, p).isEmpty()) {
            p.move(0, 1, 0);
            moved++;
        }
        if (moved == 0) {
            while (moved < 96 && p.getY() > level.getMinBuildHeight()
                    && level.getBlockState(p.below()).getCollisionShape(level, p.below()).isEmpty()) {
                p.move(0, -1, 0);
                moved++;
            }
        }
        if (moved > 0) {
            setPos(getX(), p.getY(), getZ());
        }
    }

    /** His entrance's ticks: the earth cracking open (client.GhidorahEffects' bursts with them), his heaves out of it. */
    private static final int[] ENTRANCE_CRACKS = {18, 36, 52, 62, 112, 124, 160, 179, 197};
    private static final int[] ENTRANCE_HEAVES = {174, 193, 212};

    /** The sounds of his entrance, from the server so everyone in earshot hears them (the effects are the client's). */
    private void entranceTick(ServerLevel level, int t) {
        Vec3 at = position();
        if (reached(0) || reached(110)) {
            sound(level, at, ModSounds.KING_GHIDORAH_RUMBLE.get(), 14.0F, 0.85F);
        }
        if (reachedAny(ENTRANCE_CRACKS)) {
            sound(level, at, ModSounds.KING_GHIDORAH_EARTH_CRACK.get(), t >= 160 ? 14.0F : 9.0F, 0.75F + this.random.nextFloat() * 0.25F);
        }
        if (reachedAny(ENTRANCE_HEAVES)) {
            footstep(level, at, 16.0F);       // (each heave of him out of the ground)
        }
        if (reached(216)) {
            sound(level, at.add(0.0, 40.0, 0.0), ModSounds.KING_GHIDORAH_WING_SPREAD.get(), 16.0F, 0.9F);
        }
        if (reached(250)) {
            level.playSound(null, getX(), getY() + REAL_HEIGHT * 0.8, getZ(), ModSounds.KING_GHIDORAH_ENTRANCE_ROAR.get(),
                    SoundSource.HOSTILE, 25.0F, 1.0F);
        }
        if (t >= ENTRANCE_TICKS) {
            endAction();
            // the storm follows the end of his entrance, not his spawning
            int duration = Config.GHIDORAH_STORM_DURATION_TICKS.get();
            if (duration > 0) {
                this.stormAt = level.getGameTime() + Config.GHIDORAH_STORM_DELAY_TICKS.get();
            }
        }
    }

    private void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.HOSTILE, volume, pitch);
    }

    /** His three footstep sounds, in turn (1, 2, 3, 1, ...): never the same one twice running. */
    public static SoundEvent footstepSound(int step) {
        return switch (Math.floorMod(step, 3)) {
            case 0 -> ModSounds.KING_GHIDORAH_FOOTSTEP_1.get();
            case 1 -> ModSounds.KING_GHIDORAH_FOOTSTEP_2.get();
            default -> ModSounds.KING_GHIDORAH_FOOTSTEP_3.get();
        };
    }

    /**
     * A footstep from the server (his stomp, his landing, his heaves out of the ground at his entrance), the next of his
     * three in turn. (His walk's footsteps are his walk animation's: walkFootstep, on each client.)
     */
    private void footstep(ServerLevel level, Vec3 at, float volume) {
        sound(level, at, footstepSound(this.serverSteps++), volume, 1.0F);
    }

    /**
     * The thunderstorm after his entrance: rain and thunder over the whole dimension (through the game's own weather,
     * so every player in it gets it, and lightning strikes as it does in any storm) for entranceStormDurationTicks.
     * Only where there is weather: a dimension with a sky and no ceiling, with weather of its own (the Overworld).
     */
    private void storm(ServerLevel level) {
        int duration = Config.GHIDORAH_STORM_DURATION_TICKS.get();
        if (duration <= 0 || !level.dimensionType().hasSkyLight() || level.dimensionType().hasCeiling()) {
            return;
        }
        level.setWeatherParameters(0, duration, true, true);
    }

    /** When his storm is due (game time), or -1. */
    public long stormAt() {
        return this.stormAt;
    }

    // ------------------------------------------------------------------ the roar

    private void roar(ServerLevel level) {
        // (his wings spread wide as he roars; loud enough to carry 400 blocks, from up where his heads are)
        level.playSound(null, getX(), getY() + REAL_HEIGHT * 0.8, getZ(), ModSounds.KING_GHIDORAH_WING_SPAN_ROAR.get(),
                SoundSource.HOSTILE, 25.0F, 1.0F);
    }

    /** Roar now (a game test, or the harness). */
    public void startRoar() {
        startAction(ACTION_ROAR);
    }

    // ------------------------------------------------------------------ bites, stomps, his tails

    /** The head nearest a point: the middle one unless it's well off to his right or left. */
    public Head nearestHead(Vec3 point) {
        double side = localOf(point).x;
        return side > 10.0 ? Head.RIGHT : side < -10.0 ? Head.LEFT : Head.MIDDLE;
    }

    /** A point relative to him: {right, up, forward} blocks. */
    public Vec3 localOf(Vec3 point) {
        Vec3 fwd = Vec3.directionFromRotation(0.0F, this.yBodyRot);
        Vec3 right = new Vec3(-fwd.z, 0.0, fwd.x);
        Vec3 d = point.subtract(position());
        return new Vec3(d.dot(right), d.y, d.dot(fwd));
    }

    /** Where a head's bite reaches: round the point below where its jaws snap shut, from the ground up to them. */
    boolean inBiteReach(Entity target, Head head) {
        Vec3 jaws = locator(head.biteMouth);
        Vec3 c = target.getBoundingBox().getCenter();
        double dx = c.x - jaws.x;
        double dz = c.z - jaws.z;
        return dx * dx + dz * dz < BITE_RADIUS * BITE_RADIUS && c.y < jaws.y + 10.0 && c.y > getY() - 6.0;
    }

    public void startBite(Head head) {
        this.biteCooldown = 34;
        startAction(ACTION_BITE + head.ordinal());
    }

    private void bite(ServerLevel level, Head head) {
        Vec3 jaws = locator(head.biteMouth);
        AABB zone = new AABB(jaws.x - BITE_RADIUS, getY() - 6.0, jaws.z - BITE_RADIUS, jaws.x + BITE_RADIUS, jaws.y + 10.0, jaws.z + BITE_RADIUS);
        for (LivingEntity e : victims(level, zone)) {
            if (inBiteReach(e, head)) {
                strike(e, (float) getAttributeValue(Attributes.ATTACK_DAMAGE), e.position().subtract(position()), 2.0, 0.8);
            }
        }
        if (breaksBlocks(level)) {
            Vec3 push = Vec3.directionFromRotation(0.0F, this.yBodyRot).scale(0.6).add(0.0, 0.4, 0.0);
            Destruction.tearThrough(level, this, jaws, 7.0, push);
        }
    }

    /** Where his right foot comes down. */
    public Vec3 stompFoot() {
        return locator(new Vec3(11.4, 0.0, 2.2).scale(UNIT));
    }

    public void startStomp() {
        this.stompCooldown = 60;
        startAction(ACTION_STOMP);
    }

    private void stomp(ServerLevel level) {
        Vec3 foot = stompFoot();
        ModNetwork.shake(level, foot, 80.0, 1.6F, 1.2F);
        for (LivingEntity e : victims(level, new AABB(foot, foot).inflate(STOMP_RADIUS, 8.0, STOMP_RADIUS))) {
            Vec3 d = e.position().subtract(foot);
            if (d.horizontalDistance() < STOMP_RADIUS + e.getBbWidth() / 2.0 && e.getY() < foot.y + 8.0) {
                strike(e, (float) getAttributeValue(Attributes.ATTACK_DAMAGE), d, 1.6, 1.2);
            }
        }
        if (breaksBlocks(level) && Config.FOOTPRINTS.get()) {
            Destruction.footprint(level, this, foot, this.yBodyRot, 9.0, 12.0);
        }
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, foot.x, foot.y + 1.0, foot.z, 2, 4.0, 0.5, 4.0, 0.0);
    }

    public void startWhip() {
        this.whipCooldown = 100;
        startAction(ACTION_WHIP);
    }

    /** Both tails swung round behind and about him: whatever's in their sweep is struck and flung, what's built swept away. */
    private void whip(ServerLevel level) {
        Vec3 c = position();
        level.playSound(null, c.x, c.y + 6.0, c.z, ModSounds.GODZILLA_ATTACK.get(), SoundSource.HOSTILE, 12.0F, 0.7F);
        for (LivingEntity e : victims(level, getBoundingBox().inflate(WHIP_OUTER, 10.0, WHIP_OUTER))) {
            Vec3 local = localOf(e.position());
            double d = local.horizontalDistance();
            if (d > WHIP_INNER && d < WHIP_OUTER && local.z < 20.0 && e.getY() < getY() + 30.0) {
                strike(e, (float) getAttributeValue(Attributes.ATTACK_DAMAGE), e.position().subtract(c), 3.0, 1.0);
            }
        }
        if (breaksBlocks(level)) {
            Destruction.tailSweep(level, this, c, WHIP_INNER, WHIP_OUTER, 24);
        }
    }

    /** Hurt and fling something (spared: other King Ghidorahs, spectators). */
    private void strike(LivingEntity e, float damage, Vec3 away, double push, double up) {
        if (e.hurt(damageSources().mobAttack(this), damage)) {
            Vec3 h = away.multiply(1, 0, 1);
            h = h.lengthSqr() > 1.0E-6 ? h.normalize() : Vec3.ZERO;
            e.setDeltaMovement(e.getDeltaMovement().add(h.scale(push)).add(0.0, up, 0.0));
            e.hurtMarked = true;
        }
    }

    private List<LivingEntity> victims(ServerLevel level, AABB box) {
        return level.getEntitiesOfClass(LivingEntity.class, box, e -> e != this && e.isAlive() && !e.isSpectator()
                && !(e instanceof KingGhidorahEntity) && e.getVehicle() != this && !(e instanceof ArmorStand));
    }

    private boolean breaksBlocks(ServerLevel level) {
        return Config.GODZILLA_BREAKS_BLOCKS.get() && ForgeEventFactory.getMobGriefingEvent(level, this);
    }

    // ------------------------------------------------------------------ gravity beams, and the lift

    public boolean canFireBeam(LivingEntity target) {
        double d = target.position().subtract(position()).horizontalDistance();
        return d >= BEAM_MIN_RANGE && d <= BEAM_RANGE;
    }

    private boolean clearLine(Vec3 from, Vec3 to) {
        return level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }

    /** Fire his gravity beams at a point (a game test, or the harness; the hunt fires them at his target). */
    public void startBeam(Vec3 aim) {
        this.entityData.set(DATA_AIM, aim.toVector3f());
        this.beamCooldown = BEAM_COOLDOWN;
        startAction(ACTION_BEAM);
    }

    /** Seize something with his beams and lift it up high (a game test, the harness, or the hunt). */
    public void startLift(LivingEntity target) {
        this.entityData.set(DATA_AIM, target.getBoundingBox().getCenter().toVector3f());
        this.lifted = target;
        this.liftedFromY = target.getY();
        this.liftCooldown = LIFT_COOLDOWN;
        this.beamCooldown = Math.max(this.beamCooldown, BEAM_COOLDOWN / 2);
        startAction(ACTION_LIFT);
    }

    private void beamTick(ServerLevel level, int action, int t, @Nullable LivingEntity target) {
        LivingEntity follow = action == ACTION_LIFT ? this.lifted : target;
        if (follow != null && follow.isAlive() && t < BEAM_END_AT) {
            // the aim follows what it's after (a step behind, the beams: at once, the lift)
            Vec3 c = follow.getBoundingBox().getCenter();
            this.entityData.set(DATA_AIM, (action == ACTION_LIFT ? c : getAim().lerp(c, 0.15)).toVector3f());
        }
        Vec3 aim = getAim();
        if (reached(Head.MIDDLE.fireAt)) {
            // (the sound of all three firing: it roars as long as they do, and dies away as they stop)
            Vec3 m = beamMouth(Head.MIDDLE);
            sound(level, m, ModSounds.KING_GHIDORAH_GRAVITY_BEAM.get(), 25.0F, 1.0F);
        }
        for (Head head : Head.values()) {
            if (reached(head.fireAt)) {
                Vec3 m = beamMouth(head);
                if (action == ACTION_BEAM && breaksBlocks(level)) {
                    this.vaporizing.add(new Vaporize(level, m, beamEnd(head, m, aim)));
                }
            }
            if (!isFiring(head)) {
                continue;
            }
            Vec3 from = beamMouth(head);
            Vec3 end = beamEnd(head, from, aim);
            if (action == ACTION_BEAM && (t - head.fireAt) % 8 == 0) {      // (as hard over his long beam as Minus Zero's)
                zap(level, from, end);
            }
        }
        if (action == ACTION_LIFT && this.lifted != null) {
            lift(level, t);
        }
    }

    /** What his beams touch, and everything round where they strike, is struck down and flung. */
    private void zap(ServerLevel level, Vec3 from, Vec3 to) {
        float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
        AABB box = new AABB(from, to).inflate(VAPORIZE_RADIUS);
        Vec3 d = to.subtract(from);
        double len2 = d.lengthSqr();
        for (LivingEntity e : victims(level, box)) {
            Vec3 c = e.getBoundingBox().getCenter();
            double f = len2 > 0 ? Mth.clamp(c.subtract(from).dot(d) / len2, 0.0, 1.0) : 0.0;
            boolean onBeam = from.add(d.scale(f)).distanceTo(c) < BORE_RADIUS + 1.0 + e.getBbWidth() / 2.0;
            boolean atStrike = c.distanceTo(to) < VAPORIZE_RADIUS + e.getBbWidth() / 2.0;
            if ((onBeam || atStrike) && e.hurt(damageSources().indirectMagic(this, this), damage)) {
                Vec3 away = c.subtract(to);
                e.setDeltaMovement(e.getDeltaMovement().add(away.normalize().scale(0.8)).add(0.0, 1.0, 0.0));
                e.hurtMarked = true;
            }
        }
        level.sendParticles(ParticleTypes.FLASH, to.x, to.y, to.z, 1, 0.0, 0.0, 0.0, 0.0);
    }

    /** His lift: the beams haul what they hold straight up, hold it there, and let go (it falls). */
    private void lift(ServerLevel level, int t) {
        LivingEntity e = this.lifted;
        if (e == null || !e.isAlive()) {
            this.lifted = null;
            return;
        }
        int first = Head.MIDDLE.fireAt;
        if (t < first || t >= BEAM_END_AT) {
            return;
        }
        double rise = e.getY() - this.liftedFromY;
        double vy = rise < LIFT_HEIGHT ? 0.95 : 0.06;
        e.setDeltaMovement(e.getDeltaMovement().multiply(0.3, 0.0, 0.3).add(0.0, vy, 0.0));
        e.hurtMarked = true;
        e.fallDistance = 0.0F;
        if ((t - first) % 20 == 0) {
            e.hurt(damageSources().indirectMagic(this, this), 2.0F);     // (it's the fall that does the harm)
        }
        if (reached(BEAM_END_AT - 1)) {
            e.setDeltaMovement(e.getDeltaMovement().multiply(1.0, 0.0, 1.0).add(0.0, -0.4, 0.0));
            e.hurtMarked = true;
        }
    }

    /** What's being lifted, if anything. */
    @Nullable
    public LivingEntity getLifted() {
        return this.lifted;
    }

    /**
     * Where a gravity beam strikes, every block within {@link #VAPORIZE_RADIUS} goes, and every block within
     * {@link #BORE_RADIUS} of its path there (all but liquids and what can't be broken: bedrock); nearest first, a few
     * thousand a tick, so a strike into a hillside doesn't stall the server.
     */
    private static final class Vaporize {
        private static final int PER_TICK = 2500;
        private final ServerLevel level;
        private final List<BlockPos> blocks = new ArrayList<>();
        private int next;

        Vaporize(ServerLevel level, Vec3 from, Vec3 to) {
            this.level = level;
            BlockPos c = BlockPos.containing(to);
            int r = Mth.ceil(VAPORIZE_RADIUS);
            List<BlockPos> sphere = new ArrayList<>();
            for (BlockPos p : BlockPos.betweenClosed(c.offset(-r, -r, -r), c.offset(r, r, r))) {
                if (p.distSqr(c) <= VAPORIZE_RADIUS * VAPORIZE_RADIUS) {
                    sphere.add(p.immutable());
                }
            }
            sphere.sort(Comparator.comparingDouble(p -> p.distSqr(c)));
            // the bore along the path, from the strike back towards his mouth (only near the strike's end: 40 blocks)
            Vec3 d = from.subtract(to);
            double len = Math.min(d.length(), 40.0);
            Vec3 step = d.normalize();
            for (double s = VAPORIZE_RADIUS; s < len; s += 1.0) {
                BlockPos m = BlockPos.containing(to.add(step.scale(s)));
                int b = Mth.ceil(BORE_RADIUS);
                for (BlockPos p : BlockPos.betweenClosed(m.offset(-b, -b, -b), m.offset(b, b, b))) {
                    if (p.distSqr(m) <= BORE_RADIUS * BORE_RADIUS) {
                        this.blocks.add(p.immutable());
                    }
                }
            }
            this.blocks.addAll(0, sphere);
        }

        /** Vaporize the next lot; true once all of it is gone. */
        boolean run(Entity breaker) {
            int end = Math.min(this.blocks.size(), this.next + PER_TICK);
            for (; this.next < end; this.next++) {
                BlockPos p = this.blocks.get(this.next);
                BlockState state = this.level.getBlockState(p);
                if (state.isAir() || !state.getFluidState().isEmpty() || state.getBlock() instanceof LiquidBlock) {
                    continue;
                }
                if (Destruction.canBreak(this.level, p, state, breaker instanceof LivingEntity l ? l : null)) {
                    this.level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
                }
            }
            return this.next >= this.blocks.size();
        }
    }

    private void tickVaporizing(ServerLevel level) {
        this.vaporizing.removeIf(v -> v.run(this));
    }

    // ------------------------------------------------------------------ flight: the grab, and his retreat

    /** Take off, seize {@code target} in his talons, carry it up and dive, slamming it into the ground. */
    public void startGrab(LivingEntity target) {
        this.prey = target;
        this.slammed = false;
        this.grabCooldown = GRAB_COOLDOWN;
        beginFlight(ACTION_GRAB);
    }

    /** Fly off away from {@code from} (his health low: retreatHealthPercent). */
    public void startRetreat(@Nullable Vec3 from) {
        this.fleeFrom = from != null ? from : position().add(Vec3.directionFromRotation(0.0F, getYRot()).scale(10.0));
        this.retreatCooldown = RETREAT_COOLDOWN;
        beginFlight(ACTION_RETREAT);
    }

    private void beginFlight(int action) {
        this.flight = Flight.TAKEOFF;
        this.flightTicks = 0;
        getNavigation().stop();
        startAction(action);
        setFlying(true);
        level().playSound(null, getX(), getY() + 30.0, getZ(), ModSounds.KING_GHIDORAH_WING_SPREAD.get(), SoundSource.HOSTILE, 16.0F, 1.0F);
        this.flyingSoundIn = 0;
    }

    private void setFlying(boolean flying) {
        this.entityData.set(DATA_FLYING, flying);
        setNoGravity(flying);
        this.noPhysics = flying;        // (his wings and body clear what's below them: he flies over it, not into it)
        if (!flying) {
            this.entityData.set(DATA_CARRYING, false);
        }
    }

    private int groundBelow() {
        return level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(getX()), Mth.floor(getZ()));
    }

    private void flightTick(ServerLevel level, int action, @Nullable LivingEntity target) {
        this.flightTicks++;
        if (--this.flyingSoundIn <= 0) {
            // (his wingbeats, from takeoff, and again as each one ends, for as long as he's up)
            this.flyingSoundIn = FLYING_SOUND_TICKS;
            level.playSound(null, getX(), getY() + 30.0, getZ(), ModSounds.KING_GHIDORAH_FLYING.get(), SoundSource.HOSTILE, 16.0F, 1.0F);
        }
        int ground = groundBelow();
        Vec3 v = Vec3.ZERO;
        switch (this.flight) {
            case TAKEOFF -> {
                v = new Vec3(0.0, 1.1, 0.0);
                if (this.flightTicks >= 18) {
                    next(action == ACTION_GRAB ? Flight.APPROACH : Flight.FLEE);
                }
            }
            case APPROACH -> {
                LivingEntity p = this.prey;
                if (p == null || !p.isAlive() || this.flightTicks > 240) {
                    next(Flight.LAND);
                    break;
                }
                // his talons (his feet) to just over its head
                Vec3 want = new Vec3(p.getX(), p.getY() + p.getBbHeight() + 1.0, p.getZ());
                Vec3 d = want.subtract(position());
                faceTowards(want);
                v = d.length() > FLY_SPEED ? d.normalize().scale(FLY_SPEED) : d;
                if (d.length() < 3.5) {
                    p.stopRiding();
                    p.startRiding(this, true);
                    this.entityData.set(DATA_CARRYING, true);
                    level.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.KING_GHIDORAH_BITE.get(), SoundSource.HOSTILE, 10.0F, 0.85F);
                    next(Flight.ASCEND);
                }
            }
            case ASCEND -> {
                LivingEntity p = this.prey;
                regrip(p);
                double carried = p != null ? p.getBbHeight() : 0.0;
                v = new Vec3(0.0, 0.85, 0.0).add(Vec3.directionFromRotation(0.0F, getYRot()).scale(0.3));
                if (getY() - ground >= GRAB_ALTITUDE + carried || this.flightTicks > 100 || p == null || !p.isAlive()) {
                    next(Flight.DIVE);
                }
            }
            case DIVE -> {
                LivingEntity p = this.prey;
                regrip(p);
                double carried = p != null && p.getVehicle() == this ? p.getBbHeight() : 0.0;
                v = new Vec3(0.0, -2.6, 0.0).add(Vec3.directionFromRotation(0.0F, getYRot()).scale(0.6));
                if (getY() - ground <= carried + 2.0 || this.flightTicks > 60) {
                    slam(level, p);
                    next(Flight.LAND);
                }
            }
            case FLEE -> {
                Vec3 from = this.fleeFrom != null ? this.fleeFrom : position();
                Vec3 away = position().subtract(from).multiply(1, 0, 1);
                away = away.lengthSqr() > 1.0E-4 ? away.normalize() : Vec3.directionFromRotation(0.0F, getYRot());
                double climb = Mth.clamp((ground + 50.0 - getY()) * 0.05, -0.6, 0.9);
                v = away.scale(FLY_SPEED).add(0.0, climb, 0.0);
                faceTowards(position().add(away));
                if (this.flightTicks > 320 || position().subtract(from).horizontalDistance() > 220.0) {
                    setTarget(null);
                    next(Flight.LAND);
                }
            }
            case LAND -> {
                v = new Vec3(0.0, getY() - ground > 6.0 ? -1.4 : -0.5, 0.0);
                if (getY() + v.y <= ground) {
                    setPos(getX(), ground, getZ());
                    setFlying(false);
                    setDeltaMovement(Vec3.ZERO);
                    ejectPassengers();
                    this.prey = null;
                    footstep(level, position(), 16.0F);
                    stopFlyingSound(level);
                    ModNetwork.shake(level, position(), 90.0, 1.2F, 1.0F);
                    endAction();
                    return;
                }
                if (this.flightTicks > 200) {
                    setPos(getX(), ground, getZ());       // (somehow still up: down he comes)
                }
            }
        }
        setDeltaMovement(v);       // (the game moves him by it next tick: he flies through whatever's below, noPhysics)
    }

    /** Down: his wingbeats stop with him (the sound would otherwise run on for up to 8 s), for those in earshot. */
    private void stopFlyingSound(ServerLevel level) {
        ClientboundStopSoundPacket stop = new ClientboundStopSoundPacket(ModSounds.KING_GHIDORAH_FLYING.getId(), SoundSource.HOSTILE);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(this) < 400.0 * 400.0) {
                player.connection.send(stop);
            }
        }
    }

    private void next(Flight phase) {
        this.flight = phase;
        this.flightTicks = 0;
    }

    /** Keep hold of what he's carrying (a player can't wriggle free by sneaking). */
    private void regrip(@Nullable LivingEntity p) {
        if (p != null && p.isAlive() && p.getVehicle() != this && p.distanceTo(this) < 20.0 + p.getBbHeight()) {
            p.startRiding(this, true);
        }
    }

    /** At the bottom of his dive he lets go: what he carried is dashed into the ground. */
    private void slam(ServerLevel level, @Nullable LivingEntity p) {
        Vec3 at = position();
        if (p != null && p.isAlive()) {
            p.stopRiding();
            at = p.position();
            p.setDeltaMovement(0.0, -2.5, 0.0);
            p.hurtMarked = true;
            p.hurt(damageSources().mobAttack(this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE));
        }
        this.slammed = true;
        this.entityData.set(DATA_CARRYING, false);
        level.explode(this, at.x, at.y, at.z, 5.0F, Level.ExplosionInteraction.NONE);
        level.playSound(null, at.x, at.y, at.z, ModSounds.GODZILLA_STOMP.get(), SoundSource.HOSTILE, 16.0F, 0.5F);
        ModNetwork.shake(level, at, 100.0, 2.0F, 1.5F);
        for (LivingEntity e : victims(level, new AABB(at, at).inflate(12.0, 6.0, 12.0))) {
            if (e != p) {
                strike(e, (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.25F, e.position().subtract(at), 1.5, 0.8);
            }
        }
    }

    /** Whether his last dive slammed what he carried into the ground (game tests). */
    public boolean hasSlammed() {
        return this.slammed;
    }

    private void faceTowards(Vec3 point) {
        double dx = point.x - getX();
        double dz = point.z - getZ();
        if (dx * dx + dz * dz < 1.0E-4) {
            return;
        }
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        float turned = Mth.approachDegrees(getYRot(), yaw, 6.0F);
        setYRot(turned);
        this.yBodyRot = turned;
        this.yHeadRot = turned;
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction move) {
        // held in his talons, hanging below him
        Vec3 fwd = Vec3.directionFromRotation(0.0F, this.yBodyRot);
        move.accept(passenger, getX() + fwd.x * 2.0, getY() - passenger.getBbHeight() + 1.5, getZ() + fwd.z * 2.0);
    }

    @Override
    public boolean shouldRiderSit() {
        return false;
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        return null;     // (what he carries doesn't steer him)
    }

    // ------------------------------------------------------------------ walking through things

    private void smashBlocks(ServerLevel level) {
        if (!breaksBlocks(level)) {
            return;
        }
        Vec3 forward = Vec3.directionFromRotation(0.0F, getYRot());
        // (his legs and body: half his height; his necks and raised wings rise above what he walks through)
        AABB box = getBoundingBox().expandTowards(forward.x * 4.0, 0.0, forward.z * 4.0).inflate(1.0, 0.0, 1.0);
        int minY = Math.max(Mth.floor(getY()) + 1, walkFloorY());
        int maxY = Mth.floor(getY() + REAL_HEIGHT * 0.55);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int broken = 0;
        for (int y = minY; y <= maxY; y++) {
            for (int x = Mth.floor(box.minX); x <= Mth.floor(box.maxX); x++) {
                for (int z = Mth.floor(box.minZ); z <= Mth.floor(box.maxZ); z++) {
                    pos.set(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir() || state.getBlock() instanceof LiquidBlock || state.getFluidState().is(FluidTags.LAVA)) {
                        continue;
                    }
                    if (Destruction.canBreak(level, pos, state, this)) {
                        level.destroyBlock(pos, false, this);
                        if (++broken >= 400) {
                            return;
                        }
                    }
                }
            }
        }
    }

    /** Stride to a point and stop there (the harness; with nothing to hunt). */
    public void walkTo(@Nullable Vec3 at) {
        this.walkTarget = at;
    }

    // ------------------------------------------------------------------ damage, death

    /**
     * Minus Zero's durability: only naval mines hurt him (onlyHurtByNavalMines), /kill and the void aside; nothing
     * does while he rises out of the ground, nor another King Ghidorah.
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurt(source, amount);
        }
        if (inEntrance() || source.getEntity() instanceof KingGhidorahEntity) {
            return false;
        }
        if (!source.is(ModDamageTypes.NAVAL_MINE) && Config.GODZILLA_ONLY_HURT_BY_EXPLOSIVES.get()) {
            return false;
        }
        return super.hurt(source, amount);
    }

    /** Called by {@link Blasts#navalMine}: as for Minus Zero, a flat share of his max health (mineDamagePercent). */
    public void hitByNavalMine(DamageSource source) {
        if (inEntrance()) {
            return;
        }
        float amount = (float) (getMaxHealth() * Config.MINE_DAMAGE_PERCENT.get() / 100.0);
        this.invulnerableTime = 0;
        if (amount > 0.0F) {
            hurt(source, amount);
        }
    }

    /** Whether he should fly off: his health low, retreats on, and not just back from a retreat. */
    boolean shouldRetreat() {
        return Config.GHIDORAH_RETREATS.get() && this.retreatCooldown <= 0
                && getHealth() <= getMaxHealth() * Config.GHIDORAH_RETREAT_HEALTH_PERCENT.get() / 100.0;
    }

    @Override
    public void die(DamageSource source) {
        ejectPassengers();
        if (isFlying() && !level().isClientSide) {
            setFlying(false);
            setNoGravity(false);
        }
        super.die(source);
    }

    /** Brought down, he collapses (animation.king_ghidorah.death, 3.6 s) before he's gone. */
    @Override
    protected void tickDeath() {
        this.deathTime++;
        if (this.deathTime >= 72 && !level().isClientSide && !isRemoved()) {
            level().broadcastEntityEvent(this, (byte) 60);
            remove(RemovalReason.KILLED);
        }
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 240;
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return inEntrance() ? null : ModSounds.KING_GHIDORAH_IDLE.get();     // (not a peep from under the ground)
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.KING_GHIDORAH_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.KING_GHIDORAH_DEATH.get();
    }

    @Override
    protected float getSoundVolume() {
        return 12.0F;
    }

    /** Not the game's: his footsteps come as his feet come down in his walk (walkFootstep). */
    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
    }

    /**
     * On each client, every tick: his walk's own clock, which his walk animation (WalkClockController) and its footsteps
     * both go by, so each footstep comes as a hind foot comes down (WALK_FOOTFALLS), the next of his three in turn so no
     * two running sound the same. It runs whether he's being drawn or not (GeckoLib only animates what's drawn), so he's
     * heard walking behind you too. Walking is what GeckoLib's own test takes it to be (moving, not doing anything else).
     */
    private void walkClockTick() {
        Vec3 v = getDeltaMovement();
        boolean walking = !isFlying() && !isDeadOrDying() && !isBusy() && this.walkAnimation.speed() != 0.0F
                && (Math.abs(v.x) + Math.abs(v.z)) / 2.0 >= 0.015;
        if (!walking) {
            this.walkingFor = -1;
            this.walkClock = 0.0;
            this.walkClockSpeed = 0.0;
            this.groundSpeed = 0.0;
            return;
        }
        if (++this.walkingFor < MAIN_TRANSITION) {
            strideSpeed();
            return;         // (blending into his walk first: it starts once that's done)
        }
        double before = this.walkClock;
        this.walkClockSpeed = strideSpeed();
        this.walkClock += this.walkClockSpeed;
        for (int foot : WALK_FOOTFALLS) {
            if (Math.floor((before - foot) / WALK_TICKS) != Math.floor((this.walkClock - foot) / WALK_TICKS)) {
                level().playLocalSound(getX(), getY(), getZ(), footstepSound(this.walkSteps++), SoundSource.HOSTILE, 10.0F, 1.0F, false);
            }
        }
    }

    /** Where in his stride he is on this client, {@code partialTick} on from the last tick (animation ticks, 0 to WALK_TICKS). */
    double walkClock(double partialTick) {
        if (this.walkingFor < MAIN_TRANSITION) {
            return 0.0;
        }
        double t = (this.walkClock + partialTick * this.walkClockSpeed) % WALK_TICKS;
        return t < 0.0 ? t + WALK_TICKS : t;
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("BeamCooldown", this.beamCooldown);
        // (his entrance is had once: a reload neither replays it nor loses one under way)
        tag.putBoolean("EntranceDone", this.entranceDone || this.wantsEntrance);
        if (inEntrance()) {
            tag.putInt("EntranceTicks", getActionTicks());
        }
        tag.putLong("StormAt", this.stormAt);
        tag.putInt("RetreatCooldown", this.retreatCooldown);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.beamCooldown = tag.getInt("BeamCooldown");
        this.entranceDone = tag.getBoolean("EntranceDone");
        this.wantsEntrance = false;
        if (tag.contains("EntranceTicks")) {
            this.entranceYaw = getYRot();
            this.entityData.set(DATA_ACTION_START, (int) level().getGameTime() - tag.getInt("EntranceTicks"));
            this.actionTickedTo = tag.getInt("EntranceTicks");     // (what of it was had before isn't had again)
            this.entityData.set(DATA_ACTION, ACTION_ENTRANCE);
        }
        this.stormAt = tag.contains("StormAt") ? tag.getLong("StormAt") : -1L;
        this.retreatCooldown = tag.getInt("RetreatCooldown");
        // (a flight isn't saved: loaded mid-air, he comes down and carries on from the ground)
        setNoGravity(false);
        this.noPhysics = false;
        if (hasCustomName()) {
            this.bossEvent.setName(getDisplayName());
        }
    }

    // ------------------------------------------------------------------ GeckoLib

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new WalkClockController(this, "main", MAIN_TRANSITION, this::mainAnimation));
        controllers.add(new ServerClockController(this, "action", 4, this::actionAnimation));
    }

    /**
     * How fast his walk goes: as fast as he's actually going over the ground, so his feet keep pace with it
     * (WALK_BLOCKS_PER_TICK at its own pace). (Measured by how far he moves on this client, not his motion: the server sends that after the
     * ground's friction, about half what he covers.)
     */
    private double strideSpeed() {
        double moved = Math.sqrt((getX() - this.xo) * (getX() - this.xo) + (getZ() - this.zo) * (getZ() - this.zo));
        this.groundSpeed += (moved - this.groundSpeed) * 0.35;
        return Mth.clamp(this.groundSpeed / WALK_BLOCKS_PER_TICK, 0.25, 1.6);
    }

    /** How fast his walk is going on this client (1: its own pace), for the harness. */
    public double walkPace() {
        return this.walkClockSpeed;
    }

    private PlayState mainAnimation(AnimationState<KingGhidorahEntity> state) {
        if (isDeadOrDying()) {
            return state.setAndContinue(DEATH);
        }
        if (isFlying()) {
            return state.setAndContinue(isCarrying() ? FLY_CARRY : FLY);
        }
        return state.setAndContinue(state.isMoving() && !isBusy() ? WALK : IDLE);
    }

    private static final RawAnimation[] ACTIONS = {null,
            RawAnimation.begin().thenPlay("animation.king_ghidorah.roar"),
            RawAnimation.begin().thenPlay("animation.king_ghidorah.bite_c"),
            RawAnimation.begin().thenPlay("animation.king_ghidorah.bite_r"),
            RawAnimation.begin().thenPlay("animation.king_ghidorah.bite_l"),
            RawAnimation.begin().thenPlay("animation.king_ghidorah.gravity_beam"),
            RawAnimation.begin().thenPlay("animation.king_ghidorah.gravity_lift"),
            RawAnimation.begin().thenPlay("animation.king_ghidorah.stomp"),
            RawAnimation.begin().thenPlay("animation.king_ghidorah.tail_whip"),
            RawAnimation.begin().thenPlayAndHold("animation.king_ghidorah.entrance"),
            null, null};

    private int playing = ACTION_NONE;

    private PlayState actionAnimation(AnimationState<KingGhidorahEntity> state) {
        int action = getAction();
        if (isDeadOrDying() || action < 0 || action >= ACTIONS.length || ACTIONS[action] == null) {
            this.playing = ACTION_NONE;
            return PlayState.STOP;
        }
        if (action != this.playing) {
            this.playing = action;
            state.getController().forceAnimationReset();
        }
        return state.setAndContinue(ACTIONS[action]);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    /** Plays his walk where his walk's clock is (walkClock: the same clock his footsteps go by), the rest as GeckoLib does. */
    static final class WalkClockController extends AnimationController<KingGhidorahEntity> {
        WalkClockController(KingGhidorahEntity animatable, String name, int transition,
                            AnimationStateHandler<KingGhidorahEntity> handler) {
            super(animatable, name, transition, handler);
        }

        @Override
        protected double adjustTick(double tick) {
            double t = super.adjustTick(tick);
            if (getAnimationState() == State.RUNNING && this.currentAnimation != null
                    && WALK_NAME.equals(this.currentAnimation.animation().name())) {
                return this.animatable.walkClock(tick - Math.floor(tick));
            }
            return t;
        }
    }

    /**
     * Plays what he's doing at the point the server is in it (DATA_ACTION_START): a player who starts watching him
     * halfway through his entrance (or anything else) sees it from there, not from its beginning.
     */
    static final class ServerClockController extends AnimationController<KingGhidorahEntity> {
        ServerClockController(KingGhidorahEntity animatable, String name, int transition,
                              AnimationStateHandler<KingGhidorahEntity> handler) {
            super(animatable, name, transition, handler);
        }

        @Override
        protected double adjustTick(double tick) {
            double t = super.adjustTick(tick);
            if (getAnimationState() == State.RUNNING && this.currentAnimation != null && this.animatable.isBusy()) {
                return Math.max(0.0, this.animatable.getActionTicks(tick - Math.floor(tick)));
            }
            return t;
        }
    }

    // ------------------------------------------------------------------ AI

    /**
     * Whom he hunts, as Minus Zero: every creature (targetsAllMobs), or else players, Shinden fighters, iron golems and
     * hostile mobs; Godzilla too (not other King Ghidorahs, naval mines, unmanned planes, creative or spectating players).
     */
    boolean isValidTarget(LivingEntity entity) {
        if (entity instanceof KingGhidorahEntity || entity instanceof NavalMineEntity || entity instanceof ArmorStand) {
            return false;
        }
        if (entity instanceof ShindenEntity shinden && !shinden.isVehicle()) {
            return false;
        }
        if (entity instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return false;
        }
        return Config.GODZILLA_TARGETS_ALL_MOBS.get() || entity instanceof Player || entity instanceof ShindenEntity
                || entity instanceof Enemy || entity instanceof IronGolem || entity instanceof GodzillaEntity;
    }

    static class Prey extends NearestAttackableTargetGoal<LivingEntity> {
        Prey(KingGhidorahEntity ghidorah) {
            super(ghidorah, LivingEntity.class, 20, true, false, ghidorah::isValidTarget);
        }

        @Override
        protected AABB getTargetSearchArea(double range) {
            return this.mob.getBoundingBox().inflate(range, range / 2.0, range);
        }

        @Override
        public boolean canUse() {
            return !((KingGhidorahEntity) this.mob).inEntrance() && super.canUse();
        }
    }

    /**
     * Strides at his target and fights it: bites what's in reach of a head, stomps what's under him, whips his tails
     * at what's behind him, and from further off fires his beams, lifts it with them, or flies at it and grabs it.
     */
    static class HuntGoal extends Goal {
        private final KingGhidorahEntity ghidorah;

        HuntGoal(KingGhidorahEntity ghidorah) {
            this.ghidorah = ghidorah;
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = this.ghidorah.getTarget();
            return target != null && target.isAlive() || this.ghidorah.isBusy();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            KingGhidorahEntity g = this.ghidorah;
            if (g.isBusy()) {
                g.getNavigation().stop();
                if (!g.isFlying()) {
                    g.getMoveControl().setWantedPosition(g.getX(), g.getY(), g.getZ(), 0.0);
                }
                LivingEntity target = g.getTarget();
                int action = g.getAction();
                if (target != null && (action == ACTION_BEAM || action == ACTION_LIFT)) {
                    g.faceTowards(target.position());
                }
                return;
            }
            LivingEntity target = g.getTarget();
            if (target == null) {
                return;
            }
            g.getLookControl().setLookAt(target, 10.0F, 10.0F);
            if (g.shouldRetreat()) {
                g.startRetreat(target.position());
                return;
            }
            Vec3 local = g.localOf(target.position());
            double d = local.horizontalDistance();
            Head head = g.nearestHead(target.position());
            if (g.biteCooldown <= 0 && g.inBiteReach(target, head)) {
                g.startBite(head);
                return;
            }
            if (g.stompCooldown <= 0 && d < STOMP_RADIUS + 8.0 && local.z > -10.0 && target.getY() < g.getY() + 10.0) {
                g.startStomp();
                return;
            }
            if (g.whipCooldown <= 0 && local.z < -6.0 && d > WHIP_INNER && d < WHIP_OUTER - 6.0) {
                g.startWhip();
                return;
            }
            if (--g.decisionDelay <= 0) {
                g.decisionDelay = 20;
                if (g.grabCooldown <= 0 && d > 20.0 && d < 90.0 && g.random.nextInt(3) == 0) {
                    g.startGrab(target);
                    return;
                }
                if (g.canFireBeam(target) && g.clearLine(g.beamMouth(Head.MIDDLE), target.getBoundingBox().getCenter())) {
                    if (g.liftCooldown <= 0 && g.random.nextInt(5) < 2) {
                        g.faceTowards(target.position());
                        g.startLift(target);
                        return;
                    }
                    if (g.beamCooldown <= 0) {
                        g.faceTowards(target.position());
                        g.startBeam(target.getBoundingBox().getCenter());
                        return;
                    }
                }
            }
            g.getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), 1.0);
        }
    }
}
