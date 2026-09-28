package com.npjt25.godzillaminusone.entity;

import com.npjt25.godzillaminusone.Config;
import com.npjt25.godzillaminusone.GodzillaMinusOne;
import com.npjt25.godzillaminusone.network.FxPacket;
import com.npjt25.godzillaminusone.network.ModNetwork;
import com.npjt25.godzillaminusone.registry.ModDamageTypes;
import com.npjt25.godzillaminusone.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
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
 * <p>Add-on behaviour reproduced here:
 * <ul>
 *   <li>Spawning plays the emerge + roar sequence before he becomes active.</li>
 *   <li>Radiation aura: "RADIATION POISONING", Wither, and any Shinden that gets too close blows up.</li>
 *   <li>Every step while walking shakes the camera and hurts everything around him.</li>
 *   <li>With a target he charges his atomic breath (blue dorsal plates, 13.6 s charge), fires, and roars.</li>
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

    // Timings in ticks, taken from the add-on's timers and animation timelines.
    private static final int EMERGE_TICKS = 180;        // gojira_spawn1 timer: 9 s
    private static final int RISE_TICKS = 120;          // gojira_spawn2 timer: 6 s
    private static final int COLLAPSE_TICKS = 100;      // gojira_dead timer: 5 s
    private static final int DEAD_TICKS = 300;          // gojira_dead2 timer: 15 s
    private static final int ANGER_DELAY = 40;          // angry timeline: charge starts at 2.0 s
    private static final int CHARGE_BLAST_TICK = 272;   // angry timeline: blast at 15.6 s = 13.6 s into the charge
    private static final int BEAM_END_TICK = 304;       // charge animation: beam bone shrinks at 15.17 s
    private static final int CHARGE_LENGTH = 433;       // charge animation: 21.67 s
    private static final int AURA_INTERVAL = 10;        // idle timeline loops every 0.5 s
    private static final int STEP_INTERVAL = 20;        // big_steps timeline loops every 1 s
    private static final int REGEN_INTERVAL = 100;      // calm timeline loops every 5 s
    private static final int STUN_TICKS = 100;          // naval mine: slowness for 5 s

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
    private static final RawAnimation ROAR = RawAnimation.begin().thenPlay("animation.gojira.roar");

    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(GodzillaEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_CHARGE = SynchedEntityData.defineId(GodzillaEntity.class, EntityDataSerializers.INT);

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
    private int stepTicks;
    private int pendingHitTicks = -1;
    @Nullable
    private Attack pendingAttack;
    private int blackRainTicks = -1;
    private Vec3 blackRainPos = Vec3.ZERO;

    private enum Attack {
        BITE("attack", 10, 20), STOMP("stomp", 25, 65), TAIL("tail", 16, 35);

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
                .add(Attributes.MOVEMENT_SPEED, 0.4D)
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

    /** Skips the emerge-and-roar entrance (used by the game tests). */
    public void skipIntro() {
        setPhase(Phase.ACTIVE);
    }

    private void setChargeState(int state) {
        this.entityData.set(DATA_CHARGE, state);
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
        return super.isImmobile() || !isActive() || isCharging() || this.stunTicks > 0 || this.pendingHitTicks >= 0;
    }

    // ------------------------------------------------------------------ ticking

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        this.phaseTicks++;
        this.bossEvent.setProgress(Mth.clamp(getHealth() / getMaxHealth(), 0.0F, 1.0F));

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
        } else if (target != null && this.angryTicks >= ANGER_DELAY && this.breathCooldown <= 0 && this.stunTicks <= 0
                && this.pendingHitTicks < 0) {
            this.chargeTicks = 0;
            setChargeState(CHARGE_GLOWING);
        }

        if (this.pendingHitTicks >= 0) {
            if (target != null) {
                faceTowards(target, 3.0F);
            }
            if (--this.pendingHitTicks < 0 && this.pendingAttack != null) {
                performAttack(level, this.pendingAttack, target);
                this.pendingAttack = null;
            }
        }

        double dx = getX() - this.xo;
        double dz = getZ() - this.zo;
        boolean walking = dx * dx + dz * dz > 0.0004;
        if (walking && ++this.stepTicks >= STEP_INTERVAL) {
            this.stepTicks = 0;
            stepShockwave(level);
        }
        if ((walking || this.horizontalCollision) && this.tickCount % 5 == 0) {
            smashBlocks(level);
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

    /** animation.gojira.big_steps timeline: each step shakes the ground and hurts everything close by. */
    private void stepShockwave(ServerLevel level) {
        ModNetwork.shake(level, position(), 100.0, 0.25F, 1.5F);
        float damage = Config.STEP_DAMAGE.get().floatValue();
        int radius = Config.STEP_RADIUS.get();
        if (damage <= 0 || radius <= 0) {
            return;
        }
        DamageSource source = ModDamageTypes.source(level, ModDamageTypes.STOMP, this);
        for (LivingEntity entity : nearby(radius)) {
            if (!(entity instanceof GodzillaEntity) && !Blasts.isPilot(entity) && entity.getY() < getY() + 8.0) {
                entity.hurt(source, damage);
            }
        }
    }

    // ------------------------------------------------------------------ atomic breath

    private void tickCharge(ServerLevel level, @Nullable LivingEntity target) {
        this.chargeTicks++;
        if (target != null && this.chargeTicks < CHARGE_BLAST_TICK) {
            faceTowards(target, 4.0F);
        }

        if (this.chargeTicks % 4 == 0 && this.chargeTicks < CHARGE_BLAST_TICK) {
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

        if (this.chargeTicks == CHARGE_BLAST_TICK) {
            fireAtomicBreath(level);
        } else if (this.chargeTicks == BEAM_END_TICK) {
            setChargeState(CHARGE_COOLING);
        } else if (this.chargeTicks >= CHARGE_LENGTH) {
            this.chargeTicks = -1;
            this.angryTicks = 0;
            this.breathCooldown = Config.ATOMIC_BREATH_COOLDOWN.get() * 20;
            setChargeState(CHARGE_NONE);
        }
    }

    /**
     * The add-on's atomic breath. The charge animation stretches bone "B" into a beam that leaves his mouth
     * ~20 blocks up and slopes down ~19 degrees for ~120 blocks; the blast goes off where that beam meets the
     * ground (on flat ground ~70 blocks ahead), then a crater, a mushroom cloud and black rain.
     */
    private void fireAtomicBreath(ServerLevel level) {
        Vec3 mouth = locator(0.0, 19.8, 14.5);
        Vec3 beamEnd = locator(0.0, -19.8, 129.2);
        HitResult hit = level.clip(new ClipContext(mouth, beamEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
        Vec3 impact = hit.getType() == HitResult.Type.MISS ? beamEnd : hit.getLocation();

        DamageSource source = ModDamageTypes.source(level, ModDamageTypes.ATOMIC_BREATH, this);

        // Anything caught in the beam itself.
        AABB beamBox = new AABB(mouth, impact).inflate(4.0);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, beamBox, e -> e != this && e.isAlive())) {
            if (distanceSqrToSegment(entity.getBoundingBox().getCenter(), mouth, impact) <= 16.0) {
                atomicHit(entity, source);
            }
        }
        // The blast.
        for (LivingEntity entity : Blasts.livingNear(level, impact, Config.ATOMIC_BLAST_KILL_RADIUS.get(), this)) {
            atomicHit(entity, source);
        }
        // Radiation / fallout.
        int radiation = Config.ATOMIC_BLAST_RADIATION_RADIUS.get();
        int amplifier = Config.ATOMIC_BLAST_RADIATION_AMPLIFIER.get();
        for (LivingEntity entity : Blasts.livingNear(level, impact, radiation, this)) {
            if (!(entity instanceof GodzillaEntity)) {
                entity.addEffect(new MobEffectInstance(MobEffects.WITHER, 600, amplifier), this);
            }
        }

        carveCrater(level, impact);

        ModNetwork.sendNear(level, impact, 640.0, new FxPacket(FxPacket.Type.ATOMIC_BLAST, impact, getYRot(), 0.0F));
        level.playSound(null, impact.x, impact.y, impact.z, ModSounds.GODZILLA_ATOMIC_BLAST.get(), SoundSource.HOSTILE, 24.0F, 1.0F);
        ModNetwork.shake(level, impact, 640.0, 4.0F, 6.0F);
        ModNetwork.fade(level, position(), 100.0, Blasts.FADE_COLOR, 0.1F, 1.3F, 1.0F);

        Component quote = Component.translatable("message." + GodzillaMinusOne.MODID + ".become_death").withStyle(ChatFormatting.BOLD);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(impact) < 640.0 * 640.0) {
                player.displayClientMessage(quote, true);
            }
        }
        triggerAnim("action", "roar");

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
            // a piloted Shinden rides out the blast, like its pilot
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

    /** The add-on's goji:nuclear_hole, as a crater (needs mobGriefing). */
    private void carveCrater(ServerLevel level, Vec3 center) {
        int radius = Config.ATOMIC_BLAST_CRATER_RADIUS.get();
        if (radius <= 0 || !ForgeEventFactory.getMobGriefingEvent(level, this)) {
            return;
        }
        BlockPos c = BlockPos.containing(center);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        double r2 = radius * radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -radius / 2; dy <= radius; dy++) {
                    double d = dx * dx + dz * dz + dy * dy * (dy < 0 ? 4.0 : 1.0);
                    if (d > r2) {
                        continue;
                    }
                    pos.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
                    BlockState state = level.getBlockState(pos);
                    if (!state.isAir() && canSmash(level, pos, state)) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
        // Scattered fires on the crater floor and rim.
        for (int i = 0; i < radius * 4; i++) {
            int x = c.getX() + this.random.nextInt(radius * 3) - radius * 3 / 2;
            int z = c.getZ() + this.random.nextInt(radius * 3) - radius * 3 / 2;
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(x, 0, z));
            BlockPos below = top.below();
            if (level.getBlockState(top).isAir() && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
                level.setBlock(top, Blocks.FIRE.defaultBlockState(), 3);
            }
        }
    }

    // ------------------------------------------------------------------ melee

    private void startAttack(LivingEntity target) {
        double heightAbove = target.getY() - getY();
        Vec3 toTarget = target.position().subtract(position()).multiply(1.0, 0.0, 1.0).normalize();
        Vec3 facing = Vec3.directionFromRotation(0.0F, getYRot());
        Attack attack;
        if (toTarget.dot(facing) < -0.3) {
            attack = Attack.TAIL;
        } else if (heightAbove > 10.0) {
            attack = Attack.BITE;
        } else {
            int roll = this.random.nextInt(10);
            attack = roll < 4 ? Attack.BITE : roll < 8 ? Attack.STOMP : Attack.TAIL;
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
                Vec3 foot = locator(5.7, 0.4, 3.0);
                areaAttack(level, foot, 10.0, (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.5F);
                ModNetwork.sendNear(level, foot, 256.0, new FxPacket(FxPacket.Type.STOMP, foot, 1.5F, 0.0F));
                ModNetwork.shake(level, foot, 128.0, 1.2F, 2.0F);
            }
            case TAIL -> {
                areaAttack(level, position(), 22.0, (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.3F);
                ModNetwork.shake(level, position(), 64.0, 0.6F, 1.2F);
            }
        }
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
        double reach = getBbWidth() * 0.5 + 12.0 + extra;
        double dx = target.getX() - getX();
        double dz = target.getZ() - getZ();
        double dy = target.getY() - getY();
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
        if (isActive()) {
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

    /** Walks along the sea floor at full speed instead of swimming or floating. */
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

    void faceTowards(Entity target, float maxTurn) {
        double dx = target.getX() - getX();
        double dz = target.getZ() - getZ();
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
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

    private boolean canSmash(Level level, BlockPos pos, BlockState state) {
        return state.getDestroySpeed(level, pos) >= 0.0F
                && !state.is(BlockTags.WITHER_IMMUNE)
                && state.canEntityDestroy(level, pos, this)
                && ForgeEventFactory.onEntityDestroyBlock(this, pos, state);
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
                    if (canSmash(level, pos, state)) {
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
    }

    // ------------------------------------------------------------------ GeckoLib

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, this::mainAnimation)
                .setSoundKeyframeHandler(this::onSoundKeyframe)
                .setParticleKeyframeHandler(this::onParticleKeyframe));
        controllers.add(new AnimationController<>(this, "action", 3, state -> PlayState.STOP)
                .triggerableAnim("attack", ATTACK)
                .triggerableAnim("stomp", STOMP)
                .triggerableAnim("tail", TAIL)
                .triggerableAnim("roar", ROAR)
                .setSoundKeyframeHandler(this::onSoundKeyframe)
                .setParticleKeyframeHandler(this::onParticleKeyframe));
    }

    private PlayState mainAnimation(AnimationState<GodzillaEntity> state) {
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
        if (state.isMoving()) {
            return state.setAndContinue(isInWater() ? SWIM : WALK);
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

    /** Vanilla MoveControl turns up to 90 degrees a tick; a 35 block kaiju turns slowly (add-on: max_turn 15). */
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

    /** Walks straight at his target and attacks when in reach (no pathfinding: he goes through things). */
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
                    this.godzilla.faceTowards(target, 3.0F);
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
