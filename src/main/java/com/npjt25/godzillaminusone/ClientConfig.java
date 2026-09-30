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

    // The atomic blast (client.render.NuclearCloud, client.render.BlastTimeline). The defaults are a proposal for the
    // film's sequence at a playable pace, not timings measured from it.
    static {
        BUILDER.comment("What the atomic blast looks like (Godzilla's atomic breath landing, or a nuclear bomb). Only the look:",
                "the damage, crater and flattening happen at the moment of impact whatever these say. Times are counted",
                "from the impact.").push("atomicBlast");
    }

    public static final ForgeConfigSpec.IntValue CLOUD_HEIGHT = BUILDER
            .comment("How tall the mushroom cloud grows, in blocks above ground zero (the top of its cap). Everything else",
                    "is in proportion: at 240 its cap is about 290 blocks across and 80 deep, its stem about 70 thick, the",
                    "fireball about 70 across, and the dust wave rolls out about 230 blocks.")
            .defineInRange("cloudHeight", 240, 20, 1000);

    public static final ForgeConfigSpec.IntValue FLASH_STRENGTH = BUILDER
            .comment("How strongly the flash whites out your view, in percent (0 = not at all). The flash in the world stays,",
                    "dimmed at 0. Minecraft's \"Hide Lightning Flashes\" accessibility option also turns the white-out off.")
            .defineInRange("flashStrength", 100, 0, 100);

    public static final ForgeConfigSpec.DoubleValue FLASH_SECONDS = BUILDER
            .comment("How long the blinding flash lasts: white-hot at once, then dying away.")
            .defineInRange("flashSeconds", 1.5, 0.2, 5.0);

    public static final ForgeConfigSpec.DoubleValue FIREBALL_SECONDS = BUILDER
            .comment("How long the fireball blazes (expanding and rising) before it cools from the outside in.")
            .defineInRange("fireballSeconds", 4.0, 1.0, 20.0);

    public static final ForgeConfigSpec.DoubleValue SHOCKWAVE_SECONDS = BUILDER
            .comment("How long the ground-level dust wave takes to roll out to its full reach (fast at first, then slowing).")
            .defineInRange("shockwaveSeconds", 8.0, 1.0, 30.0);

    public static final ForgeConfigSpec.IntValue RISE_SECONDS = BUILDER
            .comment("How long the column takes to rise and the cap to spread to their full size.")
            .defineInRange("riseSeconds", 30, 5, 300);

    public static final ForgeConfigSpec.IntValue COOLING_SECONDS = BUILDER
            .comment("When the fire glowing inside the cloud has died out (before its smoke goes).")
            .defineInRange("coolingSeconds", 30, 2, 300);

    public static final ForgeConfigSpec.IntValue MATURE_SECONDS = BUILDER
            .comment("How long the full-grown cloud then towers over ground zero, slowly rising, spreading and rolling over.")
            .defineInRange("matureSeconds", 60, 0, 600);

    public static final ForgeConfigSpec.IntValue DISSIPATE_SECONDS = BUILDER
            .comment("How long it then takes to break up: the cap broadens and thins from its edges, then the stem and its",
                    "heart go, leaving scattered smoke and a haze over the ground that fade last.")
            .defineInRange("dissipateSeconds", 90, 5, 600);

    static {
        BUILDER.pop();
    }

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private ClientConfig() {
    }
}
