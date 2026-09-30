# Godzilla Minus One — Forge 1.20.1

A Minecraft Java Edition (Forge 1.20.1, GeckoLib 4) port of the Bedrock add-on
**"Godzilla Minus One" V3 by GojiDraw**. Godzilla is the add-on's model in charcoal grey, made bigger and
given more detail: layered dorsal plates, a jagged, lumpy hide with tumor-like growths, sinister yellow eyes
and rows of teeth, using the
[Godzilla Minus One model by ArachnoBoy](https://sketchfab.com/3d-models/godzilla-minus-one-ccaf341a3e734263a88cecb5c49eac0e)
only as a reference (none of it is in the mod). The Shinden, naval mine, sounds and effects come from the
add-on. The mod also adds the **Odo Island Monster** (Godzilla's smaller, dinosaur-like form from the start
of the film), a **searchlight**, a **nuclear bomb**, a **remote detonator** for the naval mines, and posed
**Godzilla statues** for screenshots and thumbnails.

![Godzilla idle, charging, and breathing in](docs/godzilla_preview.png)

*Left: idle. Middle: charging, the dorsal plates lighting up from the tail. Right: the in-breath before the beam.*

![The Odo Island Monster](docs/odo_preview.png)

*The Odo Island Monster: standing, roaring, and burnt to a crisp by a nuclear bomb.*

![Searchlight and nuclear bomb](docs/props_preview.png)

![Godzilla statues](docs/poses_preview.png)

*The thumbnail statues: charging roar, tail whip, roar to the sky.*

![The blocky mushroom cloud](docs/cloud_preview.png)

*The nuclear explosion: fireball, cap, stem, condensation ring and base surge, all built from 4-block cubes.*

## Requirements

- Minecraft 1.20.1 with Forge 47.x
- [GeckoLib](https://www.curseforge.com/minecraft/mc-mods/geckolib) 4.x for 1.20.1 (4.8.4 tested against)
- Optional: [AttributeFix](https://www.curseforge.com/minecraft/mc-mods/attributefix), only if you raise
  Godzilla's max health above 1024 in the config

## What's in it

| Add-on content | In this mod |
| --- | --- |
| `goji:gojira` (+ dead / spawn stages) | **Godzilla** (`godzillaminusone:godzilla`), one entity with emerge → roar → active → collapse → dead → regenerate phases |
| `goji:kyushu_j7w1` | **Kyushu J7W1 Shinden**, a fighter you can fly |
| `goji:torrid` | **Torrid**, the Shinden's cannon, handed to the pilot |
| `goji:mine_spawn` / `goji:mine` | **Naval Mine**, craftable (4 netherite ingots, 4 netherite blocks, TNT in the middle), and a **Remote Detonator** for it |
| `goji:nuclear_hole` | The crater and blast wave left by the atomic breath |
| Nuclear explosion particles | A blocky 3D fireball and mushroom cloud (`client/render/NuclearCloud.java`) |
| Particles, camera shakes, custom boss bar | Recreated in Java (see `client/`); the fade to black is a blur instead |
| *(new)* | **Odo Island Monster** (`godzillaminusone:odo_island_monster`), with its spawn egg |
| *(new)* | **Searchlight**, a mountable WWII searchlight |
| *(new)* | **Nuclear Bomb**, called in from the sky |
| *(new)* | **Godzilla statues** in three poses, `/summon` only |

### Godzilla

- About 45 blocks tall and 70 long. Spawns from his egg by rising out of the ground and roaring, then becomes
  active (with the add-on's boss bar).
- **Radiation aura**: anything within 16 blocks gets "RADIATION POISONING" and Wither.
- Every footfall shakes the camera, hurts everything near him and **crushes a footprint** of rubble into the
  ground. He smashes through blocks (needs `mobGriefing`).
- Up close he attacks in a combo: a **claw swipe with each hand**, then a **heavy spin** of his whole body with
  the tail sweeping round him and **snapping like a whip**. He bites at things above him, stomps on things
  under his feet, and swings the tail round at anything behind him.
- With a target he **charges his atomic breath**: the dorsal plates light up blue one by one from the tail to
  the neck as they punch up, mobs freeze in fear, he breathes in for 4 seconds (the in-breath sound lasts exactly as long), then fires with his head angled
  up (he tilts it further up at planes). The beam comes straight out of his open mouth. Where it lands a
  **nuclear blast** kills everything around it, blasts out a large crater, flattens trees and buildings and
  scorches the ground in a wide ring, and a blocky fireball rises into a mushroom cloud. Everyone nearby sees a
  blur, then black rain falls around ground zero (just particles: the weather isn't changed). He then needs
  **3 minutes to recharge** before the next breath.
- The breath **burns his own mouth**: the flesh round it is left raw, blistered and glowing, smoking and
  spitting embers, then tears and knits back together over 40 seconds.
- **A plane in the air always comes first**: if someone is flying a Shinden, he goes after it.
- He regenerates while calm and likes to wander into water. In shallow water he walks along the bottom; in deep
  water he **swims at the surface** with his dorsal plates out of the water, runs down boats and bites at them
  (a new swimming bite, he doesn't stand up). Every 10 seconds his **head comes up out of the water** and
  glares around for 4 seconds, then sinks back under.
- **Only naval mines hurt him** (as in the add-on), each taking about 10% of his health and stunning him.
  At zero health he collapses, lies dead for 15 seconds, then regenerates and rises again.

### Kyushu J7W1 Shinden

- Right-click to climb in. **W** accelerates, **S** brakes, the mouse steers: look up to climb, down to dive.
  The plane noses up as it climbs and down as it dives, stays level in level flight, and banks into turns.
- You get the **Torrid** while seated (right-click to fire forward); it disappears when you get out.
- Pilots have the add-on's "plot armour": Godzilla's blasts barely scratch them.
- Fly within 15 blocks of Godzilla and the plane detonates like a naval mine (the film's ending), and you bail
  out with Slow Falling as a parachute.
- Crash into something at (near) full speed and it explodes on impact as big as **3 TNT** (a block-breaking
  explosion of power 12). You bail out and the explosion spares you.
- Hit it like a boat: it rocks, and a few hits knock it back into its item (one hit in creative).
- **Multiplayer servers**: set `allow-flight=true` in `server.properties`, or flying players get kicked.

### Naval Mine

- Floats on water; can be leashed and towed.
- Hitting it by hand (or with a tool) doesn't hurt it: it rocks with a clank, and **four hits knock it loose**
  into its item so you can pick it back up (one hit in creative).
- Anything else that damages it sets it off: arrows, explosions, fire, Godzilla's footsteps, his radiation.
- **Remote Detonator** (a lightning rod on top as the antenna / iron ingot, redstone, iron ingot / iron ingot,
  stone button, iron ingot): point it at a naval mine up to 160 blocks away and right-click, and it goes off.
  Blast radius 30: stand well back.

### Odo Island Monster

- Godzilla's first form from Odo Island: a shorter, dinosaur-like Godzilla, **15 blocks tall**, 600 health,
  hunched forward on massive legs, with a long thick neck, big clawed arms, a brown hide of overlapping scale
  rows and a cluster of tall jagged plates over his shoulders.
- Left alone he wanders and ignores you, but get within about 16 blocks and he **charges**: he runs you down,
  crashing through trees and fences, and **bites** or **stomps** you.
- **Caught in a searchlight's beam he goes berserk**: he roars and comes for whoever is working the light, and
  stays enraged for a minute.
- **Nuke him** with a Nuclear Bomb and he doesn't die: he collapses, **burnt to a crisp**, the embers in his
  hide slowly going out. Nothing can hurt him while he lies there. **Three minutes later he rises again as
  Godzilla.** (Godzilla's own atomic breath just kills him.)

### Searchlight

- Crafted from iron ingots, glass, a redstone lamp and iron blocks (iron ingot, glass, iron ingot / iron
  ingot, redstone lamp, iron ingot / three iron blocks). Right-click the ground to set it up facing the way
  you look.
- Right-click it to sit at it: the lamp turns and tilts with where you look, and while you're manning it the
  beam is lit, reaching 160 blocks and lighting up where it lands. Sneak to get off.
- Hit it like a boat to knock it back into its item.

### Nuclear Bomb

- Crafted from netherite ingots in the corners, TNT on the sides and a nether star in the middle.
- Use it while looking at a spot up to 256 blocks away: a bomb is dropped from high above it, marked by a red
  flare and a column of smoke. Everyone nearby gets a countdown; it **falls for 20 seconds**, whistling over
  the last seconds, then goes off like Godzilla's atomic blast (same kill radius, radiation, crater and
  mushroom cloud; see the atomic blast options in the config). It goes off early if it hits something on the
  way down, such as a roof. Stand well back: the kill radius is 64 blocks by default.

### Godzilla statues (for thumbnails)

Godzilla frozen in poses from the film's posters. They never spawn on their own and have no spawn egg: make
them with `/summon`. A new statue turns to face you; it holds still (in mid-air too), does nothing and can't be
hurt. Hit it in creative mode, or `/kill` it, to remove it.

| Pose | Command |
| --- | --- |
| Roaring, one arm flung out, the glowing tail raised high round his side | `/summon godzillaminusone:godzilla_pose_charging_roar` |
| The tail swung round in front of him, its tip rising | `/summon godzillaminusone:godzilla_pose_tail_whip` |
| Roaring up at the sky, arms spread | `/summon godzillaminusone:godzilla_pose_sky_roar` |

To face a statue a given way, give it a rotation, e.g. `/summon godzillaminusone:godzilla_pose_sky_roar ~ ~ ~ {Rotation:[180f,0f]}`.

## Configuration

- **In game**: Mods → Godzilla Minus One → Config. **Godzilla Health Bar** turns his health bar at the top of
  the screen on or off (saved in `config/godzillaminusone-client.toml`).
- **Gameplay**: `config/godzillaminusone-common.toml`. The add-on's world-ending numbers (2000-block kill
  radius, Wither XXVI) are toned down by default; each option's comment lists the add-on's original value so
  you can restore it. You can also make Godzilla mortal, stop him regenerating, or turn off block damage.

## Building

```
./gradlew build
```

The jar ends up in `build/libs/`. GitHub Actions builds it on every push (see the *Actions* tab, artifact
`godzillaminusone-jar`) and then runs the server-side game tests (`./gradlew runGameTestServer`, see
`src/main/java/com/npjt25/godzillaminusone/gametest`).

## Re-converting the add-on assets

`tools/convert_addon.py` regenerates everything under `src/main/resources/assets` from the extracted
`.mcaddon` (models, cleaned-up animations, recoloured textures and glow masks, sounds converted to mono Ogg):

```
pip install pillow numpy imageio-ffmpeg
python3 tools/convert_addon.py path/to/extracted/addon
```

Then add the detail to Godzilla, edit the animations, and build the rest:

```
python3 tools/detail_godzilla.py        # plates, lumps and growths, teeth, eyes, the finer hide; the burns after the beam
python3 tools/edit_animations.py        # in-breath, beam from the mouth, swimming bite and peek, melee combo, statue poses
python3 tools/make_sounds.py            # the in-breath and the falling bomb's whistle
python3 tools/make_odo_model.py         # the Odo Island Monster: model, textures, animations (odo_animations.py)
python3 tools/make_props.py             # the searchlight and nuclear bomb models
python3 tools/make_icons.py             # item icons of the new items
python3 tools/make_cloud_textures.py    # noise textures of the 3D explosion
```

Previews without starting the game: `tools/render_preview.py` renders a cube model with its texture (and any
animation frame), `tools/preview_cloud.py` renders the blocky nuclear explosion at given times.

## Credits

Original Bedrock add-on (models, textures, animations, sounds): **GojiDraw**. The add-on is marked
"Bedrock only"; this port is for personal use. Ask GojiDraw before publishing it anywhere.

Godzilla's added detail was modelled with ["Godzilla Minus One" by ArachnoBoy](https://sketchfab.com/3d-models/godzilla-minus-one-ccaf341a3e734263a88cecb5c49eac0e)
as a visual reference; none of that model is included.
Godzilla is a trademark of Toho Co., Ltd.
