package com.npjt25.godzillaminusone.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.npjt25.godzillaminusone.GodzillaMinusOne;
import com.npjt25.godzillaminusone.entity.ShinseiMaruEntity;
import com.npjt25.godzillaminusone.network.ModNetwork;
import com.npjt25.godzillaminusone.network.ShinseiMaruPacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * The Shinsei Maru's crew, client side: the helmsman drops a naval mine over the stern with the "Drop Naval Mine" key
 * (R by default, in Controls, Godzilla Minus One), and whoever works the stern gun fires it by right-clicking, one
 * round per click (holding the button down doesn't keep firing).
 */
@Mod.EventBusSubscriber(modid = GodzillaMinusOne.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ShinseiMaruControls {
    public static final KeyMapping DROP_MINE = new KeyMapping("key.godzillaminusone.drop_mine", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.godzillaminusone");
    /** Set when a click fired; cleared once the button is let go. */
    private static boolean fireHeld;

    @Mod.EventBusSubscriber(modid = GodzillaMinusOne.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Keys {
        @SubscribeEvent
        public static void register(RegisterKeyMappingsEvent event) {
            event.register(DROP_MINE);
        }

        private Keys() {
        }
    }

    @SubscribeEvent
    public static void onUseKey(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (!event.isUseItem() || mc.player == null || !(mc.player.getVehicle() instanceof ShinseiMaruEntity boat)
                || boat.getGunner() != mc.player) {
            return;
        }
        event.setCanceled(true);
        event.setSwingHand(false);
        if (!fireHeld) {
            fireHeld = true;
            ModNetwork.CHANNEL.sendToServer(new ShinseiMaruPacket(boat.getId(), false));
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (fireHeld && !mc.options.keyUse.isDown()) {
            fireHeld = false;
        }
        while (DROP_MINE.consumeClick()) {
            if (mc.player != null && mc.player.getVehicle() instanceof ShinseiMaruEntity boat && boat.getControllingPassenger() == mc.player) {
                ModNetwork.CHANNEL.sendToServer(new ShinseiMaruPacket(boat.getId(), true));
            }
        }
    }

    private ShinseiMaruControls() {
    }
}
