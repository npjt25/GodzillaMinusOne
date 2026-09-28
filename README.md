# Godzilla Minus One — Forge 1.20.1

A Minecraft Java Edition (Forge 1.20.1, GeckoLib 4) port of the Bedrock add-on
**"Godzilla Minus One V3" by GojiDraw**. The model, animations, textures and sounds are the add-on's;
Godzilla's brown skin is recoloured to a charcoal grey.

![Original colours, new grey, and charging](docs/godzilla_preview.png)

*Left: the add-on's original colours. Middle: this mod. Right: charging the atomic breath.*

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
| `goji:nuclear_hole` | The crater left by the atomic breath |
| Particles, camera shakes/fades, custom boss bar | Recreated in Java (see `client/`) |

### Godzilla

- Spawns from his egg by rising out of the ground and roaring, then becomes active (with the add-on's boss bar).
- **Radiation aura**: anything within 16 blocks gets "RADIATION POISONING" and Wither.
- Every step shakes the camera and hurts everything near him. He smashes through blocks (needs `mobGriefing`).
- Attacks with bites, stomps and tail swipes.
- With a target he **charges his atomic breath**: the dorsal plates glow blue, mobs freeze in fear, and about
  11 seconds in the beam fires. The blast goes off where the beam hits the ground, kills everything around it,
  leaves a crater and fire, and is followed by a mushroom cloud and black rain.
- He regenerates while calm and likes to wander into water. He walks along the sea floor.
- **Only naval mines hurt him** (as in the add-on), each taking about 10% of his health and stunning him.
  At zero health he collapses, lies dead for 15 seconds, then regenerates and rises again.

### Kyushu J7W1 Shinden

- Right-click to climb in. **W** accelerates, **S** brakes, the mouse steers: look up to climb, down to dive.
- You get the **Torrid** while seated (right-click to fire forward); it disappears when you get out.
- Pilots have the add-on's "plot armour": Godzilla's blasts barely scratch them.
- Fly within 15 blocks of Godzilla and the plane detonates like a naval mine — the film's ending — and you bail
  out with Slow Falling as a parachute.
- Sneak-punch an empty plane to pick it back up.
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

`tools/render_preview.py` renders a model with a texture to a PNG, for checking texture changes without
starting the game.

## Credits

Original Bedrock add-on (model, textures, animations, sounds): **GojiDraw**. The add-on is marked
"Bedrock only"; this port is for personal use. Ask GojiDraw before publishing it anywhere.
Godzilla is a trademark of Toho Co., Ltd.
