package com.npjt25.godzillaminusone.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.npjt25.godzillaminusone.GodzillaMinusOne;
import com.npjt25.godzillaminusone.entity.ShindenEntity;
import com.npjt25.godzillaminusone.network.ModNetwork;
import com.npjt25.godzillaminusone.network.ShindenFirePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The Shinden's pilot, client side: right-click fires the plane's guns (one burst per click, whatever is in your
 * hand), and the pilot's model leans and pitches with the plane so they stay sat in the cockpit.
 */
@Mod.EventBusSubscriber(modid = GodzillaMinusOne.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ShindenControls {
    /** Set when a click fired; cleared once the button is let go (holding it down doesn't keep firing). */
    private static boolean fireHeld;

    @SubscribeEvent
    public static void onUseKey(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (!event.isUseItem() || mc.player == null || !(mc.player.getVehicle() instanceof ShindenEntity plane)
                || plane.getControllingPassenger() != mc.player) {
            return;
        }
        event.setCanceled(true);
        event.setSwingHand(false);
        if (!fireHeld) {
            fireHeld = true;
            ModNetwork.CHANNEL.sendToServer(new ShindenFirePacket(plane.getId()));
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && fireHeld && !Minecraft.getInstance().options.keyUse.isDown()) {
            fireHeld = false;
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
