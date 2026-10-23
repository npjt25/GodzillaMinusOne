# Godzilla Minus One — Minecraft Forge mod (overview for context)

**What it is:** A Minecraft Java Edition mod for **Forge 1.20.1 (47.x)**, using **GeckoLib 4.x** for animated models. It ports GojiDraw's Bedrock add-on "Godzilla Minus One" V3 to Java and adds a lot of new content from the film *Godzilla Minus One* (2023). Optional support for **Walkers / ReMorphed** (play as Godzilla). Built and tested with GitHub Actions (build + server-side game tests + a scripted run of the real client on a virtual display, with checks and screenshots). Source: github.com/npjt25/GodzillaMinusOne (branch `claude/admiring-euler-xygs92`).

## Godzilla (`godzillaminusone:godzilla`)
- Boss mob, GojiDraw's model scaled to ~45 blocks tall, ~76 long. Phases: emerge from ground → roar → active → collapse → dead → regenerate.
- Roar heard up to 3000 blocks away (louder as you get closer); from 200 blocks on, listeners hear distant-roar recordings instead (alternating). Roars every 2 min (configurable).
- Radiation aura (Wither) within 16 blocks. Footsteps shake the camera, hurt nearby mobs and crush footprints into the ground.
- Smashes through blocks; **buildings topple over** as he walks through/swipes them (falling blocks).
- Knocks tall buildings (≥ half his ~45-block height, 23) down to a quarter (11) when nothing else to fight, then rests `demolitionIntervalSeconds` (20) before the next.
- Melee combo: two claw swipes, a full-body spin with a whip-snap tail; a real bite (lunge + jaw snap, bite sound), stomps below, tail swing behind.
- Only breathes at targets **≥75 blocks** away (`minTargetDistance`); closer ones get melee. After the beam he roars once (full roar) and goes back to normal.
- **Atomic breath:** dorsal plates light up blue tail-to-neck, 4-second in-breath while his **mouth glows blue, brighter until it's white-hot just before firing**; he rears up and fires. His **head turns to aim** (left/right up to 50°, up/down) and the beam comes **out between his jaws**. Plates slam down as the beam fires.
- 2 s after impact, a **nuclear blast**: kills nearby, ~96-wide crater, blast wave, **levels land above Y 65 within 60 blocks** (no drops), and **ruins everything out to 100 blocks** (burnt bare trees, charred trunks, broken glass, holed walls/roofs, cracked stone, dead crops, fires). Black rain particles.
- Breath recharges in 3 min (configurable); not usable in his first 2–3 minutes. Burns his own mouth (heals over 40 s).
- Swims in deep water with plates showing; head periodically surfaces to glare. **In the water the Shinsei Maru boat is his main target.**
- Only naval mines hurt him; at zero health he collapses, lies down `downedSeconds` (15), then regenerates. Exception: while his mouth is burnt after a breath, a Shinden crashed into his **face** defeats him outright.

## The atomic blast (visuals)
Film-accurate "Ginza blast" sequence: flash, fireball, dust ring/shockwave, rising column, mushroom cap (built from Alex's Caves cloud textures), cooling, mature cloud, slow break-up (~3 min). Fully configurable timings/size.

## Odo Island Monster (`godzillaminusone:odo_island_monster`)
- Godzilla's earlier dinosaur form (Monster122's Godzillasaurus model), 14 blocks tall, 600 HP.
- Charges players within ~16 blocks; frenzies when seen/hurt (faster, harder bites/stomps, breaks wooden things).
- **Spotlight reaction:** caught by a manned searchlight's beam he stares into it, roars, charges and smashes it, then hunts its operator. If the light is on/in a building he **tears the building down** to get it. A player coming within 12 blocks distracts him onto them.
- Hit by a nuclear bomb he's burnt, lies dormant 3 minutes, then **rises as Godzilla**.
- Sounds: roar, searchlight-spotted shriek, spawn cry (alone: no vanilla ambient growl or roar over it), bite, nuked cry, distant roar (100+ blocks). Vanilla-style spawn egg. Never gets stuck going for a light (tears through obstacles; gives up after 30 s and hunts the operator).

## Searchlight
WWII searchlight, mountable. Rider aims it by looking; **R** toggles the lamp. Unmanned it **sweeps automatically like a clock** (doesn't anger Odo). Beam is slightly blocky (stepped square sections + soft core) with a block-by-block lit pool; drawn after water/ice so it no longer x-rays through them.

## Shinsei Maru (boat)
- The film's run-down minesweeper, **16.3 × 5.6 blocks**, heavily weathered (rust streaks, flaking paint, tyre fenders, nets, rigging).
- **Walkable solid decks** (overlapping small tiles: support at any heading, incl. the wheelhouse roof), you're carried as she moves; **ladders** on both sides and up the wheelhouse to the roof helm.
- **Two seats:** driver (wheel on wheelhouse roof; W/S/A/D; **R** drops naval mines from her 9-slot hold) and gunner (Type 93 machine gun at the stern, **automatic while holding right-click**). Both crew stand at their stations.
- Sits steady (no bobbing of her own). Physics Mod 3.0.20 moves every entity it draws on its own waves (EntityOcean, mixed into all entities, applied in EntityRenderDispatcher), so she should ride them where its ocean runs; not seen working (its ocean never came up in CI's software-rendered game). Quiet synthesized diesel motor loop while under way. Godzilla prioritises her in water.

## Other items/entities
- **Kyushu J7W1 Shinden** fighter plane: flyable, hold right-click for cannons, explodes near Godzilla (film ending) or on crash. Automatic third person framing the plane while piloting; engine silent while parked on the ground; hold Left Ctrl to free-look without steering.
- **Naval Mine** (craftable; also in end city 25%, nether fortress 15%, ancient city 20% chests, 1–3 per chest weighted 6:3:1, 3 rarest) + **Remote Detonator**. Only thing that damages Godzilla.
- **Nuclear Bomb** item: called down at a target, 20 s air-raid siren, same blast as the atomic breath.
- **Godzilla statues** (summon only, for thumbnails): `godzilla_pose_charging_roar`, `_tail_whip`, `_sky_roar`, `_poster_snarl` (70th anniversary poster, glowing mouth and plates), `_tail_hook`, `_hunched_snarl` (square-on, hunched, head low between the shoulders).

## Playing as Godzilla (optional Walkers / ReMorphed)
- Unlock via Walkers (unlock key / `/walkers change2ndShape`) or ReMorphed (defeat him or `/remorphed addShape`).
- Forced third-person camera pulled back to frame his whole body.
- Moves with a small top-left HUD: **Z** Tail Whip, **H** Scratch, **J** Bite, **K** Roar (weakness/slowness in 48 blocks), **B** Atomic Breath (full charge + nuclear blast, 3-min recharge; head follows your look).
- Walking breaks through buildings and leaves footprints (`playable` config section). J is a real bite. Rooted from breath charge to the end of the post-beam roar; any interruption frees you.

## Playing as the Odo Island Monster (optional)
- Hold Left Ctrl (rebindable, only active as Odo) to run at 6 blocks/s; left click bites.
- Note: Walkers' ability key is also R (conflicts with the mod's "Vehicle Action"). Shinden: hold Left Ctrl for free look.
- Checked in the released game with Walkers 5.8.13, ReMorphed 4.2, CraftedCore 5.8.2 (mod ids walkers, remorphed, craftedcore): Godzilla's J bite, rooted through B's charge and roar then free, breaking through a building, changing back; the Odo's run (6.00 blocks/s measured) and left-click bite.

## Config
In-game config screen (health bar, breath/roar intervals, blast visuals) plus `godzillaminusone-common.toml` (kill/radiation radius, `craterRadius` 48, `scorchRadius` 60, `ruinRadius` 100, `flattenRadius` 60, `flattenDownToY` 65, building demolition (`demolishFromHeight`/`demolishDownToHeight` -1 = auto, `demolitionIntervalSeconds`), `downedSeconds`, `minTargetDistance`, playable-shape options, block breaking, mortality, etc.).

## Physics Mod
No Physics Mod API is called (only its presence is detected). Building toppling is the mod's own falling-block simulation; other breaking uses vanilla block breaking, which Physics Mod shatters by itself (with it installed, all demolition breaks go that way and the mod adds no extra particles). Its waves are its own client-side business for every entity (see the boat above). All in-game checks pass in the released game with Physics Mod 3.0.20 installed.

## Credits
GojiDraw (original add-on assets), Monster122 (Odo/Godzillasaurus), Alex's Caves by AlexModGuy (LGPL, cloud textures), Walkers/ReMorphed by To_Craft (MIT, optional API). Godzilla is a trademark of Toho Co., Ltd. Personal-use port.
