package com.npjt25.godzillaminusone.client;

import com.npjt25.godzillaminusone.GodzillaMinusOne;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The mod's key bindings, as the game's own (Options, Controls, Key Binds, Godzilla Minus One; saved in options.txt):
 * the Shinsei Maru's and searchlight's Vehicle Action, the Shinden's free look, the played Odo's run and the played
 * Godzilla's five moves.
 *
 * <p>All registered here, in one handler: each used to be registered by a {@code Keys.register} of its own (four
 * classes named alike, with methods named alike), and the event bus took them all for the first one, so Vehicle
 * Action was registered four times and the others never (they worked, but weren't in Controls or saved).
 */
@Mod.EventBusSubscriber(modid = GodzillaMinusOne.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ModKeyMappings {
    /** Every one of them, in the order Controls lists them. */
    public static KeyMapping[] all() {
        return new KeyMapping[] {ShinseiMaruControls.VEHICLE_ACTION, ShindenControls.FREE_LOOK, PlayerOdoControls.RUN,
                PlayerGodzillaControls.TAIL_WHIP, PlayerGodzillaControls.SCRATCH, PlayerGodzillaControls.BITE,
                PlayerGodzillaControls.ROAR, PlayerGodzillaControls.ATOMIC_BREATH};
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        for (KeyMapping key : all()) {
            event.register(key);
        }
    }

    private ModKeyMappings() {
    }
}
