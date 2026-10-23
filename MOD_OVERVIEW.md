# Godzilla Minus One — Minecraft Forge mod (overview for context)

**What it is:** A Minecraft Java Edition mod for **Forge 1.20.1 (47.x)**, using **GeckoLib 4.x** for animated models. It ports GojiDraw's Bedrock add-on "Godzilla Minus One" V3 to Java and adds a lot of new content from the film *Godzilla Minus One* (2023). Optional support for **Walkers / ReMorphed** (play as Godzilla). Built and tested with GitHub Actions (build + 32 server-side game tests). Source: github.com/npjt25/GodzillaMinusOne (branch `claude/admiring-euler-xygs92`).

## Godzilla (`godzillaminusone:godzilla`)
- Boss mob, GojiDraw's model scaled to ~45 blocks tall, ~76 long. Phases: emerge from ground → roar → active → collapse → dead → regenerate.
- Roar heard up to 3000 blocks away (louder as you get closer); roars every 2 min (configurable).
- Radiation aura (Wither) within 16 blocks. Footsteps shake the camera, hurt nearby mobs and crush footprints into the ground.
- Smashes through blocks; **buildings topple over** as he walks through/swipes them (falling blocks).
- Knocks tall buildings (≥23 blocks) down to ~11 blocks when nothing else to fight.
- Melee combo: two claw swipes, a full-body spin with a whip-snap tail; bites above, stomps below, tail swing behind.
- **Atomic breath:** dorsal plates light up blue tail-to-neck, 4-second in-breath while his **mouth glows blue, brighter until it's white-hot just before firing**; he rears up and fires. His **head turns to aim** (left/right up to 50°, up/down) and the beam comes **out between his jaws**. Plates slam down as the beam fires.
- 2 s after impact, a **nuclear blast**: kills nearby, ~96-wide crater, blast wave, **levels land above Y 65 within 60 blocks** (no drops), and **ruins everything out to 100 blocks** (burnt bare trees, charred trunks, broken glass, holed walls/roofs, cracked stone, dead crops, fires). Black rain particles.
- Breath recharges in 3 min (configurable); not usable in his first 2–3 minutes. Burns his own mouth (heals over 40 s).
- Swims in deep water with plates showing; head periodically surfaces to glare. **In the water the Shinsei Maru boat is his main target.**
- Only naval mines hurt him; at zero health he collapses, then regenerates.

## The atomic blast (visuals)
Film-accurate "Ginza blast" sequence: flash, fireball, dust ring/shockwave, rising column, mushroom cap (built from Alex's Caves cloud textures), cooling, mature cloud, slow break-up (~3 min). Fully configurable timings/size.

## Odo Island Monster (`godzillaminusone:odo_island_monster`)
- Godzilla's earlier dinosaur form (Monster122's Godzillasaurus model), 14 blocks tall, 600 HP.
- Charges players within ~16 blocks; frenzies when seen/hurt (faster, harder bites/stomps, breaks wooden things).
- **Spotlight reaction:** caught by a manned searchlight's beam he stares into it, roars, charges and smashes it, then hunts its operator. If the light is on/in a building he **tears the building down** to get it. A player coming within 12 blocks distracts him onto them.
- Hit by a nuclear bomb he's burnt, lies dormant 3 minutes, then **rises as Godzilla**.

## Searchlight
WWII searchlight, mountable. Rider aims it by looking; **R** toggles the lamp. Unmanned it **sweeps automatically like a clock** (doesn't anger Odo). Beam is a soft translucent shaft with a lit pool where it lands.

## Shinsei Maru (boat)
- The film's run-down minesweeper, **16.3 × 5.6 blocks**, heavily weathered (rust streaks, flaking paint, tyre fenders, nets, rigging).
- **Walkable solid decks** (25 collision sections), you're carried as she moves; **ladders** on both sides and up the wheelhouse to the roof helm.
- **Two seats:** driver (wheel on wheelhouse roof; W/S/A/D; **R** drops naval mines from her 9-slot hold) and gunner (Type 93 machine gun at the stern, **automatic while holding right-click**). Seats tilt with the deck.
- Rides the swell (pitch/roll/heave, rougher in storms, bigger with Physics Mod installed) with occasional wave slaps. Godzilla prioritises her in water.

## Other items/entities
- **Kyushu J7W1 Shinden** fighter plane: flyable, hold right-click for cannons, explodes near Godzilla (film ending) or on crash.
- **Naval Mine** (craftable) + **Remote Detonator**. Only thing that damages Godzilla.
- **Nuclear Bomb** item: called down at a target, 20 s air-raid siren, same blast as the atomic breath.
- **Godzilla statues** (summon only, for thumbnails): `godzilla_pose_charging_roar`, `_tail_whip`, `_sky_roar`, `_poster_snarl` (70th anniversary poster, glowing mouth and plates), `_tail_hook`.

## Playing as Godzilla (optional Walkers / ReMorphed)
- Unlock via Walkers (unlock key / `/walkers change2ndShape`) or ReMorphed (defeat him or `/remorphed addShape`).
- Forced third-person camera pulled back to frame his whole body.
- Moves with a small top-left HUD: **Z** Tail Whip, **H** Scratch, **J** Bite, **K** Roar (weakness/slowness in 48 blocks), **B** Atomic Breath (full charge + nuclear blast, 3-min recharge; head follows your look).
- Walking breaks through whatever you walk into and leaves footprints.
- Note: Walkers' ability key is also R (conflicts with the mod's "Vehicle Action").

## Config
In-game config screen (health bar, breath/roar intervals, blast visuals) plus `godzillaminusone-common.toml` (kill/radiation radius, `craterRadius` 48, `scorchRadius` 60, `ruinRadius` 100, `flattenRadius` 60, `flattenDownToY` 65, building demolition, block breaking, mortality, etc.).

## Physics Mod
No API exists to read its waves, so the mod does its own physics: the boat bobs on its own swell (scaled up when Physics Mod is present), toppled buildings fall as falling blocks, and some blocks break normally so Physics Mod can shatter them.

## Credits
GojiDraw (original add-on assets), Monster122 (Odo/Godzillasaurus), Alex's Caves by AlexModGuy (LGPL, cloud textures), Walkers/ReMorphed by To_Craft (MIT, optional API). Godzilla is a trademark of Toho Co., Ltd. Personal-use port.
