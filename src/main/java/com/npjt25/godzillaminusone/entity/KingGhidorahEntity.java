package com.npjt25.godzillaminusone.entity;

import com.npjt25.godzillaminusone.Config;
import com.npjt25.godzillaminusone.GodzillaMinusOne;
import com.npjt25.godzillaminusone.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;

/**
 * King Ghidorah: an enormous, ancient three-headed golden dragon, as tall as Godzilla Minus Zero (90.74 blocks: his
 * model, tools/make_ghidorah.py, drawn at {@link #RENDER_SCALE}), with Minus Zero's hitbox (20 x 80).
 *
 * <p>He hunts players, iron golems and villagers (and whoever hurts him) across 160 blocks, striding straight at them
 * and through what's built (as Godzilla does: only at Y 60 and up, {@code walkBreaksFromY}), and:
 * <ul>
 *   <li>bites what's in front of him with whichever of his three heads is nearest it, the other two staying busy;</li>
 *   <li>from 40 to 160 blocks off, fires his gravity beams: his necks brace, his heads aim, and each in turn
 *       (staggered) looses a crackling golden bolt that stops at what's in its way, hurts and lifts whatever it
 *       touches, and blasts and flings up the blocks where it lands;</li>
 *   <li>roars, all three heads at once, when he finds something to hunt, and now and then while he does.</li>
 * </ul>
 * Each head minds its own business between attacks (client.render.KingGhidorahModel).
 */
public class KingGhidorahEntity extends Monster implements GeoEntity {
    /** His model's height standing (tools/make_ghidorah.py: the tops of his middle head's horns, idling). */
    public static final double MODEL_HEIGHT = 117.1;
    /** As tall as Godzilla Minus Zero (twice Minus One's standing height). */
    public static final double REAL_HEIGHT = GodzillaEntity.REAL_HEIGHT * GodzillaMinusZeroEntity.SCALE;
    /** How big his renderer draws him (sixteenths of a block per model unit). */
    public static final float RENDER_SCALE = (float) (REAL_HEIGHT * 16.0 / MODEL_HEIGHT);
    /** Blocks per model unit. */
    public static final double UNIT = RENDER_SCALE / 16.0;

    public static final int ACTION_NONE = 0;
    public static final int ACTION_ROAR = 1;
    public static final int ACTION_BITE = 2;   // + the head: 2 middle, 3 right, 4 left
    public static final int ACTION_BEAM = 5;

    /** Ticks each animation lasts (animation.king_ghidorah.*). */
    public static final int ROAR_TICKS = 64;
    public static final int BITE_TICKS = 26;
    public static final int BEAM_TICKS = 84;
    /** The tick his roar sounds (the middle head throws itself up), the tick a bite's jaws snap shut. */
    private static final int ROAR_SOUND_AT = 6;
    public static final int BITE_SNAP_AT = 12;
    /** The tick his beams stop (the animation's BEAM_END). */
    public static final int BEAM_END_AT = 66;

    public static final double BEAM_MIN_RANGE = 40.0;
    public static final double BEAM_RANGE = 160.0;
    private static final int BEAM_COOLDOWN = 15 * 20;
    private static final int ROAR_INTERVAL = 45 * 20;
    private static final double BITE_RADIUS = 18.0;
    private static final float BEAM_DAMAGE = 14.0F;
    private static final double BEAM_RADIUS = 2.5;

    /**
     * His three heads, and where their mouths are (model units: his right, up, forward) as they fire their beams
     * (animation.king_ghidorah.gravity_beam at 2 s), and as they snap shut on a bite (bite_*, 0.62 s), and when their
     * beams fire (animation seconds).
     */
    public enum Head {
        MIDDLE("c", 0.0, 90.9, 41.8, 0.0, 70.5, 55.2, 1.2),
        RIGHT("r", 20.0, 85.6, 38.7, 26.2, 66.3, 48.6, 1.35),
        LEFT("l", -20.0, 85.9, 38.8, -26.2, 66.3, 48.6, 1.5);

        public final String bone;
        final Vec3 beamMouth;
        final Vec3 biteMouth;
        /** The tick its beam fires. */
        public final int fireAt;

        Head(String bone, double bx, double by, double bz, double kx, double ky, double kz, double fireSeconds) {
            this.bone = bone;
            this.beamMouth = new Vec3(bx, by, bz).scale(UNIT);
            this.biteMouth = new Vec3(kx, ky, kz).scale(UNIT);
            this.fireAt = (int) Math.round(fireSeconds * 20);
        }

        /** Each head's beam lands a little apart from the others: {right, up} blocks off the aim. */
        public Vec3 spread(int tick) {
            double wobble = Math.sin(tick * 0.31 + ordinal() * 2.1) * 1.5;
            return switch (this) {
                case MIDDLE -> new Vec3(wobble, 0.0, 0.0);
                case RIGHT -> new Vec3(3.5 + wobble, -0.5, 0.0);
                case LEFT -> new Vec3(-3.5 + wobble, -0.5, 0.0);
            };
        }
    }

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.king_ghidorah.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.king_ghidorah.walk");

    private static final EntityDataAccessor<Integer> DATA_ACTION = SynchedEntityData.defineId(KingGhidorahEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> DATA_AIM = SynchedEntityData.defineId(KingGhidorahEntity.class, EntityDataSerializers.VECTOR3);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final ServerBossEvent bossEvent = new ServerBossEvent(
            Component.translatable("boss." + GodzillaMinusOne.MODID + ".king_ghidorah"),
            BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.PROGRESS);

    private int actionTicks;
    private int clientActionStart;
    private int beamCooldown = 60;
    private int roarCooldown;
    private int biteCooldown;
    @Nullable
    private LivingEntity lastTarget;
    @Nullable
    private Integer walkFloorOverride;
    @Nullable
    private Vec3 walkTarget;
    /** (client) Where each head's mouth was last drawn, relative to him (client.render.KingGhidorahRenderer). */
    public final Vec3[] drawnMouths = new Vec3[3];

    public KingGhidorahEntity(EntityType<? extends KingGhidorahEntity> type, Level level) {
        super(type, level);
        this.xpReward = 500;
        this.setMaxUpStep(6.0F);
        this.setPersistenceRequired();
        this.moveControl = new GodzillaEntity.GodzillaMoveControl(this, 4.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 2000.0D)
                .add(Attributes.ATTACK_DAMAGE, 60.0D)
                .add(Attributes.ATTACK_KNOCKBACK, 3.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.FOLLOW_RANGE, BEAM_RANGE)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.ARMOR, 20.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_ACTION, ACTION_NONE);
        this.entityData.define(DATA_AIM, new Vector3f());
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (DATA_ACTION.equals(accessor)) {
            this.clientActionStart = this.tickCount;
        }
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(2, new HuntGoal(this));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 64.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new Prey<>(this, Player.class));
        this.targetSelector.addGoal(3, new Prey<>(this, IronGolem.class));
        this.targetSelector.addGoal(4, new Prey<>(this, AbstractVillager.class));
    }

    // ------------------------------------------------------------------ state

    /** What he's doing: {@link #ACTION_NONE}, {@link #ACTION_ROAR}, a bite ({@link #ACTION_BITE} + a head) or his beam. */
    public int getAction() {
        return this.entityData.get(DATA_ACTION);
    }

    /** Ticks into what he's doing (on either side). */
    public int getActionTicks() {
        return level().isClientSide ? this.tickCount - this.clientActionStart : this.actionTicks;
    }

    /** Where his beams are aimed (world). */
    public Vec3 getAim() {
        Vector3f v = this.entityData.get(DATA_AIM);
        return new Vec3(v.x(), v.y(), v.z());
    }

    /** Whether a head's beam is firing now. */
    public boolean isFiring(Head head) {
        int t = getActionTicks();
        return getAction() == ACTION_BEAM && t >= head.fireAt && t < BEAM_END_AT + head.ordinal() * 2;
    }

    public boolean isBusy() {
        return getAction() != ACTION_NONE;
    }

    /** The lowest Y his stride destroys anything at (walkBreaksFromY; a game test sets its own). */
    public int walkFloorY() {
        return this.walkFloorOverride != null ? this.walkFloorOverride : Config.WALK_BREAKS_FROM_Y.get();
    }

    public void setWalkFloorY(@Nullable Integer y) {
        this.walkFloorOverride = y;
    }

    /** World position of a point on him: {@code side} blocks to his right, {@code up}, {@code forward}. */
    public Vec3 locator(Vec3 local) {
        Vec3 fwd = Vec3.directionFromRotation(0.0F, this.yBodyRot);
        Vec3 right = new Vec3(-fwd.z, 0.0, fwd.x);
        return position().add(fwd.scale(local.z)).add(right.scale(local.x)).add(0.0, local.y, 0.0);
    }

    public Vec3 beamMouth(Head head) {
        return locator(head.beamMouth);
    }

    /** Where a head's beam ends: at its aim (spread a little from the others'), or at the first block in the way. */
    public Vec3 beamEnd(Head head, Vec3 from, Vec3 aim) {
        Vec3 fwd = aim.subtract(from);
        Vec3 right = new Vec3(-fwd.z, 0.0, fwd.x).normalize();
        Vec3 off = head.spread(this.tickCount);
        Vec3 to = aim.add(right.scale(off.x)).add(0.0, off.y, 0.0);
        Vec3 far = from.add(to.subtract(from).normalize().scale(BEAM_RANGE + 40.0));
        HitResult hit = level().clip(new ClipContext(from, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        return hit.getType() == HitResult.Type.MISS ? far : hit.getLocation();
    }

    // ------------------------------------------------------------------ ticking

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            beamSparks();
            return;
        }
        ServerLevel level = (ServerLevel) level();
        this.bossEvent.setProgress(Mth.clamp(getHealth() / getMaxHealth(), 0.0F, 1.0F));
        if (this.beamCooldown > 0) {
            this.beamCooldown--;
        }
        if (this.roarCooldown > 0) {
            this.roarCooldown--;
        }
        if (this.biteCooldown > 0) {
            this.biteCooldown--;
        }
        LivingEntity target = getTarget();
        if (target != null && target != this.lastTarget && !isBusy() && this.roarCooldown <= 0) {
            startAction(ACTION_ROAR);       // (he lets what he's found to hunt know it)
        }
        this.lastTarget = target;
        int action = getAction();
        if (action != ACTION_NONE) {
            this.actionTicks++;
            if (action == ACTION_ROAR && this.actionTicks == ROAR_SOUND_AT) {
                roar(level, 1.0F);
            } else if (action >= ACTION_BITE && action < ACTION_BEAM && this.actionTicks == BITE_SNAP_AT) {
                bite(level, Head.values()[action - ACTION_BITE]);
            } else if (action == ACTION_BEAM) {
                beamTick(level, target);
            }
            int length = action == ACTION_ROAR ? ROAR_TICKS : action == ACTION_BEAM ? BEAM_TICKS : BITE_TICKS;
            if (this.actionTicks >= length) {
                this.entityData.set(DATA_ACTION, ACTION_NONE);
            }
        }
        if (this.walkTarget != null && target == null && !isBusy()) {
            if (position().distanceToSqr(this.walkTarget.x, getY(), this.walkTarget.z) < 16.0) {
                this.walkTarget = null;
            } else {
                getMoveControl().setWantedPosition(this.walkTarget.x, this.walkTarget.y, this.walkTarget.z, 1.0);
            }
        }
        if (this.tickCount % 4 == 0 && getDeltaMovement().horizontalDistanceSqr() > 1.0E-4) {
            smashBlocks(level);
        }
    }

    /** Stride to a point and stop there (the harness; with nothing to hunt). */
    public void walkTo(@Nullable Vec3 at) {
        this.walkTarget = at;
    }

    private void startAction(int action) {
        this.actionTicks = 0;
        this.entityData.set(DATA_ACTION, action);
        if (action == ACTION_ROAR) {
            this.roarCooldown = ROAR_INTERVAL;
        }
    }

    // ------------------------------------------------------------------ the roar

    private void roar(ServerLevel level, float volume) {
        // (loud enough to carry 400 blocks, from up where his heads are)
        level.playSound(null, getX(), getY() + REAL_HEIGHT * 0.8, getZ(), ModSounds.KING_GHIDORAH_ROAR.get(),
                SoundSource.HOSTILE, 25.0F * volume, 0.95F + this.random.nextFloat() * 0.1F);
    }

    /** Roar now (a game test, or the harness). */
    public void startRoar() {
        startAction(ACTION_ROAR);
    }

    // ------------------------------------------------------------------ bites

    /** The head nearest a point: the middle one unless it's well off to his right or left. */
    public Head nearestHead(Vec3 point) {
        Vec3 fwd = Vec3.directionFromRotation(0.0F, this.yBodyRot);
        Vec3 right = new Vec3(-fwd.z, 0.0, fwd.x);
        double side = point.subtract(position()).dot(right);
        return side > 10.0 ? Head.RIGHT : side < -10.0 ? Head.LEFT : Head.MIDDLE;
    }

    /** Where a head's bite reaches: around the point below where its jaws snap shut, from the ground up to them. */
    boolean inBiteReach(Entity target, Head head) {
        Vec3 jaws = locator(head.biteMouth);
        Vec3 c = target.getBoundingBox().getCenter();
        double dx = c.x - jaws.x;
        double dz = c.z - jaws.z;
        return dx * dx + dz * dz < BITE_RADIUS * BITE_RADIUS && c.y < jaws.y + 10.0 && c.y > getY() - 6.0;
    }

    public void startBite(Head head) {
        this.biteCooldown = 34;
        startAction(ACTION_BITE + head.ordinal());
    }

    private void bite(ServerLevel level, Head head) {
        Vec3 jaws = locator(head.biteMouth);
        level.playSound(null, jaws.x, jaws.y, jaws.z, ModSounds.GODZILLA_BITE.get(), SoundSource.HOSTILE, 10.0F, 1.15F);
        float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
        AABB zone = new AABB(jaws.x - BITE_RADIUS, getY() - 6.0, jaws.z - BITE_RADIUS, jaws.x + BITE_RADIUS, jaws.y + 10.0, jaws.z + BITE_RADIUS);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, zone, e -> e != this && e.isAlive()
                && !(e instanceof KingGhidorahEntity) && !e.isSpectator() && inBiteReach(e, head))) {
            if (e.hurt(damageSources().mobAttack(this), damage)) {
                Vec3 away = e.position().subtract(position()).multiply(1, 0, 1).normalize();
                e.setDeltaMovement(e.getDeltaMovement().add(away.scale(2.0)).add(0.0, 0.8, 0.0));
                e.hurtMarked = true;
            }
        }
        if (Config.GODZILLA_BREAKS_BLOCKS.get() && ForgeEventFactory.getMobGriefingEvent(level, this)) {
            Vec3 push = Vec3.directionFromRotation(0.0F, this.yBodyRot).scale(0.6).add(0.0, 0.4, 0.0);
            Destruction.tearThrough(level, this, jaws, 7.0, push);
        }
    }

    // ------------------------------------------------------------------ gravity beams

    public boolean canFireBeam(LivingEntity target) {
        double d = target.position().subtract(position()).horizontalDistance();
        return this.beamCooldown <= 0 && d >= BEAM_MIN_RANGE && d <= BEAM_RANGE
                && clearLine(beamMouth(Head.MIDDLE), target.getBoundingBox().getCenter());
    }

    private boolean clearLine(Vec3 from, Vec3 to) {
        return level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }

    /** Fire his gravity beams at a point (a game test, or the harness; the hunt fires them at his target). */
    public void startBeam(Vec3 aim) {
        this.entityData.set(DATA_AIM, aim.toVector3f());
        this.beamCooldown = BEAM_COOLDOWN;
        startAction(ACTION_BEAM);
    }

    private void beamTick(ServerLevel level, @Nullable LivingEntity target) {
        int t = this.actionTicks;
        if (target != null && target.isAlive() && t < BEAM_END_AT) {
            // his aim follows his target, a step behind
            Vec3 aim = getAim().lerp(target.getBoundingBox().getCenter(), 0.15);
            this.entityData.set(DATA_AIM, aim.toVector3f());
        }
        Vec3 aim = getAim();
        for (Head head : Head.values()) {
            if (t == head.fireAt) {
                Vec3 m = beamMouth(head);
                level.playSound(null, m.x, m.y, m.z, ModSounds.KING_GHIDORAH_BEAM.get(), SoundSource.HOSTILE, 20.0F,
                        0.9F + head.ordinal() * 0.08F);
            }
            if (!isFiring(head) || (t - head.fireAt) % 4 != 0) {
                continue;
            }
            Vec3 from = beamMouth(head);
            Vec3 end = beamEnd(head, from, aim);
            hurtAlong(level, from, end);
            if ((t - head.fireAt) % 8 == 0) {
                impact(level, end, from);
            }
        }
    }

    /** (client) Sparks and smoke where his beams land, and crackling along them. */
    private void beamSparks() {
        if (getAction() != ACTION_BEAM) {
            return;
        }
        Vec3 aim = getAim();
        for (Head head : Head.values()) {
            if (!isFiring(head)) {
                continue;
            }
            Vec3 drawn = this.drawnMouths[head.ordinal()];
            Vec3 from = drawn != null ? position().add(drawn) : beamMouth(head);
            Vec3 end = beamEnd(head, from, aim);
            for (int i = 0; i < 6; i++) {
                level().addParticle(ParticleTypes.ELECTRIC_SPARK, end.x + this.random.nextGaussian() * 1.5,
                        end.y + this.random.nextDouble() * 2.0, end.z + this.random.nextGaussian() * 1.5,
                        this.random.nextGaussian() * 0.6, 0.4 + this.random.nextDouble() * 0.8, this.random.nextGaussian() * 0.6);
            }
            if (this.tickCount % 3 == 0) {
                level().addParticle(ParticleTypes.EXPLOSION, end.x, end.y + 1.0, end.z, 0.0, 0.0, 0.0);
            }
            level().addParticle(ParticleTypes.LARGE_SMOKE, end.x, end.y + 1.0, end.z, 0.0, 0.25, 0.0);
            Vec3 along = from.lerp(end, this.random.nextDouble());
            level().addParticle(ParticleTypes.ELECTRIC_SPARK, along.x, along.y, along.z, 0.0, 0.0, 0.0);
        }
    }

    /** Whatever the beam touches is hurt and lifted off its feet. */
    private void hurtAlong(ServerLevel level, Vec3 from, Vec3 to) {
        AABB box = new AABB(from, to).inflate(BEAM_RADIUS);
        Vec3 d = to.subtract(from);
        double len2 = d.lengthSqr();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != this && e.isAlive()
                && !(e instanceof KingGhidorahEntity) && !e.isSpectator())) {
            Vec3 c = e.getBoundingBox().getCenter();
            double f = len2 > 0 ? Mth.clamp(c.subtract(from).dot(d) / len2, 0.0, 1.0) : 0.0;
            Vec3 nearest = from.add(d.scale(f));
            double reach = BEAM_RADIUS + e.getBbWidth() / 2.0;
            if (nearest.distanceToSqr(c) > reach * reach) {
                continue;
            }
            if (e.hurt(damageSources().indirectMagic(this, this), BEAM_DAMAGE)) {
                Vec3 push = d.normalize().scale(0.6);
                e.setDeltaMovement(e.getDeltaMovement().multiply(0.5, 0.0, 0.5).add(push.x, 0.9, push.z));
                e.hurtMarked = true;
            }
        }
    }

    /** Where a beam lands: a blast, and the blocks there torn loose and flung up. */
    private void impact(ServerLevel level, Vec3 at, Vec3 from) {
        level.explode(this, at.x, at.y, at.z, 2.5F, Level.ExplosionInteraction.NONE);
        if (Config.GODZILLA_BREAKS_BLOCKS.get() && ForgeEventFactory.getMobGriefingEvent(level, this)) {
            Vec3 back = from.subtract(at).multiply(1, 0, 1).normalize().scale(0.25);
            Destruction.tearThrough(level, this, at, 4.0, back.add(0.0, 1.1, 0.0));
        }
    }

    // ------------------------------------------------------------------ walking through things

    private void smashBlocks(ServerLevel level) {
        if (!Config.GODZILLA_BREAKS_BLOCKS.get() || !ForgeEventFactory.getMobGriefingEvent(level, this)) {
            return;
        }
        Vec3 forward = Vec3.directionFromRotation(0.0F, getYRot());
        // (his legs, hips and folded wings: half his height; his necks rise above what he walks through)
        AABB box = getBoundingBox().expandTowards(forward.x * 4.0, 0.0, forward.z * 4.0).inflate(1.0, 0.0, 1.0);
        int minY = Math.max(Mth.floor(getY()) + 1, walkFloorY());
        int maxY = Mth.floor(getY() + REAL_HEIGHT * 0.55);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int broken = 0;
        for (int y = minY; y <= maxY; y++) {
            for (int x = Mth.floor(box.minX); x <= Mth.floor(box.maxX); x++) {
                for (int z = Mth.floor(box.minZ); z <= Mth.floor(box.maxZ); z++) {
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

    // ------------------------------------------------------------------ the rest

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.getEntity() instanceof KingGhidorahEntity) {
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

    @Override
    public int getAmbientSoundInterval() {
        return 240;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.KING_GHIDORAH_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.KING_GHIDORAH_AMBIENT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.KING_GHIDORAH_ROAR.get();
    }

    @Override
    protected float getSoundVolume() {
        return 12.0F;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(ModSounds.GODZILLA_STEP.get(), 4.0F, 0.85F + this.random.nextFloat() * 0.1F);
    }

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

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("BeamCooldown", this.beamCooldown);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.beamCooldown = tag.getInt("BeamCooldown");
        if (hasCustomName()) {
            this.bossEvent.setName(getDisplayName());
        }
    }

    // ------------------------------------------------------------------ GeckoLib

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 6, this::mainAnimation)
                .setAnimationSpeedHandler(KingGhidorahEntity::strideSpeed));
        controllers.add(new AnimationController<>(this, "action", 4, this::actionAnimation));
    }

    /** Plays his walk as fast as he's actually going, so his feet don't slide (about 0.6 blocks a tick, at its own pace). */
    private static double strideSpeed(KingGhidorahEntity ghidorah) {
        double speed = ghidorah.getDeltaMovement().horizontalDistance();
        return speed < 0.02 ? 1.0 : Mth.clamp(speed / 0.6, 0.4, 1.6);
    }

    private PlayState mainAnimation(AnimationState<KingGhidorahEntity> state) {
        return state.setAndContinue(state.isMoving() && !isBusy() ? WALK : IDLE);
    }

    private static final RawAnimation[] ACTIONS = {null,
            RawAnimation.begin().thenPlay("animation.king_ghidorah.roar"),
            RawAnimation.begin().thenPlay("animation.king_ghidorah.bite_c"),
            RawAnimation.begin().thenPlay("animation.king_ghidorah.bite_r"),
            RawAnimation.begin().thenPlay("animation.king_ghidorah.bite_l"),
            RawAnimation.begin().thenPlay("animation.king_ghidorah.gravity_beam")};

    private int playing = ACTION_NONE;

    private PlayState actionAnimation(AnimationState<KingGhidorahEntity> state) {
        int action = getAction();
        if (action == ACTION_NONE) {
            this.playing = ACTION_NONE;
            return PlayState.STOP;
        }
        if (action != this.playing) {
            this.playing = action;
            state.getController().forceAnimationReset();
        }
        return state.setAndContinue(ACTIONS[action]);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    // ------------------------------------------------------------------ AI

    /** Who he hunts: players, iron golems and villagers, across his whole range. */
    static class Prey<T extends LivingEntity> extends NearestAttackableTargetGoal<T> {
        Prey(KingGhidorahEntity ghidorah, Class<T> type) {
            super(ghidorah, type, 20, true, false, e -> !(e instanceof Player p && (p.isCreative() || p.isSpectator())));
        }

        @Override
        protected AABB getTargetSearchArea(double range) {
            return this.mob.getBoundingBox().inflate(range, range / 2.0, range);
        }
    }

    /** Strides straight at his target; bites it when it's in reach of a head, fires his beams when it's far. */
    static class HuntGoal extends Goal {
        private final KingGhidorahEntity ghidorah;

        HuntGoal(KingGhidorahEntity ghidorah) {
            this.ghidorah = ghidorah;
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = this.ghidorah.getTarget();
            return target != null && target.isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = this.ghidorah.getTarget();
            if (target == null) {
                return;
            }
            this.ghidorah.getLookControl().setLookAt(target, 10.0F, 10.0F);
            if (this.ghidorah.isBusy()) {
                this.ghidorah.getNavigation().stop();
                this.ghidorah.getMoveControl().setWantedPosition(this.ghidorah.getX(), this.ghidorah.getY(), this.ghidorah.getZ(), 0.0);
                if (this.ghidorah.getAction() == ACTION_BEAM) {
                    turnTowards(target);
                }
                return;
            }
            Head head = this.ghidorah.nearestHead(target.position());
            if (this.ghidorah.biteCooldown <= 0 && this.ghidorah.inBiteReach(target, head)) {
                this.ghidorah.startBite(head);
                return;
            }
            if (this.ghidorah.canFireBeam(target)) {
                turnTowards(target);
                this.ghidorah.startBeam(target.getBoundingBox().getCenter());
                return;
            }
            this.ghidorah.getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), 1.0);
        }

        private void turnTowards(Entity target) {
            double dx = target.getX() - this.ghidorah.getX();
            double dz = target.getZ() - this.ghidorah.getZ();
            float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
            float turned = Mth.approachDegrees(this.ghidorah.getYRot(), yaw, 3.0F);
            this.ghidorah.setYRot(turned);
            this.ghidorah.setYBodyRot(turned);
        }
    }
}
