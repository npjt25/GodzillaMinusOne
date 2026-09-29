package com.npjt25.godzillaminusone.entity;

import com.npjt25.godzillaminusone.Config;
import com.npjt25.godzillaminusone.GodzillaMinusOne;
import com.npjt25.godzillaminusone.network.FxPacket;
import com.npjt25.godzillaminusone.network.ModNetwork;
import com.npjt25.godzillaminusone.registry.ModDamageTypes;
import com.npjt25.godzillaminusone.registry.ModEntities;
import com.npjt25.godzillaminusone.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

/**
 * A nuclear bomb called in with the Nuclear Bomb item: it is dropped from high above its target and falls for
 * exactly {@link #FALL_TICKS} (20 seconds), with a red flare marking where it will land, a countdown for everyone
 * nearby and a whistle over its last seconds. Then it goes off like Godzilla's atomic blast; an Odo Island Monster
 * caught in it is burnt to a crisp instead of killed, and rises again as Godzilla.
 */
public class NuclearBombEntity extends Entity implements GeoEntity {
    public static final int FALL_TICKS = 20 * 20;
    private static final double DROP_HEIGHT = 200.0;
    private static final int WHISTLE_TICKS = 7 * 20;
    private static final double WARN_RADIUS = 320.0;

    private static final EntityDataAccessor<Float> DATA_TARGET_Y = SynchedEntityData.defineId(NuclearBombEntity.class, EntityDataSerializers.FLOAT);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private Vec3 target = Vec3.ZERO;
    private int fuse = FALL_TICKS;
    private double fallSpeed;
    @Nullable
    private UUID ownerId;

    public NuclearBombEntity(EntityType<? extends NuclearBombEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    /** Drops a bomb on {@code target}: it lands 20 seconds from now. */
    public static void drop(ServerLevel level, Vec3 target, @Nullable Entity owner) {
        drop(level, target, owner, FALL_TICKS);
    }

    public static NuclearBombEntity drop(ServerLevel level, Vec3 target, @Nullable Entity owner, int fallTicks) {
        NuclearBombEntity bomb = ModEntities.NUCLEAR_BOMB.get().create(level);
        if (bomb == null) {
            return null;
        }
        double top = Math.max(target.y + 8.0, Math.min(target.y + DROP_HEIGHT, level.getMaxBuildHeight() - 4.0));
        bomb.target = target;
        bomb.fuse = fallTicks;
        bomb.fallSpeed = (top - target.y) / fallTicks;
        bomb.ownerId = owner != null ? owner.getUUID() : null;
        bomb.moveTo(target.x, top, target.z, owner != null ? owner.getYRot() : 0.0F, 0.0F);
        bomb.entityData.set(DATA_TARGET_Y, (float) target.y);
        level.addFreshEntity(bomb);
        GodzillaMinusOne.LOGGER.debug("Nuclear bomb dropped on {} by {}", target, owner);
        return bomb;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_TARGET_Y, 0.0F);
    }

    public int getFuse() {
        return this.fuse;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientEffects();
            return;
        }
        ServerLevel level = (ServerLevel) level();
        this.fuse--;
        setDeltaMovement(0.0, -this.fallSpeed, 0.0);
        setPos(getX(), getY() - this.fallSpeed, getZ());
        if (this.fuse % 20 == 0 && this.fuse > 0) {
            warn(level, this.fuse / 20);
        }
        if (this.fuse == WHISTLE_TICKS) {
            level.playSound(null, this.target.x, this.target.y + 20.0, this.target.z, ModSounds.NUKE_WHISTLE.get(), SoundSource.HOSTILE, 16.0F, 1.0F);
        }
        boolean hitSomething = this.fuse < FALL_TICKS - 20 && !level.getBlockState(blockPosition()).getCollisionShape(level, blockPosition()).isEmpty();
        if (this.fuse <= 0 || hitSomething) {
            detonate(level, this.fuse <= 0 ? this.target : position());
        }
    }

    private void warn(ServerLevel level, int seconds) {
        Component message = Component.translatable("message." + GodzillaMinusOne.MODID + ".nuke_incoming", seconds)
                .withStyle(seconds <= 5 ? ChatFormatting.RED : ChatFormatting.GOLD, ChatFormatting.BOLD);
        for (ServerPlayer player : level.players()) {
            if (player.position().distanceToSqr(this.target.x, player.getY(), this.target.z) < WARN_RADIUS * WARN_RADIUS) {
                player.displayClientMessage(message, true);
            }
        }
    }

    private void detonate(ServerLevel level, Vec3 at) {
        Entity owner = this.ownerId != null ? level.getEntity(this.ownerId) : null;
        DamageSource source = ModDamageTypes.source(level, ModDamageTypes.NUCLEAR_BOMB, this, owner);
        Vec3 zero = Blasts.nuclear(level, at, true, source, this, null, Config.ATOMIC_BLAST_CRATER_RADIUS.get() > 0, true, getYRot());
        ModNetwork.sendNear(level, zero, 640.0, new FxPacket(FxPacket.Type.BLACK_RAIN, zero, 150.0F, 20.0F));
        discard();
    }

    /** A red flare marks where it will land; a thin trail of smoke follows it down. */
    private void clientEffects() {
        float targetY = this.entityData.get(DATA_TARGET_Y);
        if (this.tickCount % 6 == 0) {
            level().addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, getX(), targetY + 0.5, getZ(),
                    0.0, 0.07, 0.0);
        }
        if (this.tickCount % 2 == 0) {
            for (int i = 0; i < 3; i++) {
                level().addParticle(new DustParticleOptions(new Vector3f(1.0F, 0.1F, 0.05F), 2.0F),
                        getX() + (this.random.nextDouble() - 0.5) * 1.5, targetY + 0.3 + this.random.nextDouble(),
                        getZ() + (this.random.nextDouble() - 0.5) * 1.5, 0.0, 0.05, 0.0);
            }
            level().addParticle(ParticleTypes.SMOKE, getX(), getY() + 3.2, getZ(), 0.0, 0.02, 0.0);
        }
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 512.0 * 512.0;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.target = new Vec3(tag.getDouble("TargetX"), tag.getDouble("TargetY"), tag.getDouble("TargetZ"));
        this.fuse = tag.getInt("Fuse");
        this.fallSpeed = tag.getDouble("FallSpeed");
        this.ownerId = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        this.entityData.set(DATA_TARGET_Y, (float) this.target.y);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putDouble("TargetX", this.target.x);
        tag.putDouble("TargetY", this.target.y);
        tag.putDouble("TargetZ", this.target.z);
        tag.putInt("Fuse", this.fuse);
        tag.putDouble("FallSpeed", this.fallSpeed);
        if (this.ownerId != null) {
            tag.putUUID("Owner", this.ownerId);
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
