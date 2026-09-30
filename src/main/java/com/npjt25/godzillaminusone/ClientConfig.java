package com.npjt25.godzillaminusone;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Per-player display settings (config/godzillaminusone-client.toml), also editable in game:
 * Mods, Godzilla Minus One, Config (client.ModConfigScreen).
 */
public final class ClientConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue SHOW_BOSS_BAR = BUILDER
            .comment("Show Godzilla's health bar at the top of the screen.")
            .define("showGodzillaHealthBar", true);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private ClientConfig() {
    }
}
