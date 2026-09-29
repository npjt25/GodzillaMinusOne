# Godzilla Minus One — Forge 1.20.1

A Minecraft Java Edition (Forge 1.20.1, GeckoLib 4) port of the Bedrock add-on
**"Godzilla Minus One V3" by GojiDraw**. Godzilla is drawn with the high-detail
[Godzilla Minus One model by ArachnoBoy](https://sketchfab.com/3d-models/godzilla-minus-one-ccaf341a3e734263a88cecb5c49eac0e)
(in charcoal grey), rigged to the add-on's skeleton so he moves with its animations. The Shinden, naval
mine, sounds and effects come from the add-on.

![Godzilla idle, charging, and breathing in](docs/godzilla_preview.png)

*Left: idle. Middle: charging, the dorsal plates lighting up from the tail. Right: the in-breath before the beam.*

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
| `goji:mine_spawn` / `goji:mine` | **Naval Mine**, craftable (4 netherite ingots, 4 netherite blocks, TNT in the middle) |
| `goji:nuclear_hole` | The crater and blast wave left by the atomic breath |
| Nuclear explosion particles | A 3D fireball and mushroom cloud (`client/render/NuclearCloud.java`) |
| Particles, camera shakes, custom boss bar | Recreated in Java (see `client/`); the fade to black is a blur instead |

### Godzilla

- About 45 blocks tall and 80 long. Spawns from his egg by rising out of the ground and roaring, then becomes
  active (with the add-on's boss bar).
- **Radiation aura**: anything within 16 blocks gets "RADIATION POISONING" and Wither.
- Every footfall shakes the camera, hurts everything near him and **crushes a footprint** of rubble into the
  ground. He smashes through blocks (needs `mobGriefing`).
- Attacks with bites, stomps and tail swipes.
- With a target he **charges his atomic breath**: the dorsal plates light up blue one by one from the tail to
  the neck as they punch up, mobs freeze in fear, he breathes in for 4 seconds, then fires with his head angled
  up (he tilts it further up at planes). Where the beam lands a **nuclear blast** kills everything around it,
  blasts out a large crater, flattens trees and buildings and scorches the ground in a wide ring, and a 3D
  fireball rises into a mushroom cloud. Everyone nearby sees a blur, the sky clouds over with (black) rain for
  2 minutes, then clears. He then needs **3 minutes to recharge** before the next breath.
- **A plane in the air always comes first**: if someone is flying a Shinden, he goes after it.
- He regenerates while calm and likes to wander into water. In shallow water he walks along the bottom; in deep
  water he **swims at the surface** with his dorsal plates out of the water, runs down boats and bites at them
  (a new swimming bite, he doesn't stand up).
- **Only naval mines hurt him** (as in the add-on), each taking about 10% of his health and stunning him.
  At zero health he collapses, lies dead for 15 seconds, then regenerates and rises again.

### Kyushu J7W1 Shinden

- Right-click to climb in. **W** accelerates, **S** brakes, the mouse steers: look up to climb, down to dive.
- You get the **Torrid** while seated (right-click to fire forward); it disappears when you get out.
- Pilots have the add-on's "plot armour": Godzilla's blasts barely scratch them.
- Fly within 15 blocks of Godzilla, or crash into something at (near) full speed, and the plane detonates like
  a naval mine — the film's ending — and you bail out with Slow Falling as a parachute.
- Hit it like a boat: it rocks, and a few hits knock it back into its item (one hit in creative).
- **Multiplayer servers**: set `allow-flight=true` in `server.properties`, or flying players get kicked.

### Naval Mine

- Floats on water; can be leashed and towed.
- Anything that damages it sets it off: arrows, Godzilla's footsteps, his radiation.
- Blast radius 30. Sneak-punch it with an empty hand to pick it back up.

## Configuration

`config/godzillaminusone-common.toml`. The add-on's world-ending numbers (2000-block kill radius,
Wither XXVI) are toned down by default; each option's comment lists the add-on's original value so you can
restore it. You can also make Godzilla mortal, stop him regenerating, or turn off block damage.

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

Then rebuild the high-detail Godzilla from the Sketchfab download and adapt the animations to it:

```
python3 tools/build_godzilla_mesh.py path/to/extracted/godzilla_minus_one   # skinned mesh, skeleton, textures
python3 tools/edit_animations.py                                           # bigger model, in-breath, swim bite
python3 tools/make_cloud_textures.py                                       # noise textures of the 3D explosion
```

Previews without starting the game: `tools/preview_godzilla.py` poses the skinned mesh with any animation frame,
`tools/preview_cloud.py` renders the nuclear explosion at given times, `tools/render_preview.py` renders a
cube model with its texture.

## Credits

Original Bedrock add-on (models, textures, animations, sounds): **GojiDraw**. The add-on is marked
"Bedrock only"; this port is for personal use. Ask GojiDraw before publishing it anywhere.

High-detail Godzilla model: ["Godzilla Minus One" by ArachnoBoy](https://sketchfab.com/3d-models/godzilla-minus-one-ccaf341a3e734263a88cecb5c49eac0e),
Sketchfab Standard licence.
Godzilla is a trademark of Toho Co., Ltd.
