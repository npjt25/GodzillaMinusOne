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
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
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

/**
 * The Odo Island Monster: Godzilla Minus One in his earlier, dinosaur-like form (15 blocks tall).
 *
 * <ul>
 *   <li>Left alone he wanders. Come within {@link #PROXIMITY} blocks and he charges you, biting and stomping.</li>
 *   <li>Catch him in a searchlight's beam and he goes berserk: he roars and runs down whoever is working the light.</li>
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

    // Measured on the model (blocks: right, up, forward).
    private static final Vec3 JAWS = new Vec3(0.0, 10.0, 10.5);
    private static final double BITE_RADIUS = 4.5;
    private static final Vec3 LEFT_FOOT = new Vec3(-1.8, 0.0, 1.1);
    private static final double STOMP_RADIUS = 6.0;

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.odo.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.odo.walk");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("animation.odo.run");
    private static final RawAnimation BURNT = RawAnimation.begin().thenPlayAndHold("animation.odo.burnt");
    private static final RawAnimation BITE = RawAnimation.begin().thenPlay("animation.odo.bite");
    private static final RawAnimation STOMP = RawAnimation.begin().thenPlay("animation.odo.stomp");
    private static final RawAnimation ROAR = RawAnimation.begin().thenPlay("animation.odo.roar");

    private static final EntityDataAccessor<Integer> DATA_STATE = SynchedEntityData.defineId(OdoIslandMonsterEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_RUNNING = SynchedEntityData.defineId(OdoIslandMonsterEntity.class, EntityDataSerializers.BOOLEAN);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int rageTicks;
    private int burntTicks;
    private int attackCooldown;
    private int roarTicks;
    private int roarCooldown;
    private int pendingHitTicks = -1;
    private boolean pendingStomp;
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

    /** Lit up by a searchlight: he goes berserk and runs down whoever is working it. */
    public void spottedBy(@Nullable LivingEntity operator) {
        if (isBurnt() || !isAlive()) {
            return;
        }
        if (operator != null && operator.isAlive() && !(operator instanceof Player p && p.isSpectator())) {
            setTarget(operator);
        }
        enrage(SPOTLIGHT_RAGE);
    }

    /** Caught in a nuclear blast: he doesn't die, he collapses, burnt to a crisp (then rises as Godzilla). */
    public void nuke() {
        if (isBurnt() || level().isClientSide) {
            return;
        }
        setState(STATE_BURNT);
        this.burntTicks = BURNT_TICKS;
        this.rageTicks = 0;
        this.pendingHitTicks = -1;
        this.entityData.set(DATA_RUNNING, false);
        setTarget(null);
        setHealth(getMaxHealth());
        clearFire();
    }

    private void enrage(int ticks) {
        boolean wasCalm = this.rageTicks <= 0;
        this.rageTicks = Math.max(this.rageTicks, ticks);
        setState(STATE_ANGRY);
        if (wasCalm && this.roarCooldown <= 0) {
            this.roarTicks = 40;
            this.roarCooldown = 200;
            triggerAnim("action", "roar");
        }
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
        if (this.rageTicks <= 0 && target == null && getState() == STATE_ANGRY) {
            setState(STATE_CALM);
        }
        if (this.horizontalCollision && this.tickCount % 5 == 0 && ForgeEventFactory.getMobGriefingEvent(level, this)) {
            crashThrough(level);
        }
        if (this.pendingHitTicks >= 0 && --this.pendingHitTicks < 0) {
            if (this.pendingStomp) {
                stomp(level);
            } else {
                bite(level, target);
            }
        }
        boolean running = target != null && distanceTo(target) > 14.0 && this.roarTicks <= 0;
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
                    if (hardness >= 0.0F && hardness <= 2.0F && Destruction.canBreak(level, pos, state, this)) {
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
        this.pendingHitTicks = stomp ? 17 : 11;           // the stomp lands 0.72 s in, the jaws close 0.42 s in
        this.attackCooldown = stomp ? 34 : 22;
        triggerAnim("action", stomp ? "stomp" : "bite");
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
        return super.hurt(source, amount);
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
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setState(Mth.clamp(tag.getInt("OdoState"), STATE_CALM, STATE_BURNT));
        this.burntTicks = tag.getInt("BurntTicks");
        this.rageTicks = tag.getInt("RageTicks");
    }

    // ------------------------------------------------------------------ GeckoLib

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, this::mainAnimation)
                .setAnimationSpeedHandler(OdoIslandMonsterEntity::strideSpeed)
                .setSoundKeyframeHandler(this::onSoundKeyframe));
        controllers.add(new AnimationController<>(this, "action", 3, state -> PlayState.STOP)
                .triggerableAnim("bite", BITE)
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
        double nominal = odo.isRunning() ? 0.47 : 0.15;   // blocks per tick the cycles are made for
        return speed < 0.01 ? 1.0 : Mth.clamp(speed / nominal, 0.6, 1.5);
    }

    private PlayState mainAnimation(AnimationState<OdoIslandMonsterEntity> state) {
        if (isBurnt()) {
            return state.setAndContinue(BURNT);
        }
        if (state.isMoving()) {
            return state.setAndContinue(isRunning() ? RUN : WALK);
        }
        return state.setAndContinue(IDLE);
    }

    private void onSoundKeyframe(SoundKeyframeEvent<OdoIslandMonsterEntity> event) {
        String name = event.getKeyframeData().getSound();
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

            double speed = this.odo.rageTicks > 0 && this.odo.distanceTo(target) > 14.0 ? RUN_SPEED : 1.0;
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
