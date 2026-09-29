package com.npjt25.godzillaminusone.entity;

import com.npjt25.godzillaminusone.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * A WWII searchlight. Right-click to sit at it: it turns and tilts with where you look, and while someone is
 * manning it the lamp is lit: a beam of light reaching {@link #RANGE} blocks, lighting up whatever it lands on.
 * An Odo Island Monster caught in the beam goes berserk and comes for whoever is working the light. Hit it like a
 * boat to knock it back into its item.
 */
public class SearchlightEntity extends Entity implements GeoEntity {
    public static final double RANGE = 160.0;
    /** How wide the beam is (blocks from its axis) for spotting things in it. */
    private static final double BEAM_RADIUS = 3.0;
    /** Where the lamp's lens is (blocks up from the base, and ahead of the lamp's pivot). */
    public static final double LENS_HEIGHT = 1.25;
    public static final double LENS_FORWARD = 0.75;

    private static final EntityDataAccessor<Float> DATA_AIM_YAW = SynchedEntityData.defineId(SearchlightEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_AIM_PITCH = SynchedEntityData.defineId(SearchlightEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_LIT = SynchedEntityData.defineId(SearchlightEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_WOBBLE = SynchedEntityData.defineId(SearchlightEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_DAMAGE = SynchedEntityData.defineId(SearchlightEntity.class, EntityDataSerializers.FLOAT);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private BlockPos lightPos;

    public SearchlightEntity(EntityType<? extends SearchlightEntity> type, Level level) {
        super(type, level);
        this.noCulling = true; // the beam reaches far outside the searchlight
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_AIM_YAW, 0.0F);
        this.entityData.define(DATA_AIM_PITCH, -15.0F);
        this.entityData.define(DATA_LIT, false);
        this.entityData.define(DATA_WOBBLE, 0);
        this.entityData.define(DATA_DAMAGE, 0.0F);
    }

    // ------------------------------------------------------------------ aim

    /** Where the lamp points: yaw (like an entity's) and pitch (negative is up). */
    public float getAimYaw() {
        return this.entityData.get(DATA_AIM_YAW);
    }

    public float getAimPitch() {
        return this.entityData.get(DATA_AIM_PITCH);
    }

    public boolean isLit() {
        return this.entityData.get(DATA_LIT);
    }

    public int getWobble() {
        return this.entityData.get(DATA_WOBBLE);
    }

    public void setAim(float yaw, float pitch) {
        this.entityData.set(DATA_AIM_YAW, Mth.wrapDegrees(yaw));
        this.entityData.set(DATA_AIM_PITCH, Mth.clamp(pitch, -85.0F, 25.0F));
    }

    /** The lens, and the direction the beam shines. */
    public Vec3 lens() {
        Vec3 dir = beamDirection();
        Vec3 flat = new Vec3(dir.x, 0.0, dir.z).normalize();
        return position().add(0.0, LENS_HEIGHT, 0.0).add(flat.scale(LENS_FORWARD));
    }

    public Vec3 beamDirection() {
        return Vec3.directionFromRotation(getAimPitch(), getAimYaw());
    }

    /** Where the beam ends: the first block it hits, or its full reach. */
    public Vec3 beamEnd() {
        Vec3 from = lens();
        Vec3 to = from.add(beamDirection().scale(RANGE));
        HitResult hit = level().clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, this));
        return hit.getLocation();
    }

    // ------------------------------------------------------------------ riding

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player.isSecondaryUseActive() || isVehicle()) {
            return InteractionResult.PASS;
        }
        if (!level().isClientSide) {
            player.startRiding(this);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    /** Whoever is sitting at it (it isn't steered like a vehicle: it just follows where they look). */
    @Nullable
    public LivingEntity getOperator() {
        return getFirstPassenger() instanceof LivingEntity living ? living : null;
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction move) {
        if (!hasPassenger(passenger)) {
            return;
        }
        // the seat, on the right of the lamp, turning with it
        Vec3 seat = new Vec3(-1.3, 0.0, 0.25).yRot(-getAimYaw() * Mth.DEG_TO_RAD);
        move.accept(passenger, getX() + seat.x, getY() + 0.62 + passenger.getMyRidingOffset(), getZ() + seat.z);
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().isEmpty();
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    // ------------------------------------------------------------------ ticking

    @Override
    public void tick() {
        super.tick();
        // it sits on the ground
        if (!isNoGravity()) {
            setDeltaMovement(getDeltaMovement().add(0.0, -0.04, 0.0));
        }
        move(MoverType.SELF, getDeltaMovement());
        setDeltaMovement(getDeltaMovement().multiply(0.5, 0.98, 0.5));
        if (onGround()) {
            setDeltaMovement(getDeltaMovement().multiply(1.0, 0.0, 1.0));
        }
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        if (getWobble() > 0) {
            this.entityData.set(DATA_WOBBLE, getWobble() - 1);
        }
        if (this.entityData.get(DATA_DAMAGE) > 0.0F) {
            this.entityData.set(DATA_DAMAGE, Math.max(0.0F, this.entityData.get(DATA_DAMAGE) - 1.0F));
        }
        LivingEntity operator = getOperator();
        if (operator != null) {
            setAim(operator.getYRot(), operator.getXRot());
        }
        boolean lit = operator != null;
        if (lit != isLit()) {
            this.entityData.set(DATA_LIT, lit);
            level.playSound(null, getX(), getY() + 1.0, getZ(), lit ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_DEACTIVATE,
                    SoundSource.BLOCKS, 0.8F, lit ? 1.4F : 1.2F);
        }
        if (this.tickCount % 4 == 0) {
            if (lit) {
                shine(level, operator);
            } else {
                clearLight(level);
            }
        }
    }

    /** Lights up where the beam lands, and catches any Odo Island Monster standing in it. */
    private void shine(ServerLevel level, LivingEntity operator) {
        Vec3 from = lens();
        Vec3 dir = beamDirection();
        HitResult hit = level.clip(new ClipContext(from, from.add(dir.scale(RANGE)), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, this));
        Vec3 end = hit.getLocation();
        BlockPos light = null;
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            light = blockHit.getBlockPos().relative(blockHit.getDirection());
        }
        moveLight(level, light);

        double length = end.distanceTo(from);
        AABB area = new AABB(from, end).inflate(BEAM_RADIUS + 16.0);
        for (OdoIslandMonsterEntity odo : level.getEntitiesOfClass(OdoIslandMonsterEntity.class, area, e -> e.isAlive() && !e.isBurnt())) {
            AABB box = odo.getBoundingBox().inflate(BEAM_RADIUS);
            if (box.contains(from) || box.clip(from, from.add(dir.scale(length + 2.0))).isPresent()) {
                odo.spottedBy(operator);
            }
        }
    }

    private void moveLight(ServerLevel level, @Nullable BlockPos pos) {
        if (pos != null && pos.equals(this.lightPos)) {
            return;
        }
        clearLight(level);
        if (pos != null && level.isLoaded(pos) && level.getBlockState(pos).isAir()) {
            level.setBlock(pos, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15), 3);
            this.lightPos = pos;
        }
    }

    private void clearLight(Level level) {
        if (this.lightPos != null) {
            BlockState state = level.getBlockState(this.lightPos);
            if (state.is(Blocks.LIGHT)) {
                level.setBlock(this.lightPos, Blocks.AIR.defaultBlockState(), 3);
            }
            this.lightPos = null;
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide) {
            clearLight(level());
        }
        super.remove(reason);
    }

    // ------------------------------------------------------------------ breaking it

    /** Hits work like on a boat: it rocks, and enough of them (one in creative) knock it back into its item. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isInvulnerableTo(source) || source.is(DamageTypeTags.IS_FIRE)) {
            return false;
        }
        if (level().isClientSide || isRemoved()) {
            return true;
        }
        if (source.getEntity() != null && source.getEntity() == getFirstPassenger()) {
            return false;
        }
        this.entityData.set(DATA_WOBBLE, 10);
        this.entityData.set(DATA_DAMAGE, this.entityData.get(DATA_DAMAGE) + amount * 10.0F);
        markHurt();
        playSound(SoundEvents.IRON_GOLEM_DAMAGE, 0.5F, 1.4F);
        boolean creative = source.getEntity() instanceof Player player && player.getAbilities().instabuild;
        if (creative || this.entityData.get(DATA_DAMAGE) > 40.0F || source.is(DamageTypeTags.IS_EXPLOSION)) {
            ejectPassengers();
            if (!creative && level().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
                spawnAtLocation(new ItemStack(ModItems.SEARCHLIGHT.get()));
            }
            discard();
        }
        return true;
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(ModItems.SEARCHLIGHT.get());
    }

    // ------------------------------------------------------------------ saving & GeckoLib

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setAim(tag.getFloat("AimYaw"), tag.getFloat("AimPitch"));
        if (tag.contains("Light")) {
            // a light left behind when the world was saved with the lamp lit: cleared on the next tick
            this.lightPos = NbtUtils.readBlockPos(tag.getCompound("Light"));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("AimYaw", getAimYaw());
        tag.putFloat("AimPitch", getAimPitch());
        if (this.lightPos != null) {
            tag.put("Light", NbtUtils.writeBlockPos(this.lightPos));
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // the turret and lamp are turned directly by the model (SearchlightRenderer)
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
