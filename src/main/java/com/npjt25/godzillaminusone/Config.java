package com.npjt25.godzillaminusone;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Gameplay settings (config/godzillaminusone-common.toml). Defaults follow the Bedrock add-on,
 * except where noted: the add-on's world-wide values (2000 block kill radius, Wither XXVI...)
 * are toned down but can be restored here.
 */
public final class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    // ---- Godzilla
    public static final ForgeConfigSpec.DoubleValue GODZILLA_MAX_HEALTH;
    public static final ForgeConfigSpec.DoubleValue GODZILLA_ATTACK_DAMAGE;
    public static final ForgeConfigSpec.BooleanValue GODZILLA_ONLY_HURT_BY_EXPLOSIVES;
    public static final ForgeConfigSpec.DoubleValue MINE_DAMAGE_PERCENT;
    public static final ForgeConfigSpec.BooleanValue GODZILLA_REGENERATES;
    public static final ForgeConfigSpec.DoubleValue CALM_REGEN_PERCENT;
    public static final ForgeConfigSpec.BooleanValue GODZILLA_BREAKS_BLOCKS;
    public static final ForgeConfigSpec.BooleanValue GODZILLA_TARGETS_ALL_MOBS;
    public static final ForgeConfigSpec.IntValue AURA_RADIUS;
    public static final ForgeConfigSpec.IntValue AURA_WITHER_AMPLIFIER;
    public static final ForgeConfigSpec.DoubleValue STEP_DAMAGE;
    public static final ForgeConfigSpec.IntValue STEP_RADIUS;

    // ---- Atomic breath
    public static final ForgeConfigSpec.IntValue ATOMIC_BREATH_COOLDOWN;
    public static final ForgeConfigSpec.IntValue ATOMIC_BLAST_KILL_RADIUS;
    public static final ForgeConfigSpec.IntValue ATOMIC_BLAST_RADIATION_RADIUS;
    public static final ForgeConfigSpec.IntValue ATOMIC_BLAST_RADIATION_AMPLIFIER;
    public static final ForgeConfigSpec.IntValue ATOMIC_BLAST_CRATER_RADIUS;
    public static final ForgeConfigSpec.IntValue CHARGE_FREEZE_RADIUS;

    // ---- Shinden / Torrid / naval mine
    public static final ForgeConfigSpec.DoubleValue SHINDEN_MAX_SPEED;
    public static final ForgeConfigSpec.IntValue SHINDEN_KAMIKAZE_DISTANCE;
    public static final ForgeConfigSpec.DoubleValue TORRID_DAMAGE;
    public static final ForgeConfigSpec.IntValue TORRID_RANGE;
    public static final ForgeConfigSpec.IntValue MINE_BLAST_RADIUS;
    public static final ForgeConfigSpec.DoubleValue MINE_BLAST_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue PILOT_BLAST_DAMAGE;

    static {
        BUILDER.push("godzilla");
        GODZILLA_MAX_HEALTH = BUILDER
                .comment("Godzilla's max health. Values above 1024 need the AttributeFix mod (the add-on used 2,500,000).",
                        "His health bar works the same either way because naval mines deal a percentage of it.")
                .defineInRange("maxHealth", 1000.0, 1.0, 1_000_000.0);
        GODZILLA_ATTACK_DAMAGE = BUILDER
                .comment("Melee damage (add-on: 15000; vanilla caps attack damage at 2048 without AttributeFix).")
                .defineInRange("attackDamage", 2000.0, 0.0, 1_000_000.0);
        GODZILLA_ONLY_HURT_BY_EXPLOSIVES = BUILDER
                .comment("Like the add-on, Godzilla ignores swords, arrows, fire, lava, explosions... and can only be hurt",
                        "by naval mine blasts (including a Shinden crashing into him) or /kill. Set false to make him mortal.")
                .define("onlyHurtByNavalMines", true);
        MINE_DAMAGE_PERCENT = BUILDER
                .comment("Percent of Godzilla's max health removed by one naval mine blast (add-on: ~10%, i.e. 10 mines).")
                .defineInRange("mineDamagePercent", 10.0, 0.0, 100.0);
        GODZILLA_REGENERATES = BUILDER
                .comment("When his health reaches zero he collapses, lies dead, then regenerates and rises again (add-on behaviour).",
                        "Set false to let him die for good.")
                .define("regenerates", true);
        CALM_REGEN_PERCENT = BUILDER
                .comment("Percent of max health healed every 5 seconds while he has no target.")
                .defineInRange("calmRegenPercent", 0.5, 0.0, 100.0);
        GODZILLA_BREAKS_BLOCKS = BUILDER
                .comment("Godzilla smashes through blocks in his way (also needs the mobGriefing gamerule).")
                .define("breaksBlocks", true);
        GODZILLA_TARGETS_ALL_MOBS = BUILDER
                .comment("Add-on behaviour: Godzilla attacks every creature he sees. If false he only targets players,",
                        "Shinden fighters, iron golems and hostile mobs.")
                .define("targetsAllMobs", true);
        AURA_RADIUS = BUILDER
                .comment("Radius of his radiation aura (Wither + \"RADIATION POISONING\"). Add-on: 16.")
                .defineInRange("auraRadius", 16, 0, 256);
        AURA_WITHER_AMPLIFIER = BUILDER
                .comment("Wither amplifier of the radiation aura (0 = Wither I). Add-on: 25, which kills in seconds.")
                .defineInRange("auraWitherAmplifier", 3, 0, 255);
        STEP_DAMAGE = BUILDER
                .comment("Damage dealt every second to everything near him while he walks. Add-on: 150.")
                .defineInRange("stepDamage", 150.0, 0.0, 1_000_000.0);
        STEP_RADIUS = BUILDER
                .comment("Radius of the walking shockwave. Add-on: 20.")
                .defineInRange("stepRadius", 20, 0, 128);
        BUILDER.pop();

        BUILDER.push("atomic_breath");
        ATOMIC_BREATH_COOLDOWN = BUILDER
                .comment("Seconds between the end of one atomic breath and the start of the next charge (add-on: ~18).")
                .defineInRange("cooldownSeconds", 18, 0, 3600);
        ATOMIC_BLAST_KILL_RADIUS = BUILDER
                .comment("Everything within this many blocks of the blast is killed, except Shinden pilots (add-on: 2000 around Godzilla).")
                .defineInRange("killRadius", 64, 0, 2000);
        ATOMIC_BLAST_RADIATION_RADIUS = BUILDER
                .comment("Radius of the radiation (Wither) cloud after the blast (add-on: 300).")
                .defineInRange("radiationRadius", 96, 0, 2000);
        ATOMIC_BLAST_RADIATION_AMPLIFIER = BUILDER
                .comment("Wither amplifier of the blast's radiation (add-on: 25).")
                .defineInRange("radiationAmplifier", 3, 0, 255);
        ATOMIC_BLAST_CRATER_RADIUS = BUILDER
                .comment("Radius of the crater carved by the blast (0 = no block damage; also needs mobGriefing).")
                .defineInRange("craterRadius", 12, 0, 48);
        CHARGE_FREEZE_RADIUS = BUILDER
                .comment("While he charges, mobs within this radius are frozen in place (add-on: 2000).")
                .defineInRange("freezeRadius", 128, 0, 2000);
        BUILDER.pop();

        BUILDER.push("weapons");
        SHINDEN_MAX_SPEED = BUILDER
                .comment("Top speed of the Kyushu J7W1 Shinden in blocks per tick.")
                .defineInRange("shindenMaxSpeed", 1.6, 0.1, 5.0);
        SHINDEN_KAMIKAZE_DISTANCE = BUILDER
                .comment("A Shinden flown this close to Godzilla detonates like a naval mine; its pilot bails out (add-on: 15).")
                .defineInRange("shindenKamikazeDistance", 15, 0, 128);
        TORRID_DAMAGE = BUILDER
                .comment("Damage of each Torrid (Shinden cannon) burst (add-on: 400).")
                .defineInRange("torridDamage", 400.0, 0.0, 1_000_000.0);
        TORRID_RANGE = BUILDER
                .comment("Range of the Torrid in blocks (add-on: 40, in every direction; here it fires forward).")
                .defineInRange("torridRange", 48, 1, 256);
        MINE_BLAST_RADIUS = BUILDER
                .comment("Naval mine blast radius (add-on: 30).")
                .defineInRange("mineBlastRadius", 30, 1, 128);
        MINE_BLAST_DAMAGE = BUILDER
                .comment("Naval mine blast damage to everything except Godzilla and Shinden pilots (add-on: 255000).")
                .defineInRange("mineBlastDamage", 255000.0, 0.0, Double.MAX_VALUE);
        PILOT_BLAST_DAMAGE = BUILDER
                .comment("Blast damage taken by Shinden pilots ('plot armour' in the add-on: 30).")
                .defineInRange("pilotBlastDamage", 30.0, 0.0, Double.MAX_VALUE);
        BUILDER.pop();
    }

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private Config() {
    }
}
