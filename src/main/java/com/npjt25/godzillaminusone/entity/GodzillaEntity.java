package com.npjt25.godzillaminusone.entity;

import com.npjt25.godzillaminusone.Config;
import com.npjt25.godzillaminusone.GodzillaMinusOne;
import com.npjt25.godzillaminusone.network.FxPacket;
import com.npjt25.godzillaminusone.network.ModNetwork;
import com.npjt25.godzillaminusone.registry.ModDamageTypes;
import com.npjt25.godzillaminusone.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
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
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
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
import java.util.List;

/**
 * Godzilla (goji:gojira and its gojira_dead / gojira_dead2 / gojira_spawn1 / gojira_spawn2 stages from the
 * add-on, merged into one entity with a {@link Phase}).
 *
 * <ul>
 *   <li>Spawning plays the emerge + roar sequence before he becomes active.</li>
 *   <li>Radiation aura: "RADIATION POISONING", Wither, and any Shinden that gets too close blows up.</li>
 *   <li>Every footfall shakes the camera, hurts everything around him and crushes a footprint into the ground.</li>
 *   <li>In deep water he swims at the surface, dorsal plates out of the water, and bites at boats.</li>
 *   <li>With a target he charges his atomic breath (the plates light up from the tail to the neck), breathes
 *       in, and fires; where the beam lands a nuclear blast leaves a crater and a mushroom cloud. Then he
 *       needs {@link Config#ATOMIC_BREATH_COOLDOWN} seconds to recharge. A plane in the air always comes first.</li>
 *   <li>Only naval mine blasts hurt him. At zero health he collapses, lies dead, then regenerates.</li>
 * </ul>
 */
public class GodzillaEntity extends Monster implements GeoEntity {
    public enum Phase {
        EMERGING, RISING, ACTIVE, COLLAPSING, DEAD
    }

    public static final int CHARGE_NONE = 0;
    public static final int CHARGE_GLOWING = 1;
    public static final int CHARGE_COOLING = 2;

    /** Ticks GeckoLib blends into a new looping animation (the "main" controller's transition length). */
    public static final int ANIM_TRANSITION = 5;

    // Timings in ticks, taken from the add-on's timers and animation timelines.
    private static final int EMERGE_TICKS = 180;        // gojira_spawn1 timer: 9 s
    private static final int RISE_TICKS = 120;          // gojira_spawn2 timer: 6 s
    private static final int COLLAPSE_TICKS = 100;      // gojira_dead timer: 5 s
    private static final int DEAD_TICKS = 300;          // gojira_dead2 timer: 15 s
    private static final int ANGER_DELAY = 40;          // angry timeline: charge starts at 2.0 s
    private static final int BEAM_START_TICK = 304;     // charge animation: the beam appears at 14.96 s (after the 4 s in-breath)
    private static final int CHARGE_BLAST_TICK = 312;   // ...and lands
    private static final int BEAM_END_TICK = 388;       // charge animation: the beam is gone at 19.17 s
    private static final int CHARGE_LENGTH = 518;       // charge animation: 25.67 s
    private static final int AURA_INTERVAL = 10;        // idle timeline loops every 0.5 s
    private static final int REGEN_INTERVAL = 100;      // calm timeline loops every 5 s
    private static final int STUN_TICKS = 100;          // naval mine: slowness for 5 s

    /** He walks a little slower than the add-on: the walk cycle plays at 70% speed... */
    public static final double WALK_ANIM_SPEED = 0.7;
    /** ...so it lasts 2.5 s / 0.7 = 71 ticks, with the footfalls (0.95 s and 2.15 s) on these ticks. */
    private static final int WALK_CYCLE = 71;
    private static final int RIGHT_STEP = 27;
    private static final int LEFT_STEP = 61;
    /** Where his feet land (blocks right, forward) and the footprint's half width and half length. */
    public static final double FOOT_SIDE = 7.25;
    public static final double FOOT_FORWARD = 3.8;
    private static final double FOOT_HALF_WIDTH = 2.6;
    private static final double FOOT_HALF_LENGTH = 4.6;

    // Swimming: in water at least this deep he floats with his back just under the surface (his belly hangs
    // 12 blocks below his back in the swimming pose).
    private static final double SWIM_START_DEPTH = 17.0;
    private static final double SWIM_STOP_DEPTH = 14.0;
    /** How far below the water surface his feet (the entity position) are while swimming. */
    public static final double SWIM_SINK = 4.3;
    /** Swimming speed relative to walking: fast enough to run down a boat. */
    private static final double SWIM_SPEED = 1.17;
    private static final EntityDimensions SWIM_SIZE = EntityDimensions.scalable(10.0F, 10.0F);
    /** How far ahead of him the swimming bite lands (the jaw in animation.gojira.swim_bite). */
    public static final double SWIM_BITE_REACH = 24.0;

    // The atomic breath, measured on the charge animation (blocks: right, up, forward). The head pivots about
    // HEAD; the beam leaves his mouth at BEAM_NEAR and reaches out to BEAM_FAR: with his head angled 10 degrees up
    // it slopes down ~10 degrees and lands ~160 blocks ahead on flat ground.
    private static final Vec3 HEAD = new Vec3(-0.46, 25.45, 17.48);
    private static final Vec3 BEAM_NEAR = new Vec3(-0.43, 25.10, 16.75);
    private static final Vec3 BEAM_FAR = new Vec3(-8.27, -5.53, 191.96);
    private static final double BEAM_REACH = 185.0;
    /** The beam veers this far left of where he faces, so he turns this far right to aim it. */
    private static final float BEAM_YAW = (float) Math.toDegrees(Math.atan2(7.84, 175.2));
    /** Extra head pitch to aim at a target (degrees, up); never lower than the add-on's original angle. */
    private static final float MIN_AIM = -10.0F;
    private static final float MAX_AIM = 60.0F;

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.gojira.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.gojira.walking");
    private static final RawAnimation SWIM = RawAnimation.begin().thenLoop("animation.gojira.swim2");
    private static final RawAnimation CHARGE = RawAnimation.begin().thenPlayAndHold("animation.gojira.charge");
    private static final RawAnimation EMERGE = RawAnimation.begin().thenPlayAndHold("animation.gojira.spawn");
    private static final RawAnimation RISE = RawAnimation.begin().thenPlayAndHold("animation.gojira.roar2");
    private static final RawAnimation COLLAPSE = RawAnimation.begin().thenPlayAndHold("animation.gojira.dead2");
    private static final RawAnimation LIE_DEAD = RawAnimation.begin().thenLoop("animation.gojira.dead");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("animation.gojira.attack");
    private static final RawAnimation STOMP = RawAnimation.begin().thenPlay("animation.gojira.stomp");
    private static final RawAnimation TAIL = RawAnimation.begin().thenPlay("animation.gojira.tail_attack");
    private static final RawAnimation SWIM_BITE_ANIM = RawAnimation.begin().thenPlay("animation.gojira.swim_bite");
    private static final RawAnimation ROAR = RawAnimation.begin().thenPlay("animation.gojira.roar");

    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(GodzillaEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_CHARGE = SynchedEntityData.defineId(GodzillaEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_AIM = SynchedEntityData.defineId(GodzillaEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_SWIMMING = SynchedEntityData.defineId(GodzillaEntity.class, EntityDataSerializers.BOOLEAN);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final ServerBossEvent bossEvent = new ServerBossEvent(
            Component.translatable("boss." + GodzillaMinusOne.MODID + ".godzilla"),
            BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.PROGRESS);

    private int phaseTicks;
    private int angryTicks;
    private int chargeTicks = -1;
    private int breathCooldown;
    private int attackCooldown;
    private int stunTicks;
    private int walkTicks;
    private int stillTicks;
    private int pendingHitTicks = -1;
    @Nullable
    private Attack pendingAttack;
    private int blackRainTicks = -1;
    private Vec3 blackRainPos = Vec3.ZERO;
    private double swimY;

    // Client side: when the charge state last changed (for the plate glow), and the smoothed head aim.
    private int chargeChangedTick;
    private float aimO;
    private float aimClient;
    private boolean walkAnimation;

    private enum Attack {
        BITE("attack", 10, 20), STOMP("stomp", 25, 65), TAIL("tail", 16, 35), SWIM_BITE("swim_bite", 14, 26);

        final String anim;
        final int hitDelay;
        final int length;

        Attack(String anim, int hitDelay, int length) {
            this.anim = anim;
            this.hitDelay = hitDelay;
            this.length = length;
        }
    }

    public GodzillaEntity(EntityType<? extends GodzillaEntity> type, Level level) {
        super(type, level);
        this.xpReward = 45;
        this.noCulling = true; // the model (tail, atomic breath) reaches far outside the hitbox
        this.setMaxUpStep(4.0F);
        this.moveControl = new GodzillaMoveControl(this);
        this.setPersistenceRequired();
        if (!level.isClientSide) {
            applyConfig();
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 1000.0D)
                .add(Attributes.ATTACK_DAMAGE, 2000.0D)
                .add(Attributes.ATTACK_KNOCKBACK, 3.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.36D)
                .add(Attributes.FOLLOW_RANGE, 128.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.ARMOR, 20.0D);
    }

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
        this.entityData.define(DATA_PHASE, Phase.EMERGING.ordinal());
        this.entityData.define(DATA_CHARGE, CHARGE_NONE);
        this.entityData.define(DATA_AIM, 0.0F);
        this.entityData.define(DATA_SWIMMING, false);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (DATA_CHARGE.equals(accessor)) {
            this.chargeChangedTick = this.tickCount;
        } else if (DATA_SWIMMING.equals(accessor)) {
            refreshDimensions();
        }
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(2, new ChaseGoal(this));
        this.goalSelector.addGoal(6, new WanderGoal(this));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 48.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new GodzillaTargetGoal(this));
    }

    // ------------------------------------------------------------------ state

    public Phase getPhase() {
        int i = this.entityData.get(DATA_PHASE);
        return Phase.values()[Mth.clamp(i, 0, Phase.values().length - 1)];
    }

    public boolean isActive() {
        return getPhase() == Phase.ACTIVE;
    }

    public int getChargeState() {
        return this.entityData.get(DATA_CHARGE);
    }

    public boolean isCharging() {
        return getChargeState() != CHARGE_NONE;
    }

    public boolean isStunned() {
        return this.stunTicks > 0;
    }

    /** Swimming at the surface of deep water (back awash, dorsal plates out). */
    public boolean isSwimmingAtSurface() {
        return this.entityData.get(DATA_SWIMMING);
    }

    /** Seconds into the charge animation (client side), for the dorsal plates lighting up one by one. */
    public float getChargeSeconds(float partialTick) {
        return Math.max(0.0F, this.tickCount - this.chargeChangedTick + partialTick - ANIM_TRANSITION) / 20.0F;
    }

    /** Seconds since the beam ended (client side), for the plates fading out. */
    public float getCoolingSeconds(float partialTick) {
        return Math.max(0.0F, this.tickCount - this.chargeChangedTick + partialTick) / 20.0F;
    }

    /** Extra head pitch (degrees, up) aiming the atomic breath at his target. */
    public float getAimPitch(float partialTick) {
        return level().isClientSide ? Mth.lerp(partialTick, this.aimO, this.aimClient) : this.entityData.get(DATA_AIM);
    }

    /** Whether the walk cycle (played at {@link #WALK_ANIM_SPEED}) is the current animation. */
    public boolean isWalkAnimation() {
        return this.walkAnimation;
    }

    /** Skips the emerge-and-roar entrance (used by the game tests). */
    public void skipIntro() {
        setPhase(Phase.ACTIVE);
    }

    public int getBreathCooldown() {
        return this.breathCooldown;
    }

    private void setChargeState(int state) {
        this.entityData.set(DATA_CHARGE, state);
    }

    private void setSurfaceSwimming(boolean swimming) {
        if (swimming != isSwimmingAtSurface()) {
            this.entityData.set(DATA_SWIMMING, swimming);
        }
    }

    private void setPhase(Phase phase) {
        this.entityData.set(DATA_PHASE, phase.ordinal());
        this.phaseTicks = 0;
        switch (phase) {
            case COLLAPSING -> {
                this.chargeTicks = -1;
                this.pendingHitTicks = -1;
                this.stunTicks = 0;
                setChargeState(CHARGE_NONE);
                this.entityData.set(DATA_AIM, 0.0F);
                setTarget(null);
                this.bossEvent.setVisible(false);
            }
            case DEAD -> this.bossEvent.setVisible(false);
            case EMERGING -> {
                setHealth(getMaxHealth());
                this.bossEvent.setVisible(true);
            }
            case RISING -> this.bossEvent.setVisible(true);
            case ACTIVE -> {
                this.bossEvent.setVisible(true);
                this.angryTicks = 0;
                this.breathCooldown = 0;
            }
        }
    }

    /** Everything that isn't the active phase, charging, or a naval-mine stun keeps him rooted. */
    @Override
    protected boolean isImmobile() {
        boolean rootedByAttack = this.pendingHitTicks >= 0 && this.pendingAttack != Attack.SWIM_BITE;
        return super.isImmobile() || !isActive() || isCharging() || this.stunTicks > 0 || rootedByAttack;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return isSwimmingAtSurface() ? SWIM_SIZE : super.getDimensions(pose);
    }

    // ------------------------------------------------------------------ ticking

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            this.aimO = this.aimClient;
            this.aimClient = Mth.approach(this.aimClient, this.entityData.get(DATA_AIM), 4.0F);
            return;
        }
        this.phaseTicks++;
        this.bossEvent.setProgress(Mth.clamp(getHealth() / getMaxHealth(), 0.0F, 1.0F));
        if (this.tickCount % 4 == 0) {
            updateSwimming(level);
        }

        switch (getPhase()) {
            case EMERGING -> {
                if (this.phaseTicks == 1) {
                    ModNetwork.shake(level, position(), 96.0, 0.5F, 7.0F);
                }
                if (this.phaseTicks >= EMERGE_TICKS) {
                    setPhase(Phase.RISING);
                }
            }
            case RISING -> {
                if (this.phaseTicks == 16) {
                    ModNetwork.shake(level, position(), 128.0, 1.0F, 2.5F);
                }
                if (this.phaseTicks >= RISE_TICKS) {
                    setPhase(Phase.ACTIVE);
                }
            }
            case COLLAPSING -> {
                if (this.phaseTicks == 15) {
                    ModNetwork.shake(level, position(), 128.0, 1.5F, 2.0F);
                    ModNetwork.sendNear(level, position(), 256.0, new FxPacket(FxPacket.Type.STOMP, position(), 3.0F, 0.0F));
                }
                if (this.phaseTicks >= COLLAPSE_TICKS) {
                    setPhase(Phase.DEAD);
                }
            }
            case DEAD -> {
                if (this.phaseTicks >= DEAD_TICKS) {
                    setPhase(Phase.EMERGING);
                }
            }
            case ACTIVE -> activeTick(level);
        }

        if (this.blackRainTicks >= 0 && --this.blackRainTicks < 0) {
            startBlackRain(level);
        }
    }

    private void activeTick(ServerLevel level) {
        if (this.tickCount % 10 == 0) {
            preferPlanes(level);
        }
        LivingEntity target = getTarget();
        if (target != null && (!target.isAlive() || !isValidTarget(target))) {
            setTarget(null);
            target = null;
        }

        if (this.tickCount % AURA_INTERVAL == 0) {
            radiationAura(level);
        }

        if (target == null) {
            this.angryTicks = 0;
            if (this.tickCount % REGEN_INTERVAL == 0 && getHealth() < getMaxHealth()) {
                heal((float) (getMaxHealth() * Config.CALM_REGEN_PERCENT.get() / 100.0));
            }
        } else {
            this.angryTicks++;
        }

        if (this.breathCooldown > 0) {
            this.breathCooldown--;
        }
        if (this.attackCooldown > 0) {
            this.attackCooldown--;
        }
        if (this.stunTicks > 0) {
            this.stunTicks--;
        }

        if (this.chargeTicks >= 0) {
            tickCharge(level, target);
        } else {
            updateAim(null);
            if (target != null && this.angryTicks >= ANGER_DELAY && this.breathCooldown <= 0 && this.stunTicks <= 0
                    && this.pendingHitTicks < 0 && !isSwimmingAtSurface()) {
                this.chargeTicks = 0;
                setChargeState(CHARGE_GLOWING);
            }
        }

        if (this.pendingHitTicks >= 0) {
            if (target != null) {
                faceTowards(target, 3.0F, 0.0F);
            }
            if (--this.pendingHitTicks < 0 && this.pendingAttack != null) {
                performAttack(level, this.pendingAttack, target);
                this.pendingAttack = null;
            }
        }

        double dx = getX() - this.xo;
        double dz = getZ() - this.zo;
        boolean moving = dx * dx + dz * dz > 0.0004;
        if (moving && !isSwimmingAtSurface()) {
            this.stillTicks = 0;
            this.walkTicks++;
            int phase = this.walkTicks - ANIM_TRANSITION;
            if (phase >= 0 && (phase % WALK_CYCLE == RIGHT_STEP || phase % WALK_CYCLE == LEFT_STEP)) {
                footfall(level, phase % WALK_CYCLE == RIGHT_STEP ? 1.0 : -1.0);
            }
        } else if (++this.stillTicks > 6) {
            this.walkTicks = 0;
        }
        if ((moving || this.horizontalCollision) && this.tickCount % 5 == 0) {
            smashBlocks(level);
        }
    }

    /** Whoever is flying a Shinden becomes his target (the plane itself, not its pilot). */
    private void preferPlanes(ServerLevel level) {
        LivingEntity target = getTarget();
        if (target != null && target.getVehicle() instanceof ShindenEntity plane && plane.isAlive()) {
            setTarget(plane);
            return;
        }
        if (target instanceof ShindenEntity) {
            return;
        }
        double range = getAttributeValue(Attributes.FOLLOW_RANGE);
        ShindenEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (ShindenEntity plane : level.getEntitiesOfClass(ShindenEntity.class, getBoundingBox().inflate(range),
                p -> p.isAlive() && p.isVehicle() && isValidTarget(p))) {
            double d = plane.distanceToSqr(this);
            if (d < best && d <= range * range) {
                best = d;
                nearest = plane;
            }
        }
        if (nearest != null) {
            setTarget(nearest);
        }
    }

    /** animation.gojira.idle timeline: radiation poisoning, Wither, and planes that fly too close explode. */
    private void radiationAura(ServerLevel level) {
        int radius = Config.AURA_RADIUS.get();
        if (radius > 0) {
            Component warning = Component.translatable("message." + GodzillaMinusOne.MODID + ".radiation")
                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
            int amplifier = Config.AURA_WITHER_AMPLIFIER.get();
            for (LivingEntity entity : nearby(radius)) {
                if (entity instanceof GodzillaEntity) {
                    continue;
                }
                entity.addEffect(new MobEffectInstance(MobEffects.WITHER, 600, amplifier), this);
                if (entity instanceof Player player) {
                    player.displayClientMessage(warning, true);
                }
            }
            if (this.tickCount % 80 == 0) {
                level.playSound(null, getX(), getY() + 4.0, getZ(), ModSounds.GODZILLA_RADIATION.get(), SoundSource.HOSTILE,
                        Math.max(1.0F, (radius + 8) / 16.0F), 1.0F);
            }
        }

        int kamikaze = Config.SHINDEN_KAMIKAZE_DISTANCE.get();
        if (kamikaze > 0) {
            for (ShindenEntity shinden : level.getEntitiesOfClass(ShindenEntity.class, getBoundingBox().inflate(kamikaze))) {
                shinden.explode();
            }
        }
    }

    /**
     * One of his feet comes down (animation.gojira.walking's footfalls, add-on big_steps timeline): the ground
     * shakes, everything close by is hurt, and what he stepped on is crushed into a footprint.
     */
    private void footfall(ServerLevel level, double side) {
        Vec3 foot = locator(side * FOOT_SIDE, 0.0, FOOT_FORWARD);
        ModNetwork.shake(level, foot, 100.0, 0.3F, 1.2F);
        float damage = Config.STEP_DAMAGE.get().floatValue();
        int radius = Config.STEP_RADIUS.get();
        if (damage > 0 && radius > 0) {
            DamageSource source = ModDamageTypes.source(level, ModDamageTypes.STOMP, this);
            for (LivingEntity entity : nearby(radius)) {
                if (!(entity instanceof GodzillaEntity) && !Blasts.isPilot(entity) && entity.getY() < getY() + 8.0) {
                    entity.hurt(source, damage);
                }
            }
        }
        if (Config.FOOTPRINTS.get() && Config.GODZILLA_BREAKS_BLOCKS.get() && ForgeEventFactory.getMobGriefingEvent(level, this)) {
            Destruction.footprint(level, this, foot, this.yBodyRot, FOOT_HALF_WIDTH, FOOT_HALF_LENGTH);
        }
    }

    // ------------------------------------------------------------------ swimming

    /** Finds the water surface and depth under him; deep water makes him swim at the surface. */
    private void updateSwimming(ServerLevel level) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(getX(), getY() + 0.5, getZ());
        if (!isActive() || !level.getFluidState(pos).is(FluidTags.WATER)) {
            setSurfaceSwimming(false);
            return;
        }
        int y0 = pos.getY();
        int top = y0;
        while (top < y0 + 64 && level.getFluidState(pos.setY(top + 1)).is(FluidTags.WATER)) {
            top++;
        }
        int bed = y0;
        while (bed > level.getMinBuildHeight() && level.getFluidState(pos.setY(bed - 1)).is(FluidTags.WATER)) {
            bed--;
        }
        double surface = top + 0.9;
        this.swimY = surface - SWIM_SINK;
        double depth = surface - bed;
        if (isSwimmingAtSurface()) {
            if (depth < SWIM_STOP_DEPTH || isCharging()) {
                setSurfaceSwimming(false);
            }
        } else if (depth >= SWIM_START_DEPTH && !isCharging()) {
            setSurfaceSwimming(true);
        }
    }

    /** While swimming he glides along the surface with no gravity; otherwise he walks (on the sea floor too). */
    @Override
    public void travel(Vec3 input) {
        if (!isSwimmingAtSurface()) {
            super.travel(input);
            return;
        }
        if (isControlledByLocalInstance()) {
            Vec3 forward = Vec3.directionFromRotation(0.0F, getYRot());
            double speed = Math.max(0.0, input.z) * SWIM_SPEED;
            Vec3 v = getDeltaMovement();
            double vx = Mth.lerp(0.08, v.x, forward.x * speed);
            double vz = Mth.lerp(0.08, v.z, forward.z * speed);
            double vy = Mth.clamp((this.swimY - getY()) * 0.08, -0.25, 0.25);
            setDeltaMovement(vx, vy, vz);
            move(MoverType.SELF, getDeltaMovement());
            resetFallDistance();
        }
        calculateEntityAnimation(false);
    }

    // ------------------------------------------------------------------ atomic breath

    private void tickCharge(ServerLevel level, @Nullable LivingEntity target) {
        this.chargeTicks++;
        if (target != null) {
            if (this.chargeTicks < BEAM_START_TICK) {
                faceTowards(target, 4.0F, BEAM_YAW);
            } else if (this.chargeTicks < BEAM_END_TICK) {
                faceTowards(target, 1.0F, BEAM_YAW); // the beam follows, slowly
            }
        }
        updateAim(this.chargeTicks < BEAM_END_TICK ? target : null);

        if (this.chargeTicks % 4 == 0 && this.chargeTicks < BEAM_START_TICK) {
            // animation.gojira.powered timeline: everything freezes, and it burns to stand close to him.
            int freeze = Config.CHARGE_FREEZE_RADIUS.get();
            if (freeze > 0) {
                for (Mob mob : level.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(freeze),
                        m -> !(m instanceof GodzillaEntity) && !(m instanceof ShindenEntity) && !(m instanceof NavalMineEntity))) {
                    mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 255, false, false), this);
                }
            }
            DamageSource heat = ModDamageTypes.source(level, ModDamageTypes.RADIATION, this);
            for (LivingEntity entity : nearby(20)) {
                if (!(entity instanceof GodzillaEntity) && !Blasts.isPilot(entity)) {
                    entity.hurt(heat, 1.0F);
                }
            }
            ModNetwork.shake(level, position(), 150.0, 0.15F, 0.5F);
        }

        if (this.chargeTicks >= BEAM_START_TICK && this.chargeTicks < BEAM_END_TICK && (this.chargeTicks - BEAM_START_TICK) % 4 == 0) {
            beamHits(level);
        }
        if (this.chargeTicks == CHARGE_BLAST_TICK) {
            fireAtomicBlast(level);
        } else if (this.chargeTicks == BEAM_END_TICK) {
            setChargeState(CHARGE_COOLING);
            triggerAnim("action", "roar");
        } else if (this.chargeTicks >= CHARGE_LENGTH) {
            this.chargeTicks = -1;
            this.angryTicks = 0;
            setChargeState(CHARGE_NONE);
        }
    }

    /** Tilts his head towards the target: up at planes, down a little at things close by. */
    private void updateAim(@Nullable LivingEntity target) {
        float wanted = 0.0F;
        if (target != null) {
            Vec3 mouth = locator(BEAM_NEAR.x, BEAM_NEAR.y, BEAM_NEAR.z);
            Vec3 at = target.getBoundingBox().getCenter();
            double horizontal = Math.max(1.0, Math.sqrt(Mth.square(at.x - mouth.x) + Mth.square(at.z - mouth.z)));
            double slope = Math.toDegrees(Math.atan2(at.y - mouth.y, horizontal));
            double base = Math.toDegrees(Math.atan2(BEAM_FAR.y - BEAM_NEAR.y, BEAM_FAR.z - BEAM_NEAR.z));
            wanted = Mth.clamp((float) (slope - base), MIN_AIM, MAX_AIM);
        }
        float aim = this.entityData.get(DATA_AIM);
        if (aim != wanted) {
            this.entityData.set(DATA_AIM, Mth.approach(aim, wanted, 1.5F));
        }
    }

    /** The beam with his head tilted by {@code aim} degrees: {start, end} in world space ({@link #BEAM_REACH} long). */
    public Vec3[] beamLine(float aim) {
        double a = Math.toRadians(aim);
        double cos = Math.cos(a);
        double sin = Math.sin(a);
        Vec3 near = tiltAboutHead(BEAM_NEAR, cos, sin);
        Vec3 far = tiltAboutHead(BEAM_FAR, cos, sin);
        Vec3 dir = far.subtract(near).normalize();
        Vec3 end = near.add(dir.scale(BEAM_REACH));
        return new Vec3[]{locator(near.x, near.y, near.z), locator(end.x, end.y, end.z)};
    }

    private static Vec3 tiltAboutHead(Vec3 p, double cos, double sin) {
        double up = p.y - HEAD.y;
        double fwd = p.z - HEAD.z;
        return new Vec3(p.x, HEAD.y + up * cos + fwd * sin, HEAD.z + fwd * cos - up * sin);
    }

    /** Where the beam currently ends: the first block it hits, or its far end. */
    private HitResult beamHit(ServerLevel level, Vec3[] line) {
        return level.clip(new ClipContext(line[0], line[1], ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
    }

    /** Anything caught in the beam itself is vaporised; a plane it touches blows up (its pilot bails out). */
    private void beamHits(ServerLevel level) {
        Vec3[] line = beamLine(this.entityData.get(DATA_AIM));
        Vec3 start = line[0];
        Vec3 end = beamHit(level, line).getLocation();
        DamageSource source = ModDamageTypes.source(level, ModDamageTypes.ATOMIC_BREATH, this);
        AABB box = new AABB(start, end).inflate(4.0);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != this && e.isAlive())) {
            if (distanceSqrToSegment(entity.getBoundingBox().getCenter(), start, end) > 16.0) {
                continue;
            }
            if (entity instanceof ShindenEntity plane && plane.isVehicle()) {
                plane.explode();
            } else {
                atomicHit(entity, source);
            }
        }
    }

    /**
     * The nuclear blast where the beam lands: everything nearby dies, radiation, a crater and blast wave, the
     * 3D fireball and mushroom cloud (client.render.NuclearCloud), a blurred view for everyone who saw it, then
     * black rain (particles only: the weather isn't touched).
     */
    private void fireAtomicBlast(ServerLevel level) {
        Vec3[] line = beamLine(this.entityData.get(DATA_AIM));
        HitResult hit = beamHit(level, line);
        Vec3 impact = hit.getLocation();
        boolean grounded = hit.getType() != HitResult.Type.MISS;
        if (!grounded) {
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(impact.x), Mth.floor(impact.z));
            if (impact.y - ground < 16.0) {
                impact = new Vec3(impact.x, ground, impact.z);
                grounded = true;
            }
        }

        DamageSource source = ModDamageTypes.source(level, ModDamageTypes.ATOMIC_BREATH, this);
        for (LivingEntity entity : Blasts.livingNear(level, impact, Config.ATOMIC_BLAST_KILL_RADIUS.get(), this)) {
            atomicHit(entity, source);
        }
        int radiation = Config.ATOMIC_BLAST_RADIATION_RADIUS.get();
        int amplifier = Config.ATOMIC_BLAST_RADIATION_AMPLIFIER.get();
        for (LivingEntity entity : Blasts.livingNear(level, impact, radiation, this)) {
            if (!(entity instanceof GodzillaEntity)) {
                entity.addEffect(new MobEffectInstance(MobEffects.WITHER, 600, amplifier), this);
            }
        }

        // Ground zero: the ground under the impact, not the tree top or roof the beam happened to hit.
        Vec3 zero = impact;
        if (grounded) {
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(impact.x), Mth.floor(impact.z));
            zero = new Vec3(impact.x, Math.min(impact.y, ground), impact.z);
            if (ForgeEventFactory.getMobGriefingEvent(level, this)) {
                Destruction.atomicBlast(level, this, zero, Config.ATOMIC_BLAST_CRATER_RADIUS.get(), Config.ATOMIC_BLAST_DESTRUCTION_RADIUS.get());
            }
        }

        ModNetwork.sendNear(level, zero, 1024.0, new FxPacket(FxPacket.Type.ATOMIC_BLAST, zero, getYRot(), grounded ? 1.0F : 0.0F));
        level.playSound(null, impact.x, impact.y, impact.z, ModSounds.GODZILLA_ATOMIC_BLAST.get(), SoundSource.HOSTILE, 24.0F, 1.0F);
        ModNetwork.shake(level, impact, 640.0, 4.0F, 6.0F);
        ModNetwork.blur(level, impact, 320.0, 1.0F, 0.15F, 2.5F, 4.0F);
        ModNetwork.blur(level, position(), 120.0, 1.0F, 0.15F, 2.5F, 4.0F);

        this.breathCooldown = Config.ATOMIC_BREATH_COOLDOWN.get() * 20;
        this.blackRainPos = impact;
        this.blackRainTicks = 80;
    }

    private void atomicHit(LivingEntity entity, DamageSource source) {
        if (entity instanceof GodzillaEntity) {
            return;
        }
        if (Blasts.isPilot(entity)) {
            entity.hurt(source, 15.0F); // "plot armor"
        } else if (entity instanceof ShindenEntity shinden && shinden.isVehicle()) {
            // a piloted Shinden rides out the blast, like its pilot (only the beam itself brings it down)
        } else {
            entity.hurt(source, 1.0E9F);
        }
    }

    private void startBlackRain(ServerLevel level) {
        ModNetwork.sendNear(level, this.blackRainPos, 640.0, new FxPacket(FxPacket.Type.BLACK_RAIN, this.blackRainPos, 150.0F, 20.0F));
        Component rain = Component.translatable("message." + GodzillaMinusOne.MODID + ".black_rain").withStyle(ChatFormatting.BOLD);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(this.blackRainPos) < 640.0 * 640.0) {
                player.displayClientMessage(rain, true);
            }
        }
    }

    // ------------------------------------------------------------------ melee

    private void startAttack(LivingEntity target) {
        Attack attack;
        if (isSwimmingAtSurface()) {
            attack = Attack.SWIM_BITE;
        } else {
            double heightAbove = target.getY() - getY();
            Vec3 toTarget = target.position().subtract(position()).multiply(1.0, 0.0, 1.0).normalize();
            Vec3 facing = Vec3.directionFromRotation(0.0F, getYRot());
            if (toTarget.dot(facing) < -0.3) {
                attack = Attack.TAIL;
            } else if (heightAbove > 10.0) {
                attack = Attack.BITE;
            } else {
                int roll = this.random.nextInt(10);
                attack = roll < 4 ? Attack.BITE : roll < 8 ? Attack.STOMP : Attack.TAIL;
            }
        }
        this.pendingAttack = attack;
        this.pendingHitTicks = attack.hitDelay;
        this.attackCooldown = attack.length + 10;
        triggerAnim("action", attack.anim);
    }

    private void performAttack(ServerLevel level, Attack attack, @Nullable LivingEntity target) {
        switch (attack) {
            case BITE -> {
                if (target != null && target.isAlive() && canReach(target, 4.0)) {
                    doHurtTarget(target);
                }
                ModNetwork.shake(level, position(), 48.0, 0.4F, 1.0F);
            }
            case STOMP -> {
                Vec3 foot = locator(FOOT_SIDE, 0.4, FOOT_FORWARD);
                areaAttack(level, foot, 12.0, (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.5F);
                ModNetwork.sendNear(level, foot, 256.0, new FxPacket(FxPacket.Type.STOMP, foot, 1.5F, 0.0F));
                ModNetwork.shake(level, foot, 128.0, 1.2F, 2.0F);
            }
            case TAIL -> {
                areaAttack(level, position(), 26.0, (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.3F);
                ModNetwork.shake(level, position(), 64.0, 0.6F, 1.2F);
            }
            case SWIM_BITE -> swimBite(level, target);
        }
    }

    /** The jaws snap shut at the surface ahead of him: boats are smashed and whoever was in them bitten. */
    private void swimBite(ServerLevel level, @Nullable LivingEntity target) {
        Vec3 jaws = locator(0.0, SWIM_SINK, SWIM_BITE_REACH);
        AABB box = new AABB(jaws, jaws).inflate(9.0, 7.0, 9.0);
        DamageSource source = damageSources().mobAttack(this);
        float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
        for (Boat boat : level.getEntitiesOfClass(Boat.class, box)) {
            for (Entity rider : boat.getPassengers()) {
                if (rider instanceof LivingEntity living && !(living instanceof GodzillaEntity)) {
                    living.hurt(source, damage);
                }
            }
            boat.hurt(source, 1000.0F);
        }
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != this && e.isAlive())) {
            if (entity == target) {
                doHurtTarget(entity);
            } else if (!(entity instanceof GodzillaEntity) && !Blasts.isPilot(entity)) {
                entity.hurt(source, damage);
            }
        }
        level.playSound(null, jaws.x, jaws.y, jaws.z, SoundEvents.GENERIC_SPLASH, SoundSource.HOSTILE, 6.0F, 0.5F);
        ModNetwork.shake(level, jaws, 48.0, 0.4F, 1.0F);
    }

    private void areaAttack(ServerLevel level, Vec3 center, double radius, float damage) {
        DamageSource source = damageSources().mobAttack(this);
        for (LivingEntity entity : Blasts.livingNear(level, center, radius, this)) {
            if (!(entity instanceof GodzillaEntity) && !Blasts.isPilot(entity)) {
                entity.hurt(source, damage);
                Vec3 push = entity.position().subtract(center).multiply(1.0, 0.0, 1.0).normalize().scale(2.0).add(0.0, 0.8, 0.0);
                entity.setDeltaMovement(entity.getDeltaMovement().add(push));
                entity.hurtMarked = true;
            }
        }
    }

    boolean canReach(LivingEntity target, double extra) {
        double dx = target.getX() - getX();
        double dz = target.getZ() - getZ();
        double dy = target.getY() - getY();
        if (isSwimmingAtSurface()) {
            // the swimming bite reaches along the surface in front of him
            Vec3 facing = Vec3.directionFromRotation(0.0F, getYRot());
            double dist = Math.sqrt(dx * dx + dz * dz);
            double ahead = (dx * facing.x + dz * facing.z) / Math.max(dist, 1.0E-3);
            double reach = SWIM_BITE_REACH + 7.0 + extra;
            return dist <= reach && (dist < 10.0 || ahead > 0.8) && dy > -4.0 && dy < SWIM_SINK + 10.0;
        }
        double reach = getBbWidth() * 0.5 + 14.0 + extra;
        return dx * dx + dz * dz <= reach * reach && dy > -6.0 && dy < getBbHeight() + 4.0;
    }

    // ------------------------------------------------------------------ damage & death

    /** Called by {@link Blasts#navalMine}: a flat share of his max health, a 5 s stun, and a roar. */
    public void hitByNavalMine(DamageSource source) {
        if (!isActive()) {
            return;
        }
        float amount = (float) (getMaxHealth() * Config.MINE_DAMAGE_PERCENT.get() / 100.0);
        this.invulnerableTime = 0;
        this.stunTicks = STUN_TICKS;
        if (amount > 0.0F) {
            hurt(source, amount);
        }
        if (isActive() && !isCharging()) {
            triggerAnim("action", "roar");
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurt(source, amount); // /kill and the void
        }
        if (!isActive()) {
            return false;
        }
        if (!source.is(ModDamageTypes.NAVAL_MINE) && Config.GODZILLA_ONLY_HURT_BY_EXPLOSIVES.get()) {
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide && Config.GODZILLA_REGENERATES.get() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            // gojira -> gojira_dead: collapse, lie dead, then regenerate (see tick()).
            setHealth(1.0F);
            setPhase(Phase.COLLAPSING);
            return;
        }
        super.die(source);
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        return false; // naval mine "slowness" is handled as stunTicks
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    /** Water doesn't push him about: he walks along the sea floor, or swims at the surface of deep water. */
    @Override
    public boolean isAffectedByFluids() {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
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
    public boolean canChangeDimensions() {
        return false;
    }

    // ------------------------------------------------------------------ targeting helpers

    boolean isValidTarget(LivingEntity entity) {
        if (entity instanceof GodzillaEntity || entity instanceof NavalMineEntity || entity instanceof ArmorStand) {
            return false;
        }
        if (entity instanceof ShindenEntity shinden && !shinden.isVehicle()) {
            return false;
        }
        if (entity instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return false;
        }
        return Config.GODZILLA_TARGETS_ALL_MOBS.get() || entity instanceof Player || entity instanceof ShindenEntity
                || entity instanceof Enemy || entity instanceof IronGolem;
    }

    /** Living entities whose hitbox is within {@code radius} blocks of his hitbox. */
    private List<LivingEntity> nearby(double radius) {
        return level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(radius), e -> e != this && e.isAlive());
    }

    /** Turns towards {@code target} (plus {@code offset} degrees to the right), at most {@code maxTurn} degrees. */
    void faceTowards(Entity target, float maxTurn, float offset) {
        double dx = target.getX() - getX();
        double dz = target.getZ() - getZ();
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F + offset;
        float newYaw = Mth.approachDegrees(getYRot(), yaw, maxTurn);
        setYRot(newYaw);
        this.yBodyRot = newYaw;
        this.yHeadRot = newYaw;
    }

    /** World position of a point on the model: {@code side} to the right, {@code up}, {@code forward}. */
    public Vec3 locator(double side, double up, double forward) {
        Vec3 fwd = Vec3.directionFromRotation(0.0F, this.yBodyRot);
        Vec3 right = new Vec3(-fwd.z, 0.0, fwd.x);
        return position().add(fwd.scale(forward)).add(right.scale(side)).add(0.0, up, 0.0);
    }

    private static double distanceSqrToSegment(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double t = Mth.clamp(p.subtract(a).dot(ab) / Math.max(ab.lengthSqr(), 1.0E-6), 0.0, 1.0);
        return p.distanceToSqr(a.add(ab.scale(t)));
    }

    /** Clears blocks in his path (the add-on clears a column in front of him when he attacks). */
    private void smashBlocks(ServerLevel level) {
        if (!Config.GODZILLA_BREAKS_BLOCKS.get() || !ForgeEventFactory.getMobGriefingEvent(level, this)) {
            return;
        }
        Vec3 forward = Vec3.directionFromRotation(0.0F, getYRot());
        AABB box = getBoundingBox().expandTowards(forward.x * 3.0, 0.0, forward.z * 3.0).inflate(1.5, 0.0, 1.5);
        int minX = Mth.floor(box.minX);
        int maxX = Mth.floor(box.maxX);
        int minY = Mth.floor(getY()) + 1;
        int maxY = Mth.floor(box.maxY);
        int minZ = Mth.floor(box.minZ);
        int maxZ = Mth.floor(box.maxZ);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int broken = 0;
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
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

    // ------------------------------------------------------------------ boss bar, sounds, saving

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

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return isActive() && !isCharging() ? ModSounds.GODZILLA_AMBIENT.get() : null;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 500;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.GODZILLA_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.GODZILLA_DEATH.get();
    }

    @Override
    protected float getSoundVolume() {
        return 12.0F;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        // footsteps come from the walking animation's sound keyframes
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Phase", getPhase().ordinal());
        tag.putInt("PhaseTicks", this.phaseTicks);
        tag.putInt("BreathCooldown", this.breathCooldown);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Phase")) {
            int phase = Mth.clamp(tag.getInt("Phase"), 0, Phase.values().length - 1);
            this.entityData.set(DATA_PHASE, phase);
            this.phaseTicks = tag.getInt("PhaseTicks");
            this.bossEvent.setVisible(phase != Phase.COLLAPSING.ordinal() && phase != Phase.DEAD.ordinal());
        }
        this.breathCooldown = tag.getInt("BreathCooldown");
        // Godzillas saved by an older version of the mod walked faster.
        AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.setBaseValue(0.36D);
        }
    }

    // ------------------------------------------------------------------ GeckoLib

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", ANIM_TRANSITION, this::mainAnimation)
                .setAnimationSpeedHandler(g -> g.isWalkAnimation() ? WALK_ANIM_SPEED : 1.0)
                .setSoundKeyframeHandler(this::onSoundKeyframe)
                .setParticleKeyframeHandler(this::onParticleKeyframe));
        controllers.add(new AnimationController<>(this, "action", 3, state -> PlayState.STOP)
                .triggerableAnim("attack", ATTACK)
                .triggerableAnim("stomp", STOMP)
                .triggerableAnim("tail", TAIL)
                .triggerableAnim("swim_bite", SWIM_BITE_ANIM)
                .triggerableAnim("roar", ROAR)
                .setSoundKeyframeHandler(this::onSoundKeyframe)
                .setParticleKeyframeHandler(this::onParticleKeyframe));
    }

    private PlayState mainAnimation(AnimationState<GodzillaEntity> state) {
        this.walkAnimation = false;
        switch (getPhase()) {
            case EMERGING:
                return state.setAndContinue(EMERGE);
            case RISING:
                return state.setAndContinue(RISE);
            case COLLAPSING:
                return state.setAndContinue(COLLAPSE);
            case DEAD:
                return state.setAndContinue(LIE_DEAD);
            default:
                break;
        }
        if (isCharging()) {
            return state.setAndContinue(CHARGE);
        }
        if (isSwimmingAtSurface()) {
            return state.setAndContinue(SWIM);
        }
        if (state.isMoving()) {
            this.walkAnimation = true;
            return state.setAndContinue(WALK);
        }
        return state.setAndContinue(IDLE);
    }

    private void onSoundKeyframe(SoundKeyframeEvent<GodzillaEntity> event) {
        String name = event.getKeyframeData().getSound();
        RegistryObject<SoundEvent> sound = ModSounds.BY_NAME.get(name);
        if (sound != null) {
            float volume = name.equals("godzilla.attack") ? 6.0F : 12.0F;
            level().playLocalSound(getX(), getY() + 6.0, getZ(), sound.get(), SoundSource.HOSTILE, volume,
                    0.95F + this.random.nextFloat() * 0.1F, false);
        }
    }

    private void onParticleKeyframe(ParticleKeyframeEvent<GodzillaEntity> event) {
        EffectHooks.particleKeyframe.spawn(this, event.getKeyframeData().getEffect(), event.getKeyframeData().getLocator());
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    // ------------------------------------------------------------------ AI

    /** Vanilla MoveControl turns up to 90 degrees a tick; a 45 block kaiju turns slowly (add-on: max_turn 15). */
    static class GodzillaMoveControl extends MoveControl {
        GodzillaMoveControl(Mob mob) {
            super(mob);
        }

        @Override
        public void tick() {
            if (this.operation == MoveControl.Operation.MOVE_TO) {
                this.operation = MoveControl.Operation.WAIT;
                double dx = this.wantedX - this.mob.getX();
                double dz = this.wantedZ - this.mob.getZ();
                if (dx * dx + dz * dz < 9.0) {
                    this.mob.setZza(0.0F);
                    return;
                }
                float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
                this.mob.setYRot(this.rotlerp(this.mob.getYRot(), yaw, 5.0F));
                float speed = (float) (this.speedModifier * this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED));
                float turn = Mth.degreesDifferenceAbs(this.mob.getYRot(), yaw);
                this.mob.setSpeed(turn > 60.0F ? speed * 0.25F : speed);
            } else {
                this.mob.setZza(0.0F);
            }
        }
    }

    /** Walks (or swims) straight at his target and attacks when in reach (no pathfinding: he goes through things). */
    static class ChaseGoal extends Goal {
        private final GodzillaEntity godzilla;

        ChaseGoal(GodzillaEntity godzilla) {
            this.godzilla = godzilla;
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = this.godzilla.getTarget();
            return this.godzilla.isActive() && target != null && target.isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = this.godzilla.getTarget();
            if (target == null) {
                return;
            }
            this.godzilla.getLookControl().setLookAt(target, 10.0F, 10.0F);
            if (this.godzilla.canReach(target, 0.0)) {
                if (this.godzilla.attackCooldown <= 0) {
                    this.godzilla.startAttack(target);
                } else {
                    this.godzilla.faceTowards(target, 3.0F, 0.0F);
                }
                if (this.godzilla.isSwimmingAtSurface()) {
                    // keep after a fleeing boat while snapping at it
                    this.godzilla.getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), 1.0);
                }
            } else {
                this.godzilla.getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), 1.0);
            }
        }
    }

    /** Calm wandering; like the add-on's move_to_water behaviour he likes to head for water. */
    static class WanderGoal extends Goal {
        private final GodzillaEntity godzilla;
        @Nullable
        private Vec3 destination;
        private int timeout;

        WanderGoal(GodzillaEntity godzilla) {
            this.godzilla = godzilla;
            setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!this.godzilla.isActive() || this.godzilla.getTarget() != null || this.godzilla.getRandom().nextInt(100) != 0) {
                return false;
            }
            this.destination = pickDestination();
            return this.destination != null;
        }

        @Nullable
        private Vec3 pickDestination() {
            Level level = this.godzilla.level();
            Vec3 fallback = null;
            for (int i = 0; i < 12; i++) {
                double angle = this.godzilla.getRandom().nextDouble() * Math.PI * 2.0;
                double dist = 16.0 + this.godzilla.getRandom().nextDouble() * 32.0;
                int x = Mth.floor(this.godzilla.getX() + Math.cos(angle) * dist);
                int z = Mth.floor(this.godzilla.getZ() + Math.sin(angle) * dist);
                BlockPos top = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(x, 0, z));
                Vec3 candidate = Vec3.atBottomCenterOf(top);
                if (level.getFluidState(top.below()).is(FluidTags.WATER)) {
                    return candidate;
                }
                if (fallback == null) {
                    fallback = candidate;
                }
            }
            return fallback;
        }

        @Override
        public void start() {
            this.timeout = 600;
        }

        @Override
        public boolean canContinueToUse() {
            if (this.destination == null || !this.godzilla.isActive() || this.godzilla.getTarget() != null || --this.timeout <= 0) {
                return false;
            }
            double dx = this.destination.x - this.godzilla.getX();
            double dz = this.destination.z - this.godzilla.getZ();
            return dx * dx + dz * dz > 16.0;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (this.destination != null) {
                this.godzilla.getMoveControl().setWantedPosition(this.destination.x, this.destination.y, this.destination.z, 0.8);
            }
        }

        @Override
        public void stop() {
            this.destination = null;
        }
    }

    /** Like the add-on's nearest_attackable_target: anything that isn't another Godzilla, within 128 blocks. */
    static class GodzillaTargetGoal extends NearestAttackableTargetGoal<LivingEntity> {
        GodzillaTargetGoal(GodzillaEntity godzilla) {
            super(godzilla, LivingEntity.class, 20, true, false, godzilla::isValidTarget);
        }

        @Override
        protected AABB getTargetSearchArea(double range) {
            return this.mob.getBoundingBox().inflate(range, range, range);
        }

        @Override
        public boolean canUse() {
            return ((GodzillaEntity) this.mob).isActive() && super.canUse();
        }
    }
}
