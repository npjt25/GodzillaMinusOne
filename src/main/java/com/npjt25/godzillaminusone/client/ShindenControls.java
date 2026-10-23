package com.npjt25.godzillaminusone.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.npjt25.godzillaminusone.GodzillaMinusOne;
import com.npjt25.godzillaminusone.entity.ShindenEntity;
import com.npjt25.godzillaminusone.network.ModNetwork;
import com.npjt25.godzillaminusone.network.ShindenFirePacket;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * The Shinden's pilot, client side: holding right-click keeps the plane's guns firing (whatever is in your hand; the
 * plane sets the rate of fire), and the pilot's model leans and pitches with the plane so they stay sat in the
 * cockpit. Holding Free Look (Left Control by default, in Controls, Godzilla Minus One; it only counts while flying a
 * Shinden) lets the pilot look around while the plane flies on as it was heading; let go, and the view swings smoothly
 * back to the nose before the plane answers to the mouse again, so it never jerks round. Piloting it, you see it from
 * behind (third person, forced; the camera pulled back far enough for its 13-block wingspan), back to first person
 * when you get out.
 */
@Mod.EventBusSubscriber(modid = GodzillaMinusOne.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ShindenControls {
    /** Ticks between "fire" requests while the button is held (the plane fires as fast as its guns allow). */
    private static final int REQUEST_INTERVAL = 2;
    private static int requestCooldown;
    public static final KeyMapping FREE_LOOK = new KeyMapping("key.godzillaminusone.shinden_free_look", ModKeyContexts.SHINDEN_PILOT,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_CONTROL, "key.categories.godzillaminusone");
    /** Each tick after Free Look is let go, the view turns this much of the way back to the nose. */
    private static final float EASE_BACK = 0.35F;
    /** The plane we're free-looking from (to hand its steering back if we leave it). */
    private static ShindenEntity freeLooking;
    /** Piloting, the camera looks at the middle of the plane from this far away: all of it in the picture. */
    static final double CAMERA_DISTANCE = 11.0;
    /** Whether we put the camera into third person (to put it back in first person when the pilot gets out). */
    private static boolean forcedThirdPerson;

    @Mod.EventBusSubscriber(modid = GodzillaMinusOne.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Keys {
        @SubscribeEvent
        public static void register(RegisterKeyMappingsEvent event) {
            event.register(FREE_LOOK);
        }

        private Keys() {
        }
    }

    @SubscribeEvent
    public static void onUseKey(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (!event.isUseItem() || mc.player == null || !(mc.player.getVehicle() instanceof ShindenEntity plane)
                || plane.getControllingPassenger() != mc.player) {
            return;
        }
        // (the guns are fired from the client tick below, for as long as the button is held)
        event.setCanceled(true);
        event.setSwingHand(false);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (requestCooldown > 0) {
            requestCooldown--;
        }
        freeLook(mc);
        thirdPerson(mc);
        if (mc.player == null || mc.screen != null || !mc.options.keyUse.isDown()
                || !(mc.player.getVehicle() instanceof ShindenEntity plane) || plane.getControllingPassenger() != mc.player) {
            return;
        }
        if (requestCooldown <= 0) {
            requestCooldown = REQUEST_INTERVAL;
            ModNetwork.CHANNEL.sendToServer(new ShindenFirePacket(plane.getId()));
        }
    }

    /** Piloting, the camera is behind the plane (third person); out of it, back to first person if we moved it. */
    private static void thirdPerson(Minecraft mc) {
        boolean piloting = mc.player != null && mc.player.getVehicle() instanceof ShindenEntity plane && plane.getControllingPassenger() == mc.player;
        if (piloting && mc.options.getCameraType().isFirstPerson()) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            forcedThirdPerson = true;
        } else if (!piloting && forcedThirdPerson) {
            forcedThirdPerson = false;
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        }
    }

    /** Piloting, in third person: the camera pulled back to frame the whole plane (not Minecraft's 4 blocks, inside it). */
    @SubscribeEvent
    public static void onCameraSetup(ViewportEvent.ComputeCameraAngles event) {
        Camera camera = event.getCamera();
        if (!camera.isDetached() || !(camera.getEntity() instanceof Player player) || !(player.getVehicle() instanceof ShindenEntity plane)) {
            return;
        }
        Vec3 middle = plane.getPosition((float) event.getPartialTick()).add(0.0, 2.0, 0.0);
        CameraPlacement.pullBack(camera, plane, middle, CAMERA_DISTANCE);
    }

    /** Free Look: the plane keeps the heading it had when the key went down; the mouse only turns the pilot's view. */
    private static void freeLook(Minecraft mc) {
        LocalPlayer player = mc.player;
        ShindenEntity plane = player != null && player.getVehicle() instanceof ShindenEntity p && p.getControllingPassenger() == player ? p : null;
        if (freeLooking != null && freeLooking != plane) {
            freeLooking.stopFreeLook(); // left the plane (or it's gone) mid-look
            freeLooking = null;
        }
        if (plane == null) {
            return;
        }
        if (FREE_LOOK.isDown() && mc.screen == null) {
            if (!plane.isFreeLooking()) {
                plane.startFreeLook(player.getYRot(), player.getXRot());
                freeLooking = plane;
            }
        } else if (plane.isFreeLooking()) {
            // swing the view back to the nose, then the mouse steers again: no sudden turn either way
            float yaw = Mth.wrapDegrees(plane.getSteerYaw() - player.getYRot());
            float pitch = plane.getSteerPitch() - player.getXRot();
            if (Math.abs(yaw) < 1.0F && Math.abs(pitch) < 1.0F) {
                player.setYRot(player.getYRot() + yaw);
                player.setXRot(plane.getSteerPitch());
                plane.stopFreeLook();
                freeLooking = null;
            } else {
                player.setYRot(player.getYRot() + yaw * EASE_BACK);
                player.setXRot(player.getXRot() + pitch * EASE_BACK);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderPilot(RenderLivingEvent.Pre<?, ?> event) {
        if (!(event.getEntity().getVehicle() instanceof ShindenEntity plane)) {
            return;
        }
        float partialTick = event.getPartialTick();
        float pitch = plane.getNosePitch(partialTick);
        float bank = plane.getBank(partialTick);
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        if (pitch != 0.0F || bank != 0.0F) {
            // the same turns as the plane's model (ShindenRenderer), about the pilot's seat
            float yaw = 180.0F - Mth.rotLerp(partialTick, plane.yRotO, plane.getYRot());
            poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
            poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
            poseStack.mulPose(Axis.ZP.rotationDegrees(-bank));
            poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderPilotDone(RenderLivingEvent.Post<?, ?> event) {
        LivingEntity entity = event.getEntity();
        if (entity.getVehicle() instanceof ShindenEntity) {
            event.getPoseStack().popPose();
        }
    }

    private ShindenControls() {
    }
}
