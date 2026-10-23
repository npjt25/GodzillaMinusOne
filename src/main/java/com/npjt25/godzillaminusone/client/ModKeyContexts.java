package com.npjt25.godzillaminusone.client;

import com.npjt25.godzillaminusone.compat.Shapes;
import com.npjt25.godzillaminusone.entity.OdoIslandMonsterEntity;
import com.npjt25.godzillaminusone.entity.ShindenEntity;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.settings.IKeyConflictContext;
import net.minecraftforge.client.settings.KeyConflictContext;

/**
 * When the mod's context keys count: each only in its own situation, so they can share a key with each other and with
 * vanilla's (Left Control is Sprint, the Shinden's free look and the played Odo's run all at once, and never clash: a
 * Shinden's pilot isn't an Odo Island Monster).
 */
public enum ModKeyContexts implements IKeyConflictContext {
    /** Flying a Shinden. */
    SHINDEN_PILOT {
        @Override
        public boolean isActive() {
            Minecraft mc = Minecraft.getInstance();
            return KeyConflictContext.IN_GAME.isActive() && mc.player != null
                    && mc.player.getVehicle() instanceof ShindenEntity plane && plane.getControllingPassenger() == mc.player;
        }
    },
    /** Playing as the Odo Island Monster (Walkers / ReMorphed). */
    ODO_SHAPE {
        @Override
        public boolean isActive() {
            Minecraft mc = Minecraft.getInstance();
            return KeyConflictContext.IN_GAME.isActive() && mc.player != null && mc.player.getVehicle() == null
                    && Shapes.shapeOf(mc.player) instanceof OdoIslandMonsterEntity;
        }
    };

    @Override
    public boolean conflicts(IKeyConflictContext other) {
        return this == other;
    }
}
