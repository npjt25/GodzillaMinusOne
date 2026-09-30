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

    public static final ForgeConfigSpec.IntValue NUCLEAR_CLOUD_HEIGHT = BUILDER
            .comment("How tall (blocks above ground zero) the mushroom cloud of an atomic blast grows. It's Alex's Caves'",
                    "mushroom cloud, scaled evenly: at 240 its cap is about 230 blocks across.")
            .defineInRange("mushroomCloudHeight", 240, 20, 1000);

    public static final ForgeConfigSpec.IntValue NUCLEAR_CLOUD_SECONDS = BUILDER
            .comment("How long the mushroom cloud hangs in the sky, in seconds (it keeps growing, slower and slower, until it goes).")
            .defineInRange("mushroomCloudSeconds", 60, 5, 600);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private ClientConfig() {
    }
}
