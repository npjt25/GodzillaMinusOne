package com.npjt25.godzillaminusone.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.npjt25.godzillaminusone.GodzillaMinusOne;
import com.npjt25.godzillaminusone.entity.SearchlightEntity;
import com.npjt25.godzillaminusone.entity.ShinseiMaruEntity;
import com.npjt25.godzillaminusone.network.ModNetwork;
import com.npjt25.godzillaminusone.network.SearchlightPacket;
import com.npjt25.godzillaminusone.network.ShinseiMaruPacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * The "Vehicle Action" key (R by default, in Controls, Godzilla Minus One) and the Shinsei Maru's gun, client side: the
 * Shinsei Maru's helmsman drops a naval mine over the stern with it, and whoever is at a searchlight switches its lamp
 * off and on. Whoever is in the gunner's seat fires the boat's stern gun by holding right-click: it's automatic, firing
 * as long as the button is held (the game repeats the click every 4 ticks, the gun's rate of fire).
 */
@Mod.EventBusSubscriber(modid = GodzillaMinusOne.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ShinseiMaruControls {
    public static final KeyMapping VEHICLE_ACTION = new KeyMapping("key.godzillaminusone.vehicle_action", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.godzillaminusone");


    @SubscribeEvent
    public static void onUseKey(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (!event.isUseItem() || mc.player == null || !(mc.player.getVehicle() instanceof ShinseiMaruEntity boat)
                || boat.getGunner() != mc.player) {
            return;
        }
        event.setCanceled(true);
        event.setSwingHand(false);
        ModNetwork.CHANNEL.sendToServer(new ShinseiMaruPacket(boat.getId(), false));
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        while (VEHICLE_ACTION.consumeClick()) {
            if (mc.player == null) {
                continue;
            }
            if (mc.player.getVehicle() instanceof ShinseiMaruEntity boat && boat.getControllingPassenger() == mc.player) {
                ModNetwork.CHANNEL.sendToServer(new ShinseiMaruPacket(boat.getId(), true));
            } else if (mc.player.getVehicle() instanceof SearchlightEntity light && light.getOperator() == mc.player) {
                ModNetwork.CHANNEL.sendToServer(new SearchlightPacket(light.getId()));
            }
        }
    }

    private ShinseiMaruControls() {
    }
}
