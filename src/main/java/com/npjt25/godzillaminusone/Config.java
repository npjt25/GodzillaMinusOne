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
    public static final ForgeConfigSpec.BooleanValue FOOTPRINTS;
    public static final ForgeConfigSpec.BooleanValue KNOCKS_DOWN_TALL_BUILDINGS;
    public static final ForgeConfigSpec.IntValue TALL_BUILDING_HEIGHT;
    public static final ForgeConfigSpec.IntValue KNOCK_DOWN_TO_HEIGHT;
    public static final ForgeConfigSpec.IntValue ROAR_INTERVAL;

    // ---- Atomic breath
    public static final ForgeConfigSpec.IntValue ATOMIC_BREATH_COOLDOWN;
    public static final ForgeConfigSpec.IntValue FIRST_BREATH_MIN_DELAY;
    public static final ForgeConfigSpec.IntValue FIRST_BREATH_MAX_DELAY;
    public static final ForgeConfigSpec.IntValue ATOMIC_BLAST_KILL_RADIUS;
    public static final ForgeConfigSpec.IntValue ATOMIC_BLAST_RADIATION_RADIUS;
    public static final ForgeConfigSpec.IntValue ATOMIC_BLAST_RADIATION_AMPLIFIER;
    public static final ForgeConfigSpec.IntValue ATOMIC_BLAST_CRATER_RADIUS;
    public static final ForgeConfigSpec.IntValue ATOMIC_BLAST_DESTRUCTION_RADIUS;
    public static final ForgeConfigSpec.IntValue ATOMIC_FLATTEN_RADIUS;
    public static final ForgeConfigSpec.IntValue ATOMIC_FLATTEN_LEVEL;
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
        FOOTPRINTS = BUILDER
                .comment("Each step crushes what he walks on into a footprint of rubble (also needs breaksBlocks and mobGriefing).")
                .define("footprints", true);
        KNOCKS_DOWN_TALL_BUILDINGS = BUILDER
                .comment("When there's nothing for him to kill and he sees a building (or anything else built) standing",
                        "demolishFromHeight blocks or more above the ground, he goes over and smashes it down to demolishDownToHeight,",
                        "then leaves it alone (also needs breaksBlocks and mobGriefing).")
                .define("knocksDownTallBuildings", true);
        TALL_BUILDING_HEIGHT = BUILDER
                .comment("How tall (in blocks above the ground) a building must be for him to knock it down: half his height (45).")
                .defineInRange("demolishFromHeight", 23, 4, 256);
        KNOCK_DOWN_TO_HEIGHT = BUILDER
                .comment("How tall (in blocks above the ground) he leaves a building he knocks down: a quarter of his height.")
                .defineInRange("demolishDownToHeight", 11, 0, 256);
        ROAR_INTERVAL = BUILDER
                .comment("Seconds between the roars he lets out on his own (0 = only when he fires or is hit). Also in the",
                        "config menu: Mods, Godzilla Minus One, Config.")
                .defineInRange("roarIntervalSeconds", 120, 0, 3600);
        BUILDER.pop();

        BUILDER.push("atomic_breath");
        ATOMIC_BREATH_COOLDOWN = BUILDER
                .comment("Seconds he needs to recharge after an atomic breath before he can charge the next one (add-on: ~18).")
                .defineInRange("rechargeSeconds", 180, 0, 3600);
        FIRST_BREATH_MIN_DELAY = BUILDER
                .comment("After he appears, he waits between firstBreathMinSeconds and firstBreathMaxSeconds (picked at random)",
                        "before he can use his atomic breath for the first time.")
                .defineInRange("firstBreathMinSeconds", 120, 0, 3600);
        FIRST_BREATH_MAX_DELAY = BUILDER
                .comment("See firstBreathMinSeconds.")
                .defineInRange("firstBreathMaxSeconds", 180, 0, 3600);
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
                .comment("Radius of the crater blasted out where the atomic breath lands (0 = no block damage; also needs mobGriefing).")
                .defineInRange("blastCraterRadius", 24, 0, 64);
        ATOMIC_BLAST_DESTRUCTION_RADIUS = BUILDER
                .comment("Around the crater the blast wave flattens trees and buildings and scorches the ground out to this radius.")
                .defineInRange("blastWaveRadius", 64, 0, 160);
        ATOMIC_FLATTEN_RADIUS = BUILDER
                .comment("Everything above flattenDownToY within this many blocks of where the atomic breath lands (trees, grass,",
                        "buildings, hills) is erased, leaving the land flat; nothing drops. Chunks loaded later are flattened as they load.",
                        "0 = off; also needs mobGriefing.")
                .defineInRange("flattenRange", 140, 0, 2000);
        ATOMIC_FLATTEN_LEVEL = BUILDER
                .comment("The height (Y) the atomic breath flattens the land down to: anything above it is obliterated.")
                .defineInRange("flattenDownToY", 65, -64, 320);
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
