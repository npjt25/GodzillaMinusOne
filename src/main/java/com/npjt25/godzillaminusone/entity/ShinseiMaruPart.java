package com.npjt25.godzillaminusone.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.PartEntity;
import org.jetbrains.annotations.Nullable;

/**
 * One section of the Shinsei Maru's hull and decks (like the ender dragon's parts): a solid box the crew can walk and
 * stand on, kept in place along her as she moves and turns. Hits and clicks on it go to the boat; a click on a section
 * astern takes the gunner's seat (ShinseiMaruEntity#interactFromPart).
 */
public class ShinseiMaruPart extends PartEntity<ShinseiMaruEntity> {
    /** Where it sits on her: blocks to starboard (0: on her centreline) and forward of her middle. */
    final double forward;
    /** The height of its top (the deck or roof there) above her keel. */
    final double top;
    private final EntityDimensions size;

    public ShinseiMaruPart(ShinseiMaruEntity boat, double forward, float width, double top) {
        super(boat);
        this.forward = forward;
        this.top = top;
        this.size = EntityDimensions.scalable(width, (float) top);
        refreshDimensions();
    }

    /** Puts it back in its place on her. */
    void follow() {
        Vec3 at = getParent().at(new Vec3(0.0, 0.0, this.forward));
        this.xo = getX();
        this.yo = getY();
        this.zo = getZ();
        setPos(at.x, at.y, at.z);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return this.size;
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return getParent().isPickable();
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return !isInvulnerableTo(source) && getParent().hurt(source, amount);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        return getParent().interactFromPart(this, player, hand);
    }

    @Override
    public boolean is(Entity entity) {
        return this == entity || getParent() == entity;
    }

    @Nullable
    @Override
    public ItemStack getPickResult() {
        return getParent().getPickResult();
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        throw new UnsupportedOperationException();
    }
}
