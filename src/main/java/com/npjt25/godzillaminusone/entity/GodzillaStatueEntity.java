package com.npjt25.godzillaminusone.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Godzilla frozen in a pose from the film's posters, for screenshots and thumbnails. Statues never spawn on their
 * own and have no spawn egg: they're only made with /summon (e.g. {@code /summon godzillaminusone:godzilla_pose_sky_roar}).
 * They do nothing, don't fall and can't be hurt; a new statue turns to face the nearest player (unless the
 * /summon gives it a Rotation). Hit one in creative mode, or /kill it, to remove it.
 */
public class GodzillaStatueEntity extends Entity implements GeoEntity {
    public enum Stance {
        /** Roaring, one arm flung out, the glowing tail raised high round his side. */
        CHARGING_ROAR("pose_charging_roar", true),
        /** The tail swung round in front of him, its tip rising. */
        TAIL_WHIP("pose_tail_whip", false),
        /** Roaring up at the sky, arms spread. */
        SKY_ROAR("pose_sky_roar", false);

        public final String id;
        public final boolean glowingTail;
        final RawAnimation animation;

        Stance(String id, boolean glowingTail) {
            this.id = id;
            this.glowingTail = glowingTail;
            this.animation = RawAnimation.begin().thenLoop("animation.gojira." + id);
        }
    }

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Stance stance;
    private boolean placed;

    public GodzillaStatueEntity(EntityType<? extends GodzillaStatueEntity> type, Level level, Stance stance) {
        super(type, level);
        this.stance = stance;
        this.noPhysics = true;
        this.noCulling = true;
        setNoGravity(true);
    }

    public Stance getStance() {
        return this.stance;
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.placed && !level().isClientSide) {
            this.placed = true;
            Player player = level().getNearestPlayer(this, 128.0);
            if (player != null) {
                float yaw = (float) (Mth.atan2(player.getZ() - getZ(), player.getX() - getX()) * Mth.RAD_TO_DEG) - 90.0F;
                setYRot(yaw);
                this.yRotO = yaw;
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && !isRemoved() && source.getEntity() instanceof Player player && player.getAbilities().instabuild) {
            discard();
            return true;
        }
        return false;
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 320.0 * 320.0;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        // a /summon that sets its Rotation keeps it
        this.placed = tag.getBoolean("Placed") || tag.contains("Rotation");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putBoolean("Placed", this.placed);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "pose", 0, state -> state.setAndContinue(this.stance.animation)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
