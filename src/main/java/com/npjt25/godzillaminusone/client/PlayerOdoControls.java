package com.npjt25.godzillaminusone.client;

import com.npjt25.godzillaminusone.GodzillaMinusOne;
import com.npjt25.godzillaminusone.compat.Shapes;
import com.npjt25.godzillaminusone.entity.OdoIslandMonsterEntity;
import com.npjt25.godzillaminusone.network.ModNetwork;
import com.npjt25.godzillaminusone.network.OdoMoveRequestPacket;
import com.npjt25.godzillaminusone.network.ShapeAnimPacket;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import software.bernie.geckolib.animatable.GeoEntity;

/**
 * Playing as the Odo Island Monster (Walkers / ReMorphed: entity.PlayerOdo), client side: hold Run (Left Control by
 * default, in Controls, Godzilla Minus One; it only counts as him, so it never clashes with the Shinden's Free Look) to
 * run, and left-click to bite (instead of a punch). His shape runs and bites for everyone who sees him.
 */
@Mod.EventBusSubscriber(modid = GodzillaMinusOne.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class PlayerOdoControls {
    public static final KeyMapping RUN = new ContextKeyMapping("key.godzillaminusone.odo_run", ModKeyContexts.ODO_SHAPE,
            GLFW.GLFW_KEY_LEFT_CONTROL, "key.categories.godzillaminusone");
    /** Faster than this (blocks a tick, over the ground) his shape is shown running. */
    private static final double RUNNING = 0.2;
    private static boolean sentRunning;

    @Mod.EventBusSubscriber(modid = GodzillaMinusOne.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Keys {
        @SubscribeEvent
        public static void register(RegisterKeyMappingsEvent event) {
            event.register(RUN);
        }

        private Keys() {
        }
    }

    private static boolean isOdo(Player player) {
        return Shapes.shapeOf(player) instanceof OdoIslandMonsterEntity;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        boolean running = mc.player != null && mc.screen == null && isOdo(mc.player) && RUN.isDown();
        if (running != sentRunning && mc.getConnection() != null) {
            sentRunning = running;
            ModNetwork.CHANNEL.sendToServer(new OdoMoveRequestPacket(OdoMoveRequestPacket.RUN, running));
        }
    }

    /** A left click as him is a bite, not a punch. */
    @SubscribeEvent
    public static void onAttackKey(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (!event.isAttack() || mc.player == null || !isOdo(mc.player)) {
            return;
        }
        event.setCanceled(true);
        event.setSwingHand(false);
        ModNetwork.CHANNEL.sendToServer(new OdoMoveRequestPacket(OdoMoveRequestPacket.BITE, true));
    }

    /**
     * As him, in third person: the camera looks at the middle of him from far enough back for all of him (instead of
     * Minecraft's 4 blocks from the eyes, which is inside the plates on his back), closer if the ground or a wall is in
     * the way.
     */
    @SubscribeEvent
    public static void onCameraSetup(ViewportEvent.ComputeCameraAngles event) {
        Camera camera = event.getCamera();
        if (!camera.isDetached() || !(camera.getEntity() instanceof Player player) || !isOdo(player)) {
            return;
        }
        Vec3 middle = player.getPosition((float) event.getPartialTick()).add(0.0, player.getBbHeight() * 0.55, 0.0);
        CameraPlacement.pullBack(camera, player, middle, PlayerGodzillaControls.cameraDistance(player));
    }

    /** Before a player is drawn: if they're him, his shape is put where they are, running if they're going fast. */
    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        if (Shapes.shapeOf(player) instanceof OdoIslandMonsterEntity odo) {
            double dx = player.getX() - player.xo;
            double dz = player.getZ() - player.zo;
            odo.prepareAsShape(player, Math.sqrt(dx * dx + dz * dz) > RUNNING);
        }
    }

    /** A player playing as one of the mod's creatures made a move: their shape plays it. */
    public static void handle(ShapeAnimPacket msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getEntity(msg.playerId()) instanceof Player player)) {
            return;
        }
        LivingEntity shape = Shapes.shapeOf(player);
        if (shape instanceof OdoIslandMonsterEntity odo) {
            odo.prepareAsShape(player, odo.isRunning());
        }
        if (shape instanceof GeoEntity geo) {
            geo.triggerAnim("action", msg.animation());
        }
    }

    private PlayerOdoControls() {
    }
}
