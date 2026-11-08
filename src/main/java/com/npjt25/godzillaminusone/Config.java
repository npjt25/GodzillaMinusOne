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
    public static final ForgeConfigSpec.BooleanValue EVOLVES_INTO_MINUS_ZERO;
    public static final ForgeConfigSpec.DoubleValue CALM_REGEN_PERCENT;
    public static final ForgeConfigSpec.BooleanValue GODZILLA_BREAKS_BLOCKS;
    public static final ForgeConfigSpec.BooleanValue GODZILLA_TARGETS_ALL_MOBS;
    public static final ForgeConfigSpec.IntValue AURA_RADIUS;
    public static final ForgeConfigSpec.IntValue AURA_WITHER_AMPLIFIER;
    public static final ForgeConfigSpec.DoubleValue STEP_DAMAGE;
    public static final ForgeConfigSpec.IntValue STEP_RADIUS;
    public static final ForgeConfigSpec.BooleanValue FOOTPRINTS;
    public static final ForgeConfigSpec.IntValue WALK_BREAKS_FROM_Y;
    public static final ForgeConfigSpec.BooleanValue KNOCKS_DOWN_TALL_BUILDINGS;
    public static final ForgeConfigSpec.IntValue TALL_BUILDING_HEIGHT;
    public static final ForgeConfigSpec.IntValue KNOCK_DOWN_TO_HEIGHT;
    public static final ForgeConfigSpec.IntValue DEMOLITION_INTERVAL;
    public static final ForgeConfigSpec.IntValue ROAR_INTERVAL;
    public static final ForgeConfigSpec.IntValue DOWNED_SECONDS;

    // ---- Atomic breath
    public static final ForgeConfigSpec.IntValue ATOMIC_BREATH_COOLDOWN;
    public static final ForgeConfigSpec.IntValue FIRST_BREATH_MIN_DELAY;
    public static final ForgeConfigSpec.IntValue FIRST_BREATH_MAX_DELAY;
    public static final ForgeConfigSpec.IntValue BREATH_MIN_TARGET_DISTANCE;
    public static final ForgeConfigSpec.IntValue ATOMIC_BLAST_KILL_RADIUS;
    public static final ForgeConfigSpec.IntValue ATOMIC_BLAST_RADIATION_RADIUS;
    public static final ForgeConfigSpec.IntValue ATOMIC_BLAST_RADIATION_AMPLIFIER;
    public static final ForgeConfigSpec.IntValue ATOMIC_BLAST_CRATER_RADIUS;
    public static final ForgeConfigSpec.IntValue ATOMIC_BLAST_DESTRUCTION_RADIUS;
    public static final ForgeConfigSpec.IntValue ATOMIC_BLAST_RUIN_RADIUS;
    public static final ForgeConfigSpec.IntValue ATOMIC_FLATTEN_RADIUS;
    public static final ForgeConfigSpec.IntValue ATOMIC_FLATTEN_LEVEL;
    public static final ForgeConfigSpec.IntValue CHARGE_FREEZE_RADIUS;

    // ---- Shinden / Torrid / naval mine
    public static final ForgeConfigSpec.DoubleValue SHINDEN_MAX_SPEED;
    public static final ForgeConfigSpec.IntValue SHINDEN_KAMIKAZE_DISTANCE;
    public static final ForgeConfigSpec.BooleanValue SHINDEN_INHALE_KNOCKOUT;
    public static final ForgeConfigSpec.DoubleValue TORRID_DAMAGE;
    public static final ForgeConfigSpec.IntValue TORRID_RANGE;
    public static final ForgeConfigSpec.IntValue MINE_BLAST_RADIUS;
    public static final ForgeConfigSpec.DoubleValue MINE_BLAST_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue PILOT_BLAST_DAMAGE;

    // ---- Playing as Godzilla / the Odo Island Monster (Walkers or ReMorphed)
    public static final ForgeConfigSpec.BooleanValue PLAYER_GODZILLA_BREAKS_BLOCKS;
    public static final ForgeConfigSpec.BooleanValue PLAYER_GODZILLA_FOOTPRINTS;
    public static final ForgeConfigSpec.IntValue PLAYER_GODZILLA_STEP_HEIGHT;
    public static final ForgeConfigSpec.DoubleValue PLAYER_ODO_SPEED;
    public static final ForgeConfigSpec.DoubleValue PLAYER_ODO_BITE_DAMAGE;

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
        EVOLVES_INTO_MINUS_ZERO = BUILDER
                .comment("When Godzilla Minus One's health reaches zero he collapses, lies there for downedSeconds, and then rises as",
                        "Godzilla Minus Zero (twice his height; every move and number his), once, in his place. Minus Zero brought",
                        "down falls for good. Set false for Minus One to regenerate (or die, with regenerates = false) as before.")
                .define("evolvesIntoMinusZero", true);
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
                .comment("Radius of his radiation aura: everything in it gets Radiation (a whole hazmat suit keeps it off). Add-on: 16.")
                .defineInRange("auraRadius", 16, 0, 256);
        AURA_WITHER_AMPLIFIER = BUILDER
                .comment("Strength of the aura's Radiation (0 = Radiation I, which hurts like Wither I). Add-on: 25, which kills in seconds.")
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
        WALK_BREAKS_FROM_Y = BUILDER
                .comment("Walking (Godzilla, Minus Zero, and a player playing as either), he destroys everything in his path",
                        "at this Y level or above (buildings, trees, the hillside: anything but liquids and bedrock) and stamps",
                        "footprints there; below it he destroys nothing as he walks (his blows and his breath are another matter).",
                        "Also needs breaksBlocks and mobGriefing.")
                .defineInRange("walkBreaksFromY", 60, -2048, 2048);
        KNOCKS_DOWN_TALL_BUILDINGS = BUILDER
                .comment("When there's nothing for him to kill and he sees a building (or anything else built) standing",
                        "demolishFromHeight blocks or more above the ground, he goes over and smashes it down to demolishDownToHeight,",
                        "then leaves it alone (also needs breaksBlocks and mobGriefing).")
                .define("knocksDownTallBuildings", true);
        TALL_BUILDING_HEIGHT = BUILDER
                .comment("How tall (in blocks above the ground) a building must be for him to go and knock it down.",
                        "-1 = half his real height (he stands ~45 blocks tall: 23).")
                .defineInRange("demolishFromHeight", -1, -1, 256);
        KNOCK_DOWN_TO_HEIGHT = BUILDER
                .comment("How tall (in blocks above the ground) he leaves a building he knocks down. -1 = a quarter of his",
                        "real height (11). Always kept below demolishFromHeight.")
                .defineInRange("demolishDownToHeight", -1, -1, 256);
        DEMOLITION_INTERVAL = BUILDER
                .comment("How often he picks a building to demolish: after he's done with one (or given up on it) he waits this",
                        "many seconds before he looks for the next (0 = straight away). Only buildings, and only with nothing to",
                        "kill about; what he walks into or treads on is broken regardless (breaksBlocks, footprints).")
                .defineInRange("demolitionIntervalSeconds", 20, 0, 3600);
        ROAR_INTERVAL = BUILDER
                .comment("Seconds between the roars he lets out on his own (0 = only when he fires or is hit). Also in the",
                        "config menu: Mods, Godzilla Minus One, Config.")
                .defineInRange("roarIntervalSeconds", 120, 0, 3600);
        DOWNED_SECONDS = BUILDER
                .comment("Resting time, in seconds: when his health reaches zero he plays his collapse (5 s), then lies defeated",
                        "for this many seconds before he regenerates and gets back up (the timer starts once the collapse is over",
                        "and he lies still; his get-up animation follows it as usual). 0 = he gets up as soon as he's down.",
                        "Kept by the server (or the single-player world): only its copy of this file counts. Only used when",
                        "regenerates = true.")
                .defineInRange("downedSeconds", 15, 0, 3600);
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
        BREATH_MIN_TARGET_DISTANCE = BUILDER
                .comment("While he's after a target, he only starts an atomic breath at one at least this many blocks away",
                        "(measured from him to it, horizontally and vertically); anything closer he walks up to and attacks in",
                        "melee. Checked only when he decides to start charging: a charge already under way is never called off.")
                .defineInRange("minTargetDistance", 75, 0, 1000);
        ATOMIC_BLAST_KILL_RADIUS = BUILDER
                .comment("Everything within this many blocks of the blast is killed, except Shinden pilots (add-on: 2000 around Godzilla).")
                .defineInRange("killRadius", 64, 0, 2000);
        ATOMIC_BLAST_RADIATION_RADIUS = BUILDER
                .comment("Radius of the fallout after the blast: everything in it gets Radiation (a whole hazmat suit keeps it off; add-on: 300).")
                .defineInRange("radiationRadius", 96, 0, 2000);
        ATOMIC_BLAST_RADIATION_AMPLIFIER = BUILDER
                .comment("Strength of the fallout's Radiation (0 = Radiation I; add-on: 25).")
                .defineInRange("radiationAmplifier", 3, 0, 255);
        ATOMIC_BLAST_CRATER_RADIUS = BUILDER
                .comment("Radius of the crater blasted out where the atomic breath lands: a bowl about half as deep as it is",
                        "wide, floored with scorched rubble (0 = no block damage; also needs mobGriefing).")
                .defineInRange("craterRadius", 48, 0, 96);
        ATOMIC_BLAST_DESTRUCTION_RADIUS = BUILDER
                .comment("Around the crater the blast wave flattens trees and buildings and scorches the ground out to this radius.")
                .defineInRange("scorchRadius", 60, 0, 160);
        ATOMIC_BLAST_RUIN_RADIUS = BUILDER
                .comment("Out to this radius, whatever the blast wave leaves standing is left in ruins: trees burnt to bare, charred",
                        "trunks, windows blown in, holed walls and roofs, cracked stone, dead crops and grass, scattered fires (fewer further",
                        "out). 0 = off; also needs mobGriefing.")
                .defineInRange("ruinRadius", 100, 0, 200);
        ATOMIC_FLATTEN_RADIUS = BUILDER
                .comment("Everything above flattenDownToY within this many blocks of where the atomic breath lands (trees, grass,",
                        "buildings, hills) is erased, leaving the land flat; nothing drops. Chunks loaded later are flattened as they load.",
                        "0 = off; also needs mobGriefing.")
                .defineInRange("flattenRadius", 60, 0, 2000);
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
        SHINDEN_INHALE_KNOCKOUT = BUILDER
                .comment("While Godzilla takes his deep breath in just before the atomic breath's beam (the 4 s in-breath), a",
                        "piloted Shinden brings him down in one hit: crashed into him (or blown up by his radiation as it closes in)",
                        "or a Torrid burst into him takes all his health, and he collapses (and rests for downedSeconds).")
                .define("shindenInhaleKnockout", true);
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

        BUILDER.push("playable");
        PLAYER_GODZILLA_BREAKS_BLOCKS = BUILDER
                .comment("Playing as Godzilla (Walkers / ReMorphed): walking through buildings breaks them, each stride crushes a",
                        "footprint, and his claws and jaws tear through what's built. Also needs breaksBlocks, the mobGriefing",
                        "gamerule, and the player being allowed to build there.")
                .define("playerGodzillaBreaksBlocks", true);
        PLAYER_GODZILLA_FOOTPRINTS = BUILDER
                .comment("Playing as Godzilla: his strides crush footprints (with playerGodzillaBreaksBlocks).")
                .define("playerGodzillaFootprints", true);
        PLAYER_GODZILLA_STEP_HEIGHT = BUILDER
                .comment("Playing as Godzilla and climbing (not walking through things: each player picks with a key, M by default,",
                        "or in the config screen): he steps straight up onto anything up to this many blocks high.")
                .defineInRange("playerGodzillaStepHeight", 20, 1, 64);
        PLAYER_ODO_SPEED = BUILDER
                .comment("Playing as the Odo Island Monster: how fast he moves, in blocks per second on level ground, whenever he",
                        "moves (no key to hold; sprinting doesn't add to it). Only the player playing as him. (Replaces",
                        "playerOdoRunBlocksPerSecond, his speed while a Run key was held, in earlier versions.)")
                .defineInRange("playerOdoBlocksPerSecond", 12.0, 1.0, 20.0);
        PLAYER_ODO_BITE_DAMAGE = BUILDER
                .comment("Playing as the Odo Island Monster: damage of his bite (left click).")
                .defineInRange("playerOdoBiteDamage", 30.0, 0.0, 1000.0);
        BUILDER.pop();
    }

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private Config() {
    }
}
