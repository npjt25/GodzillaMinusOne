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
import org.joml.Vector3f;
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
 *   <li>Her decks are solid ({@link ShinseiMaruPart}s tiling her whole length and beam: the forecastle, the main deck
 *       and the side decks, the wheelhouse and its roof, and the stern platform), and whoever stands on them is
 *       carried along as she moves and turns. Her own box covers all of her (Minecraft only looks at a multipart
 *       entity's parts where its own box is), so she does her own collisions with blocks, along her hull; anything
 *       she moves into (or that finds itself inside her) is pushed out, onto her deck if it's nearly up there.</li>
 *   <li>Boarding ladders on both sides, just aft of the wheelhouse, and one up the back of the wheelhouse to its roof
 *       (the helm): walk (or swim) into one to climb it, sneak to hold on.</li>
 *   <li>Her crew stand at their stations: the helmsman at the wheel, the gunner on the stern platform behind the gun.</li>
 *   <li>Afloat she sits steady at the surface: no bobbing of her own. (Physics Mod's ocean waves are drawn on its
 *       client side and it has no API to read their height from, so she can't follow them; rather than bob out of
 *       step with them, she keeps still.)</li>
 *   <li>Under way her motor runs (client.ShinseiMaruSounds): quiet, a little louder and higher as she picks up
 *       speed, fading out as she stops.</li>
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
    public static final Vec3 HELM = new Vec3(0.0, 4.45, -0.12);
    public static final Vec3 GUNNER = new Vec3(0.0, 2.35, -5.27);
    private static final Vec3 GUN_PIVOT = new Vec3(0.0, 3.54, -6.25);
    private static final double BARREL = 2.1;
    private static final double STERN = -8.8;
    /** Half her beam, her bow and her transom (blocks from her middle), her deck's height above her keel. */
    public static final double BEAM = 2.8;
    public static final double BOW = 8.6;
    public static final double TRANSOM = -7.7;
    public static final double DECK = 2.2;
    /** The boarding ladders, port and starboard: how far forward of her middle. */
    public static final double LADDER = -3.6;
    /** The ladder up the back of the wheelhouse to its roof: how far to starboard (it's to starboard of the door). */
    public static final double ROOF_LADDER = 1.2;
    public static final double WHEELHOUSE_AFT = -2.94;

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
    /**
     * Her heading, exactly (the game's own packets send it to 1.4 degrees, which would shift her decks a little on
     * everyone's screen), and how far she turned and moved in her last tick (to keep her where the server has her).
     */
    private static final EntityDataAccessor<Float> DATA_YAW = SynchedEntityData.defineId(ShinseiMaruEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_TURN = SynchedEntityData.defineId(ShinseiMaruEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Vector3f> DATA_MOTION = SynchedEntityData.defineId(ShinseiMaruEntity.class, EntityDataSerializers.VECTOR3);
    /** Whether her engine is running: the helm (or the autopilot) asking for way, ahead or astern. */
    private static final EntityDataAccessor<Boolean> DATA_ENGINE = SynchedEntityData.defineId(ShinseiMaruEntity.class, EntityDataSerializers.BOOLEAN);
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
    // with nobody at the wheel: a throttle and wheel set by the game tests and the in-game checks (0, 0: none)
    private float autoThrottle;
    private float autoTurn;
    private int lastShot = -GUN_COOLDOWN;

    // where the server last said she was (everyone but the helmsman: see tickLerp)
    private boolean lerpPending;
    private boolean yawPending;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    // where she was at the end of her last tick
    @Nullable
    private Vec3 lastTickPos;
    private float lastTickYaw;

    /**
     * Her decks as tiles, bow to stern: the forecastle, the main deck, the wheelhouse (up to its roof) and the stern
     * platform. Each is a square box (Minecraft's boxes never turn), small enough, and overlapping its neighbours enough,
     * that whichever way she heads the decks have no gap anyone could fall through, and stick out past her sides by
     * little.
     */
    private ShinseiMaruPart[] makeParts() {
        List<ShinseiMaruPart> list = new ArrayList<>();
        tiles(list, 8.1, 8.6, 0.8, 2.7, 1.0);                       // her stem
        tiles(list, 7.4, 8.1, 1.6, 2.95, 1.1);                      // the forecastle
        tiles(list, -4.2, 7.4, 2.65, DECK, 1.4);                    // the main deck, the side decks beside the wheelhouse
        tiles(list, WHEELHOUSE_AFT, 1.47, 2.05, HELM.y, 1.1);       // the wheelhouse, up to its roof (the helm)
        tiles(list, -7.5, -4.2, 2.5, GUNNER.y, 1.2);                // the stern platform (the gun)
        return list.toArray(new ShinseiMaruPart[0]);
    }

    /** Covers forward {@code aft} to {@code fore}, {@code half} each side, up to {@code top}, in tiles of about {@code size}. */
    private void tiles(List<ShinseiMaruPart> list, double aft, double fore, double half, double top, double size) {
        int along = Math.max(1, (int) Math.ceil((fore - aft) / size));
        int across = Math.max(1, (int) Math.ceil(2.0 * half / size));
        double df = (fore - aft) / along;
        double ds = 2.0 * half / across;
        float width = (float) (Math.max(df, ds) * 1.12);
        for (int i = along - 1; i >= 0; i--) {
            for (int j = 0; j < across; j++) {
                list.add(new ShinseiMaruPart(this, -half + ds * (j + 0.5), aft + df * (i + 0.5), width, top));
            }
        }
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
        this.entityData.define(DATA_YAW, 0.0F);
        this.entityData.define(DATA_TURN, 0.0F);
        this.entityData.define(DATA_MOTION, new Vector3f());
        this.entityData.define(DATA_ENGINE, false);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_YAW.equals(key)) {
            this.yawPending = true;
        }
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

    /**
     * For the game tests and the in-game checks (server side): steers her with nobody at the wheel, as a helmsman's
     * keys would (throttle 1 ahead, -1 astern; turn 1 to port, -1 to starboard); 0, 0 stops her where she is.
     */
    public void autopilot(float throttle, float turn) {
        this.autoThrottle = Mth.clamp(throttle, -1.0F, 1.0F);
        this.autoTurn = Mth.clamp(turn, -1.0F, 1.0F);
        if (throttle == 0.0F && turn == 0.0F) {
            this.speed = 0.0;
            setDeltaMovement(0.0, getDeltaMovement().y, 0.0);
        }
    }

    /** Puts her deck tiles where they belong on her as she now lies (each tick does this; the game tests too). */
    public void placeParts() {
        for (ShinseiMaruPart part : this.parts) {
            part.follow();
        }
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

    /** Who works the gun: whoever is in the gunner's seat (the helmsman has the wheel and the mines, not the gun). */
    @Nullable
    public Player getGunner() {
        return gunStation() instanceof Player gunner ? gunner : null;
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
        // they stand at their stations (shouldRiderSit): feet on the deck
        Vec3 seat = at(passenger == gunStation() ? GUNNER : HELM);
        move.accept(passenger, seat.x, seat.y, seat.z);
        // the crew turn with her
        passenger.setYRot(passenger.getYRot() + this.deltaRotation);
        passenger.setYHeadRot(passenger.getYHeadRot() + this.deltaRotation);
    }

    @Override
    public double getPassengersRidingOffset() {
        return HELM.y;
    }

    /** Her helmsman stands at the wheel and her gunner behind the gun (Forge: drawn standing, not sitting). */
    @Override
    public boolean shouldRiderSit() {
        return false;
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
        // (from where she was at the end of her last tick: a helmsman's moves reach the server between its ticks)
        Vec3 before = this.lastTickPos != null ? this.lastTickPos : position();
        float yawBefore = this.lastTickPos != null ? this.lastTickYaw : getYRot();
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
        placeParts();
        float turned = Mth.wrapDegrees(getYRot() - yawBefore);
        Vec3 moved = position().subtract(before);
        carry(standing, before, turned);
        for (Entity entity : around()) {
            pushOut(entity);
            climbLadder(entity);
        }
        this.lastTickPos = position();
        this.lastTickYaw = getYRot();
        if (!level().isClientSide) {
            this.entityData.set(DATA_YAW, getYRot());
            this.entityData.set(DATA_TURN, turned);
            Vec3 motion = moved.lengthSqr() > 16.0 ? Vec3.ZERO : moved; // (put somewhere else: that's not her motion)
            this.entityData.set(DATA_MOTION, new Vector3f((float) motion.x, (float) motion.y, (float) motion.z));
            this.entityData.set(DATA_ENGINE, throttle() != 0.0F);
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

    /**
     * Moves those standing on her decks along with her: as far as she went, and round as far as she turned (not when
     * she's put somewhere else altogether, say by a command).
     */
    private void carry(List<Entity> standing, Vec3 before, float turned) {
        Vec3 moved = position().subtract(before);
        if (standing.isEmpty() || moved.lengthSqr() < 1.0E-8 && Math.abs(turned) < 1.0E-4F || moved.lengthSqr() > 16.0) {
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
        double s = -dx * forward.z + dz * forward.x;
        double y = living.getY() - getY();
        boolean side = Math.abs(f - LADDER) <= 0.6 && Math.abs(s) >= BEAM - 0.4 && Math.abs(s) <= BEAM + 1.0 && y >= -1.5 && y <= DECK + 0.5;
        boolean roof = Math.abs(s - ROOF_LADDER) <= 0.55 && f <= WHEELHOUSE_AFT + 0.1 && f >= WHEELHOUSE_AFT - 0.9
                && y >= DECK - 0.3 && y <= HELM.y + 0.5;
        if (!side && !roof) {
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

    /** The helmsman's throttle (W ahead, S astern; the autopilot's with nobody at the wheel), as on the server. */
    private float throttle() {
        LivingEntity helmsman = getControllingPassenger();
        return helmsman != null ? helmsman.zza : this.autoThrottle;
    }

    /**
     * Whether her engine is running (the helm asking for way, ahead or astern), for her motor's sound: at once for the
     * helmsman, as the server has it for everyone else.
     */
    public boolean isEngineRunning() {
        return getControllingPassenger() instanceof Player helmsman && helmsman.isLocalPlayer()
                ? helmsman.zza != 0.0F : this.entityData.get(DATA_ENGINE);
    }

    /** The helmsman's throttle (W/S) and wheel (A/D). */
    private void steer() {
        LivingEntity helmsman = getControllingPassenger();
        float forward = throttle();
        float turn = helmsman != null ? helmsman.xxa : this.autoTurn;
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
            double vy = Mth.clamp((surface - DRAFT - getY()) * 0.25, -0.35, 0.35);
            setDeltaMovement(v.x, vy, v.z);
            resetFallDistance();
        } else {
            this.speed *= 0.6;
            setDeltaMovement(v.x * 0.6, onGround() ? 0.0 : v.y - 0.08, v.z * 0.6);
        }
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
        this.lerpPending = true;
    }

    /**
     * Everyone but the helmsman: she goes where the server has her, not easing towards it as a boat does (which would
     * leave her blocks behind at speed). The server sends where she was at the end of its last tick, every tick, so
     * she's put a tick's worth of her motion on from there (and carries on so between updates): where she is on the
     * server, as near as can be told. Whoever walks her decks here has to be where the server has them on its deck,
     * or it takes them for walking into her and puts them back.
     */
    private void tickLerp() {
        if (isControlledByLocalInstance()) {
            this.lerpPending = false;
            this.yawPending = false;
            syncPacketPositionCodec(getX(), getY(), getZ());
            return;
        }
        if (!level().isClientSide) {
            return; // (the server, her helmsman steering her: his moves put her where she is)
        }
        Vector3f motion = this.entityData.get(DATA_MOTION);
        if (this.lerpPending) {
            setPos(this.lerpX + motion.x(), this.lerpY + motion.y(), this.lerpZ + motion.z());
        } else if (motion.lengthSquared() > 0.0F) {
            setPos(getX() + motion.x(), getY() + motion.y(), getZ() + motion.z());
        }
        float turn = this.entityData.get(DATA_TURN);
        setYRot((this.yawPending ? this.entityData.get(DATA_YAW) : getYRot()) + turn);
        this.lerpPending = false;
        this.yawPending = false;
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
