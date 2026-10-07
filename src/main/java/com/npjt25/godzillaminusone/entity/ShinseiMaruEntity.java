package com.npjt25.godzillaminusone.entity;

import com.npjt25.godzillaminusone.menu.ShinseiMaruMenu;
import com.npjt25.godzillaminusone.network.FxPacket;
import com.npjt25.godzillaminusone.network.ModNetwork;
import com.npjt25.godzillaminusone.registry.ModDamageTypes;
import com.npjt25.godzillaminusone.registry.ModEntities;
import com.npjt25.godzillaminusone.registry.ModItems;
import com.npjt25.godzillaminusone.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * The Shinsei Maru: the small, run-down wooden minesweeper (a converted fishing boat) from the film, built to clear
 * the sea of the war's magnetic mines (model: tools/make_props.py).
 *
 * <ul>
 *   <li>Two places to man: right-click her stern (the gun platform, or her from astern) to take the machine gun;
 *       anywhere else to take the wheel up on the wheelhouse roof (W/S throttle, A/D steer; she makes
 *       {@link #MAX_SPEED} blocks a tick flat out, as fast as Godzilla swims) and drop the naval mines.</li>
 *   <li>The Type 93 13 mm machine gun at the stern points aft and swings {@link #GUN_ARC} degrees either way: its
 *       gunner (or the helmsman, when there's no gunner) aims it by looking and fires one round per click
 *       (client.ShinseiMaruControls). A round sets off a naval mine it hits.</li>
 *   <li>Sneak and right-click to open her mine hold ({@link #MINE_SLOTS} slots, naval mines only; right-clicking her
 *       with a naval mine in hand loads it too). The helmsman drops one over the stern with the "Vehicle Action" key
 *       (R): it floats there, and goes off when Godzilla comes near.</li>
 *   <li>Her decks are solid ({@link ShinseiMaruPart}s over her whole length and beam: the foredeck, the wheelhouse and
 *       its roof, the side decks beside it, the after deck and the stern platform), and whoever stands on them is
 *       carried along as she moves and turns. Her own box covers all of her (Minecraft only looks at a multipart
 *       entity's parts where its own box is), so she does her own collisions with blocks, along her hull; anything
 *       she moves into (or that finds itself inside her) is pushed out, onto her deck if it's nearly up there.</li>
 *   <li>Boarding ladders on both sides, just aft of the wheelhouse: walk (or swim) into one to climb it, sneak to
 *       hold on.</li>
 *   <li>At sea she rides the swell: she heaves up and down with it, and pitches and rolls as it passes under her
 *       (rougher in the rain, rougher still in a thunderstorm, and higher with Physics Mod installed), and now and
 *       then a bigger wave slaps her side and sets her rocking.</li>
 *   <li>Hit her like a boat to knock her back into her item (with her mines); explosions and Godzilla wreck her.</li>
 * </ul>
 */
public class ShinseiMaruEntity extends Entity implements GeoEntity {
    public static final int MINE_SLOTS = 9;
    /** Flat out she's as fast as Godzilla swimming after something. */
    public static final double MAX_SPEED = GodzillaEntity.SWIM_TOP_SPEED;
    private static final double REVERSE_SPEED = MAX_SPEED * 0.3;
    private static final double ACCELERATION = 0.01;
    /** Her keel sits this far below the waterline. */
    public static final double DRAFT = 0.9;

    // Where things are on her (blocks: to starboard, up, forward), as tools/make_props.py builds her.
    private static final Vec3 HELM = new Vec3(0.0, 4.45, -0.12);
    private static final Vec3 GUNNER = new Vec3(0.0, 2.35, -5.27);
    private static final Vec3 GUN_PIVOT = new Vec3(0.0, 3.54, -6.25);
    private static final double BARREL = 2.1;
    private static final double STERN = -8.8;
    /** Half her beam, her bow and her transom (blocks from her middle), her deck's height above her keel. */
    public static final double BEAM = 2.8;
    private static final double BOW = 8.6;
    private static final double TRANSOM = -7.7;
    private static final double DECK = 2.2;
    /** The boarding ladders, port and starboard: how far forward of her middle. */
    private static final double LADDER = -3.6;

    /** How far the gun swings either side of dead astern, and up and down (degrees). */
    public static final float GUN_ARC = 135.0F;
    private static final float GUN_UP = 45.0F;
    private static final float GUN_DOWN = 15.0F;
    private static final float GUN_DAMAGE = 10.0F;
    private static final double GUN_RANGE = 96.0;
    private static final int GUN_COOLDOWN = 4;

    private static final EntityDataAccessor<Float> DATA_GUN_YAW = SynchedEntityData.defineId(ShinseiMaruEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_GUN_PITCH = SynchedEntityData.defineId(ShinseiMaruEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_MINES = SynchedEntityData.defineId(ShinseiMaruEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_WOBBLE = SynchedEntityData.defineId(ShinseiMaruEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_DAMAGE = SynchedEntityData.defineId(ShinseiMaruEntity.class, EntityDataSerializers.FLOAT);
    /** The entity id of whoever took the gun (-1: nobody chose it). */
    private static final EntityDataAccessor<Integer> DATA_GUNNER = SynchedEntityData.defineId(ShinseiMaruEntity.class, EntityDataSerializers.INT);
    /** A click this far astern of her middle (or from there) takes the gun. */
    private static final double GUN_STATION = -4.6;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** Her hull and decks, bow to stern (to starboard, forward of her middle, width, deck height above the keel). */
    private final ShinseiMaruPart[] parts = makeParts();
    private final SimpleContainer mines = new SimpleContainer(MINE_SLOTS) {
        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return isMine(stack);
        }
    };
    private double speed;
    private float deltaRotation;
    private int lastShot = -GUN_COOLDOWN;

    // (smooth movement for everyone but the helmsman, as a boat does it)
    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private double lerpYRot;

    private ShinseiMaruPart[] makeParts() {
        List<ShinseiMaruPart> list = new ArrayList<>();
        list.add(new ShinseiMaruPart(this, 0.0, 7.0, 2.6F, 2.6));                       // the bow
        for (double f : new double[] {4.6, 2.4, -3.9}) {                                 // the foredeck, the after deck
            for (double s : new double[] {-1.4, 1.4}) {
                list.add(new ShinseiMaruPart(this, s, f, 2.8F, DECK));
            }
        }
        for (double f : new double[] {0.37, -1.83}) {                                    // the wheelhouse, up to its roof
            for (double s : new double[] {-0.95, 0.95}) {
                list.add(new ShinseiMaruPart(this, s, f, 2.2F, HELM.y));
            }
        }
        for (double f = 1.1; f > -2.5; f -= 0.7) {                                       // the side decks beside it
            for (double s : new double[] {-2.45, 2.45}) {
                list.add(new ShinseiMaruPart(this, s, f, 0.7F, DECK));
            }
        }
        for (double s : new double[] {-1.35, 1.35}) {                                    // the stern platform
            list.add(new ShinseiMaruPart(this, s, -6.2, 2.7F, GUNNER.y));
        }
        return list.toArray(new ShinseiMaruPart[0]);
    }

    public ShinseiMaruEntity(EntityType<? extends ShinseiMaruEntity> type, Level level) {
        super(type, level);
        // (her parts take the ids after hers, here and on every client: see setId)
        setId(getId());
        this.noCulling = true; // her mast and gun reach outside her box
        this.mines.addListener(container -> {
            if (!level().isClientSide) {
                this.entityData.set(DATA_MINES, countMines());
            }
        });
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_GUN_YAW, 0.0F);
        this.entityData.define(DATA_GUN_PITCH, 0.0F);
        this.entityData.define(DATA_MINES, 0);
        this.entityData.define(DATA_WOBBLE, 0);
        this.entityData.define(DATA_DAMAGE, 0.0F);
        this.entityData.define(DATA_GUNNER, -1);
    }

    @Override
    public void setId(int id) {
        super.setId(id);
        for (int i = 0; i < this.parts.length; i++) {
            this.parts[i].setId(id + i + 1);
        }
    }

    @Override
    public boolean isMultipartEntity() {
        return true;
    }

    @Override
    public ShinseiMaruPart[] getParts() {
        return this.parts;
    }

    public static boolean isMine(ItemStack stack) {
        return stack.is(ModItems.NAVAL_MINE_SPAWN_EGG.get());
    }

    // ------------------------------------------------------------------ state for the renderer

    /** Where the gun points: degrees either side of dead astern (positive to starboard... as her yaw goes). */
    public float getGunYaw() {
        return this.entityData.get(DATA_GUN_YAW);
    }

    /** The gun's tilt: negative is up (like a player's pitch). */
    public float getGunPitch() {
        return this.entityData.get(DATA_GUN_PITCH);
    }

    /** Naval mines in her hold (the rack on deck shows up to three). */
    public int getMinesAboard() {
        return this.entityData.get(DATA_MINES);
    }

    public int getWobble() {
        return this.entityData.get(DATA_WOBBLE);
    }

    public SimpleContainer getMines() {
        return this.mines;
    }

    private int countMines() {
        int n = 0;
        for (int i = 0; i < this.mines.getContainerSize(); i++) {
            ItemStack stack = this.mines.getItem(i);
            if (isMine(stack)) {
                n += stack.getCount();
            }
        }
        return n;
    }

    /** A point on her: {@code local} = (to starboard, up, forward) blocks. */
    public Vec3 at(Vec3 local) {
        Vec3 forward = Vec3.directionFromRotation(0.0F, getYRot());
        Vec3 starboard = new Vec3(-forward.z, 0.0, forward.x);
        return position().add(starboard.scale(local.x)).add(0.0, local.y, 0.0).add(forward.scale(local.z));
    }

    /** Which way the gun points, in the world. */
    public Vec3 gunDirection() {
        return Vec3.directionFromRotation(getGunPitch(), getYRot() + 180.0F + getGunYaw());
    }

    public Vec3 muzzle() {
        return at(GUN_PIVOT).add(gunDirection().scale(BARREL));
    }

    // ------------------------------------------------------------------ crew

    /** The one at the gun: whoever took it, or (when nobody chose it) the second aboard. */
    @Nullable
    private Entity gunStation() {
        int id = this.entityData.get(DATA_GUNNER);
        for (Entity passenger : getPassengers()) {
            if (passenger.getId() == id) {
                return passenger;
            }
        }
        return getPassengers().size() > 1 ? getPassengers().get(1) : null;
    }

    /** The helmsman: whoever is aboard and not at the gun. */
    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        Entity gun = gunStation();
        for (Entity passenger : getPassengers()) {
            if (passenger != gun && passenger instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    /** Who works the gun: the gunner, or the helmsman when there's nobody at it. */
    @Nullable
    public Player getGunner() {
        if (gunStation() instanceof Player gunner) {
            return gunner;
        }
        return getControllingPassenger() instanceof Player helmsman ? helmsman : null;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().size() < 2;
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (passenger.getId() == this.entityData.get(DATA_GUNNER)) {
            this.entityData.set(DATA_GUNNER, -1);
        }
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction move) {
        if (!hasPassenger(passenger)) {
            return;
        }
        Vec3 seat = at(passenger == gunStation() ? GUNNER : HELM);
        move.accept(passenger, seat.x, seat.y + passenger.getMyRidingOffset(), seat.z);
        // the crew turn with her
        passenger.setYRot(passenger.getYRot() + this.deltaRotation);
        passenger.setYHeadRot(passenger.getYHeadRot() + this.deltaRotation);
    }

    @Override
    public double getPassengersRidingOffset() {
        return HELM.y;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (player.isSecondaryUseActive()) {
            if (player instanceof ServerPlayer serverPlayer) {
                openHold(serverPlayer);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (isMine(held) && !hasPassenger(player)) {
            // loading a mine by hand
            if (!level().isClientSide) {
                ItemStack one = held.copyWithCount(1);
                if (this.mines.addItem(one).isEmpty()) {
                    if (!player.getAbilities().instabuild) {
                        held.shrink(1);
                    }
                    playSound(SoundEvents.CHAIN_PLACE, 1.0F, 0.8F);
                }
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        return board(player, forwardOf(player.position()) < GUN_STATION);
    }

    /** A click on one of her parts: the stern takes the gun, the rest the wheel. */
    InteractionResult interactFromPart(ShinseiMaruPart part, Player player, InteractionHand hand) {
        if (player.isSecondaryUseActive() || isMine(player.getItemInHand(hand)) && !hasPassenger(player)) {
            return interact(player, hand);
        }
        return board(player, part.forward < GUN_STATION);
    }

    /** Takes the gun (if it's free) or the wheel (if that's free); otherwise the other station. */
    private InteractionResult board(Player player, boolean wantsGun) {
        if (!canAddPassenger(player) || hasPassenger(player)) {
            return InteractionResult.PASS;
        }
        if (level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        boolean gunFree = gunStation() == null;
        boolean wheelFree = getControllingPassenger() == null;
        boolean gun = wantsGun ? gunFree : !wheelFree && gunFree;
        if (!player.startRiding(this)) {
            return InteractionResult.PASS;
        }
        if (gun) {
            this.entityData.set(DATA_GUNNER, player.getId());
        }
        return InteractionResult.CONSUME;
    }

    /** How far forward of her middle a point is (negative: astern). */
    private double forwardOf(Vec3 point) {
        Vec3 forward = Vec3.directionFromRotation(0.0F, getYRot());
        return (point.x - getX()) * forward.x + (point.z - getZ()) * forward.z;
    }

    private void openHold(ServerPlayer player) {
        NetworkHooks.openScreen(player, new SimpleMenuProvider((id, inventory, p) -> new ShinseiMaruMenu(id, inventory, this.mines, this),
                Component.translatable("container.godzillaminusone.shinsei_maru")));
    }

    /** For the hold's screen: still close enough to reach her. */
    public boolean canReachHold(Player player) {
        return isAlive() && player.distanceToSqr(this) < 12.0 * 12.0;
    }

    // ------------------------------------------------------------------ weapons

    /** Points the gun where {@code shooter} is looking, as far as it goes. */
    private void aimGun(Entity shooter) {
        float yaw = Mth.clamp(Mth.wrapDegrees(shooter.getYRot() - (getYRot() + 180.0F)), -GUN_ARC, GUN_ARC);
        float pitch = Mth.clamp(shooter.getXRot(), -GUN_UP, GUN_DOWN);
        this.entityData.set(DATA_GUN_YAW, yaw);
        this.entityData.set(DATA_GUN_PITCH, pitch);
    }

    /** A click on the gun: one 13 mm round, if it's ready. */
    public void fireGun(Player shooter) {
        if (shooter != getGunner()) {
            return;
        }
        aimGun(shooter);
        fireGun(shooter, false);
    }

    /** One round where the gun points (for the game tests, {@code force} skips the rate of fire). */
    public boolean fireGun(@Nullable Entity shooter, boolean force) {
        if (!(level() instanceof ServerLevel level) || (!force && this.tickCount - this.lastShot < GUN_COOLDOWN && this.tickCount >= this.lastShot)) {
            return false;
        }
        this.lastShot = this.tickCount;
        Vec3 dir = gunDirection();
        Vec3 from = muzzle();
        Vec3 to = from.add(dir.scale(GUN_RANGE));
        BlockHitResult wall = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (wall.getType() != HitResult.Type.MISS) {
            to = wall.getLocation();
        }
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, this, from, to, new AABB(from, to).inflate(1.0),
                e -> !e.is(this) && !hasPassenger(e) && e.isAlive() && e.isPickable() && !e.isSpectator());
        if (hit != null) {
            // (from the boat, not by hand: a naval mine it hits goes off rather than being knocked loose)
            hit.getEntity().hurt(ModDamageTypes.source(level, ModDamageTypes.MACHINE_GUN, this, shooter), GUN_DAMAGE);
        }
        level.playSound(null, from.x, from.y, from.z, ModSounds.SHINSEI_MARU_GUN.get(), SoundSource.NEUTRAL, 3.0F,
                0.95F + this.random.nextFloat() * 0.1F);
        float worldYaw = getYRot() + 180.0F + getGunYaw();
        ModNetwork.sendNear(level, from, 160.0, new FxPacket(FxPacket.Type.BOAT_GUN, from, worldYaw, getGunPitch()));
        return true;
    }

    /** The helmsman's "Drop Naval Mine" key: one mine from the hold over the stern. */
    public boolean dropMine(@Nullable Player helmsman) {
        if (!(level() instanceof ServerLevel level)) {
            return false;
        }
        for (int i = 0; i < this.mines.getContainerSize(); i++) {
            ItemStack stack = this.mines.getItem(i);
            if (!isMine(stack)) {
                continue;
            }
            NavalMineEntity mine = ModEntities.NAVAL_MINE.get().create(level);
            if (mine == null) {
                return false;
            }
            Vec3 stern = at(new Vec3(0.0, 0.0, STERN));
            // afloat, it goes in just under the surface; aground, onto the ground (put in the ground, it would be
            // crushed, and set off right behind her)
            double surface = waterSurface(stern.x, stern.z);
            stern = new Vec3(stern.x, Double.isNaN(surface) ? getY() + 0.5 : surface - 1.2, stern.z);
            Vec3 aft = Vec3.directionFromRotation(0.0F, getYRot() + 180.0F);
            mine.moveTo(stern.x, stern.y, stern.z, getYRot(), 0.0F);
            mine.setDeltaMovement(getDeltaMovement().scale(0.5).add(aft.scale(0.2)));
            level.addFreshEntity(mine);
            stack.shrink(1);
            this.mines.setChanged();
            level.playSound(null, stern.x, stern.y, stern.z, SoundEvents.GENERIC_SPLASH, SoundSource.NEUTRAL, 1.5F, 0.8F);
            return true;
        }
        if (helmsman != null) {
            helmsman.displayClientMessage(Component.translatable("message.godzillaminusone.shinsei_maru.no_mines"), true);
        }
        return false;
    }

    // ------------------------------------------------------------------ ticking

    @Override
    public void tick() {
        super.tick();
        Vec3 before = position();
        float yawBefore = getYRot();
        List<Entity> standing = standingOnDeck();
        tickLerp();
        this.deltaRotation = 0.0F;
        if (isControlledByLocalInstance()) {
            steer();
            stayAfloat();
            sail();
            if (this.horizontalCollision) {
                this.speed *= 0.5;
            }
        } else {
            setDeltaMovement(Vec3.ZERO);
        }
        for (ShinseiMaruPart part : this.parts) {
            part.follow();
        }
        carry(standing, before, getYRot() - yawBefore);
        for (Entity entity : around()) {
            pushOut(entity);
            climbLadder(entity);
        }
        if (!level().isClientSide) {
            if (getWobble() > 0) {
                this.entityData.set(DATA_WOBBLE, getWobble() - 1);
            }
            if (this.entityData.get(DATA_DAMAGE) > 0.0F) {
                this.entityData.set(DATA_DAMAGE, Math.max(0.0F, this.entityData.get(DATA_DAMAGE) - 1.0F));
            }
            Player gunner = getGunner();
            if (gunner != null) {
                aimGun(gunner);
            }
        }
    }

    /**
     * Who is on or around her (not aboard) that this side moves: the client its player, the server everything else
     * (players move themselves).
     */
    private List<Entity> around() {
        List<Entity> list = new ArrayList<>();
        for (Entity entity : level().getEntities(this, getBoundingBox().inflate(1.0, 1.0, 1.0),
                e -> !e.isPassenger() && !(e instanceof ShinseiMaruPart) && !e.noPhysics && !e.isSpectator())) {
            if (level().isClientSide ? entity instanceof Player player && player.isLocalPlayer() : !(entity instanceof Player)) {
                list.add(entity);
            }
        }
        return list;
    }

    /** Who is standing on her decks, to be carried along with her. */
    private List<Entity> standingOnDeck() {
        List<Entity> standing = new ArrayList<>();
        for (Entity entity : around()) {
            AABB feet = entity.getBoundingBox();
            for (ShinseiMaruPart part : this.parts) {
                AABB deck = part.getBoundingBox();
                if (feet.minY >= deck.maxY - 0.1 && feet.minY <= deck.maxY + 0.35 && feet.maxX > deck.minX && feet.minX < deck.maxX
                        && feet.maxZ > deck.minZ && feet.minZ < deck.maxZ) {
                    standing.add(entity);
                    break;
                }
            }
        }
        return standing;
    }

    /** Moves those standing on her decks along with her: as far as she went, and round as far as she turned. */
    private void carry(List<Entity> standing, Vec3 before, float turned) {
        Vec3 moved = position().subtract(before);
        if (standing.isEmpty() || moved.lengthSqr() < 1.0E-8 && Math.abs(turned) < 1.0E-4F) {
            return;
        }
        double cos = Math.cos(-turned * Mth.DEG_TO_RAD);
        double sin = Math.sin(-turned * Mth.DEG_TO_RAD);
        for (Entity entity : standing) {
            double rx = entity.getX() - before.x;
            double rz = entity.getZ() - before.z;
            double x = before.x + rx * cos - rz * sin + moved.x;
            double z = before.z + rx * sin + rz * cos + moved.z;
            entity.setPos(x, entity.getY() + moved.y, z);
            entity.setYRot(entity.getYRot() + turned);
            entity.setYHeadRot(entity.getYHeadRot() + turned);
        }
    }

    /**
     * Anything she has moved into (or that has got inside her) is pushed out of her decks: up onto the deck if its feet
     * are nearly up there, otherwise out over her side, whichever way is shortest.
     */
    private void pushOut(Entity entity) {
        for (int pass = 0; pass < 2; pass++) {
            for (ShinseiMaruPart part : this.parts) {
                AABB deck = part.getBoundingBox();
                AABB box = entity.getBoundingBox();
                if (!box.intersects(deck.deflate(0.001))) {
                    continue;
                }
                double up = deck.maxY - box.minY;
                if (up <= 0.75) {
                    entity.setPos(entity.getX(), deck.maxY, entity.getZ());
                    Vec3 v = entity.getDeltaMovement();
                    entity.setDeltaMovement(v.x, Math.max(0.0, v.y), v.z);
                    entity.setOnGround(true);
                    entity.resetFallDistance();
                    continue;
                }
                double east = deck.maxX - box.minX;
                double west = box.maxX - deck.minX;
                double south = deck.maxZ - box.minZ;
                double north = box.maxZ - deck.minZ;
                double least = Math.min(Math.min(east, west), Math.min(south, north)) + 0.01;
                if (least - 0.01 == east) {
                    entity.setPos(entity.getX() + least, entity.getY(), entity.getZ());
                } else if (least - 0.01 == west) {
                    entity.setPos(entity.getX() - least, entity.getY(), entity.getZ());
                } else if (least - 0.01 == south) {
                    entity.setPos(entity.getX(), entity.getY(), entity.getZ() + least);
                } else {
                    entity.setPos(entity.getX(), entity.getY(), entity.getZ() - least);
                }
            }
        }
    }

    /**
     * Her boarding ladders: up against one, in the water or on it, you climb it as you would a ladder (walking into it
     * climbs, sneaking holds on), up to her deck.
     */
    private void climbLadder(Entity entity) {
        if (!(entity instanceof LivingEntity living)) {
            return;
        }
        Vec3 forward = Vec3.directionFromRotation(0.0F, getYRot());
        double dx = living.getX() - getX();
        double dz = living.getZ() - getZ();
        double f = dx * forward.x + dz * forward.z;
        double s = Math.abs(-dx * forward.z + dz * forward.x);
        double y = living.getY() - getY();
        if (Math.abs(f - LADDER) > 0.6 || s < BEAM - 0.4 || s > BEAM + 1.0 || y < -1.5 || y > DECK + 0.5) {
            return;
        }
        living.resetFallDistance();
        Vec3 v = living.getDeltaMovement();
        double vy = living.isShiftKeyDown() ? Math.max(v.y, 0.0) : living.horizontalCollision ? 0.2 : Math.max(v.y, -0.15);
        living.setDeltaMovement(v.x, vy, v.z);
    }

    /**
     * Moves her by her momentum, colliding with blocks along her hull (her own box is much bigger than she is): if her
     * hull would run into something, she stops (and doesn't turn into it); aground, she rests on what's under her keel.
     */
    private void sail() {
        Vec3 v = getDeltaMovement();
        boolean grounded = solid(getX(), getY() - 0.05, getZ());
        if (grounded && v.y < 0.0) {
            v = new Vec3(v.x, 0.0, v.z);
        }
        setOnGround(grounded);
        Vec3 to = position().add(v);
        this.horizontalCollision = false;
        // (already stuck fast, say thrown up on a beach, she can work herself free)
        if ((v.x != 0.0 || v.z != 0.0 || this.deltaRotation != 0.0F) && hullBlocked(to.x, to.y, to.z, getYRot())
                && !hullBlocked(getX(), getY(), getZ(), getYRot() - this.deltaRotation)) {
            this.horizontalCollision = true;
            to = new Vec3(getX(), to.y, getZ());
            if (this.deltaRotation != 0.0F && hullBlocked(to.x, to.y, to.z, getYRot())) {
                setYRot(getYRot() - this.deltaRotation);
                this.deltaRotation = 0.0F;
            }
            setDeltaMovement(0.0, v.y, 0.0);
        }
        setPos(to.x, to.y, to.z);
    }

    /** Whether her hull, set down at (x, y, z) heading {@code yaw}, would be in a solid block anywhere along it. */
    private boolean hullBlocked(double x, double y, double z, float yaw) {
        Vec3 forward = Vec3.directionFromRotation(0.0F, yaw);
        for (double f = TRANSOM; f <= BOW + 0.01; f += 1.0) {
            double half = f > 6.2 ? BEAM - (f - 6.2) / (BOW - 6.2) * 2.0 : f < -7.3 ? 2.6 : BEAM;
            for (double s : new double[] {-half, 0.0, half}) {
                double px = x + forward.x * f - forward.z * s;
                double pz = z + forward.z * f + forward.x * s;
                for (double dy : new double[] {DRAFT - 0.4, DRAFT + 0.6, DECK}) {
                    if (solid(px, y + dy, pz)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean solid(double x, double y, double z) {
        BlockPos pos = BlockPos.containing(x, y, z);
        net.minecraft.world.level.block.state.BlockState state = level().getBlockState(pos);
        if (state.isAir()) {
            return false;
        }
        var shape = state.getCollisionShape(level(), pos);
        return !shape.isEmpty() && shape.max(net.minecraft.core.Direction.Axis.Y) > 0.25;  // (lily pads and carpets don't count)
    }

    /** The helmsman's throttle (W/S) and wheel (A/D). */
    private void steer() {
        LivingEntity helmsman = getControllingPassenger();
        float forward = helmsman != null ? helmsman.zza : 0.0F;
        float turn = helmsman != null ? helmsman.xxa : 0.0F;
        double wanted = forward > 0.0F ? MAX_SPEED : forward < 0.0F ? -REVERSE_SPEED : 0.0;
        this.speed = forward != 0.0F ? approach(this.speed, wanted, ACCELERATION) : this.speed * 0.985;
        // the faster she goes, the better she answers the helm
        float rate = (float) (0.6 + 2.4 * Math.min(1.0, Math.abs(this.speed) / MAX_SPEED));
        this.deltaRotation = -turn * rate * (this.speed < -0.001 ? -1.0F : 1.0F);
        setYRot(getYRot() + this.deltaRotation);
        Vec3 heading = Vec3.directionFromRotation(0.0F, getYRot()).scale(this.speed);
        Vec3 v = getDeltaMovement();
        setDeltaMovement(Mth.lerp(0.3, v.x, heading.x), v.y, Mth.lerp(0.3, v.z, heading.z));
    }

    private static double approach(double value, double target, double step) {
        return value < target ? Math.min(value + step, target) : Math.max(value - step, target);
    }

    /** Afloat she rides with her keel {@link #DRAFT} below the surface; aground she grinds to a halt. */
    private void stayAfloat() {
        double surface = waterSurface();
        Vec3 v = getDeltaMovement();
        if (!Double.isNaN(surface)) {
            double heave = swell(getX(), getZ(), level().getGameTime());
            double vy = Mth.clamp((surface - DRAFT + heave - getY()) * 0.25, -0.35, 0.35);
            setDeltaMovement(v.x, vy, v.z);
            resetFallDistance();
        } else {
            this.speed *= 0.6;
            setDeltaMovement(v.x * 0.6, onGround() ? 0.0 : v.y - 0.08, v.z * 0.6);
        }
    }

    /**
     * The height of the sea's swell (blocks above or below its calm surface) at (x, z) at a moment: two long rollers
     * crossing, a little higher in the rain and higher still in a thunderstorm. She heaves with it; the renderer
     * pitches and rolls her by how it slopes under her bow and stern, port and starboard.
     */
    public double swell(double x, double z, double time) {
        return seaState() * (Math.sin(time * 0.11 + x * 0.09 + z * 0.03) + 0.6 * Math.sin(time * 0.07 - z * 0.08 + x * 0.02 + 1.3)
                + 0.3 * Math.sin(time * 0.23 + x * 0.21 - z * 0.17 + 0.7));
    }

    /**
     * How high the sea runs: a little higher in the rain and higher still in a thunderstorm. Physics Mod's ocean waves
     * (when it's installed) run taller than anything vanilla shows, so she rides a bigger swell with it; its waves
     * themselves can't be read (it has no API), so hers are her own, not exactly its.
     */
    private double seaState() {
        return 0.2 * (1.0 + level().getRainLevel(1.0F) + 1.5 * level().getThunderLevel(1.0F)) * (physicsMod() ? 1.8 : 1.0);
    }

    @Nullable
    private static Boolean physicsMod;

    private static boolean physicsMod() {
        if (physicsMod == null) {
            net.minecraftforge.fml.ModList mods = net.minecraftforge.fml.ModList.get();
            physicsMod = mods != null && mods.isLoaded("physicsmod");
        }
        return physicsMod;
    }

    /**
     * Now and then (every 3 to 4 seconds) a bigger wave slaps her side: a sharp kick of roll that rocks her and dies
     * away; some waves hit harder than others. -1 to 1.
     */
    private double slap(double time) {
        double waves = time / 70.0 + (getId() % 97) * 0.37;
        double since = (waves - Math.floor(waves)) * 70.0;
        double strength = 0.4 + 0.6 * Math.abs(Math.sin(Math.floor(waves) * 12.9898));
        return strength * Math.exp(-since / 16.0) * Math.sin(since * 0.45);
    }

    /** Her pitch (degrees, bow up) and roll (degrees, to starboard) on the swell at a moment, for the renderer. */
    public float[] swellTilt(double time) {
        if (!isAfloat()) {
            return new float[] {0.0F, 0.0F};
        }
        Vec3 bow = at(new Vec3(0.0, 0.0, 6.0));
        Vec3 stern = at(new Vec3(0.0, 0.0, -6.0));
        Vec3 port = at(new Vec3(-BEAM, 0.0, 0.0));
        Vec3 starboard = at(new Vec3(BEAM, 0.0, 0.0));
        double pitch = Math.atan2(swell(bow.x, bow.z, time) - swell(stern.x, stern.z, time), 12.0);
        double roll = Math.atan2(swell(port.x, port.z, time) - swell(starboard.x, starboard.z, time), 2.0 * BEAM);
        double slap = slap(time) * seaState() / 0.2;
        return new float[] {(float) (Math.toDegrees(pitch) * 2.2 + slap * 1.5), (float) (Math.toDegrees(roll) * 2.5 + slap * 6.0)};
    }

    /** Whether she's afloat (not aground). */
    public boolean isAfloat() {
        return !Double.isNaN(waterSurface());
    }

    /** The height of the water's surface under her middle, or NaN if she's not in water. */
    public double waterSurface() {
        return waterSurface(getX(), getZ());
    }

    /** The height of the water's surface at (x, z), around her waterline, or NaN if there's none. */
    private double waterSurface(double atX, double atZ) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int x = Mth.floor(atX);
        int z = Mth.floor(atZ);
        for (int y = Mth.floor(getY() + DRAFT + 1.5); y >= Mth.floor(getY() - 1.0); y--) {
            FluidState fluid = level().getFluidState(pos.set(x, y, z));
            if (fluid.is(FluidTags.WATER)) {
                return y + fluid.getHeight(level(), pos);
            }
        }
        return Double.NaN;
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps, boolean teleport) {
        this.lerpX = x;
        this.lerpY = y;
        this.lerpZ = z;
        this.lerpYRot = yRot;
        this.lerpSteps = 10;
    }

    private void tickLerp() {
        if (isControlledByLocalInstance()) {
            this.lerpSteps = 0;
            syncPacketPositionCodec(getX(), getY(), getZ());
        }
        if (this.lerpSteps > 0) {
            double x = getX() + (this.lerpX - getX()) / this.lerpSteps;
            double y = getY() + (this.lerpY - getY()) / this.lerpSteps;
            double z = getZ() + (this.lerpZ - getZ()) / this.lerpSteps;
            setYRot(getYRot() + (float) Mth.wrapDegrees(this.lerpYRot - getYRot()) / this.lerpSteps);
            this.lerpSteps--;
            setPos(x, y, z);
        }
    }

    // ------------------------------------------------------------------ damage

    @Override
    public boolean isPickable() {
        return false; // (her box is far bigger than she is: you click and hit her parts)
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false; // (her parts are her solid hull and decks)
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    /**
     * Hits work like on a boat: she rocks, and enough of them (one in creative) knock her back into her item, with the
     * mines aboard. Explosions and Godzilla wreck her.
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isInvulnerableTo(source) || source.is(DamageTypeTags.IS_FIRE) || level().isClientSide || isRemoved()) {
            return !isInvulnerableTo(source) && !source.is(DamageTypeTags.IS_FIRE);
        }
        if (source.getEntity() != null && hasPassenger(source.getEntity())) {
            return false; // her own crew
        }
        if (source.is(DamageTypeTags.IS_EXPLOSION) || source.getEntity() instanceof GodzillaEntity) {
            wreck();
            return true;
        }
        this.entityData.set(DATA_WOBBLE, 10);
        this.entityData.set(DATA_DAMAGE, this.entityData.get(DATA_DAMAGE) + amount * 10.0F);
        markHurt();
        boolean creative = source.getEntity() instanceof Player player && player.getAbilities().instabuild;
        if (creative || this.entityData.get(DATA_DAMAGE) > 40.0F) {
            ejectPassengers();
            if (!creative && level().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
                spawnAtLocation(ModItems.SHINSEI_MARU.get());
            }
            Containers.dropContents(level(), blockPosition(), this.mines);
            discard();
        }
        return true;
    }

    /** Smashed to matchwood: the crew are thrown in the water, the mines she carried float free. */
    private void wreck() {
        ejectPassengers();
        playSound(SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, 2.0F, 0.6F);
        Containers.dropContents(level(), blockPosition(), this.mines);
        discard();
    }

    // ------------------------------------------------------------------ saving & GeckoLib

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.mines.fromTag(tag.getList("Mines", CompoundTag.TAG_COMPOUND));
        this.entityData.set(DATA_MINES, countMines());
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.put("Mines", this.mines.createTag());
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // (the gun, the mines on deck and the rocking are posed by client/render/ShinseiMaruRenderer)
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
