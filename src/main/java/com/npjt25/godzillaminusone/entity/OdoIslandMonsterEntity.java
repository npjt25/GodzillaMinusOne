package com.npjt25.godzillaminusone.entity;

import com.npjt25.godzillaminusone.GodzillaMinusOne;
import com.npjt25.godzillaminusone.network.FxPacket;
import com.npjt25.godzillaminusone.network.ModNetwork;
import com.npjt25.godzillaminusone.registry.ModEntities;
import com.npjt25.godzillaminusone.registry.ModSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.keyframe.event.ParticleKeyframeEvent;
import software.bernie.geckolib.core.keyframe.event.SoundKeyframeEvent;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;
import java.util.UUID;

/**
 * The Odo Island Monster: Godzilla Minus One in his earlier, dinosaur-like form (Monster122's Godzillasaurus,
 * about 14 blocks tall).
 *
 * <ul>
 *   <li>Left alone he wanders. Come within {@link #PROXIMITY} blocks and he charges you, biting and stomping.</li>
 *   <li>Spotted, he goes into a frenzy: seen by a player (a player looking at him within {@link #SPOT_RANGE} blocks,
 *       or one in his own sight within {@link #SIGHT_RANGE}), he roars, then runs down whoever spotted him, faster,
 *       biting and stomping harder and twice as often, and doesn't let up for {@link #FRENZY_TICKS} ticks, crashing
 *       through whatever is in his way.</li>
 *   <li>Caught in the beam of a searchlight someone is working, he turns and stares into the light and roars, then
 *       charges the searchlight and smashes it, and then goes after whoever was working it. (A searchlight sweeping
 *       on its own, with nobody at it, doesn't bother him.)</li>
 *   <li>Caught in a nuclear bomb's blast he doesn't die: he collapses, burnt to a crisp, and three minutes later he
 *       rises again as Godzilla.</li>
 * </ul>
 */
public class OdoIslandMonsterEntity extends Monster implements GeoEntity {
    public static final int STATE_CALM = 0;
    public static final int STATE_ANGRY = 1;
    public static final int STATE_BURNT = 2;

    /** How close you can get before he charges. */
    public static final double PROXIMITY = 16.0;
    /** Three minutes lying burnt before he becomes Godzilla. */
    public static final int BURNT_TICKS = 3 * 60 * 20;
    private static final int SPOTLIGHT_RAGE = 60 * 20;
    private static final int PROXIMITY_RAGE = 20 * 20;
    private static final double RUN_SPEED = 1.75;
    /** A player looking at him from this far away (with nothing in between) sets him off... */
    public static final double SPOT_RANGE = 64.0;
    /** ...and so does a player he sees this close. */
    public static final double SIGHT_RANGE = 32.0;
    /** How long a frenzy lasts (renewed every time he's spotted again). */
    public static final int FRENZY_TICKS = 90 * 20;
    private static final UUID FRENZY_SPEED_ID = UUID.fromString("4f3b2a39-6d8e-4f7c-9a55-0d2b8a1f6c01");
    private static final UUID FRENZY_DAMAGE_ID = UUID.fromString("9c1d7e42-3b6a-4d18-8f2e-5a7c0b9e4d02");
    private static final AttributeModifier FRENZY_SPEED = new AttributeModifier(FRENZY_SPEED_ID, "Odo frenzy speed", 0.35,
            AttributeModifier.Operation.MULTIPLY_TOTAL);
    private static final AttributeModifier FRENZY_DAMAGE = new AttributeModifier(FRENZY_DAMAGE_ID, "Odo frenzy damage", 0.6,
            AttributeModifier.Operation.MULTIPLY_TOTAL);

    // Measured on the model (blocks: right, up, forward): where the jaws snap shut as his head lunges down in the
    // bite (animation.odo.bite at 0.75 s), and where his left foot comes down in the stomp (at 1.08 s).
    private static final Vec3 JAWS = new Vec3(0.6, 3.6, 7.2);
    private static final double BITE_RADIUS = 4.5;
    public static final Vec3 LEFT_FOOT = new Vec3(-2.3, 0.0, -0.5);
    private static final double STOMP_RADIUS = 6.0;
    /** Going for a searchlight, a player who comes this close draws him off it. */
    private static final double DISTRACTED_BY_PLAYER = 12.0;
    /** Within this many blocks of a searchlight he can't reach, he tears down whatever it stands on or behind. */
    private static final double TEAR_DOWN_RANGE = 14.0;
    /** Ticks into the bite, the stomp and the roar: the jaws close, the foot lands, the roar is over. */
    private static final int BITE_HIT = 15;
    private static final int STOMP_HIT = 22;
    private static final int ROAR_LENGTH = 110;
    /** animation.odo.roar opens his jaws 1.5 s in: that's when the roar's sound goes out. */
    private static final int ROAR_SOUND_AT = ROAR_LENGTH - 30;
    /** His roars carry this far; from {@link #FAR_ROAR_DISTANCE} on, listeners hear the distant roar (odo.roar_far). */
    public static final double ROAR_RANGE = 400.0;
    public static final double FAR_ROAR_DISTANCE = 100.0;
    /** Hunting a searchlight, if he's made no headway for this long he tears down whatever's in the way... */
    private static final int STUCK_TICKS = 40;
    /** ...and if he still can't get at it after this long, he gives up on the light and goes for whoever worked it. */
    private static final int GIVE_UP_TICKS = 30 * 20;

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.odo.idle");
    private static final RawAnimation IDLE_ANGRY = RawAnimation.begin().thenLoop("animation.odo.idle_angry");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.odo.walk");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("animation.odo.run");
    private static final RawAnimation BURNT = RawAnimation.begin().thenPlayAndHold("animation.odo.burnt");
    private static final RawAnimation BITE = RawAnimation.begin().thenPlay("animation.odo.bite");
    private static final RawAnimation BITE2 = RawAnimation.begin().thenPlay("animation.odo.bite2");
    private static final RawAnimation STOMP = RawAnimation.begin().thenPlay("animation.odo.stomp");
    private static final RawAnimation ROAR = RawAnimation.begin().thenPlay("animation.odo.roar");

    private static final EntityDataAccessor<Integer> DATA_STATE = SynchedEntityData.defineId(OdoIslandMonsterEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_RUNNING = SynchedEntityData.defineId(OdoIslandMonsterEntity.class, EntityDataSerializers.BOOLEAN);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int rageTicks;
    private int frenzyTicks;
    /** Whoever set off his frenzy: he keeps coming for them, even out of sight. */
    @Nullable
    private LivingEntity frenzyTarget;
    private int burntTicks;
    private int attackCooldown;
    private int roarTicks;
    private int roarCooldown;
    /** The sound the roar under way makes (odo.roar, or odo.spotted when a searchlight caught him). */
    @Nullable
    private SoundEvent roarSound;
    // headway towards the hunted searchlight: the closest he's come (horizontally) and how long since he got closer
    private double lightBest = Double.MAX_VALUE;
    private int lightStuckTicks;
    private int pendingHitTicks = -1;
    private boolean pendingStomp;
    /** The searchlight that caught him in its beam: he's going to smash it. */
    @Nullable
    private SearchlightEntity huntedLight;
    /** Whoever was working it: he goes after them next. */
    @Nullable
    private LivingEntity lightOperator;
    // client side: when he was burnt (for the embers cooling)
    private int burntSinceTick = -1;

    public OdoIslandMonsterEntity(EntityType<? extends OdoIslandMonsterEntity> type, Level level) {
        super(type, level);
        this.xpReward = 60;
        this.setMaxUpStep(2.5F);
        this.setPersistenceRequired();
        // far too big for vanilla pathfinding: he heads straight for where he's going (like Godzilla)
        this.moveControl = new GodzillaEntity.GodzillaMoveControl(this, 10.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 600.0D)
                .add(Attributes.ATTACK_DAMAGE, 30.0D)
                .add(Attributes.ATTACK_KNOCKBACK, 2.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.27D)
                .add(Attributes.FOLLOW_RANGE, 96.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.ARMOR, 16.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_STATE, STATE_CALM);
        this.entityData.define(DATA_RUNNING, false);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (DATA_STATE.equals(accessor) && isBurnt()) {
            this.burntSinceTick = this.tickCount;
        }
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new LightGoal(this));
        this.goalSelector.addGoal(1, new ChargeGoal(this));
        this.goalSelector.addGoal(5, new WanderGoal(this));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 24.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
                p -> p.distanceTo(this) < PROXIMITY + 4.0));
    }

    // ------------------------------------------------------------------ state

    public int getState() {
        return this.entityData.get(DATA_STATE);
    }

    public boolean isBurnt() {
        return getState() == STATE_BURNT;
    }

    public boolean isRunning() {
        return this.entityData.get(DATA_RUNNING);
    }

    /**
     * Client side, as a player's shape (Walkers / ReMorphed: entity.PlayerOdo; Walkers draws it in their place but never
     * runs it): where the player is (so its sounds come from them), running or walking as they are.
     */
    public void prepareAsShape(Player player, boolean running) {
        setPosRaw(player.getX(), player.getY(), player.getZ());
        if (running != isRunning()) {
            this.entityData.set(DATA_RUNNING, running);
        }
    }

    /** Seconds since he was burnt (client side), for the embers in his hide cooling down. */
    public float getBurntSeconds(float partialTick) {
        return this.burntSinceTick < 0 ? 0.0F : (this.tickCount - this.burntSinceTick + partialTick) / 20.0F;
    }

    public int getBurntTicksLeft() {
        return this.burntTicks;
    }

    /** For the game tests: how long until he rises as Godzilla. */
    public void setBurntTicksLeft(int ticks) {
        this.burntTicks = ticks;
    }

    private void setState(int state) {
        this.entityData.set(DATA_STATE, state);
    }

    /**
     * Caught in the beam of a searchlight someone is working: he goes into a frenzy, stares into the light and roars,
     * then charges it and smashes it ({@link LightGoal}); then he goes after whoever was working it.
     */
    public void spottedBy(SearchlightEntity light, @Nullable LivingEntity operator) {
        if (isBurnt() || !isAlive() || level().isClientSide) {
            return;
        }
        this.frenzyTicks = FRENZY_TICKS;
        setFrenzyModifiers(true);
        this.rageTicks = Math.max(this.rageTicks, SPOTLIGHT_RAGE);
        setState(STATE_ANGRY);
        if (operator != null && operator.isAlive()) {
            this.lightOperator = operator;
            this.frenzyTarget = operator;
        }
        if (this.huntedLight == light) {
            return; // (already on his way to it)
        }
        this.huntedLight = light;
        this.lightBest = Double.MAX_VALUE;
        this.lightStuckTicks = 0;
        setTarget(null);
        lookAtLight(light, 180.0F);
        startRoar(ModSounds.ODO_SPOTTED.get()); // caught in the light: once, as he turns on it
    }

    /** The searchlight he's going to smash, if any. */
    @Nullable
    public SearchlightEntity getHuntedLight() {
        SearchlightEntity light = this.huntedLight;
        return light != null && light.isAlive() ? light : null;
    }

    /** Turns (up to {@code maxTurn} degrees) to face the light, and looks into it. */
    private void lookAtLight(SearchlightEntity light, float maxTurn) {
        double dx = light.getX() - getX();
        double dz = light.getZ() - getZ();
        float yaw = Mth.approachDegrees(getYRot(), (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F, maxTurn);
        setYRot(yaw);
        this.yBodyRot = yaw;
        this.yHeadRot = yaw;
        getLookControl().setLookAt(light.getX(), light.getY() + SearchlightEntity.LENS_HEIGHT, light.getZ(), 30.0F, 30.0F);
    }

    boolean inReachOfLight(SearchlightEntity light) {
        return inReachOfJaws(light) || light.getBoundingBox().getCenter().distanceTo(at(LEFT_FOOT)) < STOMP_RADIUS + 1.0;
    }

    private boolean inReachOfJaws(Entity entity) {
        AABB box = entity.getBoundingBox();
        return box.inflate(BITE_RADIUS).contains(at(JAWS)) || box.getCenter().distanceTo(at(JAWS)) < BITE_RADIUS + 2.0;
    }

    /** A bite (or, if it's under his feet, a stomp) at the searchlight: it lands on {@link #smashLight}. */
    private void attackLight(SearchlightEntity light) {
        boolean stomp = !inReachOfJaws(light);
        this.pendingStomp = stomp;
        this.pendingHitTicks = stomp ? STOMP_HIT : BITE_HIT;
        this.attackCooldown = stomp ? 22 : 12;
        triggerAnim("action", stomp ? "stomp" : "bite2");
    }

    /** His jaws (or foot) come down on the searchlight: it's smashed, and he turns on whoever was working it. */
    public void smashLight() {
        SearchlightEntity light = getHuntedLight();
        this.huntedLight = null;
        if (light != null) {
            light.smash(this);
        }
        LivingEntity operator = this.lightOperator;
        this.lightOperator = null;
        if (operator != null && operator.isAlive()) {
            frenzy(operator, true);
        }
    }

    public boolean isFrenzied() {
        return this.frenzyTicks > 0;
    }

    /** Whether {@code player} has spotted him (looking at him from within {@link #SPOT_RANGE}) or he has spotted them
     * (within {@link #SIGHT_RANGE}), with nothing in between; if so, he goes into a frenzy after them. */
    public boolean noticePlayer(Player player) {
        if (isBurnt() || !isAlive() || !player.isAlive() || player.isCreative() || player.isSpectator()) {
            return false;
        }
        double distance = distanceTo(player);
        if (distance > SPOT_RANGE) {
            return false;
        }
        boolean spotted = distance < SIGHT_RANGE && hasLineOfSight(player);
        if (!spotted) {
            // looking at him: his middle within the angle he fills in the player's view (he's 14 blocks tall)
            Vec3 to = new Vec3(getX() - player.getX(), getY(0.5) - player.getEyeY(), getZ() - player.getZ());
            double d = Math.max(to.length(), 1.0);
            double within = Math.cos(Math.atan(Math.max(getBbWidth(), getBbHeight()) * 0.5 / d));
            spotted = player.getViewVector(1.0F).dot(to.scale(1.0 / d)) > within && player.hasLineOfSight(this);
        }
        if (spotted) {
            frenzy(player, false);
        }
        return spotted;
    }

    /** Starts (or renews) his frenzy after {@code who}; he only turns on them if {@code retarget}, or if whoever set
     * him off before is gone. */
    private void frenzy(@Nullable LivingEntity who, boolean retarget) {
        if (isBurnt() || level().isClientSide) {
            return;
        }
        this.frenzyTicks = FRENZY_TICKS;
        boolean busy = this.frenzyTarget != null && this.frenzyTarget.isAlive() && this.frenzyTarget != who;
        if (who != null && (retarget || !busy) && who.isAlive() && !(who instanceof Player p && (p.isSpectator() || p.isCreative()))) {
            this.frenzyTarget = who;
            if (getTarget() != who) {
                setTarget(who);
            }
        }
        enrage(FRENZY_TICKS);
        setFrenzyModifiers(true);
    }

    private void setFrenzyModifiers(boolean on) {
        AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
        AttributeInstance damage = getAttribute(Attributes.ATTACK_DAMAGE);
        if (speed == null || damage == null) {
            return;
        }
        if (on) {
            if (!speed.hasModifier(FRENZY_SPEED)) {
                speed.addTransientModifier(FRENZY_SPEED);
            }
            if (!damage.hasModifier(FRENZY_DAMAGE)) {
                damage.addTransientModifier(FRENZY_DAMAGE);
            }
        } else {
            speed.removeModifier(FRENZY_SPEED_ID);
            damage.removeModifier(FRENZY_DAMAGE_ID);
        }
    }

    /** Caught in a nuclear blast: he doesn't die, he collapses, burnt to a crisp (then rises as Godzilla). */
    public void nuke() {
        if (isBurnt() || level().isClientSide) {
            return;
        }
        setState(STATE_BURNT);
        this.burntTicks = BURNT_TICKS;
        this.rageTicks = 0;
        this.frenzyTicks = 0;
        this.frenzyTarget = null;
        setFrenzyModifiers(false);
        this.pendingHitTicks = -1;
        this.entityData.set(DATA_RUNNING, false);
        setTarget(null);
        setHealth(getMaxHealth());
        clearFire();
        this.roarTicks = 0;
        this.roarSound = null;
        if (level() instanceof ServerLevel level) {
            GodzillaEntity.broadcast(level, getX(), getY() + 6.0, getZ(), ModSounds.ODO_NUKED.get(), null, 0.0, ROAR_RANGE, 1.0F,
                    this.random.nextLong());
        }
    }

    private void enrage(int ticks) {
        boolean wasCalm = this.rageTicks <= 0;
        this.rageTicks = Math.max(this.rageTicks, ticks);
        setState(STATE_ANGRY);
        if (wasCalm && this.roarCooldown <= 0) {
            startRoar(ModSounds.ODO_ROAR.get());
        }
    }

    private void startRoar(SoundEvent sound) {
        this.roarTicks = ROAR_LENGTH;
        this.roarCooldown = 200;
        this.roarSound = sound;
        triggerAnim("action", "roar");
    }

    /**
     * Sends his roar out (once per roar) to everyone within {@link #ROAR_RANGE} blocks: up to {@link #FAR_ROAR_DISTANCE}
     * the roar itself, further off the distant roar.
     */
    private void roarOut(ServerLevel level, SoundEvent sound) {
        GodzillaEntity.broadcast(level, getX(), getY() + 8.0, getZ(), sound, ModSounds.ODO_ROAR_FAR.get(), FAR_ROAR_DISTANCE,
                ROAR_RANGE, 1.0F, this.random.nextLong());
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        LivingEntity old = getTarget();
        super.setTarget(target);
        if (target != null && target != old && !level().isClientSide && !isBurnt()) {
            enrage(PROXIMITY_RAGE);
        }
    }

    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || isBurnt() || this.roarTicks > 0 || (this.pendingHitTicks >= 0 && this.pendingStomp);
    }

    // ------------------------------------------------------------------ ticking

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            if (isBurnt() && this.random.nextInt(3) == 0) {
                EffectHooks.particleKeyframe.spawn(this, "odo_smoke", "");
            }
            return;
        }
        if (isBurnt()) {
            if (--this.burntTicks <= 0) {
                becomeGodzilla(level);
            }
            return;
        }
        if (this.attackCooldown > 0) {
            this.attackCooldown--;
        }
        if (this.roarTicks > 0) {
            this.roarTicks--;
            if (this.roarTicks == ROAR_SOUND_AT && this.roarSound != null) {
                roarOut(level, this.roarSound);
                this.roarSound = null;
            }
        }
        if (this.roarCooldown > 0) {
            this.roarCooldown--;
        }
        LivingEntity target = getTarget();
        if (target != null && !target.isAlive()) {
            setTarget(null);
            target = null;
        }
        if (this.rageTicks > 0) {
            this.rageTicks--;
        }
        if (this.frenzyTicks > 0) {
            if (--this.frenzyTicks <= 0 || this.frenzyTarget != null && !this.frenzyTarget.isAlive()) {
                this.frenzyTicks = 0;
                this.frenzyTarget = null;
                setFrenzyModifiers(false);
            } else if (target == null && this.frenzyTarget != null && distanceTo(this.frenzyTarget) < getAttributeValue(Attributes.FOLLOW_RANGE)) {
                // out of sight isn't out of mind
                setTarget(this.frenzyTarget);
                target = this.frenzyTarget;
            }
        }
        if (this.tickCount % 10 == 0) {
            for (Player player : level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(SPOT_RANGE))) {
                if (noticePlayer(player)) {
                    break;
                }
            }
        }
        if (this.rageTicks <= 0 && target == null && getState() == STATE_ANGRY) {
            setState(STATE_CALM);
        }
        if (this.horizontalCollision && this.tickCount % 5 == 0 && ForgeEventFactory.getMobGriefingEvent(level, this)) {
            crashThrough(level);
        }
        SearchlightEntity hunted = getHuntedLight();
        if (hunted != null && this.tickCount % 10 == 0 && this.roarTicks <= 0) {
            // a player coming up close draws him off the light, onto them
            Player near = level.getNearestPlayer(getX(), getY(), getZ(), DISTRACTED_BY_PLAYER,
                    p -> p.isAlive() && !p.isSpectator() && !((Player) p).isCreative() && p != this.lightOperator
                            && p.getVehicle() != this.huntedLight && hasLineOfSight(p));
            if (near != null) {
                this.huntedLight = null;
                this.lightOperator = null;
                frenzy(near, true);
                hunted = null;
            }
        }
        boolean stuck = false;
        if (hunted != null && this.roarTicks <= 0) {
            // making headway? (a wall, a cliff or a building in the way: he's not)
            double d = Math.sqrt(hunted.position().subtract(position()).horizontalDistanceSqr());
            if (d < this.lightBest - 0.5 || inReachOfLight(hunted)) {
                this.lightBest = Math.min(this.lightBest, d);
                this.lightStuckTicks = 0;
            } else {
                this.lightStuckTicks++;
            }
            stuck = this.lightStuckTicks >= STUCK_TICKS || this.horizontalCollision;
            if (this.lightStuckTicks >= GIVE_UP_TICKS) {
                // nothing he can break gets him to it (it's up a cliff, say): on to whoever was working it
                LivingEntity operator = this.lightOperator;
                this.huntedLight = null;
                this.lightOperator = null;
                this.lightStuckTicks = 0;
                hunted = null;
                if (operator != null && operator.isAlive()) {
                    frenzy(operator, true);
                }
            }
        }
        if (hunted != null && this.tickCount % 5 == 0 && !inReachOfLight(hunted) && ForgeEventFactory.getMobGriefingEvent(level, this)
                && (stuck || hunted.position().distanceTo(position()) < TEAR_DOWN_RANGE)) {
            tearDownTowards(level, hunted);
        }
        if (this.huntedLight != null && !this.huntedLight.isAlive()) {
            // someone else broke it (or knocked it back into its item): straight on to whoever was working it
            this.huntedLight = null;
            if (this.lightOperator != null && this.lightOperator.isAlive()) {
                frenzy(this.lightOperator, true);
            }
            this.lightOperator = null;
        }
        if (this.pendingHitTicks >= 0 && --this.pendingHitTicks < 0) {
            SearchlightEntity light = getHuntedLight();
            if (light != null && inReachOfLight(light)) {
                if (this.pendingStomp) {
                    stomp(level);
                }
                smashLight();
            } else if (this.pendingStomp) {
                stomp(level);
            } else {
                bite(level, target);
            }
        }
        Entity chasing = getHuntedLight() != null ? getHuntedLight() : target;
        boolean running = chasing != null && distanceTo(chasing) > (isFrenzied() ? 8.0 : 14.0) && this.roarTicks <= 0;
        if (running != isRunning()) {
            this.entityData.set(DATA_RUNNING, running);
        }
    }

    /** Three minutes after the bomb, he rises again: as Godzilla. */
    private void becomeGodzilla(ServerLevel level) {
        GodzillaEntity godzilla = ModEntities.GODZILLA.get().create(level);
        if (godzilla == null) {
            return;
        }
        godzilla.moveTo(getX(), getY(), getZ(), getYRot(), 0.0F);
        godzilla.setYBodyRot(getYRot());
        godzilla.setYHeadRot(getYRot());
        godzilla.awaken();
        level.addFreshEntity(godzilla);
        ModNetwork.sendNear(level, position(), 256.0, new FxPacket(FxPacket.Type.STOMP, position(), 3.0F, 0.0F));
        ModNetwork.shake(level, position(), 160.0, 1.5F, 3.0F);
        GodzillaMinusOne.LOGGER.debug("An Odo Island Monster rose again as Godzilla at {}", blockPosition());
        discard();
    }

    /**
     * The searchlight is on or in a building he can't get his jaws to: he tears through whatever stands between him and
     * it, whatever it's made of (but not the ground), and once he's under it, the building it stands on comes down, and
     * the light with it.
     */
    private void tearDownTowards(ServerLevel level, SearchlightEntity light) {
        Vec3 to = light.position().subtract(position());
        double horizontal = Math.sqrt(to.x * to.x + to.z * to.z);
        AABB box = getBoundingBox().expandTowards(Mth.clamp(to.x, -4.0, 4.0), 0.0, Mth.clamp(to.z, -4.0, 4.0)).inflate(0.5, 0.0, 0.5);
        if (horizontal < BITE_RADIUS + 4.0 && light.getY() > getY()) {
            // close under it: bring down what it stands on
            box = box.minmax(new AABB(light.getX() - 2.0, getY() + 1.0, light.getZ() - 2.0, light.getX() + 2.0, light.getY(), light.getZ() + 2.0));
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int broken = 0;
        for (int y = Mth.floor(box.maxY); y >= Mth.floor(getY()) + 1; y--) {
            for (int x = Mth.floor(box.minX); x <= Mth.floor(box.maxX); x++) {
                for (int z = Mth.floor(box.minZ); z <= Mth.floor(box.maxZ); z++) {
                    BlockState state = level.getBlockState(pos.set(x, y, z));
                    if (state.isAir() || !state.getFluidState().isEmpty() || Destruction.isGround(state)) {
                        continue;
                    }
                    float hardness = state.getDestroySpeed(level, pos);
                    if (hardness >= 0.0F && hardness < 50.0F && Destruction.canBreak(level, pos, state, this)) {
                        level.destroyBlock(pos, false, this);
                        if (++broken >= 80) {
                            return;
                        }
                    }
                }
            }
        }
        if (horizontal < BITE_RADIUS + 4.0 && light.getY() > getY() + 1.0) {
            BlockPos under = BlockPos.containing(light.getX(), light.getY() - 0.5, light.getZ());
            if (level.getBlockState(under).getCollisionShape(level, under).isEmpty()) {
                // nothing left holding it up: it comes down with the building
                smashLight();
            }
        }
    }

    /** Trees, plants, fences and glass in his way give; stone and earth don't. */
    private void crashThrough(ServerLevel level) {
        Vec3 fwd = Vec3.directionFromRotation(0.0F, getYRot());
        AABB box = getBoundingBox().expandTowards(fwd.x * 2.0, 0.0, fwd.z * 2.0).inflate(0.5, 0.0, 0.5);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int broken = 0;
        for (int y = Mth.floor(getY()) + 1; y <= Mth.floor(box.maxY); y++) {
            for (int x = Mth.floor(box.minX); x <= Mth.floor(box.maxX); x++) {
                for (int z = Mth.floor(box.minZ); z <= Mth.floor(box.maxZ); z++) {
                    BlockState state = level.getBlockState(pos.set(x, y, z));
                    if (state.isAir() || !state.getFluidState().isEmpty() || Destruction.isGround(state)) {
                        continue;
                    }
                    float hardness = state.getDestroySpeed(level, pos);
                    float strength = isFrenzied() ? 3.0F : 2.0F; // in a frenzy, wooden doors and planks give too
                    if (hardness >= 0.0F && hardness <= strength && Destruction.canBreak(level, pos, state, this)) {
                        level.destroyBlock(pos, true, this);
                        if (++broken >= 60) {
                            return;
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ attacks

    /** World position of a point on him: {@code side} to the right, {@code up}, {@code forward}. */
    public Vec3 locator(double side, double up, double forward) {
        Vec3 fwd = Vec3.directionFromRotation(0.0F, this.yBodyRot);
        Vec3 right = new Vec3(-fwd.z, 0.0, fwd.x);
        return position().add(fwd.scale(forward)).add(right.scale(side)).add(0.0, up, 0.0);
    }

    private Vec3 at(Vec3 local) {
        return locator(local.x, local.y, local.z);
    }

    boolean inBiteReach(LivingEntity target) {
        Vec3 jaws = at(JAWS);
        return target.getBoundingBox().inflate(BITE_RADIUS).contains(jaws)
                || target.getBoundingBox().getCenter().distanceTo(jaws) < BITE_RADIUS + 2.0;
    }

    boolean inStompReach(LivingEntity target) {
        return target.getBoundingBox().getCenter().distanceTo(at(LEFT_FOOT)) < STOMP_RADIUS + 1.0 && target.getY() < getY() + 4.0;
    }

    private void startAttack(LivingEntity target) {
        boolean stomp = inStompReach(target) && (!inBiteReach(target) || this.random.nextInt(3) == 0);
        this.pendingStomp = stomp;
        this.pendingHitTicks = stomp ? STOMP_HIT : BITE_HIT;
        this.attackCooldown = stomp ? 44 : 24;
        if (isFrenzied()) {
            this.attackCooldown /= 2; // twice as often
        }
        // (like the add-on, he bites one of two ways: the second with a swipe of his arm too)
        triggerAnim("action", stomp ? "stomp" : this.random.nextBoolean() ? "bite" : "bite2");
    }

    private void bite(ServerLevel level, @Nullable LivingEntity target) {
        if (target != null && target.isAlive() && inBiteReach(target)) {
            doHurtTarget(target);
        }
    }

    private void stomp(ServerLevel level) {
        Vec3 foot = at(LEFT_FOOT);
        DamageSource source = damageSources().mobAttack(this);
        float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.7F;
        for (LivingEntity e : Blasts.livingNear(level, foot, STOMP_RADIUS, this)) {
            if (e instanceof OdoIslandMonsterEntity || e instanceof GodzillaEntity) {
                continue;
            }
            e.hurt(source, damage);
            Vec3 push = e.position().subtract(foot).multiply(1.0, 0.0, 1.0).normalize().scale(1.4).add(0.0, 0.7, 0.0);
            e.setDeltaMovement(e.getDeltaMovement().add(push));
            e.hurtMarked = true;
        }
        ModNetwork.shake(level, foot, 48.0, 0.5F, 0.8F);
        ModNetwork.sendNear(level, foot, 128.0, new FxPacket(FxPacket.Type.STOMP, foot, 0.8F, 0.0F));
    }

    // ------------------------------------------------------------------ damage

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isBurnt() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        boolean hurt = super.hurt(source, amount);
        if (hurt && isAlive() && source.getEntity() instanceof Player player) {
            frenzy(player, false);
        }
        return hurt;
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

    // ------------------------------------------------------------------ sounds & saving

    /** Spawned (spawn egg, /summon, a spawner): his arrival cry, heard ~64 blocks around, once. */
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        @Nullable SpawnGroupData data, @Nullable CompoundTag tag) {
        level.getLevel().playSound(null, getX(), getY() + 4.0, getZ(), ModSounds.ODO_SPAWN.get(), SoundSource.HOSTILE, 4.0F, 1.0F);
        return super.finalizeSpawn(level, difficulty, reason, data, tag);
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return isBurnt() ? null : ModSounds.ODO_AMBIENT.get();
    }

    @Override
    public int getAmbientSoundInterval() {
        return 300;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.ODO_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.ODO_DEATH.get();
    }

    @Override
    protected float getSoundVolume() {
        return 5.0F;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        // footsteps come from the walking animation
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("OdoState", getState());
        tag.putInt("BurntTicks", this.burntTicks);
        tag.putInt("RageTicks", this.rageTicks);
        tag.putInt("FrenzyTicks", this.frenzyTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setState(Mth.clamp(tag.getInt("OdoState"), STATE_CALM, STATE_BURNT));
        this.burntTicks = tag.getInt("BurntTicks");
        this.rageTicks = tag.getInt("RageTicks");
        this.frenzyTicks = tag.getInt("FrenzyTicks");
        if (this.frenzyTicks > 0) {
            setFrenzyModifiers(true);
        }
    }

    // ------------------------------------------------------------------ GeckoLib

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, this::mainAnimation)
                .setAnimationSpeedHandler(OdoIslandMonsterEntity::strideSpeed)
                .setSoundKeyframeHandler(this::onSoundKeyframe));
        controllers.add(new AnimationController<>(this, "action", 3, state -> PlayState.STOP)
                .triggerableAnim("bite", BITE)
                .triggerableAnim("bite2", BITE2)
                .triggerableAnim("stomp", STOMP)
                .triggerableAnim("roar", ROAR)
                .setSoundKeyframeHandler(this::onSoundKeyframe)
                .setParticleKeyframeHandler(this::onParticleKeyframe));
    }

    /** Plays the walk and run cycles as fast as he's actually moving, so his feet don't slide. */
    private static double strideSpeed(OdoIslandMonsterEntity odo) {
        if (odo.isBurnt()) {
            return 1.0;
        }
        double speed = odo.getDeltaMovement().horizontalDistance();
        double nominal = odo.isRunning() ? 0.48 : 0.3;   // blocks per tick the cycles are made for (their strides)
        return speed < 0.01 ? 1.0 : Mth.clamp(speed / nominal, 0.5, 1.6);
    }

    private PlayState mainAnimation(AnimationState<OdoIslandMonsterEntity> state) {
        if (isBurnt()) {
            return state.setAndContinue(BURNT);
        }
        if (state.isMoving()) {
            return state.setAndContinue(isRunning() ? RUN : WALK);
        }
        return state.setAndContinue(getState() == STATE_ANGRY ? IDLE_ANGRY : IDLE);
    }

    private void onSoundKeyframe(SoundKeyframeEvent<OdoIslandMonsterEntity> event) {
        String name = event.getKeyframeData().getSound();
        if (name.equals("odo.roar") || name.equals("odo.death")) {
            return; // the server sends his roars (so they carry, and the one that fits: see roarOut) and his nuked cry
        }
        RegistryObject<SoundEvent> sound = ModSounds.BY_NAME.get(name);
        if (sound != null) {
            float volume = name.equals("odo.step") ? 3.0F : 6.0F;
            level().playLocalSound(getX(), getY() + 4.0, getZ(), sound.get(), SoundSource.HOSTILE, volume,
                    0.95F + this.random.nextFloat() * 0.1F, false);
        }
    }

    private void onParticleKeyframe(ParticleKeyframeEvent<OdoIslandMonsterEntity> event) {
        EffectHooks.particleKeyframe.spawn(this, event.getKeyframeData().getEffect(), event.getKeyframeData().getLocator());
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    // ------------------------------------------------------------------ AI

    /** Runs at his target when enraged (walks otherwise) and bites or stomps once in reach. */
    static class ChargeGoal extends Goal {
        private final OdoIslandMonsterEntity odo;

        ChargeGoal(OdoIslandMonsterEntity odo) {
            this.odo = odo;
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = this.odo.getTarget();
            return !this.odo.isBurnt() && target != null && target.isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = this.odo.getTarget();
            if (target == null) {
                return;
            }
            this.odo.getLookControl().setLookAt(target, 30.0F, 30.0F);
            boolean reach = this.odo.inBiteReach(target) || this.odo.inStompReach(target);
            if (reach) {
                turnTowards(target);
                if (this.odo.attackCooldown <= 0 && this.odo.pendingHitTicks < 0 && this.odo.roarTicks <= 0) {
                    this.odo.startAttack(target);
                }
                return;
            }

            double speed = this.odo.rageTicks > 0 && this.odo.distanceTo(target) > (this.odo.isFrenzied() ? 8.0 : 14.0) ? RUN_SPEED : 1.0;
            // aim a little short, so his jaws (10 blocks ahead of his middle) end up on the target
            Vec3 toTarget = target.position().subtract(this.odo.position());
            Vec3 goal = toTarget.lengthSqr() > 100.0 ? target.position().subtract(toTarget.normalize().scale(8.0)) : target.position();
            this.odo.getMoveControl().setWantedPosition(goal.x, goal.y, goal.z, speed);
        }

        private void turnTowards(Entity target) {
            double dx = target.getX() - this.odo.getX();
            double dz = target.getZ() - this.odo.getZ();
            float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
            float newYaw = Mth.approachDegrees(this.odo.getYRot(), yaw, 6.0F);
            this.odo.setYRot(newYaw);
            this.odo.yBodyRot = newYaw;
            this.odo.yHeadRot = newYaw;
        }
    }

    /**
     * Caught in a searchlight's beam: he stares into the light while he roars, then runs at the searchlight and, once
     * it's in reach, smashes it (bite or stomp). Comes before everything else.
     */
    static class LightGoal extends Goal {
        private final OdoIslandMonsterEntity odo;

        LightGoal(OdoIslandMonsterEntity odo) {
            this.odo = odo;
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return !this.odo.isBurnt() && this.odo.getHuntedLight() != null;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            SearchlightEntity light = this.odo.getHuntedLight();
            if (light == null) {
                return;
            }
            if (this.odo.roarTicks > 0) {
                this.odo.lookAtLight(light, 12.0F); // roaring into the glare
                return;
            }
            if (this.odo.inReachOfLight(light)) {
                this.odo.lookAtLight(light, 8.0F);
                if (this.odo.attackCooldown <= 0 && this.odo.pendingHitTicks < 0) {
                    this.odo.attackLight(light);
                }
                return;
            }
            this.odo.getLookControl().setLookAt(light.getX(), light.getY() + SearchlightEntity.LENS_HEIGHT, light.getZ(), 30.0F, 30.0F);
            Vec3 to = light.position().subtract(this.odo.position());
            Vec3 goal = to.lengthSqr() > 100.0 ? light.position().subtract(to.normalize().scale(8.0)) : light.position();
            this.odo.getMoveControl().setWantedPosition(goal.x, goal.y, goal.z, RUN_SPEED);
        }
    }

    /** Calm: now and then strolls somewhere nearby. */
    static class WanderGoal extends Goal {
        private final OdoIslandMonsterEntity odo;
        @Nullable
        private Vec3 destination;
        private int timeout;

        WanderGoal(OdoIslandMonsterEntity odo) {
            this.odo = odo;
            setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (this.odo.isBurnt() || this.odo.getTarget() != null || this.odo.getRandom().nextInt(120) != 0) {
                return false;
            }
            double angle = this.odo.getRandom().nextDouble() * Math.PI * 2.0;
            double dist = 10.0 + this.odo.getRandom().nextDouble() * 24.0;
            this.destination = this.odo.position().add(Math.cos(angle) * dist, 0.0, Math.sin(angle) * dist);
            return true;
        }

        @Override
        public void start() {
            this.timeout = 400;
        }

        @Override
        public boolean canContinueToUse() {
            return this.destination != null && !this.odo.isBurnt() && this.odo.getTarget() == null && --this.timeout > 0
                    && this.odo.position().subtract(this.destination).horizontalDistanceSqr() > 9.0;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (this.destination != null) {
                this.odo.getMoveControl().setWantedPosition(this.destination.x, this.odo.getY(), this.destination.z, 0.8);
            }
        }

        @Override
        public void stop() {
            this.destination = null;
        }
    }
}
