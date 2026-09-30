package com.npjt25.godzillaminusone.entity;

import com.npjt25.godzillaminusone.registry.ModItems;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Naval mine (goji:mine_spawn). Floats on water and can be leashed and towed. Hitting it by hand (or with a tool)
 * only rocks it, and a few hits knock it loose into its item. Anything else that hurts it (an arrow, an explosion,
 * Godzilla's footsteps, his radiation...) sets it off, and so does a Remote Detonator pointed at it.
 */
public class NavalMineEntity extends Mob implements GeoEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.mine.idle");
    /** Hits that knock it loose into its item (one in creative). */
    public static final int HITS_TO_PICK_UP = 4;
    /** Hits further apart than this start the count again. */
    private static final int HIT_MEMORY = 60;

    private static final EntityDataAccessor<Integer> DATA_WOBBLE = SynchedEntityData.defineId(NavalMineEntity.class, EntityDataSerializers.INT);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private boolean detonated;
    private int hits;
    private int lastHitTick;

    public NavalMineEntity(EntityType<? extends NavalMineEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 3.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_WOBBLE, 0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
    }

    /** Ticks left of the rocking after a hit (for the renderer). */
    public int getWobble() {
        return this.entityData.get(DATA_WOBBLE);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && getWobble() > 0) {
            this.entityData.set(DATA_WOBBLE, getWobble() - 1);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isInvulnerableTo(source) || isRemoved()) {
            return false;
        }
        if (source.getDirectEntity() instanceof Player player && source.getEntity() == player) {
            // Mined by hand: it rocks with a clank, and a few hits knock it loose.
            if (level().isClientSide) {
                return true;
            }
            this.hits = this.tickCount - this.lastHitTick > HIT_MEMORY ? 1 : this.hits + 1;
            this.lastHitTick = this.tickCount;
            this.entityData.set(DATA_WOBBLE, 10);
            playSound(SoundEvents.CHAIN_HIT, 1.0F, 0.7F);
            boolean creative = player.getAbilities().instabuild;
            if (creative || this.hits >= HITS_TO_PICK_UP) {
                playSound(SoundEvents.CHAIN_BREAK, 1.0F, 0.7F);
                if (!creative && level().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
                    spawnAtLocation(new ItemStack(ModItems.NAVAL_MINE_SPAWN_EGG.get()));
                }
                discard();
            }
            return true;
        }
        return super.hurt(source, amount);
    }

    /** Sets it off (with a Remote Detonator, or anything else), blaming {@code by}. */
    public void detonate(@Nullable Entity by) {
        if (level() instanceof ServerLevel level && !this.detonated) {
            this.detonated = true;
            Blasts.navalMine(level, position(), this, by);
            discard();
        }
    }

    @Override
    public void die(DamageSource source) {
        if (level() instanceof ServerLevel && !this.detonated) {
            detonate(source.getEntity());
            return;
        }
        super.die(source);
    }

    // It's a lump of steel: it doesn't cry out when it's hit.
    @Nullable
    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return null;
    }

    @Nullable
    @Override
    protected SoundEvent getDeathSound() {
        return null;
    }

    @Nullable
    @Override
    public ItemStack getPickResult() {
        return new ItemStack(ModItems.NAVAL_MINE_SPAWN_EGG.get());
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
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
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "idle", 0, state -> state.setAndContinue(IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
