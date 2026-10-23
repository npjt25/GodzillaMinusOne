package com.npjt25.godzillaminusone.entity;

import com.npjt25.godzillaminusone.Config;
import com.npjt25.godzillaminusone.network.ModNetwork;
import com.npjt25.godzillaminusone.network.ShindenCrashPacket;
import com.npjt25.godzillaminusone.registry.ModDamageTypes;
import com.npjt25.godzillaminusone.registry.ModItems;
import com.npjt25.godzillaminusone.registry.ModSounds;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.keyframe.event.ParticleKeyframeEvent;
import software.bernie.geckolib.core.keyframe.event.SoundKeyframeEvent;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The Kyushu J7W1 Shinden (goji:kyushu_j7w1). Right-click to fly it: W/S throttle, the mouse steers and
 * pitches (look up to climb, down to dive); each right-click fires one burst from its nose cannons. Like a boat, a few
 * hits from a player knock it back into its item. If it is crashed into something at (near) full speed,
 * blown up, or flown within {@link Config#SHINDEN_KAMIKAZE_DISTANCE} blocks of Godzilla, it goes off like a
 * naval mine and the pilot bails out.
 */
public class ShindenEntity extends Mob implements GeoEntity {
    private static final RawAnimation FLY = RawAnimation.begin().thenLoop("animation.kplane.fly");
    private static final RawAnimation FIRE = RawAnimation.begin().thenPlay("animation.kplane.fire");

    /** Below this speed the plane can't hold altitude. */
    private static final double STALL_SPEED = 0.35;
    /** Hitting something at this fraction of top speed or more is a crash. */
    private static final double CRASH_SPEED = 0.75;
    /** A crash goes off like three blocks of TNT (TNT is 4). */
    public static final float CRASH_EXPLOSION_POWER = 12.0F;

    private static final EntityDataAccessor<Integer> DATA_HURT_TIME = SynchedEntityData.defineId(ShindenEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_HURT_DIR = SynchedEntityData.defineId(ShindenEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_DAMAGE = SynchedEntityData.defineId(ShindenEntity.class, EntityDataSerializers.FLOAT);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private double flightSpeed;
    private boolean exploded;
    private boolean crashed;
    /** Client side, while the pilot holds Free Look: the heading it keeps (client.ShindenControls). */
    private boolean freeLook;
    private float steerYaw;
    private float steerPitch;
    /** Server side: how far it moved over the last tick (its pilot's client flies it; the server only sees where to). */
    private Vec3 flightMotion = Vec3.ZERO;
    @Nullable
    private Vec3 lastServerPos;
    /** Ticks between rounds while the trigger is held: five a second. */
    private static final int GUN_COOLDOWN = 4;
    private int lastShotTick = -GUN_COOLDOWN;
    // Client side: how far the plane leans into a turn (degrees, right wing down is positive), and how far its
    // nose is up (positive) or down along its flight path.
    private float bank;
    private float bankO;
    private float bankYaw;
    private float pitch;
    private float pitchO;

    public ShindenEntity(EntityType<? extends ShindenEntity> type, Level level) {
        super(type, level);
        this.setMaxUpStep(1.0F);
        this.setPersistenceRequired();
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_HURT_TIME, 0);
        this.entityData.define(DATA_HURT_DIR, 1);
        this.entityData.define(DATA_DAMAGE, 0.0F);
    }

    public int getWobbleTime() {
        return this.entityData.get(DATA_HURT_TIME);
    }

    public int getWobbleSide() {
        return this.entityData.get(DATA_HURT_DIR);
    }

    public float getWobbleDamage() {
        return this.entityData.get(DATA_DAMAGE);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            // Level flight; it only leans into turns, as far as it is turning.
            this.bankO = this.bank;
            float turn = Mth.wrapDegrees(getYRot() - this.bankYaw);
            this.bankYaw = getYRot();
            float wanted = isVehicle() ? Mth.clamp(turn * 3.5F, -40.0F, 40.0F) : 0.0F;
            this.bank += (wanted - this.bank) * 0.2F;
            // Nose up when climbing, down when diving: along the way it's actually moving.
            this.pitchO = this.pitch;
            double dx = getX() - this.xo;
            double dy = getY() - this.yo;
            double dz = getZ() - this.zo;
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            float wantedPitch = 0.0F;
            if (isVehicle() && !onGround() && horizontal + Math.abs(dy) > 0.1) {
                wantedPitch = Mth.clamp((float) (Mth.atan2(dy, horizontal) * Mth.RAD_TO_DEG), -60.0F, 60.0F);
            }
            this.pitch += (wantedPitch - this.pitch) * 0.2F;
            return;
        }
        Vec3 here = position();
        this.flightMotion = this.lastServerPos != null ? here.subtract(this.lastServerPos) : Vec3.ZERO;
        this.lastServerPos = here;
        // like a boat, knocks wear off again
        if (getWobbleTime() > 0) {
            this.entityData.set(DATA_HURT_TIME, getWobbleTime() - 1);
        }
        if (getWobbleDamage() > 0.0F) {
            this.entityData.set(DATA_DAMAGE, Math.max(0.0F, getWobbleDamage() - 1.0F));
        }
    }

    /** How far the plane leans into a turn (degrees, right wing down is positive). */
    public float getBank(float partialTick) {
        return Mth.lerp(partialTick, this.bankO, this.bank);
    }

    /** How far the nose is up (degrees; negative is nose down), following its climb or dive. */
    public float getNosePitch(float partialTick) {
        return Mth.lerp(partialTick, this.pitchO, this.pitch);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 200.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    // ------------------------------------------------------------------ riding

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player.isSecondaryUseActive() || isVehicle()) {
            return InteractionResult.PASS;
        }
        if (!level().isClientSide) {
            player.startRiding(this);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        return getFirstPassenger() instanceof Player player ? player : null;
    }

    @Override
    protected void tickRidden(Player player, Vec3 input) {
        super.tickRidden(player, input);
        if (this.freeLook) {
            setRot(this.steerYaw, this.steerPitch); // the pilot is looking around: it flies on as it was
        } else {
            setRot(player.getYRot(), player.getXRot());
        }
        this.yRotO = this.yBodyRot = this.yHeadRot = getYRot();
    }

    /** Client side: the pilot starts looking around; the plane keeps this heading until {@link #stopFreeLook()}. */
    public void startFreeLook(float yaw, float pitch) {
        this.freeLook = true;
        this.steerYaw = yaw;
        this.steerPitch = pitch;
    }

    public void stopFreeLook() {
        this.freeLook = false;
    }

    public boolean isFreeLooking() {
        return this.freeLook;
    }

    public float getSteerYaw() {
        return this.steerYaw;
    }

    public float getSteerPitch() {
        return this.steerPitch;
    }

    /** Server side: its movement over the last tick (blocks). */
    public Vec3 getFlightMotion() {
        return this.flightMotion;
    }

    /** Flying at least this fast (blocks per tick) into something is a crash. */
    public static double crashSpeed() {
        return Config.SHINDEN_MAX_SPEED.get() * CRASH_SPEED;
    }

    @Override
    protected Vec3 getRiddenInput(Player player, Vec3 input) {
        return new Vec3(player.xxa, 0.0, player.zza);
    }

    @Override
    public void travel(Vec3 input) {
        if (!(getControllingPassenger() instanceof Player)) {
            this.flightSpeed = 0.0;
            super.travel(Vec3.ZERO); // unmanned: falls and rolls to a stop
            return;
        }
        if (!isControlledByLocalInstance()) {
            return;
        }
        double max = Config.SHINDEN_MAX_SPEED.get();
        if (input.z > 0.0) {
            this.flightSpeed = Math.min(max, this.flightSpeed + 0.035);
        } else if (input.z < 0.0) {
            this.flightSpeed = Math.max(0.0, this.flightSpeed - 0.06);
        } else {
            this.flightSpeed = Math.max(0.0, this.flightSpeed - 0.004);
        }

        Vec3 look = Vec3.directionFromRotation(getXRot(), getYRot());
        Vec3 wanted = look.scale(this.flightSpeed);
        if (this.flightSpeed < STALL_SPEED) {
            // not enough airspeed: sink (like the add-on's slow falling when looking down)
            wanted = new Vec3(wanted.x, Math.min(wanted.y, -0.3 * (1.0 - this.flightSpeed / STALL_SPEED)), wanted.z);
        }
        if (onGround() && this.flightSpeed < STALL_SPEED) {
            wanted = new Vec3(wanted.x, Math.max(wanted.y, 0.0), wanted.z);
        }
        Vec3 motion = getDeltaMovement().lerp(wanted, 0.2);
        setDeltaMovement(motion);
        move(MoverType.SELF, motion);
        boolean fast = this.flightSpeed >= max * CRASH_SPEED;
        if (fast && (this.horizontalCollision || (this.verticalCollisionBelow && motion.y < -0.5)) && !this.crashed) {
            // Flew into a building, a hill or the ground at full speed: the pilot's client tells the server.
            this.crashed = true;
            if (level().isClientSide) {
                ModNetwork.CHANNEL.sendToServer(new ShindenCrashPacket(getId()));
            } else if (getControllingPassenger() instanceof Player pilot) {
                crash(pilot);
            }
        }
        if (this.horizontalCollision) {
            this.flightSpeed *= 0.5;
        }
        resetFallDistance();
        calculateEntityAnimation(false);
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction move) {
        if (!hasPassenger(passenger)) {
            return;
        }
        // add-on seat: [-0.05, 2.3, -0.2] (just ahead of the middle). On the pilot's screen the plane pitches and
        // banks about y 1.5 (ShindenRenderer); the seat turns with it, so the pilot stays in the cockpit.
        Vector3f seat = new Matrix4f()
                .rotateY((180.0F - getYRot()) * Mth.DEG_TO_RAD)
                .translate(0.0F, 1.5F, 0.0F)
                .rotateX(this.pitch * Mth.DEG_TO_RAD)
                .rotateZ(-this.bank * Mth.DEG_TO_RAD)
                .translate(0.0F, -1.5F, 0.0F)
                .transformPosition(new Vector3f(0.0F, 2.3F, -0.2F));
        move.accept(passenger, getX() + seat.x, getY() + seat.y + passenger.getMyRidingOffset(), getZ() + seat.z);
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (!level().isClientSide && passenger instanceof Player player) {
            removeTorrid(player); // (older versions handed the pilot the Torrid as an item)
        }
    }

    public static void removeTorrid(Player player) {
        player.getInventory().clearOrCountMatchingItems(stack -> stack.is(ModItems.TORRID.get()), -1, player.inventoryMenu.getCraftSlots());
    }

    // ------------------------------------------------------------------ weapons & destruction

    /** The pilot holding right-click: a round, if the guns are ready (client.ShindenControls asks every other tick). */
    public void tryFireGuns(Player pilot) {
        if (this.tickCount - this.lastShotTick >= GUN_COOLDOWN || this.tickCount < this.lastShotTick) {
            this.lastShotTick = this.tickCount;
            fireGuns(pilot);
        }
    }

    /** The Torrid: a cannon burst along the nose (add-on: 400 damage to everything within 40 blocks). */
    public void fireGuns(Player pilot) {
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        triggerAnim("guns", "fire");
        ModNetwork.shake(level, position(), 10.0, 0.1F, 0.6F); // the gun sound + muzzle flashes come from the "fire" animation

        double range = Config.TORRID_RANGE.get();
        float damage = Config.TORRID_DAMAGE.get().floatValue();
        Vec3 aim = Vec3.directionFromRotation(getXRot(), getYRot()); // along the nose (the pilot may be looking round)
        Vec3 origin = position().add(0.0, 1.5, 0.0);
        DamageSource source = ModDamageTypes.source(level, ModDamageTypes.TORRID, this, pilot);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(range),
                e -> e != this && e != pilot && e.isAlive() && !e.isPassengerOfSameVehicle(pilot))) {
            Vec3 center = target.getBoundingBox().getCenter();
            Vec3 to = center.subtract(origin);
            double dist = to.length();
            double spread = Math.max(0.9, 1.0 - 3.0 / Math.max(dist, 1.0)); // wider near the plane
            if (dist > range || to.normalize().dot(aim) < spread) {
                continue;
            }
            HitResult hit = level.clip(new ClipContext(origin, center, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (hit.getType() == HitResult.Type.BLOCK && hit.getLocation().distanceTo(origin) < dist - 1.5) {
                continue;
            }
            target.hurt(source, damage);
        }
    }

    /** Blows up like a naval mine; the pilot bails out with a "parachute" (Slow Falling). */
    public void explode() {
        explode(false);
    }

    private void explode(boolean impact) {
        if (this.exploded || !(level() instanceof ServerLevel level)) {
            return;
        }
        this.exploded = true;
        Entity pilot = getFirstPassenger();
        ejectPassengers();
        if (pilot instanceof LivingEntity living) {
            living.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 600));
            living.setDeltaMovement(getDeltaMovement().scale(0.3).add(0.0, 1.4, 0.0));
            living.hurtMarked = true;
        }
        Vec3 at = position().add(0.0, 1.5, 0.0);
        Blasts.navalMine(level, at, this, pilot);
        if (impact) {
            Blasts.impact(level, at, CRASH_EXPLOSION_POWER, this, pilot);
        }
        discard();
    }

    /** Called (on the server) when the pilot flies it into something at (near) full speed: a huge explosion. */
    public void crash(Player pilot) {
        if (getControllingPassenger() == pilot && isAlive()) {
            explode(true);
        }
    }

    /**
     * Hits work like on a boat rather than a mob: the plane wobbles, and enough of them (one in creative) knock it
     * back into its item. Explosions and monsters' attacks wreck it instead, and then it blows up.
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurt(source, amount); // /kill and the void
        }
        if (isInvulnerableTo(source) || source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_DROWNING)
                || source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.CACTUS) || source.is(DamageTypes.SWEET_BERRY_BUSH)) {
            return false;
        }
        if (source.getEntity() != null && source.getEntity() == getFirstPassenger()) {
            return false; // don't shoot yourself down
        }
        if (level().isClientSide || isRemoved()) {
            return true;
        }
        this.entityData.set(DATA_HURT_DIR, -getWobbleSide());
        this.entityData.set(DATA_HURT_TIME, 10);
        this.entityData.set(DATA_DAMAGE, getWobbleDamage() + amount * 10.0F);
        markHurt();
        gameEvent(GameEvent.ENTITY_DAMAGE, source.getEntity());
        playSound(SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, 0.5F, 1.4F + this.random.nextFloat() * 0.2F);
        boolean creative = source.getEntity() instanceof Player player && player.getAbilities().instabuild;
        if (creative || getWobbleDamage() > 40.0F) {
            if (source.getEntity() instanceof Player) {
                ejectPassengers();
                if (!creative && level().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
                    spawnAtLocation(ModItems.SHINDEN_SPAWN_EGG.get());
                }
                discard();
            } else {
                explode();
            }
        }
        return true;
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            explode();
        } else {
            super.die(source);
        }
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected boolean canRide(Entity vehicle) {
        return false;
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    // ------------------------------------------------------------------ GeckoLib

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "flight", 5,
                state -> isVehicle() ? state.setAndContinue(FLY) : PlayState.STOP)
                .setSoundKeyframeHandler(this::onSoundKeyframe));
        controllers.add(new AnimationController<>(this, "guns", 0, state -> PlayState.STOP)
                .triggerableAnim("fire", FIRE)
                .setSoundKeyframeHandler(this::onSoundKeyframe)
                .setParticleKeyframeHandler(this::onParticleKeyframe));
    }

    private void onSoundKeyframe(SoundKeyframeEvent<ShindenEntity> event) {
        String name = event.getKeyframeData().getSound();
        RegistryObject<SoundEvent> sound = ModSounds.BY_NAME.get(name);
        if (sound != null) {
            level().playLocalSound(getX(), getY(), getZ(), sound.get(), SoundSource.NEUTRAL,
                    name.equals("shinden.engine") ? 3.0F : 4.0F, 1.0F, false);
        }
    }

    private void onParticleKeyframe(ParticleKeyframeEvent<ShindenEntity> event) {
        EffectHooks.particleKeyframe.spawn(this, event.getKeyframeData().getEffect(), event.getKeyframeData().getLocator());
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
