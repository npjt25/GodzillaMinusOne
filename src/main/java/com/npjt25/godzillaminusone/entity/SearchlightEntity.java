package com.npjt25.godzillaminusone.entity;

import com.npjt25.godzillaminusone.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
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

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * A WWII searchlight. Right-click to sit at it: it turns and tilts with where you look, and while someone is
 * manning it the lamp is lit: a cone of light reaching {@link #RANGE} blocks that widens as it goes
 * ({@link #beamRadius}), lighting up a wide pool around wherever it lands (a ring of light sources, {@link #spotRadius}).
 * An Odo Island Monster caught in the beam goes berserk and comes for whoever is working the light. Hit it like a
 * boat to knock it back into its item.
 */
public class SearchlightEntity extends Entity implements GeoEntity {
    public static final double RANGE = 160.0;
    /** The ring of light sources around where the beam lands: how many, and at most how far from the middle. */
    private static final int SPOT_LIGHTS = 8;
    private static final double MAX_SPOT_LIGHT_RING = 7.0;
    /** Where the lamp's lens is (blocks up from the base, and ahead of the lamp's pivot). */
    public static final double LENS_HEIGHT = 1.25;
    public static final double LENS_FORWARD = 0.75;

    private static final EntityDataAccessor<Float> DATA_AIM_YAW = SynchedEntityData.defineId(SearchlightEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_AIM_PITCH = SynchedEntityData.defineId(SearchlightEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_LIT = SynchedEntityData.defineId(SearchlightEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_WOBBLE = SynchedEntityData.defineId(SearchlightEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_DAMAGE = SynchedEntityData.defineId(SearchlightEntity.class, EntityDataSerializers.FLOAT);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** The light sources (light blocks) it has put where the beam lands. */
    private final Set<BlockPos> lights = new HashSet<>();

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

    /** How wide the cone of light is (blocks from its axis) this far from the lens. */
    public static double beamRadius(double distance) {
        return 0.8 + distance * 0.06;
    }

    /** How wide the pool of light is where a beam this long lands. */
    public static double spotRadius(double length) {
        return 2.5 + beamRadius(length) * 1.4;
    }

    /** Where the beam ends: the first block it hits, or its full reach. */
    public Vec3 beamEnd() {
        return beamHit(level()).getLocation();
    }

    /** What the beam hits (for where to draw the pool of light, and which way it faces). */
    public HitResult beamHit() {
        return beamHit(level());
    }

    private HitResult beamHit(Level level) {
        Vec3 from = lens();
        Vec3 dir = beamDirection();
        Vec3 to = from.add(dir.scale(loadedReach(level, from, dir, RANGE)));
        return level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, this));
    }

    /**
     * How far a ray from {@code from} can go (up to {@code range}) before it leaves the loaded chunks: a raycast into
     * unloaded chunks would load them, on the server synchronously.
     */
    public static double loadedReach(Level level, Vec3 from, Vec3 dir, double range) {
        for (double d = 8.0; d < range + 8.0; d += 8.0) {
            if (!level.hasChunkAt(BlockPos.containing(from.add(dir.scale(Math.min(d, range)))))) {
                return Math.max(0.0, d - 8.0);
            }
        }
        return range;
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

    /** Lights up a pool around where the beam lands, and catches any Odo Island Monster standing in the cone. */
    private void shine(ServerLevel level, LivingEntity operator) {
        Vec3 from = lens();
        Vec3 dir = beamDirection();
        HitResult hit = beamHit(level);
        Vec3 end = hit.getLocation();
        double length = end.distanceTo(from);
        Set<BlockPos> wanted = new HashSet<>();
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            Direction face = blockHit.getDirection();
            addLight(level, wanted, blockHit.getBlockPos().relative(face), face);
            // a ring of lights around it, in the plane of the face it lands on
            Vec3 normal = Vec3.atLowerCornerOf(face.getNormal());
            Vec3 u = Math.abs(normal.y) > 0.5 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
            Vec3 v = normal.cross(u);
            double ring = Math.min(MAX_SPOT_LIGHT_RING, spotRadius(length) * 0.6);
            Vec3 middle = end.add(normal.scale(0.5));
            for (int i = 0; i < SPOT_LIGHTS; i++) {
                double angle = Math.PI * 2.0 * i / SPOT_LIGHTS;
                Vec3 p = middle.add(u.scale(Math.cos(angle) * ring)).add(v.scale(Math.sin(angle) * ring));
                addLight(level, wanted, BlockPos.containing(p), face);
            }
        }
        moveLights(level, wanted);

        AABB area = new AABB(from, end).inflate(beamRadius(length) + 16.0);
        for (OdoIslandMonsterEntity odo : level.getEntitiesOfClass(OdoIslandMonsterEntity.class, area, e -> e.isAlive() && !e.isBurnt())) {
            // how far along the beam he is, and how wide the cone is there
            Vec3 centre = odo.getBoundingBox().getCenter();
            double along = Mth.clamp(centre.subtract(from).dot(dir), 0.0, length + 2.0);
            AABB box = odo.getBoundingBox().inflate(beamRadius(along));
            if (box.contains(from) || box.clip(from, from.add(dir.scale(length + 2.0))).isPresent()) {
                odo.spottedBy(operator);
            }
        }
    }

    /** The air block at (or just in front of, along {@code face}) {@code pos} gets a light, if it's loaded. */
    private static void addLight(ServerLevel level, Set<BlockPos> wanted, BlockPos pos, Direction face) {
        for (int step = 0; step < 3; step++) {
            BlockPos at = pos.relative(face, step);
            if (!level.isLoaded(at)) {
                return;
            }
            BlockState state = level.getBlockState(at);
            if (state.isAir() || state.is(Blocks.LIGHT)) {
                wanted.add(at.immutable());
                return;
            }
        }
    }

    /** Puts light blocks where they're wanted now and takes away the ones that aren't. */
    private void moveLights(ServerLevel level, Set<BlockPos> wanted) {
        for (Iterator<BlockPos> it = this.lights.iterator(); it.hasNext(); ) {
            BlockPos pos = it.next();
            if (!wanted.contains(pos)) {
                removeLight(level, pos);
                it.remove();
            }
        }
        for (BlockPos pos : wanted) {
            if (!this.lights.contains(pos) && level.getBlockState(pos).isAir()) {
                level.setBlock(pos, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15), 3);
                this.lights.add(pos);
            }
        }
    }

    private static void removeLight(Level level, BlockPos pos) {
        // (a light left in a chunk that has since unloaded stays: loading the chunk just for it isn't worth it)
        if (level.hasChunkAt(pos) && level.getBlockState(pos).is(Blocks.LIGHT)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    private void clearLight(Level level) {
        for (BlockPos pos : this.lights) {
            removeLight(level, pos);
        }
        this.lights.clear();
    }

    /** For the game tests: the light sources it has put down. */
    public Set<BlockPos> getLights() {
        return this.lights;
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
        // lights left behind when the world was saved with the lamp lit: cleared on the next tick
        if (tag.contains("Light")) {
            this.lights.add(NbtUtils.readBlockPos(tag.getCompound("Light")));   // (saved by older versions)
        }
        ListTag saved = tag.getList("Lights", Tag.TAG_COMPOUND);
        for (int i = 0; i < saved.size(); i++) {
            this.lights.add(NbtUtils.readBlockPos(saved.getCompound(i)));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("AimYaw", getAimYaw());
        tag.putFloat("AimPitch", getAimPitch());
        if (!this.lights.isEmpty()) {
            ListTag saved = new ListTag();
            for (BlockPos pos : this.lights) {
                saved.add(NbtUtils.writeBlockPos(pos));
            }
            tag.put("Lights", saved);
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
